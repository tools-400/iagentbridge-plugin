/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import de.tools400.iagentbridge.mcp.McpToolResult;
import de.tools400.iagentbridge.tools.TestRunReport;
import de.tools400.iagentbridge.tools.TestRunReport.End;
import de.tools400.iagentbridge.tools.TestRunReport.Outcome;
import de.tools400.iagentbridge.tools.TestRunReport.Result;

public class TestRunReportTest {

    private static final String LAUNCH = "gp-java tests";

    private static final String TEST_CLASS = "gp.java.CalcTest";

    @Test
    public void testToJson_passed() {

        TestRunReport report = new TestRunReport(LAUNCH, 20);
        report.add(ok("testAdd"));
        report.add(ok("testSub"));
        report.setEnd(End.COMPLETED, 1234);

        assertEquals("{\"launch\":\"gp-java tests\",\"result\":\"passed\",\"counts\":{\"run\":2,\"failures\":0,\"errors\":0,\"ignored\":0},"
            + "\"seconds\":1.2,\"truncated\":false,\"failures\":[]}", report.toJson().toString());
    }

    @Test
    public void testToJson_failed() {

        TestRunReport report = new TestRunReport(LAUNCH, 20);
        report.add(ok("testAdd"));
        report.add(new Result(Outcome.FAILURE, TEST_CLASS, "testSub", "org.junit.ComparisonFailure: expected:<[1]> but was:<[2]>\n"
            + "\tat org.junit.Assert.assertEquals(Assert.java:115)\n\tat gp.java.CalcTest.testSub(CalcTest.java:20)\n", "1", "2"));
        report.add(new Result(Outcome.ERROR, TEST_CLASS, "testDiv", "java.lang.ArithmeticException: / by zero", null, null));
        report.add(new Result(Outcome.IGNORED, TEST_CLASS, "testMul", null, null, null));
        report.setEnd(End.COMPLETED, 50);

        JsonObject json = report.toJson();

        assertEquals("failed", json.get("result").getAsString());
        assertEquals("{\"run\":3,\"failures\":1,\"errors\":1,\"ignored\":1}", json.get("counts").toString());
        assertEquals(0.1, json.get("seconds").getAsDouble(), 0.0);
        assertEquals(
            "{\"result\":\"failure\",\"class\":\"gp.java.CalcTest\",\"method\":\"testSub\",\"expected\":\"1\",\"actual\":\"2\","
                + "\"trace\":\"org.junit.ComparisonFailure: expected:<[1]> but was:<[2]>\\n\\tat gp.java.CalcTest.testSub(CalcTest.java:20)\"}",
            failures(json).get(0).toString());
        assertEquals(
            "{\"result\":\"error\",\"class\":\"gp.java.CalcTest\",\"method\":\"testDiv\",\"trace\":\"java.lang.ArithmeticException: / by zero\"}",
            failures(json).get(1).toString());
        assertEquals(2, failures(json).size());
    }

    @Test
    public void testToJson_failureOfTestClass() {

        TestRunReport report = new TestRunReport(LAUNCH, 20);
        report.add(new Result(Outcome.ERROR, TEST_CLASS, null, "java.lang.IllegalStateException: setUpClass", null, null));
        report.setEnd(End.COMPLETED, 0);

        JsonObject json = report.toJson();

        assertEquals("failed", json.get("result").getAsString());
        assertEquals("{\"run\":0,\"failures\":0,\"errors\":1,\"ignored\":0}", json.get("counts").toString());
        assertFalse(failures(json).get(0).getAsJsonObject().has("method"));
        assertEquals(TEST_CLASS, failures(json).get(0).getAsJsonObject().get("class").getAsString());
    }

    @Test
    public void testToJson_truncated() {

        TestRunReport report = new TestRunReport(LAUNCH, 2);
        report.add(failure("test1"));
        report.add(failure("test2"));
        report.add(failure("test3"));
        report.setEnd(End.COMPLETED, 0);

        JsonObject json = report.toJson();

        assertTrue(json.get("truncated").getAsBoolean());
        assertEquals(3, json.get("counts").getAsJsonObject().get("failures").getAsInt());
        assertEquals(2, failures(json).size());
        assertEquals("test1", failures(json).get(0).getAsJsonObject().get("method").getAsString());
        assertEquals("test2", failures(json).get(1).getAsJsonObject().get("method").getAsString());
    }

