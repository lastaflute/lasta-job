/*
 * Copyright 2015-2024 the original author or authors.
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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author jflute
 * @since 0.4.1 (2017/03/20 Monday)
 */
public class JobErrorStackTracer {

    private static final Logger logger = LoggerFactory.getLogger(JobErrorStackTracer.class);

    public String buildExceptionStackTrace(Throwable cause) { // similar to logging filter
        final StringBuilder sb = new StringBuilder();
        final ByteArrayOutputStream out = new ByteArrayOutputStream(1024);
        final String encoding = "UTF-8"; // for on-memory closed-scope I/O
        PrintStream ps = null;
        try {
            final boolean autoFlush = false; // the output stream does not need flush
            ps = new PrintStream(out, autoFlush, encoding);
            cause.printStackTrace(ps);
            sb.append(out.toString(encoding));
        } catch (UnsupportedEncodingException continued) { // basically no way
            logger.warn("Unknown encoding: " + encoding, continued);
            sb.append(out.toString()); // retry without encoding
        } finally {
            if (ps != null) {
                ps.close();
            }
        }
        return sb.toString();
    }
}
