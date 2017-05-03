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

import org.dbflute.optional.OptionalThing;
import org.dbflute.util.DfReflectionUtil;
import org.lastaflute.job.subsidiary.LaunchNowOption;

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
    protected final OptionalThing<LaunchNowOption> nowOption; // not null

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
    public RomanticCron4jNativeTaskExecutor(Scheduler scheduler, Task task, OptionalThing<LaunchNowOption> nowOption) {
        super(scheduler, task);
        this.linkedScheduler = scheduler;
        this.linkedTask = task;
        this.nowOption = nowOption;
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
        return "cron4j::scheduler[" + schedulerGuid + "]::executor[" + executorGuid + "]"; // same as native
    }

    protected void prepareThread(boolean daemon, String threadName) {
        linkedThread.setDaemon(daemon);
        linkedThread.setName(threadName);
    }

    protected void actuallyThreadStart() {
        linkedThread.start();
    }

    // ===================================================================================
    //                                                                     Romantic Runner
    //                                                                     ===============
    protected class RomanticRunner implements Runnable {

        @Override
        public void run() {
            final TaskExecutor myself = RomanticCron4jNativeTaskExecutor.this;
            registerStartTimeCurrentTime(); // #thiking duplicate? (also in native code) by jflute
            Throwable cause = null;
            try {
                linkedScheduler.notifyTaskLaunching(myself);
                setupLinkedContextIfNeeds();
                linkedTask.execute(createRomanticContext());
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

    protected RomanticCron4jTaskExecutionContext createRomanticContext() {
        return new RomanticCron4jTaskExecutionContext(linkedContext, nowOption);
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
                    final String methodName = "notifyExecutionTerminated";
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

    // -----------------------------------------------------
    //                                            Start Time
    //                                            ----------
    protected void registerStartTimeCurrentTime() {
        readyStartTimeFieldIfNeeds();
        final long millis = System.currentTimeMillis(); // #thinking should be from time-manager? (but basically unused...)
        setFieldValue(startTimeField, millis);
        linkedStartTime = millis;
    }

    protected void readyStartTimeFieldIfNeeds() {
        if (startTimeField == null) {
            synchronized (reflectionPartyLock) {
                if (startTimeField == null) {
                    startTimeField = getAccessibleField("startTime");
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
                    linkedGuid = getFieldValue(guidField);
                }
            }
        }
    }

    protected void readyGuidFieldIfNeeds() {
        if (guidField == null) {
            synchronized (reflectionPartyLock) {
                if (guidField == null) {
                    guidField = getAccessibleField("guid");
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
        setFieldValue(threadField, thread);
        linkedThread = thread;
    }

    protected void readyThreadFieldIfNeeds() {
        if (threadField == null) {
            synchronized (reflectionPartyLock) {
                if (threadField == null) {
                    threadField = getAccessibleField("thread");
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
                    linkedContext = getFieldValue(contextField);
                }
            }
        }
    }

    protected void readyContextFieldIfNeeds() {
        if (contextField == null) {
            synchronized (reflectionPartyLock) {
                if (contextField == null) {
                    contextField = getAccessibleField("context");
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

    protected void setFieldValue(Field field, Object value) {
        DfReflectionUtil.setValue(field, this, value);
    }

    protected Field getAccessibleField(String fieldName) {
        final Field field = DfReflectionUtil.getWholeField(getClass(), fieldName);
        field.setAccessible(true);
        return field;
    }
}
