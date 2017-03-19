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

import java.util.function.Supplier;

import org.dbflute.helper.function.IndependentProcessor;
import org.dbflute.optional.OptionalThing;
import org.lastaflute.job.LaJobHistory;
import org.lastaflute.job.LaScheduledJob;

/**
 * @author jflute
 * @since 0.2.8 (2017/03/04 Saturday)
 */
public class LaunchedProcess {

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    protected final LaScheduledJob scheduledJob; // not null
    protected final IndependentProcessor jobEndingWaiter; // not null
    protected final Supplier<OptionalThing<LaJobHistory>> jobHistoryFinder; // not null

    // ===================================================================================
    //                                                                         Constructor
    //                                                                         ===========
    public LaunchedProcess(LaScheduledJob scheduledJob, IndependentProcessor jobEndingWaiter,
            Supplier<OptionalThing<LaJobHistory>> jobHistoryFinder) {
        this.scheduledJob = scheduledJob;
        this.jobEndingWaiter = jobEndingWaiter;
        this.jobHistoryFinder = jobHistoryFinder;
    }

    // ===================================================================================
    //                                                                              Facade
    //                                                                              ======
    /**
     * Wait for ending of the job. <br>
     * (current thread waits for closing job thread)
     * @return The optional job history of the launched job. (NotNull, EmptyAllowed: when too old process)
     */
    public OptionalThing<LaJobHistory> waitForEnding() {
        // no logging here, caller application should determine log level
        jobEndingWaiter.process();
        return jobHistoryFinder.get();
    }

    // ===================================================================================
    //                                                                            Accessor
    //                                                                            ========
    public LaScheduledJob getScheduledJob() {
        return scheduledJob;
    }
}
