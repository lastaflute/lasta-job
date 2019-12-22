/*
 * Copyright 2015-2019 the original author or authors.
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

import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author jflute
 * @since 0.2.0 (2016/01/15 Friday)
 */
public class JobNoticeLog {

    // ===================================================================================
    //                                                                          Definition
    //                                                                          ==========
    /** The logger instance for this class. (NotNull) */
    private static final Logger logger = LoggerFactory.getLogger(JobNoticeLog.class);

    // ===================================================================================
    //                                                                      Job Notice Log
    //                                                                      ==============
    public static void log(JobNoticeLogLevel logLevel, Supplier<String> msg) { // very internal
        if (JobNoticeLogLevel.INFO.equals(logLevel)) {
            logger.info(msg.get());
        } else if (JobNoticeLogLevel.DEBUG.equals(logLevel)) {
            logger.debug(msg.get());
        }
    }
}
