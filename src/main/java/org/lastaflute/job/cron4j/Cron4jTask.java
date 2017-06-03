/*
 * Copyright 2015-2017 the original author or authors.
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

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.dbflute.bhv.proposal.callback.TraceableSqlAdditionalInfoProvider;
import org.dbflute.helper.message.ExceptionMessageBuilder;
import org.dbflute.hook.AccessContext;
import org.dbflute.hook.CallbackContext;
import org.dbflute.hook.SqlFireHook;
import org.dbflute.hook.SqlResultHandler;
import org.dbflute.hook.SqlStringFilter;
import org.dbflute.optional.OptionalThing;
import org.dbflute.util.DfTypeUtil;
import org.lastaflute.core.magic.ThreadCacheContext;
import org.lastaflute.db.dbflute.accesscontext.AccessContextArranger;
import org.lastaflute.db.dbflute.accesscontext.AccessContextResource;
import org.lastaflute.db.dbflute.accesscontext.PreparedAccessContext;
import org.lastaflute.db.dbflute.callbackcontext.traceablesql.RomanticTraceableSqlFireHook;
import org.lastaflute.db.dbflute.callbackcontext.traceablesql.RomanticTraceableSqlResultHandler;
import org.lastaflute.db.dbflute.callbackcontext.traceablesql.RomanticTraceableSqlStringFilter;
import org.lastaflute.job.LaJob;
import org.lastaflute.job.LaJobRunner;
import org.lastaflute.job.exception.JobConcurrentlyExecutingException;
import org.lastaflute.job.exception.JobLaunchParameterConflictException;
import org.lastaflute.job.key.LaJobKey;
import org.lastaflute.job.key.LaJobNote;
import org.lastaflute.job.key.LaJobUnique;
import org.lastaflute.job.log.JobErrorLog;
import org.lastaflute.job.log.JobErrorResource;
import org.lastaflute.job.log.JobErrorStackTracer;
import org.lastaflute.job.log.JobHistoryHook;
import org.lastaflute.job.log.JobHistoryResource;
import org.lastaflute.job.log.JobNoticeLog;
import org.lastaflute.job.log.JobNoticeLogLevel;
import org.lastaflute.job.subsidiary.ConcurrentJobStopper;
import org.lastaflute.job.subsidiary.CrossVMHook;
import org.lastaflute.job.subsidiary.CrossVMState;
import org.lastaflute.job.subsidiary.EndTitleRoll;
import org.lastaflute.job.subsidiary.ExecResultType;
import org.lastaflute.job.subsidiary.JobConcurrentExec;
import org.lastaflute.job.subsidiary.JobIdentityAttr;
import org.lastaflute.job.subsidiary.LaunchNowOption;
import org.lastaflute.job.subsidiary.NeighborConcurrentGroup;
import org.lastaflute.job.subsidiary.NeighborConcurrentJobStopper;
import org.lastaflute.job.subsidiary.ReadableJobAttr;
import org.lastaflute.job.subsidiary.RunnerResult;
import org.lastaflute.job.subsidiary.TaskRunningState;
import org.lastaflute.job.subsidiary.VaryingCron;
import org.lastaflute.job.subsidiary.VaryingCronOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.sauronsoftware.cron4j.RomanticCron4jTaskExecutionContext;
import it.sauronsoftware.cron4j.Task;
import it.sauronsoftware.cron4j.TaskExecutionContext;
import it.sauronsoftware.cron4j.TaskExecutor;

/**
 * @author jflute
 * @since 0.2.0 (2016/01/11 Monday)
 */
public class Cron4jTask extends Task { // unique per job in lasta job world

    // ===================================================================================
    //                                                                          Definition
    //                                                                          ==========
    private static final Logger logger = LoggerFactory.getLogger(Cron4jTask.class);
    protected static final String LF = "\n";

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    protected VaryingCron varyingCron; // not null, can be switched
    protected final Class<? extends LaJob> jobType; // not null
    protected final JobConcurrentExec concurrentExec; // not null
    protected final Supplier<String> threadNaming; // not null
    protected final LaJobRunner jobRunner; // not null, singleton
    protected final Cron4jNow cron4jNow; // not null
    protected final Supplier<LocalDateTime> currentTime; // not null
    protected final boolean frameworkDebug;
    protected final TaskRunningState runningState; // not null
    protected final Object preparingLock = new Object(); // not null
    protected final Object runningLock = new Object(); // not null
    protected final Object varyingLock = new Object(); // not null

