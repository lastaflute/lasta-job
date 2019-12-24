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

import java.lang.reflect.Field;
import java.nio.charset.Charset;

import org.dbflute.utflute.core.PlainTestCase;
import org.dbflute.util.DfReflectionUtil;
import org.dbflute.util.Srl;

/**
 * @author jflute
 * @since 0.5.3 (2018/02/26 Monday)
 */
public class JobErrorStackTracerTest extends PlainTestCase {

    // ===================================================================================
    //                                                                            Japanese
    //                                                                            ========
    public void test_buildExceptionStackTrace_Japanese_basic() {
        // ## Arrange ##
        JobErrorStackTracer tracer = new JobErrorStackTracer();
        String msg = "Japanese: 日本語";

        // ## Act ##
        IllegalStateException cause = new IllegalStateException(msg);
        String exp = tracer.buildExceptionStackTrace(cause);

        // ## Assert ##
        String firstLine = Srl.substringFirstFront(exp, ln());
        log("first line: {}", firstLine);
        assertContains(firstLine, msg);
        assertContains(exp, "JobErrorStackTracerTest.test_buildExceptionStackTrace_Japanese_basic");
    }

    // new PrintStream(out) uses system encoding, so you should use new PrintStream(out, false, "UTF-8")
    public void test_buildExceptionStackTrace_Japanese_windows() {
        // ## Arrange ##
        String systemEncodingKey = "file.encoding";
        String windowsEcoding = "MS932";
        String originally = System.getProperty(systemEncodingKey);
        System.setProperty(systemEncodingKey, windowsEcoding);
        forcedlySetDefaultCharset(Charset.forName(windowsEcoding));
        log("originally={}, currentEncoding={}, defaultCharset={}", originally, System.getProperty(systemEncodingKey),
                Charset.defaultCharset());
        try {
            JobErrorStackTracer tracer = new JobErrorStackTracer();
            String msg = "Japanese: 日本語";

            // ## Act ##
            IllegalStateException cause = new IllegalStateException(msg);
            String exp = tracer.buildExceptionStackTrace(cause);

            // ## Assert ##
            String firstLine = Srl.substringFirstFront(exp, ln());
            log("first line: {}", firstLine);
            assertContains(firstLine, msg);
            assertContains(exp, "JobErrorStackTracerTest.test_buildExceptionStackTrace_Japanese_windows");
        } finally {
            System.setProperty(systemEncodingKey, originally);
            forcedlySetDefaultCharset(Charset.forName(originally));
        }
    }

    private void forcedlySetDefaultCharset(Charset charset) {
        Field defaultCharsetField = DfReflectionUtil.getWholeField(Charset.class, "defaultCharset");
        DfReflectionUtil.setValueForcedly(defaultCharsetField, null, charset);
    }
}
