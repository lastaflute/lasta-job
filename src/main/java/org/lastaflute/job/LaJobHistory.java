/*
 * Copyright 2015-2021 the original author or authors.
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
package org.lastaflute.job;

import java.time.LocalDateTime;
import java.util.Map;

import org.dbflute.optional.OptionalThing;
import org.lastaflute.job.key.LaJobKey;
import org.lastaflute.job.key.LaJobNote;
import org.lastaflute.job.key.LaJobUnique;
import org.lastaflute.job.subsidiary.ExecResultType;

/**
 * @author jflute
 * @since 0.2.8 (2017/03/04 Saturday)
 */
public interface LaJobHistory {

    // ===================================================================================
    //                                                                       Job Attribute
    //                                                                       =============
    /**
     * @return The key of job, managed by framework. (NotNull)
     */
    LaJobKey getJobKey();

    /**
     * @return The optional note of job, e.g. title, description. (NotNull, EmptyAllowed: not required)
     */
    OptionalThing<LaJobNote> getJobNote();

    /**
     * @return The optional unique code of job, specified by application. (NotNull, EmptyAllowed: not required)
     */
    OptionalThing<LaJobUnique> getJobUnique();

    /**
     * @return The optional cron expression of job. (NotNull, EmptyAllowed: if non-cron)
     */
    OptionalThing<String> getCronExp(); // varying so this is snapshot

    /**
     * @return The full qualified class name of job type. (NotNull)
     */
    String getJobTypeFqcn();

    // ===================================================================================
    //                                                                    Execution Result
    //                                                                    ================
    /**
     * @return The local date-time of job activation. (NotNull: exists even if not begun)
     */
    LocalDateTime getActivationTime();

    /**
     * @return The optional local date-time of job beginning. (NotNull, EmptyAllowed: e.g. quit by duplicate)
     */
    OptionalThing<LocalDateTime> getBeginTime();

    /**
     * @return The optional local date-time of job ending. (NotNull, EmptyAllowed: e.g. quit by duplicate)
     */
    OptionalThing<LocalDateTime> getEndTime();

    /**
     * @return The type of execution result, e.g. SUCCESS, QUIT_BY_CONCURRENT (NotNull)
     */
    ExecResultType getExecResultType();

    /**
     * @return The read-only and snapshot map of end-title-roll. (NotNull, EmptyAllowed)
     */
    Map<String, String> getEndTitleRollSnapshotMap(); // not to keep application instance

    /**
     * @return The optional exception of failure cause. (NotNull, EmptyAllowed: if success)
     */
    OptionalThing<Throwable> getCause();
}