    // ===================================================================================
    //                                                                         Constructor
    //                                                                         ===========
    public Cron4jTask(VaryingCron varyingCron, Class<? extends LaJob> jobType, JobConcurrentExec concurrentExec,
            Supplier<String> threadNaming, LaJobRunner jobRunner, Cron4jNow cron4jNow, Supplier<LocalDateTime> currentTime,
            boolean frameworkDebug) {
        this.varyingCron = varyingCron;
        this.jobType = jobType;
        this.concurrentExec = concurrentExec;
        this.threadNaming = threadNaming;
        this.jobRunner = jobRunner;
        this.cron4jNow = cron4jNow;
        this.currentTime = currentTime;
        this.frameworkDebug = frameworkDebug;
        this.runningState = new TaskRunningState(currentTime);
    }

    // ===================================================================================
    //                                                                  Execute - Top Flow
    //                                                                  ==================
    @Override
    public void execute(TaskExecutionContext context) { // e.g. error handling
        debugFw("...Beginning the cron4j task (before run): {}", jobType);
        final TaskExecutionContext nativeContext;
        final OptionalThing<LaunchNowOption> nowOption;
        if (context instanceof RomanticCron4jTaskExecutionContext) {
            final RomanticCron4jTaskExecutionContext romantic = (RomanticCron4jTaskExecutionContext) context;
            nativeContext = romantic.getNativeContext();
            nowOption = romantic.getLaunchNowOption();
        } else {
            nativeContext = context;
            nowOption = OptionalThing.empty();
        }
        try {
            final LocalDateTime activationTime = currentTime.get();
            final Cron4jJob job = findJob();
            final Thread jobThread = Thread.currentThread();
            RunnerResult runnerResult = null;
            Throwable controllerCause = null;
            try {
                debugFw("...Calling doExecute() of task (before run)");
                runnerResult = doExecute(job, nativeContext, nowOption); // not null
                if (canTriggerNext(job, runnerResult)) {
                    debugFw("...Calling triggerNext() of job in task (after run)");
                    job.triggerNext(); // should be after current job ending
                }
            } catch (JobConcurrentlyExecutingException e) { // these catch statements are related to deriveRunnerExecResultType()
                debugFw("...Calling catch clause of job concurrently executing exception: {}", e.getClass().getSimpleName());
                final String msg = "Cannot execute the job task by concurrent execution: " + varyingCron + ", " + jobType.getSimpleName();
                error(OptionalThing.of(job), msg, e);
                controllerCause = e;
            } catch (Throwable cause) { // from framework part (exception in appilcation job are already handled)
                debugFw("...Calling catch clause of job controller's exception: {}", cause.getClass().getSimpleName());
                final String msg = "Failed to execute the job task: " + varyingCron + ", " + jobType.getSimpleName();
                error(OptionalThing.of(job), msg, cause);
                controllerCause = cause;
            }
            final OptionalThing<RunnerResult> optRunnerResult = optRunnerResult(runnerResult); // empty when error 
            final OptionalThing<LocalDateTime> endTime = deriveEndTime(optRunnerResult);
            debugFw("...Calling recordJobHistory() of task (after run): {}, {}", optRunnerResult, endTime);
            recordJobHistory(nativeContext, job, jobThread, activationTime, optRunnerResult, endTime, optControllerCause(controllerCause));
            debugFw("...Ending the cron4j task (after run): {}, {}", optRunnerResult, endTime);
        } catch (Throwable coreCause) { // controller dead
            final String msg = "Failed to control the job task: " + varyingCron + ", " + jobType.getSimpleName();
            error(OptionalThing.empty(), msg, coreCause);
        }
    }

    protected Cron4jJob findJob() {
        return cron4jNow.findJobByTask(this).get();
    }

    protected OptionalThing<LocalDateTime> deriveEndTime(OptionalThing<RunnerResult> runnerResult) {
        return runnerResult.filter(res -> res.getBeginTime().isPresent()).map(res -> currentTime.get());
    }

    protected OptionalThing<RunnerResult> optRunnerResult(RunnerResult runnerResult) {
        return OptionalThing.ofNullable(runnerResult, () -> {
            throw new IllegalStateException("Not found the runner result.");
        });
    }

    protected OptionalThing<Throwable> optControllerCause(Throwable controllerCause) {
        return OptionalThing.ofNullable(controllerCause, () -> {
            throw new IllegalStateException("Not found the controller cause.");
        });
    }

