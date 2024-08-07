/*
 * Copyright 2015-2024 the original author or authors.
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Supplier;

import org.dbflute.optional.OptionalThing;
import org.dbflute.util.DfTypeUtil;
import org.dbflute.util.Srl;
import org.lastaflute.job.LaJobHistory;
import org.lastaflute.job.LaJobRunner;
import org.lastaflute.job.LaSchedulingNow;
import org.lastaflute.job.cron4j.Cron4jCron.CronRegistrationType;
import org.lastaflute.job.cron4j.Cron4jTask.TaskJobIdentity;
import org.lastaflute.job.exception.JobNotFoundException;
import org.lastaflute.job.key.LaJobKey;
import org.lastaflute.job.key.LaJobNote;
import org.lastaflute.job.key.LaJobUnique;
import org.lastaflute.job.log.JobChangeLog;
import org.lastaflute.job.subsidiary.CronConsumer;
import org.lastaflute.job.subsidiary.JobConcurrentExec;
import org.lastaflute.job.subsidiary.JobSubIdentityAttr;
import org.lastaflute.job.subsidiary.NeighborConcurrentGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author jflute
 * @since 0.2.0 (2016/01/11 Monday)
 */
public class Cron4jNow implements LaSchedulingNow {

    // ===================================================================================
    //                                                                          Definition
    //                                                                          ==========
    private static final Logger logger = LoggerFactory.getLogger(Cron4jNow.class);

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    protected final Cron4jScheduler cron4jScheduler;
    protected final LaJobRunner jobRunner;
    protected final Supplier<LocalDateTime> currentTime;
    protected final boolean frameworkDebug;
    protected final Map<LaJobKey, Cron4jJob> jobKeyJobMap = new ConcurrentHashMap<LaJobKey, Cron4jJob>();
    protected final List<Cron4jJob> jobOrderedList = new CopyOnWriteArrayList<Cron4jJob>(); // same lifecycle as jobKeyJobMap
    protected final Map<LaJobUnique, Cron4jJob> jobUniqueJobMap = new ConcurrentHashMap<LaJobUnique, Cron4jJob>();
    protected final Map<TaskJobIdentity, Cron4jJob> cron4jTaskJobMap = new ConcurrentHashMap<TaskJobIdentity, Cron4jJob>();
    protected final Map<String, NeighborConcurrentGroup> neighborConcurrentMap = new ConcurrentHashMap<String, NeighborConcurrentGroup>();
    protected int incrementedJobNumber;

    // ===================================================================================
    //                                                                         Constructor
    //                                                                         ===========
    public Cron4jNow(Cron4jScheduler cron4jScheduler, LaJobRunner jobRunner, Supplier<LocalDateTime> currentTime, boolean frameworkDebug) {
        this.cron4jScheduler = cron4jScheduler;
        this.jobRunner = jobRunner;
        this.currentTime = currentTime;
        this.frameworkDebug = frameworkDebug;
    }

    // ===================================================================================
    //                                                                            Save Job
    //                                                                            ========
    /**
     * @param cron4jTask (NotNull)
     * @param subIdentityAttr The optional identity attributes of job, e.g. jobTitle, jobUnique. (NotNull)
     * @param triggeringJobKeyList The list of job key triggering me. (NotNull, EmptyAllowed)
     * @param cron4jId The ID auto-generated by cron4j. (NotNull, EmptyAllowed: when non-scheduling)
     * @return The new-created job to be saved in this object. (NotNull)
     */
    public synchronized Cron4jJob saveJob(Cron4jTask cron4jTask, JobSubIdentityAttr subIdentityAttr, List<LaJobKey> triggeringJobKeyList,
            OptionalThing<String> cron4jId) {
        assertArgumentNotNull("cron4jTask", cron4jTask);
        final LaJobKey jobKey = generateJobKey(cron4jTask);
        final Cron4jJob cron4jJob = createCron4jJob(jobKey, subIdentityAttr, triggeringJobKeyList, cron4jId, cron4jTask);
        assertDuplicateJobKey(jobKey);
        jobKeyJobMap.put(jobKey, cron4jJob);
        jobOrderedList.add(cron4jJob);
        subIdentityAttr.getJobUnique().ifPresent(uniqueCode -> {
            assertDuplicateUniqueCode(jobKey, uniqueCode);
            jobUniqueJobMap.put(uniqueCode, cron4jJob);
        });
        // task is unique in lasta-job world (except outlaw parallel so uses identity object)
        cron4jTaskJobMap.put(cron4jTask.getTaskJobIdentity(), cron4jJob);
        return cron4jJob;
    }

    // -----------------------------------------------------
    //                                                JobKey
    //                                                ------
    protected LaJobKey generateJobKey(Cron4jTask cron4jTask) {
        if (incrementedJobNumber == Integer.MAX_VALUE) { // no no no, no way! but just in case
            incrementedJobNumber = 1;
        } else { // mainly here
            ++incrementedJobNumber;
        }
        return createJobKey(buildJobKey(cron4jTask, incrementedJobNumber));
    }

