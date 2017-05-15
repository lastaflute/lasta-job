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

import org.lastaflute.job.exception.JobConcurrentlyExecutingException;
import org.lastaflute.job.exception.JobNeighborConcurrentlyExecutingException;

/**
 * @author jflute
 * @since 0.4.1 (2017/03/20 Monday)
 */
public interface CrossVMHook {

    // e.g.
    // _/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/
    // public CrossVMState hookBeginning(ReadableJobState jobState, LocalDateTime activationTime) {
    //     ConcurrentJobStopper stopper = createConcurrentJobStopper();
    //     return stopper.stopIfNeeds(jobState, ...).map(result -> { // quit (or exception)
    //         return asQuitState();
    //     }).orElseGet(() -> {
    //         return asNormalState();
    //     });
    // }
    // private ConcurrentJobStopper createConcurrentJobStopper() {
    //     return new ConcurrentJobStopper(jobState -> {
    //         return jobExecControlBhv.selectCount(cb -> {
    //             cb.query().setJobUnique(job.getJobUnique().get());
    //         }) > 0;
    //     }).waitress(jobState -> waitForEndingBySomething(jobState));
    // });
    // private CrossVMState asQuitState() {
    //     return new CrossVMState().asQuit();
    // }
    // private CrossVMState asNormalState() {
    //     JobExecControl jobExecControl = new JobExecControl();
    //     ...
    //     jobExecControlBhv.insert(jobExecControl);
    //     return new CrossVMState().withAttribute("jobExecControlId", jobExecControl.getJobExecControlId());
    // }
    // _/_/_/_/_/_/_/_/_/_/
    // not jobState because jobAttr is enough to hook for self job
    /**
     * Hook beginning for cross VM handling.
     * @param jobState The object that can provide attributes and states of job. (NotNull)
     * @param activationTime The date-time of job activation. (NotNull)
     * @return The state object of hook runtime, can be used at ending hook. (NotNull)
     * @throws JobConcurrentlyExecutingException When the job is duplicate boot.
     * @throws JobNeighborConcurrentlyExecutingException When the neightbor job is boot.
     */
    CrossVMState hookBeginning(ReadableJobState jobState, LocalDateTime activationTime);

    // e.g.
    // _/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/
    // public void hookEnding(ReadableJobState jobState, CrossVMState crossVMState, LocalDateTime endTime) {
    //     Long jobExecControlId = crossVMState.getAttribute("jobExecControlId", Long.class).get();
    //     jobExecControlBhv.delete(...);
    // }
    // _/_/_/_/_/_/_/_/_/_/
    /**
     * Hook ending for cross VM handling. <br>
     * Cannot be called if quit and error for concurrent. <br>
     * While, can be called if exception from job execution.
     * @param jobState The object that can provide attributes and states of job. (NotNull)
     * @param crossVMState The state object of hook runtime, can be used at ending hook. (NotNull)
     * @param endTime The date-time of job ending. (NotNull)
     */
    void hookEnding(ReadableJobState jobState, CrossVMState crossVMState, LocalDateTime endTime);
}