    // ===================================================================================
    //                                                        Execute - Concurrent Control
    //                                                        ============================
    protected RunnerResult doExecute(Cron4jJob job, TaskExecutionContext context, OptionalThing<LaunchNowOption> nowOption) { // e.g. concurrent control, cross vm
        // ...may be hard to read, synchronized hell
        final String cronExp;
        final VaryingCronOption cronOption;
        debugFw("...Locking varying lock (before run): {}", varyingLock);
        synchronized (varyingLock) {
            cronExp = varyingCron.getCronExp();
            cronOption = varyingCron.getCronOption();
        }
        final List<NeighborConcurrentGroup> neighborConcurrentGroupList = job.getNeighborConcurrentGroupList();
        debugFw("...Locking preparing lock (before run): {}", preparingLock);
        synchronized (preparingLock) { // waiting for previous preparing end
            final OptionalThing<RunnerResult> concurrentResult = stopConcurrentJobIfNeeds(job);
            if (concurrentResult.isPresent()) { // e.g. quit, error
                return concurrentResult.get();
            }
            // no duplicate or duplicate as waiting, here
            final OptionalThing<RunnerResult> neighborConcurrentResult =
                    synchronizedNeighborPreparing(neighborConcurrentGroupList.iterator(), () -> {
                        final OptionalThing<RunnerResult> result = stopNeighborConcurrentJobIfNeeds(job, neighborConcurrentGroupList);
                        if (!result.isPresent()) { // no duplicate neighbor or duplicate as waiting
                            sleepForLockableLife(); // not to get running lock before previous thread in crevasse point
                            debugFw("...Locking running lock (before run): {}", runningLock);
                            synchronized (runningLock) { // waiting for previous running end
                                debugFw("...Locking running state in preparing and running lock (before run): {}", runningState);
                                synchronizedNeighborRunning(neighborConcurrentGroupList.iterator(), () -> { // also neighbor's running
                                    synchronized (runningState) { // to protect running state, begin() and end()
                                        // may be override by crevasse headache, but recover it later
                                        runningState.begin(); // needs to get in preparing lock, to suppress duplicate begin()
                                    }
                                    return null; // unused
                                });
                            }
                        }
                        return result;
                    });
            if (neighborConcurrentResult.isPresent()) { // e.g. quit, error
                return neighborConcurrentResult.get();
            }
        }
        // *here is first crevasse point
        // (really want to get running lock before returning preparing lock)
        debugFw("...Locking running lock (before run): {}", runningLock);
        synchronized (runningLock) { // to make next thread wait for me (or waiting by crevasse headache)
            try { // *here is second crevasse point
                return synchronizedNeighborRunning(neighborConcurrentGroupList.iterator(), () -> {
                    if (!runningState.getBeginTime().isPresent()) { // almost no way, but may be crevasse headache
                        runningState.begin(); // just in case
                    }
                    final OptionalThing<CrossVMState> crossVMState = crossVMBeginning(job);
                    if (crossVMState.isPresent() && crossVMState.get().isQuit()) {
                        return RunnerResult.asQuitByConcurrent(); // quit by cross VM handling
                    }
                    final RunnerResult runnerResult;
                    final LocalDateTime endTime;
                    try {
                        debugFw("...Calling actuallyExecute() of task (before run): {}", job);
                        runnerResult = actuallyExecute(job, cronExp, cronOption, context, nowOption);
                    } finally {
                        debugFw("...Calling finally clause of job execution (after run)");
                        endTime = currentTime.get();
                        crossVMEnding(job, crossVMState, endTime);
                    }
                    runnerResult.acceptEndTime(endTime); // lazy load now
                    return runnerResult;
                });
            } finally {
                debugFw("...Locking running state in running lock (after run): {}", runningState);
                synchronized (runningState) { // running state is only my job so outside neighbor synchronization
                    if (runningState.getBeginTime().isPresent()) {
                        runningState.end(); // for controller dead
                    }
                }
            }
        }
    }

    protected void sleepForLockableLife() {
        try {
            Thread.sleep(200);
        } catch (InterruptedException ignored) {}
    }

    // -----------------------------------------------------
    //                                   (Myself) Concurrent
    //                                   -------------------
    protected OptionalThing<RunnerResult> stopConcurrentJobIfNeeds(Cron4jJob job) { // in preparing lock
        synchronized (runningState) {
            final OptionalThing<RunnerResult> concurrentResult = createConcurrentJobStopper().stopIfNeeds(job, () -> {
                return runningState.getBeginTime().get().toString(); // locked so can get safely
            });
            if (concurrentResult.isPresent()) {
                return concurrentResult;
            }
            // will wait for previous job by synchronization later
        }
        return OptionalThing.empty();
    }

