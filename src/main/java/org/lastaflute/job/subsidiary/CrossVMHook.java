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
    // public CrossVMState hookBeginning(ReadableJobAttr jobAttr, LocalDateTime beginTime) {
    //     OptionalThing<JobExecution> jobExecution = jobExecutionBhv.selectByUniqueOf(job.getJobUnique().get());
    //     if (jobExecution.isPresent()) { // concurrent
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
    //     JobExecution jobExecution = jobExecutionBhv.insert(...);
    //     return new CrossVMState().withAttribute("jobExecutionId", jobExecution.getJobExecutionId());
    // }
    // _/_/_/_/_/_/_/_/_/_/
    CrossVMState hookBeginning(ReadableJobAttr jobAttr, LocalDateTime beginTime);

    // e.g.
    // _/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/
    // public void hookEnding(ReadableJobAttr jobAttr, CrossVMState state) {
    //     Long jobExecutionId = state.getAttribute("jobExecutionId", Long.class).get();
    //     jobExecutionBhv.delete(...);
    // }
    // _/_/_/_/_/_/_/_/_/_/
    void hookEnding(ReadableJobAttr jobAttr, CrossVMState state, LocalDateTime endTime);
}
