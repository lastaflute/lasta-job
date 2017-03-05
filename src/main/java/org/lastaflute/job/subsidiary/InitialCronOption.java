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

import org.lastaflute.job.LaScheduledJob;

/**
 * @author jflute
 * @since 0.2.2 (2016/01/22 Friday at bay maihama)
 */
public interface InitialCronOption extends VaryingCronOption { // also varying to set varying options as first settings 

    // ===================================================================================
    //                                                                              Facade
    //                                                                              ======
    /**
     * @param jobTitle The title expression for the job. (NotNull)
     * @return this. (NotNull)
     */
    InitialCronOption title(String jobTitle);

    /**
     * @param jobDesc The description expression for the job. (NotNull)
     * @return this. (NotNull)
     */
    InitialCronOption desc(String jobDesc);

    /**
     * @param uniqueCode The unique code provided by application to identify job. (NotNull)
     * @return this. (NotNull)
     */
    InitialCronOption uniqueBy(String uniqueCode);

    /**
     * @param triggeringJob The job triggering me when success, means previous job (NotNull) 
     * @return this. (NotNull)
     */
    InitialCronOption triggeredBy(LaScheduledJob triggeringJob);
}