    protected ConcurrentJobStopper createConcurrentJobStopper() {
        // direct call to avoid instance worry about runningState handling so jobState is unused
        return new ConcurrentJobStopper(unused -> isRunningNow());

    }

    // -----------------------------------------------------
    //                                   Neighbor Concurrent
    //                                   -------------------
    protected OptionalThing<RunnerResult> synchronizedNeighborPreparing(Iterator<NeighborConcurrentGroup> groupIte,
            Supplier<OptionalThing<RunnerResult>> runner) { // in preparing lock
        if (groupIte.hasNext()) {
            final NeighborConcurrentGroup group = groupIte.next();
            synchronized (group.getGroupPreparingLock()) {
                return synchronizedNeighborPreparing(groupIte, runner);
            }
        } else {
            return runner.get();
        }
    }

    protected RunnerResult synchronizedNeighborRunning(Iterator<NeighborConcurrentGroup> groupIte, Supplier<RunnerResult> runner) { // in running lock
        if (groupIte.hasNext()) {
            final NeighborConcurrentGroup group = groupIte.next();
            synchronized (group.getGroupRunningLock()) {
                return synchronizedNeighborRunning(groupIte, runner);
            }
        } else {
            return runner.get();
        }
    }

    protected OptionalThing<RunnerResult> stopNeighborConcurrentJobIfNeeds(Cron4jJob job,
            List<NeighborConcurrentGroup> neighborConcurrentGroupList) { // in neighbor preparing lock
        return createNeighborConcurrentJobStopper(neighborConcurrentGroupList).stopIfNeeds(job, jobState -> {
            return jobState.mapExecutingNow(execState -> execState.getBeginTime().toString()).orElseGet(() -> {
                return "*the job have just ended now"; // may be ended while message building
            });
        });
        // will wait for previous job by synchronization later
    }

    protected NeighborConcurrentJobStopper createNeighborConcurrentJobStopper(List<NeighborConcurrentGroup> neighborConcurrentGroupList) {
        return new NeighborConcurrentJobStopper(jobState -> jobState.isExecutingNow() // jobExecutingDeterminer
                , jobKey -> cron4jNow.findJobByKey(jobKey) // jobFinder
                , neighborConcurrentGroupList);
    }

    // ===================================================================================
    //                                                          Execute - Really Executing
    //                                                          ==========================
    // in execution lock, cannot use varingCron here
    protected RunnerResult actuallyExecute(JobIdentityAttr identityProvider, String cronExp, VaryingCronOption cronOption,
            TaskExecutionContext context, OptionalThing<LaunchNowOption> nowOption) { // in synchronized world
        adjustThreadNameIfNeeds(cronOption);
        return runJob(identityProvider, cronExp, cronOption, context, nowOption);
    }

    protected void adjustThreadNameIfNeeds(VaryingCronOption cronOption) { // because of too long name of cron4j
        final String supplied = threadNaming.get();
        final Thread currentThread = Thread.currentThread();
        if (currentThread.getName().equals(supplied)) { // already adjusted
            return;
        }
        currentThread.setName(supplied);
    }

    protected RunnerResult runJob(JobIdentityAttr identityProvider, String cronExp, VaryingCronOption cronOption,
            TaskExecutionContext cron4jContext, OptionalThing<LaunchNowOption> nowOption) {
        final LocalDateTime beginTime = runningState.getBeginTime().get(); // already begun here
        debugFw("...Calling run() of job runner in task (before run): beginTime={}", beginTime);
        return jobRunner.run(jobType, () -> {
            return createCron4jRuntime(identityProvider, cronExp, cronOption, beginTime, cron4jContext, nowOption);
        }).acceptEndTime(currentTime.get());
    }

    protected Cron4jRuntime createCron4jRuntime(JobIdentityAttr identityProvider, String cronExp, VaryingCronOption cronOption,
            LocalDateTime beginTime, TaskExecutionContext cron4jContext, OptionalThing<LaunchNowOption> nowOption) {
        final LaJobKey jobKey = identityProvider.getJobKey();
        final OptionalThing<LaJobNote> jobNote = identityProvider.getJobNote();
        final OptionalThing<LaJobUnique> jobUnique = identityProvider.getJobUnique();
        final Map<String, Object> parameterMap = prepareParameterMap(cronOption, nowOption);
        final JobNoticeLogLevel noticeLogLevel = cronOption.getNoticeLogLevel();
        return new Cron4jRuntime(jobKey, jobNote, jobUnique, cronExp, jobType, parameterMap, noticeLogLevel // basic
                , beginTime, isFrameworkDebug() // state
                , cron4jContext); // cron4j
    }

