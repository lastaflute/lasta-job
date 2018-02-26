/*
 * Copyright 2015-2018 the original author or authors.
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

import org.dbflute.optional.OptionalThing;
import org.dbflute.util.DfReflectionUtil;
import org.lastaflute.job.cron4j.Cron4jTask;
import org.lastaflute.job.subsidiary.LaunchNowOption;

/**
 * @author jflute
 * @since 0.4.6 (2017/05/01 Monday)
 */
public class RomanticCron4jNativeScheduler extends Scheduler {

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    // -----------------------------------------------------
    //                                            Reflection
    //                                            ----------
    protected final Object attributeLinkLock = this; // per instance
    protected final Object reflectionPartyLock = RomanticCron4jNativeScheduler.class; // as static

    protected static Field executorsField; // cached
    protected List<TaskExecutor> linkedExecutors;

    protected static Field lockField; // cached
    protected Object linkedLock;

    // ===================================================================================
    //                                                                          Launch Now
    //                                                                          ==========
    public TaskExecutor launchNow(Cron4jTask cron4jTask, LaunchNowOption nowOption) {
        setupLinkedLockIfNeeds();
        synchronized (linkedLock) {
            if (!isStarted()) {
                throw new IllegalStateException("Scheduler not started");
            }
            return doSpawnExecutor(cron4jTask, OptionalThing.of(nowOption));
        }
    }

    // ===================================================================================
    //                                                                      Spawn Executor
    //                                                                      ==============
    @Override
    protected TaskExecutor spawnExecutor(Task task) { // called by run() (and unused launch())
        return doSpawnExecutor(task, OptionalThing.ofNullable(null, () -> {
            throw new IllegalStateException("Not found the launch-now option because of not launch-now.");
        }));
    }

    protected TaskExecutor doSpawnExecutor(Task task, OptionalThing<LaunchNowOption> nowOption) {
        setupLinkedExecutorsIfNeeds();
        final TaskExecutor executor = createTaskExecutor(task, nowOption);
        synchronized (linkedExecutors) {
            linkedExecutors.add(executor);
        }
        executor.start(isDaemon());
        return executor;
    }

    protected TaskExecutor createTaskExecutor(Task task, OptionalThing<LaunchNowOption> nowOption) {
        return new RomanticCron4jNativeTaskExecutor(this, task, nowOption);
    }

    // ===================================================================================
    //                                                                 Reflection Festival
    //                                                                 ===================
    // -----------------------------------------------------
    //                                             Executors
    //                                             ---------
    protected void setupLinkedExecutorsIfNeeds() {
        readyExecutorsFieldIfNeeds();
        if (linkedExecutors == null) {
            synchronized (attributeLinkLock) {
                if (linkedExecutors == null) {
                    linkedExecutors = getFieldValue(executorsField);
                }
            }
        }
    }

    protected void readyExecutorsFieldIfNeeds() {
        if (executorsField == null) {
            synchronized (reflectionPartyLock) {
                if (executorsField == null) {
                    executorsField = getAccessibleField("executors");
                }
            }
        }
    }

    // -----------------------------------------------------
    //                                                 Lock
    //                                                ------
    protected void setupLinkedLockIfNeeds() {
        readyLockFieldIfNeeds();
        if (linkedLock == null) {
            synchronized (attributeLinkLock) {
                if (linkedLock == null) {
                    linkedLock = getFieldValue(lockField);
                }
            }
        }
    }

    protected void readyLockFieldIfNeeds() {
        if (lockField == null) {
            synchronized (reflectionPartyLock) {
                if (lockField == null) {
                    lockField = getAccessibleField("lock");
                }
            }
        }
    }

    // ===================================================================================
    //                                                                        Small Helper
    //                                                                        ============
    @SuppressWarnings("unchecked")
    protected <RESULT> RESULT getFieldValue(Field field) {
        return (RESULT) DfReflectionUtil.getValue(field, this);
    }

    protected Field getAccessibleField(String fieldName) {
        final Field field = DfReflectionUtil.getWholeField(getClass(), fieldName);
        field.setAccessible(true);
        return field;
    }
}
