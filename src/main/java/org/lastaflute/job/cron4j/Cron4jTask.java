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
package org.lastaflute.job.cron4j;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.dbflute.helper.HandyDate;
import org.dbflute.optional.OptionalThing;
import org.dbflute.util.DfTypeUtil;
import org.lastaflute.job.LaCronOption;
import org.lastaflute.job.LaCronOption.AlreadyExecutingBehavior;
import org.lastaflute.job.LaJob;
import org.lastaflute.job.LaJobRunner;
import org.lastaflute.job.exception.JobAlreadyExecutingSystemException;
import org.lastaflute.job.key.LaJobUnique;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.sauronsoftware.cron4j.Task;
import it.sauronsoftware.cron4j.TaskExecutionContext;
import it.sauronsoftware.cron4j.TaskExecutor;

/**
 * @author jflute
 * @since 0.2.0 (2016/01/11 Monday)
 */
public class Cron4jTask extends Task { // unique per job in lasta job world

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    private static final Logger logger = LoggerFactory.getLogger(Cron4jTask.class);

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    protected final String cronExp;
    protected final Class<? extends LaJob> jobType;
    protected final LaCronOption option;
    protected final LaJobRunner jobRunner;
    protected final Cron4jNow cron4jNow;

    // ===================================================================================
    //                                                                         Constructor
    //                                                                         ===========
    public Cron4jTask(String cronExp, Class<? extends LaJob> jobType, LaCronOption option, LaJobRunner jobRunner, Cron4jNow cron4jNow) {
        this.cronExp = cronExp;
        this.jobType = jobType;
        this.option = option;
        this.jobRunner = jobRunner;
        this.cron4jNow = cron4jNow;
    }

    // ===================================================================================
    //                                                                             Execute
    //                                                                             =======
    @Override
    public void execute(TaskExecutionContext context) {
        final Cron4jJob job = findJob();
        final List<TaskExecutor> executorList = job.findExecutorList(); // myself included
        if (executorList.size() >= 2) { // other executions of same task exist
            final AlreadyExecutingBehavior executingBehavior = option.getAlreadyExecutingBehavior();
            if (executingBehavior.equals(AlreadyExecutingBehavior.SILENTLY_QUIT)) {
                noticeSilentlyQuit(job, executorList);
                return;
            } else if (executingBehavior.equals(AlreadyExecutingBehavior.SYSTEM_EXCEPTION)) {
                throwJobAlreadyExecutingSystemException(job, executorList);
            }
            // wait by synchronization as default
        }
        synchronized (this) { // avoid duplicate execution, waiting for previous ending
            doExecute(context);
        }
    }

    protected Cron4jJob findJob() {
        return cron4jNow.findJobByTask(this).get();
    }

    protected void noticeSilentlyQuit(final Cron4jJob job, final List<TaskExecutor> executorList) {
        final List<LocalDateTime> startTimeList = extractExecutingStartTimes(executorList);
        logger.info("...Quitting the job because of already existing job: " + job + " startTimes=" + startTimeList);
    }

    protected void throwJobAlreadyExecutingSystemException(final Cron4jJob job, final List<TaskExecutor> executorList) {
        final List<LocalDateTime> startTimeList = extractExecutingStartTimes(executorList);
        throw new JobAlreadyExecutingSystemException("Already executing the job: " + job + " startTimes=" + startTimeList);
    }

    protected List<LocalDateTime> extractExecutingStartTimes(List<TaskExecutor> executorList) {
        final List<LocalDateTime> startTimeList = executorList.stream().map(executor -> {
            return new HandyDate(new Date(executor.getStartTime())).getLocalDateTime();
        }).collect(Collectors.toList());
        return startTimeList;
    }

    // -----------------------------------------------------
    //                                             Executing
    //                                             ---------
    protected void doExecute(TaskExecutionContext context) {
        adjustThreadNameIfNeeds();
        runJob(context);
    }

    protected void adjustThreadNameIfNeeds() { // because of too long name of cron4j
        final Thread currentThread = Thread.currentThread();
        final String adjustedName = "cron4j_" + Integer.toHexString(currentThread.hashCode());
        if (!adjustedName.equals(currentThread.getName())) { // first time
            currentThread.setName(adjustedName);
        }
    }

    protected void runJob(TaskExecutionContext cron4jContext) {
        jobRunner.run(jobType, () -> createCron4jRuntime(cron4jContext));
    }

    protected Cron4jRuntime createCron4jRuntime(TaskExecutionContext cron4jContext) {
        return new Cron4jRuntime(cronExp, jobType, extractParameterMap(), cron4jContext);
    }

    protected Map<String, Object> extractParameterMap() {
        return option.getParamsSupplier().map(supplier -> supplier.supply()).orElse(Collections.emptyMap());
    }

    // ===================================================================================
    //                                                                              Option
    //                                                                              ======
    @Override
    public boolean canBeStopped() {
        return true; // #thiking fixedly true, all right?
    }

    public OptionalThing<LaJobUnique> getJobUnique() {
        return option.getJobUnique();
    }

    // ===================================================================================
    //                                                                      Basic Override
    //                                                                      ==============
    @Override
    public String toString() {
        return DfTypeUtil.toClassTitle(this) + ":{" + cronExp + ", " + jobType + ", " + option + "}";
    }
}
