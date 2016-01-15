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
import org.lastaflute.job.key.LaJobKey;
import org.lastaflute.job.key.LaJobUnique;
import org.lastaflute.job.subsidiary.CronOpCall;

/**
 * @author jflute
 * @since 0.2.0 (2016/01/11 Monday)
 */
public interface LaScheduledJob {

    // ===================================================================================
    //                                                                          Basic Info
    //                                                                          ==========
    /**
     * @return The job key auto-generated by framework when schedule registration. (NotNull)
     */
    LaJobKey getJobKey();

    /**
     * @return The optional job unique code provided by application when schedule registration. (NotNull, EmptyAllowed)
     */
    OptionalThing<LaJobUnique> getJobUnique();

    /**
     * @return The cron expression of the job e.g. '10 * * * *' (NotNull)
     */
    String getCronExp();

    /**
     * @return The type of job component for your application. (NotNull)
     */
    Class<? extends LaJob> getJobType();

    // ===================================================================================
    //                                                                            Behavior
    //                                                                            ========
    /**
     * Is the job actually executing now? <br>
     * If you want to stop it, use stopNow().
     * @return true if executing now.
     */
    boolean isExecutingNow();

    /**
     * Actually launch the job now, no-related to cron time. <br>
     * If executing job exists, the launched job is waiting for <br>
     * finishing the executing job. (you can change the behavior by option) <br>
     * You cannot call this if the job is closed. (throws exception)
     */
    void launchNow();

    /**
     * Stop the executing job by Thread.interrupt() and runtime.stopIfNeeds(). <br>
     * So it's not always true that the job is stoped. (needs call sleep(), stopIfNeeds(), ...) <br>
     * If executing job does not exist, do nothing. <br>
     * You can call this even if the job is closed. (might be exeucuting even if closed)
     */
    void stopNow();

    /**
     * Reschedule the job by the cron expression and options. <br>
     * If executing job exists, the process continues until finishing. <br>
     * New cron schedule is used since next execution.
     * @param cronExp The new cron expression of the job e.g. '10 * * * *' (NotNull)
     * @param opLambda The callback to setup option for e.g. parameter. (NotNull)
     */
    void reschedule(String cronExp, CronOpCall opLambda);

    /**
     * Unschedule the job, no more launched by cron and launghNow(). <br>
     * If the job is executing, the process continues until finishing. <br>
     * So call stopNow() if you want to stop it immediately. <br>
     * And you cannot find the job after unscheduling.
     */
    void unschedule();

    /**
     * @return true if the job is unscheduled.
     */
    boolean isUnscheduled();

    /**
     * This job becomes non-cron so the job will not executed by scheduler. <br>
     * You can only execute by launchNow(). <br>
     * And you can restore the job as normal cron by reschedule(). <br>
     * Do nothing if already non-cron.
     */
    void becomeNonCron();

    /**
     * @return true if the job is non-cron.
     */
    boolean isNonCron();
}