    protected Map<String, Object> prepareParameterMap(VaryingCronOption cronOption, OptionalThing<LaunchNowOption> nowOption) {
        final Map<String, Object> byCronMap = extractParameterMap(cronOption);
        return nowOption.isPresent() ? mergeParameterMap(byCronMap, nowOption.get()) : byCronMap;
    }

    protected Map<String, Object> extractParameterMap(VaryingCronOption cronOption) {
        return cronOption.getParamsSupplier().map(supplier -> supplier.supply()).orElse(Collections.emptyMap());
    }

    protected Map<String, Object> mergeParameterMap(Map<String, Object> byCronMap, LaunchNowOption nowOption) {
        final Map<String, Object> byLaunchMap = nowOption.getParameterMap();
        if (!nowOption.isPriorParams()) {
            byCronMap.keySet().forEach(key -> {
                if (byLaunchMap.containsKey(key)) {
                    throwJobLaunchParameterConflictException(byCronMap, byLaunchMap);
                }
            });
        }
        final Map<String, Object> parameterMap = new LinkedHashMap<String, Object>();
        parameterMap.putAll(byCronMap);
        parameterMap.putAll(byLaunchMap); // may override same-key value
        return Collections.unmodifiableMap(parameterMap);
    }

    protected void throwJobLaunchParameterConflictException(Map<String, Object> byCronMap, Map<String, Object> byLaunchMap) {
        final ExceptionMessageBuilder br = new ExceptionMessageBuilder();
        br.addNotice("Conflicted the key of launch parameter.");
        br.addItem("Advice");
        br.addElement("You cannot use the launch parameter key");
        br.addElement("that is same as cron paramter key.");
        br.addElement("Or you can override by launch parameter option.");
        br.addItem("Cron Parameter");
        br.addElement(byCronMap.keySet());
        br.addItem("Launch Parameter");
        br.addElement(byLaunchMap.keySet());
        final String msg = br.buildExceptionMessage();
        throw new JobLaunchParameterConflictException(msg);
    }

    // ===================================================================================
    //                                                                 Execute - Supporter
    //                                                                 ===================
    // -----------------------------------------------------
    //                                          Next Trigger
    //                                          ------------
    protected boolean canTriggerNext(Cron4jJob job, RunnerResult runnerResult) {
        return !runnerResult.getCause().isPresent() && !runnerResult.isNextTriggerSuppressed();
    }

    // -----------------------------------------------------
    //                                               CrossVM
    //                                               -------
    protected OptionalThing<CrossVMState> crossVMBeginning(Cron4jJob job) {
        return jobRunner.getCrossVMHook().map(hook -> {
            final Method hookMethod = findHookMethod(hook, "hookBeginning");
            arrangeHookThreadCacheContext();
            jobRunner.getAccessContextArranger().ifPresent(arranger -> { // for DB control
                arrangeHookPreparedAccessContext(arranger, hook, hookMethod, job);
            });
            arrangeHookCallbackContext(hook, hookMethod, job);
            try {
                showCrossVMBeginning(job, hook);
                return hook.hookBeginning(job, runningState.getBeginTime().get()); // already begun here
            } finally {
                clearHookCallbackContext();
                clearHookPreparedAccessContext();
                clearHookThreadCacheContext();
            }
        });
    }

    protected void showCrossVMBeginning(Cron4jJob job, CrossVMHook hook) {
        if (!hook.suppressesNoticeLog()) {
            JobNoticeLog.log(getCrossVMHookNoticeLogLovel(job), () -> {
                return "#flow #job ...hookBeginning crossVM for the job: " + job.toIdentityDisp();
            });
        }
    }

