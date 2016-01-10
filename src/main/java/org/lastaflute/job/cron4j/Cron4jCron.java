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

import java.util.function.Supplier;

import org.lastaflute.job.LaCron;
import org.lastaflute.job.LaJob;
import org.lastaflute.job.LaJobRunner;
import org.lastaflute.job.LaJobRuntime;

import it.sauronsoftware.cron4j.Scheduler;
import it.sauronsoftware.cron4j.Task;
import it.sauronsoftware.cron4j.TaskExecutionContext;

/**
 * @author jflute
 * @since 0.2.0 (2016/01/09 Saturday)
 */
public class Cron4jCron implements LaCron {

    protected final Scheduler scheduler;
    protected final LaJobRunner runner;

    public Cron4jCron(Scheduler scheduler, LaJobRunner runner) {
        this.scheduler = scheduler;
        this.runner = runner;
    }

    @Override
    public void register(String cronExp, Supplier<Class<? extends LaJob>> noArgInLambda) {
        if (cronExp == null) {
            throw new IllegalArgumentException("The argument 'cronExp' should not be null.");
        }
        if (noArgInLambda == null) {
            throw new IllegalArgumentException("The argument 'noArgInLambda' should not be null.");
        }
        scheduler.schedule(cronExp, createCron4jTask(noArgInLambda));
    }

    protected Task createCron4jTask(Supplier<Class<? extends LaJob>> jobTypeSupplier) {
        return new Task() { // adapter task
            public void execute(TaskExecutionContext context) throws RuntimeException {
                adjustThreadNameIfNeeds();
                runJob(jobTypeSupplier, context);
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

    protected void runJob(Supplier<Class<? extends LaJob>> jobTypeSupplier, TaskExecutionContext cron4jContext) {
        runner.run(jobTypeSupplier, jobType -> createJobRuntime(jobType, cron4jContext));
    }

    protected LaJobRuntime createJobRuntime(Class<? extends LaJob> jobType, TaskExecutionContext cron4jContext) {
        return new Cron4jJobRuntime(jobType, cron4jContext);
    }
}
