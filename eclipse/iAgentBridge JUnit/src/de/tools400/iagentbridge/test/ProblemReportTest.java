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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import de.tools400.iagentbridge.mcp.McpToolResult;
import de.tools400.iagentbridge.tools.ProblemReport;
import de.tools400.iagentbridge.tools.ProblemReport.Problem;
import de.tools400.iagentbridge.tools.ProblemReport.Severity;

public class ProblemReportTest {

    private static final String JAVA_PROBLEM = "org.eclipse.jdt.core.problem";

    private static final String PDE_PROBLEM = "org.eclipse.pde.core.problem";

    @Test
    public void testToJson_empty() {

        JsonObject json = new ProblemReport("incremental", Severity.WARNING, 200).toJson();

        assertEquals("{\"build\":\"incremental\",\"counts\":{\"error\":0,\"warning\":0},\"truncated\":false,\"problems\":[]}", json.toString());
    }

    @Test
    public void testToJson_problem() {

        ProblemReport report = new ProblemReport("full", Severity.WARNING, 200);
        report.add(new Problem(Severity.ERROR, "C:/ws/p/src/a/B.java", "p", 42, "The method foo() is undefined for the type B", JAVA_PROBLEM,
            Integer.valueOf(67108964)));

        assertEquals(
            "{\"severity\":\"error\",\"file\":\"C:/ws/p/src/a/B.java\",\"project\":\"p\",\"line\":42,"
                + "\"message\":\"The method foo() is undefined for the type B\",\"type\":\"org.eclipse.jdt.core.problem\",\"id\":67108964}",
            problems(report.toJson()).get(0).toString());
    }

    @Test
    public void testToJson_problemWithoutLineAndId() {

        ProblemReport report = new ProblemReport("none", Severity.WARNING, 200);
        report.add(new Problem(Severity.ERROR, "C:/ws/p", "p", -1, "The project cannot be built", PDE_PROBLEM, null));

        JsonObject problem = problems(report.toJson()).get(0).getAsJsonObject();

        assertFalse(problem.has("line"));
        assertFalse(problem.has("id"));
        assertEquals("C:/ws/p", problem.get("file").getAsString());
    }

    @Test
    public void testToJson_negativeId() {

        ProblemReport report = new ProblemReport("none", Severity.WARNING, 200);
        report.add(new Problem(Severity.WARNING, "C:/ws/p/A.java", "p", 3, "Javadoc: Missing tag", JAVA_PROBLEM, Integer.valueOf(-1610612250)));

        assertEquals(-1610612250, problems(report.toJson()).get(0).getAsJsonObject().get("id").getAsInt());
    }

    @Test
    public void testToJson_sortedBySeverityFileAndLine() {

        ProblemReport report = new ProblemReport("incremental", Severity.INFO, 200);
        report.add(problem(Severity.INFO, "C:/ws/p/A.java", 1));
        report.add(problem(Severity.WARNING, "C:/ws/p/B.java", 7));
        report.add(problem(Severity.ERROR, "C:/ws/p/B.java", 20));
        report.add(problem(Severity.ERROR, "C:/ws/p/B.java", 3));
        report.add(problem(Severity.ERROR, "C:/ws/p/A.java", 50));
        report.add(problem(Severity.ERROR, "C:/ws/p", -1));

        JsonArray problems = problems(report.toJson());

        assertProblem(problems, 0, "error", "C:/ws/p", -1);
        assertProblem(problems, 1, "error", "C:/ws/p/A.java", 50);
        assertProblem(problems, 2, "error", "C:/ws/p/B.java", 3);
        assertProblem(problems, 3, "error", "C:/ws/p/B.java", 20);
        assertProblem(problems, 4, "warning", "C:/ws/p/B.java", 7);
        assertProblem(problems, 5, "info", "C:/ws/p/A.java", 1);
    }

