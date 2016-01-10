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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.dbflute.optional.OptionalThing;
import org.dbflute.util.DfTypeUtil;
import org.lastaflute.job.LaJobRunner;
import org.lastaflute.job.LaScheduledJob;
import org.lastaflute.job.LaSchedulingNow;

import it.sauronsoftware.cron4j.Scheduler;
import it.sauronsoftware.cron4j.Task;

/**
 * @author jflute
 * @since 0.2.0 (2016/01/11 Monday)
 */
public class Cron4jNow implements LaSchedulingNow {

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    protected final Scheduler cron4jScheduler;
    protected final LaJobRunner jobRunner;
    protected final Map<String, Cron4jJob> cron4jJobMap = new LinkedHashMap<String, Cron4jJob>();

    // ===================================================================================
    //                                                                         Constructor
    //                                                                         ===========
    public Cron4jNow(Scheduler cron4jScheduler, LaJobRunner jobRunner) {
        this.cron4jScheduler = cron4jScheduler;
        this.jobRunner = jobRunner;
    }

    // ===================================================================================
    //                                                                            Save Job
    //                                                                            ========
    public Cron4jJob saveJob(String jobKey, String cronExp, Task cron4jTask) {
        final Cron4jJob cron4jJob = createCron4jJob(jobKey, cronExp, cron4jTask);
        cron4jJobMap.put(jobKey, cron4jJob);
        return cron4jJob;
    }

    protected Cron4jJob createCron4jJob(String jobKey, String cronExp, Task cron4jTask) {
        return new Cron4jJob(jobKey, cronExp, cron4jTask, cron4jScheduler);
    }

    // ===================================================================================
    //                                                                            Behavior
    //                                                                            ========
    @Override
    public OptionalThing<LaScheduledJob> findJobByKey(String jobKey) {
        final Cron4jJob found = cron4jJobMap.get(jobKey);
        return OptionalThing.ofNullable(found, () -> {
            throw new IllegalStateException("Not found the job by the key: " + jobKey + " existing=" + cron4jJobMap.keySet());
        });
    }

    @Override
    public List<LaScheduledJob> getJobList() {
        return Collections.unmodifiableList(new ArrayList<LaScheduledJob>(cron4jJobMap.values()));
    }

    @Override
    public void stop() {
        cron4jScheduler.stop();
    }

    // ===================================================================================
    //                                                                      Basic Override
    //                                                                      ==============
    @Override
    public String toString() {
        return DfTypeUtil.toClassTitle(this) + ":{scheduled=" + cron4jJobMap.size() + "}@" + Integer.toHexString(hashCode());
    }

    // ===================================================================================
    //                                                                            Accessor
    //                                                                            ========
    public Scheduler getCron4jScheduler() {
        return cron4jScheduler;
    }

    public LaJobRunner getJobRunner() {
        return jobRunner;
    }

    public Map<String, Cron4jJob> getCron4jJobMap() {
        return cron4jJobMap;
    }
}
