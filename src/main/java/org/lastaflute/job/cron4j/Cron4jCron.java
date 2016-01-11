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

import org.lastaflute.job.LaCron;
import org.lastaflute.job.LaCronOption;
import org.lastaflute.job.LaJob;
import org.lastaflute.job.LaJobRunner;
import org.lastaflute.job.LaScheduledJob;
import org.lastaflute.job.key.LaJobKey;
import org.lastaflute.job.subsidiary.CronOpCall;

/**
 * @author jflute
 * @since 0.2.0 (2016/01/09 Saturday)
 */
public class Cron4jCron implements LaCron {

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    protected final Cron4jScheduler cron4jScheduler;
    protected final LaJobRunner jobRunner;
    protected final Cron4jNow cron4jNow;

    // ===================================================================================
    //                                                                         Constructor
    //                                                                         ===========
    public Cron4jCron(Cron4jScheduler cron4jScheduler, LaJobRunner jobRunner, Cron4jNow cron4jNow) {
        this.cron4jScheduler = cron4jScheduler;
        this.jobRunner = jobRunner;
        this.cron4jNow = cron4jNow;
    }

    // ===================================================================================
    //                                                                            Register
    //                                                                            ========
    @Override
    public LaScheduledJob register(String cronExp, Class<? extends LaJob> jobType) {
        assertArgumentNotNull("cronExp", cronExp);
        assertArgumentNotNull("jobType", jobType);
        return doRegister(cronExp, jobType, op -> {});
    }

    @Override
    public LaScheduledJob register(String cronExp, Class<? extends LaJob> jobType, CronOpCall opLambda) {
        assertArgumentNotNull("cronExp", cronExp);
        assertArgumentNotNull("jobType", jobType);
        assertArgumentNotNull("opLambda (cronOptionConsumer)", opLambda);
        return doRegister(cronExp, jobType, opLambda);
    }

    protected LaScheduledJob doRegister(String cronExp, Class<? extends LaJob> jobType, CronOpCall opLambda) {
        final Cron4jTask cron4jTask = createCron4jTask(cronExp, jobType, createOption(opLambda));
        final String jobKey = cron4jScheduler.schedule(cronExp, cron4jTask);
        return cron4jNow.saveJob(createJobKey(jobKey), cronExp, cron4jTask);
    }

    protected LaCronOption createOption(CronOpCall opLambda) {
        final LaCronOption option = new LaCronOption();
        opLambda.callback(option);
        return option;
    }

    protected Cron4jTask createCron4jTask(String cronExp, Class<? extends LaJob> jobType, LaCronOption option) {
        return new Cron4jTask(cronExp, jobType, option, jobRunner, cron4jNow); // adapter task
    }

    protected LaJobKey createJobKey(String jobKey) {
        return new LaJobKey(jobKey);
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
