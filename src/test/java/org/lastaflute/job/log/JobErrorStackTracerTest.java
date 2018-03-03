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
