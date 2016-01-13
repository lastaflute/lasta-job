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
import org.lastaflute.job.subsidiary.ConcurrentExec;
import org.lastaflute.job.subsidiary.NoticeLogLevel;
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
    protected String cronExp; // can be updated
    protected final Class<? extends LaJob> jobType;
    protected ConcurrentExec concurrentExec;
    protected LaCronOption cronOption; // can be updated
    protected final LaJobRunner jobRunner;
    protected final Cron4jNow cron4jNow;

    // ===================================================================================
    //                                                                         Constructor
    //                                                                         ===========
    public Cron4jTask(String cronExp, Class<? extends LaJob> jobType, ConcurrentExec concurrentExec, LaCronOption cronOption,
            LaJobRunner jobRunner, Cron4jNow cron4jNow) {
        this.cronExp = cronExp;
        this.jobType = jobType;
        this.concurrentExec = concurrentExec;
        this.cronOption = cronOption;
        this.jobRunner = jobRunner;
        this.cron4jNow = cron4jNow;
    }

    // ===================================================================================
    //                                                                              Switch
    //                                                                              ======
    public synchronized void switchCron(String cronExp, LaCronOption cronOption) {
        this.cronExp = cronExp;
        this.cronOption = cronOption;
    }

    // ===================================================================================
    //                                                                             Execute
    //                                                                             =======
    @Override
    public void execute(TaskExecutionContext context) {
        final Cron4jJob job = findJob();
        final List<TaskExecutor> executorList = job.findExecutorList(); // myself included
        if (executorList.size() >= 2) { // other executions of same task exist
            if (concurrentExec.equals(ConcurrentExec.QUIT)) {
                noticeSilentlyQuit(job, executorList);
                return;
            } else if (concurrentExec.equals(ConcurrentExec.ERROR)) {
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

    protected synchronized void noticeSilentlyQuit(Cron4jJob job, List<TaskExecutor> executorList) {
        final List<LocalDateTime> startTimeList = extractExecutingStartTimes(executorList);
        final String msg = "...Quitting the job because of already existing job: " + job + " startTimes=" + startTimeList;
        if (cronOption.getNoticeLogLevel().equals(NoticeLogLevel.INFO)) {
            logger.info(msg);
        } else if (cronOption.getNoticeLogLevel().equals(NoticeLogLevel.DEBUG)) {
            logger.debug(msg);
        }
    }

    protected void throwJobAlreadyExecutingSystemException(Cron4jJob job, List<TaskExecutor> executorList) {
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
    protected void doExecute(TaskExecutionContext context) { // in synchronized world
        adjustThreadNameIfNeeds();
        runJob(context);
    }

    protected void adjustThreadNameIfNeeds() { // because of too long name of cron4j
        final Thread currentThread = Thread.currentThread();
        final String adjustedName = buildThreadName(currentThread);
        if (!adjustedName.equals(currentThread.getName())) { // first time
            currentThread.setName(adjustedName);
        }
    }

    protected String buildThreadName(Thread currentThread) {
        return "job_" + cronOption.getJobUnique().map(uq -> uq.value()).orElseGet(() -> {
            return Integer.toHexString(currentThread.hashCode());
        });
    }

    protected void runJob(TaskExecutionContext cron4jContext) {
        jobRunner.run(jobType, () -> createCron4jRuntime(cron4jContext));
    }

    protected Cron4jRuntime createCron4jRuntime(TaskExecutionContext cron4jContext) {
        return new Cron4jRuntime(cronExp, jobType, extractParameterMap(), cronOption, cron4jContext);
    }

    protected Map<String, Object> extractParameterMap() {
        return cronOption.getParamsSupplier().map(supplier -> supplier.supply()).orElse(Collections.emptyMap());
    }

    // ===================================================================================
    //                                                                              Option
    //                                                                              ======
    @Override
    public boolean canBeStopped() {
        return true; // #thiking fixedly true, all right?
    }

    protected synchronized AlreadyExecutingBehavior getAlreadyExecutingBehavior() {
        return cronOption.getAlreadyExecutingBehavior();
    }

    public synchronized OptionalThing<LaJobUnique> getJobUnique() {
        return cronOption.getJobUnique();
    }

    // ===================================================================================
    //                                                                      Basic Override
    //                                                                      ==============
    @Override
    public String toString() {
        return DfTypeUtil.toClassTitle(this) + ":{" + cronExp + ", " + jobType + ", " + cronOption + "}";
    }

    // ===================================================================================
    //                                                                            Accessor
    //                                                                            ========
    public String getCronExp() {
        return cronExp;
    }

    public Class<? extends LaJob> getJobType() {
        return jobType;
    }
}
