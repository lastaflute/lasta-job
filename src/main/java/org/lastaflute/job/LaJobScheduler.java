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

import org.lastaflute.job.subsidiary.ConcurrentExec;

/**
 * @author jflute
 * @since 0.2.0 (2016/01/09 Saturday)
 */
public interface LaJobScheduler {

    // ===================================================================================
    //                                                                               Basic
    //                                                                               =====
    /**
     * Schedule application jobs.
     * @param cron The cron object to register jobs. (NotNull)
     */
    void schedule(LaCron cron);

    /**
     * Create job runner, which executes your jobs.
     * @return The new-created job runner. (NotNull)
     */
    default LaJobRunner createRunner() { // you can override
        return new LaJobRunner();
    }

    // ===================================================================================
    //                                                                          Concurrent
    //                                                                          ==========
    /**
     * Get the execution type of concurrent for 'wait', <br>
     * means the second job waits for finishing the first job.
     * @return The execution type of concurrent. (NotNull)
     */
    default ConcurrentExec waitIfConcurrent() {
        return ConcurrentExec.WAIT;
    }

    /**
     * Get the execution type of concurrent for 'quit', <br>
     * means the second job quits executing quietly.
     * @return The execution type of concurrent. (NotNull)
     */
    default ConcurrentExec quitIfConcurrent() {
        return ConcurrentExec.QUIT;
    }

    /**
     * Get the execution type of concurrent for 'error', <br>
     * means the second job outputs error log (and quits).
     * @return The execution type of concurrent. (NotNull)
     */
    default ConcurrentExec errorIfConcurrent() {
        return ConcurrentExec.ERROR;
    }
}
