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

import java.util.Collections;
import java.util.Map;
import java.util.function.Supplier;

import org.lastaflute.job.LaCron;
import org.lastaflute.job.LaJob;
import org.lastaflute.job.LaJobRunner;
import org.lastaflute.job.LaScheduledJob;

import it.sauronsoftware.cron4j.Scheduler;
import it.sauronsoftware.cron4j.Task;
import it.sauronsoftware.cron4j.TaskExecutionContext;

/**
 * @author jflute
 * @since 0.2.0 (2016/01/09 Saturday)
 */
public class Cron4jCron implements LaCron {

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    protected final Scheduler cron4jScheduler;
    protected final LaJobRunner jobRunner;
    protected final Cron4jNow cron4jNow;

    // ===================================================================================
    //                                                                         Constructor
    //                                                                         ===========
    public Cron4jCron(Scheduler cron4jScheduler, LaJobRunner jobRunner, Cron4jNow cron4jNow) {
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
        return doRegister(cronExp, jobType, () -> Collections.emptyMap());
    }

    @Override
    public LaScheduledJob register(String cronExp, Class<? extends LaJob> jobType, Supplier<Map<String, Object>> noArgLambda) {
        assertArgumentNotNull("cronExp", cronExp);
        assertArgumentNotNull("jobType", jobType);
        assertArgumentNotNull("noArgLambda (parameterSupplier)", noArgLambda);
        return doRegister(cronExp, jobType, noArgLambda);
    }

    protected LaScheduledJob doRegister(String cronExp, Class<? extends LaJob> jobType, Supplier<Map<String, Object>> parameterSupplier) {
        final Task cron4jTask = createCron4jTask(cronExp, jobType, parameterSupplier);
        final String jobKey = cron4jScheduler.schedule(cronExp, cron4jTask);
        return cron4jNow.saveJob(jobKey, cronExp, cron4jTask);
    }

    protected Task createCron4jTask(String cronExp, Class<? extends LaJob> jobType, Supplier<Map<String, Object>> parameterSupplier) {
        return new Task() { // adapter task
            public void execute(TaskExecutionContext context) throws RuntimeException {
                adjustThreadNameIfNeeds();
                runJob(cronExp, jobType, parameterSupplier, context);
            }
        };
    }

    protected void adjustThreadNameIfNeeds() { // because of too long name of cron4j
        final Thread currentThread = Thread.currentThread();
        final String adjustedName = "cron4j_" + Integer.toHexString(currentThread.hashCode());
        if (!adjustedName.equals(currentThread.getName())) { // first time
            currentThread.setName(adjustedName);
        }
    }

    protected void runJob(String cronExp, Class<? extends LaJob> jobType, Supplier<Map<String, Object>> parameterSupplier,
            TaskExecutionContext cron4jContext) {
        jobRunner.run(jobType, () -> createCron4jRuntime(cronExp, jobType, parameterSupplier, cron4jContext));
    }

    protected Cron4jRuntime createCron4jRuntime(String cronExp, Class<? extends LaJob> jobType,
            Supplier<Map<String, Object>> parameterSupplier, TaskExecutionContext cron4jContext) {
        return new Cron4jRuntime(cronExp, jobType, parameterSupplier.get(), cron4jContext);
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
