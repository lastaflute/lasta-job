/*
 * Copyright 2015-2021 the original author or authors.
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
import java.util.List;

import org.dbflute.optional.OptionalThing;

/**
 * @author jflute
 * @since 0.5.6 (2021/08/23 Monday at roppongi japanese)
 */
public class JobExecutingSnapshot {

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    protected final int executingCount;
    protected final OptionalThing<SnapshotExecState> mainExecState; // empty if no running
    protected final List<SnapshotExecState> outlawParallelExecStateList; // running only, not null

    // ===================================================================================
    //                                                                         Constructor
    //                                                                         ===========
    public JobExecutingSnapshot(int executingCount, OptionalThing<SnapshotExecState> mainExecState,
            List<SnapshotExecState> outlawParallelExecStateList) {
        this.executingCount = executingCount;
        this.mainExecState = mainExecState;
        this.outlawParallelExecStateList = outlawParallelExecStateList;
    }

    // ===================================================================================
    //                                                                            Accessor
    //                                                                            ========
    public int getExecutingCount() {
        return executingCount;
    }

    public OptionalThing<SnapshotExecState> getMainExecState() {
        return mainExecState;
    }

    public List<SnapshotExecState> getOutlawParallelExecStateList() {
        return Collections.unmodifiableList(outlawParallelExecStateList); // just in case
    }
}
