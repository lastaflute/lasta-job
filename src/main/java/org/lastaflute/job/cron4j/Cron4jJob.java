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

import org.dbflute.util.DfTypeUtil;
import org.lastaflute.job.LaScheduledJob;

import it.sauronsoftware.cron4j.Scheduler;
import it.sauronsoftware.cron4j.Task;

/**
 * @author jflute
 * @since 0.2.0 (2016/01/11 Monday)
 */
public class Cron4jJob implements LaScheduledJob {

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    protected final String jobKey;
    protected final String cronExp;
    protected final Task cron4jTask;
    protected final Scheduler cron4jScheduler;

    // ===================================================================================
    //                                                                         Constructor
    //                                                                         ===========
    public Cron4jJob(String jobKey, String cronExp, Task cron4jTask, Scheduler cron4jScheduler) {
        this.jobKey = jobKey;
        this.cronExp = cronExp;
        this.cron4jTask = cron4jTask;
        this.cron4jScheduler = cron4jScheduler;
    }

    // ===================================================================================
    //                                                                          Launch Now
    //                                                                          ==========
    @Override
    public void launchNow() {
        cron4jScheduler.launch(cron4jTask);
    }

    // ===================================================================================
    //                                                                      Basic Override
    //                                                                      ==============
    @Override
    public String toString() {
        final String hash = Integer.toHexString(hashCode());
        return DfTypeUtil.toClassTitle(this) + ":{" + jobKey + ", " + cronExp + ", " + cron4jTask + "}@" + hash;
    }

    // ===================================================================================
    //                                                                            Accessor
    //                                                                            ========
    @Override
    public String getJobKey() {
        return jobKey;
    }

    @Override
    public String getCronExp() {
        return cronExp;
    }

    public Task getCron4jTask() {
        return cron4jTask;
    }

    public Scheduler getCron4jScheduler() {
        return cron4jScheduler;
    }
}
