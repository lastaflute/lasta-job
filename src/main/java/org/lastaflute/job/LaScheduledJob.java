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

import org.dbflute.optional.OptionalThing;
import org.lastaflute.job.exception.JobAlreadyClosedException;
import org.lastaflute.job.exception.JobAlreadyExecutingNowException;
import org.lastaflute.job.exception.JobNoExecutingNowException;
import org.lastaflute.job.key.LaJobKey;
import org.lastaflute.job.key.LaJobUniqueCode;

/**
 * @author jflute
 * @since 0.2.0 (2016/01/11 Monday)
 */
public interface LaScheduledJob {

    LaJobKey getJobKey();

    String getCronExp();

    OptionalThing<LaJobUniqueCode> getUniqueCode();

    boolean isExecutingNow();

    void launchNow() throws JobAlreadyClosedException, JobAlreadyExecutingNowException;

    void stopNow() throws JobNoExecutingNowException;

    void closeNow(); // executing job continues, so call stopNow() before if it needs

    boolean isClosed();
}
