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
import java.util.function.Supplier;

import org.dbflute.optional.OptionalThing;

/**
 * @author jflute
 * @since 0.4.1 (2017/03/25 Saturday)
 */
public class TaskRunningState {

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    protected final Supplier<LocalDateTime> currentTime; // not null
    protected volatile LocalDateTime beginTime; // null allowed when no executing, volatile just in case

    // ===================================================================================
    //                                                                         Constructor
    //                                                                         ===========
    public TaskRunningState(Supplier<LocalDateTime> currentTime) {
        this.currentTime = currentTime;
    }

    // ===================================================================================
    //                                                                        Change State
    //                                                                        ============
    public void begin() {
        this.beginTime = currentTime.get();
    }

    public void end() {
        this.beginTime = null;
    }

    // ===================================================================================
    //                                                                      Basic Override
    //                                                                      ==============
    @Override
    public String toString() {
        return "runningState:{" + beginTime + "}";
    }

    // ===================================================================================
    //                                                                            Accessor
    //                                                                            ========
    public OptionalThing<LocalDateTime> getBeginTime() { // running if present
        return OptionalThing.ofNullable(beginTime, () -> {
            throw new IllegalStateException("Not found the beginTime.");
        });
    }
}
