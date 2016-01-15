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
import org.lastaflute.job.LaCronOption;
import org.lastaflute.job.LaJob;
import org.lastaflute.job.LaScheduledJob;
import org.lastaflute.job.exception.JobAlreadyUnscheduleException;
import org.lastaflute.job.key.LaJobKey;
import org.lastaflute.job.key.LaJobUnique;
import org.lastaflute.job.log.JobChangeLog;
import org.lastaflute.job.subsidiary.CronOpCall;

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
    protected final OptionalThing<LaJobUnique> jobUnique;
    protected OptionalThing<Cron4jId> cron4jId; // mutable
    protected final Cron4jTask cron4jTask;
    protected final Cron4jNow cron4jNow;
    protected volatile boolean unscheduled;

    // ===================================================================================
    //                                                                         Constructor
    //                                                                         ===========
    public Cron4jJob(LaJobKey jobKey, OptionalThing<LaJobUnique> jobUnique, OptionalThing<Cron4jId> cron4jId, Cron4jTask cron4jTask,
            Cron4jNow cron4jNow) {
        this.jobKey = jobKey;
        this.jobUnique = jobUnique;
        this.cron4jId = cron4jId;
        this.cron4jTask = cron4jTask;
        this.cron4jNow = cron4jNow;
    }

    // ===================================================================================
    //                                                                       Executing Now
    //                                                                       =============
    @Override
    public boolean isExecutingNow() {
        return !findExecutorList().isEmpty();
    }

    public List<TaskExecutor> findExecutorList() {
        return cron4jNow.getCron4jScheduler().findExecutorList(cron4jTask);
    }

    // ===================================================================================
    //                                                                          Launch Now
    //                                                                          ==========
    @Override
    public synchronized void launchNow() {
        if (unscheduled) {
            throw new JobAlreadyUnscheduleException("Already unscheduled the job: " + toString());
        }
        if (JobChangeLog.isEnabled()) {
            JobChangeLog.log("#job ...Launching now: {}", toString());
        }
        // if executed by cron here, duplicate execution occurs but task level synchronization exists
        cron4jNow.getCron4jScheduler().launch(cron4jTask);
    }

    // ===================================================================================
    //                                                                            Stop Now
    //                                                                            ========
    @Override
    public synchronized void stopNow() {
        final List<TaskExecutor> executorList = findExecutorList();
        if (JobChangeLog.isEnabled()) {
            JobChangeLog.log("#job ...Stopping {} execution(s) now: {}", executorList.size(), toString());
        }
        if (!executorList.isEmpty()) {
            executorList.forEach(executor -> executor.stop());
        }
    }

    // ===================================================================================
    //                                                                          Reschedule
    //                                                                          ==========
    @Override
    public synchronized void reschedule(String cronExp, CronOpCall opLambda) {
        if (unscheduled) {
            throw new JobAlreadyUnscheduleException("Already unscheduled the job: " + toString());
        }
        if (isNonCrom(cronExp)) {
            throw new IllegalArgumentException("The cronExp for reschedule() should not be non-cron: " + toString());
        }
        final String existingCronExp = cron4jTask.getCronExp();
        cron4jTask.switchCron(cronExp, createCronOption(opLambda));
        final Cron4jScheduler cron4jScheduler = cron4jNow.getCron4jScheduler();
        cron4jId.ifPresent(id -> {
            if (JobChangeLog.isEnabled()) {
                JobChangeLog.log("#job ...Rescheduling {} as cron from '{}' to '{}'", jobKey, existingCronExp, cronExp);
            }
            cron4jScheduler.reschedule(id, cronExp);
        }).orElse(() -> {
            if (JobChangeLog.isEnabled()) {
                JobChangeLog.log("#job ...Rescheduling {} as cron from non-cron to '{}'", jobKey, cronExp);
            }
            final String generatedId = cron4jScheduler.schedule(cronExp, cron4jTask);
            cron4jId = OptionalThing.of(Cron4jId.of(generatedId));
        });
    }

    protected boolean isNonCrom(String cronExp) {
        return Cron4jCron.isNonCron(cronExp);
    }

    protected LaCronOption createCronOption(CronOpCall opLambda) {
        final LaCronOption option = new LaCronOption();
        opLambda.callback(option);
        return option;
    }

    // ===================================================================================
    //                                                                          Unschedule
    //                                                                          ==========
    @Override
    public synchronized void unschedule() {
        if (JobChangeLog.isEnabled()) {
            JobChangeLog.log("#job ...Unscheduling {}", toString());
        }
        cron4jId.ifPresent(id -> {
            cron4jNow.getCron4jScheduler().deschedule(id);
        });
        cron4jNow.clearUnscheduleJob(); // immediately clear, executing process is kept
        unscheduled = true;
    }

    @Override
    public synchronized boolean isUnscheduled() {
        return unscheduled;
    }

    // ===================================================================================
    //                                                                            Non-Cron
    //                                                                            ========
    @Override
    public synchronized void becomeNonCron() {
        if (JobChangeLog.isEnabled()) {
            JobChangeLog.log("#job ...Becoming non-cron: {}", toString());
        }
        cron4jId.ifPresent(id -> {
            cron4jTask.becomeNonCrom();
            cron4jNow.getCron4jScheduler().deschedule(id);
            cron4jId = OptionalThing.empty();
        });
    }

    @Override
    public synchronized boolean isNonCron() {
        return cron4jId.isPresent();
    }

    // ===================================================================================
    //                                                                      Basic Override
    //                                                                      ==============
    @Override
    public String toString() {
        final String keyExp = jobUnique.map(uq -> uq + "(" + jobKey + ")").orElseGet(() -> jobKey.toString());
        final String idExp = cron4jId.map(id -> id.value()).orElse("non-cron");
        final String hash = Integer.toHexString(hashCode());
        return DfTypeUtil.toClassTitle(this) + ":{" + keyExp + ", " + idExp + ", " + cron4jTask + "}@" + hash;
    }

    // ===================================================================================
    //                                                                            Accessor
    //                                                                            ========
    @Override
    public LaJobKey getJobKey() {
        return jobKey;
    }

    @Override
    public OptionalThing<LaJobUnique> getJobUnique() {
        return jobUnique;
    }

    @Override
    public String getCronExp() {
        return cron4jTask.getCronExp();
    }

    @Override
    public Class<? extends LaJob> getJobType() {
        return cron4jTask.getJobType();
    }

    public Cron4jTask getCron4jTask() {
        return cron4jTask;
    }
}
