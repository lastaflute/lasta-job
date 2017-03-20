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
import org.lastaflute.job.exception.JobAlreadyIllegallyExecutingException;
import org.lastaflute.job.log.JobNoticeLog;
import org.lastaflute.job.log.JobNoticeLogLevel;

/**
 * @author jflute
 * @since 0.4.1 (2017/03/20 Monday)
 */
public class ConcurrentJobStopper {

    public OptionalThing<RunnerResult> stopIfNeeds(ReadableJobAttr jobAttr, Supplier<String> concurrentDisp) {
        final JobConcurrentExec concurrentExec = jobAttr.getConcurrentExec();
        if (concurrentExec.equals(JobConcurrentExec.QUIT)) {
            noticeSilentlyQuit(jobAttr, concurrentDisp);
            return OptionalThing.of(RunnerResult.asQuitByConcurrent());
        } else if (concurrentExec.equals(JobConcurrentExec.ERROR)) {
            throwJobAlreadyIllegallyExecutingException(jobAttr, concurrentDisp);
        }
        return OptionalThing.empty();
    }

    protected void noticeSilentlyQuit(ReadableJobAttr jobAttr, Supplier<String> concurrentDisp) { // in varying lock
        final JobNoticeLogLevel noticeLogLevel = jobAttr.getNoticeLogLevel();
        JobNoticeLog.log(noticeLogLevel, () -> {
            return "...Quitting the job because of already existing job: " + jobAttr.toIdentityDisp() + ", " + concurrentDisp.get();
        });
    }

    protected void throwJobAlreadyIllegallyExecutingException(ReadableJobAttr jobAttr, Supplier<String> concurrentDisp) {
        throw new JobAlreadyIllegallyExecutingException(buildJobAlreadyIllegallyExecutingMessage(jobAttr, concurrentDisp));
    }

    protected String buildJobAlreadyIllegallyExecutingMessage(ReadableJobAttr jobAttr, Supplier<String> concurrentDisp) {
        return "Already executing the job: " + jobAttr.toIdentityDisp() + ", " + concurrentDisp.get();
    }
}
