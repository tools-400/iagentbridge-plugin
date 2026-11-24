/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.tools;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import de.tools400.iagentbridge.mcp.McpToolResult;

/**
 * Result of the tool run_tests: how the test run ended, the number of tests per
 * outcome, the duration and the failed tests with their shortened stack traces.
 * <p>
 * The class has no Eclipse dependencies, so that it can be tested outside of
 * RDi.
 */
public final class TestRunReport {

    /** Maximum number of lines of a stack trace in the report */
    public static final int MAX_TRACE_LINES = 30;

    /** Maximum number of characters of an expected or actual value */
    public static final int MAX_VALUE_LENGTH = 4000;

    /**
     * Frames, that the view JUnit of RDi filters by default: the test runners
     * and JUnit itself, e.g. the methods of Assert, and the calls by
     * reflection. A pattern, that ends with '*', is a package, else a class or
     * method.
     */
    private static final String[] TRACE_FILTERS = { "org.eclipse.jdt.internal.junit.runner.*", //$NON-NLS-1$
        "org.eclipse.jdt.internal.junit4.runner.*", //$NON-NLS-1$
        "org.eclipse.jdt.internal.junit5.runner.*", //$NON-NLS-1$
        "org.eclipse.jdt.internal.junit.ui.*", //$NON-NLS-1$
        "junit.framework.TestCase", //$NON-NLS-1$
        "junit.framework.TestResult", //$NON-NLS-1$
        "junit.framework.TestResult$1", //$NON-NLS-1$
        "junit.framework.TestSuite", //$NON-NLS-1$
        "junit.framework.Assert", //$NON-NLS-1$
        "org.junit.*", //$NON-NLS-1$
        "java.lang.reflect.Method.invoke", //$NON-NLS-1$
        "sun.reflect.*", //$NON-NLS-1$
        "jdk.internal.reflect.*" }; //$NON-NLS-1$

    private static final String FRAME_PREFIX = "at "; //$NON-NLS-1$

    private final String launch;
    private final int maxFailures;
    private final List<Result> failures;
    private final List<String> running;
    private final List<String> warnings;
    private int runCount;
    private int failureCount;
    private int errorCount;
    private int ignoredCount;
    private End end;
    private long millis;

    /**
     * Creates an empty report of a test run, that has not ended yet.
     *
     * @param aLaunch - name of the launch configuration
     * @param aMaxFailures - maximum number of failed tests in the report
     */
    public TestRunReport(String aLaunch, int aMaxFailures) {
        launch = aLaunch;
        maxFailures = aMaxFailures;
        failures = new ArrayList<Result>();
        running = new ArrayList<String>();
        warnings = new ArrayList<String>();
        end = End.ABORTED;
    }

    /**
     * Adds the result of a test or the failure of a test class, e.g. of a
     * method annotated with BeforeClass.
     *
     * @param aResult - result
     */
    public void add(Result aResult) {

        switch (aResult.outcome) {
        case IGNORED:
            ignoredCount++;
            return;
        case FAILURE:
            failureCount++;
            break;
        case ERROR:
            errorCount++;
            break;
        default:
            break;
        }

        if (aResult.methodName != null) {
            runCount++;
        }
        if (aResult.outcome != Outcome.OK) {
            failures.add(aResult);
        }
    }

    /**
     * Adds a test, that was still running, when the test run ended, e.g. a test
     * that hangs, when the timeout ended the run.
     *
     * @param aClassName - name of the test class
     * @param aMethodName - name of the test method
     */
    public void addRunning(String aClassName, String aMethodName) {
        running.add(aClassName + "." + aMethodName); //$NON-NLS-1$
    }

    /**
     * Adds a warning of the tool, e.g. about the exit value of a test run, that
     * ended too early.
     *
     * @param aWarning - text of the warning
     */
    public void addWarning(String aWarning) {
        warnings.add(aWarning);
    }

    /**
     * Sets how the test run ended and how long it took.
     *
     * @param anEnd - end of the test run
     * @param aMillis - duration in milliseconds, from the launch to the end
     */
    public void setEnd(End anEnd, long aMillis) {
        end = anEnd;
        millis = aMillis;
    }

    /**
     * Returns whether all tests ran.
     *
     * @return <code>true</code>, if the test run has completed
     */
    public boolean isCompleted() {
        return end == End.COMPLETED;
    }

