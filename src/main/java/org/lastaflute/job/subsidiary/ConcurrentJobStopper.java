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

    public OptionalThing<RunnerResult> stopIfNeeds(ReadableJobAttr jobAttr, Supplier<String> stateDisp) {
        final JobConcurrentExec concurrentExec = jobAttr.getConcurrentExec();
        if (concurrentExec.equals(JobConcurrentExec.QUIT)) {
            noticeSilentlyQuit(jobAttr, stateDisp);
            return OptionalThing.of(RunnerResult.asQuitByConcurrent());
        } else if (concurrentExec.equals(JobConcurrentExec.ERROR)) {
            throwJobConcurrentlyExecutingException(jobAttr, stateDisp);
        }
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
