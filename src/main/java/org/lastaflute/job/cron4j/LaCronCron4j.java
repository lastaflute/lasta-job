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
import org.lastaflute.job.LaJobContext;
import org.lastaflute.job.LaJobRunner;

import it.sauronsoftware.cron4j.Scheduler;
import it.sauronsoftware.cron4j.Task;
import it.sauronsoftware.cron4j.TaskExecutionContext;

/**
 * @author jflute
 * @since 0.1.0 (2016/01/09 Saturday)
 */
public class LaCronCron4j implements LaCron {

    protected final Scheduler scheduler;

    public LaCronCron4j(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public void register(String cronExp, Supplier<Class<? extends LaJob>> noArgInLambda) {
        scheduler.schedule(cronExp, createCron4jTask(noArgInLambda));
    }

    protected Task createCron4jTask(Supplier<Class<? extends LaJob>> jobTypeSupplier) {
        return new Task() { // adapter task
            public void execute(TaskExecutionContext context) throws RuntimeException {
                runJob(jobTypeSupplier, context);
            }
        };
    }

    protected void runJob(Supplier<Class<? extends LaJob>> jobTypeSupplier, TaskExecutionContext context) {
        createJobRunner(jobTypeSupplier, context).run();
    }

    protected LaJobRunner createJobRunner(Supplier<Class<? extends LaJob>> jobTypeSupplier, TaskExecutionContext context) {
        return new LaJobRunner(jobTypeSupplier, createContext(context));
    }

    protected LaJobContext createContext(TaskExecutionContext context) {
        return new LaJobContextCron4j(context);
    }
}
