/*
 * Copyright 2015-2018 the original author or authors.
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
package org.lastaflute.job.mock;

import it.sauronsoftware.cron4j.Scheduler;
import it.sauronsoftware.cron4j.TaskExecutionContext;
import it.sauronsoftware.cron4j.TaskExecutor;

/**
 * @author jflute
 * @since 0.2.0 (2016/01/15 Friday)
 */
public class MockTaskExecutionContext implements TaskExecutionContext {

    protected final Scheduler scheduler;
    protected final TaskExecutor taskExecutor;

    public MockTaskExecutionContext(Scheduler scheduler, TaskExecutor taskExecutor) {
        this.scheduler = scheduler;
        this.taskExecutor = taskExecutor;
    }

    @Override
    public Scheduler getScheduler() {
        return new Scheduler();
    }

    @Override
    public TaskExecutor getTaskExecutor() {
        return taskExecutor;
    }

    @Override
    public void setStatusMessage(String message) {
    }

    @Override
    public void setCompleteness(double completeness) {
    }

    @Override
    public void pauseIfRequested() {
    }

    @Override
    public boolean isStopped() {
        return false;
    }
}
