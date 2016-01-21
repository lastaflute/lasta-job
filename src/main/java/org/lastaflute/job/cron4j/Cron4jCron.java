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

import org.dbflute.optional.OptionalThing;
import org.lastaflute.job.LaCron;
import org.lastaflute.job.LaJob;
import org.lastaflute.job.LaJobRunner;
import org.lastaflute.job.LaScheduledJob;
import org.lastaflute.job.key.LaJobUnique;
import org.lastaflute.job.log.JobChangeLog;
import org.lastaflute.job.subsidiary.ConcurrentExec;
import org.lastaflute.job.subsidiary.CronOption;
import org.lastaflute.job.subsidiary.InitialCronOpCall;
import org.lastaflute.job.subsidiary.InitialCronOption;
import org.lastaflute.job.subsidiary.VaryingCron;

/**
 * @author jflute
 * @since 0.2.0 (2016/01/09 Saturday)
 */
public class Cron4jCron implements LaCron {

    // ===================================================================================
    //                                                                          Definition
    //                                                                          ==========
    /** The cronExp to specify non-scheduling. */
    public static String NON_CRON = "$$nonCron$$";

    public static boolean isNonCron(String cronExp) {
        return NON_CRON.equals(cronExp);
    }

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    protected final Cron4jScheduler cron4jScheduler;
    protected final LaJobRunner jobRunner;
    protected final Cron4jNow cron4jNow;
    protected final CronRegistrationType registrationType;

    // ===================================================================================
    //                                                                         Constructor
    //                                                                         ===========
    public Cron4jCron(Cron4jScheduler cron4jScheduler, LaJobRunner jobRunner, Cron4jNow cron4jNow, CronRegistrationType registrationType) {
        this.cron4jScheduler = cron4jScheduler;
        this.jobRunner = jobRunner;
        this.cron4jNow = cron4jNow;
        this.registrationType = registrationType;
    }

    public enum CronRegistrationType {
        START, CHANGE
    }

    // ===================================================================================
    //                                                                            Register
    //                                                                            ========
    @Override
    public LaScheduledJob register(String cronExp, Class<? extends LaJob> jobType, ConcurrentExec concurrentExec,
            InitialCronOpCall opLambda) {
        assertArgumentNotNull("cronExp", cronExp);
        if (isNonCrom(cronExp)) {
            throw new IllegalArgumentException("The cronExp for register() should not be non-cron: " + toString());
        }
        assertArgumentNotNull("jobType", jobType);
        assertArgumentNotNull("concurrentExec", concurrentExec);
        assertArgumentNotNull("opLambda (cronOptionConsumer)", opLambda);
        return doRegister(cronExp, jobType, concurrentExec, opLambda);
    }

    protected boolean isNonCrom(String cronExp) {
        return Cron4jCron.isNonCron(cronExp);
    }

    @Override
    public LaScheduledJob registerNonCron(Class<? extends LaJob> jobType, ConcurrentExec concurrentExec, InitialCronOpCall opLambda) {
        assertArgumentNotNull("jobType", jobType);
        assertArgumentNotNull("concurrentExec", concurrentExec);
        assertArgumentNotNull("opLambda (cronOptionConsumer)", opLambda);
        return doRegister(NON_CRON, jobType, concurrentExec, opLambda);
    }

    protected LaScheduledJob doRegister(String cronExp, Class<? extends LaJob> jobType, ConcurrentExec concurrentExec,
            InitialCronOpCall opLambda) {
        final InitialCronOption cronOption = createCronOption(opLambda);
        final Cron4jTask cron4jTask = createCron4jTask(cronExp, jobType, concurrentExec, cronOption);
        showRegistering(cron4jTask);
        final OptionalThing<LaJobUnique> jobUnique = cronOption.getJobUnique();
        final String cron4jId = scheduleIfNeeds(cronExp, cron4jTask);
        return saveJob(cron4jTask, jobUnique, cron4jId);
    }

    protected InitialCronOption createCronOption(InitialCronOpCall opLambda) {
        final CronOption option = new CronOption();
        opLambda.callback(option);
        return option;
    }

    protected Cron4jTask createCron4jTask(String cronExp, Class<? extends LaJob> jobType, ConcurrentExec concurrentExec,
            InitialCronOption cronOption) {
        final VaryingCron varyingCron = createVaryingCron(cronExp, cronOption);
        final String threadName = buildThreadName(cronOption);
        return new Cron4jTask(varyingCron, jobType, concurrentExec, threadName, jobRunner, cron4jNow); // adapter task
    }

    protected VaryingCron createVaryingCron(String cronExp, InitialCronOption cronOption) {
        return new VaryingCron(cronExp, cronOption);
    }

    protected String buildThreadName(InitialCronOption cronOption) {
        return "job_" + cronOption.getJobUnique().map(uq -> uq.value()).orElseGet(() -> {
            return Integer.toHexString(Thread.currentThread().hashCode());
        });
    }

    protected void showRegistering(final Cron4jTask cron4jTask) {
        if (CronRegistrationType.CHANGE.equals(registrationType) && JobChangeLog.isEnabled()) {
            // only when change, because starter shows rich logging when start
            JobChangeLog.log("#job ...Registering job: {}", cron4jTask);
        }
    }

    protected String scheduleIfNeeds(String cronExp, Cron4jTask cron4jTask) {
        final String cron4jId;
        if (cron4jTask.isNonCron()) {
            cron4jId = null;
        } else { // mainly here
            cron4jId = cron4jScheduler.schedule(cronExp, cron4jTask);
        }
        return cron4jId;
    }

    protected Cron4jJob saveJob(Cron4jTask cron4jTask, OptionalThing<LaJobUnique> jobUnique, String cron4jId) {
        return cron4jNow.saveJob(cron4jTask, jobUnique, OptionalThing.ofNullable(cron4jId, () -> {
            throw new IllegalStateException("Not found the cron4jId: " + cron4jTask);
        }));
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
}
