/*
 * Copyright 2015-2018 the original author or authors.
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
public class LaJobUnique {

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    protected final String uniqueCode;

    // ===================================================================================
    //                                                                         Constructor
    //                                                                         ===========
    protected LaJobUnique(String uniqueCode) {
        if (uniqueCode == null) {
            throw new IllegalArgumentException("The argument 'uniqueCode' should not be null.");
        }
        this.uniqueCode = uniqueCode;
    }

    public static LaJobUnique of(String uniqueCode) {
        return new LaJobUnique(uniqueCode);
    }

    // ===================================================================================
    //                                                                      Basic Override
    //                                                                      ==============
    @Override
    public int hashCode() {
        return uniqueCode.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof LaJobUnique && uniqueCode.equals(((LaJobUnique) obj).uniqueCode);
    }

    @Override
    public String toString() {
        return uniqueCode;
    }

    // ===================================================================================
    //                                                                            Accessor
    //                                                                            ========
    public String value() {
        return uniqueCode;
    }
}
