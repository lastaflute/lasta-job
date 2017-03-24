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

import org.dbflute.optional.OptionalThing;

/**
 * @author jflute
 * @since 0.2.7 (2017/02/22 Wednesday)
 */
public class RunnerResult {

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    protected final OptionalThing<EndTitleRoll> endTitleRoll; // not null, empty allowed
    protected final OptionalThing<Throwable> cause; // null allowed, already handled (e.g. logging)
    protected final boolean nextTriggerSuppressed; // by runtime
    protected final boolean quitByConcurrent; // by runner

    // ===================================================================================
    //                                                                         Constructor
    //                                                                         ===========
    protected RunnerResult(OptionalThing<EndTitleRoll> endTitleRoll, OptionalThing<Throwable> cause, boolean nextTriggerSuppressed,
            boolean quitByConcurrent) {
        this.endTitleRoll = endTitleRoll;
        this.cause = cause;
        this.nextTriggerSuppressed = nextTriggerSuppressed;
        this.quitByConcurrent = quitByConcurrent;
    }

    public static RunnerResult asExecuted(OptionalThing<EndTitleRoll> endTitleRoll, OptionalThing<Throwable> cause,
            boolean nextTriggerSuppressed) {
        return new RunnerResult(endTitleRoll, cause, nextTriggerSuppressed, false);
    }

    public static RunnerResult asQuitByConcurrent() {
        return new RunnerResult(OptionalThing.empty(), OptionalThing.empty(), false, true);
    }

    // ===================================================================================
    //                                                                            Accessor
    //                                                                            ========
    public OptionalThing<EndTitleRoll> getEndTitleRoll() {
        return endTitleRoll;
    }

    public OptionalThing<Throwable> getCause() {
        return cause;
    }

    public boolean isNextTriggerSuppressed() {
        return nextTriggerSuppressed;
    }

    public boolean isQuitByConcurrent() {
        return quitByConcurrent;
    }
}
