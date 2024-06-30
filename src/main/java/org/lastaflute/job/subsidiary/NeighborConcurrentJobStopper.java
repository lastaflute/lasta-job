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
package org.lastaflute.job.subsidiary;

import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

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
    protected final Predicate<ReadableJobState> jobExecutingDeterminer; // for neighbor job
    protected final Function<LaJobKey, OptionalThing<? extends ReadableJobState>> jobFinder; // for neighbor job
    protected final List<NeighborConcurrentGroup> neighborConcurrentGroupList; // inherit outer list for synchronization
    protected Consumer<ReadableJobState> waitress; // basically for cross VM

    // ===================================================================================
    //                                                                         Constructor
    //                                                                         ===========
    public NeighborConcurrentJobStopper(Predicate<ReadableJobState> jobExecutingDeterminer,
            Function<LaJobKey, OptionalThing<? extends ReadableJobState>> jobFinder,
            List<NeighborConcurrentGroup> neighborConcurrentGroupList) {
        this.jobExecutingDeterminer = jobExecutingDeterminer;
        this.jobFinder = jobFinder;
        this.neighborConcurrentGroupList = neighborConcurrentGroupList;
    }

    // -----------------------------------------------------
    //                                                Option
    //                                                ------
    public NeighborConcurrentJobStopper waitress(Consumer<ReadableJobState> waitress) {
        if (waitress == null) {
            throw new IllegalArgumentException("The argument 'waitress' should not be null.");
        }
        this.waitress = waitress;
        return this;
    }

    // ===================================================================================
    //                                                                       Stop if needs
    //                                                                       =============
    public OptionalThing<RunnerResult> stopIfNeeds(ReadableJobAttr me, Function<ReadableJobState, String> stateDisp) {
        final OptionalThing<RunnerResult> quitResult = doStopIfNeeds(me, JobConcurrentExec.QUIT, (neighbor, group) -> {
            noticeSilentlyQuit(me, neighbor, stateDisp, group);
            return RunnerResult.asQuitByConcurrent();
        });
        if (quitResult.isPresent()) {
            return quitResult;
        }
        final OptionalThing<RunnerResult> errorResult = doStopIfNeeds(me, JobConcurrentExec.ERROR, (neighbor, group) -> {
            throwJobNeighborConcurrentlyExecutingException(me, neighbor, stateDisp, group);
            return null; // unreachable
        });
        if (quitResult.isPresent()) {
            return errorResult;
        }
        if (waitress != null) {
            // this is not perfect concurrent control, because of no lock
            // so basically used only for cross VM concurrent control
            doStopIfNeeds(me, JobConcurrentExec.WAIT, (neighbor, group) -> {
                waitress.accept(neighbor);
                return null; // as empty (converted to empty later)
            });
        }
        // will wait for previous job naturally by synchronization later if waitress is unused
        return OptionalThing.empty();
    }

    protected OptionalThing<RunnerResult> doStopIfNeeds(ReadableJobAttr me, JobConcurrentExec concurrentExec,
            BiFunction<ReadableJobState, NeighborConcurrentGroup, RunnerResult> action) {
        final List<NeighborConcurrentGroup> filteredGroupList = neighborConcurrentGroupList.stream().filter(group -> {
            return concurrentExec.equals(group.getConcurrentExec());
        }).collect(Collectors.toList());
        for (NeighborConcurrentGroup group : filteredGroupList) {
            final Set<LaJobKey> neighborJobKeySet = group.getNeighborJobKeySet();
            for (LaJobKey neighborJobKey : neighborJobKeySet) {
                if (me.getJobKey().equals(neighborJobKey)) { // myself
                    continue; // skip
                }
                final OptionalThing<? extends ReadableJobState> optJobState = jobFinder.apply(neighborJobKey);
                if (!optJobState.isPresent()) { // ignoring unscheduled job, no problem
                    return OptionalThing.empty();
                }
                final ReadableJobState neighbor = optJobState.get();
                if (jobExecutingDeterminer.test(neighbor)) { // no lock here (for cross VM hook)
                    return OptionalThing.ofNullable(action.apply(neighbor, group), () -> { // so may be ended while message building
                        throw new IllegalStateException("Not found the neighbor concurrent runner result: " + neighbor);
                    });
                }
            }
        }
        return OptionalThing.empty();
    }

    // -----------------------------------------------------
    //                                                Notice
    //                                                ------
    protected void noticeSilentlyQuit(ReadableJobAttr me, ReadableJobState neighbor, Function<ReadableJobState, String> stateDisp,
            NeighborConcurrentGroup group) { // in varying lock
        final JobNoticeLogLevel noticeLogLevel = me.getNoticeLogLevel(); // in varying lock so exclusive
        JobNoticeLog.log(noticeLogLevel, () -> {
            return "...Quitting the job for already executing neighbor job: " + buildMeAndNeighbor(me, neighbor, stateDisp, group);
        });
    }

    // -----------------------------------------------------
    //                                             Exception
    //                                             ---------
    protected void throwJobNeighborConcurrentlyExecutingException(ReadableJobAttr me, ReadableJobState neighbor,
            Function<ReadableJobState, String> stateDisp, NeighborConcurrentGroup group) {
        throw new JobNeighborConcurrentlyExecutingException(buildConcurrentMessage(me, neighbor, stateDisp, group));
    }

    protected String buildConcurrentMessage(ReadableJobAttr me, ReadableJobState neighbor, Function<ReadableJobState, String> stateDisp,
            NeighborConcurrentGroup group) {
        return "Already executing the neighbor job: " + buildMeAndNeighbor(me, neighbor, stateDisp, group);
    }

    // -----------------------------------------------------
    //                                               Display
    //                                               -------
    protected String buildMeAndNeighbor(ReadableJobAttr me, ReadableJobState neighbor, Function<ReadableJobState, String> stateDisp,
            NeighborConcurrentGroup group) {
        final StringBuilder sb = new StringBuilder();
        sb.append("me=").append(me.toIdentityDisp());
        sb.append(", neighbor=").append(neighbor.toIdentityDisp());
        sb.append("(").append(stateDisp.apply(neighbor)).append(")");
        sb.append(", group=").append(group.getGroupName());
        return sb.toString();
    }
}
