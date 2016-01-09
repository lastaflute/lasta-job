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

import java.util.ArrayList;
import java.util.List;

import org.dbflute.util.DfReflectionUtil;
import org.lastaflute.core.magic.async.AsyncManager;
import org.lastaflute.core.util.ContainerUtil;
import org.lastaflute.di.core.smart.hot.HotdeployUtil;
import org.lastaflute.di.naming.NamingConvention;
import org.lastaflute.job.cron4j.LaCronCron4j;

import it.sauronsoftware.cron4j.Scheduler;

/**
 * @author jflute
 * @since 0.1.0 (2016/01/09 Saturday)
 */
public class LastaJobStarter {

    // ===================================================================================
    //                                                                               Start
    //                                                                               =====
    public void start() {
        final boolean needsHot = !HotdeployUtil.isAlreadyHotdeploy(); // just in case
        if (needsHot) {
            HotdeployUtil.start();
        }
        final Scheduler cron4jScheduler = createCron4jScheduler();
        final LaCron cron = createCronScheduler(cron4jScheduler);
        try {
            final List<LaScheduler> schedulerList = new ArrayList<LaScheduler>(); // to check not found
            final List<String> derivedNameList = new ArrayList<String>(); // for exception message
            final NamingConvention convention = getNamingConvention();
            for (String root : convention.getRootPackageNames()) {
                final String schedulerName = buildSchedulerName(root);
                derivedNameList.add(schedulerName);
                final Class<?> schedulerType;
                try {
                    schedulerType = forSchedulerName(schedulerName);
                } catch (ClassNotFoundException ignored) {
                    continue;
                }
                final LaScheduler scheduler = createScheduler(schedulerType);
                schedulerList.add(scheduler);
                inject(scheduler);
                scheduler.schedule(cron);
            }
            if (schedulerList.isEmpty()) {
                throw new IllegalStateException("Not found scheduling object: " + derivedNameList);
            }
        } finally {
            if (needsHot) {
                HotdeployUtil.stop();
            }
        }
        startCron(cron4jScheduler);
    }

    // -----------------------------------------------------
    //                                        Cron Scheduler
    //                                        --------------
    protected Scheduler createCron4jScheduler() {
        return new Scheduler();
    }

    protected LaCron createCronScheduler(Scheduler cron4jScheduler) {
        return new LaCronCron4j(cron4jScheduler);
    }

    // -----------------------------------------------------
    //                                            Scheduling
    //                                            ----------
    protected String buildSchedulerName(String root) {
        return root + "." + getSchedulerPackage() + "." + getSchedulerPureName();
    }

    protected String getSchedulerPackage() {
        return "job";
    }

    protected String getSchedulerPureName() {
        return "AllJobScheduler";
    }

    protected Class<?> forSchedulerName(String schedulingName) throws ClassNotFoundException {
        return Class.forName(schedulingName, false, Thread.currentThread().getContextClassLoader());
    }

    protected LaScheduler createScheduler(Class<?> schedulingType) {
        final Object schedulerObj = DfReflectionUtil.newInstance(schedulingType);
        if (!(schedulerObj instanceof LaScheduler)) {
            throw new IllegalStateException("Your scheduler should implement LaScheduler: " + schedulerObj);
        }
        return (LaScheduler) schedulerObj;
    }

    // -----------------------------------------------------
    //                                            Start Cron
    //                                            ----------
    protected void startCron(Scheduler cron4jScheduler) {
        getAsyncManager().async(() -> {
            try {
                Thread.sleep(3000L); // delay to wait for finishing application boot
            } catch (InterruptedException ignored) {}
            cron4jScheduler.start();
        });
    }

    // ===================================================================================
    //                                                                           Component
    //                                                                           =========
    protected AsyncManager getAsyncManager() {
        return ContainerUtil.getComponent(AsyncManager.class);
    }

    protected NamingConvention getNamingConvention() {
        return ContainerUtil.getComponent(NamingConvention.class);
    }

    protected void inject(Object target) {
        ContainerUtil.injectSimply(target);
    }
}
