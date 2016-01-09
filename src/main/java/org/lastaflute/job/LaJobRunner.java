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
package org.lastaflute.job;

import java.util.function.Supplier;

import org.dbflute.util.DfReflectionUtil;
import org.lastaflute.core.util.ContainerUtil;
import org.lastaflute.di.core.smart.hot.HotdeployLock;
import org.lastaflute.di.core.smart.hot.HotdeployUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author jflute
 * @since 0.1.0 (2016/01/10 Sunday)
 */
public class LaJobRunner {

    // ===================================================================================
    //                                                                          Definition
    //                                                                          ==========
    private static final Logger logger = LoggerFactory.getLogger(LaJobRunner.class);

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    protected final Supplier<Class<? extends LaJob>> jobTypeSupplier;
    protected final LaJobContext jobContext;

    // ===================================================================================
    //                                                                         Constructor
    //                                                                         ===========
    public LaJobRunner(Supplier<Class<? extends LaJob>> jobTypeSupplier, LaJobContext jobContext) {
        this.jobTypeSupplier = jobTypeSupplier;
        this.jobContext = jobContext;
    }

    // ===================================================================================
    //                                                                                Run
    //                                                                               =====
    public void run() {
        if (!HotdeployUtil.isHotdeploy()) { // e.g. production, unit-test
            doRun();
        }
        synchronized (HotdeployLock.class) { // #thiking: cannot hotdeploy, why?
            HotdeployUtil.start();
            try {
                doRun();
            } finally {
                HotdeployUtil.stop();
            }
        }
    }

    protected void doRun() { // #thiking: notice log, access context, exception handling
        try {
            actuallyRun();
        } catch (Throwable e) {
            logger.error("TODO jflute", e);
        }
    }

    protected void actuallyRun() {
        final Class<? extends LaJob> jobType = jobTypeSupplier.get();
        final LaJob job = ContainerUtil.getComponent(jobType);
        job.run(jobContext);
    }

    // ===================================================================================
    //                                                                          Create Job
    //                                                                          ==========
    protected LaJob createJob(Class<? extends LaJob> jobType) {
        final LaJob job = newJob(jobType);
        inject(job);
        return job;
    }

    protected LaJob newJob(Class<? extends LaJob> jobType) {
        return (LaJob) DfReflectionUtil.newInstance(jobType);
    }

    // ===================================================================================
    //                                                                           Component
    //                                                                           =========
    protected void inject(Object target) {
        ContainerUtil.injectSimply(target);
    }
}
