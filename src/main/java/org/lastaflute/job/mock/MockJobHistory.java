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
package org.lastaflute.job.mock;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;

import org.dbflute.optional.OptionalThing;
import org.lastaflute.job.LaJobHistory;
import org.lastaflute.job.key.LaJobKey;
import org.lastaflute.job.key.LaJobNote;
import org.lastaflute.job.key.LaJobUnique;
import org.lastaflute.job.subsidiary.ExecResultType;

/**
 * @author jflute
 * @since 0.2.8 (2017/03/04 Saturday)
 */
public class MockJobHistory implements LaJobHistory {

    @Override
    public LaJobKey getJobKey() {
        return LaJobKey.of("sea_904");
    }

    @Override
    public OptionalThing<LaJobNote> getJobNote() {
        return OptionalThing.of(LaJobNote.of("Sea Job", "birthdate is 904"));
    }

    @Override
    public OptionalThing<LaJobUnique> getJobUnique() {
        return OptionalThing.of(LaJobUnique.of("sea"));
    }

    @Override
    public OptionalThing<String> getCronExp() {
        return OptionalThing.empty();
    }

    @Override
    public String getJobTypeFqcn() {
        return "org.docksidestage.SeaJob";
    }

    @Override
    public LocalDateTime getActivationTime() {
        return LocalDateTime.now();
    }

    @Override
    public OptionalThing<LocalDateTime> getBeginTime() {
        return OptionalThing.of(LocalDateTime.now());
    }

    @Override
    public OptionalThing<LocalDateTime> getEndTime() {
        return OptionalThing.of(LocalDateTime.now());
    }

    @Override
    public ExecResultType getExecResultType() {
        return ExecResultType.SUCCESS;
    }

    @Override
    public Map<String, String> getEndTitleRollSnapshotMap() {
        return Collections.emptyMap();
    }

    @Override
    public OptionalThing<Throwable> getCause() {
        return OptionalThing.empty();
    }
}
