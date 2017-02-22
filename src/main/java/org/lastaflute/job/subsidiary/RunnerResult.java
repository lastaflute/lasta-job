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
package org.lastaflute.job.subsidiary;

import org.dbflute.optional.OptionalThing;

/**
 * @author jflute
 * @since 0.2.7 (2017/02/22 Wednesday)
 */
public class RunnerResult {

    protected final boolean success;
    protected final Throwable cause; // null allowed

    public RunnerResult(boolean success, Throwable cause) {
        this.success = success;
        this.cause = cause;
    }

    public boolean isSuccess() {
        return success;
    }

    public OptionalThing<Throwable> getCause() {
        return OptionalThing.ofNullable(cause, () -> {
            throw new IllegalStateException("Not found the cause.");
        });
    }
}
