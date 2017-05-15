/*
 * Copyright 2015-2017 the original author or authors.
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
package org.lastaflute.job.subsidiary;

import java.time.LocalDateTime;

/**
 * @author jflute
 * @since 0.4.1 (2017/03/20 Monday)
 */
public interface CrossVMHook {

    // e.g.
    // _/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/
    // public CrossVMState hookBeginning(ReadableJobAttr jobAttr, LocalDateTime activationTime) {
    //     OptionalThing<JobExecControl> jobExecControl = jobExecControlBhv.selectByUniqueOf(job.getJobUnique().get());
    //     if (jobExecControl.isPresent()) { // concurrent
    //         ConcurrentJobStopper stopper = new ConcurrentJobStopper();
    //         return stopper.stopIfNeeds(...).map(result -> { // quit (or exception)
    //             return asQuitState();
    //         }).orElseGet(() -> { // wait
    //             waitForEndingBySomething();
    //             return asNormalState();
    //         });
    //     } else {
    //         return asNormalExecution();
    //     }
    // }
    // private CrossVMState asQuitState() {
    //     return new CrossVMState().asQuit();
    // }
    // private CrossVMState asNormalState() {
    //     JobExecControl jobExecControl = jobExecControlBhv.insert(...);
    //     return new CrossVMState().withAttribute("jobExecControlId", jobExecControl.getJobExecControlId());
    // }
    // _/_/_/_/_/_/_/_/_/_/
    // not jobState because jobAttr is enough to hook for self job
    /**
     * @param jobAttr The object that can provide attributes of job. (NotNull)
     * @param activationTime The date-time of job activation. (NotNull)
     * @return The state object of hook runtime, can be used at ending hook. (NotNull)
     */
    CrossVMState hookBeginning(ReadableJobAttr jobAttr, LocalDateTime activationTime);

    // e.g.
    // _/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/
    // public void hookEnding(ReadableJobAttr jobAttr, CrossVMState state) {
    //     Long jobExecControlId = state.getAttribute("jobExecControlId", Long.class).get();
    //     jobExecControlBhv.delete(...);
    // }
    // _/_/_/_/_/_/_/_/_/_/
    /**
     * @param jobAttr The object that can provide attributes of job. (NotNull)
     * @param state The state object of hook runtime, can be used at ending hook. (NotNull)
     * @param endTime The date-time of job ending. (NotNull)
     */
    void hookEnding(ReadableJobAttr jobAttr, CrossVMState state, LocalDateTime endTime);
}
