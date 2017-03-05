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
package org.lastaflute.job;

import java.time.LocalDateTime;

import org.dbflute.optional.OptionalThing;
import org.lastaflute.job.key.LaJobKey;
import org.lastaflute.job.key.LaJobNote;
import org.lastaflute.job.key.LaJobUnique;
import org.lastaflute.job.subsidiary.ExecResultType;

/**
 * @author jflute
 * @since 0.2.8 (2017/03/04 Saturday)
 */
public interface LaJobHistory {

    // ===================================================================================
    //                                                                       Job Attribute
    //                                                                       =============
    LaJobKey getJobKey();

    OptionalThing<LaJobNote> getJobNote();

    OptionalThing<LaJobUnique> getJobUnique();

    OptionalThing<String> getCronExp(); // varying so this is snapshot

    String getJobTypeFqcn();

    // ===================================================================================
    //                                                                    Execution Result
    //                                                                    ================
    LocalDateTime getBeginTime();

    LocalDateTime getEndTime();

    ExecResultType getExecResultType();

    OptionalThing<Throwable> getCause();
}