    protected void crossVMEnding(Cron4jJob job, OptionalThing<CrossVMState> crossVMState, LocalDateTime endTime) {
        if (!crossVMState.isPresent()) {
            return;
        }
        jobRunner.getCrossVMHook().alwaysPresent(hook -> {
            final Method hookMethod = findHookMethod(hook, "hookEnding");
            arrangeHookThreadCacheContext();
            jobRunner.getAccessContextArranger().ifPresent(arranger -> { // for DB control
                arrangeHookPreparedAccessContext(arranger, hook, hookMethod, job);
            });
            arrangeHookCallbackContext(hook, hookMethod, job);
            try {
                showCrossVMEnding(job, hook);
                hook.hookEnding(job, crossVMState.get(), endTime);
            } finally {
                clearHookCallbackContext();
                clearHookPreparedAccessContext();
                clearHookThreadCacheContext();
            }
        });
    }

    protected void showCrossVMEnding(Cron4jJob job, CrossVMHook hook) {
        if (!hook.suppressesNoticeLog()) {
            JobNoticeLog.log(getCrossVMHookNoticeLogLovel(job), () -> {
                return "#flow #job ...hookEnding crossVM for the job: " + job.toIdentityDisp();
            });
        }
    }

    protected JobNoticeLogLevel getCrossVMHookNoticeLogLovel(Cron4jJob job) {
        return job.getNoticeLogLevel(); // matches with job's one as default
    }

    // -----------------------------------------------------
    //                                           Job History
    //                                           -----------
    protected void recordJobHistory(TaskExecutionContext context, Cron4jJob job, Thread jobThread, LocalDateTime activationTime,
            OptionalThing<RunnerResult> runnerResult, OptionalThing<LocalDateTime> endTime, OptionalThing<Throwable> controllerCause) {
        final TaskExecutor taskExecutor = context.getTaskExecutor();
        final Cron4jJobHistory jobHistory = prepareJobHistory(job, activationTime, runnerResult, endTime, controllerCause);
        final int historyLimit = getHistoryLimit();
        jobRunner.getHistoryHook().ifPresent(hook -> {
            final Method hookMethod = findHookMethod(hook, "hookRecord");
            arrangeHookThreadCacheContext();
            jobRunner.getAccessContextArranger().ifPresent(arranger -> { // for DB control
                arrangeHookPreparedAccessContext(arranger, hook, hookMethod, job);
            });
            arrangeHookCallbackContext(hook, hookMethod, job);
            try {
                showJobHistoryHookRecording(job, hook);
                hook.hookRecord(jobHistory, new JobHistoryResource(historyLimit));
            } finally {
                clearHookCallbackContext();
                clearHookPreparedAccessContext();
                clearHookThreadCacheContext();
            }
        });
        Cron4jJobHistory.record(taskExecutor, jobHistory, historyLimit);
    }

    protected Cron4jJobHistory prepareJobHistory(Cron4jJob job, LocalDateTime activationTime, OptionalThing<RunnerResult> runnerResult,
            OptionalThing<LocalDateTime> endTime, OptionalThing<Throwable> controllerCause) {
        final OptionalThing<LocalDateTime> beginTime = runnerResult.flatMap(res -> res.getBeginTime());
        final Cron4jJobHistory jobHistory;
        if (!controllerCause.isPresent()) { // mainly here, and runnerResult is not null here
            jobHistory = createJobHistory(job, activationTime, beginTime, endTime, () -> {
                return deriveRunnerExecResultType(runnerResult);
            }, runnerResult.flatMap(res -> res.getEndTitleRoll()), runnerResult.flatMap(res -> res.getCause()));
        } else if (controllerCause.get() instanceof JobConcurrentlyExecutingException) {
            jobHistory = createJobHistory(job, activationTime, beginTime, endTime, () -> ExecResultType.ERROR_BY_CONCURRENT,
                    OptionalThing.empty(), controllerCause);
        } else { // may be framework exception
            jobHistory = createJobHistory(job, activationTime, beginTime, endTime, () -> ExecResultType.CAUSED_BY_FRAMEWORK,
                    OptionalThing.empty(), controllerCause);
        }
        return jobHistory;
    }

    protected ExecResultType deriveRunnerExecResultType(OptionalThing<RunnerResult> runnerResult) {
        return runnerResult.map(res -> { // basically exists
            if (res.getCause().isPresent()) {
                return ExecResultType.CAUSED_BY_APPLICATION;
            } else if (res.isQuitByConcurrent()) {
                return ExecResultType.QUIT_BY_CONCURRENT;
            } else {
                return ExecResultType.SUCCESS;
            }
        }).orElseGet(() -> { // no way, either runnerResult or controllerCause always exists, but just in case
            return ExecResultType.SUCCESS;
        });
    }

