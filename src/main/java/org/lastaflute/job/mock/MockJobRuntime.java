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
import org.lastaflute.job.log.JobNoticeLogLevel;
import org.lastaflute.job.subsidiary.EndTitleRoll;

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
    protected final Cron4jRuntime cron4jRuntime;

    // ===================================================================================
    //                                                                         Constructor
    //                                                                         ===========
    public MockJobRuntime(String cronExp, Class<? extends LaJob> jobType, Map<String, Object> parameterMap,
            JobNoticeLogLevel noticeLogLevel, TaskExecutionContext cron4jContext) {
        cron4jRuntime = new Cron4jRuntime(cronExp, jobType, parameterMap, noticeLogLevel, cron4jContext);
    }

    // -----------------------------------------------------
    //                                               Factory
    //                                               -------
    public static MockJobRuntime asDefault() {
        return createRuntime(MOCK_CRON_EXP, MockJob.class, Collections.emptyMap());
    }

    public static MockJobRuntime of(Class<? extends LaJob> jobType) {
        return createRuntime(MOCK_CRON_EXP, jobType, Collections.emptyMap());
    }

    public static MockJobRuntime of(Class<? extends LaJob> jobType, Map<String, Object> parameterMap) {
        return createRuntime(MOCK_CRON_EXP, jobType, parameterMap);
    }

    @Deprecated
    public static MockJobRuntime withParameter(Map<String, Object> parameterMap) { // use of()
        return createRuntime(MOCK_CRON_EXP, MockJob.class, parameterMap);
    }

    protected static MockJobRuntime createRuntime(String cronExp, Class<? extends LaJob> jobType, Map<String, Object> parameterMap) {
        return new MockJobRuntime(cronExp, jobType, parameterMap, prepareMockLogLevel(), createMockContext());
    }

    protected static JobNoticeLogLevel prepareMockLogLevel() {
        return JobNoticeLogLevel.INFO;
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
    //                                                                            Delegate
    //                                                                            ========
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
    public String toCronMethodDisp() {
        return cron4jRuntime.toCronMethodDisp();
    }

    @Override
    public String toRunMethodDisp() {
        return cron4jRuntime.toRunMethodDisp();
    }
}
