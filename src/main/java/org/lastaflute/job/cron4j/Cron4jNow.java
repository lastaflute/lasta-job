/*
 * Copyright 2015-2016 the original author or authors.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.dbflute.optional.OptionalThing;
import org.dbflute.util.DfTypeUtil;
import org.lastaflute.job.LaJobRunner;
import org.lastaflute.job.LaSchedulingNow;
import org.lastaflute.job.key.LaJobKey;
import org.lastaflute.job.key.LaJobUniqueCode;
import org.lastaflute.job.subsidiary.CronConsumer;

/**
 * @author jflute
 * @since 0.2.0 (2016/01/11 Monday)
 */
public class Cron4jNow implements LaSchedulingNow {

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    protected final Cron4jScheduler cron4jScheduler;
    protected final LaJobRunner jobRunner;
    protected final Map<LaJobKey, Cron4jJob> jobKeyJobMap = new ConcurrentHashMap<LaJobKey, Cron4jJob>();
    protected final Map<LaJobUniqueCode, Cron4jJob> uniqueCodeJobMap = new ConcurrentHashMap<LaJobUniqueCode, Cron4jJob>();
    protected final Map<Cron4jTask, Cron4jJob> cron4jTaskJobMap = new ConcurrentHashMap<Cron4jTask, Cron4jJob>();

    // ===================================================================================
    //                                                                         Constructor
    //                                                                         ===========
    public Cron4jNow(Cron4jScheduler cron4jScheduler, LaJobRunner jobRunner) {
        this.cron4jScheduler = cron4jScheduler;
        this.jobRunner = jobRunner;
    }

    // ===================================================================================
    //                                                                            Save Job
    //                                                                            ========
    public synchronized Cron4jJob saveJob(LaJobKey jobKey, String cronExp, Cron4jTask cron4jTask) {
        assertArgumentNotNull("jobKey", jobKey);
        assertArgumentNotNull("cronExp", cronExp);
        assertArgumentNotNull("cron4jTask", cron4jTask);
        final Cron4jJob cron4jJob = createCron4jJob(jobKey, cronExp, cron4jTask);
        assertDuplicateJobKey(jobKey);
        jobKeyJobMap.put(jobKey, cron4jJob);
        cron4jTask.getUniqueCode().ifPresent(uniqueCode -> {
            assertDuplicateUniqueCode(jobKey, uniqueCode);
            uniqueCodeJobMap.put(uniqueCode, cron4jJob);
        });
        cron4jTaskJobMap.put(cron4jTask, cron4jJob); // task is unique in lasta-job world
        return cron4jJob;
    }

    protected Cron4jJob createCron4jJob(LaJobKey jobKey, String cronExp, Cron4jTask cron4jTask) {
        return new Cron4jJob(jobKey, cronExp, cron4jTask, cron4jScheduler);
    }

    protected void assertDuplicateJobKey(LaJobKey jobKey) {
        if (jobKeyJobMap.containsKey(jobKey)) { // no way because of auto-generated, just in case
            throw new IllegalStateException("Duplicate job key: " + jobKey + " existing=" + jobKeyJobMap);
        }
    }

    protected void assertDuplicateUniqueCode(LaJobKey jobKey, LaJobUniqueCode uniqueCode) {
        if (uniqueCodeJobMap.containsKey(uniqueCode)) { // application mistake
            // simple message #for_now
            throw new IllegalStateException("Duplicate unique code: " + jobKey + " existing=" + uniqueCodeJobMap);
        }
    }

    protected void assertDuplicateTask(Cron4jTask cron4jTask) {
        if (cron4jTaskJobMap.containsKey(cron4jTask)) { // no way just in case
            throw new IllegalStateException("Duplicate task: " + cron4jTask + " existing=" + jobKeyJobMap);
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
            String msg = "Not found the job by the key: " + jobKey + " existing=" + jobKeyJobMap.keySet();
            throw new IllegalStateException(msg);
        });
    }

    @Override
    public OptionalThing<Cron4jJob> findJobByUniqueCode(LaJobUniqueCode uniqueCode) {
        assertArgumentNotNull("uniqueCode", uniqueCode);
        final Cron4jJob found = uniqueCodeJobMap.get(uniqueCode);
        return OptionalThing.ofNullable(found, () -> {
            String msg = "Not found the job by the unique code: " + uniqueCode + " existing=" + uniqueCodeJobMap.keySet();
            throw new IllegalStateException(msg);
        });
    }

    public OptionalThing<Cron4jJob> findJobByTask(Cron4jTask task) {
        assertArgumentNotNull("task", task);
        final Cron4jJob found = cron4jTaskJobMap.get(task);
        return OptionalThing.ofNullable(found, () -> {
            String msg = "Not found the job by the task: " + task + " existing=" + cron4jTaskJobMap.keySet();
            throw new IllegalStateException(msg);
        });
    }

    @Override
    public List<Cron4jJob> getJobList() {
        return Collections.unmodifiableList(new ArrayList<Cron4jJob>(jobKeyJobMap.values()));
    }

    public List<Cron4jJob> getCron4jJobList() {
        return Collections.unmodifiableList(new ArrayList<Cron4jJob>(jobKeyJobMap.values()));
    }

    // ===================================================================================
    //                                                                        Register Job
    //                                                                        ============
    @Override
    public synchronized void registerJob(CronConsumer oneArgLambda) {
        assertArgumentNotNull("oneArgLambda", oneArgLambda);
        oneArgLambda.consume(createCron4jCron());
    }

    protected Cron4jCron createCron4jCron() {
        return new Cron4jCron(cron4jScheduler, jobRunner, this);
    }

    // ===================================================================================
    //                                                                          Closed Job
    //                                                                          ==========
    @Override
    public synchronized void clearClosedJob() {
        getCron4jJobList().stream().filter(job -> job.isClosed()).forEach(job -> {
            jobKeyJobMap.remove(job.getJobKey());
            job.getUniqueCode().ifPresent(uniqueCode -> uniqueCodeJobMap.remove(uniqueCode));
            cron4jTaskJobMap.remove(job.getCron4jTask());
        });
    }

    // ===================================================================================
    //                                                                    Destroy Schedule
    //                                                                    ================
    @Override
    public synchronized void destroySchedule() {
        cron4jScheduler.stop();
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
    public Cron4jScheduler getCron4jScheduler() {
        return cron4jScheduler;
    }

    public LaJobRunner getJobRunner() {
        return jobRunner;
    }

    public Map<LaJobKey, Cron4jJob> getJobKeyJobMap() {
        return Collections.unmodifiableMap(jobKeyJobMap);
    }

    public Map<LaJobUniqueCode, Cron4jJob> getUniqueCodeJobMap() {
        return Collections.unmodifiableMap(uniqueCodeJobMap);
    }

    public Map<Cron4jTask, Cron4jJob> getTaskJobMap() {
        return Collections.unmodifiableMap(cron4jTaskJobMap);
    }
}