    protected String buildJobKey(Cron4jTask cron4jTask, int jobNumber) {
        return Srl.initUncap(cron4jTask.getJobType().getSimpleName()) + "_" + jobNumber;
    }

    protected LaJobKey createJobKey(String jobKey) {
        return LaJobKey.of(jobKey);
    }

    // -----------------------------------------------------
    //                                             Cron4jJob
    //                                             ---------
    protected Cron4jJob createCron4jJob(LaJobKey jobKey, JobSubIdentityAttr subIdentityAttr, List<LaJobKey> triggeringJobKeyList,
            OptionalThing<String> cron4jId, Cron4jTask cron4jTask) {
        final Cron4jJob job = newCron4jJob(jobKey, subIdentityAttr.getJobNote(), subIdentityAttr.getJobUnique(),
                cron4jId.map(id -> Cron4jId.of(id)), cron4jTask, this);
        triggeringJobKeyList.forEach(triggeringJobKey -> {
            findJobByKey(triggeringJobKey).alwaysPresent(triggeringJob -> {
                triggeringJob.registerNext(jobKey);
            });
        });
        return job;
    }

    protected Cron4jJob newCron4jJob(LaJobKey jobKey, OptionalThing<LaJobNote> jobNote, OptionalThing<LaJobUnique> jobUnique,
            OptionalThing<Cron4jId> cron4jId, Cron4jTask cron4jTask, Cron4jNow cron4jNow) {
        return new Cron4jJob(jobKey, jobNote, jobUnique, cron4jId, cron4jTask, cron4jNow);
    }

    // -----------------------------------------------------
    //                                                Assert
    //                                                ------
    protected void assertDuplicateJobKey(LaJobKey jobKey) {
        if (jobKeyJobMap.containsKey(jobKey)) { // no way because of auto-generated, just in case
            throw new IllegalStateException("Duplicate job key: " + jobKey + " existing=" + jobKeyJobMap);
        }
    }

    protected void assertDuplicateUniqueCode(LaJobKey jobKey, LaJobUnique jobUnique) {
        if (jobUniqueJobMap.containsKey(jobUnique)) { // application mistake
            // simple message #for_now
            throw new IllegalStateException("Duplicate job unique: " + jobKey + " existing=" + jobUniqueJobMap);
        }
    }

    protected void assertDuplicateTask(Cron4jTask cron4jTask) {
        if (cron4jTaskJobMap.containsKey(cron4jTask.getTaskJobIdentity())) { // no way just in case
            throw new IllegalStateException("Duplicate task: " + cron4jTask + " existing=" + cron4jTaskJobMap);
        }
    }

    // ===================================================================================
    //                                                                            Find Job
    //                                                                            ========
    @Override
    public OptionalThing<Cron4jJob> findJobByKey(LaJobKey jobKey) {
        assertArgumentNotNull("jobKey", jobKey);
        final Cron4jJob found = jobKeyJobMap.get(jobKey);
        return OptionalThing.ofNullable(found, () -> {
            String msg = "Not found the job by the key: " + jobKey + " existing=" + jobKeyJobMap;
            throw new JobNotFoundException(msg);
        });
    }

    @Override
    public OptionalThing<Cron4jJob> findJobByUniqueOf(LaJobUnique jobUnique) {
        assertArgumentNotNull("jobUnique", jobUnique);
        final Cron4jJob found = jobUniqueJobMap.get(jobUnique);
        return OptionalThing.ofNullable(found, () -> {
            String msg = "Not found the job by the unique code: " + jobUnique + " existing=" + jobUniqueJobMap;
            throw new JobNotFoundException(msg);
        });
    }

    public OptionalThing<Cron4jJob> findJobByTask(TaskJobIdentity taskJobIdentity) {
        assertArgumentNotNull("taskJobIdentity", taskJobIdentity);
        final Cron4jJob found = cron4jTaskJobMap.get(taskJobIdentity);
        return OptionalThing.ofNullable(found, () -> {
            String msg = "Not found the job by the task: " + taskJobIdentity + " existing=" + cron4jTaskJobMap;
            throw new JobNotFoundException(msg);
        });
    }

    @Override
    public List<Cron4jJob> getJobList() {
        return Collections.unmodifiableList(jobOrderedList);
    }

    public List<Cron4jJob> getCron4jJobList() {
        return Collections.unmodifiableList(jobOrderedList);
    }

    // ===================================================================================
    //                                                                        Register Job
    //                                                                        ============
    @Override
    public synchronized void schedule(CronConsumer oneArgLambda) {
        assertArgumentNotNull("oneArgLambda", oneArgLambda);
        oneArgLambda.consume(createCron4jCron());
    }

    protected Cron4jCron createCron4jCron() {
        return new Cron4jCron(cron4jScheduler, jobRunner, this, CronRegistrationType.CHANGE, currentTime, isFrameworkDebug());
    }

