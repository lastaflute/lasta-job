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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.dbflute.optional.OptionalThing;
import org.dbflute.util.DfTypeUtil;
import org.lastaflute.job.LaJob;
import org.lastaflute.job.LaJobHistory;
import org.lastaflute.job.LaScheduledJob;
import org.lastaflute.job.exception.JobAlreadyUnscheduleException;
import org.lastaflute.job.exception.JobTriggeredNotFoundException;
import org.lastaflute.job.key.LaJobKey;
import org.lastaflute.job.key.LaJobNote;
import org.lastaflute.job.key.LaJobUnique;
import org.lastaflute.job.log.JobChangeLog;
import org.lastaflute.job.log.JobNoticeLogLevel;
import org.lastaflute.job.subsidiary.CronOption;
import org.lastaflute.job.subsidiary.CronParamsSupplier;
import org.lastaflute.job.subsidiary.JobConcurrentExec;
import org.lastaflute.job.subsidiary.JobIdentityAttr;
import org.lastaflute.job.subsidiary.LaunchedProcess;
import org.lastaflute.job.subsidiary.ReadableJobAttr;
import org.lastaflute.job.subsidiary.VaryingCronOpCall;
import org.lastaflute.job.subsidiary.VaryingCronOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.sauronsoftware.cron4j.TaskExecutor;

/**
 * @author jflute
 * @since 0.2.0 (2016/01/11 Monday)
 */
public class Cron4jJob implements LaScheduledJob, JobIdentityAttr, ReadableJobAttr {

    // ===================================================================================
    //                                                                          Definition
    //                                                                          ==========
    private static final Logger logger = LoggerFactory.getLogger(Cron4jJob.class);

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    protected final LaJobKey jobKey;
    protected final OptionalThing<LaJobNote> jobNote;
    protected final OptionalThing<LaJobUnique> jobUnique;
    protected OptionalThing<Cron4jId> cron4jId; // mutable for non-cron
    protected final Cron4jTask cron4jTask;
    protected final Cron4jNow cron4jNow;
    protected volatile boolean unscheduled;
    protected List<LaJobKey> triggeredJobKeyList; // null allowed if no next trigger

    // ===================================================================================
    //                                                                         Constructor
    //                                                                         ===========
    public Cron4jJob(LaJobKey jobKey, OptionalThing<LaJobNote> jobNote, OptionalThing<LaJobUnique> jobUnique,
            OptionalThing<Cron4jId> cron4jId, Cron4jTask cron4jTask, Cron4jNow cron4jNow) {
        this.jobKey = jobKey;
        this.jobNote = jobNote;
        this.jobUnique = jobUnique;
        this.cron4jId = cron4jId;
        this.cron4jTask = cron4jTask;
        this.cron4jNow = cron4jNow;
    }

    // ===================================================================================
    //                                                                       Executing Now
    //                                                                       =============
    @Override
    public boolean isExecutingNow() {
        return !findNativeExecutorList().isEmpty();
    }

    public List<TaskExecutor> findNativeExecutorList() { // public for framework
        return cron4jNow.getCron4jScheduler().findExecutorList(cron4jTask);
    }

    // ===================================================================================
    //                                                                          Launch Now
    //                                                                          ==========
    @Override
    public synchronized LaunchedProcess launchNow() {
        verifyScheduledState();
        if (JobChangeLog.isEnabled()) {
            JobChangeLog.log("#job ...Launching now: {}", toString());
        }
        // if executed by cron here, duplicate execution occurs but task level synchronization exists
        final TaskExecutor taskExecutor = cron4jNow.getCron4jScheduler().launch(cron4jTask);
        return createLaunchedProcess(taskExecutor);
    }

    protected LaunchedProcess createLaunchedProcess(TaskExecutor taskExecutor) {
        return new LaunchedProcess(this, () -> joinJobThread(taskExecutor), () -> findJobHistory(taskExecutor));
    }

    protected void joinJobThread(TaskExecutor taskExecutor) {
        try {
            taskExecutor.join();
        } catch (InterruptedException e) {
            String msg = "The current thread has been interrupted while join: taskExecutor=" + taskExecutor + ", job=" + this;
            throw new IllegalStateException(msg, e);
        }
    }

    protected OptionalThing<LaJobHistory> findJobHistory(TaskExecutor taskExecutor) {
        return Cron4jJobHistory.find(taskExecutor);
    }

