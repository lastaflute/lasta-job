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
package org.lastaflute.job;

import java.util.ArrayList;
import java.util.List;

import org.dbflute.helper.message.ExceptionMessageBuilder;
import org.dbflute.util.DfReflectionUtil;
import org.lastaflute.core.smartdeploy.ManagedHotdeploy;
import org.lastaflute.core.time.TimeManager;
import org.lastaflute.core.util.ContainerUtil;
import org.lastaflute.di.naming.NamingConvention;
import org.lastaflute.job.cron4j.Cron4jCron;
import org.lastaflute.job.cron4j.Cron4jCron.CronRegistrationType;
import org.lastaflute.job.cron4j.Cron4jNow;
import org.lastaflute.job.cron4j.Cron4jScheduler;
import org.lastaflute.job.exception.JobSchedulerNoInterfaceException;
import org.lastaflute.job.exception.JobSchedulerNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.sauronsoftware.cron4j.RomanticCron4jNativeScheduler;

/**
 * @author jflute
 * @since 0.2.0 (2016/01/09 Saturday)
 */
public class LastaJobStarter {

    // ===================================================================================
    //                                                                          Definition
    //                                                                          ==========
    private static final Logger logger = LoggerFactory.getLogger(LastaJobStarter.class);

    // ===================================================================================
    //                                                                               Start
    //                                                                               =====
    public LaSchedulingNow start() {
        final ClassLoader originalLoader = startHotdeploy();
        try {
            final LaJobScheduler appScheduler = findAppScheduler();
            inject(appScheduler);
            final LaJobRunner jobRunner = appScheduler.createRunner();
            inject(jobRunner);
            final Cron4jScheduler cron4jScheduler = createCron4jScheduler(jobRunner);
            final Cron4jNow cron4jNow = createCron4jNow(cron4jScheduler, jobRunner);
            final Cron4jCron cron4jCron = createCron4jCron(cron4jScheduler, jobRunner, cron4jNow);
            appScheduler.schedule(cron4jCron);
            showBoot(appScheduler, jobRunner, cron4jScheduler, cron4jNow);
            startCron(cron4jScheduler);
            return cron4jNow;
        } finally {
            stopHotdeploy(originalLoader);
        }
    }

    protected ClassLoader startHotdeploy() {
        return ManagedHotdeploy.start();
    }

    protected void stopHotdeploy(ClassLoader originalLoader) {
        ManagedHotdeploy.stop(originalLoader);
    }

    protected void showBoot(LaJobScheduler scheduler, LaJobRunner jobRunner, Cron4jScheduler cron4jScheduler, Cron4jNow cron4jNow) {
        logger.info("[Job Scheduling]");
        logger.info(" scheduler: {}", scheduler);
        logger.info(" jobRunner: {}", jobRunner);
        logger.info(" cron4j: {}", cron4jScheduler);
        int entryNumber = 1;
        for (LaScheduledJob job : cron4jNow.getJobList()) {
            logger.info(" ({}) {}", entryNumber, job);
            ++entryNumber;
        }
    }

