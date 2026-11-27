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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import de.tools400.iagentbridge.mcp.IMcpTool;
import de.tools400.iagentbridge.mcp.McpToolResult;
import de.tools400.iagentbridge.ui.forwarding.TestInstance;
import de.tools400.iagentbridge.ui.launches.Identity;
import de.tools400.iagentbridge.ui.launches.RunningLaunch;
import de.tools400.iagentbridge.ui.launches.StopDecision;
import de.tools400.iagentbridge.ui.model.ErrorLogParser;
import de.tools400.iagentbridge.ui.swt.UiThreadBusyException;

/**
 * Checks of single tools without RDi: their arguments and what they do without
 * a widget. The test lies in the package of the tools, because it calls their
 * package visible and protected methods.
 */
public class UiToolsTest {

    /** Name of a launch configuration in quotes, as the texts show it */
    private static final String QUOTED = "\"iAgentBridge (de)\""; //$NON-NLS-1$

    @Test
    public void testScreenshotResult_withImage() {

        McpToolResult result = UiScreenshotTool.result("Screenshot of shell, saved in x.png", new byte[] { 1, 2, 3 }, true); //$NON-NLS-1$

        JsonArray content = result.toJson().getAsJsonArray("content"); //$NON-NLS-1$
        assertEquals(2, content.size());
        assertEquals("Screenshot of shell, saved in x.png", content.get(0).getAsJsonObject().get("text").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("image", content.get(1).getAsJsonObject().get("type").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testScreenshotResult_withoutImage() {

        McpToolResult result = UiScreenshotTool.result("Screenshot of shell, saved in x.png", new byte[] { 1, 2, 3 }, false); //$NON-NLS-1$

        JsonArray content = result.toJson().getAsJsonArray("content"); //$NON-NLS-1$
        assertEquals(1, content.size());
        assertEquals("Screenshot of shell, saved in x.png; the image is not returned (returnImage: false).", //$NON-NLS-1$
            content.get(0).getAsJsonObject().get("text").getAsString()); //$NON-NLS-1$
        assertFalse(result.isError());
    }

    @Test
    public void testScreenshotSchema_returnImage() {

        JsonObject returnImage = new UiScreenshotTool().getInputSchema().getAsJsonObject("properties").getAsJsonObject("returnImage"); //$NON-NLS-1$ //$NON-NLS-2$

        assertEquals("boolean", returnImage.get("type").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        String description = returnImage.get("description").getAsString(); //$NON-NLS-1$
        assertTrue(description, description.contains("newest " + ScreenshotFiles.KEEP)); //$NON-NLS-1$
        assertTrue(description, description.contains("Default: true")); //$NON-NLS-1$
    }

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

        assertEquals(
            " The test instance shows a modal dialog, which blocks its other windows: \"Statusinformationen\" [w2], " //$NON-NLS-1$
                + "\"Kennwort eingeben\" [w4]. Operate or close it with the UI tools; a sign-on is for the developer.", //$NON-NLS-1$
            StartLaunchTool.modalDialogNote(snapshot));
        assertEquals("", StartLaunchTool.modalDialogNote("display: activeShell=none\n")); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testStartLaunchTexts() {
        assertEquals("Port 39196 of \"iAgentBridge (de)\" is in use by another process, so the new instance has no server. It was stopped. " //$NON-NLS-1$
            + "list_launches shows, whether a launch uses it. Stop such a launch with stop_launch only, if you started it; otherwise ask the " //$NON-NLS-1$
            + "developer to end it or the other process.", StartLaunchTool.portTaken(QUOTED, Integer.valueOf(39196))); //$NON-NLS-1$
        assertEquals(
            "The process of \"iAgentBridge (de)\" ended with exit code 13, before port 39196 answered. The view Console of RDi " //$NON-NLS-1$
                + "shows its output. Read the file .metadata/.log of its workspace, or ask the developer for the output in the view Console.", //$NON-NLS-1$
            StartLaunchTool.processEnded(QUOTED, Integer.valueOf(13), ", before port 39196 answered")); //$NON-NLS-1$
        assertEquals("\"iAgentBridge (de)\" runs, but port 39196 did not answer within 180 s. Maybe its server is turned off in its " //$NON-NLS-1$
            + "workspace (iagentbridge.serverEnabled). The launch keeps running. Try a UI tool later; if the port stays silent, end it with " //$NON-NLS-1$
            + "stop_launch.", StartLaunchTool.portSilent(QUOTED, 39196, 180, true)); //$NON-NLS-1$
        // Without the UI tools, a UI tool does not reach the launch.
        assertEquals(
            "\"iAgentBridge (de)\" runs, but port 39196 did not answer within 180 s. Maybe its server is turned off in its " //$NON-NLS-1$
                + "workspace (iagentbridge.serverEnabled). The launch keeps running. If the port stays silent, end it with stop_launch.", //$NON-NLS-1$
            StartLaunchTool.portSilent(QUOTED, 39196, 180, false));
    }

    @Test
    public void testStopLaunchTexts() {
        assertEquals(
            "The start of \"iAgentBridge (de)\" still waits for an answer in a dialog of RDi. Ask the developer to answer the dialog: the UI " //$NON-NLS-1$
                + "tools cannot reach the RDi, in which the developer works. Then repeat stop_launch.", //$NON-NLS-1$
            StopLaunchTool.startWaiting("iAgentBridge (de)")); //$NON-NLS-1$
        assertEquals(
            "Terminated \"iAgentBridge (de)\", because 'force' is true. Its process still runs 10 s later; the view Debug of RDi " //$NON-NLS-1$
                + "shows it. Ask the developer to end it there; list_launches shows, whether it runs.", //$NON-NLS-1$
            StopLaunchTool.stillRuns("Terminated \"iAgentBridge (de)\", because 'force' is true.")); //$NON-NLS-1$
    }

    @Test
    public void testStopLaunchTexts_portOfAnotherProcess() {
        RunningLaunch launch = new RunningLaunch("iAgentBridge (de)", "run", Integer.valueOf(39196), true, 0, false, null); //$NON-NLS-1$ //$NON-NLS-2$
        String reason = StopDecision.before(launch, false, Identity.Result.OTHER).getReason();
        assertEquals(
            "Terminated \"iAgentBridge (de)\", because its port belongs to another process, which keeps running. start_launch " //$NON-NLS-1$
                + "rejects a new start on this port, until that process ends; list_launches shows, whether a launch uses it. Stop such a launch " //$NON-NLS-1$
                + "with stop_launch only, if you started it; otherwise ask the developer to end it or the other process.", //$NON-NLS-1$
            StopLaunchTool.terminatedBecause(QUOTED, reason));
    }

    @Test
    public void testNoVisibleShell() {
        assertEquals("No shell of the test instance is visible. It may be starting or ending: list_launches shows, whether the test " //$NON-NLS-1$
            + "instance runs; start_launch starts it again.", AbstractUiTool.NO_VISIBLE_SHELL); //$NON-NLS-1$
    }

    @Test
    public void testScreenshotTexts() {
        assertEquals("Ref w17 is not visible. Make it visible first, or take a screenshot of its shell.", UiScreenshotTool.notVisible("w17")); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testCommandTexts() {
        assertEquals("The test instance has no workbench window. It may be starting or ending: list_launches shows, whether the test " //$NON-NLS-1$
            + "instance runs; start_launch starts it again.", UiCommandTool.noWorkbenchWindow()); //$NON-NLS-1$
        assertEquals("The command 'org.eclipse.ui.file.save' has no active handler in the current state of the workbench. Activate the " //$NON-NLS-1$
            + "part, that handles it, e.g. with ui_click into it, then repeat.", UiCommandTool.noActiveHandler("org.eclipse.ui.file.save")); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testKeyTexts() {
        assertEquals("ESC did not close shell \"Preferences\" [w4]. If it is a dialog, close it with its Cancel or Close button (ui_click); " //$NON-NLS-1$
            + "ESC closes no workbench window.", UiKeyTool.escapeFailed("shell \"Preferences\" [w4]")); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testTypeTexts() {
        assertEquals("button \"OK\" [w20] is no field. ui_type works on text fields, editable combos, StyledText and spinners. Use " //$NON-NLS-1$
            + "ui_click for buttons, ui_select for lists, tables and trees.", UiTypeTool.noField("button \"OK\" [w20]")); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("combo [w8] is read-only; use ui_select.", UiTypeTool.readOnly("combo [w8]", true)); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("text [w9] is read-only. Its value is in the snapshot; ui_type works on editable fields only.", //$NON-NLS-1$
            UiTypeTool.readOnly("text [w9]", false)); //$NON-NLS-1$
    }

    @Test
    public void testSelectTexts() {
        assertEquals("button \"OK\" [w20] has no entries to select. ui_select works on combos, lists, tables and trees. Use ui_type for " //$NON-NLS-1$
            + "text fields, ui_click for buttons, tabs and links.", UiSelectTool.noEntries("button \"OK\" [w20]")); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testCheckTexts() {
        assertEquals(
            "button \"OK\" [w20] has no state to check. ui_check works on checkboxes, toggle and radio buttons and on rows and " //$NON-NLS-1$
                + "tree items of a table or tree with check boxes. Use ui_click for other buttons, ui_select with the reference of the table " //$NON-NLS-1$
                + "or tree for rows and items.", //$NON-NLS-1$
            UiCheckTool.noState("button \"OK\" [w20]")); //$NON-NLS-1$
    }

    @Test
    public void testLpexTexts() {
        assertEquals("No LPEX editor is visible in the active shell. If a dialog is the active shell, close it first or give 'ref' of the " //$NON-NLS-1$
            + "editor; otherwise open the source, e.g. with ui_click and 'double', or select its editor tab.", UiLpexTool.noEditor()); //$NON-NLS-1$
        assertEquals("The LPEX editor [w31] shows no document. Wait, until the source is loaded, e.g. with ui_wait, then repeat.", //$NON-NLS-1$
            UiLpexTool.noDocument("w31")); //$NON-NLS-1$
    }

    @Test
    public void testWaitTexts() {
        assertEquals("Timeout after 10.0 s waiting for the shell \"Preferences\". See the snapshot; ui_error_log may show, why nothing " //$NON-NLS-1$
            + "happened.", UiWaitTool.timedOut("10.0", "the shell \"Preferences\"", true)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        // The snapshot after the wait failed.
        assertEquals("Timeout after 10.0 s waiting for the shell \"Preferences\". ui_error_log may show, why nothing happened.", //$NON-NLS-1$
            UiWaitTool.timedOut("10.0", "the shell \"Preferences\"", false)); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /**
     * The result of a wait, built after the snapshot: the way through
     * UiWaitTool.execute() needs a display, because every check of a condition
     * reads it.
     */
    @Test
    public void testWaitResult() {

        String condition = "the shell \"Preferences\""; //$NON-NLS-1$
        String snapshot = "shell \"Preferences\" [w1] modal active"; //$NON-NLS-1$

        McpToolResult met = UiWaitTool.result("", true, "0.5", condition, snapshot, true); //$NON-NLS-1$ //$NON-NLS-2$
        assertFalse(met.isError());
        assertEquals("Waited 0.5 s for the shell \"Preferences\".\nshell \"Preferences\" [w1] modal active", met.getText()); //$NON-NLS-1$

        McpToolResult timeout = UiWaitTool.result("", false, "10.0", condition, snapshot, true); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue(timeout.isError());
        assertEquals("Timeout after 10.0 s waiting for the shell \"Preferences\". See the snapshot; ui_error_log may show, why nothing " //$NON-NLS-1$
            + "happened.\nshell \"Preferences\" [w1] modal active", timeout.getText()); //$NON-NLS-1$

        String busy = UiWaitTool.snapshotBusy(new UiThreadBusyException(20).getMessage());
        McpToolResult noSnapshot = UiWaitTool.result("The earlier action ui_click w17 failed: x.\n", false, "10.0", condition, busy, false); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue(noSnapshot.isError());
        assertEquals("The earlier action ui_click w17 failed: x.\nTimeout after 10.0 s waiting for the shell \"Preferences\". ui_error_log " //$NON-NLS-1$
            + "may show, why nothing happened.\nNo snapshot: UI thread busy for 20 s. Take one later with ui_snapshot.", noSnapshot.getText()); //$NON-NLS-1$
    }

    /**
     * The snapshot after a wait. Outside of RDi no condition is met without a
     * display: the job manager needs OSGi.
     */
    @Test
    public void testWaitSnapshotTexts() {
        assertEquals("No snapshot: UI thread busy for 20 s. Take one later with ui_snapshot.", //$NON-NLS-1$
            UiWaitTool.snapshotBusy(new UiThreadBusyException(20).getMessage()));
        assertEquals("No snapshot: java.lang.IllegalStateException: disposed. Take one with ui_snapshot.", //$NON-NLS-1$
            UiWaitTool.snapshotFailed(new IllegalStateException("disposed"))); //$NON-NLS-1$
        assertEquals("No snapshot: java.lang.IllegalStateException: Widget is disposed. Take one with ui_snapshot.", //$NON-NLS-1$
            AbstractUiTool.snapshotFailed(new IllegalStateException("Widget is disposed."))); //$NON-NLS-1$
    }

    @Test
    public void testHotCodeReplace_restartToTestAChange() {

        String earlierBuild = "may belong to an earlier build"; //$NON-NLS-1$
        String restart = "To test a change, restart the test instance with stop_launch and start_launch."; //$NON-NLS-1$

        String description = new StartLaunchTool().getDescription();
        assertTrue(description, description.contains(earlierBuild) && description.contains(restart));

        String note = StartLaunchTool.debugNote("debug"); //$NON-NLS-1$
        assertTrue(note, note.contains(earlierBuild) && note.contains(restart));
        // A dialog, that may come, not one, that is there.
        assertTrue(note, note.endsWith(" JDT may fail and show a dialog in this RDi, which only the developer can answer.")); //$NON-NLS-1$
        assertEquals("", StartLaunchTool.debugNote("run")); //$NON-NLS-1$ //$NON-NLS-2$

        String list = new ListLaunchesTool().getDescription();
        assertTrue(list, list.contains(earlierBuild) && list.contains("to test a change, restart")); //$NON-NLS-1$
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
