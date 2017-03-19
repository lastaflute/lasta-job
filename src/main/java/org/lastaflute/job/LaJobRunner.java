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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import org.dbflute.bhv.proposal.callback.ExecutedSqlCounter;
import org.dbflute.bhv.proposal.callback.TraceableSqlAdditionalInfoProvider;
import org.dbflute.hook.AccessContext;
import org.dbflute.hook.CallbackContext;
import org.dbflute.hook.SqlFireHook;
import org.dbflute.hook.SqlResultHandler;
import org.dbflute.hook.SqlStringFilter;
import org.dbflute.optional.OptionalThing;
import org.dbflute.util.DfTraceViewUtil;
import org.dbflute.util.DfTypeUtil;
import org.dbflute.util.Srl;
import org.lastaflute.core.exception.ExceptionTranslator;
import org.lastaflute.core.magic.ThreadCacheContext;
import org.lastaflute.core.mail.PostedMailCounter;
import org.lastaflute.core.smartdeploy.ManagedHotdeploy;
import org.lastaflute.core.util.ContainerUtil;
import org.lastaflute.db.dbflute.accesscontext.AccessContextArranger;
import org.lastaflute.db.dbflute.accesscontext.AccessContextResource;
import org.lastaflute.db.dbflute.accesscontext.PreparedAccessContext;
import org.lastaflute.db.dbflute.callbackcontext.traceablesql.RomanticTraceableSqlFireHook;
import org.lastaflute.db.dbflute.callbackcontext.traceablesql.RomanticTraceableSqlResultHandler;
import org.lastaflute.db.dbflute.callbackcontext.traceablesql.RomanticTraceableSqlStringFilter;
import org.lastaflute.db.jta.romanticist.SavedTransactionMemories;
import org.lastaflute.db.jta.romanticist.TransactionMemoriesProvider;
import org.lastaflute.job.key.LaJobKey;
import org.lastaflute.job.log.JobNoticeLog;
import org.lastaflute.job.log.JobNoticeLogHook;
import org.lastaflute.job.subsidiary.RunnerResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author jflute
 * @since 0.2.0 (2016/01/10 Sunday)
 */
public class LaJobRunner {

    // ===================================================================================
    //                                                                          Definition
    //                                                                          ==========
    private static final Logger logger = LoggerFactory.getLogger(LaJobRunner.class);
    protected static final String LF = "\n";
    protected static final String EX_IND = "  "; // indent for exception message

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    @Resource
    private JobManager jobManager; // injected simply
    @Resource
    private ExceptionTranslator exceptionTranslator; // injected simply

    protected AccessContextArranger accessContextArranger; // null allowed, option
    protected JobNoticeLogHook noticeLogHook; // null allowed, option
    protected int jobHistoryLimit = 100; // as framework default

    // ===================================================================================
    //                                                                              Option
    //                                                                              ======
    /**
     * @param oneArgLambda The callback of arranger for access contect of DBFlute. (NotNull)
     * @return this. (NotNull)
     */
    public LaJobRunner useAccessContext(AccessContextArranger oneArgLambda) { // almost required if DBFlute
        if (oneArgLambda == null) {
            throw new IllegalArgumentException("The argument 'oneArgLambda' (accessContextArranger) should not be null.");
        }
        this.accessContextArranger = oneArgLambda;
        return this;
    }

    /**
     * @param noticeLogHook The callback of notice log hook for e.g. saving to database. (NotNull)
     * @return this. (NotNull)
     */
    public LaJobRunner useNoticeLogHook(JobNoticeLogHook noticeLogHook) { // almost required if DBFlute
        if (noticeLogHook == null) {
            throw new IllegalArgumentException("The argument 'noticeLogHook' should not be null.");
        }
        this.noticeLogHook = noticeLogHook;
        return this;
    }

    /**
     * @param jobHistoryLimit The limit size of job history saved in memory. (NotNull)
     * @return this. (NotNull)
     */
    public LaJobRunner limitJobHistory(int jobHistoryLimit) { // almost required if DBFlute
        if (jobHistoryLimit < getJobHistoryMinimumLimit()) {
            throw new IllegalArgumentException("The argument 'jobHistoryLimit' should not be over or equal 10: " + jobHistoryLimit);
        }
        this.jobHistoryLimit = jobHistoryLimit;
        return this;
    }

