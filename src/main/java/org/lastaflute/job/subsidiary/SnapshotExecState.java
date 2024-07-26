/*
 * Copyright 2015-2024 the original author or authors.
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
 * @since 0.4.1 (2017/03/25 Saturday)
 */
public class SnapshotExecState {

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    protected final LocalDateTime beginTime; // not null

    // ===================================================================================
    //                                                                         Constructor
    //                                                                         ===========
    public SnapshotExecState(LocalDateTime beginTime) {
        this.beginTime = beginTime;
    }

    // ===================================================================================
    //                                                                      Basic Override
    //                                                                      ==============
    @Override
    public String toString() {
        return "state:{" + beginTime + "}";
    }

    // ===================================================================================
    //                                                                            Accessor
    //                                                                            ========
    public LocalDateTime getBeginTime() {
        return beginTime;
    }
}
