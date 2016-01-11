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

import java.util.Collections;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.dbflute.optional.OptionalThing;
import org.lastaflute.job.key.LaJobKey;
import org.lastaflute.job.key.LaJobUniqueCode;
import org.lastaflute.job.subsidiary.CronConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author jflute
 * @since 0.2.0 (2016/01/11 Monday)
 */
public class SimpleJobManager implements JobManager {

    // ===================================================================================
    //                                                                          Definition
    //                                                                          ==========
    private static final Logger logger = LoggerFactory.getLogger(SimpleJobManager.class);

    /** The current state of scheduling. (NotNull) */
    protected LaSchedulingNow schedulingNow = createEmptyNow(); // because of delayed initialization

    // ===================================================================================
    //                                                                          Initialize
    //                                                                          ==========
    /**
     * Initialize this component. <br>
     * This is basically called by DI setting file.
     */
    @PostConstruct
    public synchronized void initialize() {
        new Thread(() -> { // use plain thread for silent asynchronous
            delayBeforeStartCron();
            try {
                schedulingNow = createStarter().start();
                showBootLogging();
            } catch (Throwable cause) {
                logger.error("Failed to start job scheduling.", cause);
            }
        }).start();
    }

    protected void delayBeforeStartCron() {
        try {
            Thread.sleep(getStartDelayMillis()); // delay to wait for finishing application boot
        } catch (InterruptedException ignored) {}
    }

    protected long getStartDelayMillis() {
        return 5000L;
    }

    protected void showBootLogging() {
        if (logger.isInfoEnabled()) {
            logger.info("[Job Manager]");
            logger.info(" schedulingNow: " + schedulingNow);
        }
    }

    // ===================================================================================
    //                                                                             Destroy
    //                                                                             =======
    @PreDestroy
    public synchronized void destroy() {
        destroySchedule();
    }

    // ===================================================================================
    //                                                                         Control Job
    //                                                                         ===========
    @Override
    public OptionalThing<LaScheduledJob> findJobByKey(LaJobKey jobKey) {
        assertArgumentNotNull("jobKey", jobKey);
        @SuppressWarnings("unchecked")
        final OptionalThing<LaScheduledJob> job = (OptionalThing<LaScheduledJob>) schedulingNow.findJobByKey(jobKey);
        return job;
    }

    @Override
    public OptionalThing<LaScheduledJob> findJobByUniqueCode(LaJobUniqueCode uniqueCode) {
        assertArgumentNotNull("uniqueCode", uniqueCode);
        @SuppressWarnings("unchecked")
        final OptionalThing<LaScheduledJob> job = (OptionalThing<LaScheduledJob>) schedulingNow.findJobByUniqueCode(uniqueCode);
        return job;
    }

    @Override
    public List<LaScheduledJob> getJobList() {
        @SuppressWarnings("unchecked")
        final List<LaScheduledJob> jobList = (List<LaScheduledJob>) schedulingNow.getJobList();
        return jobList;
    }

    @Override
    public void registerJob(CronConsumer oneArgLambda) {
        assertArgumentNotNull("oneArgLambda", oneArgLambda);
        schedulingNow.registerJob(oneArgLambda);
    }

    @Override
    public synchronized void destroySchedule() {
        schedulingNow.destroySchedule();
    }

    // ===================================================================================
    //                                                                        Assist Logic
    //                                                                        ============
    protected LastaJobStarter createStarter() {
        return new LastaJobStarter();
    }

    protected LaSchedulingNow createEmptyNow() {
        return new LaSchedulingNow() {

            @Override
            public OptionalThing<? extends LaScheduledJob> findJobByKey(LaJobKey jobKey) {
                return OptionalThing.empty();
            }

            @Override
            public OptionalThing<? extends LaScheduledJob> findJobByUniqueCode(LaJobUniqueCode uniqueCode) {
                return OptionalThing.empty();
            }

            @Override
            public List<LaScheduledJob> getJobList() {
                return Collections.emptyList();
            }

            @Override
            public void registerJob(CronConsumer oneArgLambda) {
            }

            @Override
            public void destroySchedule() {
            }

            @Override
            public void clearClosedJob() {
            }
        };
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