    protected int getJobHistoryMinimumLimit() {
        return 10; // as default, no history is not allowed for LaunchedProcess
    }

    // ===================================================================================
    //                                                                                Run
    //                                                                               =====
    public RunnerResult run(Class<? extends LaJob> jobType, Supplier<LaJobRuntime> runtimeSupplier) {
        if (isPlainlyRun()) { // e.g. production, unit-test
            return doRun(jobType, runtimeSupplier.get());
        }
        // e.g. local development (hot deploy)
        // no synchronization here because allow web and job to be executed concurrently
        //synchronized (HotdeployLock.class) {
        final ClassLoader originalLoader = startHotdeploy();
        try {
            return doRun(jobType, runtimeSupplier.get());
        } finally {
            stopHotdeploy(originalLoader);
        }
    }

    protected boolean isPlainlyRun() {
        return !isHotdeployEnabled() || isSuppressHotdeploy();
    }

    protected boolean isHotdeployEnabled() {
        return ManagedHotdeploy.isHotdeploy();
    }

    protected boolean isSuppressHotdeploy() { // for emergency
        return false;
    }

    protected ClassLoader startHotdeploy() {
        return ManagedHotdeploy.start(); // #thiking: cannot hotdeploy, why?
    }

    protected void stopHotdeploy(ClassLoader originalLoader) {
        ManagedHotdeploy.stop(originalLoader);
    }

    protected RunnerResult doRun(Class<? extends LaJob> jobType, LaJobRuntime runtime) {
        // simplar to async manager's process
        arrangeThreadCacheContext(runtime);
        arrangePreparedAccessContext(runtime);
        arrangeCallbackContext(runtime);
        final Object variousPreparedObj = prepareVariousContext(runtime);
        final long before = showRunning(runtime);
        Throwable cause = null;
        try {
            hookBefore(runtime);
            actuallyRun(jobType, runtime);
        } catch (Throwable e) {
            final Throwable filtered = filterCause(e);
            cause = filtered;
            showJobException(runtime, before, filtered);
        } finally {
            hookFinally(runtime, OptionalThing.ofNullable(cause, () -> {
                throw new IllegalStateException("Not found the cause: " + runtime);
            }));
            showFinishing(runtime, before, cause); // should be before clearing because of using them
            clearVariousContext(runtime, variousPreparedObj);
            clearPreparedAccessContext();
            clearCallbackContext();
            clearThreadCacheContext();
        }
        return createRunnerResult(runtime, cause);
    }

    protected void hookBefore(LaJobRuntime runtime) {
        // you can check your rule
    }

    protected void actuallyRun(Class<? extends LaJob> jobType, LaJobRuntime runtime) {
        final LaJob job = getJobComponent(jobType);
        job.run(runtime);
    }

    protected LaJob getJobComponent(Class<? extends LaJob> jobType) {
        return ContainerUtil.getComponent(jobType);
    }

    protected void hookFinally(LaJobRuntime runtime, OptionalThing<Throwable> cause) {
        // you can check your rule
    }

    protected RunnerResult createRunnerResult(LaJobRuntime runtime, Throwable cause) {
        return RunnerResult.asExecuted(cause, runtime.isNextTriggerSuppressed());
    }

    protected boolean isBusinessSuccess(LaJobRuntime runtime, Throwable cause) {
        return cause == null; // simple as default
    }

    // ===================================================================================
    //                                                                            Show Job
    //                                                                            ========
    protected long showRunning(LaJobRuntime runtime) {
        // no use enabled determination to be simple
        final String msg = "#flow #job ...Running job: " + runtime.toCronMethodDisp();
        JobNoticeLog.log(runtime.getNoticeLogLevel(), () -> msg);
        if (noticeLogHook != null) {
            noticeLogHook.hookRunning(runtime, msg);
        }
        return System.currentTimeMillis();

    }

    protected void showFinishing(LaJobRuntime runtime, long before, Throwable cause) {
        final String msg = buildFinishingMsg(runtime, before, cause); // also no use enabled
        if (noticeLogHook != null) {
            noticeLogHook.hookFinishing(runtime, msg, OptionalThing.ofNullable(cause, () -> {
                throw new IllegalStateException("Not found the cause: " + runtime);
            }));
        }
        JobNoticeLog.log(runtime.getNoticeLogLevel(), () -> msg);
    }

