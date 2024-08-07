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
package org.lastaflute.job;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Consumer;

import org.dbflute.optional.OptionalThing;
import org.lastaflute.job.key.LaJobKey;
import org.lastaflute.job.key.LaJobNote;
import org.lastaflute.job.key.LaJobUnique;
import org.lastaflute.job.log.JobNoticeLogLevel;
import org.lastaflute.job.subsidiary.EndTitleRoll;

/**
 * @author jflute
 * @since 0.2.0 (2016/01/09 Saturday)
 */
public interface LaJobRuntime {

    // ===================================================================================
    //                                                                          Basic Info
    //                                                                          ==========
    LaJobKey getJobKey(); // not null

    OptionalThing<LaJobNote> getJobNote(); // not null

    OptionalThing<LaJobUnique> getJobUnique(); // not null

    String getCronExp(); // not null

    Class<? extends LaJob> getJobType(); // not null

    Method getRunMethod(); // not null

    /**
     * Get parameter map for the job, from both cron option and launch-now option.
     * @return The read-only map of your parameter. (NotNull)
     */
    Map<String, Object> getParameterMap(); // not null, read-only, business method

    JobNoticeLogLevel getNoticeLogLevel(); // not null

    // ===================================================================================
    //                                                                           Job State
    //                                                                           =========
    LocalDateTime getBeginTime();

    boolean isFrameworkDebug();

    // ===================================================================================
    //                                                                      End-Title Roll
    //                                                                      ==============
    OptionalThing<EndTitleRoll> getEndTitleRoll();

    /**
     * @param dataLambda The callback of end-title roll data for registration. (NotNull)
     */
    void showEndTitleRoll(Consumer<EndTitleRoll> dataLambda); // business method

    // ===================================================================================
    //                                                                            Stop Job
    //                                                                            ========
    void stopIfNeeds(); // exception if stopped, business method

    // ===================================================================================
    //                                                                    Business Failure
    //                                                                    ================
    void suppressNextTrigger(); // business method

    boolean isNextTriggerSuppressed();

    // ===================================================================================
    //                                                                             Display
    //                                                                             =======
    String toCronMethodDisp(); // not null

    String toRunMethodDisp(); // not null
}
