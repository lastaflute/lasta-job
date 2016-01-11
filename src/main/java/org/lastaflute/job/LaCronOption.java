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

import org.dbflute.optional.OptionalThing;
import org.lastaflute.job.key.LaJobUniqueCode;
import org.lastaflute.job.subsidiary.ParamsSupplier;

/**
 * @author jflute
 * @since 0.2.0 (2016/01/11 Monday)
 */
public class LaCronOption {

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    protected LaJobUniqueCode uniqueCode;
    protected ParamsSupplier paramsSupplier;
    protected AlreadyExecutingBehavior duplicateExecutingBehavior = AlreadyExecutingBehavior.WAIT;

    public enum AlreadyExecutingBehavior {
        WAIT, SILENTLY_QUIT, SYSTEM_EXCEPTION
    }

    // ===================================================================================
    //                                                                              Facade
    //                                                                              ======
    public LaCronOption uniqueBy(String uniqueCode) {
        if (uniqueCode == null) {
            throw new IllegalArgumentException("The argument 'uniqueCode' should not be null.");
        }
        this.uniqueCode = createUniqueCode(uniqueCode);
        return this;
    }

    protected LaJobUniqueCode createUniqueCode(String uniqueCode) {
        return new LaJobUniqueCode(uniqueCode);
    }

    public LaCronOption params(ParamsSupplier noArgLambda) {
        if (noArgLambda == null) {
            throw new IllegalArgumentException("The argument 'noArgLambda' should not be null.");
        }
        paramsSupplier = noArgLambda;
        return this;
    }

    public LaCronOption silentlyQuitIfAlreadyExecuting() {
        duplicateExecutingBehavior = AlreadyExecutingBehavior.SILENTLY_QUIT;
        return this;
    }

    public LaCronOption systemExceptionIfAlreadyExecuting() {
        duplicateExecutingBehavior = AlreadyExecutingBehavior.SYSTEM_EXCEPTION;
        return this;
    }

    // ===================================================================================
    //                                                                      Basic Override
    //                                                                      ==============
    @Override
    public String toString() {
        final String uniqueExp = uniqueCode != null ? uniqueCode.value() : "noUnique";
        final String paramsExp = paramsSupplier != null ? "hasParams" : "noParams";
        return "option:{" + uniqueExp + ", " + paramsExp + ", " + duplicateExecutingBehavior + "}";
    }

    // ===================================================================================
    //                                                                            Accessor
    //                                                                            ========
    public OptionalThing<LaJobUniqueCode> getUniqueCode() {
        return OptionalThing.ofNullable(uniqueCode, () -> {
            throw new IllegalStateException("Not found the application unique code.");
        });
    }

    public OptionalThing<ParamsSupplier> getParamsSupplier() {
        return OptionalThing.ofNullable(paramsSupplier, () -> {
            throw new IllegalStateException("Not found the parameters supplier.");
        });
    }

    public AlreadyExecutingBehavior getAlreadyExecutingBehavior() {
        return duplicateExecutingBehavior;
    }
}
