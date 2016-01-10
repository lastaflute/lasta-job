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

/**
 * @author jflute
 * @since 0.2.0 (2016/01/09 Saturday)
 */
public interface LaCron {

    /**
     * Register job with scheduling.
     * <pre>
     * cron.register("* * * * *", () -&gt; SeaJob.class); // per one minute
     * </pre>
     * @param cronExp The cron expression e.g. "10 * * * *". (NotNull)
     * @param jobType The type of registered job that implements the provided interface. (NotNull)
     * @return The registered job which is scheduled by the cron. (NotNull)
     */
    LaScheduledJob register(String cronExp, Class<? extends LaJob> jobType);

    /**
     * Register job with scheduling.
     * <pre>
     * cron.register("* * * * *", () -&gt; SeaJob.class); // per one minute
     * </pre>
     * @param cronExp The cron expression e.g. "10 * * * *". (NotNull)
     * @param jobType The type of registered job that implements the provided interface. (NotNull)
     * @param noArgLambda The supplier of job parameter. (NotNull)
     * @return The registered job which is scheduled by the cron. (NotNull)
     */
    LaScheduledJob register(String cronExp, Class<? extends LaJob> jobType, Supplier<Map<String, Object>> noArgLambda);
}
