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

import java.util.Collections;
import java.util.Set;

import org.lastaflute.job.key.LaJobKey;

/**
 * @author jflute
 * @since 0.4.1 (2017/03/25 Saturday)
 */
public class NeighborConcurrentGroup {

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    protected final String groupName; // not null
    protected final JobConcurrentExec concurrentExec; // not null
    protected final Set<LaJobKey> neighborJobKeySet; // not null
    protected final Object groupPreparingLock; // not null
    protected final Object groupRunningLock; // not null

    // ===================================================================================
    //                                                                         Constructor
    //                                                                         ===========
    public NeighborConcurrentGroup(String groupName, JobConcurrentExec concurrentExec, Set<LaJobKey> neighborJobKeySet,
            Object groupPreparingLock, Object groupRunningLock) {
        assertArgumentNotNull("groupName", groupName);
        if (groupName.trim().isEmpty()) {
            throw new IllegalArgumentException("The argument 'groupName' should not be empty: [" + groupName + "]");
        }
        assertArgumentNotNull("concurrentExec", concurrentExec);
        assertArgumentNotNull("neighborJobKeySet", neighborJobKeySet);
        assertArgumentNotNull("groupPreparingLock", groupPreparingLock);
        assertArgumentNotNull("groupRunningLock", groupRunningLock);
        this.groupName = groupName;
        this.concurrentExec = concurrentExec;
        this.neighborJobKeySet = neighborJobKeySet;
        this.groupPreparingLock = groupPreparingLock;
        this.groupRunningLock = groupRunningLock;
    }

    // ===================================================================================
    //                                                                        Small Helper
    //                                                                        ============
    protected void assertArgumentNotNull(String variableName, Object value) {
        if (variableName == null) {
            throw new IllegalArgumentException("The variableName should not be null.");
        }
        if (value == null) {
            throw new IllegalArgumentException("The argument '" + variableName + "' should not be null.");
        }
    }

    // ===================================================================================
    //                                                                      Basic Override
    //                                                                      ==============
    @Override
    public String toString() {
        return "group:{" + groupName + ", " + concurrentExec + ", " + neighborJobKeySet + "}";
    }

    // ===================================================================================
    //                                                                            Accessor
    //                                                                            ========
    public String getGroupName() {
        return groupName;
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