    protected String buildFinishingMsg(LaJobRuntime runtime, long before, Throwable cause) {
        final long after = System.currentTimeMillis();
        final StringBuilder sb = new StringBuilder();
        sb.append("#flow #job ...Finishing job: ").append(runtime.toRunMethodDisp());
        sb.append(LF).append("[Job Result]");
        sb.append(LF).append(" performanceView: ").append(toPerformanceView(before, after));
        extractSqlCount().ifPresent(counter -> {
            sb.append(LF).append(" sqlCount: ").append(counter.toLineDisp());
        });
        extractMailCount().ifPresent(counter -> {
            sb.append(LF).append(" mailCount: ").append(counter.toLineDisp());
        });
        sb.append(LF).append(" runtime: ").append(runtime);
        buildTriggeredNextJobExp(runtime).ifPresent(exp -> {
            sb.append(LF).append(" triggeredNextJob: ").append(exp);
        });
        if (runtime.isNextTriggerSuppressed()) {
            sb.append(LF).append(" suppressNextTrigger: true");
        }
        runtime.getEndTitleRoll().ifPresent(roll -> {
            sb.append(LF).append(" endTitleRoll:");
            roll.getDataMap().forEach((key, value) -> {
                sb.append(LF).append("   ").append(key).append(": ").append(value);
            });
        });
        if (cause != null) {
            sb.append(LF).append(" cause: ");
            sb.append(cause.getClass().getSimpleName()).append(" *Read the exception message!");
            sb.append(" ").append(Integer.toHexString(cause.hashCode()));
        }
        sb.append(LF).append(LF); // to separate from job logging
        return sb.toString();
    }

    protected OptionalThing<String> buildTriggeredNextJobExp(LaJobRuntime runtime) {
        final LaJobKey jobKey = runtime.getJobKey();
        final String exp = jobManager.findJobByKey(jobKey).map(job -> {
            final List<LaJobKey> triggeredJobKeyList = job.getTriggeredJobKeyList();
            if (!triggeredJobKeyList.isEmpty()) {
                final List<String> dispList = triggeredJobKeyList.stream().map(triggeredJobKey -> {
                    return jobManager.findJobByKey(triggeredJobKey).map(triggeredJob -> {
                        return triggeredJob.toIdentityDisp();
                    }).orElseGet(() -> "(*Not found the triggered job: " + triggeredJobKey + ")");
                }).collect(Collectors.toList());
                return dispList.size() == 1 ? dispList.get(0) : dispList.toString();
            } else {
                return ""; // no show
            }
        }).orElseGet(() -> { // no way but just in case for logging
            return "(*Not found the current job: " + jobKey + ")";
        });
        return OptionalThing.ofNullable(!exp.isEmpty() ? exp : null, () -> {
            throw new IllegalStateException("Not found the triggeredNextJob expression: " + exp);
        });
    }

    protected String toPerformanceView(long before, long after) {
        return DfTraceViewUtil.convertToPerformanceView(after - before);
    }

    // ===================================================================================
    //                                                                        Thread Cache
    //                                                                        ============
    protected void arrangeThreadCacheContext(LaJobRuntime runtime) {
        ThreadCacheContext.initialize();
        ThreadCacheContext.registerRequestPath(runtime.getJobType().getName());
        ThreadCacheContext.registerEntryMethod(runtime.getRunMethod());
    }

    protected void clearThreadCacheContext() {
        ThreadCacheContext.clear();
    }

    // ===================================================================================
    //                                                                       AccessContext
    //                                                                       =============
    protected void arrangePreparedAccessContext(LaJobRuntime runtime) {
        if (accessContextArranger == null) {
            return;
        }
        final AccessContextResource resource = createAccessContextResource(runtime);
        final AccessContext context = accessContextArranger.arrangePreparedAccessContext(resource);
        if (context == null) {
            String msg = "Cannot return null from access context arranger: " + accessContextArranger + " runtime=" + runtime;
            throw new IllegalStateException(msg);
        }
        PreparedAccessContext.setAccessContextOnThread(context);
    }

    protected AccessContextResource createAccessContextResource(LaJobRuntime runtime) {
        return new AccessContextResource(DfTypeUtil.toClassTitle(runtime.getJobType()), runtime.getRunMethod());
    }

    protected void clearPreparedAccessContext() {
        PreparedAccessContext.clearAccessContextOnThread();
    }

