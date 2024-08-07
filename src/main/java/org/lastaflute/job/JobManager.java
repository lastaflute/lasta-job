/*
 * Copyright 2015-2024 the original author or authors.
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

import java.util.List;

import org.dbflute.optional.OptionalThing;
import org.lastaflute.job.key.LaJobKey;
import org.lastaflute.job.key.LaJobUnique;
import org.lastaflute.job.subsidiary.CronConsumer;

/**
 * @author jflute
 * @since 0.2.0 (2016/01/11 Monday)
 */
public interface JobManager {

    // ===================================================================================
    //                                                                         Control Job
    //                                                                         ===========
    /**
     * Find job by the job key in the scheduled jobs.
     * @param jobKey The job key auto-generated by scheduler. (NotNull)
     * @return The optional scheduled job, which is unique by job key. (NotNull, EmptyAllowed: when not found)
     */
    OptionalThing<LaScheduledJob> findJobByKey(LaJobKey jobKey);

    /**
     * Find job by the job unique code in the scheduled jobs. <br>
     * Job unique code is specified by application using uniqueBy() of cron option. <br>
     * So always empty if no specified.
     * @param jobUnique The value of job unique including unique code. (NotNull)
     * @return The optional scheduled job, which is unique by job key. (NotNull, EmptyAllowed: when not found)
     */
    OptionalThing<LaScheduledJob> findJobByUniqueOf(LaJobUnique jobUnique);

    /**
     * Get the list of all jobs in the scheduled jobs. <br>
     * Sorted as registration order.
     * @return The list of all jobs. (NotNull)
     */
    List<LaScheduledJob> getJobList();

    /**
     * Schedule new job while scheduler is active.
     * <pre>
     * jobManager.schedule(cron -&gt; {
     *     cron.register("* * * * *", SeaJob.class);
     * });
     * </pre>
     * @param oneArgLambda The callback to consume cron. (NotNull)
     */
    void schedule(CronConsumer oneArgLambda);

    // ===================================================================================
    //                                                                             Destroy
    //                                                                             =======
    /**
     * (Dangrous!) Destory registered schedule in framework, that means all jobs and crons. <br>
     * It requests stop to executing jobs and delete cron and delete scheduler completely. <br>
     * This is automatically called when application is closed.
     */
    void destroy();

    // ===================================================================================
    //                                                                              Reboot
    //                                                                              ======
    /**
     * (Dangrous!) Destory and re-start schedules.
     */
    void reboot();

    // ===================================================================================
    //                                                                         Initialized
    //                                                                         ===========
    /**
     * Is scheduling done? (scheduling is lazy loaded)
     * @return true if registration of all schedulers is done.
     */
    boolean isSchedulingDone();

    // ===================================================================================
    //                                                                         Job History
    //                                                                         ===========
    /**
     * Search job history as registered order. (not begun order)
     * @return The list of job history, removed too old histories. (NotNull)
     */
    List<LaJobHistory> searchJobHistoryList();
}
