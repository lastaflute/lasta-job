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

import org.dbflute.optional.OptionalThing;
import org.lastaflute.job.key.LaJobNote;
import org.lastaflute.job.key.LaJobUnique;

/**
 * @author jflute
 * @since 0.2.6 (2017/02/18 Saturday)
 */
public interface JobSubIdentityAttr { // for internal assist

    /**
     * @return The optional note (title, description) of the job. (NotNull, EmptyAllowed: if both is no value)
     */
    OptionalThing<LaJobNote> getJobNote();

    /**
     * @return The optional job unique code provided by application when schedule registration. (NotNull, EmptyAllowed)
     */
    OptionalThing<LaJobUnique> getJobUnique();
}
