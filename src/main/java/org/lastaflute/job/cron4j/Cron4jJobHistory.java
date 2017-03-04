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
import java.util.List;

import org.dbflute.optional.OptionalThing;
import org.lastaflute.job.LaJobHistory;
import org.lastaflute.job.key.LaJobKey;
import org.lastaflute.job.key.LaJobUnique;
import org.lastaflute.job.subsidiary.ExecResultType;
import org.lastaflute.job.subsidiary.SavedHistoryCache;

import it.sauronsoftware.cron4j.TaskExecutor;

/**
 * @author jflute
 * @since 0.2.8 (2017/03/04 Saturday)
 */
public class Cron4jJobHistory implements LaJobHistory {

    // ===================================================================================
    //                                                                       History Cache
    //                                                                       =============
    protected static final SavedHistoryCache historyCache = new SavedHistoryCache();

    public synchronized static OptionalThing<LaJobHistory> find(TaskExecutor taskExecutor) {
        return historyCache.find(generateHistoryKey(taskExecutor));
    }

    public synchronized static void record(TaskExecutor taskExecutor, LaJobHistory jobHistory, int limit) {
        historyCache.record(generateHistoryKey(taskExecutor), jobHistory, limit);
    }

    public synchronized static List<LaJobHistory> list() {
        return historyCache.list();
    }

    protected static String generateHistoryKey(TaskExecutor taskExecutor) {
        return taskExecutor.getGuid(); // trust it
    }

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    protected final LaJobKey jobKey; // not null
    protected final OptionalThing<String> jobTitle; // not null
    protected final OptionalThing<LaJobUnique> jobUnique; // not null
    protected final OptionalThing<String> cronExp; // not null
    protected final String jobTypeFqcn; // not null, not save class directly to avoid hot-deploy trouble
    protected final LocalDateTime beginTime; // not null
    protected final LocalDateTime endTime; // not null
    protected final ExecResultType execResultType; // not null
    protected final Throwable cause; // null allowed

    // ===================================================================================
    //                                                                         Constructor
    //                                                                         ===========
    public Cron4jJobHistory(LaJobKey jobKey, OptionalThing<String> jobTitle, OptionalThing<LaJobUnique> jobUnique // identity
            , OptionalThing<String> cronExp, String jobTypeFqcn // cron
            , LocalDateTime beginTime, LocalDateTime endTime, ExecResultType execResultType, Throwable cause // execution
    ) {
        this.jobKey = jobKey;
        this.jobTitle = jobTitle;
        this.jobUnique = jobUnique;
        this.cronExp = cronExp;
        this.jobTypeFqcn = jobTypeFqcn;
        this.beginTime = beginTime;
        this.endTime = endTime;
        this.execResultType = execResultType;
        this.cause = cause;
    }

    // ===================================================================================
    //                                                                      Basic Override
    //                                                                      ==============
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("history:{");
        sb.append(jobKey);
        sb.append(jobTitle.map(title -> ", " + title).orElse(""));
        sb.append(jobUnique.map(uq -> ", " + uq).orElse(""));
        sb.append(cronExp.map(cron -> ", " + cron).orElse(""));
        sb.append(", ").append(jobTypeFqcn);
        sb.append(", ").append(execResultType);
        if (cause != null) {
            sb.append(", ").append(cause.getClass().getSimpleName());
        }
        sb.append("}@").append(Integer.toHexString(hashCode()));
        return sb.toString();
    }

    // ===================================================================================
    //                                                                            Accessor
    //                                                                            ========
    // -----------------------------------------------------
    //                                         Job Attribute
    //                                         -------------
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
    public OptionalThing<String> getCronExp() {
        return cronExp;
    }

    @Override
    public String getJobTypeFqcn() {
        return jobTypeFqcn;
    }

    // -----------------------------------------------------
    //                                      Execution Result
    //                                      ----------------
    @Override
    public LocalDateTime getBeginTime() {
        return beginTime;
    }

    @Override
    public LocalDateTime getEndTime() {
        return endTime;
    }

    @Override
    public ExecResultType getExecResultType() {
        return execResultType;
    }

    @Override
    public OptionalThing<Throwable> getCause() {
        return OptionalThing.ofNullable(cause, () -> {
            throw new IllegalStateException("Not found the cause: " + toString());
        });
    }
}