    /**
     * Returns the report as JSON object.
     *
     * @return the report with 'launch', 'result', 'counts', 'seconds',
     *         'truncated', 'failures' and, if there are any, 'running' and
     *         'warnings'
     */
    public JsonObject toJson() {

        JsonObject counts = new JsonObject();
        counts.addProperty("run", Integer.valueOf(runCount)); //$NON-NLS-1$
        counts.addProperty("failures", Integer.valueOf(failureCount)); //$NON-NLS-1$
        counts.addProperty("errors", Integer.valueOf(errorCount)); //$NON-NLS-1$
        counts.addProperty("ignored", Integer.valueOf(ignoredCount)); //$NON-NLS-1$

        JsonArray failureArray = new JsonArray();
        for (Result failure : failures.subList(0, Math.min(failures.size(), maxFailures))) {
            failureArray.add(failure.toJson());
        }

        JsonObject report = new JsonObject();
        report.addProperty("launch", launch); //$NON-NLS-1$
        report.addProperty("result", getResult()); //$NON-NLS-1$
        report.add("counts", counts); //$NON-NLS-1$
        report.addProperty("seconds", Double.valueOf(Math.round(millis / 100.0) / 10.0)); //$NON-NLS-1$
        report.addProperty("truncated", Boolean.valueOf(failures.size() > maxFailures)); //$NON-NLS-1$
        report.add("failures", failureArray); //$NON-NLS-1$
        addStrings(report, "running", running); //$NON-NLS-1$
        addStrings(report, "warnings", warnings); //$NON-NLS-1$

        return report;
    }

    /**
     * Returns the report as result of the tool. Failed tests are a result like
     * any other; a test run, that has not completed, e.g. because of the
     * timeout, is an error of the tool.
     *
     * @return result with the report as text, an error, if the test run has not
     *         completed
     */
    public McpToolResult toToolResult() {

        String text = JsonTexts.toText(toJson());
        if (!isCompleted()) {
            return McpToolResult.error(text);
        }

        return McpToolResult.success(text);
    }

    /**
     * Shortens a stack trace: removes the frames of the test runners, of JUnit
     * and of the calls by reflection, like the view JUnit of RDi, and cuts the
     * trace after {@link #MAX_TRACE_LINES} lines.
     *
     * @param aTrace - stack trace or <code>null</code>
     * @return shortened stack trace or <code>null</code>
     */
    public static String shortenTrace(String aTrace) {

        if (aTrace == null) {
            return null;
        }

        List<String> lines = new ArrayList<String>();
        for (String line : aTrace.split("\r?\n")) { //$NON-NLS-1$
            if (!isFilteredFrame(line)) {
                lines.add(line);
            }
        }

        StringBuilder trace = new StringBuilder();
        for (String line : lines.subList(0, Math.min(lines.size(), MAX_TRACE_LINES))) {
            if (trace.length() > 0) {
                trace.append('\n');
            }
            trace.append(line);
        }
        if (lines.size() > MAX_TRACE_LINES) {
            trace.append("\n\t... ").append(lines.size() - MAX_TRACE_LINES).append(" more lines cut"); //$NON-NLS-1$ //$NON-NLS-2$
        }

        return trace.toString();
    }

    /**
     * Shortens an expected or actual value to {@link #MAX_VALUE_LENGTH}
     * characters.
     *
     * @param aValue - value or <code>null</code>
     * @return shortened value or <code>null</code>
     */
    public static String shortenValue(String aValue) {

        if (aValue == null || aValue.length() <= MAX_VALUE_LENGTH) {
            return aValue;
        }

        return aValue.substring(0, MAX_VALUE_LENGTH) + " ... " + (aValue.length() - MAX_VALUE_LENGTH) + " more characters cut"; //$NON-NLS-1$ //$NON-NLS-2$
    }

    private String getResult() {

        if (end != End.COMPLETED) {
            return end.getLabel();
        }

        if (failureCount + errorCount > 0) {
            return "failed"; //$NON-NLS-1$
        }

        return "passed"; //$NON-NLS-1$
    }

