/*
 * Copyright 2015-2019 the original author or authors.
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.lastaflute.job.subsidiary.LaunchNowOption;

import it.sauronsoftware.cron4j.RomanticCron4jNativeScheduler;
import it.sauronsoftware.cron4j.Scheduler;
import it.sauronsoftware.cron4j.TaskExecutor;

/**
 * @author jflute
 * @since 0.2.0 (2016/01/11 Monday)
 */
public class Cron4jScheduler {

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    protected final RomanticCron4jNativeScheduler nativeScheduler;

    // ===================================================================================
    //                                                                         Constructor
    //                                                                         ===========
    public Cron4jScheduler(RomanticCron4jNativeScheduler cron4jScheduler) {
        this.nativeScheduler = cron4jScheduler;
    }

    // ===================================================================================
    //                                                                              Facade
    //                                                                              ======
    public void start() {
        nativeScheduler.start();
    }

    public String schedule(String cronExp, Cron4jTask cron4jTask) {
        return nativeScheduler.schedule(cronExp, cron4jTask);
    }

    public void reschedule(Cron4jId cron4jId, String cronExp) {
        nativeScheduler.reschedule(cron4jId.value(), cronExp);
    }

    public void deschedule(Cron4jId cron4jId) {
        nativeScheduler.deschedule(cron4jId.value());
    }

    public List<TaskExecutor> findExecutorList(Cron4jTask cron4jTask) {
        return getExecutorList().stream().filter(executor -> {
            return cron4jTask.equals(executor.getTask());
        }).collect(Collectors.toList());
    }

    public List<TaskExecutor> getExecutorList() {
        return Stream.of(nativeScheduler.getExecutingTasks()).collect(Collectors.toList());
    }

    // unused because of launch-now option
    //public TaskExecutor launch(Cron4jTask cron4jTask) {
    //    return nativeScheduler.launch(cron4jTask);
    //}

    public TaskExecutor launchNow(Cron4jTask cron4jTask, LaunchNowOption option) {
        return nativeScheduler.launchNow(cron4jTask, option);
    }

    public void stop() {
        nativeScheduler.stop();
    }

    // ===================================================================================
    //                                                                            Accessor
    //                                                                            ========
    public Scheduler getNativeScheduler() {
        return nativeScheduler;
    }
}