    @Test
    public void testToJson_notTruncatedAtTheLimit() {

        TestRunReport report = new TestRunReport(LAUNCH, 2);
        report.add(failure("test1"));
        report.add(failure("test2"));
        report.setEnd(End.COMPLETED, 0);

        assertFalse(report.toJson().get("truncated").getAsBoolean());
    }

    @Test
    public void testToJson_timeout() {

        TestRunReport report = new TestRunReport(LAUNCH, 20);
        report.add(ok("testAdd"));
        report.addRunning("gp.java.HangTest", "testHang");
        report.setEnd(End.TIMEOUT, 10049);

        JsonObject json = report.toJson();

        assertEquals("timeout", json.get("result").getAsString());
        assertEquals(10.0, json.get("seconds").getAsDouble(), 0.0);
        assertEquals("[\"gp.java.HangTest.testHang\"]", json.get("running").toString());
    }

    @Test
    public void testToJson_endsOtherThanCompleted() {

        assertEquals("stopped", resultOf(End.STOPPED));
        assertEquals("aborted", resultOf(End.ABORTED));
        assertEquals("aborted", new TestRunReport(LAUNCH, 20).toJson().get("result").getAsString());
    }

    @Test
    public void testToJson_warnings() {

        TestRunReport report = new TestRunReport(LAUNCH, 20);
        assertNull(report.toJson().get("warnings"));
        assertNull(report.toJson().get("running"));

        report.addWarning("The launch ended, before all tests ran, exit value 1.");

        assertEquals("[\"The launch ended, before all tests ran, exit value 1.\"]", report.toJson().get("warnings").toString());
    }

    @Test
    public void testToToolResult_completed() {

        TestRunReport report = new TestRunReport(LAUNCH, 20);
        report.add(failure("test1"));
        report.setEnd(End.COMPLETED, 0);

        McpToolResult result = report.toToolResult();

        assertFalse(result.isError());
        assertTrue(report.isCompleted());
        assertEquals(report.toJson(), new JsonParser().parse(result.getText()));
    }

    @Test
    public void testToToolResult_notCompleted() {

        for (End end : new End[] { End.STOPPED, End.TIMEOUT, End.ABORTED }) {
            TestRunReport report = new TestRunReport(LAUNCH, 20);
            report.setEnd(end, 0);

            McpToolResult result = report.toToolResult();

            assertTrue(end.toString(), result.isError());
            assertFalse(report.isCompleted());
            assertEquals(report.toJson(), new JsonParser().parse(result.getText()));
        }
    }

    @Test
    public void testShortenTrace_filtersFramesOfJUnit() {

        String trace = "java.lang.AssertionError: expected:<1> but was:<2>\r\n" //
            + "\tat org.junit.Assert.fail(Assert.java:88)\r\n" //
            + "\tat org.junit.Assert.assertEquals(Assert.java:645)\r\n" //
            + "\tat gp.java.CalcTest.testSub(CalcTest.java:20)\r\n" //
            + "\tat sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)\r\n" //
            + "\tat java.lang.reflect.Method.invoke(Method.java:508)\r\n" //
            + "\tat junit.framework.TestCase.runTest(TestCase.java:176)\r\n" //
            + "\tat junit.framework.TestResult$1.protect(TestResult.java:122)\r\n" //
            + "\tat junit.framework.TestResultPrinter.print(TestResultPrinter.java:1)\r\n" //
            + "\tat org.eclipse.jdt.internal.junit4.runner.JUnit4TestReference.run(JUnit4TestReference.java:86)\r\n" //
            + "\tat org.eclipse.jdt.internal.junit.runner.RemoteTestRunner.main(RemoteTestRunner.java:192)\r\n";

        assertEquals("java.lang.AssertionError: expected:<1> but was:<2>\n" //
            + "\tat gp.java.CalcTest.testSub(CalcTest.java:20)\n" //
            + "\tat junit.framework.TestResultPrinter.print(TestResultPrinter.java:1)", TestRunReport.shortenTrace(trace));
    }

