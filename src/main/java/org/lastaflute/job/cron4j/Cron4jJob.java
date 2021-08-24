/*
 * Copyright 2015-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, 
 * either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.lastaflute.job.cron4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.dbflute.optional.OptionalThing;
import org.dbflute.optional.OptionalThingIfPresentAfter;
import org.dbflute.util.DfTypeUtil;
import org.lastaflute.job.LaJob;
import org.lastaflute.job.LaJobHistory;
import org.lastaflute.job.LaScheduledJob;
import org.lastaflute.job.exception.JobAlreadyDisappearedException;
import org.lastaflute.job.exception.JobAlreadyUnscheduleException;
import org.lastaflute.job.exception.JobTriggeredNotFoundException;
import org.lastaflute.job.key.LaJobKey;
import org.lastaflute.job.key.LaJobNote;
import org.lastaflute.job.key.LaJobUnique;
import org.lastaflute.job.log.JobChangeLog;
import org.lastaflute.job.log.JobNoticeLogLevel;
import org.lastaflute.job.subsidiary.CronOption;
import org.lastaflute.job.subsidiary.CronParamsSupplier;
import org.lastaflute.job.subsidiary.JobConcurrentExec;
import org.lastaflute.job.subsidiary.JobExecutingSnapshot;
import org.lastaflute.job.subsidiary.LaunchNowOpCall;
import org.lastaflute.job.subsidiary.LaunchNowOption;
import org.lastaflute.job.subsidiary.LaunchedProcess;
import org.lastaflute.job.subsidiary.NeighborConcurrentGroup;
import org.lastaflute.job.subsidiary.SnapshotExecState;
import org.lastaflute.job.subsidiary.VaryingCronOpCall;
import org.lastaflute.job.subsidiary.VaryingCronOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.sauronsoftware.cron4j.TaskExecutor;

/**
 * @author jflute
 * @since 0.2.0 (2016/01/11 Monday)
 */
public class Cron4jJob implements LaScheduledJob {

    // ===================================================================================
    //                                                                          Definition
    //                                                                          ==========
    private static final Logger logger = LoggerFactory.getLogger(Cron4jJob.class);

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    protected final LaJobKey jobKey;
    protected final OptionalThing<LaJobNote> jobNote;
    protected final OptionalThing<LaJobUnique> jobUnique;
    protected volatile OptionalThing<Cron4jId> cron4jId; // mutable for non-cron
    protected final Cron4jTask cron4jTask; // 1:1
    protected final Cron4jNow cron4jNow; // n:1

    protected volatile boolean unscheduled;
    protected volatile boolean disappeared;

    // next trigger, used in synchronized but copy-on-write just in case
    protected final Set<LaJobKey> triggeredJobKeySet = new CopyOnWriteArraySet<LaJobKey>(); // not null
    protected final Object triggeredJobLock = new Object(); // for minimum lock scope to avoid deadlock

    // outlaw parallel, used in synchronized but copy-on-write just in case
    protected final List<Cron4jTask> outlawParallelTaskList = new CopyOnWriteArrayList<Cron4jTask>(); // not null
    protected final Object outlawParallelLock = new Object(); // for minimum lock scope to avoid deadlock

    // these are same life-cycle (list to keep order for machine-gun synchronization)
    protected Map<String, NeighborConcurrentGroup> neighborConcurrentGroupMap; // null allowed if no neighbor
    protected List<NeighborConcurrentGroup> neighborConcurrentGroupList; // null allowed if no neighbor

    // ===================================================================================
    //                                                                         Constructor
    //                                                                         ===========
    public Cron4jJob(LaJobKey jobKey, OptionalThing<LaJobNote> jobNote, OptionalThing<LaJobUnique> jobUnique,
            OptionalThing<Cron4jId> cron4jId, Cron4jTask cron4jTask, Cron4jNow cron4jNow) {
        this.jobKey = jobKey;
        this.jobNote = jobNote;
        this.jobUnique = jobUnique;
        this.cron4jId = cron4jId;
        this.cron4jTask = cron4jTask;
        this.cron4jNow = cron4jNow;
    }

    // ===================================================================================
    //                                                                       Executing Now
    //                                                                       =============
    // -----------------------------------------------------
    //                                          If Executing
    //                                          ------------
    @Override
    public OptionalThingIfPresentAfter ifExecutingNow(Consumer<SnapshotExecState> oneArgLambda) {
        return mapExecutingNow(execState -> {
            oneArgLambda.accept(execState);
            return (OptionalThingIfPresentAfter) (processor -> {});
        }).orElseGet(() -> {
            return processor -> processor.process();
        });
    }