    protected Cron4jJobHistory createJobHistory(Cron4jJob job, LocalDateTime activationTime, OptionalThing<LocalDateTime> beginTime,
            OptionalThing<LocalDateTime> endTime, Supplier<ExecResultType> execResultTypeProvider, OptionalThing<EndTitleRoll> endTitleRoll,
            OptionalThing<Throwable> cause) {
        final LaJobKey jobKey = job.getJobKey();
        final OptionalThing<LaJobNote> jobNote = job.getJobNote();
        final OptionalThing<LaJobUnique> jobUnique = job.getJobUnique();
        final OptionalThing<String> cronExp = job.getCronExp();
        final String jobTypeFqcn = job.getJobType().getName();
        final ExecResultType execResultType = execResultTypeProvider.get();
        return new Cron4jJobHistory(jobKey, jobNote, jobUnique // identity
                , cronExp, jobTypeFqcn // cron
                , activationTime, beginTime, endTime // execution time
                , execResultType // execution result
                , endTitleRoll, cause);
    }

    protected int getHistoryLimit() {
        return 300;
    }

    protected void showJobHistoryHookRecording(Cron4jJob job, JobHistoryHook hook) {
        if (!hook.suppressesNoticeLog()) {
            JobNoticeLog.log(getJobHistoryHookNoticeLogLovel(job), () -> {
                return "#flow #job ...hookRecording job history for the job: " + job.toIdentityDisp();
            });
        }
    }

    protected JobNoticeLogLevel getJobHistoryHookNoticeLogLovel(Cron4jJob job) {
        return job.getNoticeLogLevel(); // matches with job's one as default
    }

    // -----------------------------------------------------
    //                                             Error Log
    //                                             ---------
    protected void error(OptionalThing<ReadableJobAttr> jobAttr, String msg, Throwable cause) {
        final String bigMsg = (msg + LF + new JobErrorStackTracer().buildExceptionStackTrace(cause)).trim();
        jobRunner.getErrorLogHook().ifPresent(hook -> {
            hook.hookError(new JobErrorResource(jobAttr, OptionalThing.empty(), bigMsg, cause));
        });
        JobErrorLog.log(bigMsg);
    }

    // ===================================================================================
    //                                                                        Hook Context
    //                                                                        ============
    // -----------------------------------------------------
    //                                           ThreadCache
    //                                           -----------
    protected void arrangeHookThreadCacheContext() {
        ThreadCacheContext.initialize();
    }

    protected void clearHookThreadCacheContext() {
        ThreadCacheContext.clear();
    }

    // -----------------------------------------------------
    //                                         AccessContext
    //                                         -------------
    protected void arrangeHookPreparedAccessContext(AccessContextArranger arranger, Object hook, Method hookMethod, Cron4jJob job) {
        final String moduleName = DfTypeUtil.toClassTitle(hook.getClass());
        final AccessContext context = arranger.arrangePreparedAccessContext(new AccessContextResource(moduleName, hookMethod));
        if (context == null) {
            String msg = "Cannot return null from access context arranger: " + arranger + " job=" + job.toIdentityDisp();
            throw new IllegalStateException(msg);
        }
        PreparedAccessContext.setAccessContextOnThread(context);
    }

    protected void clearHookPreparedAccessContext() {
        PreparedAccessContext.clearAccessContextOnThread();
    }

    // -----------------------------------------------------
    //                                       CallbackContext
    //                                       ---------------
    protected void arrangeHookCallbackContext(Object hook, Method hookMethod, Cron4jJob job) {
        CallbackContext.setSqlFireHookOnThread(createHookSqlFireHook());
        CallbackContext.setSqlStringFilterOnThread(createHookSqlStringFilter(hook, hookMethod, job));
        CallbackContext.setSqlResultHandlerOnThread(createHookSqlResultHandler());
    }

    protected SqlFireHook createHookSqlFireHook() {
        return newHookRomanticTraceableSqlFireHook();
    }

    protected RomanticTraceableSqlFireHook newHookRomanticTraceableSqlFireHook() {
        return new RomanticTraceableSqlFireHook();
    }

    protected SqlStringFilter createHookSqlStringFilter(Object hook, Method hookMethod, Cron4jJob job) {
        return newHookRomanticTraceableSqlStringFilter(hookMethod, () -> buildHookSqlMarkingAdditionalInfo(hook, job));
    }

    protected String buildHookSqlMarkingAdditionalInfo(Object hook, Cron4jJob job) {
        return "(" + hook.getClass().getSimpleName() + ", " + job.toIdentityDisp() + ")"; // concrete class + job identity
    }

