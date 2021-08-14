/*
 * Copyright 2015-2021 the original author or authors.
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

import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.dbflute.optional.OptionalThing;
import org.lastaflute.job.exception.JobConcurrentlyExecutingException;
import org.lastaflute.job.log.JobNoticeLog;
import org.lastaflute.job.log.JobNoticeLogLevel;

/**
 * @author jflute
 * @since 0.4.1 (2017/03/20 Monday)
 */
public class ConcurrentJobStopper {

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    protected final Predicate<ReadableJobState> jobExecutingDeterminer;
    protected Consumer<ReadableJobState> waitress; // option, basically for cross VM

    // ===================================================================================
    //                                                                         Constructor
    //                                                                         ===========
    public ConcurrentJobStopper(Predicate<ReadableJobState> jobExecutingDeterminer) {
        this.jobExecutingDeterminer = jobExecutingDeterminer;
    }

    // -----------------------------------------------------
    //                                                Option
    //                                                ------
    public ConcurrentJobStopper waitress(Consumer<ReadableJobState> waitress) {
        if (waitress == null) {
            throw new IllegalArgumentException("The argument 'waitress' should not be null.");
        }
        this.waitress = waitress;
        return this;
    }

    // ===================================================================================
    //                                                                       Stop if needs
    //                                                                       =============
    public OptionalThing<RunnerResult> stopIfNeeds(ReadableJobState jobState, Supplier<String> stateDisp) {
        if (jobExecutingDeterminer.test(jobState)) {
            final JobConcurrentExec concurrentExec = jobState.getConcurrentExec();
            if (concurrentExec.equals(JobConcurrentExec.QUIT)) {
                noticeSilentlyQuit(jobState, stateDisp);
                return OptionalThing.of(RunnerResult.asQuitByConcurrent());
            } else if (concurrentExec.equals(JobConcurrentExec.ERROR)) {
                throwJobConcurrentlyExecutingException(jobState, stateDisp);
            } else if (concurrentExec.equals(JobConcurrentExec.WAIT)) {
                if (waitress != null) {
                    // this is not perfect concurrent control, because of no lock
                    // so basically used only for cross VM concurrent control
                    waitress.accept(jobState);
                }
            }
        }
        // will wait for previous job naturally by synchronization later if waitress is unused
        return OptionalThing.empty();
    }

    protected void noticeSilentlyQuit(ReadableJobAttr jobAttr, Supplier<String> stateDisp) {
        final JobNoticeLogLevel noticeLogLevel = jobAttr.getNoticeLogLevel(); // in varying lock so exclusive
        JobNoticeLog.log(noticeLogLevel, () -> {
            final String identityDisp = jobAttr.toIdentityDisp();
            return "...Quitting the job for already executing job: " + identityDisp + "(" + stateDisp.get() + ")";
        });
    }

    protected void throwJobConcurrentlyExecutingException(ReadableJobAttr jobAttr, Supplier<String> stateDisp) {
        throw new JobConcurrentlyExecutingException(buildConcurrentMessage(jobAttr, stateDisp));
    }

    protected String buildConcurrentMessage(ReadableJobAttr jobAttr, Supplier<String> stateDisp) {
        final String identityDisp = jobAttr.toIdentityDisp();
        return "Already executing the job: " + identityDisp + "(" + stateDisp.get() + ")";
    }
}