    // -----------------------------------------------------
    //                                          Is Executing
    //                                          ------------
    @Override
    public boolean isExecutingNow() {
        return cron4jTask.isRunningNow() || isAnyOutlawParallelRunningNow();
    }

    protected boolean isAnyOutlawParallelRunningNow() {
        synchronized (outlawParallelLock) { // just in case
            return outlawParallelTaskList.stream().anyMatch(task -> task.isRunningNow());
        }
    }

    // -----------------------------------------------------
    //                                         Map Executing
    //                                         -------------
    @Override
    public <RESULT> OptionalThing<RESULT> mapExecutingNow(Function<SnapshotExecState, RESULT> oneArgLambda) {
        return findRunningBeginTime().flatMap(time -> {
            return OptionalThing.ofNullable(oneArgLambda.apply(new SnapshotExecState(time)), () -> {
                throw new IllegalStateException("Not found the result from your scope: job=" + toIdentityDisp() + "(" + time + ")");
            });
        });
    }

    protected OptionalThing<LocalDateTime> findRunningBeginTime() {
        OptionalThing<LocalDateTime> beginTime = extractRunningBeginTime(cron4jTask);
        if (!beginTime.isPresent()) {
            synchronized (outlawParallelLock) { // just in case
                final Optional<LocalDateTime> parallelTime = outlawParallelTaskList.stream()
                        .map(task -> extractRunningBeginTime(task))
                        .filter(optTime -> optTime.isPresent())
                        .map(optTime -> optTime.get())
                        .findFirst(); // may have many running tasks
                if (parallelTime.isPresent()) {
                    beginTime = OptionalThing.of(parallelTime.get());
                }
            }
        }
        return beginTime;
    }

    // -----------------------------------------------------
    //                                    Executing Snapshot
    //                                    ------------------
    public JobExecutingSnapshot takeSnapshotNow() {
        final OptionalThing<SnapshotExecState> mainExecState = extractRunningBeginTime(cron4jTask).map(beginTime -> {
            return new SnapshotExecState(beginTime);
        });
        final List<SnapshotExecState> outlawParallelExecStateList; // running only
        synchronized (outlawParallelLock) { // just in case
            outlawParallelExecStateList = outlawParallelTaskList.stream()
                    .map(task -> extractRunningBeginTime(task))
                    .filter(optTime -> optTime.isPresent()) // running only
                    .map(optTime -> optTime.get()) // to beginTime
                    .map(beginTime -> new SnapshotExecState(beginTime)) // wrap
                    .collect(Collectors.toList());
        }
        final int executingCount = (mainExecState.isPresent() ? 1 : 0) + outlawParallelExecStateList.size();
        return new JobExecutingSnapshot(executingCount, mainExecState, outlawParallelExecStateList);
    }

    // ===================================================================================
    //                                                                          Launch Now
    //                                                                          ==========
    @Override
    public synchronized LaunchedProcess launchNow() {
        return doLaunchNow(op -> {});
    }

    @Override
    public synchronized LaunchedProcess launchNow(LaunchNowOpCall opLambda) {
        return doLaunchNow(opLambda);
    }

    protected LaunchedProcess doLaunchNow(LaunchNowOpCall opLambda) {
        verifyCanScheduleState();
        final LaunchNowOption option = createLaunchNowOption(opLambda);
        if (JobChangeLog.isEnabled()) {
            JobChangeLog.log("#job ...Launching now: {}, {}", option, this);
        }
        final Cron4jTask nowTask;
        if (option.isOutlawParallel()) { // rare case
            nowTask = prepareOutlawParallelLaunch();
        } else { // mainly here
            // if executed by cron now, duplicate execution occurs but task level synchronization exists
            nowTask = cron4jTask;
        }
        final TaskExecutor taskExecutor = cron4jNow.getCron4jScheduler().launchNow(nowTask, option);
        return createLaunchedProcess(taskExecutor);
    }

    // -----------------------------------------------------
    //                                      LaunchNow Option
    //                                      ----------------
    protected LaunchNowOption createLaunchNowOption(LaunchNowOpCall opLambda) {
        final LaunchNowOption op = new LaunchNowOption();
        opLambda.callback(op);
        return op;
    }

