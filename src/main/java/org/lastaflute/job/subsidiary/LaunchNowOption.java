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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author jflute
 * @since 0.4.6 (2017/05/01 Monday)
 */
public class LaunchNowOption {

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    protected Map<String, Object> parameterMap; // lazy-loaded
    protected boolean priorParams;
    protected boolean outlawParallel;

    // ===================================================================================
    //                                                                           Parameter
    //                                                                           =========
    /**
     * Register parameter for job. <br>
     * Job can get from runtime as parameter map.
     * @param key The key of parameter. (NotNull)
     * @param value The value as parameter. (NullAllowed: parameter value can be null)
     * @return this. (NotNull)
     */
    public LaunchNowOption param(String key, Object value) {
        if (key == null) {
            throw new IllegalArgumentException("The argument 'key' should not be null.");
        }
        if (parameterMap == null) {
            parameterMap = new LinkedHashMap<String, Object>();
        }
        parameterMap.put(key, value);
        return this;
    }

    /**
     * Set up as prior parameters. <br>
     * It means parameters by launch-now can override same-key parameters by cron option.
     * @return this. (NotNull)
     */
    public LaunchNowOption asPriorParams() {
        priorParams = true;
        return this;
    }

    /**
     * Set up as outlaw parallel launch, ignoring concurrent type setting of the job. <br>
     * And this is for non-cron job only. You cannot schedule the job if you use this. <br>
     * <span style="color: #AD4747; font-size: 120%">This is trial function, so be careful with your use.</span>
     * @return this. (NotNull)
     */
    public LaunchNowOption asOutlawParallel() {
        outlawParallel = true;
        return this;
    }

    // ===================================================================================
    //                                                                      Basic Override
    //                                                                      ==============
    @Override
    public String toString() {
        final String priorParamsExp = priorParams ? ", priorParams" : "";
        final String outlawParallelExp = outlawParallel ? ", outlawParallel" : "";
        return "option:{params=" + parameterMap + priorParamsExp + outlawParallelExp + "}";
    }

    // ===================================================================================
    //                                                                            Accessor
    //                                                                            ========
    public Map<String, Object> getParameterMap() { // read-only
        return parameterMap != null ? Collections.unmodifiableMap(parameterMap) : Collections.emptyMap();
    }

    public boolean isPriorParams() {
        return priorParams;
    }

    public boolean isOutlawParallel() {
        return outlawParallel;
    }
}
