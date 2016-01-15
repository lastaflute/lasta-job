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
package org.lastaflute.job.log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author jflute
 * @since 0.2.0 (2016/01/15 Friday)
 */
public class JobChangeLog {

    // ===================================================================================
    //                                                                          Definition
    //                                                                          ==========
    /** The logger instance for this class. (NotNull) */
    private static final Logger logger = LoggerFactory.getLogger(JobChangeLog.class);

    protected static boolean _jobLogLevelInfo;
    protected static boolean _loggingInHolidayMood;
    protected static boolean _locked = true;

    // ===================================================================================
    //                                                                         Job Logging
    //                                                                         ===========
    public static void log(String msg, Object... args) { // very internal
        if (_jobLogLevelInfo) {
            logger.info(msg, args);
        } else {
            logger.debug(msg, args);
        }
    }

    public static boolean isLogEnabled() { // very internal
        if (_loggingInHolidayMood) {
            return false;
        }
        if (_jobLogLevelInfo) {
            return logger.isInfoEnabled();
        } else {
            return logger.isDebugEnabled();
        }
    }

    // ===================================================================================
    //                                                                  Logging Adjustment
    //                                                                  ==================
    protected static boolean isJobLogLevelInfo() {
        return _jobLogLevelInfo;
    }

    public static void setJobLogLevelInfo(boolean jobLogLevelInfo) {
        assertUnlocked();
        if (logger.isInfoEnabled()) {
            logger.info("...Setting jobLogLevelInfo: " + jobLogLevelInfo);
        }
        _jobLogLevelInfo = jobLogLevelInfo;
        lock(); // auto-lock here, because of deep world
    }

    protected static boolean isLoggingInHolidayMood() {
        return _loggingInHolidayMood;
    }

    public static void setLoggingInHolidayMood(boolean loggingInHolidayMood) {
        assertUnlocked();
        if (logger.isInfoEnabled()) {
            logger.info("...Setting loggingInHolidayMood: " + loggingInHolidayMood);
        }
        _loggingInHolidayMood = loggingInHolidayMood;
        lock(); // auto-lock here, because of deep world
    }

    // ===================================================================================
    //                                                                        Logging Lock
    //                                                                        ============
    public static boolean isLocked() {
        return _locked;
    }

    public static void lock() {
        if (_locked) {
            return;
        }
        if (logger.isInfoEnabled()) {
            logger.info("...Locking the log object for job!");
        }
        _locked = true;
    }

    public static void unlock() {
        if (!_locked) {
            return;
        }
        if (logger.isInfoEnabled()) {
            logger.info("...Unlocking the log object for job!");
        }
        _locked = false;
    }

    protected static void assertUnlocked() {
        if (!isLocked()) {
            return;
        }
        throw new IllegalStateException("The job log is locked.");
    }
}
