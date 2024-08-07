/*
 * Copyright 2015-2024 the original author or authors.
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
import java.util.Set;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.dbflute.helper.message.ExceptionMessageBuilder;
import org.dbflute.optional.OptionalThing;
import org.lastaflute.job.key.LaJobKey;
import org.lastaflute.job.key.LaJobUnique;
import org.lastaflute.job.subsidiary.CronConsumer;
import org.lastaflute.job.subsidiary.JobConcurrentExec;
import org.lastaflute.web.servlet.filter.bowgun.BowgunCurtainBefore;
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

    // -----------------------------------------------------
    //                                              Stateful
    //                                              --------
    protected static boolean bowgunEmptyScheduling; // used when initialization
    protected static boolean locked = true;

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    /** The current state of scheduling. (NotNull) */
    protected LaSchedulingNow schedulingNow = createEmptyNow(); // because of delayed initialization

    /** Is scheduling done? (scheduling is lazy loaded) */
    protected boolean schedulingDone;

    // ===================================================================================
    //                                                                          Initialize
    //                                                                          ==========
    /**
     * Initialize this component. <br>
     * This is basically called by DI setting file.
     */
    @PostConstruct
    public synchronized void initialize() {
        BowgunCurtainBefore.unlock();
        BowgunCurtainBefore.shootBowgunCurtainBefore(assistantDirector -> {
            if (!bowgunEmptyScheduling) { // for e.g. UnitTest
                startSchedule();
            }
            showBootLogging();
        });
    }

    protected void startSchedule() { // also called by destroy()
        schedulingNow = createStarter().start();
        schedulingDone = true;
    }

    protected void showBootLogging() {
        if (logger.isInfoEnabled()) {
            logger.info("[Job Manager]");
            logger.info(" schedulingNow: " + schedulingNow);
        }
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
    public OptionalThing<LaScheduledJob> findJobByUniqueOf(LaJobUnique jobUnique) {
        assertArgumentNotNull("jobUnique", jobUnique);
        @SuppressWarnings("unchecked")
        final OptionalThing<LaScheduledJob> job = (OptionalThing<LaScheduledJob>) schedulingNow.findJobByUniqueOf(jobUnique);
        return job;
    }

    @Override
    public List<LaScheduledJob> getJobList() {
        @SuppressWarnings("unchecked")
        final List<LaScheduledJob> jobList = (List<LaScheduledJob>) schedulingNow.getJobList();
        return jobList;
    }

    @Override
    public void schedule(CronConsumer oneArgLambda) {
        assertArgumentNotNull("oneArgLambda", oneArgLambda);
        // registration log later so no logging here
        //if (JobChangeLog.isLogEnabled()) {
        //    JobChangeLog.log("#job ...Scheduling new jobs: {}", oneArgLambda);
        //}
        schedulingNow.schedule(oneArgLambda);
    }

    // ===================================================================================
    //                                                                             Destroy
    //                                                                             =======
    @PreDestroy
    @Override
    public synchronized void destroy() {
        schedulingNow.destroy(); // asynchronous
        schedulingNow = createEmptyNow();
        schedulingDone = false;
    }

    // ===================================================================================
    //                                                                              Reboot
    //                                                                              ======
    @Override
    public synchronized void reboot() { // also called by unit test
        destroy();
        startSchedule();
        showBootLogging();
    }

    // ===================================================================================
    //                                                                         Initialized
    //                                                                         ===========
    @Override
    public synchronized boolean isSchedulingDone() {
        return schedulingDone;
    }

    // ===================================================================================
    //                                                                         Job History
    //                                                                         ===========
    @Override
    public List<LaJobHistory> searchJobHistoryList() {
        return schedulingNow.searchJobHistoryList();
    }

    // ===================================================================================
    //                                                                        Assist Logic
    //                                                                        ============
    protected LastaJobStarter createStarter() {
        return new LastaJobStarter();
    }

    protected LaSchedulingNow createEmptyNow() {
        return new EmptySchedulingNow();
    }

    protected class EmptySchedulingNow implements LaSchedulingNow {

        @Override
        public OptionalThing<? extends LaScheduledJob> findJobByKey(LaJobKey jobKey) {
            // air shot for job ending after destroy()
            //throwJobManagerNotInitializedYetException();
            return createEmptyOptional();
        }

        @Override
        public OptionalThing<? extends LaScheduledJob> findJobByUniqueOf(LaJobUnique jobUnique) {
            // air shot for job ending after destroy()
            //throwJobManagerNotInitializedYetException();
            return createEmptyOptional();
        }

        protected OptionalThing<LaScheduledJob> createEmptyOptional() { // may be called via JobManager before initialization
            return OptionalThing.ofNullable(null, () -> {
                throw new IllegalStateException("Not initialized yet, confirm Job initialization flow.");
            });
        }

        @Override
        public List<LaScheduledJob> getJobList() {
            // air shot for job ending after destroy()
            //throwJobManagerNotInitializedYetException();
            return Collections.emptyList(); // unreachable
        }

        @Override
        public void schedule(CronConsumer oneArgLambda) {
            throwJobManagerNotInitializedYetException();
        }

        @Override
        public List<LaJobHistory> searchJobHistoryList() {
            // air shot for job ending after destroy()
            //throwJobManagerNotInitializedYetException();
            return Collections.emptyList(); // unreachable
        }

        @Override
        public void setupNeighborConcurrent(String groupName, JobConcurrentExec concurrentExec, Set<LaJobKey> jobKeySet) {
            throwJobManagerNotInitializedYetException();
        }

        @Override
        public void destroy() {
            // air shot for unit test (can call reboot)
            //throwJobManagerNotInitializedYetException();
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + ":{*no scheduling}";
        }
    }

    protected void throwJobManagerNotInitializedYetException() {
        final ExceptionMessageBuilder br = new ExceptionMessageBuilder();
        br.addNotice("JobManager is not initialized yet at the timing.");
        br.addItem("Advice");
        br.addElement("You cannot use JobManager in your job scheduler");
        br.addElement("because JobManager uses the your scheduling result.");
        final String msg = br.buildExceptionMessage();
        throw new IllegalStateException(msg);
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
    //                                                              Bowgun EmptyScheduling
    //                                                              ======================
    public static synchronized void shootBowgunEmptyScheduling() { // called by e.g. UTFlute
        assertUnlocked();
        if (bowgunEmptyScheduling) { // already empty
            return;
        }
        if (logger.isInfoEnabled()) {
            logger.info("...Shooting bowgun empty scheduling: true");
        }
        bowgunEmptyScheduling = true; // should be before container initialization
        lock(); // auto-lock here, because of deep world
    }

    // ===================================================================================
    //                                                                         Config Lock
    //                                                                         ===========
    // also no info logging here because mainly used by UnitTest
    public static boolean isLocked() {
        return locked;
    }

    public static void lock() {
        if (locked) {
            return;
        }
        locked = true;
    }

    public static void unlock() {
        if (!locked) {
            return;
        }
        locked = false;
    }

    protected static void assertUnlocked() {
        if (!isLocked()) {
            return;
        }
        throw new IllegalStateException("The job manager is locked.");
    }
}