    // -----------------------------------------------------
    //                                         App Scheduler
    //                                         -------------
    protected LaJobScheduler findAppScheduler() {
        final List<LaJobScheduler> schedulerList = new ArrayList<LaJobScheduler>(); // to check not found
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
            final LaJobScheduler scheduler = createScheduler(schedulerType);
            schedulerList.add(scheduler);
        }
        if (schedulerList.isEmpty()) {
            throwJobSchedulerNotFoundException(derivedNameList);
        } else if (schedulerList.size() >= 2) {
            throw new IllegalStateException("Duplicate scheduler object: " + schedulerList);
        }
        return schedulerList.get(0);
    }

    protected void throwJobSchedulerNotFoundException(List<String> derivedNameList) {
        final String pureName = getSchedulerPureName();
        final String interfaceName = LaJobScheduler.class.getSimpleName();
        final ExceptionMessageBuilder br = new ExceptionMessageBuilder();
        br.addNotice("Not found the job scheduler for your application.");
        br.addItem("Advice");
        br.addElement(pureName + " class is needed for job scheduling,");
        br.addElement("which implements " + interfaceName + ".");
        br.addElement("For example:");
        br.addElement("  app");
        br.addElement("   |-job");
        br.addElement("   |  |-" + pureName + " // Good");
        br.addElement("   |  |-SeaJob");
        br.addElement("   |  |-LandJob");
        br.addElement("   |-logic");
        br.addElement("   |-web");
        br.addElement("   |  |-...");
        br.addElement("");
        br.addElement("  public class " + pureName + " implements " + interfaceName + " {");
        br.addElement("");
        br.addElement("      @Override");
        br.addElement("      public void schedule(" + LaCron.class.getSimpleName() + " cron) {");
        br.addElement("          ...");
        br.addElement("      }");
        br.addElement("  }");
        br.addItem("Expected Class");
        for (String derivedName : derivedNameList) {
            br.addElement(derivedName);
        }
        final String msg = br.buildExceptionMessage();
        throw new JobSchedulerNotFoundException(msg);
    }

    // -----------------------------------------------------
    //                                      Cron4j Scheduler
    //                                      ----------------
    protected Cron4jScheduler createCron4jScheduler(LaJobRunner jobRunner) {
        return new Cron4jScheduler(newNativeScheduler());
    }

    protected RomanticCron4jNativeScheduler newNativeScheduler() {
        return new RomanticCron4jNativeScheduler();
    }

    protected Cron4jNow createCron4jNow(Cron4jScheduler cron4jScheduler, LaJobRunner jobRunner) {
        final TimeManager timeManager = getTimeManager();
        return new Cron4jNow(cron4jScheduler, jobRunner, () -> timeManager.currentDateTime());
    }

    protected Cron4jCron createCron4jCron(Cron4jScheduler cron4jScheduler, LaJobRunner runner, Cron4jNow cron4jNow) {
        final TimeManager timeManager = getTimeManager();
        return new Cron4jCron(cron4jScheduler, runner, cron4jNow, CronRegistrationType.START, () -> {
            return timeManager.currentDateTime();
        });
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

    protected LaJobScheduler createScheduler(Class<?> schedulingType) {
        final Object schedulerObj = DfReflectionUtil.newInstance(schedulingType);
        if (!(schedulerObj instanceof LaJobScheduler)) {
            throwJobSchedulerNoInterfaceException(schedulerObj);
        }
        return (LaJobScheduler) schedulerObj;
    }

    protected void throwJobSchedulerNoInterfaceException(Object schedulerObj) {
        final String pureName = getSchedulerPureName();
        final String interfaceName = LaJobScheduler.class.getSimpleName();
        final ExceptionMessageBuilder br = new ExceptionMessageBuilder();
        br.addNotice("You job scheduler does not implement the interface.");
        br.addItem("Advice");
        br.addElement(pureName + " class should implement " + interfaceName + ".");
        br.addElement("For example:");
        br.addElement("  public class " + pureName + " implements " + interfaceName + " { // Good");
        br.addElement("");
        br.addElement("      @Override");
        br.addElement("      public void schedule(" + LaCron.class.getSimpleName() + " cron) {");
        br.addElement("          ...");
        br.addElement("      }");
        br.addElement("  }");
        br.addItem("Job Scheduler");
        br.addElement(schedulerObj);
        final String msg = br.buildExceptionMessage();
        throw new JobSchedulerNoInterfaceException(msg);
    }

    // -----------------------------------------------------
    //                                            Start Cron
    //                                            ----------
    protected void startCron(Cron4jScheduler cron4jScheduler) {
        cron4jScheduler.start();
    }

    // ===================================================================================
    //                                                                           Component
    //                                                                           =========
    protected NamingConvention getNamingConvention() {
        return ContainerUtil.getComponent(NamingConvention.class);
    }

    protected TimeManager getTimeManager() {
        return ContainerUtil.getComponent(TimeManager.class);
    }

    protected void inject(Object target) {
        ContainerUtil.injectSimply(target);
    }
}
