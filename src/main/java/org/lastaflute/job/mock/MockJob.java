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

import org.lastaflute.job.LaJob;
import org.lastaflute.job.LaJobRuntime;

/**
 * @author jflute
 * @since 0.2.0 (2016/01/15 Friday)
 */
public class MockJob implements LaJob {

    @Override
    public void run(LaJobRuntime runtime) {
    }
}
