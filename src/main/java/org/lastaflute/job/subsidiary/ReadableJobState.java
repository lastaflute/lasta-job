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

import java.util.function.Consumer;
import java.util.function.Function;

import org.dbflute.optional.OptionalThing;
import org.dbflute.optional.OptionalThingIfPresentAfter;

/**
 * @author jflute
 * @since 0.4.1 (2017/03/24 Friday)
 */
public interface ReadableJobState extends ReadableJobAttr { // for internal assist

    /**
     * Call executing-now state if executing. <br>
     * You can use snapshot of executing state, e.g. begin-time of executing job.
     * @param oneArgLambda The callback for executing-now scope with snapshot of executing state. (NotNull)
     * @return The handler of after process when if not present. (NotNull)
     */
    OptionalThingIfPresentAfter ifExecutingNow(Consumer<SnapshotExecState> oneArgLambda);

    /**
     * Is the job actually executing now? <br>
     * If you want to stop it, use stopNow().
     * @return true if executing now.
     */
    boolean isExecutingNow();

    /**
     * Map executing-now state to something. <br>
     * You can use snapshot of executing state, e.g. begin-time of executing job.
     * @param oneArgLambda The callback for executing-now scope with snapshot of executing state. (NotNull)
     * @return The optional result from the callback. (NotNull, EmptyAllowed: if no executing or returns null)
     */
    <RESULT> OptionalThing<RESULT> mapExecutingNow(Function<SnapshotExecState, RESULT> oneArgLambda);

    /**
     * @return true if the job is unscheduled.
     */
    boolean isUnscheduled();

    /**
     * @return true if the job is disappeared.
     */
    boolean isDisappeared();

    /**
     * @return true if the job is non-cron.
     */
    boolean isNonCron();
}
