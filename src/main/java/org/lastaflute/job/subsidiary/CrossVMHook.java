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
package org.lastaflute.job.subsidiary;

import java.time.LocalDateTime;

import org.lastaflute.job.exception.JobConcurrentlyExecutingException;
import org.lastaflute.job.exception.JobNeighborConcurrentlyExecutingException;

/**
 * @author jflute
 * @since 0.4.1 (2017/03/20 Monday)
 */
public interface CrossVMHook {

    // sea ConcurrentCrossVMHook for the implementation example
    /**
     * Hook beginning for cross VM handling.
     * @param jobState The object that can provide attributes and states of job. (NotNull)
     * @param activationTime The date-time of job activation. (NotNull)
     * @return The state object of hook runtime, can be used at ending hook. (NotNull)
     * @throws JobConcurrentlyExecutingException When the job is duplicate boot.
     * @throws JobNeighborConcurrentlyExecutingException When the neightbor job is boot.
     */
    CrossVMState hookBeginning(ReadableJobState jobState, LocalDateTime activationTime);

    /**
     * Hook ending for cross VM handling. <br>
     * Cannot be called if quit and error for concurrent. <br>
     * While, can be called if exception from job execution.
     * @param jobState The object that can provide attributes and states of job. (NotNull)
     * @param crossVMState The state object of hook runtime, can be used at ending hook. (NotNull)
     * @param endTime The date-time of job ending. (NotNull)
     */
    void hookEnding(ReadableJobState jobState, CrossVMState crossVMState, LocalDateTime endTime);

    /**
     * Does it suppress notice log of the hook?
     * @return The determination, true or false.
     */
    default boolean suppressesNoticeLog() { // as you like it
        return false;
    }
}
