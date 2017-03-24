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

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.dbflute.optional.OptionalThing;
import org.lastaflute.job.exception.JobConcurrentlyExecutingException;
import org.lastaflute.job.exception.JobConcurrentlyNeighborExecutingException;
import org.lastaflute.job.key.LaJobKey;
import org.lastaflute.job.log.JobNoticeLog;
import org.lastaflute.job.log.JobNoticeLogLevel;

/**
 * @author jflute
 * @since 0.4.1 (2017/03/20 Monday)
 */
public class ConcurrentJobStopper {

    // ===================================================================================
    //                                                                 (Myself) Concurrent
    //                                                                 ===================
    public OptionalThing<RunnerResult> stopIfNeeds(ReadableJobAttr jobAttr, Supplier<String> concurrentDisp) {
        final JobConcurrentExec concurrentExec = jobAttr.getConcurrentExec();
        if (concurrentExec.equals(JobConcurrentExec.QUIT)) {
            noticeSilentlyQuit(jobAttr, concurrentDisp);
            return OptionalThing.of(RunnerResult.asQuitByConcurrent());
        } else if (concurrentExec.equals(JobConcurrentExec.ERROR)) {
            throwJobConcurrentlyExecutingException(jobAttr, concurrentDisp);
        }
        return OptionalThing.empty();
    }

    protected void noticeSilentlyQuit(ReadableJobAttr jobAttr, Supplier<String> concurrentDisp) { // in varying lock
        final JobNoticeLogLevel noticeLogLevel = jobAttr.getNoticeLogLevel();
        JobNoticeLog.log(noticeLogLevel, () -> {
            return "...Quitting the job for already executing job: " + jobAttr.toIdentityDisp() + ", " + concurrentDisp.get();
        });
    }

    protected void throwJobConcurrentlyExecutingException(ReadableJobAttr jobAttr, Supplier<String> concurrentDisp) {
        throw new JobConcurrentlyExecutingException(buildJobConcurrentlyExecutingMessage(jobAttr, concurrentDisp));
    }

    protected String buildJobConcurrentlyExecutingMessage(ReadableJobAttr jobAttr, Supplier<String> concurrentDisp) {
        return "Already executing the job: " + jobAttr.toIdentityDisp() + ", " + concurrentDisp.get();
    }

    // ===================================================================================
    //                                                                 Neighbor Concurrent
    //                                                                 ===================
    public OptionalThing<RunnerResult> stopIfNeighborNeeds(ReadableJobAttr jobAttr,
            Function<LaJobKey, OptionalThing<? extends ReadableJobState>> jobFinder, Supplier<String> concurrentDisp) {
        final Map<JobConcurrentExec, Set<LaJobKey>> concurrentMap = jobAttr.getNeighborConcurrentMap();
        doStopIfNeighborNeeds(jobFinder, concurrentMap, JobConcurrentExec.QUIT, jobState -> {
            noticeSilentlyQuitByNeighbor(jobAttr, jobState, concurrentDisp);
        });
        doStopIfNeighborNeeds(jobFinder, concurrentMap, JobConcurrentExec.ERROR, jobState -> {
            throwJobConcurrentlyNeighborExecutingException(jobAttr, jobState, concurrentDisp);
        });
        return OptionalThing.empty();
    }

    protected void doStopIfNeighborNeeds(Function<LaJobKey, OptionalThing<? extends ReadableJobState>> jobFinder,
            Map<JobConcurrentExec, Set<LaJobKey>> concurrentMap, JobConcurrentExec concurrentExec, Consumer<ReadableJobState> action) {
        concurrentMap.getOrDefault(concurrentExec, Collections.emptySet()).forEach(jobKey -> {
            jobFinder.apply(jobKey).ifPresent(jobState -> { // ignoring unscheduled job, no problem
                if (jobState.isExecutingNow()) {
                    action.accept(jobState);
                }
            });
        });
    }

    protected void noticeSilentlyQuitByNeighbor(ReadableJobAttr me, ReadableJobAttr neighbor, Supplier<String> concurrentDisp) { // in varying lock
        final JobNoticeLogLevel noticeLogLevel = me.getNoticeLogLevel();
        final String meDisp = me.toIdentityDisp();
        final String neighborDisp = neighbor.toIdentityDisp();
        final String rearDisp = concurrentDisp.get();
        JobNoticeLog.log(noticeLogLevel, () -> {
            return "...Quitting the job for already executing neighbor job: me=" + meDisp + ", neighbor=" + neighborDisp + ", " + rearDisp;
        });
    }

    protected void throwJobConcurrentlyNeighborExecutingException(ReadableJobAttr me, ReadableJobAttr neighbor,
            Supplier<String> concurrentDisp) {
        throw new JobConcurrentlyNeighborExecutingException(buildJobConcurrentlyNeighborExecutingMessage(me, neighbor, concurrentDisp));
    }

    protected String buildJobConcurrentlyNeighborExecutingMessage(ReadableJobAttr me, ReadableJobAttr neighbor,
            Supplier<String> concurrentDisp) {
        final String meDisp = me.toIdentityDisp();
        final String neighborDisp = neighbor.toIdentityDisp();
        final String rearDisp = concurrentDisp.get();
        return "Already executing the neighbor job: me=" + meDisp + ", neighbor=" + neighborDisp + ", " + rearDisp;
    }
}
