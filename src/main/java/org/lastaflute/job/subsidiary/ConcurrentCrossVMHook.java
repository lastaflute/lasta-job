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

import java.time.LocalDateTime;
import java.util.List;

import org.lastaflute.job.JobManager;

/**
 * @author jflute
 * @since 0.4.7 (2017/05/16 Tuesday at bay maihama)
 */
public abstract class ConcurrentCrossVMHook implements CrossVMHook {

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    protected final JobManager jobManager;

    // ===================================================================================
    //                                                                         Constructor
    //                                                                         ===========
    public ConcurrentCrossVMHook(JobManager jobManager) {
        this.jobManager = jobManager;
    }

    // ===================================================================================
    //                                                                      Hook Beginning
    //                                                                      ==============
    @Override
    public CrossVMState hookBeginning(ReadableJobState jobState, LocalDateTime activationTime) {
        return doHookBeginning(jobState, activationTime);
    }

    protected CrossVMState doHookBeginning(ReadableJobState jobState, LocalDateTime activationTime) {
        final CrossVMState duplicateBoot = handleDuplicateBoot(jobState, activationTime);
        if (duplicateBoot.isQuit()) {
            return duplicateBoot;
        }
        final CrossVMState neighborConcurrent = handleNeighborConcurrent(jobState, activationTime);
        if (neighborConcurrent.isQuit()) {
            return neighborConcurrent;
        }
        final CrossVMState realState = asNormalExecution();
        markExecuting(jobState, activationTime, realState);
        return realState;
    }

    // -----------------------------------------------------
    //                                             Duplicate
    //                                             ---------
    protected CrossVMState handleDuplicateBoot(ReadableJobState jobState, LocalDateTime activationTime) {
        final ConcurrentJobStopper stopper = createConcurrentJobStopper();
        return stopper.stopIfNeeds(jobState, () -> buildDuplicateJobStateDisp(jobState, activationTime)).map(result -> { // quit (or exception)
            return asQuitState();
        }).orElseGet(() -> {
            return asNormalExecution();
        });
    }

    protected ConcurrentJobStopper createConcurrentJobStopper() {
        final ConcurrentJobStopper stopper = new ConcurrentJobStopper(jobState -> determineDuplicateBoot(jobState));
        return stopper.waitress(jobState -> waitForDuplicateEnding(jobState));
    }

    protected abstract boolean determineDuplicateBoot(ReadableJobState jobState);

    protected abstract void waitForDuplicateEnding(ReadableJobState jobState);

    protected String buildDuplicateJobStateDisp(ReadableJobState jobState, LocalDateTime activationTime) {
        return activationTime.toString(); // as supplement info
    }

    // -----------------------------------------------------
    //                                              Neighbor
    //                                              --------
    protected CrossVMState handleNeighborConcurrent(ReadableJobState me, LocalDateTime activationTime) {
        final NeighborConcurrentJobStopper stopper = createNeighborConcurrentJobStopper(me.getNeighborConcurrentGroupList());
        return stopper.stopIfNeeds(me, neighbor -> buildNeighborJobStateDisp(me, neighbor, activationTime)).map(result -> {
            return asQuitState();
        }).orElseGet(() -> {
            return asNormalExecution();
        });
    }

    private NeighborConcurrentJobStopper createNeighborConcurrentJobStopper(List<NeighborConcurrentGroup> neighborConcurrentGroupList) {
        final NeighborConcurrentJobStopper stopper = new NeighborConcurrentJobStopper(jobState -> determineNeighborExecutingNow(jobState) // jobExecutingDeterminer
                , jobKey -> jobManager.findJobByKey(jobKey) // jobFinder
                , neighborConcurrentGroupList);
        return stopper.waitress(jobState -> waitForNeighborEnding(jobState));
    }

    protected abstract boolean determineNeighborExecutingNow(ReadableJobState jobState);

    protected abstract void waitForNeighborEnding(ReadableJobState jobState);

    protected String buildNeighborJobStateDisp(ReadableJobState me, ReadableJobState neighbor, LocalDateTime activationTime) {
        return activationTime.toString(); // as supplement info
    }

    // -----------------------------------------------------
    //                                         CrossVM State
    //                                         -------------
    protected CrossVMState asQuitState() {
        return new CrossVMState().asQuit();
    }

    protected CrossVMState asNormalExecution() {
        return new CrossVMState();
    }

    // -----------------------------------------------------
    //                                        Mark Executing
    //                                        --------------
    protected abstract void markExecuting(ReadableJobState jobState, LocalDateTime activationTime, CrossVMState crossVMState);

    // ===================================================================================
    //                                                                         Hook Ending
    //                                                                         ===========
    @Override
    public void hookEnding(ReadableJobState jobState, CrossVMState crossVMState, LocalDateTime endTime) {
        doHookEnding(jobState, crossVMState, endTime);
    }

    protected void doHookEnding(ReadableJobState jobState, CrossVMState crossVMState, LocalDateTime endTime) {
        closeExecuting(jobState, crossVMState, endTime);
    }

    protected abstract void closeExecuting(ReadableJobState jobState, CrossVMState crossVMState, LocalDateTime endTime);
}
