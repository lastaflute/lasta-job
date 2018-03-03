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
package org.lastaflute.job;

import org.lastaflute.job.subsidiary.InitialCronOpCall;
import org.lastaflute.job.subsidiary.JobConcurrentExec;
import org.lastaflute.job.subsidiary.RegisteredJob;

/**
 * @author jflute
 * @since 0.2.0 (2016/01/09 Saturday)
 */
public interface LaCron {

    /** The prefix of thread name when executing job */
    String THREAD_NAME_PREFIX = "job_"; // e.g. job_sea or job_G736FJD4

    /**
     * Register job with scheduling with option.
     * <pre>
     * cron.register("* * * * *", SeaJob.class, waitIfConcurrent(), op -&gt; op.params(...)); // per one minute
     * </pre>
     * @param cronExp The cron expression e.g. "10 * * * *". (NotNull)
     * @param jobType The type of registered job that implements the provided interface. (NotNull)
     * @param concurrentExec The handling type when concurrent execution of same job. (NotNull)
     * @param opLambda The callback to setup option for e.g. parameter. (NotNull)
     * @return The registered job which is scheduled by the cron. (NotNull)
     */
    RegisteredJob register(String cronExp, Class<? extends LaJob> jobType, JobConcurrentExec concurrentExec, InitialCronOpCall opLambda);

    /**
     * Register non-cron job with scheduling with option.
     * <pre>
     * cron.registerNonCron(SeaJob.class, waitIfConcurrent(), op -&gt; op.params(...)); // per one minute
     * </pre>
     * @param jobType The type of registered job that implements the provided interface. (NotNull)
     * @param concurrentExec The handling type when concurrent execution of same job. (NotNull)
     * @param opLambda The callback to setup option for e.g. parameter. (NotNull)
     * @return The registered job which is scheduled by the cron. (NotNull)
     */
    RegisteredJob registerNonCron(Class<? extends LaJob> jobType, JobConcurrentExec concurrentExec, InitialCronOpCall opLambda);

    /**
     * Set up neighbor concurrent control.
     * <pre>
     * <span style="color: #3F7E5E">// cannot executes LandJob if SeaJob is running and vice versa</span>
     * cron.setupNeighborConcurrent(errorIfConcurrent(), seaJob, landJob);
     * </pre>
     * @param groupName The unique name of neighbor concurrent group. (NotNull, NotEmpty)
     * @param concurrentExec The handling type when concurrent execution of neighbor job. (NotNull)
     * @param jobs The array of job. (NotNull)
     */
    void setupNeighborConcurrent(String groupName, JobConcurrentExec concurrentExec, RegisteredJob... jobs);
}