    /**
     * Returns whether a line of a stack trace is a frame, that the view JUnit
     * filters, e.g. '\tat org.junit.Assert.fail(Assert.java:88)'. Frames of
     * Java 9 and later may name a module or class loader, e.g.
     * 'java.base/java.lang.reflect.Method.invoke(Method.java:568)'.
     */
    private static boolean isFilteredFrame(String aLine) {

        String line = aLine.trim();
        if (!line.startsWith(FRAME_PREFIX)) {
            return false;
        }

        String method = line.substring(FRAME_PREFIX.length()).trim();
        int parenthesis = method.indexOf('(');
        if (parenthesis >= 0) {
            method = method.substring(0, parenthesis);
        }
        method = method.substring(method.lastIndexOf('/') + 1);

        for (String filter : TRACE_FILTERS) {
            if (filter.endsWith("*")) { //$NON-NLS-1$
                if (method.startsWith(filter.substring(0, filter.length() - 1))) {
                    return true;
                }
            } else if (method.equals(filter) || method.startsWith(filter + ".")) { //$NON-NLS-1$
                return true;
            }
        }

        return false;
    }

    private static void addStrings(JsonObject aReport, String aName, List<String> aValues) {

        if (aValues.isEmpty()) {
            return;
        }

        JsonArray array = new JsonArray();
        for (String value : aValues) {
            array.add(new JsonPrimitive(value));
        }
        aReport.add(aName, array);
    }

    /**
     * Outcome of a test.
     */
    public enum Outcome {

        OK ("ok"), //$NON-NLS-1$
        FAILURE ("failure"), //$NON-NLS-1$
        ERROR ("error"), //$NON-NLS-1$
        IGNORED ("ignored"); //$NON-NLS-1$

        private final String label;

        private Outcome(String aLabel) {
            label = aLabel;
        }

        /**
         * Returns the name of the outcome in the result of the tool.
         *
         * @return 'ok', 'failure', 'error' or 'ignored'
         */
        public String getLabel() {
            return label;
        }
    }

    /**
     * End of a test run.
     */
    public enum End {

        /** All tests ran. */
        COMPLETED ("completed"), //$NON-NLS-1$

        /** The user stopped the test run in the view JUnit. */
        STOPPED ("stopped"), //$NON-NLS-1$

        /** The timeout ended the test run. */
        TIMEOUT ("timeout"), //$NON-NLS-1$

        /**
         * The launch ended, before all tests ran, e.g. because the test VM
         * could not start or a test called System.exit().
         */
        ABORTED ("aborted"); //$NON-NLS-1$

        private final String label;

        private End(String aLabel) {
            label = aLabel;
        }

        /**
         * Returns the name of the end in the result of the tool.
         *
         * @return 'completed', 'stopped', 'timeout' or 'aborted'
         */
        public String getLabel() {
            return label;
        }
    }

    /**
     * Result of a test or the failure of a test class.
     */
    public static final class Result {

        private final Outcome outcome;
        private final String className;
        private final String methodName;
        private final String trace;
        private final String expected;
        private final String actual;

        /**
         * Creates a result.
         *
         * @param anOutcome - outcome
         * @param aClassName - name of the test class
         * @param aMethodName - name of the test method or <code>null</code> for
         *        the failure of a test class
         * @param aTrace - stack trace of the failure or <code>null</code>
         * @param anExpected - expected value of a failed comparison or
         *        <code>null</code>
         * @param anActual - actual value of a failed comparison or
         *        <code>null</code>
         */
        public Result(Outcome anOutcome, String aClassName, String aMethodName, String aTrace, String anExpected, String anActual) {
            outcome = anOutcome;
            className = aClassName;
            methodName = aMethodName;
            trace = aTrace;
            expected = anExpected;
            actual = anActual;
        }

        private JsonObject toJson() {

            JsonObject json = new JsonObject();
            json.addProperty("result", outcome.getLabel()); //$NON-NLS-1$
            json.addProperty("class", className); //$NON-NLS-1$
            if (methodName != null) {
                json.addProperty("method", methodName); //$NON-NLS-1$
            }
            if (expected != null) {
                json.addProperty("expected", shortenValue(expected)); //$NON-NLS-1$
            }
            if (actual != null) {
                json.addProperty("actual", shortenValue(actual)); //$NON-NLS-1$
            }
            if (trace != null) {
                json.addProperty("trace", shortenTrace(trace)); //$NON-NLS-1$
            }

            return json;
        }
    }
}