    protected RomanticTraceableSqlStringFilter newHookRomanticTraceableSqlStringFilter(Method actionMethod,
            TraceableSqlAdditionalInfoProvider additionalInfoProvider) {
        return new RomanticTraceableSqlStringFilter(actionMethod, additionalInfoProvider);
    }

    protected SqlResultHandler createHookSqlResultHandler() {
        return newHookRomanticTraceableSqlResultHandler();
    }

    protected RomanticTraceableSqlResultHandler newHookRomanticTraceableSqlResultHandler() {
        return new RomanticTraceableSqlResultHandler();
    }

    protected void clearHookCallbackContext() {
        CallbackContext.clearSqlResultHandlerOnThread();
        CallbackContext.clearSqlStringFilterOnThread();
        CallbackContext.clearSqlFireHookOnThread();
    }

    // -----------------------------------------------------
    //                                          Assist Logic
    //                                          ------------
    protected Method findHookMethod(Object hook, String methodName) {
        final Method hookMethod = Stream.of(hook.getClass().getMethods()) // always public method
                .filter(method -> method.getName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> { // no way
                    return new IllegalStateException("Not found the method in hook: " + methodName + ", " + hook);
                });
        return hookMethod;
    }

    // ===================================================================================
    //                                                                              Switch
    //                                                                              ======
    public void becomeNonCrom() {
        synchronized (varyingLock) {
            this.varyingCron = createVaryingCron(Cron4jCron.NON_CRON, varyingCron.getCronOption());
        }
    }

    public void switchCron(String cronExp, VaryingCronOption cronOption) {
        synchronized (varyingLock) {
            this.varyingCron = createVaryingCron(cronExp, cronOption);
        }
    }

    protected VaryingCron createVaryingCron(String cronExp, VaryingCronOption cronOption) {
        return new VaryingCron(cronExp, cronOption);
    }

    // ===================================================================================
    //                                                                       Determination
    //                                                                       =============
    @Override
    public boolean canBeStopped() {
        return true; // fixedly
    }

    public boolean isNonCron() {
        return Cron4jCron.isNonCronExp(varyingCron.getCronExp());
    }

    // ===================================================================================
    //                                                                             Running
    //                                                                             =======
    public <RESULT> OptionalThing<RESULT> syncRunningCall(Function<TaskRunningState, RESULT> oneArgLambda) {
        synchronized (runningState) {
            if (runningState.getBeginTime().isPresent()) {
                return OptionalThing.ofNullable(oneArgLambda.apply(runningState), () -> {
                    throw new IllegalStateException("Not found the result from your scope: " + jobType);
                });
            } else {
                return OptionalThing.ofNullable(null, () -> {
                    throw new IllegalStateException("Not running now: " + jobType);
                });
            }
        }
    }

    // ===================================================================================
    //                                                                     Framework Debug
    //                                                                     ===============
    protected void debugFw(String msg, Object... args) {
        if (isFrameworkDebug()) {
            logger.info("#job #fw " + msg, args); // info level for production environment
        }
    }

    protected boolean isFrameworkDebug() {
        return frameworkDebug;
    }

    // ===================================================================================
    //                                                                      Basic Override
    //                                                                      ==============
    @Override
    public String toString() {
        final String title = DfTypeUtil.toClassTitle(this);
        final String cronExpExp;
        final VaryingCronOption cronOption;
        synchronized (varyingLock) {
            cronExpExp = isNonCron() ? "non-cron" : varyingCron.getCronExp();
            cronOption = varyingCron.getCronOption();
        }
        return title + ":{" + cronExpExp + ", " + jobType.getName() + ", " + concurrentExec + ", " + cronOption + "}";
    }

    // ===================================================================================
    //                                                                            Accessor
    //                                                                            ========
    public VaryingCron getVaryingCron() {
        return varyingCron;
    }

    public Class<? extends LaJob> getJobType() {
        return jobType;
    }

    public JobConcurrentExec getConcurrentExec() {
        return concurrentExec;
    }

    public Object getPreparingLock() {
        return preparingLock;
    }

    public Object getRunningLock() {
        return runningLock;
    }

    public Object getVaryingLock() {
        return varyingLock;
    }

    public boolean isRunningNow() {
        synchronized (runningState) {
            return runningState.getBeginTime().isPresent();
        }
    }

    public TaskRunningState getRunningState() {
        return runningState;
    }
}
