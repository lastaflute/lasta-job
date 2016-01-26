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
package org.lastaflute.job.subsidiary;

import org.dbflute.optional.OptionalThing;
import org.lastaflute.job.key.LaJobUnique;
import org.lastaflute.job.log.JobNoticeLogLevel;

/**
 * @author jflute
 * @since 0.2.0 (2016/01/11 Monday)
 */
public class CronOption implements InitialCronOption, VaryingCronOption {

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    protected LaJobUnique jobUnique;
    protected CronParamsSupplier paramsSupplier;
    protected JobNoticeLogLevel noticeLogLevel = JobNoticeLogLevel.INFO;

    // ===================================================================================
    //                                                                              Facade
    //                                                                              ======
    @Override
    public CronOption uniqueBy(String uniqueCode) {
        if (uniqueCode == null) {
            throw new IllegalArgumentException("The argument 'uniqueCode' should not be null.");
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

    @Override
    public JobNoticeLogLevel getNoticeLogLevel() {
        return noticeLogLevel;
    }
}
