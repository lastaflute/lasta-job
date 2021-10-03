/*
 * Copyright 2015-2021 the original author or authors.
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

/**
 * @author jflute
 * @since 0.2.8 (2017/03/04 Saturday)
 */
public enum ExecResultType {

    SUCCESS(true, false) // no cause
    , QUIT_BY_CONCURRENT(false, false) // no execution as quit
    , ERROR_BY_CONCURRENT(false, true) // no execution as error
    , CAUSED_BY_APPLICATION(false, true) // exception thrown by application
    , CAUSED_BY_FRAMEWORK(false, true) // exception thrown by application
    ;

    private final boolean completeExecution;
    private final boolean errorResult;

    private ExecResultType(boolean completeExecution, boolean errorResult) {
        this.completeExecution = completeExecution;
        this.errorResult = errorResult;
    }

    public boolean isCompleteExecution() {
        return completeExecution;
    }

    public boolean isErrorResult() {
        return errorResult;
    }
}
