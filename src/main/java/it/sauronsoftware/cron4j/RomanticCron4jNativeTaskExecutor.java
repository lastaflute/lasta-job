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
import java.lang.reflect.Method;

import org.dbflute.util.DfReflectionUtil;

/**
 * @author jflute
 * @since 0.4.6 (2017/05/01 Monday)
 */
public class RomanticCron4jNativeTaskExecutor extends TaskExecutor {

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    // -----------------------------------------------------
    //                                                 Basic
    //                                                 -----
    protected final Scheduler linkedScheduler; // not null
    protected final Task linkedTask; // not null

    // -----------------------------------------------------
    //                                            Reflection
    //                                            ----------
    protected final Object attributeLinkLock = this; // per instance
    protected final Object reflectionPartyLock = RomanticCron4jNativeTaskExecutor.class; // as static

    protected static Field lockField; // cached
    protected Object linkedLock;

    protected static Field startTimeField; // cached
    protected Long linkedStartTime;

    protected static Field guidField; // cached
    protected String linkedGuid;

    protected static Field threadField; // cached
    protected Thread linkedThread;

    protected static Field contextField; // cached
    protected TaskExecutionContext linkedContext;

    protected static Method notifyExecutionTerminatedMethod; // cached

    // ===================================================================================
    //                                                                         Constructor
    //                                                                         ===========
    public RomanticCron4jNativeTaskExecutor(Scheduler scheduler, Task task) {
        super(scheduler, task);
        this.linkedScheduler = scheduler;
        this.linkedTask = task;
    }

    // ===================================================================================
    //                                                                        Start Thread
    //                                                                        ============
    @Override
    public void start(boolean daemon) {
        setupLinkedLockIfNeeds();
        synchronized (linkedLock) {
            registerStartTimeCurrentTime();
            setupLinkedGuidIfNeeds();
            final String threadName = buildThreadName(linkedScheduler.getGuid(), linkedGuid);
            registerThreadNewCreated();
            prepareThread(daemon, threadName);
            actuallyThreadStart();
        }
    }

    protected String buildThreadName(Object schedulerGuid, String executorGuid) {
        return "cron4j::scheduler[" + schedulerGuid + "]::executor[" + executorGuid + "]";
    }

    protected void prepareThread(boolean daemon, String threadName) {
        linkedThread.setDaemon(daemon);
        linkedThread.setName(threadName);
    }

    protected void actuallyThreadStart() {
        linkedThread.start();
    }

    // ===================================================================================
    //                                                                              Runner
    //                                                                              ======
    protected class RomanticRunner implements Runnable {

        @Override
        public void run() {
            final TaskExecutor myself = RomanticCron4jNativeTaskExecutor.this;
            registerStartTimeCurrentTime(); // #thiking duplicate?
            Throwable cause = null;
            try {
                linkedScheduler.notifyTaskLaunching(myself);
                setupLinkedContextIfNeeds();
                linkedTask.execute(linkedContext);
                linkedScheduler.notifyTaskSucceeded(myself);
            } catch (Throwable e) {
                cause = e;
                linkedScheduler.notifyTaskFailed(myself, e);
            } finally {
                invokeNotifyExecutionTerminated(cause);
                linkedScheduler.notifyExecutorCompleted(myself);
            }
        }
    }

    // ===================================================================================
    //                                                                 Reflection Festival
    //                                                                 ===================
    // -----------------------------------------------------
    //                                                Notify
    //                                                ------
    protected void invokeNotifyExecutionTerminated(Throwable cause) {
        readyNotifyExecutionTerminatedMethodIfNeeds();
        DfReflectionUtil.invoke(notifyExecutionTerminatedMethod, this, new Object[] { cause });
    }

    protected void readyNotifyExecutionTerminatedMethodIfNeeds() {
        if (notifyExecutionTerminatedMethod == null) {
            synchronized (reflectionPartyLock) {
                if (notifyExecutionTerminatedMethod == null) {
                    final String methodName = "notifyExecutionTerminatedMethod";
                    final Class<?>[] argTypes = new Class<?>[] { Throwable.class };
                    notifyExecutionTerminatedMethod = DfReflectionUtil.getWholeMethod(getClass(), methodName, argTypes);
                    notifyExecutionTerminatedMethod.setAccessible(true);
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
                    linkedLock = DfReflectionUtil.getValue(lockField, this);
                }
            }
        }
    }

    protected void readyLockFieldIfNeeds() {
        if (lockField == null) {
            synchronized (reflectionPartyLock) {
                if (lockField == null) {
                    lockField = DfReflectionUtil.getWholeField(getClass(), "lock");
                    lockField.setAccessible(true);
                }
            }
        }
    }

    // -----------------------------------------------------
    //                                            Start Time
    //                                            ----------
    protected void registerStartTimeCurrentTime() {
        readyStartTimeFieldIfNeeds();
        final long millis = System.currentTimeMillis();
        DfReflectionUtil.setValue(startTimeField, this, millis); // #thinking should be from time-manager?
        linkedStartTime = millis;
    }

    protected void readyStartTimeFieldIfNeeds() {
        if (startTimeField == null) {
            synchronized (reflectionPartyLock) {
                if (startTimeField == null) {
                    startTimeField = DfReflectionUtil.getWholeField(getClass(), "startTime");
                    startTimeField.setAccessible(true);
                }
            }
        }
    }

    // -----------------------------------------------------
    //                                                 GUID
    //                                                ------
    protected void setupLinkedGuidIfNeeds() {
        readyGuidFieldIfNeeds();
        if (linkedGuid == null) {
            synchronized (attributeLinkLock) {
                if (linkedGuid == null) {
                    linkedGuid = (String) DfReflectionUtil.getValue(guidField, this);
                }
            }
        }
    }

    protected void readyGuidFieldIfNeeds() {
        if (guidField == null) {
            synchronized (reflectionPartyLock) {
                if (guidField == null) {
                    guidField = DfReflectionUtil.getWholeField(getClass(), "guid");
                    guidField.setAccessible(true);
                }
            }
        }
    }

    // -----------------------------------------------------
    //                                                Thread
    //                                                ------
    protected void registerThreadNewCreated() {
        readyThreadFieldIfNeeds();
        final Thread thread = new Thread(new RomanticRunner());
        DfReflectionUtil.setValue(threadField, this, thread);
        linkedThread = thread;
    }

    protected void readyThreadFieldIfNeeds() {
        if (threadField == null) {
            synchronized (reflectionPartyLock) {
                if (threadField == null) {
                    threadField = DfReflectionUtil.getWholeField(getClass(), "thread");
                    threadField.setAccessible(true);
                }
            }
        }
    }

    // -----------------------------------------------------
    //                                               Context
    //                                               -------
    protected void setupLinkedContextIfNeeds() {
        readyContextFieldIfNeeds();
        if (linkedContext == null) {
            synchronized (attributeLinkLock) {
                if (linkedContext == null) {
                    linkedContext = (TaskExecutionContext) DfReflectionUtil.getValue(contextField, this);
                }
            }
        }
    }

    protected void readyContextFieldIfNeeds() {
        if (contextField == null) {
            synchronized (reflectionPartyLock) {
                if (contextField == null) {
                    contextField = DfReflectionUtil.getWholeField(getClass(), "context");
                    contextField.setAccessible(true);
                }
            }
        }
    }
}
