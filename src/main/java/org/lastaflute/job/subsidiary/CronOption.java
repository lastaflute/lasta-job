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
package org.lastaflute.job.subsidiary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.dbflute.optional.OptionalThing;
import org.lastaflute.job.LaScheduledJob;
import org.lastaflute.job.key.LaJobKey;
import org.lastaflute.job.key.LaJobUnique;
import org.lastaflute.job.log.JobNoticeLogLevel;

/**
 * @author jflute
 * @since 0.2.0 (2016/01/11 Monday)
 */
public class CronOption implements InitialCronOption, VaryingCronOption, JobSubAttr {

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    protected String jobTitle;
    protected LaJobUnique jobUnique;
    protected CronParamsSupplier paramsSupplier;
    protected List<LaJobKey> triggeringJobKeyList;
    protected JobNoticeLogLevel noticeLogLevel = JobNoticeLogLevel.INFO;

    // ===================================================================================
    //                                                                              Facade
    //                                                                              ======
    @Override
    public CronOption title(String jobTitle) {
        if (jobTitle == null || jobTitle.trim().isEmpty()) {
            throw new IllegalArgumentException("The argument 'jobTitle' should not be null or empty: " + jobTitle);
        }
        this.jobTitle = jobTitle;
        return this;
    }

    @Override
    public CronOption uniqueBy(String uniqueCode) {
        if (uniqueCode == null || uniqueCode.trim().isEmpty()) {
            throw new IllegalArgumentException("The argument 'uniqueCode' should not be null or empty: " + uniqueCode);
        }
        this.jobUnique = createJobUnique(uniqueCode);
        return this;
    }

    protected LaJobUnique createJobUnique(String uniqueCode) {
        return LaJobUnique.of(uniqueCode);
    }

    @Override
    public CronOption params(CronParamsSupplier noArgLambda) {
        if (noArgLambda == null) {
            throw new IllegalArgumentException("The argument 'noArgLambda' should not be null.");
        }
        paramsSupplier = noArgLambda;
        return this;
    }

    @Override
    public CronOption triggeredBy(LaScheduledJob triggeringJob) {
        if (triggeringJob == null) {
            throw new IllegalArgumentException("The argument 'triggeringJob' should not be null or empty: " + triggeringJob);
        }
        if (triggeringJobKeyList == null) {
            triggeringJobKeyList = new ArrayList<LaJobKey>();
        }
        triggeringJobKeyList.add(triggeringJob.getJobKey());
        return this;
    }

    // -----------------------------------------------------
    //                                      Notice Log Level
    //                                      ----------------
    @Override
    public CronOption changeNoticeLogToDebug() {
        noticeLogLevel = JobNoticeLogLevel.DEBUG;
        return this;
    }

    @Override
    public CronOption changeNoticeLogToSuppressed() {
        noticeLogLevel = JobNoticeLogLevel.SUPPRESSED;
        return this;
    }

    // ===================================================================================
    //                                                                      Basic Override
    //                                                                      ==============
    @Override
    public String toString() {
        final String uniqueExp = jobUnique != null ? "hasJobUnique(" + jobUnique + ")" : "noJobUnique";
        final String paramsExp = paramsSupplier != null ? "hasParams" : "noParams";
        return "option:{" + uniqueExp + ", " + paramsExp + ", " + noticeLogLevel + "}";
    }

    // ===================================================================================
    //                                                                            Accessor
    //                                                                            ========
    @Override
    public OptionalThing<String> getJobTitle() {
        return OptionalThing.ofNullable(jobTitle, () -> {
            throw new IllegalStateException("Not found the application job title.");
        });
    }

    @Override
    public OptionalThing<LaJobUnique> getJobUnique() {
        return OptionalThing.ofNullable(jobUnique, () -> {
            throw new IllegalStateException("Not found the application unique code.");
        });
    }

    @Override
    public OptionalThing<CronParamsSupplier> getParamsSupplier() {
        return OptionalThing.ofNullable(paramsSupplier, () -> {
            throw new IllegalStateException("Not found the parameters supplier.");
        });
    }

    public List<LaJobKey> getTriggeringJobKeyList() {
        return triggeringJobKeyList != null ? Collections.unmodifiableList(triggeringJobKeyList) : Collections.emptyList();
    }

    @Override
    public JobNoticeLogLevel getNoticeLogLevel() {
        return noticeLogLevel;
    }
}
