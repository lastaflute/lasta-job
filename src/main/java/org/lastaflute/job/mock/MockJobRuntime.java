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
package org.lastaflute.job.mock;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;

import org.dbflute.optional.OptionalThing;
import org.lastaflute.job.LaJob;
import org.lastaflute.job.LaJobRuntime;
import org.lastaflute.job.cron4j.Cron4jRuntime;
import org.lastaflute.job.key.LaJobKey;
import org.lastaflute.job.key.LaJobUnique;
import org.lastaflute.job.log.JobNoticeLogLevel;
import org.lastaflute.job.subsidiary.CronOption;
import org.lastaflute.job.subsidiary.EndTitleRoll;
import org.lastaflute.job.subsidiary.InitialCronOpCall;

import it.sauronsoftware.cron4j.Scheduler;
import it.sauronsoftware.cron4j.TaskExecutionContext;
import it.sauronsoftware.cron4j.TaskExecutor;

/**
 * @author jflute
 * @since 0.2.0 (2016/01/15 Friday)
 */
public class MockJobRuntime implements LaJobRuntime {

    // ===================================================================================
    //                                                                          Definition
    //                                                                          ==========
    protected static final String MOCK_CRON_EXP = "* * * * *";

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    protected final Cron4jRuntime cron4jRuntime; // not null

    // ===================================================================================
    //                                                                         Constructor
    //                                                                         ===========
    public MockJobRuntime(String cronExp, Class<? extends LaJob> jobType, CronOption cronOption, TaskExecutionContext cron4jContext) {
        final LaJobKey jobKey = LaJobKey.of("mockKey_" + jobType.getSimpleName());
        final Map<String, Object> parameterMap = cronOption.getParamsSupplier().map(supplier -> {
            return supplier.supply();
        }).orElse(Collections.emptyMap());
        final JobNoticeLogLevel noticeLogLevel = cronOption.getNoticeLogLevel();
        cron4jRuntime = new Cron4jRuntime(jobKey, cronOption.getJobTitle(), cronOption.getJobUnique(), cronExp, jobType, parameterMap,
                noticeLogLevel, cron4jContext);
    }

    // -----------------------------------------------------
    //                                               Factory
    //                                               -------
    public static MockJobRuntime asDefault() {
        return createRuntime(MOCK_CRON_EXP, MockJob.class, op -> {});
    }

    public static MockJobRuntime of(Class<? extends LaJob> jobType) {
        return createRuntime(MOCK_CRON_EXP, jobType, op -> {});
    }

    public static MockJobRuntime of(Class<? extends LaJob> jobType, InitialCronOpCall opLambda) {
        return createRuntime(MOCK_CRON_EXP, jobType, opLambda);
    }

    @Deprecated
    public static MockJobRuntime withParameter(Map<String, Object> parameterMap) { // use of()
        return createRuntime(MOCK_CRON_EXP, MockJob.class, op -> op.params(() -> parameterMap));
    }

    protected static MockJobRuntime createRuntime(String cronExp, Class<? extends LaJob> jobType, InitialCronOpCall opLambda) {
        final CronOption option = new CronOption();
        opLambda.callback(option);
        return new MockJobRuntime(cronExp, jobType, option, createMockContext());
    }

    protected static MockTaskExecutionContext createMockContext() {
        final Scheduler scheduler = new Scheduler();
        final TaskExecutor taskExecutor = null; // cannot create...
        return new MockTaskExecutionContext(scheduler, taskExecutor);
    }

    // ===================================================================================
    //                                                                              Facade
    //                                                                              ======
    /**
     * @return The read-only map of end-title-roll. (NotNull, EmptyAllowed: when e.g. not found)
     */
    public Map<String, Object> getEndTitleRollMap() {
        return getEndTitleRoll().map(roll -> roll.getDataMap()).orElse(Collections.emptyMap());
    }

    // ===================================================================================
    //                                                                      Basic Override
    //                                                                      ==============
    @Override
    public String toString() {
        return "mock:{" + cron4jRuntime + "}";
    }

    // ===================================================================================
    //                                                                            Delegate
    //                                                                            ========
    @Override
    public LaJobKey getJobKey() {
        return cron4jRuntime.getJobKey();
    }

    @Override
    public OptionalThing<String> getJobTitle() {
        return cron4jRuntime.getJobTitle();
    }

    @Override
    public OptionalThing<LaJobUnique> getJobUnique() {
        return cron4jRuntime.getJobUnique();
    }

    @Override
    public String getCronExp() {
        return cron4jRuntime.getCronExp();
    }

    @Override
    public Class<? extends LaJob> getJobType() {
        return cron4jRuntime.getJobType();
    }

    @Override
    public Method getRunMethod() {
        return cron4jRuntime.getRunMethod();
    }

    @Override
    public Map<String, Object> getParameterMap() {
        return cron4jRuntime.getParameterMap();
    }

    @Override
    public JobNoticeLogLevel getNoticeLogLevel() {
        return cron4jRuntime.getNoticeLogLevel();
    }

    @Override
    public OptionalThing<EndTitleRoll> getEndTitleRoll() {
        return cron4jRuntime.getEndTitleRoll();
    }

    @Override
    public void showEndTitleRoll(Consumer<EndTitleRoll> dataLambda) {
        cron4jRuntime.showEndTitleRoll(dataLambda);
    }

    @Override
    public void stopIfNeeds() {
        cron4jRuntime.stopIfNeeds();
    }

    @Override
    public void suppressNextTrigger() {
        cron4jRuntime.suppressNextTrigger();
    }

    @Override
    public boolean isSuppressNextTrigger() {
        return cron4jRuntime.isSuppressNextTrigger();
    }

    @Override
    public String toCronMethodDisp() {
        return cron4jRuntime.toCronMethodDisp();
    }

    @Override
    public String toRunMethodDisp() {
        return cron4jRuntime.toRunMethodDisp();
    }
}