    // ===================================================================================
    //                                                                     CallbackContext
    //                                                                     ===============
    protected void arrangeCallbackContext(LaJobRuntime runtime) {
        CallbackContext.setSqlFireHookOnThread(createSqlFireHook(runtime));
        CallbackContext.setSqlStringFilterOnThread(createSqlStringFilter(runtime));
        CallbackContext.setSqlResultHandlerOnThread(createSqlResultHandler());
    }

    protected SqlFireHook createSqlFireHook(LaJobRuntime runtime) {
        return newRomanticTraceableSqlFireHook();
    }

    protected RomanticTraceableSqlFireHook newRomanticTraceableSqlFireHook() {
        return new RomanticTraceableSqlFireHook();
    }

    protected SqlStringFilter createSqlStringFilter(LaJobRuntime runtime) {
        return newRomanticTraceableSqlStringFilter(runtime.getRunMethod(), () -> buildSqlMarkingAdditionalInfo());
    }

    protected String buildSqlMarkingAdditionalInfo() {
        return null; // no additional info as default
    }

    protected RomanticTraceableSqlStringFilter newRomanticTraceableSqlStringFilter(Method actionMethod,
            TraceableSqlAdditionalInfoProvider additionalInfoProvider) {
        return new RomanticTraceableSqlStringFilter(actionMethod, additionalInfoProvider);
    }

    protected SqlResultHandler createSqlResultHandler() {
        return newRomanticTraceableSqlResultHandler();
    }

    protected RomanticTraceableSqlResultHandler newRomanticTraceableSqlResultHandler() {
        return new RomanticTraceableSqlResultHandler();
    }

    protected void clearCallbackContext() {
        CallbackContext.clearSqlResultHandlerOnThread();
        CallbackContext.clearSqlStringFilterOnThread();
        CallbackContext.clearSqlFireHookOnThread();
    }

    // ===================================================================================
    //                                                                      VariousContext
    //                                                                      ==============
    protected Object prepareVariousContext(LaJobRuntime runtime) { // for extension
        return null;
    }

    protected void clearVariousContext(LaJobRuntime runtime, Object variousPreparedObj) { // for extension
    }

    // ===================================================================================
    //                                                                  Exception Handling
    //                                                                  ==================
    protected Throwable filterCause(Throwable e) {
        return exceptionTranslator.filterCause(e);
    }

    protected void showJobException(LaJobRuntime runtime, long before, Throwable cause) {
        final String msg = buildJobExceptionMessage(runtime, before, cause);
        logJobException(runtime, msg, cause);
    }

    // -----------------------------------------------------
    //                                      Message Building
    //                                      ----------------
    protected String buildJobExceptionMessage(LaJobRuntime runtime, long before, Throwable cause) {
        final StringBuilder sb = new StringBuilder();
        sb.append("Failed to run the job process: #flow #job");
        sb.append(LF);
        sb.append("/= = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = =: ");
        sb.append(runtime.getJobType().getName());
        sb.append(LF).append(EX_IND);
        sb.append("jobRuntime=").append(runtime);
        setupExceptionMessageAccessContext(sb, runtime);
        setupExceptionMessageCallbackContext(sb, runtime);
        setupExceptionMessageVariousContext(sb, runtime, cause);
        setupExceptionMessageSqlCountIfExists(sb);
        setupExceptionMessageTransactionMemoriesIfExists(sb);
        setupExceptionMessageMailCountIfExists(sb);
        final long after = System.currentTimeMillis();
        final String performanceView = toPerformanceView(before, after);
        sb.append(LF);
        sb.append("= = = = = = = = = =/ [").append(performanceView).append("] #").append(Integer.toHexString(cause.hashCode()));
        buildExceptionStackTrace(cause, sb);
        return sb.toString().trim();
    }

    protected void setupExceptionMessageAccessContext(StringBuilder sb, LaJobRuntime runtime) {
        sb.append(LF).append(EX_IND).append("; accessContext=").append(PreparedAccessContext.getAccessContextOnThread());
    }

    protected void setupExceptionMessageCallbackContext(StringBuilder sb, LaJobRuntime runtime) {
        sb.append(LF).append(EX_IND).append("; callbackContext=").append(CallbackContext.getCallbackContextOnThread());
    }

    protected void setupExceptionMessageVariousContext(StringBuilder sb, LaJobRuntime runtime, Throwable cause) {
        final StringBuilder variousContextSb = new StringBuilder();
        buildVariousContextInJobExceptionMessage(variousContextSb, runtime, cause);
        if (variousContextSb.length() > 0) {
            sb.append(LF).append(EX_IND).append(variousContextSb.toString());
        }
    }

