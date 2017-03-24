/*
 * Copyright 2015-2017 the original author or authors.
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
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.dbflute.helper.HandyDate;
import org.dbflute.optional.OptionalThing;
import org.dbflute.util.DfTypeUtil;
import org.lastaflute.job.LaJob;
import org.lastaflute.job.LaJobRunner;
import org.lastaflute.job.exception.JobConcurrentlyExecutingException;
import org.lastaflute.job.key.LaJobKey;
import org.lastaflute.job.key.LaJobNote;
import org.lastaflute.job.key.LaJobUnique;
import org.lastaflute.job.log.JobErrorLog;
import org.lastaflute.job.log.JobErrorStackTracer;
import org.lastaflute.job.log.JobHistoryResource;
import org.lastaflute.job.log.JobNoticeLogLevel;
import org.lastaflute.job.subsidiary.ConcurrentJobStopper;
import org.lastaflute.job.subsidiary.CrossVMState;
import org.lastaflute.job.subsidiary.EndTitleRoll;
import org.lastaflute.job.subsidiary.ExecResultType;
import org.lastaflute.job.subsidiary.JobConcurrentExec;
import org.lastaflute.job.subsidiary.JobIdentityAttr;
import org.lastaflute.job.subsidiary.RunnerResult;
import org.lastaflute.job.subsidiary.VaryingCron;
import org.lastaflute.job.subsidiary.VaryingCronOption;

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
    protected static final String LF = "\n";

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    protected VaryingCron varyingCron; // not null, can be switched
    protected final Class<? extends LaJob> jobType;
    protected final JobConcurrentExec concurrentExec;
    protected final Supplier<String> threadNaming;
    protected final LaJobRunner jobRunner;
    protected final Cron4jNow cron4jNow;
    protected final Supplier<LocalDateTime> currentTime;
    protected final Object executingLock = new Object();
    protected final Object varyingLock = new Object();

    // ===================================================================================
    //                                                                         Constructor
    //                                                                         ===========
    public Cron4jTask(VaryingCron varyingCron, Class<? extends LaJob> jobType, JobConcurrentExec concurrentExec,
            Supplier<String> threadNaming, LaJobRunner jobRunner, Cron4jNow cron4jNow, Supplier<LocalDateTime> currentTime) {
        this.varyingCron = varyingCron;
        this.jobType = jobType;
        this.concurrentExec = concurrentExec;
        this.threadNaming = threadNaming;
        this.jobRunner = jobRunner;
        this.cron4jNow = cron4jNow;
        this.currentTime = currentTime;
    }

    // ===================================================================================
    //                                                                             Execute
    //                                                                             =======
    // -----------------------------------------------------
    //                                              Top Flow
    //                                              --------
    @Override
    public void execute(TaskExecutionContext context) {
        try {
            final LocalDateTime beginTime = currentTime.get();
            final Cron4jJob job = findJob();
            final Thread jobThread = Thread.currentThread();
            RunnerResult runnerResult = null;
            Throwable controllerCause = null;
            try {
                final OptionalThing<CrossVMState> crossVMState = jobRunner.getCrossVMHook().map(hook -> {
                    return hook.hookBeginning(job, beginTime);
                });
                try {
                    runnerResult = doExecute(job, context, beginTime); // not null
                } finally {
                    if (crossVMState.isPresent()) { // hook exists
                        jobRunner.getCrossVMHook().ifPresent(hook -> { // always present here
                            hook.hookEnding(job, crossVMState.get(), currentTime.get());
                        });
                    }
                }
            } catch (JobConcurrentlyExecutingException e) {
                error("Cannot execute the job task by concurrent execution: " + varyingCron + ", " + jobType.getSimpleName(), e);
                controllerCause = e;
            } catch (Throwable cause) { // from framework part (exception in appilcation job are already handled)
                error("Failed to execute the job task: " + varyingCron + ", " + jobType.getSimpleName(), cause);
                controllerCause = cause;
            }
            final LocalDateTime endTime = currentTime.get();
            recordJobHistory(context, job, jobThread, runnerResult, beginTime, endTime, controllerCause);
        } catch (Throwable coreCause) { // controller dead
            error("Failed to control the job task: " + varyingCron + ", " + jobType.getSimpleName(), coreCause);
        }
    }

    protected Cron4jJob findJob() {
        return cron4jNow.findJobByTask(this).get();
    }

    // -----------------------------------------------------
    //                                           Job Control 
    //                                           -----------
    protected RunnerResult doExecute(Cron4jJob job, TaskExecutionContext context, LocalDateTime beginTime) {
        final String cronExp;
        final VaryingCronOption cronOption;
        synchronized (varyingLock) {
            final List<TaskExecutor> executorList = job.findNativeExecutorList();
            if (executorList.size() >= 2) { // contains myself
                final OptionalThing<RunnerResult> concurrentResult = stopConcurrentJobIfNeeds(job, executorList);
                if (concurrentResult.isPresent()) {
                    return concurrentResult.get();
                }
                // will wait by executing synchronization as default
            }
            final OptionalThing<RunnerResult> neighborConcurrentResult = stopNeighborConcurrentJobIfNeeds(job, executorList);
            if (neighborConcurrentResult.isPresent()) {
                return neighborConcurrentResult.get();
                // waiting for neighbor job is unsupported #for_now (how do I wait?) 
            }
            cronExp = varyingCron.getCronExp();
            cronOption = varyingCron.getCronOption();
        }
        synchronized (executingLock) { // avoid duplicate execution, waiting for previous ending
            final RunnerResult runnerResult = actuallyExecute(job, cronExp, cronOption, context);
            if (canTriggerNext(job, runnerResult)) {
                job.triggerNext();
            }
            return runnerResult;
        }
    }

    // -----------------------------------------------------
    //                                            Concurrent
    //                                            ----------
    protected OptionalThing<RunnerResult> stopConcurrentJobIfNeeds(Cron4jJob job, List<TaskExecutor> executorList) {
        return createConcurrentJobStopper().stopIfNeeds(job, () -> {
            return executorList.stream().map(meta -> convertToBeginTime(meta)).collect(Collectors.toList()).toString();
        });
    }

    protected OptionalThing<RunnerResult> stopNeighborConcurrentJobIfNeeds(Cron4jJob job, List<TaskExecutor> executorList) {
        return createConcurrentJobStopper().stopIfNeighborNeeds(job, jobKey -> {
            return cron4jNow.findJobByKey(jobKey);
        }, () -> {
            return buildConcurrentSupplementDisp(executorList);
        });
    }

    protected ConcurrentJobStopper createConcurrentJobStopper() {
        return new ConcurrentJobStopper();
    }

    protected String buildConcurrentSupplementDisp(List<TaskExecutor> executorList) {
        return executorList.stream().map(meta -> convertToBeginTime(meta)).collect(Collectors.toList()).toString();
    }

    protected LocalDateTime convertToBeginTime(TaskExecutor meta) {
        // #thinking timeZone? by jflute (2017/03/20)
        return new HandyDate(new java.util.Date(meta.getStartTime())).getLocalDateTime();
    }

    // -----------------------------------------------------
    //                                             Executing
    //                                             ---------
    // in execution lock, cannot use varingCron here
    protected RunnerResult actuallyExecute(JobIdentityAttr identityProvider, String cronExp, VaryingCronOption cronOption,
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
        final OptionalThing<LaJobNote> jobNote = identityProvider.getJobNote();
        final OptionalThing<LaJobUnique> jobUnique = identityProvider.getJobUnique();
        final Map<String, Object> parameterMap = extractParameterMap(cronOption);
        final JobNoticeLogLevel noticeLogLevel = cronOption.getNoticeLogLevel();
        return new Cron4jRuntime(jobKey, jobNote, jobUnique, cronExp, jobType, parameterMap, noticeLogLevel, cron4jContext);
    }

    protected Map<String, Object> extractParameterMap(VaryingCronOption cronOption) {
        return cronOption.getParamsSupplier().map(supplier -> supplier.supply()).orElse(Collections.emptyMap());
    }

    // -----------------------------------------------------
    //                                          Next Trigger
    //                                          ------------
    protected boolean canTriggerNext(Cron4jJob job, RunnerResult runnerResult) {
        return !runnerResult.getCause().isPresent() && !runnerResult.isNextTriggerSuppressed();
    }

    // -----------------------------------------------------
    //                                           Job History
    //                                           -----------
    protected void recordJobHistory(TaskExecutionContext context, Cron4jJob job, Thread jobThread, RunnerResult runnerResult,
            LocalDateTime beginTime, LocalDateTime endTime, Throwable controllerCause) {
        final TaskExecutor taskExecutor = context.getTaskExecutor();
        final Cron4jJobHistory jobHistory = prepareJobHistory(job, runnerResult, beginTime, endTime, controllerCause);
        final int historyLimit = getHistoryLimit();
        jobRunner.getHistoryHook().ifPresent(hook -> {
            hook.hookRecord(jobHistory, new JobHistoryResource(historyLimit));
        });
        Cron4jJobHistory.record(taskExecutor, jobHistory, historyLimit);
    }

    protected Cron4jJobHistory prepareJobHistory(Cron4jJob job, RunnerResult runnerResult, LocalDateTime beginTime, LocalDateTime endTime,
            Throwable controllerCause) {
        final Cron4jJobHistory jobHistory;
        if (controllerCause == null) { // mainly here, and runnerResult is not null here
            jobHistory = createJobHistory(job, beginTime, endTime, () -> {
                return deriveRunnerExecResultType(runnerResult);
            }, runnerResult.getEndTitleRoll(), runnerResult.getCause());
        } else if (controllerCause instanceof JobConcurrentlyExecutingException) {
            jobHistory = createJobHistory(job, beginTime, endTime, () -> ExecResultType.ERROR_BY_CONCURRENT, OptionalThing.empty(),
                    OptionalThing.of(controllerCause));
        } else { // may be framework exception
            jobHistory = createJobHistory(job, beginTime, endTime, () -> ExecResultType.CAUSED_BY_FRAMEWORK, OptionalThing.empty(),
                    OptionalThing.of(controllerCause));
        }
        return jobHistory;
    }

    protected ExecResultType deriveRunnerExecResultType(RunnerResult runnerResult) {
        if (runnerResult.getCause().isPresent()) {
            return ExecResultType.CAUSED_BY_APPLICATION;
        } else if (runnerResult.isQuitByConcurrent()) {
            return ExecResultType.QUIT_BY_CONCURRENT;
        } else {
            return ExecResultType.SUCCESS;
        }
    }

    protected Cron4jJobHistory createJobHistory(Cron4jJob job, LocalDateTime beginTime, LocalDateTime endTime,
            Supplier<ExecResultType> execResultTypeProvider, OptionalThing<EndTitleRoll> endTitleRoll, OptionalThing<Throwable> cause) {
        final LaJobKey jobKey = job.getJobKey();
        final OptionalThing<LaJobNote> jobNote = job.getJobNote();
        final OptionalThing<LaJobUnique> jobUnique = job.getJobUnique();
        final OptionalThing<String> cronExp = job.getCronExp();
        final String jobTypeFqcn = job.getJobType().getName();
        final ExecResultType execResultType = execResultTypeProvider.get();
        return new Cron4jJobHistory(jobKey, jobNote, jobUnique, cronExp, jobTypeFqcn, beginTime, endTime, execResultType, endTitleRoll,
                cause);
    }

    protected int getHistoryLimit() {
        return 300;
    }

    // -----------------------------------------------------
    //                                             Error Log
    //                                             ---------
    protected void error(String msg, Throwable cause) {
        final String unifiedMsg = msg + LF + new JobErrorStackTracer().buildExceptionStackTrace(cause);
        jobRunner.getErrorLogHook().ifPresent(hook -> {
            hook.hookError(unifiedMsg);
        });
        JobErrorLog.log(unifiedMsg);
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

    public JobConcurrentExec getConcurrentExec() {
        return concurrentExec;
    }

    public Object getExecutingLock() {
        return executingLock;
    }

    public Object getVaryingLock() {
        return varyingLock;
    }
}