    @Test
    public void testShortenTrace_framesWithModule() {

        String trace = "java.lang.AssertionError\n" //
            + "\tat app//org.junit.Assert.fail(Assert.java:87)\n" //
            + "\tat app//gp.java.CalcTest.testSub(CalcTest.java:20)\n" //
            + "\tat java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:103)\n" //
            + "\tat java.base/java.lang.reflect.Method.invoke(Method.java:580)\n" //
            + "\tat java.base/java.lang.reflect.Method.getName(Method.java:1)";

        assertEquals("java.lang.AssertionError\n" //
            + "\tat app//gp.java.CalcTest.testSub(CalcTest.java:20)\n" //
            + "\tat java.base/java.lang.reflect.Method.getName(Method.java:1)", TestRunReport.shortenTrace(trace));
    }

    @Test
    public void testShortenTrace_keepsCausesAndMore() {

        String trace = "java.lang.RuntimeException: wrapped\n" //
            + "\tat gp.java.A.run(A.java:5)\n" //
            + "Caused by: java.io.IOException: disk full\n" //
            + "\tat gp.java.B.write(B.java:9)\n" //
            + "\tat org.junit.internal.runners.statements.InvokeMethod.evaluate(InvokeMethod.java:17)\n" //
            + "\t... 23 more";

        assertEquals("java.lang.RuntimeException: wrapped\n" //
            + "\tat gp.java.A.run(A.java:5)\n" //
            + "Caused by: java.io.IOException: disk full\n" //
            + "\tat gp.java.B.write(B.java:9)\n" //
            + "\t... 23 more", TestRunReport.shortenTrace(trace));
    }

    @Test
    public void testShortenTrace_cut() {

        StringBuilder trace = new StringBuilder("java.lang.StackOverflowError");
        for (int i = 1; i <= 40; i++) {
            trace.append("\n\tat gp.java.Rec.call(Rec.java:").append(i).append(')');
        }

        String[] lines = TestRunReport.shortenTrace(trace.toString()).split("\n");

        assertEquals(TestRunReport.MAX_TRACE_LINES + 1, lines.length);
        assertEquals("java.lang.StackOverflowError", lines[0]);
        assertEquals("\tat gp.java.Rec.call(Rec.java:29)", lines[TestRunReport.MAX_TRACE_LINES - 1]);
        assertEquals("\t... 11 more lines cut", lines[TestRunReport.MAX_TRACE_LINES]);
    }

    @Test
    public void testShortenTrace_atTheLimit() {

        StringBuilder trace = new StringBuilder("java.lang.StackOverflowError");
        for (int i = 1; i < TestRunReport.MAX_TRACE_LINES; i++) {
            trace.append("\n\tat gp.java.Rec.call(Rec.java:").append(i).append(')');
        }

        assertEquals(trace.toString(), TestRunReport.shortenTrace(trace.toString()));
    }

    @Test
    public void testShortenTrace_null() {
        assertNull(TestRunReport.shortenTrace(null));
    }

    @Test
    public void testShortenValue() {

        assertNull(TestRunReport.shortenValue(null));
        assertEquals("abc", TestRunReport.shortenValue("abc"));

        String atTheLimit = repeat('x', TestRunReport.MAX_VALUE_LENGTH);
        assertEquals(atTheLimit, TestRunReport.shortenValue(atTheLimit));

        assertEquals(atTheLimit + " ... 5 more characters cut", TestRunReport.shortenValue(atTheLimit + "yyyyy"));
    }

    private static String resultOf(End anEnd) {

        TestRunReport report = new TestRunReport(LAUNCH, 20);
        report.setEnd(anEnd, 0);

        return report.toJson().get("result").getAsString();
    }

    private static Result ok(String aMethodName) {
        return new Result(Outcome.OK, TEST_CLASS, aMethodName, null, null, null);
    }

    private static Result failure(String aMethodName) {
        return new Result(Outcome.FAILURE, TEST_CLASS, aMethodName, "java.lang.AssertionError", null, null);
    }

    private static JsonArray failures(JsonObject aReport) {
        return aReport.get("failures").getAsJsonArray();
    }

    private static String repeat(char aCharacter, int aCount) {

        StringBuilder text = new StringBuilder();
        for (int i = 0; i < aCount; i++) {
            text.append(aCharacter);
        }

        return text.toString();
    }
}