    @Test
    public void testToJson_minSeverityError() {

        ProblemReport report = new ProblemReport("incremental", Severity.ERROR, 200);
        report.add(problem(Severity.WARNING, "C:/ws/p/A.java", 1));
        report.add(problem(Severity.ERROR, "C:/ws/p/A.java", 2));
        report.add(problem(Severity.INFO, "C:/ws/p/A.java", 3));

        JsonObject json = report.toJson();

        assertEquals("{\"error\":1}", json.get("counts").toString());
        assertEquals(1, problems(json).size());
        assertProblem(problems(json), 0, "error", "C:/ws/p/A.java", 2);
    }

    @Test
    public void testToJson_minSeverityInfo() {

        ProblemReport report = new ProblemReport("incremental", Severity.INFO, 200);
        report.add(problem(Severity.WARNING, "C:/ws/p/A.java", 1));
        report.add(problem(Severity.INFO, "C:/ws/p/A.java", 2));
        report.add(problem(Severity.INFO, "C:/ws/p/A.java", 3));

        assertEquals("{\"error\":0,\"warning\":1,\"info\":2}", report.toJson().get("counts").toString());
    }

    @Test
    public void testToJson_truncated() {

        ProblemReport report = new ProblemReport("incremental", Severity.WARNING, 2);
        report.add(problem(Severity.WARNING, "C:/ws/p/A.java", 1));
        report.add(problem(Severity.ERROR, "C:/ws/p/A.java", 9));
        report.add(problem(Severity.ERROR, "C:/ws/p/A.java", 5));

        JsonObject json = report.toJson();

        assertTrue(json.get("truncated").getAsBoolean());
        assertEquals("{\"error\":2,\"warning\":1}", json.get("counts").toString());
        assertEquals(2, problems(json).size());
        assertProblem(problems(json), 0, "error", "C:/ws/p/A.java", 5);
        assertProblem(problems(json), 1, "error", "C:/ws/p/A.java", 9);
    }

    @Test
    public void testToJson_notTruncatedAtTheLimit() {

        ProblemReport report = new ProblemReport("incremental", Severity.WARNING, 2);
        report.add(problem(Severity.WARNING, "C:/ws/p/A.java", 1));
        report.add(problem(Severity.ERROR, "C:/ws/p/A.java", 9));

        JsonObject json = report.toJson();

        assertFalse(json.get("truncated").getAsBoolean());
        assertEquals(2, problems(json).size());
    }

    @Test
    public void testToJson_warnings() {

        ProblemReport report = new ProblemReport("incremental", Severity.WARNING, 200);
        assertNull(report.toJson().get("warnings"));

        report.addWarning("C:/ws/p/src/a/C.java has unsaved changes in an RDi editor.");

        assertEquals("[\"C:/ws/p/src/a/C.java has unsaved changes in an RDi editor.\"]", report.toJson().get("warnings").toString());
    }

    @Test
    public void testToToolResult() {

        ProblemReport report = new ProblemReport("incremental", Severity.WARNING, 200);
        report.add(problem(Severity.ERROR, "C:/ws/p/A.java", 1));

        McpToolResult result = report.toToolResult();

        assertFalse(result.isError());
        assertEquals(report.toJson(), new JsonParser().parse(result.getText()));
    }

    @Test
    public void testSeverity_fromLabel() {

        assertSame(Severity.ERROR, Severity.fromLabel("error"));
        assertSame(Severity.WARNING, Severity.fromLabel("warning"));
        assertSame(Severity.INFO, Severity.fromLabel("info"));

        try {
            Severity.fromLabel("fatal");
            fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException e) {
            assertEquals("Unknown severity: fatal", e.getMessage());
        }
    }

    private static Problem problem(Severity aSeverity, String aFile, int aLine) {
        return new Problem(aSeverity, aFile, "p", aLine, "Message", JAVA_PROBLEM, null);
    }

    private static JsonArray problems(JsonObject aReport) {
        return aReport.get("problems").getAsJsonArray();
    }

    private static void assertProblem(JsonArray aProblems, int anIndex, String aSeverity, String aFile, int aLine) {

        JsonObject problem = aProblems.get(anIndex).getAsJsonObject();

        assertEquals(aSeverity, problem.get("severity").getAsString());
        assertEquals(aFile, problem.get("file").getAsString());
        assertEquals(aLine, problem.has("line") ? problem.get("line").getAsInt() : -1);
    }
}
