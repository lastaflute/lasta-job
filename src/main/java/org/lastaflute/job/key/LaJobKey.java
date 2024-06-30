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
package org.lastaflute.job.key;

/**
 * @author jflute
 * @since 0.2.0 (2016/01/11 Monday)
 */
public class LaJobKey {

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    protected final String jobKey;

    // ===================================================================================
    //                                                                         Constructor
    //                                                                         ===========
    protected LaJobKey(String jobKey) {
        if (jobKey == null) {
            throw new IllegalArgumentException("The argument 'jobKey' should not be null.");
        }
        this.jobKey = jobKey;
    }

    public static LaJobKey of(String jobKey) {
        return new LaJobKey(jobKey);
    }

    // ===================================================================================
    //                                                                      Basic Override
    //                                                                      ==============
    @Override
    public int hashCode() {
        return jobKey.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof LaJobKey && jobKey.equals(((LaJobKey) obj).jobKey);
    }

    @Override
    public String toString() {
        return jobKey;
    }

    // ===================================================================================
    //                                                                            Accessor
    //                                                                            ========
    public String value() {
        return jobKey;
    }
}
