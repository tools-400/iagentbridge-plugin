/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

import org.junit.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import de.tools400.iagentbridge.mcp.IMcpTool;
import de.tools400.iagentbridge.mcp.McpToolResult;
import de.tools400.iagentbridge.ui.forwarding.TestInstance;
import de.tools400.iagentbridge.ui.model.ErrorLogParser;
import de.tools400.iagentbridge.ui.swt.UiThreadBusyException;

/**
 * Checks of single tools without RDi: their arguments and what they do without
 * a widget. The test lies in the package of the tools, because it calls their
 * package visible and protected methods.
 */
public class UiToolsTest {

    @Test
    public void testSelectEntryWithGreaterThan() {
        // '<none>' is the text of an entry of a combo, no path of a tree.
        assertNotNull(new UiSelectTool().prepare(arguments("{\"ref\":\"w1\",\"values\":[\"<none>\"]}"))); //$NON-NLS-1$
    }

    @Test
    public void testWaitForTheEndOfAnUnknownRef() throws Exception {

        McpToolResult result = new UiWaitTool().execute(arguments("{\"gone\":\"w99999\",\"timeoutSeconds\":1}"), null, Runnable::run); //$NON-NLS-1$

        assertTrue(result.getText(), result.isError());
        assertTrue(result.getText(), result.getText().startsWith("Ref w99999 is unknown")); //$NON-NLS-1$
    }