    protected LaunchedProcess createLaunchedProcess(TaskExecutor taskExecutor) {
        return new LaunchedProcess(this, () -> joinJobThread(taskExecutor), () -> findJobHistory(taskExecutor));
    }

    protected void joinJobThread(TaskExecutor taskExecutor) {
        try {
            taskExecutor.join();
        } catch (InterruptedException e) {
            String msg = "The current thread has been interrupted while join: taskExecutor=" + taskExecutor + ", job=" + this;
            throw new IllegalStateException(msg, e);
        }
    }

    protected OptionalThing<LaJobHistory> findJobHistory(TaskExecutor taskExecutor) {
        return Cron4jJobHistory.find(taskExecutor);
    }

    // -----------------------------------------------------
    //                                 OutlawParallel Launch
    //                                 ---------------------
    protected Cron4jTask prepareOutlawParallelLaunch() {
        if (!isOutlawParallelGranted()) {
            // outlaw parallel is dangerous function so strict by jflute (2021/08/23)
            // and simple exception message here for high-skill developer only
            throw new IllegalStateException("The job is not allowed to use outlaw parallel: " + toIdentityDisp());
        }
        if (!isNonCron()) {
            // #for_now jflute outlaw parallel logic is very difficult so launchNow() only (2021/08/23)
            throw new IllegalStateException("Cannot use outlaw parallel when scheduled cron: " + toIdentityDisp());
        }
        final Cron4jTask nowTask;
        synchronized (outlawParallelLock) {
            // #for_now jflute this removing is not best timing but safety timing was not found (2021/08/22)
            outlawParallelTaskList.removeIf(task -> {
                // avoid near-beginning task delete so refers task state directly by jflute (2021/08/24)
                // outlaw parallel task is one-time instance so once-ended task can be deleted
                return task.syncRunningOnceEnded();
            }); // purge old tasks
            // clone task instance for outlaw parallel to avoid concurrent control
            nowTask = cron4jTask.createOutlawParallelTask();
            outlawParallelTaskList.add(nowTask);
        }
        return nowTask;
    }

    // ===================================================================================
    //                                                                            Stop Now
    //                                                                            ========
    @Override
    public synchronized void stopNow() { // can be called if unscheduled, disappeared, so don't use mutable variable
        verifyCanStopState();
        final List<TaskExecutor> executorList = findNativeExecutorList();
        if (JobChangeLog.isEnabled()) {
            JobChangeLog.log("#job ...Stopping {} execution(s) now: {}", executorList.size(), toString());
        }
        if (!executorList.isEmpty()) {
            executorList.forEach(executor -> executor.stop());
        }
    }

    protected List<TaskExecutor> findNativeExecutorList() {
        final List<TaskExecutor> executorList = new ArrayList<>();
        final Cron4jScheduler cron4jScheduler = cron4jNow.getCron4jScheduler();
        executorList.addAll(cron4jScheduler.findExecutorList(cron4jTask)); // running only
        synchronized (outlawParallelLock) { // just in case
            outlawParallelTaskList.stream()
                    .flatMap(task -> cron4jScheduler.findExecutorList(task).stream()) // me too
                    .forEach(executor -> executorList.add(executor));
        }
        return executorList;
    }

    // ===================================================================================
    //                                                                          Reschedule
    //                                                                          ==========
    @Override
    public synchronized void reschedule(String cronExp, VaryingCronOpCall opLambda) {
        verifyCanRescheduleState();
        assertArgumentNotNull("cronExp", cronExp);
        assertArgumentNotNull("opLambda", opLambda);
        if (isNonCromExp(cronExp)) {
            throw new IllegalArgumentException("The cronExp for reschedule() should not be non-cron: " + toString());
        }
        if (unscheduled) {
            unscheduled = false; // can revive from unscheduled
        }

        // cronExp in task is switched here, and synchronized in task
        // while, outlaw parallel tasks are not target here because they are for only non-cron
        final String existingCronExp = cron4jTask.getVaryingCron().getCronExp();
        cron4jTask.switchCron(cronExp, createCronOption(opLambda));

        final Cron4jScheduler cron4jScheduler = cron4jNow.getCron4jScheduler();
        cron4jId.ifPresent(id -> {
            if (JobChangeLog.isEnabled()) {
                JobChangeLog.log("#job ...Rescheduling {} as cron from '{}' to '{}'", jobKey, existingCronExp, cronExp);
            }
            if (isNativeScheduledId(cron4jScheduler, id)) {
                cron4jScheduler.reschedule(id, cronExp);
            } else { // after descheduled
                cron4jId = scheduleNative(cronExp, cron4jScheduler);
            }
        }).orElse(() -> {
            if (JobChangeLog.isEnabled()) {
                JobChangeLog.log("#job ...Rescheduling {} as cron from non-cron to '{}'", jobKey, cronExp);
            }
            cron4jId = scheduleNative(cronExp, cron4jScheduler);
        });
    }