    // ===================================================================================
    //                                                                            Stop Now
    //                                                                            ========
    @Override
    public synchronized void stopNow() { // can be called if unscheduled
        final List<TaskExecutor> executorList = findNativeExecutorList();
        if (JobChangeLog.isEnabled()) {
            JobChangeLog.log("#job ...Stopping {} execution(s) now: {}", executorList.size(), toString());
        }
        if (!executorList.isEmpty()) {
            executorList.forEach(executor -> executor.stop());
        }
    }

    // ===================================================================================
    //                                                                          Reschedule
    //                                                                          ==========
    @Override
    public synchronized void reschedule(String cronExp, VaryingCronOpCall opLambda) {
        verifyScheduledState();
        if (isNonCromExp(cronExp)) {
            throw new IllegalArgumentException("The cronExp for reschedule() should not be non-cron: " + toString());
        }
        final String existingCronExp = cron4jTask.getVaryingCron().getCronExp();
        cron4jTask.switchCron(cronExp, createCronOption(opLambda));
        final Cron4jScheduler cron4jScheduler = cron4jNow.getCron4jScheduler();
        cron4jId.ifPresent(id -> {
            if (JobChangeLog.isEnabled()) {
                JobChangeLog.log("#job ...Rescheduling {} as cron from '{}' to '{}'", jobKey, existingCronExp, cronExp);
            }
            cron4jScheduler.reschedule(id, cronExp);
        }).orElse(() -> {
            if (JobChangeLog.isEnabled()) {
                JobChangeLog.log("#job ...Rescheduling {} as cron from non-cron to '{}'", jobKey, cronExp);
            }
            final String generatedId = cron4jScheduler.schedule(cronExp, cron4jTask);
            cron4jId = OptionalThing.of(Cron4jId.of(generatedId));
        });
    }

    protected boolean isNonCromExp(String cronExp) {
        return Cron4jCron.isNonCronExp(cronExp);
    }

    protected VaryingCronOption createCronOption(VaryingCronOpCall opLambda) {
        final VaryingCronOption option = new CronOption();
        opLambda.callback(option);
        return option;
    }

    // ===================================================================================
    //                                                                          Unschedule
    //                                                                          ==========
    @Override
    public synchronized void unschedule() {
        verifyScheduledState();
        if (JobChangeLog.isEnabled()) {
            JobChangeLog.log("#job ...Unscheduling {}", toString());
        }
        cron4jId.ifPresent(id -> {
            cron4jNow.getCron4jScheduler().deschedule(id);
        });
        cron4jNow.clearUnscheduleJob(); // immediately clear, executing process is kept
        unscheduled = true;
    }

    @Override
    public synchronized boolean isUnscheduled() {
        return unscheduled;
    }

    protected void verifyScheduledState() {
        if (unscheduled) {
            throw new JobAlreadyUnscheduleException("Already unscheduled the job: " + toString());
        }
    }

    // ===================================================================================
    //                                                                            Non-Cron
    //                                                                            ========
    @Override
    public synchronized void becomeNonCron() {
        verifyScheduledState();
        if (JobChangeLog.isEnabled()) {
            JobChangeLog.log("#job ...Becoming non-cron: {}", toString());
        }
        cron4jId.ifPresent(id -> {
            cron4jTask.becomeNonCrom();
            cron4jNow.getCron4jScheduler().deschedule(id);
            cron4jId = OptionalThing.empty();
        });
    }

    @Override
    public synchronized boolean isNonCron() {
        return !cron4jId.isPresent();
    }

    // ===================================================================================
    //                                                                             Trigger
    //                                                                             =======
    @Override
    public synchronized void registerNext(LaJobKey triggeredJobKey) {
        verifyScheduledState();
        if (triggeredJobKey == null) {
            throw new IllegalArgumentException("The argument 'triggeredJobKey' should not be null.");
        }
        // lazy check for initialization logic
        //if (!cron4jNow.findJobByKey(triggeredJobKey).isPresent()) {
        //    throw new IllegalArgumentException("Not found the job by the job key: " + triggeredJobKey);
        //}
        if (triggeredJobKeyList == null) {
            triggeredJobKeyList = new ArrayList<LaJobKey>();
        }
        triggeredJobKeyList.add(triggeredJobKey);
    }

