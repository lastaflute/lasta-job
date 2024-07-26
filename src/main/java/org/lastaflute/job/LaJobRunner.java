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
package org.lastaflute.job;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

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
import org.lastaflute.job.exception.JobStoppedException;
import org.lastaflute.job.key.LaJobKey;
import org.lastaflute.job.log.JobErrorLog;
import org.lastaflute.job.log.JobErrorLogHook;
import org.lastaflute.job.log.JobErrorResource;
import org.lastaflute.job.log.JobErrorStackTracer;
import org.lastaflute.job.log.JobHistoryHook;
import org.lastaflute.job.log.JobNoticeLog;
import org.lastaflute.job.log.JobNoticeLogHook;
import org.lastaflute.job.subsidiary.CrossVMHook;
import org.lastaflute.job.subsidiary.RunnerResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.Resource;

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
    protected static final DateTimeFormatter beginTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    // -----------------------------------------------------
    //                                          DI Component
    //                                          ------------
    @Resource
    private JobManager jobManager; // injected simply
    @Resource
    private ExceptionTranslator exceptionTranslator; // injected simply

    // -----------------------------------------------------
    //                                                Option
    //                                                ------
    protected AccessContextArranger accessContextArranger; // null allowed, option
    protected CrossVMHook crossVMHook;
    protected JobErrorLogHook errorLogHook; // null allowed, option
    protected JobHistoryHook historyHook; // null allowed, option
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
        assertArgumentNotNull("oneArgLambda(accessContextArranger)", oneArgLambda);
        this.accessContextArranger = oneArgLambda;
        return this;
    }

    /**
     * @param crossVMHook The callback of cross-VM hook for sharing with other JavaVMs. (NotNull)
     * @return this. (NotNull)
     */
    public LaJobRunner useCrossVMHook(CrossVMHook crossVMHook) {
        assertArgumentNotNull("crossVMHook", crossVMHook);
        this.crossVMHook = crossVMHook;
        return this;
    }

    /**
     * @param errorLogHook The callback of error log hook for e.g. saving to database, sending mail. (NotNull)
     * @return this. (NotNull)
     */
    public LaJobRunner useErrorLogHook(JobErrorLogHook errorLogHook) {
        assertArgumentNotNull("errorLogHook", errorLogHook);
        this.errorLogHook = errorLogHook;
        return this;
    }

    /**
     * @param historyHook The callback of history hook for e.g. saving to database. (NotNull)
     * @return this. (NotNull)
     */
    public LaJobRunner useHistoryHook(JobHistoryHook historyHook) {
        assertArgumentNotNull("historyHook", historyHook);
        this.historyHook = historyHook;
        return this;
    }

    /**
     * @param noticeLogHook The callback of notice log hook for e.g. saving to database. (NotNull)
     * @return this. (NotNull)
     */
    public LaJobRunner useNoticeLogHook(JobNoticeLogHook noticeLogHook) {
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
    // -----------------------------------------------------
    //                                          Entry of Run
    //                                          ------------
    public RunnerResult run(Class<? extends LaJob> jobType, Supplier<LaJobRuntime> runtimeSupplier) {
        if (isPlainlyRun()) { // e.g. production, unit-test
            final LaJobRuntime runtime = runtimeSupplier.get();
            debugFw(runtime, "...Calling doRun() of job runner plainly");
            return doRun(jobType, runtime);
        }
        // e.g. local development (hot deploy)
        // no synchronization here because allow web and job to be executed concurrently
        //synchronized (HotdeployLock.class) {
        final ClassLoader originalLoader = startHotdeploy();
        try {
            final LaJobRuntime runtime = runtimeSupplier.get(); // no recycle to create hot deploy (just in case)
            debugFw(runtime, "...Calling doRun() of job runner as hotdeploy: {}", originalLoader);
            return doRun(jobType, runtime);
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
        return ManagedHotdeploy.start();
    }

    protected void stopHotdeploy(ClassLoader originalLoader) {
        ManagedHotdeploy.stop(originalLoader);
    }

    // -----------------------------------------------------
    //                                      Main Flow of Run
    //                                      ----------------
    protected RunnerResult doRun(Class<? extends LaJob> jobType, LaJobRuntime runtime) {
        // similar to async manager's process
        arrangeThreadCacheContext(runtime);
        arrangePreparedAccessContext(runtime);
        arrangeCallbackContext(runtime);
        final Object variousPreparedObj = prepareVariousContext(runtime);
        final long before = showRunning(runtime);
        Throwable cause = null;
        try {
            debugFw(runtime, "...Calling try clause of job runner");
            hookBefore(runtime);
            debugFw(runtime, "...Calling actuallyRun() of job runner");
            actuallyRun(jobType, runtime);
        } catch (Throwable e) {
            debugFw(runtime, "...Calling catch clause of job runner: {}", e.getClass().getSimpleName());
            final Throwable filtered = filterCause(e);
            cause = filtered;
            showJobException(runtime, before, filtered);
        } finally {
            debugFw(runtime, "...Calling finally clause of job runner");
            hookFinally(runtime, OptionalThing.ofNullable(cause, () -> {
                throw new IllegalStateException("Not found the cause: " + runtime);
            }));
            showFinishing(runtime, before, cause); // should be before clearing because of using them
            clearVariousContext(runtime, variousPreparedObj);
            clearPreparedAccessContext();
            clearCallbackContext();
            clearThreadCacheContext();
        }
        debugFw(runtime, "...Calling createRunnerResult() of job runner");
        return createRunnerResult(runtime, cause);
    }

    // -----------------------------------------------------
    //                                           Hook Before
    //                                           -----------
    protected void hookBefore(LaJobRuntime runtime) {
        // you can check your rule
    }

    // -----------------------------------------------------
    //                                          Actually Run
    //                                          ------------
    protected void actuallyRun(Class<? extends LaJob> jobType, LaJobRuntime runtime) {
        final LaJob job = getJobComponent(jobType);
        job.run(runtime);
    }

    // -----------------------------------------------------
    //                                         Job Component
    //                                         -------------
    protected LaJob getJobComponent(Class<? extends LaJob> jobType) {
        return ContainerUtil.getComponent(resolveJobType(jobType));
    }

    protected Class<? extends LaJob> resolveJobType(Class<? extends LaJob> jobType) {
        if (ManagedHotdeploy.isThreadContextHotdeploy()) { // means hot-deploy started
            return reloadJobTypeByContextClassLoader(jobType);
        } else { // e.g. cool-deploy
            return jobType;
        }
    }

    @SuppressWarnings("unchecked")
    protected Class<? extends LaJob> reloadJobTypeByContextClassLoader(Class<? extends LaJob> jobType) {
        try {
            // because the jobType is from the first hot-deploy class loader if hot-deploy
            final ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
            return (Class<? extends LaJob>) Class.forName(jobType.getName(), /*initialize*/true, contextClassLoader);
        } catch (ClassNotFoundException e) { // basically no way
            throw new IllegalStateException("Not found the job: " + jobType, e);
        }
    }

    // -----------------------------------------------------
    //                                          Hook Finally
    //                                          ------------
    protected void hookFinally(LaJobRuntime runtime, OptionalThing<Throwable> cause) {
        // you can check your rule
    }

    // -----------------------------------------------------
    //                                         Runner Result
    //                                         -------------
    protected RunnerResult createRunnerResult(LaJobRuntime runtime, Throwable cause) {
        return RunnerResult.asExecuted(runtime.getBeginTime(), runtime.getEndTitleRoll(), OptionalThing.ofNullable(cause, () -> {
            throw new IllegalStateException("Not found the cause.");
        }), runtime.isNextTriggerSuppressed());
    }

    // ===================================================================================
    //                                                                            Show Job
    //                                                                            ========
    // -----------------------------------------------------
    //                                               Running
    //                                               -------
    protected long showRunning(LaJobRuntime runtime) {
        JobNoticeLog.log(runtime.getNoticeLogLevel(), () -> buildRunningJobLogMessage(runtime));
        if (noticeLogHook != null) {
            noticeLogHook.hookRunning(runtime, buildRunningJobLogMessage(runtime));
        }
        return System.currentTimeMillis();
    }

    protected String buildRunningJobLogMessage(LaJobRuntime runtime) {
        final StringBuilder sb = new StringBuilder();
        sb.append("#flow #job ...Running job: ");
        sb.append(runtime.toCronMethodDisp());
        buildProcessHashIfNeeds(sb);
        return sb.toString();
    }

    // -----------------------------------------------------
    //                                             Finishing
    //                                             ---------
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
        buildProcessHashIfNeeds(sb);
        sb.append(LF).append("[Job Result]");
        buildBeginTimeIfNeeds(sb);
        sb.append(LF).append(" performanceView: ").append(toPerformanceView(before, after));
        extractSqlCounter().ifPresent(counter -> {
            sb.append(LF).append(" sqlCount: ").append(counter.toLineDisp());
        });
        extractMailCounter().ifPresent(counter -> {
            sb.append(LF).append(" mailCount: ").append(counter.toLineDisp());
        });
        extractRemoteApiCounter().ifPresent(counter -> {
            sb.append(LF).append(" remoteApiCount: ").append(counter.get());
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

    // -----------------------------------------------------
    //                                           Item Helper
    //                                           -----------
    protected void buildBeginTimeIfNeeds(StringBuilder sb) {
        final Object beginTime = findBeginTime();
        if (beginTime != null) {
            sb.append(LF).append(" beginTime: ");
            final String beginExp;
            if (beginTime instanceof LocalDateTime) { // basically here
                beginExp = beginTimeFormatter.format((LocalDateTime) beginTime);
            } else { // no way, just in case
                beginExp = beginTime.toString();
            }
            sb.append(beginExp);
        }
    }

    protected void buildProcessHashIfNeeds(StringBuilder sb) {
        final Object processHash = findProcessHash();
        if (processHash != null) {
            sb.append(" #").append(processHash);
        }
    }

    protected String toPerformanceView(long before, long after) {
        return DfTraceViewUtil.convertToPerformanceView(after - before);
    }

    protected OptionalThing<String> buildTriggeredNextJobExp(LaJobRuntime runtime) {
        final LaJobKey jobKey = runtime.getJobKey();
        final String exp = jobManager.findJobByKey(jobKey).map(job -> {
            final Set<LaJobKey> triggeredJobKeyList = job.getTriggeredJobKeySet();
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

    // ===================================================================================
    //                                                                        Thread Cache
    //                                                                        ============
    // -----------------------------------------------------
    //                                               Arrange
    //                                               -------
    protected void arrangeThreadCacheContext(LaJobRuntime runtime) {
        ThreadCacheContext.initialize();

        // core and basic items are used by e.g. remoteApi's send-receive logging
        final LocalDateTime beginTime = runtime.getBeginTime();
        registerBeginTime(beginTime);
        registerProcessHash(generateProcessHash(beginTime));
        ThreadCacheContext.registerRequestPath(buildRequestPath(runtime)); // e.g. SeaJob
        ThreadCacheContext.registerEntryMethod(runtime.getRunMethod()); // e.g. SeaJob@run()
    }

    protected String generateProcessHash(LocalDateTime beginTime) {
        final String bestEffortUnique = beginTime.toString() + Thread.currentThread().getName();
        return Integer.toHexString(bestEffortUnique.hashCode());
    }

    protected String buildRequestPath(LaJobRuntime runtime) {
        return runtime.getJobType().getSimpleName(); // e.g. SeaJob
    }

    // -----------------------------------------------------
    //                                         Expected Core
    //                                         -------------
    // expects LastaFlute-1.0.1
    protected Object findBeginTime() {
        return ThreadCacheContext.getObject("fw:beginTime");
    }

    protected void registerBeginTime(final LocalDateTime beginTime) {
        ThreadCacheContext.setObject("fw:beginTime", beginTime);
    }

    protected Object findProcessHash() {
        return ThreadCacheContext.getObject("fw:processHash");
    }

    protected void registerProcessHash(String processHash) {
        ThreadCacheContext.setObject("fw:processHash", processHash);
    }

    // -----------------------------------------------------
    //                                                 Clear
    //                                                 -----
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
        final String classTitle = DfTypeUtil.toClassTitle(runtime.getJobType());
        final Method runMethod = runtime.getRunMethod();
        final Map<String, Object> runtimeAttributeMap = new LinkedHashMap<>(runtime.getParameterMap());
        return new AccessContextResource(classTitle, runMethod, runtimeAttributeMap);
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
        sb.append("#flow #job Failed to run the job process:");
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
        setupExceptionMessageRemoteApiCountIfExists(sb);
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
        extractSqlCounter().ifPresent(counter -> {
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
        extractMailCounter().ifPresent(counter -> {
            sb.append(LF).append(EX_IND).append("; mailCount=").append(counter.toLineDisp());
        });
    }

    protected void setupExceptionMessageRemoteApiCountIfExists(StringBuilder sb) {
        extractRemoteApiCounter().ifPresent(counter -> {
            sb.append(LF).append(EX_IND).append("; remoteApiCount=").append(counter.get());
        });
    }

    protected void buildExceptionStackTrace(Throwable cause, StringBuilder sb) {
        sb.append(LF).append(new JobErrorStackTracer().buildExceptionStackTrace(cause));
    }

    // -----------------------------------------------------
    //                                     Exception Logging
    //                                     -----------------
    protected void logJobException(LaJobRuntime runtime, String bigMsg, Throwable cause) { // bigMsg contains stack-trace
        if (isBusinessStoppedException(cause)) {
            JobNoticeLog.log(runtime.getNoticeLogLevel(), () -> bigMsg);
            if (noticeLogHook != null) {
                noticeLogHook.hookStopped(runtime, bigMsg, OptionalThing.of(cause));
            }
        } else {
            if (errorLogHook != null) {
                final OptionalThing<LaScheduledJob> job = jobManager.findJobByKey(runtime.getJobKey());
                errorLogHook.hookError(new JobErrorResource(job, OptionalThing.of(runtime), bigMsg, cause));
            }
            JobErrorLog.log(bigMsg); // not use second argument here, same reason as logging filter
        }
    }

    protected boolean isBusinessStoppedException(Throwable cause) {
        return cause instanceof JobStoppedException; // from runtime.stopIfNeeds()
    }

    // ===================================================================================
    //                                                                         SQL Counter
    //                                                                         ===========
    protected OptionalThing<ExecutedSqlCounter> extractSqlCounter() {
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
    //                                                                        Mail Counter
    //                                                                        ============
    protected OptionalThing<PostedMailCounter> extractMailCounter() {
        return OptionalThing.ofNullable(ThreadCacheContext.findMailCounter(), () -> {
            throw new IllegalStateException("Not found the mail counter in the thread cache.");
        });
    }

    // ===================================================================================
    //                                                                   RemoteApi Counter
    //                                                                   =================
    protected OptionalThing<Supplier<String>> extractRemoteApiCounter() {
        final Supplier<String> counter = ThreadCacheContext.getObject("fw:remoteApiCounter"); // expectes LastaFlute-1.0.1
        return OptionalThing.ofNullable(counter, () -> {
            throw new IllegalStateException("Not found the remote-api counter in the thread cache.");
        });
    }

    // ===================================================================================
    //                                                                     Framework Debug
    //                                                                     ===============
    protected void debugFw(LaJobRuntime runtime, String msg, Object... args) {
        if (runtime.isFrameworkDebug() && logger.isInfoEnabled()) {
            logger.info("#job #fw " + msg, args); // info level for production 
        }
    }

    // ===================================================================================
    //                                                                        Small Helper
    //                                                                        ============
    protected void assertArgumentNotNull(String variableName, Object obj) {
        if (obj == null) {
            throw new IllegalArgumentException("The argument '" + variableName + "' should not be null.");
        }
    }

    // ===================================================================================
    //                                                                      Basic Override
    //                                                                      ==============
    @Override
    public String toString() {
        return DfTypeUtil.toClassTitle(this) + ":{accessContext=" + accessContextArranger + "}@" + Integer.toHexString(hashCode());
    }

    // ===================================================================================
    //                                                                            Accessor
    //                                                                            ========
    public OptionalThing<AccessContextArranger> getAccessContextArranger() {
        return OptionalThing.ofNullable(accessContextArranger, () -> {
            throw new IllegalStateException("Not found the accessContextArranger.");
        });
    }

    public OptionalThing<CrossVMHook> getCrossVMHook() {
        return OptionalThing.ofNullable(crossVMHook, () -> {
            throw new IllegalStateException("Not found the crossVMHook.");
        });
    }

    public OptionalThing<JobErrorLogHook> getErrorLogHook() {
        return OptionalThing.ofNullable(errorLogHook, () -> {
            throw new IllegalStateException("Not found the errorLogHook.");
        });
    }

    public OptionalThing<JobHistoryHook> getHistoryHook() {
        return OptionalThing.ofNullable(historyHook, () -> {
            throw new IllegalStateException("Not found the historyHook.");
        });
    }
}