    protected boolean isNonCromExp(String cronExp) {
        return Cron4jCron.isNonCronExp(cronExp);
    }

    protected VaryingCronOption createCronOption(VaryingCronOpCall opLambda) {
        final VaryingCronOption option = new CronOption();
        opLambda.callback(option);
        return option;
    }

    protected boolean isNativeScheduledId(Cron4jScheduler cron4jScheduler, Cron4jId id) {
        return cron4jScheduler.getNativeScheduler().getTask(id.value()) != null;
    }

    protected OptionalThing<Cron4jId> scheduleNative(String cronExp, Cron4jScheduler cron4jScheduler) {
        final String generatedId = cron4jScheduler.schedule(cronExp, cron4jTask);
        return OptionalThing.of(Cron4jId.of(generatedId));
    }

    // ===================================================================================
    //                                                                          Unschedule
    //                                                                          ==========
    @Override
    public synchronized void unschedule() {
        verifyCanUnscheduleState();
        if (JobChangeLog.isEnabled()) {
            JobChangeLog.log("#job ...Unscheduling {}", toString());
        }
        unscheduled = true;
        cron4jId.ifPresent(id -> {
            cron4jNow.getCron4jScheduler().deschedule(id);
        });
    }

    @Override
    public boolean isUnscheduled() {
        return unscheduled;
    }

    // ===================================================================================
    //                                                                           Disappear
    //                                                                           =========
    @Override
    public synchronized void disappear() {
        verifyCanDisappearState();
        if (JobChangeLog.isEnabled()) {
            JobChangeLog.log("#job ...Disappearing {}", toString());
        }
        disappeared = true; // should be before clearing
        cron4jId.ifPresent(id -> {
            cron4jNow.getCron4jScheduler().deschedule(id);
        });
        cron4jNow.clearDisappearedJob(); // immediately clear, executing process is kept
    }

    @Override
    public boolean isDisappeared() {
        return disappeared;
    }

    // ===================================================================================
    //                                                                            Non-Cron
    //                                                                            ========
    @Override
    public synchronized void becomeNonCron() {
        verifyCanScheduleState();
        if (JobChangeLog.isEnabled()) {
            JobChangeLog.log("#job ...Becoming non-cron: {}", toString());
        }
        cron4jId.ifPresent(id -> {
            cron4jTask.becomeNonCrom(); // without outlaw parallel tasks because they are for only non-cron
            cron4jNow.getCron4jScheduler().deschedule(id);
            cron4jId = OptionalThing.empty();
        });
    }

    @Override
    public boolean isNonCron() {
        return !cron4jId.isPresent();
    }

    // ===================================================================================
    //                                                                        Next Trigger
    //                                                                        ============
    @Override
    public void registerNext(LaJobKey triggeredJobKey) { // uses triggered lock instead of synchronize
        verifyCanScheduleState();
        assertArgumentNotNull("triggeredJobKey", triggeredJobKey);
        // lazy check for initialization logic
        //if (!cron4jNow.findJobByKey(triggeredJobKey).isPresent()) {
        //    throw new IllegalArgumentException("Not found the job by the job key: " + triggeredJobKey);
        //}
        if (triggeredJobKey.equals(jobKey)) { // myself
            throw new IllegalArgumentException("Cannot register myself job as next trigger: " + toIdentityDisp());
        }
        synchronized (triggeredJobLock) { // just in case
            triggeredJobKeySet.add(triggeredJobKey);
        }
    }

