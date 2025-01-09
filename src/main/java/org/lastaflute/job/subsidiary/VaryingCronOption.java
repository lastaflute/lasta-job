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

import org.dbflute.optional.OptionalThing;
import org.lastaflute.job.log.JobNoticeLogLevel;

/**
 * @author jflute
 * @since 0.2.2 (2016/01/22 Friday at bay maihama)
 */
public interface VaryingCronOption {

    // ===================================================================================
    //                                                                              Facade
    //                                                                              ======
    // params()'s supplier should be executed just before the job execution as user specification
    // because, for example, users may use TimeManager@currentDate() in this callback
    /**
     * Set up job parameters to change job's behavior. <br>
     * For example, target date-time, search condition, option boolean.
     * <pre>
     * op.params(() -&gt; {
     *      return DfCollectionUtil.newHashMap("celebration", "plaza");
     * }));
     * </pre>
     * <p>The callback is executed just before the job execution.</p>
     * 
     * @param noArgLambda The callback to supply parameter map. (NotNull)
     * @return this. (NotNull)
     */
    VaryingCronOption params(CronParamsSupplier noArgLambda);

    VaryingCronOption changeNoticeLogToDebug();

    VaryingCronOption changeNoticeLogToSuppressed();

    VaryingCronOption grantOutlawParallel();

    // ===================================================================================
    //                                                                            Accessor
    //                                                                            ========
    OptionalThing<CronParamsSupplier> getParamsSupplier();

    JobNoticeLogLevel getNoticeLogLevel();

    boolean isOutlawParallelGranted();
}
