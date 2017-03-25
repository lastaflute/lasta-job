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
import java.util.Set;

import org.lastaflute.job.key.LaJobKey;

/**
 * @author jflute
 * @since 0.4.1 (2017/03/25 Saturday)
 */
public class NeighborConcurrentGroup {

    protected final JobConcurrentExec concurrentExec; // not null
    protected final Set<LaJobKey> neighborJobKeySet; // not null
    protected final Object groupPreparingLock; // not null
    protected final Object groupRunningLock; // not null

    public NeighborConcurrentGroup(JobConcurrentExec concurrentExec, Set<LaJobKey> neighborJobKeySet, Object groupPreparingLock,
            Object groupRunningLock) {
        if (concurrentExec == null) {
            throw new IllegalArgumentException("The argument 'concurrentExec' should not be null.");
        }
        if (neighborJobKeySet == null) {
            throw new IllegalArgumentException("The argument 'neighborJobKeySet' should not be null.");
        }
        if (groupPreparingLock == null) {
            throw new IllegalArgumentException("The argument 'groupPreparingLock' should not be null.");
        }
        if (groupRunningLock == null) {
            throw new IllegalArgumentException("The argument 'groupRunningLock' should not be null.");
        }
        this.concurrentExec = concurrentExec;
        this.neighborJobKeySet = neighborJobKeySet;
        this.groupPreparingLock = groupPreparingLock;
        this.groupRunningLock = groupRunningLock;
    }

    public JobConcurrentExec getConcurrentExec() {
        return concurrentExec;
    }

    public Set<LaJobKey> getNeighborJobKeySet() {
        return Collections.unmodifiableSet(neighborJobKeySet);
    }

    public Object getGroupPreparingLock() {
        return groupPreparingLock;
    }

    public Object getGroupRunningLock() {
        return groupRunningLock;
    }
}