    public void triggerNext() { // called in execution (at framework), so cannot synchronize with this
        // needs to be able to execute even if unscheduled
        // because job process that is already executed can be success
        // (and this method is for framework so no worry about user call)
        //verifyCanScheduleState();
        synchronized (triggeredJobLock) {
            final List<Cron4jJob> triggeredJobList = triggeredJobKeySet.stream().map(triggeredJobKey -> {
                return findTriggeredJob(triggeredJobKey);
            }).collect(Collectors.toList());
            showPreparingNextTrigger(triggeredJobList);
            for (Cron4jJob triggeredJob : triggeredJobList) { // expception if contains unscheduled
                triggeredJob.launchNow();
            }
        }
    }

    protected Cron4jJob findTriggeredJob(LaJobKey triggeredJobKey) {
        return cron4jNow.findJobByKey(triggeredJobKey).orElseTranslatingThrow(cause -> {
            String msg = "Not found the next job: " + triggeredJobKey + " triggered by " + toString();
            throw new JobTriggeredNotFoundException(msg, cause);
        });
    }

    protected void showPreparingNextTrigger(List<Cron4jJob> triggeredJobList) {
        if (triggeredJobList.isEmpty()) {
            return; // no needed if no trigger
        }
        final List<String> expList = triggeredJobList.stream().map(triggeredJob -> {
            return triggeredJob.toIdentityDisp();
        }).collect(Collectors.toList());
        final String exp = expList.size() == 1 ? expList.get(0) : expList.toString();
        logger.info("#job ...Preparing next job {} triggered by {}", exp, toIdentityDisp());
    }

    protected String buildTriggerNextJobExp(Cron4jJob triggeredJob) {
        final String keyExp = triggeredJob.getJobUnique().map(unique -> unique.value()).orElseGet(() -> {
            return triggeredJob.getJobKey().value();
        });
        return keyExp + "(" + triggeredJob.getJobType().getSimpleName() + ")";
    }

    // ===================================================================================
    //                                                                 Neighbor Concurrent
    //                                                                 ===================
    public synchronized void registerNeighborConcurrent(String groupName, NeighborConcurrentGroup neighborConcurrentGroup) {
        verifyCanScheduleState();
        if (neighborConcurrentGroupMap == null) {
            neighborConcurrentGroupMap = new ConcurrentHashMap<String, NeighborConcurrentGroup>(); // just in case
            neighborConcurrentGroupList = new CopyOnWriteArrayList<NeighborConcurrentGroup>(); // just in case
        }
        neighborConcurrentGroupMap.put(groupName, neighborConcurrentGroup);
        neighborConcurrentGroupList.add(neighborConcurrentGroup);
    }

    // ===================================================================================
    //                                                                             Display
    //                                                                             =======
    public String toIdentityDisp() {
        final Class<? extends LaJob> jobType = cron4jTask.getJobType();
        return jobType.getSimpleName() + ":{" + jobUnique.map(uq -> uq + "(" + jobKey + ")").orElseGet(() -> jobKey.value()) + "}";
    }

    // ===================================================================================
    //                                                                        Assist Logic
    //                                                                        ============
    // -----------------------------------------------------
    //                                         Running State
    //                                         -------------
    protected OptionalThing<LocalDateTime> extractRunningBeginTime(Cron4jTask task) {
        return task.syncRunningCall(runningState -> {
            return runningState.getBeginTime().get(); // locked so can get() safely
        });
    }

    // -----------------------------------------------------
    //                                                Verify
    //                                                ------
    protected synchronized void verifyCanScheduleState() {
        if (disappeared) {
            throw new JobAlreadyDisappearedException("Already disappeared the job: " + toString());
        }
        if (unscheduled) {
            throw new JobAlreadyUnscheduleException("Already unscheduled the job: " + toString());
        }
    }

    protected synchronized void verifyCanRescheduleState() {
        if (disappeared) {
            throw new JobAlreadyDisappearedException("Already disappeared the job: " + toString());
        }
    }

    protected synchronized void verifyCanUnscheduleState() {
        if (disappeared) {
            throw new JobAlreadyDisappearedException("Already disappeared the job: " + toString());
        }
        if (unscheduled) {
            throw new JobAlreadyUnscheduleException("Already unscheduled the job: " + toString());
        }
    }

    protected synchronized void verifyCanDisappearState() {
        if (disappeared) {
            throw new JobAlreadyDisappearedException("Already disappeared the job: " + toString());
        }
    }

    protected synchronized void verifyCanStopState() {
        // everyday you can stop
    }

