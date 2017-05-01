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
package it.sauronsoftware.cron4j;

import java.lang.reflect.Field;
import java.util.List;

import org.dbflute.util.DfReflectionUtil;

/**
 * @author jflute
 * @since 0.4.6 (2017/05/01 Monday)
 */
public class RomanticCron4jNativeScheduler extends Scheduler {

    protected static Field executorsField; // cached
    protected List<TaskExecutor> linkedExecutors;

    @Override
    public TaskExecutor spawnExecutor(Task task) { // called by run() and launch()
        setupLinkedExecutorsIfNeeds();
        final RomanticCron4jNativeTaskExecutor executor = new RomanticCron4jNativeTaskExecutor(this, task);
        synchronized (linkedExecutors) {
            linkedExecutors.add(executor);
        }
        executor.start(isDaemon());
        return executor;
    }

    @SuppressWarnings("unchecked")
    protected void setupLinkedExecutorsIfNeeds() {
        readyExecutorsFieldIfNeeds();
        if (linkedExecutors == null) {
            synchronized (this) {
                if (linkedExecutors == null) {
                    linkedExecutors = (List<TaskExecutor>) DfReflectionUtil.getValue(executorsField, this);
                }
            }
        }
    }

    protected void readyExecutorsFieldIfNeeds() {
        if (executorsField == null) {
            synchronized (RomanticCron4jNativeScheduler.class) {
                if (executorsField == null) {
                    executorsField = DfReflectionUtil.getWholeField(getClass(), "executors");
                    executorsField.setAccessible(true);
                }
            }
        }
    }
}
