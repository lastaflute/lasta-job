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

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;

import org.dbflute.util.DfTypeUtil;
import org.lastaflute.job.LaJob;
import org.lastaflute.job.LaJobRuntime;

import it.sauronsoftware.cron4j.TaskExecutionContext;

/**
 * @author jflute
 * @since 0.2.0 (2016/01/09 Saturday)
 */
public class Cron4jRuntime implements LaJobRuntime {

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    protected final String cronExp;
    protected final Class<? extends LaJob> jobType;
    protected final Method runMethod;
    protected final Map<String, Object> parameterMap;
    protected final TaskExecutionContext cron4jContext;

    // ===================================================================================
    //                                                                         Constructor
    //                                                                         ===========
    public Cron4jRuntime(String cronExp, Class<? extends LaJob> jobType, Map<String, Object> parameterMap,
            TaskExecutionContext cron4jContext) {
        this.cronExp = cronExp;
        this.jobType = jobType;
        try {
            this.runMethod = jobType.getMethod("run", new Class<?>[] { LaJobRuntime.class });
        } catch (Exception e) { // no way
            throw new IllegalStateException("Not found the run method in the job: " + jobType, e);
        }
        this.parameterMap = Collections.unmodifiableMap(parameterMap);
        this.cron4jContext = cron4jContext;
    }

    // ===================================================================================
    //                                                                             Display
    //                                                                             =======
    @Override
    public String toCronMethodDisp() {
        final StringBuilder sb = new StringBuilder();
        sb.append(cronExp);
        sb.append(" ").append(jobType.getSimpleName()).append("@").append(runMethod.getName()).append("()");
        return sb.toString();
    }

    @Override
    public String toMethodDisp() {
        final StringBuilder sb = new StringBuilder();
        sb.append(jobType.getSimpleName()).append("@").append(runMethod.getName()).append("()");
        return sb.toString();
    }

    // ===================================================================================
    //                                                                      Basic Override
    //                                                                      ==============
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(DfTypeUtil.toClassTitle(this));
        sb.append(":{").append(cronExp);
        sb.append(", ").append(jobType.getSimpleName()).append("@").append(runMethod.getName()).append("()");
        sb.append(", params=").append(parameterMap);
        sb.append("}@").append(Integer.toHexString(hashCode()));
        return sb.toString();
    }

    // ===================================================================================
    //                                                                            Accessor
    //                                                                            ========
    @Override
    public Class<? extends LaJob> getJobType() {
        return jobType;
    }

    @Override
    public Method getRunMethod() {
        return runMethod;
    }

    public Map<String, Object> getParameterMap() {
        return parameterMap;
    }

    public TaskExecutionContext getCron4jContext() {
        return cron4jContext;
    }
}
