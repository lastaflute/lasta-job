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
package org.lastaflute.job;

import java.util.List;
import java.util.Set;

import org.dbflute.optional.OptionalThing;
import org.lastaflute.job.key.LaJobKey;
import org.lastaflute.job.key.LaJobUnique;
import org.lastaflute.job.subsidiary.CronConsumer;
import org.lastaflute.job.subsidiary.JobConcurrentExec;

/**
 * @author jflute
 * @since 0.2.0 (2016/01/11 Monday)
 */
public interface LaSchedulingNow {

    OptionalThing<? extends LaScheduledJob> findJobByKey(LaJobKey jobKey);

    OptionalThing<? extends LaScheduledJob> findJobByUniqueOf(LaJobUnique jobUnique);

    List<? extends LaScheduledJob> getJobList();

    void schedule(CronConsumer oneArgLambda);

    List<LaJobHistory> searchJobHistoryList();

    void setupNeighborConcurrent(String groupName, JobConcurrentExec concurrentExec, Set<LaJobKey> jobKeySet);

    void destroy();
}
