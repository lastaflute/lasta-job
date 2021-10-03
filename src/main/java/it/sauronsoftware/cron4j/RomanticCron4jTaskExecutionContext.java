/*
 * Copyright 2015-2021 the original author or authors.
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
package it.sauronsoftware.cron4j;

import org.dbflute.optional.OptionalThing;
import org.lastaflute.job.subsidiary.LaunchNowOption;

/**
 * @author jflute
 * @since 0.4.6 (2017/05/01 Monday at rainbow bird rendezvous)
 */
public class RomanticCron4jTaskExecutionContext implements TaskExecutionContext {

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    protected final TaskExecutionContext nativeContext; // not null
    protected final OptionalThing<LaunchNowOption> nowOption; // not null

    // ===================================================================================
    //                                                                         Constructor
    //                                                                         ===========
    public RomanticCron4jTaskExecutionContext(TaskExecutionContext nativeContext, OptionalThing<LaunchNowOption> nowOption) {
        this.nativeContext = nativeContext;
        this.nowOption = nowOption;
    }

    // ===================================================================================
    //                                                                           Delegator
    //                                                                           =========
    @Override
    public Scheduler getScheduler() {
        return nativeContext.getScheduler();
    }

    @Override
    public TaskExecutor getTaskExecutor() {
        return nativeContext.getTaskExecutor();
    }

    @Override
    public void setStatusMessage(String message) {
        nativeContext.setStatusMessage(message);
    }

    @Override
    public void setCompleteness(double completeness) {
        nativeContext.setCompleteness(completeness);
    }

    @Override
    public void pauseIfRequested() {
        nativeContext.pauseIfRequested();
    }

    @Override
    public boolean isStopped() {
        return nativeContext.isStopped();
    }

    // ===================================================================================
    //                                                                            Accessor
    //                                                                            ========
    public TaskExecutionContext getNativeContext() {
        return nativeContext;
    }

    public OptionalThing<LaunchNowOption> getLaunchNowOption() {
        return nowOption;
    }
}
