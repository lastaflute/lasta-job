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

import java.util.Map;
import java.util.function.Supplier;

import org.dbflute.optional.OptionalThing;

/**
 * @author jflute
 * @since 0.2.0 (2016/01/11 Monday)
 */
public class LaCronOption {

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    protected Supplier<Map<String, Object>> paramsSupplier;
    protected AlreadyExecutingBehavior duplicateExecutingBehavior = AlreadyExecutingBehavior.WAIT;

    public enum AlreadyExecutingBehavior {
        WAIT, SILENTLY_QUIT, SYSTEM_EXCEPTION
    }

    // ===================================================================================
    //                                                                              Facade
    //                                                                              ======
    public LaCronOption params(Supplier<Map<String, Object>> noArgLambda) {
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
        final String paramsExp = paramsSupplier != null ? "hasParams" : "noParams";
        return "option:{" + paramsExp + ", " + duplicateExecutingBehavior + "}";
    }

    // ===================================================================================
    //                                                                            Accessor
    //                                                                            ========
    public OptionalThing<Supplier<Map<String, Object>>> getParamsSupplier() {
        return OptionalThing.ofNullable(paramsSupplier, () -> {
            throw new IllegalStateException("Not found the parameters supplier.");
        });
    }

    public AlreadyExecutingBehavior getAlreadyExecutingBehavior() {
        return duplicateExecutingBehavior;
    }
}