    public synchronized void triggerNext() { // called in framework
        verifyScheduledState();
        if (triggeredJobKeyList == null) {
            return;
        }
        final List<Cron4jJob> triggeredJobList = triggeredJobKeyList.stream().map(triggeredJobKey -> {
            return findTriggeredJob(triggeredJobKey);
        }).collect(Collectors.toList());
        showPreparingNextTrigger(triggeredJobList);
        for (Cron4jJob triggeredJob : triggeredJobList) { // expception if contains unscheduled
            triggeredJob.launchNow();
        }
    }

    protected Cron4jJob findTriggeredJob(LaJobKey triggeredJobKey) {
        return cron4jNow.findJobByKey(triggeredJobKey).orElseTranslatingThrow(cause -> {
            String msg = "Not found the next job: " + triggeredJobKey + " triggered by " + toString();
            throw new JobTriggeredNotFoundException(msg, cause);
        });
    }

    protected void showPreparingNextTrigger(List<Cron4jJob> triggeredJobList) {
        final List<String> expList = triggeredJobList.stream().map(triggeredJob -> {
            return triggeredJob.toIdentityDisp();
        }).collect(Collectors.toList());
        final String exp = expList.size() == 1 ? expList.get(0) : expList.toString();
        logger.info("#job ...Preparing next job {} triggered by {}", exp, toIdentityDisp());
    }

    protected String buildTriggerNextJobExp(Cron4jJob triggeredJob) {
        final String keyExp = triggeredJob.getJobUnique().map(unique -> unique.value()).orElseGet(() -> {
            return triggeredJob.getJobKey().value();
        });
        return keyExp + "(" + triggeredJob.getJobType().getSimpleName() + ")";
    }

    // ===================================================================================
    //                                                                             Display
    //                                                                             =======
    public String toIdentityDisp() {
        final Class<? extends LaJob> jobType = cron4jTask.getJobType();
        return jobType.getSimpleName() + ":{" + jobUnique.map(uq -> uq + "(" + jobKey + ")").orElseGet(() -> jobKey.value()) + "}";
    }

    // ===================================================================================
    //                                                                      Basic Override
    //                                                                      ==============
    @Override
    public String toString() {
        final String titlePrefix = jobNote.map(title -> title + ", ").orElse("");
        final String keyExp = jobUnique.map(uq -> uq + "(" + jobKey + ")").orElseGet(() -> jobKey.value());
        final String idExp = cron4jId.map(id -> id.value()).orElse("non-cron");
        final String hash = Integer.toHexString(hashCode());
        return DfTypeUtil.toClassTitle(this) + ":{" + titlePrefix + keyExp + ", " + idExp + ", " + cron4jTask + "}@" + hash;
    }

    // ===================================================================================
    //                                                                            Accessor
    //                                                                            ========
    @Override
    public LaJobKey getJobKey() {
        return jobKey;
    }

    @Override
    public OptionalThing<LaJobNote> getJobNote() {
        return jobNote;
    }

    @Override
    public OptionalThing<LaJobUnique> getJobUnique() {
        return jobUnique;
    }

    @Override
    public synchronized OptionalThing<String> getCronExp() { // synchronized for varying
        final String cronExp = !isNonCron() ? cron4jTask.getVaryingCron().getCronExp() : null;
        return OptionalThing.ofNullable(cronExp, () -> {
            throw new IllegalStateException("Not found cron expression because of non-cron job: " + toString());
        });
    }

    @Override
    public Class<? extends LaJob> getJobType() {
        return cron4jTask.getJobType();
    }

    @Override
    public OptionalThing<CronParamsSupplier> getParamsSupplier() {
        return cron4jTask.getVaryingCron().getCronOption().getParamsSupplier();
    }

    @Override
    public JobNoticeLogLevel getNoticeLogLevel() {
        return cron4jTask.getVaryingCron().getCronOption().getNoticeLogLevel();
    }

    @Override
    public JobConcurrentExec getConcurrentExec() {
        return cron4jTask.getConcurrentExec();
    }

    public Cron4jTask getCron4jTask() { // for framework
        return cron4jTask;
    }

    @Override
    public synchronized List<LaJobKey> getTriggeredJobKeyList() { // synchronized for varying
        return triggeredJobKeyList != null ? Collections.unmodifiableList(triggeredJobKeyList) : Collections.emptyList();
    }
}