    // ===================================================================================
    //                                                                        Small Helper
    //                                                                        ============
    protected void assertArgumentNotNull(String variableName, Object value) {
        if (variableName == null) {
            throw new IllegalArgumentException("The variableName should not be null.");
        }
        if (value == null) {
            throw new IllegalArgumentException("The argument '" + variableName + "' should not be null.");
        }
    }

    // ===================================================================================
    //                                                                      Basic Override
    //                                                                      ==============
    @Override
    public String toString() {
        final String titlePrefix = jobNote.map(title -> title + ", ").orElse("");
        final String keyExp = jobUnique.map(uq -> uq + "(" + jobKey + ")").orElseGet(() -> jobKey.value());
        final String idExp = cron4jId.map(id -> id.value()).orElse("non-cron");
        final String hash = Integer.toHexString(hashCode());
        return DfTypeUtil.toClassTitle(this) + ":{" + titlePrefix + keyExp + ", " + idExp + ", " + cron4jTask + "}@" + hash;
    }

    // ===================================================================================
    //                                                                            Accessor
    //                                                                            ========
    // -----------------------------------------------------
    //                                            Basic Info
    //                                            ----------
    @Override
    public LaJobKey getJobKey() {
        return jobKey;
    }

    @Override
    public OptionalThing<LaJobNote> getJobNote() {
        return jobNote;
    }

    @Override
    public OptionalThing<LaJobUnique> getJobUnique() {
        return jobUnique;
    }

    @Override
    public synchronized OptionalThing<String> getCronExp() { // synchronized for varying
        final String cronExp = !isNonCron() ? cron4jTask.getVaryingCron().getCronExp() : null;
        return OptionalThing.ofNullable(cronExp, () -> {
            throw new IllegalStateException("Not found cron expression because of non-cron job: " + toString());
        });
    }

    @Override
    public Class<? extends LaJob> getJobType() {
        return cron4jTask.getJobType();
    }

    // -----------------------------------------------------
    //                                          Control Info
    //                                          ------------
    @Override
    public OptionalThing<CronParamsSupplier> getParamsSupplier() {
        return cron4jTask.getVaryingCron().getCronOption().getParamsSupplier();
    }

    @Override
    public JobNoticeLogLevel getNoticeLogLevel() {
        return cron4jTask.getVaryingCron().getCronOption().getNoticeLogLevel();
    }

    @Override
    public JobConcurrentExec getConcurrentExec() {
        return cron4jTask.getConcurrentExec();
    }

    @Override
    public boolean isOutlawParallelGranted() {
        return cron4jTask.getVaryingCron().getCronOption().isOutlawParallelGranted();
    }

    // -----------------------------------------------------
    //                                      Framework Object
    //                                      ----------------
    public OptionalThing<Cron4jId> getCron4jId() {
        return cron4jId;
    }

    public Cron4jTask getCron4jTask() { // for framework
        return cron4jTask;
    }

    public Cron4jNow getCron4jNow() { // for e.g. test
        return cron4jNow;
    }

    // -----------------------------------------------------
    //                                          Next Trigger
    //                                          ------------
    @Override
    public Set<LaJobKey> getTriggeredJobKeySet() {
        synchronized (triggeredJobLock) { // just in case
            return Collections.unmodifiableSet(triggeredJobKeySet);
        }
    }

    // -----------------------------------------------------
    //                                       Outlaw Parallel
    //                                       ---------------
    public List<Cron4jTask> getOutlawParallelTaskList() { // for framework
        synchronized (outlawParallelLock) { // just in case
            return Collections.unmodifiableList(outlawParallelTaskList);
        }
    }

    // -----------------------------------------------------
    //                                   Neighbor Concurrent
    //                                   -------------------
    @Override
    public synchronized List<NeighborConcurrentGroup> getNeighborConcurrentGroupList() {
        if (neighborConcurrentGroupList == null) {
            return Collections.emptyList();
        }
        // unmodifiable and snapshot list for concurrent process,
        // wrap wrap just in case, for machine-gun synchronization (that needs ordered groups)
        return Collections.unmodifiableList(new CopyOnWriteArrayList<NeighborConcurrentGroup>(neighborConcurrentGroupList));
    }

    @Override
    public synchronized Map<String, NeighborConcurrentGroup> getNeighborConcurrentGroupMap() {
        return neighborConcurrentGroupMap != null ? Collections.unmodifiableMap(neighborConcurrentGroupMap) : Collections.emptyMap();
    }
}
