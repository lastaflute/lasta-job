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
package org.lastaflute.job.log;

import org.dbflute.optional.OptionalThing;
import org.lastaflute.job.LaJobRuntime;
import org.lastaflute.job.subsidiary.ReadableJobAttr;

/**
 * @author jflute
 * @since 0.4.2 (2017/03/30 Thursday)
 */
public class JobErrorResource {

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    protected final OptionalThing<ReadableJobAttr> jobAttr; // not null, empty allowed when framework error or already unscheduled
    protected final OptionalThing<LaJobRuntime> runtime; // not null, empty allowed when framework error
    protected final String bigMessage; // not null
    protected final Throwable cause; // not null

    // ===================================================================================
    //                                                                         Constructor
    //                                                                         ===========
    @SuppressWarnings("unchecked")
    public JobErrorResource(OptionalThing<? extends ReadableJobAttr> jobAttr, OptionalThing<LaJobRuntime> runtime, String bigMessage,
            Throwable cause) {
        this.jobAttr = (OptionalThing<ReadableJobAttr>) jobAttr;
        this.runtime = runtime;
        this.bigMessage = bigMessage;
        this.cause = cause;
    }

    // ===================================================================================
    //                                                                            Accessor
    //                                                                            ========
    /**
     * @return The attribute provider of scheduled job. (NotNull, EmptyAllowed: when framework error or already unscheduled)
     */
    public OptionalThing<ReadableJobAttr> getJobAttr() {
        return jobAttr;
    }

    /**
     * @return The runtime of current job. (NotNull, EmptyAllowed: when framework error)
     */
    public OptionalThing<LaJobRuntime> getRuntime() {
        return runtime;
    }

    /**
     * @return The exception message that contains stack-trace. (NotNull)
     */
    public String getBigMessage() {
        return bigMessage;
    }

    /**
     * @return The exception instance. (NotNull)
     */
    public Throwable getCause() {
        return cause;
    }
}