    @Test
    public void testLpexArguments() {

        assertNotNull("read", new UiLpexTool().prepare(arguments("{\"from\":1,\"to\":500}"))); //$NON-NLS-1$ //$NON-NLS-2$
        assertNotNull("write", new UiLpexTool().prepare(arguments("{\"line\":3,\"column\":5,\"text\":\"x\"}"))); //$NON-NLS-1$ //$NON-NLS-2$
        assertLpexError("{\"column\":3}", "Argument 'column' needs 'line'."); //$NON-NLS-1$ //$NON-NLS-2$
        assertLpexError("{\"from\":1,\"to\":501}", "At most 500 lines per call."); //$NON-NLS-1$ //$NON-NLS-2$
        assertLpexError("{\"from\":10,\"to\":9}", "Argument 'to' must be at least 10."); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static void assertLpexError(String anArguments, String aMessage) {
        try {
            new UiLpexTool().prepare(arguments(anArguments));
            fail("IllegalArgumentException expected for " + anArguments); //$NON-NLS-1$
        } catch (IllegalArgumentException e) {
            assertEquals(aMessage, e.getMessage());
        }
    }

    @Test
    public void testSpinnerValue() {

        assertEquals(42, UiTypeTool.spinnerValue(" 42 ", 0, 100));
        assertEquals(0, UiTypeTool.spinnerValue("0", 0, 100));
        assertEquals(100, UiTypeTool.spinnerValue("100", 0, 100));
        assertSpinnerError("101", "The value 101 is not in the range 0..100 of the spinner."); //$NON-NLS-1$ //$NON-NLS-2$
        assertSpinnerError("-1", "The value -1 is not in the range 0..100 of the spinner."); //$NON-NLS-1$ //$NON-NLS-2$
        assertSpinnerError("4x", "A spinner takes an integer, not '4x'."); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static void assertSpinnerError(String aText, String aMessage) {
        try {
            UiTypeTool.spinnerValue(aText, 0, 100);
            fail("IllegalArgumentException expected for " + aText); //$NON-NLS-1$
        } catch (IllegalArgumentException e) {
            assertEquals(aMessage, e.getMessage());
        }
    }

    @Test
    public void testErrorLog() throws Exception {

        File directory = Files.createTempDirectory("iab-log").toFile(); //$NON-NLS-1$
        try {
            File log = new File(directory, ".log"); //$NON-NLS-1$
            String start = "2026-11-26 12:00:00.000"; //$NON-NLS-1$
            write(log, "!SESSION " + start + " ----", "", "!ENTRY a.b 4 0 2026-11-26 12:00:01.000", "!MESSAGE failed", "", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
                "!ENTRY a.b 1 0 2026-11-26 12:00:02.000", "!MESSAGE an info", ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            long startTime = ErrorLogParser.parseTime(start);

            McpToolResult result = new UiErrorLogTool().execute(arguments("{}"), log, startTime); //$NON-NLS-1$
            assertFalse(result.getText(), result.isError());
            assertTrue(result.getText(),
                result.getText().startsWith("1 entry since the start of the test instance (2026-11-26 12:00:00.000), errors and warnings")); //$NON-NLS-1$
            assertFalse(result.getText(), result.getText().contains(".bak_")); //$NON-NLS-1$

            // A rotation in this session: the backup begins with the session of
            // this start.
            write(new File(directory, ".bak_0.log"), "!SESSION " + start + " ----", ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            result = new UiErrorLogTool().execute(arguments("{\"severity\":\"info\",\"maxEntries\":1}"), log, startTime); //$NON-NLS-1$
            assertTrue(result.getText(),
                result.getText()
                    .startsWith("2 entries since the start of the test instance (2026-11-26 12:00:00.000), all severities, the last 1 shown; " //$NON-NLS-1$
                        + "earlier entries of this session are in the files .bak_*.log beside the log, which this tool does not read:\n")); //$NON-NLS-1$

            result = new UiErrorLogTool().execute(arguments("{\"since\":\"2026-11-26T12:00:01\"}"), new File(directory, "other/missing.log"), //$NON-NLS-1$ //$NON-NLS-2$
                startTime);
            assertEquals("No entries since 2026-11-26 12:00:01.000, errors and warnings.\n", result.getText()); //$NON-NLS-1$
        } finally {
            for (File file : directory.listFiles()) {
                file.delete();
            }
            directory.delete();
        }
    }

    @Test
    public void testErrorLogArguments() {
        assertErrorLogError("{\"maxEntries\":0}", "Argument 'maxEntries' must be at least 1."); //$NON-NLS-1$ //$NON-NLS-2$
        assertErrorLogError("{\"maxEntries\":101}", "Argument 'maxEntries' must be at most 100."); //$NON-NLS-1$ //$NON-NLS-2$
        assertErrorLogError("{\"severity\":\"fatal\"}", "Argument 'severity' must be error, warning or info, not 'fatal'."); //$NON-NLS-1$ //$NON-NLS-2$
        assertErrorLogError("{\"since\":\"today\"}", "Argument 'since' must be 'session' or a time like 2026-11-26T08:00:00, not 'today'."); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static void assertErrorLogError(String anArguments, String aMessage) {
        try {
            new UiErrorLogTool().execute(arguments(anArguments), new File("missing.log"), 0); //$NON-NLS-1$
            fail("IllegalArgumentException expected for " + anArguments); //$NON-NLS-1$
        } catch (IllegalArgumentException e) {
            assertEquals(aMessage, e.getMessage());
        } catch (IOException e) {
            fail(e.toString());
        }
    }

    private static void write(File aFile, String... aLines) throws IOException {
        Files.write(aFile.toPath(), Arrays.asList(aLines), StandardCharsets.UTF_8);
    }

    @Test
    public void testErrorLogReportsEarlierFailures() throws Exception {
        AbstractUiTool.takePendingFailures();
        AbstractUiTool.getPendingFailures().add("ui_click w1", new RuntimeException("x")); //$NON-NLS-1$ //$NON-NLS-2$
        try {
            McpToolResult result = new UiErrorLogTool().execute(arguments("{}"), new File("missing.log"), 0); //$NON-NLS-1$ //$NON-NLS-2$
            assertTrue(result.getText(), result.getText().startsWith("The earlier action ui_click w1 failed")); //$NON-NLS-1$
        } finally {
            AbstractUiTool.takePendingFailures();
        }
    }

    @Test
    public void testBusyNamesTheDebugger() {
        assertEquals("UI thread busy for 10 s - a long operation, a thread stopped in the debugger or a native dialog of Windows " //$NON-NLS-1$
            + "(e.g. a file dialog) may block it. Try again later.", AbstractUiTool.busy(new UiThreadBusyException(10)).getText()); //$NON-NLS-1$
    }

    @Test
    public void testModalShells() {
        String snapshot = "Test instance \"iAgentBridge (de)\" (port 39196):\n" //$NON-NLS-1$
            + "display: activeShell=\"Kennwort eingeben\" focus=text [w1]\n" //$NON-NLS-1$
            + "shells: \"Statusinformationen\" [w2] modal, \"runtime - a, \\\"b\\\" [w9] modal - RDi\" [w3], " //$NON-NLS-1$
            + "\"Kennwort eingeben\" [w4] modal active\n" //$NON-NLS-1$
            + "shell \"Kennwort eingeben\" [w4] modal active\n"; //$NON-NLS-1$
        assertEquals("\"Statusinformationen\" [w2], \"Kennwort eingeben\" [w4]", StartLaunchTool.modalShells(snapshot)); //$NON-NLS-1$
        assertEquals(null, StartLaunchTool.modalShells("shells: \"runtime - RDi\" [w3] active\n")); //$NON-NLS-1$
        assertEquals(null, StartLaunchTool.modalShells("display: activeShell=none\n")); //$NON-NLS-1$

        assertEquals(" The test instance shows a modal dialog, which blocks the UI tools until it is closed (a sign-on is for the developer): " //$NON-NLS-1$
            + "\"Statusinformationen\" [w2], \"Kennwort eingeben\" [w4].", StartLaunchTool.modalDialogNote(snapshot)); //$NON-NLS-1$
        assertEquals("", StartLaunchTool.modalDialogNote("display: activeShell=none\n")); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testLaunchArguments() throws Exception {
        assertLaunchError(new StartLaunchTool(), "{}", "Argument 'name' is missing."); //$NON-NLS-1$ //$NON-NLS-2$
        assertLaunchError(new StartLaunchTool(), "{\"name\":\"x\",\"mode\":\"profile\"}", "Argument 'mode' must be one of: run, debug."); //$NON-NLS-1$ //$NON-NLS-2$
        assertLaunchError(new StartLaunchTool(), "{\"name\":\"x\",\"timeoutSeconds\":0}", "Argument 'timeoutSeconds' must be at least 1."); //$NON-NLS-1$ //$NON-NLS-2$
        assertLaunchError(new StartLaunchTool(), "{\"name\":\"x\",\"timeoutSeconds\":601}", "Argument 'timeoutSeconds' must be at most 600."); //$NON-NLS-1$ //$NON-NLS-2$
        assertLaunchError(new StopLaunchTool(), "{\"force\":\"yes\"}", "Argument 'force' must be true or false."); //$NON-NLS-1$ //$NON-NLS-2$
        assertLaunchError(new StopLaunchTool(), "{\"timeoutSeconds\":601}", "Argument 'timeoutSeconds' must be at most 600."); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static void assertLaunchError(IMcpTool aTool, String anArguments, String aMessage) throws Exception {
        try {
            aTool.execute(arguments(anArguments));
            fail("IllegalArgumentException expected for " + anArguments); //$NON-NLS-1$
        } catch (IllegalArgumentException e) {
            assertEquals(aMessage, e.getMessage());
        }
    }

    @Test
    public void testLaunchToolsInTestInstance() throws Exception {
        String old = System.setProperty(TestInstance.PROPERTY_UI_TOOLS, "true"); //$NON-NLS-1$
        try {
            for (IMcpTool tool : new IMcpTool[] { new StartLaunchTool(), new StopLaunchTool(), new ListLaunchesTool() }) {
                McpToolResult result = tool.execute(arguments("{\"name\":\"x\"}")); //$NON-NLS-1$
                assertTrue(result.isError());
                assertEquals(tool.getName() + " works in the RDi, in which the developer works, not in a test instance.", result.getText()); //$NON-NLS-1$
            }
        } finally {
            if (old == null) {
                System.clearProperty(TestInstance.PROPERTY_UI_TOOLS);
            } else {
                System.setProperty(TestInstance.PROPERTY_UI_TOOLS, old);
            }
        }
    }

    private static JsonObject arguments(String aJson) {
        return new JsonParser().parse(aJson).getAsJsonObject();
    }
}
