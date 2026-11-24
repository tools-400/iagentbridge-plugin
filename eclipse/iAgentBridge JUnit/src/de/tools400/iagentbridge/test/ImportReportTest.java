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
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import de.tools400.iagentbridge.mcp.McpToolResult;
import de.tools400.iagentbridge.tools.ImportReport;

public class ImportReportTest {

    @Test
    public void testToJson_empty() {

        ImportReport report = new ImportReport();

        assertEquals("{\"counts\":{\"imported\":0,\"existing\":0,\"failed\":0},\"projects\":[]}", report.toJson().toString());
        assertTrue(report.isCompleted());
    }

    @Test
    public void testToJson_projectsInOrderOfImport() {

        ImportReport report = new ImportReport();
        report.addImported("rt-lib", "C:/git/tests/rt-lib");
        report.addExisting("rt-java", "C:/git/tests/rt-java", null);
        report.addExisting("rt-old", "C:/git/tests/rt-old", "The project is closed in RDi.");

        assertEquals(
            "{\"counts\":{\"imported\":1,\"existing\":2,\"failed\":0},\"projects\":["
                + "{\"result\":\"imported\",\"project\":\"rt-lib\",\"location\":\"C:/git/tests/rt-lib\"},"
                + "{\"result\":\"existing\",\"project\":\"rt-java\",\"location\":\"C:/git/tests/rt-java\"},"
                + "{\"result\":\"existing\",\"project\":\"rt-old\",\"location\":\"C:/git/tests/rt-old\",\"message\":\"The project is closed in RDi.\"}]}",
            report.toJson().toString());
        assertTrue(report.isCompleted());
    }

    @Test
    public void testToJson_failedWithoutProjectName() {

        ImportReport report = new ImportReport();
        report.addFailed(null, "C:/git/empty", "No project found.");

        assertEquals("{\"result\":\"failed\",\"location\":\"C:/git/empty\",\"message\":\"No project found.\"}",
            report.toJson().getAsJsonArray("projects").get(0).toString());
        assertFalse(report.isCompleted());
    }

    @Test
    public void testToToolResult_completed() {

        ImportReport report = new ImportReport();
        report.addImported("p", "C:/git/p");

        McpToolResult result = report.toToolResult();

        assertFalse(result.isError());
        assertEquals(report.toJson(), parse(result));
    }

    @Test
    public void testToToolResult_failedIsError() {

        ImportReport report = new ImportReport();
        report.addImported("p", "C:/git/p");
        report.addFailed("q", "C:/git/q", "A project of that name is already in the RDi workspace at C:/ws/q.");

        McpToolResult result = report.toToolResult();

        assertTrue(result.isError());
        JsonObject json = parse(result);
        assertEquals(1, json.getAsJsonObject("counts").get("imported").getAsInt());
        assertEquals(1, json.getAsJsonObject("counts").get("failed").getAsInt());
    }

    @Test
    public void testToToolResult_canceledIsError() {

        ImportReport report = new ImportReport();
        report.addImported("p", "C:/git/p");
        report.setCanceled();

        McpToolResult result = report.toToolResult();

        assertFalse(report.isCompleted());
        assertTrue(result.isError());
        assertTrue(parse(result).get("cancelled").getAsBoolean());
    }

    @Test
    public void testToToolResult_errorKeepsProjects() {

        ImportReport report = new ImportReport();
        report.addImported("p", "C:/git/p");
        report.setError("The import failed: java.lang.NullPointerException");

        McpToolResult result = report.toToolResult();

        assertFalse(report.isCompleted());
        assertTrue(result.isError());
        JsonObject json = parse(result);
        assertEquals("The import failed: java.lang.NullPointerException", json.get("error").getAsString());
        assertEquals("p", json.getAsJsonArray("projects").get(0).getAsJsonObject().get("project").getAsString());
    }

    private static JsonObject parse(McpToolResult aResult) {
        return new JsonParser().parse(aResult.getText()).getAsJsonObject();
    }
}