    protected void buildVariousContextInJobExceptionMessage(StringBuilder sb, LaJobRuntime runtime, Throwable cause) {
    }

    protected void setupExceptionMessageSqlCountIfExists(StringBuilder sb) {
        extractSqlCount().ifPresent(counter -> {
            sb.append(LF).append(EX_IND).append("; sqlCount=").append(counter.toLineDisp());
        });
    }

    protected void setupExceptionMessageTransactionMemoriesIfExists(StringBuilder sb) {
        // e.g.
        // _/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/
        // ; transactionMemories=wholeShow:  
        // *RomanticTransaction@2d1cd52a
        // << Transaction Current State >>
        // beginning time: 2015/12/22 12:04:40.574
        // table command: map:{PRODUCT = list:{selectCursor ; scalarSelect(LocalDate).max}}
        // << Transaction Recent Result >>
        // 1. (2015/12/22 12:04:40.740) [00m00s027ms] PRODUCT@selectCursor => Object:{}
        // 2. (2015/12/22 12:04:40.773) [00m00s015ms] PRODUCT@scalarSelect(LocalDate).max => LocalDate:{value=2013-08-02}
        // _/_/_/_/_/_/_/_/_/_/
        final SavedTransactionMemories memories = ThreadCacheContext.findTransactionMemories();
        if (memories != null) {
            final List<TransactionMemoriesProvider> providerList = memories.getOrderedProviderList();
            final StringBuilder txSb = new StringBuilder();
            for (TransactionMemoriesProvider provider : providerList) {
                provider.provide().ifPresent(result -> {
                    if (txSb.length() == 0) {
                        txSb.append(LF).append(EX_IND).append("; transactionMemories=wholeShow:");
                    }
                    txSb.append(Srl.indent(EX_IND.length(), LF + "*" + result));
                });
            }
            sb.append(txSb);
        }
    }

    protected void setupExceptionMessageMailCountIfExists(StringBuilder sb) {
        extractMailCount().ifPresent(counter -> {
            sb.append(LF).append(EX_IND).append("; mailCount=").append(counter.toLineDisp());
        });
    }

    protected void buildExceptionStackTrace(Throwable cause, StringBuilder sb) { // similar to logging filter
        sb.append(LF);
        final ByteArrayOutputStream out = new ByteArrayOutputStream(1024);
        PrintStream ps = null;
        try {
            ps = new PrintStream(out);
            cause.printStackTrace(ps);
            final String encoding = "UTF-8";
            try {
                sb.append(out.toString(encoding));
            } catch (UnsupportedEncodingException continued) {
                logger.warn("Unknown encoding: " + encoding, continued);
                sb.append(out.toString()); // retry without encoding
            }
        } finally {
            if (ps != null) {
                ps.close();
            }
        }
    }

    // -----------------------------------------------------
    //                                     Exception Logging
    //                                     -----------------
    protected void logJobException(LaJobRuntime runtime, String msg, Throwable cause) { // msg contains stack-trace
        logger.error(msg); // not use second argument here, same reason as logging filter
    }

    // ===================================================================================
    //                                                                           SQL Count
    //                                                                           =========
    protected OptionalThing<ExecutedSqlCounter> extractSqlCount() {
        final CallbackContext context = CallbackContext.getCallbackContextOnThread();
        if (context == null) {
            return OptionalThing.empty();
        }
        final SqlStringFilter filter = context.getSqlStringFilter();
        if (filter == null || !(filter instanceof ExecutedSqlCounter)) {
            return OptionalThing.empty();
        }
        return OptionalThing.of(((ExecutedSqlCounter) filter));
    }

    // ===================================================================================
    //                                                                          Mail Count
    //                                                                          ==========
    protected OptionalThing<PostedMailCounter> extractMailCount() {
        return OptionalThing.ofNullable(ThreadCacheContext.findMailCounter(), () -> {
            throw new IllegalStateException("Not found the mail count in the thread cache.");
        });
    }

    // ===================================================================================
    //                                                                      Basic Override
    //                                                                      ==============
    @Override
    public String toString() {
        return DfTypeUtil.toClassTitle(this) + ":{accessContext=" + accessContextArranger + "}@" + Integer.toHexString(hashCode());
    }
}
