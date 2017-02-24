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

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;

import org.dbflute.optional.OptionalThing;
import org.dbflute.util.DfTypeUtil;
import org.lastaflute.job.LaJob;
import org.lastaflute.job.LaJobRuntime;
import org.lastaflute.job.exception.JobStoppedException;
import org.lastaflute.job.key.LaJobKey;
import org.lastaflute.job.key.LaJobUnique;
import org.lastaflute.job.log.JobNoticeLogLevel;
import org.lastaflute.job.subsidiary.EndTitleRoll;

import it.sauronsoftware.cron4j.TaskExecutionContext;

/**
 * @author jflute
 * @since 0.2.0 (2016/01/09 Saturday)
 */
public class Cron4jRuntime implements LaJobRuntime {

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    // all 'final' attributes are not null
    protected final LaJobKey jobKey;
    protected final OptionalThing<String> jobTitle;
    protected final OptionalThing<LaJobUnique> jobUnique;
    protected final String cronExp;
    protected final Class<? extends LaJob> jobType;
    protected final Method runMethod;
    protected final Map<String, Object> parameterMap;
    protected final JobNoticeLogLevel noticeLogLevel;
    protected final TaskExecutionContext cron4jContext;
    protected EndTitleRoll endTitleRollData; // null allowed, specified by application in job
    protected boolean nextTriggerSuppressed;

    // ===================================================================================
    //                                                                         Constructor
    //                                                                         ===========
    public Cron4jRuntime(LaJobKey jobKey, OptionalThing<String> jobTitle, OptionalThing<LaJobUnique> jobUnique, String cronExp,
            Class<? extends LaJob> jobType, Map<String, Object> parameterMap, JobNoticeLogLevel noticeLogLevel,
            TaskExecutionContext cron4jContext) {
        this.jobKey = jobKey;
        this.jobTitle = jobTitle;
        this.jobUnique = jobUnique;
        this.cronExp = cronExp;
        this.jobType = jobType;
        try {
            this.runMethod = jobType.getMethod("run", new Class<?>[] { LaJobRuntime.class });
        } catch (Exception e) { // no way
            throw new IllegalStateException("Not found the run method in the job: " + jobType, e);
        }
        this.parameterMap = Collections.unmodifiableMap(parameterMap);
        this.noticeLogLevel = noticeLogLevel;
        this.cron4jContext = cron4jContext;
    }

    // ===================================================================================
    //                                                                      End-Title Roll
    //                                                                      ==============
    @Override
    public void showEndTitleRoll(Consumer<EndTitleRoll> dataLambda) {
        assertArgumentNotNull("dataLambda", dataLambda);
        if (endTitleRollData != null) {
            String msg = "Already existing end-title roll data: " + endTitleRollData + " runtime=" + toString();
            throw new IllegalStateException(msg);
        }
        endTitleRollData = newEndTitleRoll();
        dataLambda.accept(endTitleRollData);
    }

    protected EndTitleRoll newEndTitleRoll() {
        return new EndTitleRoll();
    }

    // ===================================================================================
    //                                                                            Stop Job
    //                                                                            ========
    @Override
    public void stopIfNeeds() {
        if (isStopRequested()) {
            throw new JobStoppedException("Stopped the job: " + toString());
        }
    }

    protected boolean isStopRequested() {
        return cron4jContext.isStopped();
    }

    // ===================================================================================
    //                                                                    Business Failure
    //                                                                    ================
    @Override
    public void suppressNextTrigger() {
        nextTriggerSuppressed = true;
    }

    // ===================================================================================
    //                                                                             Display
    //                                                                             =======
    @Override
    public String toCronMethodDisp() {
        return cronExp + " " + toRunMethodDisp();
    }

    @Override
    public String toRunMethodDisp() {
        final StringBuilder sb = new StringBuilder();
        sb.append(buildRunMethodExp()).append(buildJobUniqueSuffix());
        return sb.toString();
    }

    protected String buildRunMethodExp() {
        return jobType.getSimpleName() + "@" + runMethod.getName() + "()";
    }

    protected String buildJobUniqueSuffix() {
        return jobUnique.map(uq -> " [" + uq + "]").orElse("");
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

    // ===================================================================================
    //                                                                      Basic Override
    //                                                                      ==============
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(DfTypeUtil.toClassTitle(this));
        sb.append(":{").append(jobKey);
        sb.append(jobTitle.map(title -> ", " + title).orElse(""));
        sb.append(jobUnique.map(uq -> ", " + uq).orElse(""));
        sb.append(", ").append(cronExp);
        sb.append(", ").append(jobType.getSimpleName()).append("@").append(runMethod.getName()).append("()");
        sb.append(", params=").append(parameterMap);
        sb.append("}@").append(Integer.toHexString(hashCode()));
        return sb.toString();
    }

    // ===================================================================================
    //                                                                            Accessor
    //                                                                            ========
    @Override
    public LaJobKey getJobKey() {
        return jobKey;
    }

    @Override
    public OptionalThing<String> getJobTitle() {
        return jobTitle;
    }

    @Override
    public OptionalThing<LaJobUnique> getJobUnique() {
        return jobUnique;
    }

    @Override
    public String getCronExp() {
        return cronExp;
    }

    @Override
    public Class<? extends LaJob> getJobType() {
        return jobType;
    }

    @Override
    public Method getRunMethod() {
        return runMethod;
    }

    @Override
    public Map<String, Object> getParameterMap() {
        return parameterMap; // already unmodifiable
    }

    @Override
    public JobNoticeLogLevel getNoticeLogLevel() {
        return noticeLogLevel;
    }

    public TaskExecutionContext getCron4jContext() { // not interface method because of cron4j's one 
        return cron4jContext;
    }

    @Override
    public OptionalThing<EndTitleRoll> getEndTitleRoll() {
        return OptionalThing.ofNullable(endTitleRollData, () -> {
            throw new IllegalStateException("Not found the end-title roll data in the runtime: " + toString());
        });
    }

    @Override
    public boolean isSuppressNextTrigger() {
        return nextTriggerSuppressed;
    }
}
