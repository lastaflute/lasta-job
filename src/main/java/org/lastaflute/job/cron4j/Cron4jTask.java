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
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.dbflute.helper.HandyDate;
import org.dbflute.optional.OptionalThing;
import org.dbflute.util.DfTypeUtil;
import org.lastaflute.job.LaJob;
import org.lastaflute.job.LaJobRunner;
import org.lastaflute.job.exception.JobAlreadyIllegallyExecutingException;
import org.lastaflute.job.key.LaJobKey;
import org.lastaflute.job.key.LaJobUnique;
import org.lastaflute.job.log.JobNoticeLogLevel;
import org.lastaflute.job.subsidiary.ConcurrentExec;
import org.lastaflute.job.subsidiary.JobIdentityAttr;
import org.lastaflute.job.subsidiary.RunnerResult;
import org.lastaflute.job.subsidiary.VaryingCron;
import org.lastaflute.job.subsidiary.VaryingCronOption;
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
    protected VaryingCron varyingCron; // not null, can be switched
    protected final Class<? extends LaJob> jobType;
    protected final ConcurrentExec concurrentExec;
    protected final Supplier<String> threadNaming;
    protected final LaJobRunner jobRunner;
    protected final Cron4jNow cron4jNow;
    protected final Object executingLock = new Object();
    protected final Object varyingLock = new Object();

    // ===================================================================================
    //                                                                         Constructor
    //                                                                         ===========
    public Cron4jTask(VaryingCron varyingCron, Class<? extends LaJob> jobType, ConcurrentExec concurrentExec, Supplier<String> threadNaming,
            LaJobRunner jobRunner, Cron4jNow cron4jNow) {
        this.varyingCron = varyingCron;
        this.jobType = jobType;
        this.concurrentExec = concurrentExec;
        this.threadNaming = threadNaming;
        this.jobRunner = jobRunner;
        this.cron4jNow = cron4jNow;
    }

    // ===================================================================================
    //                                                                             Execute
    //                                                                             =======
    @Override
    public void execute(TaskExecutionContext context) {
        try {
            doExecute(context);
        } catch (Throwable cause) { // from framework part (exception in appilcation job are already handled)
            logger.error("Failed to execute the job task: " + varyingCron + ", " + jobType.getSimpleName(), cause);
        }
    }

    protected void doExecute(TaskExecutionContext context) {
        final Cron4jJob job = findJob();
        final List<TaskExecutor> executorList = job.findExecutorList(); // myself included
        final String cronExp;
        final VaryingCronOption cronOption;
        synchronized (varyingLock) {
            if (executorList.size() >= 2) { // other executions of same task exist
                if (concurrentExec.equals(ConcurrentExec.QUIT)) {
                    noticeSilentlyQuit(job, executorList);
                    return;
                } else if (concurrentExec.equals(ConcurrentExec.ERROR)) {
                    throwJobAlreadyIllegallyExecutingException(job, executorList);
                }
                // wait by synchronization as default
            }
            cronExp = varyingCron.getCronExp();
            cronOption = varyingCron.getCronOption();
        }
        synchronized (executingLock) { // avoid duplicate execution, waiting for previous ending
            final RunnerResult runnerResult = doExecute(job, cronExp, cronOption, context);
            if (runnerResult.isSuccess()) {
                job.triggerNext();
            }
        }
    }

    protected Cron4jJob findJob() {
        return cron4jNow.findJobByTask(this).get();
    }

    protected void noticeSilentlyQuit(Cron4jJob job, List<TaskExecutor> executorList) { // in varying lock
        final JobNoticeLogLevel noticeLogLevel = varyingCron.getCronOption().getNoticeLogLevel();
        final List<LocalDateTime> startTimeList = extractExecutingStartTimes(executorList);
        final String msg = "...Quitting the job because of already existing job: " + job + " startTimes=" + startTimeList;
        if (noticeLogLevel.equals(JobNoticeLogLevel.INFO)) {
            logger.info(msg);
        } else if (noticeLogLevel.equals(JobNoticeLogLevel.DEBUG)) {
            logger.debug(msg);
        }
    }

    protected void throwJobAlreadyIllegallyExecutingException(Cron4jJob job, List<TaskExecutor> executorList) {
        final List<LocalDateTime> startTimeList = extractExecutingStartTimes(executorList);
        throw new JobAlreadyIllegallyExecutingException("Already executing the job: " + job + " startTimes=" + startTimeList);
    }

    protected List<LocalDateTime> extractExecutingStartTimes(List<TaskExecutor> executorList) {
        return executorList.stream().map(executor -> {
            return new HandyDate(new Date(executor.getStartTime())).getLocalDateTime();
        }).collect(Collectors.toList());
    }

    // -----------------------------------------------------
    //                                             Executing
    //                                             ---------
    // in execution lock, cannot use varingCron here
    protected RunnerResult doExecute(JobIdentityAttr identityProvider, String cronExp, VaryingCronOption cronOption,
            TaskExecutionContext context) { // in synchronized world
        adjustThreadNameIfNeeds(cronOption);
        return runJob(identityProvider, cronExp, cronOption, context);
    }

    protected void adjustThreadNameIfNeeds(VaryingCronOption cronOption) { // because of too long name of cron4j
        final String supplied = threadNaming.get();
        final Thread currentThread = Thread.currentThread();
        if (currentThread.getName().equals(supplied)) { // already adjusted
            return;
        }
        currentThread.setName(supplied);
    }

    protected RunnerResult runJob(JobIdentityAttr identityProvider, String cronExp, VaryingCronOption cronOption,
            TaskExecutionContext cron4jContext) {
        return jobRunner.run(jobType, () -> createCron4jRuntime(identityProvider, cronExp, cronOption, cron4jContext));
    }

    protected Cron4jRuntime createCron4jRuntime(JobIdentityAttr identityProvider, String cronExp, VaryingCronOption cronOption,
            TaskExecutionContext cron4jContext) {
        final LaJobKey jobKey = identityProvider.getJobKey();
        final OptionalThing<String> jobTitle = identityProvider.getJobTitle();
        final OptionalThing<LaJobUnique> jobUnique = identityProvider.getJobUnique();
        final Map<String, Object> parameterMap = extractParameterMap(cronOption);
        final JobNoticeLogLevel noticeLogLevel = cronOption.getNoticeLogLevel();
        return new Cron4jRuntime(jobKey, jobTitle, jobUnique, cronExp, jobType, parameterMap, noticeLogLevel, cron4jContext);
    }

    protected Map<String, Object> extractParameterMap(VaryingCronOption cronOption) {
        return cronOption.getParamsSupplier().map(supplier -> supplier.supply()).orElse(Collections.emptyMap());
    }

    // ===================================================================================
    //                                                                              Switch
    //                                                                              ======
    public void becomeNonCrom() {
        synchronized (varyingLock) {
            this.varyingCron = createVaryingCron(Cron4jCron.NON_CRON, varyingCron.getCronOption());
        }
    }

    public void switchCron(String cronExp, VaryingCronOption cronOption) {
        synchronized (varyingLock) {
            this.varyingCron = createVaryingCron(cronExp, cronOption);
        }
    }

    protected VaryingCron createVaryingCron(String cronExp, VaryingCronOption cronOption) {
        return new VaryingCron(cronExp, cronOption);
    }

    // ===================================================================================
    //                                                                       Determination
    //                                                                       =============
    @Override
    public boolean canBeStopped() {
        return true; // fixedly
    }

    public boolean isNonCron() {
        return Cron4jCron.isNonCronExp(varyingCron.getCronExp());
    }

    // ===================================================================================
    //                                                                      Basic Override
    //                                                                      ==============
    @Override
    public String toString() {
        final String title = DfTypeUtil.toClassTitle(this);
        final String cronExpExp;
        final VaryingCronOption cronOption;
        synchronized (varyingLock) {
            cronExpExp = isNonCron() ? "non-cron" : varyingCron.getCronExp();
            cronOption = varyingCron.getCronOption();
        }
        return title + ":{" + cronExpExp + ", " + jobType.getName() + ", " + concurrentExec + ", " + cronOption + "}";
    }

    // ===================================================================================
    //                                                                            Accessor
    //                                                                            ========
    public VaryingCron getVaryingCron() {
        return varyingCron;
    }

    public Class<? extends LaJob> getJobType() {
        return jobType;
    }

    public ConcurrentExec getConcurrentExec() {
        return concurrentExec;
    }

    public Object getExecutingLock() {
        return executingLock;
    }

    public Object getVaryingLock() {
        return varyingLock;
    }
}
