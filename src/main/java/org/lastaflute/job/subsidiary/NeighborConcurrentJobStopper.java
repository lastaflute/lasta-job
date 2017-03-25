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

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import org.dbflute.optional.OptionalThing;
import org.lastaflute.job.exception.JobNeighborConcurrentlyExecutingException;
import org.lastaflute.job.key.LaJobKey;
import org.lastaflute.job.log.JobNoticeLog;
import org.lastaflute.job.log.JobNoticeLogLevel;

/**
 * @author jflute
 * @since 0.4.1 (2017/03/25 Saturday)
 */
public class NeighborConcurrentJobStopper {

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    protected final Function<LaJobKey, OptionalThing<? extends ReadableJobState>> jobFinder;

    // ===================================================================================
    //                                                                         Constructor
    //                                                                         ===========
    public NeighborConcurrentJobStopper(Function<LaJobKey, OptionalThing<? extends ReadableJobState>> jobFinder) {
        this.jobFinder = jobFinder;
    }

    // ===================================================================================
    //                                                                               Stop
    //                                                                              ======
    public OptionalThing<RunnerResult> stopIfNeeds(ReadableJobState jobState, Function<ReadableJobState, String> stateDisp) {
        final List<NeighborConcurrentGroup> groupList = jobState.getNeighborConcurrentGroupList();
        doStopIfNeeds(groupList, JobConcurrentExec.QUIT, neighborJobState -> {
            noticeSilentlyQuit(jobState, neighborJobState, stateDisp);
        });
        doStopIfNeeds(groupList, JobConcurrentExec.ERROR, neighborJobState -> {
            throwJobNeighborConcurrentlyExecutingException(jobState, neighborJobState, stateDisp);
        });
        return OptionalThing.empty();
    }

    protected void doStopIfNeeds(List<NeighborConcurrentGroup> groupList, JobConcurrentExec concurrentExec,
            Consumer<ReadableJobState> action) {
        groupList.stream().filter(group -> concurrentExec.equals(group.getConcurrentExec())).forEach(group -> {
            group.getNeighborJobKeySet().forEach(neighborJobKey -> {
                jobFinder.apply(neighborJobKey).ifPresent(neighborJobState -> { // ignoring unscheduled job, no problem
                    if (neighborJobState.isExecutingNow()) { // no lock here (for cross VM hook)
                        action.accept(neighborJobState); // so may be ended while message building
                    }
                });
            });
        });
    }

    // -----------------------------------------------------
    //                                                Notice
    //                                                ------
    protected void noticeSilentlyQuit(ReadableJobState me, ReadableJobState neighbor, Function<ReadableJobState, String> stateDisp) { // in varying lock
        final JobNoticeLogLevel noticeLogLevel = me.getNoticeLogLevel(); // in varying lock so exclusive
        JobNoticeLog.log(noticeLogLevel, () -> {
            return "...Quitting the job for already executing neighbor job: " + buildMeAndNeighbor(me, neighbor, stateDisp);
        });
    }

    // -----------------------------------------------------
    //                                             Exception
    //                                             ---------
    protected void throwJobNeighborConcurrentlyExecutingException(ReadableJobState me, ReadableJobState neighbor,
            Function<ReadableJobState, String> stateDisp) {
        throw new JobNeighborConcurrentlyExecutingException(buildConcurrentMessage(me, neighbor, stateDisp));
    }

    protected String buildConcurrentMessage(ReadableJobState me, ReadableJobState neighbor, Function<ReadableJobState, String> stateDisp) {
        return "Already executing the neighbor job: " + buildMeAndNeighbor(me, neighbor, stateDisp);
    }

    // -----------------------------------------------------
    //                                               Display
    //                                               -------
    protected String buildMeAndNeighbor(ReadableJobState me, ReadableJobState neighbor, Function<ReadableJobState, String> stateDisp) {
        final StringBuilder sb = new StringBuilder();
        sb.append("me=").append(me.toIdentityDisp());
        sb.append(", neighbor=").append(neighbor.toIdentityDisp());
        sb.append("(").append(stateDisp.apply(neighbor)).append(")");
        return sb.toString();
    }
}
