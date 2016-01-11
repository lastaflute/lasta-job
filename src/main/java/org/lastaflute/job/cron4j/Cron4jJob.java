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

import java.util.List;

import org.dbflute.optional.OptionalThing;
import org.dbflute.util.DfTypeUtil;
import org.lastaflute.job.LaScheduledJob;
import org.lastaflute.job.exception.JobAlreadyClosedException;
import org.lastaflute.job.exception.JobAlreadyExecutingNowException;
import org.lastaflute.job.exception.JobNoExecutingNowException;
import org.lastaflute.job.key.LaJobKey;
import org.lastaflute.job.key.LaJobUniqueCode;

import it.sauronsoftware.cron4j.TaskExecutor;

/**
 * @author jflute
 * @since 0.2.0 (2016/01/11 Monday)
 */
public class Cron4jJob implements LaScheduledJob {

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    protected final LaJobKey jobKey;
    protected final String cronExp;
    protected final Cron4jTask cron4jTask;
    protected final Cron4jScheduler cron4jScheduler;
    protected volatile boolean closed;

    // ===================================================================================
    //                                                                         Constructor
    //                                                                         ===========
    public Cron4jJob(LaJobKey jobKey, String cronExp, Cron4jTask cron4jTask, Cron4jScheduler cron4jScheduler) {
        this.jobKey = jobKey;
        this.cronExp = cronExp;
        this.cron4jTask = cron4jTask;
        this.cron4jScheduler = cron4jScheduler;
    }

    // ===================================================================================
    //                                                                       Executing Now
    //                                                                       =============
    @Override
    public boolean isExecutingNow() {
        return !findExecutorList().isEmpty();
    }

    public List<TaskExecutor> findExecutorList() {
        return cron4jScheduler.findExecutorList(cron4jTask);
    }

    // ===================================================================================
    //                                                                          Launch Now
    //                                                                          ==========
    @Override
    public synchronized void launchNow() throws JobAlreadyClosedException, JobAlreadyExecutingNowException {
        if (closed) {
            throw new JobAlreadyClosedException("Already closed the job: " + toString());
        }
        if (isExecutingNow()) {
            throw new JobAlreadyExecutingNowException("Already executing the job now: " + toString());
        }
        // if executed by cron here, duplicate execution occurs but task level synchronization exists
        cron4jScheduler.launch(cron4jTask);
    }

    // ===================================================================================
    //                                                                            Stop Now
    //                                                                            ========
    @Override
    public synchronized void stopNow() throws JobNoExecutingNowException {
        final List<TaskExecutor> executorList = findExecutorList();
        if (!executorList.isEmpty()) {
            executorList.forEach(executor -> executor.stop());
        } else {
            throw new JobNoExecutingNowException("No executing the job now: " + toString());
        }
    }

    // ===================================================================================
    //                                                                           Close Now
    //                                                                           =========
    @Override
    public synchronized void closeNow() {
        cron4jScheduler.deschedule(jobKey.value());
        closed = true;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    // ===================================================================================
    //                                                                      Basic Override
    //                                                                      ==============
    @Override
    public String toString() {
        // cron4jTask has cronExp so no use here
        final String hash = Integer.toHexString(hashCode());
        return DfTypeUtil.toClassTitle(this) + ":{" + jobKey + ", " + cron4jTask + "}@" + hash;
    }

    // ===================================================================================
    //                                                                            Accessor
    //                                                                            ========
    @Override
    public LaJobKey getJobKey() {
        return jobKey;
    }

    @Override
    public String getCronExp() {
        return cronExp;
    }

    @Override
    public OptionalThing<LaJobUniqueCode> getUniqueCode() {
        return cron4jTask.getUniqueCode();
    }

    public Cron4jTask getCron4jTask() {
        return cron4jTask;
    }

    public Cron4jScheduler getCron4jScheduler() {
        return cron4jScheduler;
    }
}
