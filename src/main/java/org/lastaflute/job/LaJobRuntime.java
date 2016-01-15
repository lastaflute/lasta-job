/*
 * Copyright 2015-2016 the original author or authors.
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
import java.util.Map;
import java.util.function.Consumer;

import org.dbflute.optional.OptionalThing;
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
    String getCronExp(); // not null

    Class<? extends LaJob> getJobType(); // not null

    Method getRunMethod(); // not null

    Map<String, Object> getParameterMap(); // not null, read-only

    JobNoticeLogLevel getNoticeLogLevel(); // not null

    // ===================================================================================
    //                                                                      End-Title Roll
    //                                                                      ==============
    OptionalThing<EndTitleRoll> getEndTitleRoll();

    /**
     * @param dataLambda The callback of end-title roll data for registration. (NotNull)
     */
    void showEndTitleRoll(Consumer<EndTitleRoll> dataLambda);

    // ===================================================================================
    //                                                                            Stop Job
    //                                                                            ========
    void stopIfNeeds(); // exception if stopped

    // ===================================================================================
    //                                                                             Display
    //                                                                             =======
    String toCronMethodDisp(); // not null

    String toRunMethodDisp(); // not null
}
