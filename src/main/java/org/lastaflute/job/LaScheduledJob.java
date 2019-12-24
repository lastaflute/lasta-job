/*
 * Copyright 2015-2019 the original author or authors.
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

import org.lastaflute.job.exception.JobAlreadyDisappearedException;
import org.lastaflute.job.exception.JobAlreadyUnscheduleException;
import org.lastaflute.job.key.LaJobKey;
import org.lastaflute.job.subsidiary.LaunchNowOpCall;
import org.lastaflute.job.subsidiary.LaunchedProcess;
import org.lastaflute.job.subsidiary.ReadableJobAttr;
import org.lastaflute.job.subsidiary.ReadableJobState;
import org.lastaflute.job.subsidiary.RegisteredJob;
import org.lastaflute.job.subsidiary.VaryingCronOpCall;

/**
 * @author jflute
 * @since 0.2.0 (2016/01/11 Monday)
 */
public interface LaScheduledJob extends ReadableJobAttr, ReadableJobState, RegisteredJob {

    // ===================================================================================
    //                                                                          Scheduling
    //                                                                          ==========
    /**
     * Actually launch the job now (at other thread), no-related to cron time. <br>
     * If executing job exists, the launched job is waiting for <br>
     * finishing the executing job. (you can change the behavior by option)
     * @return The launched process of the job. (NotNull)
     * @throws JobAlreadyDisappearedException When the job is already disappeared.
     * @throws JobAlreadyUnscheduleException When the job is already unscheduled.
     */
    LaunchedProcess launchNow();

    /**
     * Actually launch the job now (at other thread), no-related to cron time. <br>
     * If executing job exists, the launched job is waiting for <br>
     * finishing the executing job. (you can change the behavior by option) <br>
     * And you can set parameter by option, which launched job can directly use by runtime.
     * @param opLambda The callback for option of launch-now, e.g. parameter. (NotNull)
     * @return The launched process of the job. (NotNull)
     * @throws JobAlreadyDisappearedException When the job is already disappeared.
     * @throws JobAlreadyUnscheduleException When the job is already unscheduled.
     */
    LaunchedProcess launchNow(LaunchNowOpCall opLambda);

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
     * The new cron schedule is used since next execution. <br>
     * You can also reschedule from unscheduled job.
     * <pre>
     * x unschedule() to launched by, launchNow()
     * o unschedule() to find...(), get...()
     * o unschedule() to reschedule()
     * o unschedule() to disappear()
     * x disappear() to launched by, launchNow()
     * x disappear() to find...(), get...()
     * x disappear() to reschedule()
     * x disappear() to unschedule()
     * </pre>
     * @param cronExp The new cron expression of the job e.g. '10 * * * *' (NotNull)
     * @param opLambda The callback to setup varying option for e.g. parameter. (NotNull)
     * @throws JobAlreadyDisappearedException When the job is already disappeared.
     */
    void reschedule(String cronExp, VaryingCronOpCall opLambda);

    /**
     * Unschedule the job, no more launched by cron and launghNow(). <br>
     * If the job is executing, the process continues until finishing. <br>
     * So call stopNow() if you want to stop it immediately. <br>
     * You can find the job after unscheduling, and can reschedule it.
     * <pre>
     * x unschedule() to launched by, launchNow()
     * o unschedule() to find...(), get...()
     * o unschedule() to reschedule()
     * o unschedule() to disappear()
     * x disappear() to launched by, launchNow()
     * x disappear() to find...(), get...()
     * x disappear() to reschedule()
     * x disappear() to unschedule()
     * </pre>
     * @throws JobAlreadyDisappearedException When the job is already disappeared.
     * @throws JobAlreadyUnscheduleException When the job is already unscheduled.
     */
    void unschedule();

    /**
     * Disappear the job, no more launched by cron and launghNow() and reschedule. <br>
     * If the job is executing, the process continues until finishing. <br>
     * So call stopNow() if you want to stop it immediately. <br>
     * You cannot find the job after unscheduling, and cannot reschedule it.
     * <pre>
     * x unschedule() to launched by, launchNow()
     * o unschedule() to find...(), get...()
     * o unschedule() to reschedule()
     * o unschedule() to disappear()
     * x disappear() to launched by, launchNow()
     * x disappear() to find...(), get...()
     * x disappear() to reschedule()
     * x disappear() to unschedule()
     * </pre>
     * @throws JobAlreadyDisappearedException When the job is already disappeared.
     */
    void disappear();

    /**
     * This job becomes non-cron so the job will not be executed by scheduler. <br>
     * You can only execute by launchNow(). <br>
     * And you can restore the job as normal cron by reschedule(). <br>
     * Do nothing if already non-cron.
     * @throws JobAlreadyDisappearedException When the job is already disappeared.
     * @throws JobAlreadyUnscheduleException When the job is already unscheduled.
     */
    void becomeNonCron();

    // ===================================================================================
    //                                                                        Next Trigger
    //                                                                        ============
    /**
     * Register triggered job for success.
     * @param triggeredJob The job key of triggered job. (NotNull)
     * @throws JobAlreadyDisappearedException When the job is already disappeared.
     * @throws JobAlreadyUnscheduleException When the job is already unscheduled.
     */
    void registerNext(LaJobKey triggeredJob);
}
