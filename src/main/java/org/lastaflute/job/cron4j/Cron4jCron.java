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
package org.lastaflute.job.cron4j;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.dbflute.optional.OptionalThing;
import org.lastaflute.job.LaCron;
import org.lastaflute.job.LaJob;
import org.lastaflute.job.LaJobRunner;
import org.lastaflute.job.key.LaJobKey;
import org.lastaflute.job.key.LaJobUnique;
import org.lastaflute.job.log.JobChangeLog;
import org.lastaflute.job.subsidiary.CronOption;
import org.lastaflute.job.subsidiary.InitialCronOpCall;
import org.lastaflute.job.subsidiary.JobConcurrentExec;
import org.lastaflute.job.subsidiary.RegisteredJob;
import org.lastaflute.job.subsidiary.VaryingCron;
import org.lastaflute.job.subsidiary.VaryingCronOption;

/**
 * @author jflute
 * @since 0.2.0 (2016/01/09 Saturday)
 */
public class Cron4jCron implements LaCron {

    // ===================================================================================
    //                                                                          Definition
    //                                                                          ==========
    /** The cronExp to specify non-scheduling. */
    public static String NON_CRON = "$$nonCron$$";

    public static boolean isNonCronExp(String cronExp) {
        return NON_CRON.equals(cronExp);
    }

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    protected final Cron4jScheduler cron4jScheduler;
    protected final LaJobRunner jobRunner;
    protected final Cron4jNow cron4jNow;
    protected final CronRegistrationType registrationType;
    protected final Supplier<LocalDateTime> currentTime;
    protected final boolean frameworkDebug;

    // ===================================================================================
    //                                                                         Constructor
    //                                                                         ===========
    public Cron4jCron(Cron4jScheduler cron4jScheduler, LaJobRunner jobRunner, Cron4jNow cron4jNow, CronRegistrationType registrationType,
            Supplier<LocalDateTime> currentTime, boolean frameworkDebug) {
        this.cron4jScheduler = cron4jScheduler;
        this.jobRunner = jobRunner;
        this.cron4jNow = cron4jNow;
        this.registrationType = registrationType;
        this.currentTime = currentTime;
        this.frameworkDebug = frameworkDebug;
    }

    public enum CronRegistrationType {
        START, CHANGE
    }

    // ===================================================================================
    //                                                                            Register
    //                                                                            ========
    @Override
    public RegisteredJob register(String cronExp, Class<? extends LaJob> jobType, JobConcurrentExec concurrentExec,
            InitialCronOpCall opLambda) {
        assertArgumentNotNull("cronExp", cronExp);
        if (isNonCrom(cronExp)) {
            throw new IllegalArgumentException("The cronExp for register() should not be non-cron: " + toString());
        }
        assertArgumentNotNull("jobType", jobType);
        assertArgumentNotNull("concurrentExec", concurrentExec);
        assertArgumentNotNull("opLambda (cronOptionConsumer)", opLambda);
        return doRegister(cronExp, jobType, concurrentExec, opLambda);
    }

    protected boolean isNonCrom(String cronExp) {
        return Cron4jCron.isNonCronExp(cronExp);
    }

    @Override
    public RegisteredJob registerNonCron(Class<? extends LaJob> jobType, JobConcurrentExec concurrentExec, InitialCronOpCall opLambda) {
        assertArgumentNotNull("jobType", jobType);
        assertArgumentNotNull("concurrentExec", concurrentExec);
        assertArgumentNotNull("opLambda (cronOptionConsumer)", opLambda);
        return doRegister(NON_CRON, jobType, concurrentExec, opLambda);
    }

    protected RegisteredJob doRegister(String cronExp, Class<? extends LaJob> jobType, JobConcurrentExec concurrentExec,
            InitialCronOpCall opLambda) {
        final CronOption cronOption = createCronOption(opLambda);
        final Cron4jTask cron4jTask = createCron4jTask(cronExp, jobType, concurrentExec, cronOption);
        showRegistering(cron4jTask);
        final String cron4jId = scheduleIfNeeds(cronExp, cron4jTask); // null allowed when non-cron
        return saveJob(cron4jTask, cronOption, cron4jId);
    }

    protected CronOption createCronOption(InitialCronOpCall opLambda) {
        final CronOption option = new CronOption();
        opLambda.callback(option);
        return option;
    }

    protected Cron4jTask createCron4jTask(String cronExp, Class<? extends LaJob> jobType, JobConcurrentExec concurrentExec,
            CronOption cronOption) {
        final VaryingCron varyingCron = createVaryingCron(cronExp, cronOption);
        final Supplier<String> threadNaming = prepareThreadNaming(cronOption);
        return new Cron4jTask(varyingCron, jobType, concurrentExec, threadNaming, jobRunner, cron4jNow, currentTime, frameworkDebug); // adapter task
    }

    protected VaryingCron createVaryingCron(String cronExp, VaryingCronOption cronOption) {
        return new VaryingCron(cronExp, cronOption);
    }

    protected Supplier<String> prepareThreadNaming(CronOption cronOption) {
        final OptionalThing<LaJobUnique> jobUnique = cronOption.getJobUnique();
        return () -> { // callback for current thread
            return THREAD_NAME_PREFIX + jobUnique.map(uq -> uq.value()).orElseGet(() -> {
                return Integer.toHexString(Thread.currentThread().hashCode()); // task's threand
            });
        };
    }

    protected void showRegistering(Cron4jTask cron4jTask) {
        if (CronRegistrationType.CHANGE.equals(registrationType) && JobChangeLog.isEnabled()) {
            // only when change, because starter shows rich logging when start
            JobChangeLog.log("#job ...Registering job: {}", cron4jTask);
        }
    }

    protected String scheduleIfNeeds(String cronExp, Cron4jTask cron4jTask) {
        final String cron4jId;
        if (cron4jTask.isNonCron()) {
            cron4jId = null;
        } else { // mainly here
            cron4jId = cron4jScheduler.schedule(cronExp, cron4jTask);
        }
        return cron4jId;
    }

    protected Cron4jJob saveJob(Cron4jTask cron4jTask, CronOption cronOption, String cron4jId) {
        return cron4jNow.saveJob(cron4jTask, cronOption, cronOption.getTriggeringJobKeyList(), OptionalThing.ofNullable(cron4jId, () -> {
            throw new IllegalStateException("Not found the cron4jId: " + cron4jTask);
        }));
    }

    // ===================================================================================
    //                                                                 Neighbor Concurrent
    //                                                                 ===================
    @Override
    public void setupNeighborConcurrent(String groupName, JobConcurrentExec concurrentExec, RegisteredJob... jobs) {
        assertArgumentNotNull("groupName", groupName);
        if (groupName.trim().isEmpty()) {
            throw new IllegalArgumentException("The argument 'groupName' should not be empty: [" + groupName + "]");
        }
        assertArgumentNotNull("concurrentExec", concurrentExec);
        assertArgumentNotNull("jobs", jobs);
        final Set<LaJobKey> jobKeySet = Stream.of(jobs).map(job -> job.getJobKey()).collect(Collectors.toSet());
        cron4jNow.setupNeighborConcurrent(groupName, concurrentExec, jobKeySet);
    }

    // ===================================================================================
    //                                                                        Small Helper
    //                                                                        ============
    protected void assertArgumentNotNull(String variableName, Object value) {
        if (variableName == null) {
            throw new IllegalArgumentException("The variableName should not be null.");
        }
        if (value == null) {
            throw new IllegalArgumentException("The argument '" + variableName + "' should not be null.");
        }
    }
}