    // ===================================================================================
    //                                                                           Clear Job
    //                                                                           =========
    /**
     * Clear disappeared jobs from job list if it exists.
     */
    public synchronized void clearDisappearedJob() {
        getCron4jJobList().stream().filter(job -> job.isDisappeared()).forEach(job -> {
            final LaJobKey jobKey = job.getJobKey();
            jobKeyJobMap.remove(jobKey);
            jobOrderedList.remove(job);
            job.getJobUnique().ifPresent(jobUnique -> jobUniqueJobMap.remove(jobUnique));
            cron4jTaskJobMap.remove(job.getCron4jTask().getTaskJobIdentity());
        });
    }

    // ===================================================================================
    //                                                                         Job History
    //                                                                         ===========
    @Override
    public List<LaJobHistory> searchJobHistoryList() {
        final Supplier<List<LaJobHistory>> nativeSearcher = () -> Cron4jJobHistory.list();
        return jobRunner.getHistoryHook().map(hook -> {
            return hook.hookList(nativeSearcher);
        }).orElseGet(() -> {
            return nativeSearcher.get();
        });
    }

    // ===================================================================================
    //                                                                 Neighbor Concurrent
    //                                                                 ===================
    @Override
    public void setupNeighborConcurrent(String groupName, JobConcurrentExec concurrentExec, Set<LaJobKey> jobKeySet) {
        assertArgumentNotNull("groupName", groupName);
        if (groupName.trim().isEmpty()) {
            throw new IllegalArgumentException("The argument 'groupName' should not be empty: [" + groupName + "]");
        }
        assertArgumentNotNull("concurrentExec", concurrentExec);
        assertArgumentNotNull("jobKeySet", jobKeySet);
        if (jobKeySet.size() <= 1) {
            throw new IllegalArgumentException("The specified jobs should be two or more: " + jobKeySet);
        }
        synchronized (neighborConcurrentMap) {
            checkDuplicateNeighborConcurrentGroup(groupName);
            final Object groupPreparingLock = new Object();
            final Object groupRunningLock = new Object();
            final CopyOnWriteArraySet<LaJobKey> safeSet = new CopyOnWriteArraySet<LaJobKey>(jobKeySet);
            final NeighborConcurrentGroup group =
                    new NeighborConcurrentGroup(groupName, concurrentExec, safeSet, groupPreparingLock, groupRunningLock);
            neighborConcurrentMap.put(groupName, group);
            safeSet.stream().map(jobKey -> findJobByKey(jobKey).get()).forEach(job -> { // or job not found
                job.registerNeighborConcurrent(groupName, group);
            });
        }
    }

    protected void checkDuplicateNeighborConcurrentGroup(String groupName) {
        final NeighborConcurrentGroup existingGroup = neighborConcurrentMap.get(groupName);
        if (existingGroup != null) {
            String msg = "The groupName already exists: specified=" + groupName + ", existing=" + existingGroup;
            throw new IllegalArgumentException(msg);
        }
    }

    // ===================================================================================
    //                                                                    Destroy Schedule
    //                                                                    ================
    @Override
    public synchronized void destroy() {
        if (JobChangeLog.isEnabled()) {
            JobChangeLog.log("#job ...Destroying scheduler completely: jobs={} scheduler={}", jobKeyJobMap.size(), cron4jScheduler);
        }
        // not use AsyncManager here, because not frequent call, keep no dependency to core
        new Thread(() -> { // to release synchronized lock to avoid deadlock
            try {
                cron4jScheduler.stop();
            } catch (RuntimeException e) {
                final String msg = "#job Failed to stop jobs: jobs={} scheduler={}";
                if (JobChangeLog.isEnabled()) {
                    JobChangeLog.log(msg, jobKeyJobMap.size(), cron4jScheduler, e);
                } else { // just in case
                    logger.info(msg, jobKeyJobMap.size(), cron4jScheduler, e);
                }
            }
            Cron4jJobHistory.clear();
        }).start();
    }

    // ===================================================================================
    //                                                                     Framework Debug
    //                                                                     ===============
    protected boolean isFrameworkDebug() {
        return frameworkDebug;
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
        return DfTypeUtil.toClassTitle(this) + ":{scheduled=" + jobKeyJobMap.size() + "}@" + Integer.toHexString(hashCode());
    }

    // ===================================================================================
    //                                                                            Accessor
    //                                                                            ========
    // basically for framework
    public Cron4jScheduler getCron4jScheduler() {
        return cron4jScheduler;
    }

    public LaJobRunner getJobRunner() {
        return jobRunner;
    }

    public Supplier<LocalDateTime> getCurrentTime() {
        return currentTime;
    }

    public Map<LaJobKey, Cron4jJob> getJobKeyJobMap() {
        return Collections.unmodifiableMap(jobKeyJobMap);
    }

    public Map<LaJobUnique, Cron4jJob> getJobUniqueJobMap() {
        return Collections.unmodifiableMap(jobUniqueJobMap);
    }

    public Map<TaskJobIdentity, Cron4jJob> getTaskJobMap() {
        return Collections.unmodifiableMap(cron4jTaskJobMap);
    }
}
