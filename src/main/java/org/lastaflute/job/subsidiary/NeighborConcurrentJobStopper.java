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

import java.util.Collection;
import java.util.Map;
import java.util.function.BiConsumer;
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
    protected final Map<String, NeighborConcurrentGroup> neighborConcurrentGroupMap; // inherit outer map for synchronization

    // ===================================================================================
    //                                                                         Constructor
    //                                                                         ===========
    public NeighborConcurrentJobStopper(Function<LaJobKey, OptionalThing<? extends ReadableJobState>> jobFinder,
            Map<String, NeighborConcurrentGroup> neighborConcurrentGroupMap) {
        this.jobFinder = jobFinder;
        this.neighborConcurrentGroupMap = neighborConcurrentGroupMap;
    }

    // ===================================================================================
    //                                                                               Stop
    //                                                                              ======
    public OptionalThing<RunnerResult> stopIfNeeds(ReadableJobState me, Function<ReadableJobState, String> stateDisp) {
        final Collection<NeighborConcurrentGroup> groupList = neighborConcurrentGroupMap.values();
        doStopIfNeeds(me, groupList, JobConcurrentExec.QUIT, (neighbor, group) -> {
            noticeSilentlyQuit(me, neighbor, stateDisp, group);
        });
        doStopIfNeeds(me, groupList, JobConcurrentExec.ERROR, (neighbor, group) -> {
            throwJobNeighborConcurrentlyExecutingException(me, neighbor, stateDisp, group);
        });
        return OptionalThing.empty();
    }

    protected void doStopIfNeeds(ReadableJobAttr me, Collection<NeighborConcurrentGroup> groupList, JobConcurrentExec concurrentExec,
            BiConsumer<ReadableJobState, NeighborConcurrentGroup> action) {
        groupList.stream().filter(group -> concurrentExec.equals(group.getConcurrentExec())).forEach(group -> {
            group.getNeighborJobKeySet().forEach(neighborJobKey -> {
                if (me.getJobKey().equals(neighborJobKey)) { // myself
                    return; // skip
                }
                jobFinder.apply(neighborJobKey).ifPresent(neighbor -> { // ignoring unscheduled job, no problem
                    if (neighbor.isExecutingNow()) { // no lock here (for cross VM hook)
                        action.accept(neighbor, group); // so may be ended while message building
                    }
                });
            });
        });
    }

    // -----------------------------------------------------
    //                                                Notice
    //                                                ------
    protected void noticeSilentlyQuit(ReadableJobState me, ReadableJobState neighbor, Function<ReadableJobState, String> stateDisp,
            NeighborConcurrentGroup group) { // in varying lock
        final JobNoticeLogLevel noticeLogLevel = me.getNoticeLogLevel(); // in varying lock so exclusive
        JobNoticeLog.log(noticeLogLevel, () -> {
            return "...Quitting the job for already executing neighbor job: " + buildMeAndNeighbor(me, neighbor, stateDisp, group);
        });
    }

    // -----------------------------------------------------
    //                                             Exception
    //                                             ---------
    protected void throwJobNeighborConcurrentlyExecutingException(ReadableJobState me, ReadableJobState neighbor,
            Function<ReadableJobState, String> stateDisp, NeighborConcurrentGroup group) {
        throw new JobNeighborConcurrentlyExecutingException(buildConcurrentMessage(me, neighbor, stateDisp, group));
    }

    protected String buildConcurrentMessage(ReadableJobState me, ReadableJobState neighbor, Function<ReadableJobState, String> stateDisp,
            NeighborConcurrentGroup group) {
        return "Already executing the neighbor job: " + buildMeAndNeighbor(me, neighbor, stateDisp, group);
    }

    // -----------------------------------------------------
    //                                               Display
    //                                               -------
    protected String buildMeAndNeighbor(ReadableJobState me, ReadableJobState neighbor, Function<ReadableJobState, String> stateDisp,
            NeighborConcurrentGroup group) {
        final StringBuilder sb = new StringBuilder();
        sb.append("me=").append(me.toIdentityDisp());
        sb.append(", neighbor=").append(neighbor.toIdentityDisp());
        sb.append("(").append(stateDisp.apply(neighbor)).append(")");
        sb.append(", group=").append(group.getGroupName());
        return sb.toString();
    }
}
