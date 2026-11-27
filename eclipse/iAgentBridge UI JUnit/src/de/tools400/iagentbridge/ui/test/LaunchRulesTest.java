/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import de.tools400.iagentbridge.mcp.McpToolResult;
import de.tools400.iagentbridge.ui.forwarding.ForwardingClient.Kind;
import de.tools400.iagentbridge.ui.forwarding.ForwardingClient.Response;
import de.tools400.iagentbridge.ui.forwarding.TestInstance;
import de.tools400.iagentbridge.ui.launches.CloseResult;
import de.tools400.iagentbridge.ui.launches.Identity;
import de.tools400.iagentbridge.ui.launches.LaunchInfo;
import de.tools400.iagentbridge.ui.launches.RunningLaunch;
import de.tools400.iagentbridge.ui.launches.StartChecks;
import de.tools400.iagentbridge.ui.launches.StopDecision;
import de.tools400.iagentbridge.ui.launches.StopDecision.Action;
import de.tools400.iagentbridge.ui.launches.WorkspaceLock;

/**
 * The decisions of start_launch and stop_launch, without Eclipse: which launch
 * may start, and how a launch ends.
 */
public class LaunchRulesTest {

    private static final String DE = "iAgentBridge (de)"; //$NON-NLS-1$
    private static final String EN = "iAgentBridge (en)"; //$NON-NLS-1$
    private static final String VM_DE = "-Diagentbridge.port=39196 -Diagentbridge.uiTools=true"; //$NON-NLS-1$

    @Test
    public void testTestInstance_ofCommandLine() {
        TestInstance withUiTools = TestInstance.ofCommandLine(DE, "java -Diagentbridge.port=39196 -Diagentbridge.uiTools=true Main"); //$NON-NLS-1$
        assertTrue(withUiTools.hasUiTools());
        assertEquals(Integer.valueOf(39196), withUiTools.getPort());
        TestInstance withoutUiTools = TestInstance.ofCommandLine("other", "java -Diagentbridge.port=39197 Main"); //$NON-NLS-1$ //$NON-NLS-2$
        assertFalse(withoutUiTools.hasUiTools());
        assertEquals(Integer.valueOf(39197), withoutUiTools.getPort());
        assertNull(TestInstance.fromCommandLine("other", "java -Diagentbridge.port=39197 Main")); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testStart_allowed() {
        assertNull(StartChecks.check(app(DE, VM_DE), none(), null, false));
        assertNull(StartChecks.check(new LaunchInfo("rt pde", LaunchInfo.TYPE_JUNIT_PLUGIN_TEST, "", "", null, false, false), none(), null, false)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    @Test
    public void testStart_type() {
        assertEquals(
            "\"build\" is a launch configuration of the type org.eclipse.jdt.launching.localJavaApplication. start_launch starts only " //$NON-NLS-1$
                + "Eclipse Applications and JUnit Plug-in Tests.", //$NON-NLS-1$
            StartChecks.check(new LaunchInfo("build", "org.eclipse.jdt.launching.localJavaApplication", "", "", null, false, false), none(), null, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                false));
    }

    @Test
    public void testStart_promptVariables() {
        String prompt = StartChecks.check(app(DE, VM_DE + " -Dx=${string_prompt:Value}"), none(), null, false); //$NON-NLS-1$
        assertTrue(prompt, prompt.contains("${string_prompt")); //$NON-NLS-1$
        assertTrue(prompt, prompt.endsWith(", which would open a dialog in this RDi. Ask the developer to start it by hand: the UI tools " //$NON-NLS-1$
            + "cannot reach the RDi, in which the developer works.")); //$NON-NLS-1$
        assertTrue(
            StartChecks.check(new LaunchInfo(DE, LaunchInfo.TYPE_ECLIPSE_APPLICATION, VM_DE, "-pw ${password_prompt}", null, false, false), none(), //$NON-NLS-1$
                null, false).contains("${password_prompt")); //$NON-NLS-1$
        assertNull(StartChecks.check(app(DE, VM_DE + " -Dhome=${workspace_loc}"), none(), null, false)); //$NON-NLS-1$
    }

    @Test
    public void testStart_clearWorkspace() {
        String clear = StartChecks.check(new LaunchInfo(DE, LaunchInfo.TYPE_ECLIPSE_APPLICATION, VM_DE, "", null, true, true), none(), null, false); //$NON-NLS-1$
        assertTrue(clear, clear.contains("asks, whether its workspace shall be cleared")); //$NON-NLS-1$
        assertTrue(clear, clear.endsWith(" Ask the developer to start it by hand or to turn off the question in the launch configuration: " //$NON-NLS-1$
            + "the UI tools cannot reach the RDi, in which the developer works.")); //$NON-NLS-1$
        assertNull(StartChecks.check(new LaunchInfo(DE, LaunchInfo.TYPE_ECLIPSE_APPLICATION, VM_DE, "", null, true, false), none(), null, false)); //$NON-NLS-1$
    }

    @Test
    public void testStart_running() {
        List<RunningLaunch> running = Arrays.asList(new RunningLaunch(DE, "run", Integer.valueOf(39196), true, 0, false, null)); //$NON-NLS-1$
        assertEquals("\"iAgentBridge (de)\" runs already. Stop it with stop_launch first.", StartChecks.check(app(DE, VM_DE), running, null, false)); //$NON-NLS-1$
        assertEquals(
            "The test instance \"iAgentBridge (de)\" with the UI tools runs already; the UI tools work with one only. Stop it with stop_launch first.", //$NON-NLS-1$
            StartChecks.check(app(EN, VM_DE), running, null, false));
    }

    @Test
    public void testStart_waitingAndPort() {
        assertEquals(
            "A start of \"iAgentBridge (de)\" is still waiting - probably for an answer in a dialog of RDi. Ask the developer to answer " //$NON-NLS-1$
                + "the dialog: the UI tools cannot reach the RDi, in which the developer works.", //$NON-NLS-1$
            StartChecks.check(app(EN, VM_DE), none(), DE, false));
        assertEquals(
            "Port 39196 of \"iAgentBridge (en)\" is in use by another process. list_launches shows, whether a launch uses it. Stop " //$NON-NLS-1$
                + "such a launch with stop_launch only, if you started it; otherwise ask the developer to end it or the other process.", //$NON-NLS-1$
            StartChecks.check(app(EN, VM_DE), none(), null, true));
    }

    @Test
    public void testStop_before() {
        RunningLaunch testInstance = running(39196, true, false);
        RunningLaunch withoutPort = new RunningLaunch("rt pde", "run", null, false, 0, false, null); //$NON-NLS-1$ //$NON-NLS-2$

        assertFalse(StopDecision.needsIdentity(withoutPort, false));
        assertFalse(StopDecision.needsIdentity(testInstance, true));
        assertFalse(StopDecision.needsIdentity(running(39196, true, true), false));
        assertTrue(StopDecision.needsIdentity(testInstance, false));

        assertEquals(Action.TERMINATE, StopDecision.before(withoutPort, false, Identity.Result.SAME).getAction());
        assertEquals(Action.TERMINATE, StopDecision.before(running(39196, false, false), false, Identity.Result.SAME).getAction());
        assertEquals(Action.TERMINATE, StopDecision.before(testInstance, true, Identity.Result.SAME).getAction());
        StopDecision suspended = StopDecision.before(running(39196, true, true), false, Identity.Result.SAME);
        assertEquals(Action.TERMINATE, suspended.getAction());
        assertTrue(suspended.getReason(), suspended.getReason().contains("stopped in the debugger")); //$NON-NLS-1$
        StopDecision other = StopDecision.before(testInstance, false, Identity.Result.OTHER);
        assertEquals(Action.TERMINATE, other.getAction());
        assertTrue(other.getReason(), other.getReason().contains("belongs to another process")); //$NON-NLS-1$
        assertEquals("its port belongs to another process, which keeps running. start_launch rejects a new start on this port, until that " //$NON-NLS-1$
            + "process ends; list_launches shows, whether a launch uses it. Stop such a launch with stop_launch only, if you started it; " //$NON-NLS-1$
            + "otherwise ask the developer to end it or the other process.", other.getReason()); //$NON-NLS-1$
        assertEquals(Action.CLOSE, StopDecision.before(testInstance, false, Identity.Result.SAME).getAction());
        StopDecision silent = StopDecision.before(testInstance, false, Identity.Result.NO_ANSWER);
        assertEquals(Action.TERMINATE, silent.getAction());
        assertTrue(silent.getReason(), silent.getReason().contains("does not answer")); //$NON-NLS-1$
        StopDecision unknown = StopDecision.before(testInstance, false, Identity.Result.UNKNOWN);
        assertEquals(Action.TERMINATE, unknown.getAction());
        assertTrue(unknown.getReason(), unknown.getReason().contains("could not be compared")); //$NON-NLS-1$
    }

    private static RunningLaunch running(int aPort, boolean aUiTools, boolean aSuspended) {
        return new RunningLaunch(DE, "debug", Integer.valueOf(aPort), aUiTools, 0, aSuspended, null); //$NON-NLS-1$
    }

    @Test
    public void testStop_afterClose() {
        assertEquals(Action.WAIT,
            StopDecision.afterClose(new Response(Kind.ANSWERED, McpToolResult.success("Closing the workbench of the test instance."))).getAction()); //$NON-NLS-1$
        assertEquals(Action.WAIT, StopDecision.afterClose(new Response(Kind.BROKEN, McpToolResult.error("failed"))).getAction()); //$NON-NLS-1$
        assertEquals(Action.REJECT, StopDecision
            .afterClose(new Response(Kind.ANSWERED,
                McpToolResult.error(
                    "Test instance \"t\" (port 1):\nUnsaved changes in the test instance: editor a. Save or revert them, or stop it with force."))) //$NON-NLS-1$
            .getAction());
        for (Kind kind : new Kind[] { Kind.REFUSED, Kind.TIMEOUT, Kind.REJECTED }) {
            assertEquals(kind.name(), Action.TERMINATE, StopDecision.afterClose(new Response(kind, McpToolResult.error("x"))).getAction()); //$NON-NLS-1$
        }
        StopDecision busy = StopDecision.afterClose(new Response(Kind.ANSWERED, McpToolResult.error("UI thread busy for 10 s"))); //$NON-NLS-1$
        assertEquals(Action.TERMINATE, busy.getAction());
        assertTrue(busy.getReason(), busy.getReason().contains("UI thread busy for 10 s")); //$NON-NLS-1$
    }

    @Test
    public void testDescribePort() {
        assertEquals("port 39196", app(DE, VM_DE).describePort()); //$NON-NLS-1$
        assertEquals("port only known after the start", //$NON-NLS-1$
            app(DE, "-Diagentbridge.port=${env_var:IAB_PORT} -Diagentbridge.uiTools=true").describePort()); //$NON-NLS-1$
        assertEquals("without a valid -Diagentbridge.port", app(DE, "-Diagentbridge.port=abc -Diagentbridge.uiTools=true").describePort()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("without a valid -Diagentbridge.port", app(DE, "-Diagentbridge.uiTools=true").describePort()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testStart_promptInLocation() {
        LaunchInfo info = new LaunchInfo(DE, LaunchInfo.TYPE_ECLIPSE_APPLICATION, VM_DE, "", "${workspace_loc}/../${folder_prompt}", false, false); //$NON-NLS-1$ //$NON-NLS-2$
        String rejected = StartChecks.check(info, none(), null, false);
        assertTrue(rejected, rejected.startsWith("\"iAgentBridge (de)\" asks for a value at the start (${folder_prompt})")); //$NON-NLS-1$
        assertTrue(StartChecks.hasPrompt("${string_prompt:Name}")); //$NON-NLS-1$
        assertFalse(StartChecks.hasPrompt("${workspace_loc}/../runtime-iagentbridge-de")); //$NON-NLS-1$
    }

    @Test
    public void testStart_lockedWorkspace() {
        LaunchInfo info = app(DE, VM_DE);
        assertEquals(
            "\"iAgentBridge (de)\" uses the workspace C:\\w, which another process has locked. list_launches shows, whether a " //$NON-NLS-1$
                + "launch uses it. Stop such a launch with stop_launch only, if you started it; otherwise ask the developer to end it or the " //$NON-NLS-1$
                + "other process.", //$NON-NLS-1$
            StartChecks.checkWorkspace(info, "C:\\w", true)); //$NON-NLS-1$
        assertNull(StartChecks.checkWorkspace(info, "C:\\w", false)); //$NON-NLS-1$
    }

    @Test
    public void testWorkspaceLock() throws IOException {
        File workspace = Files.createTempDirectory("iab-ws").toFile(); //$NON-NLS-1$
        File metadata = new File(workspace, ".metadata"); //$NON-NLS-1$
        File lockFile = new File(metadata, ".lock"); //$NON-NLS-1$
        try {
            assertFalse("no .metadata/.lock", WorkspaceLock.isLocked(workspace.getPath())); //$NON-NLS-1$
            metadata.mkdir();
            try (RandomAccessFile file = new RandomAccessFile(lockFile, "rw")) { //$NON-NLS-1$
                assertFalse("not locked", WorkspaceLock.isLocked(workspace.getPath())); //$NON-NLS-1$
                try (FileLock lock = file.getChannel().lock()) {
                    assertTrue("locked", WorkspaceLock.isLocked(workspace.getPath())); //$NON-NLS-1$
                }
                assertFalse("released", WorkspaceLock.isLocked(workspace.getPath())); //$NON-NLS-1$
            }
        } finally {
            lockFile.delete();
            metadata.delete();
            workspace.delete();
        }
    }

    @Test
    public void testStop_reasonInOwnWords() {
        StopDecision refused = StopDecision.afterClose(
            new Response(Kind.REFUSED, McpToolResult.error("The test instance \"t\" (port 1) does not accept connections yet - still starting?"))); //$NON-NLS-1$
        assertEquals(Action.TERMINATE, refused.getAction());
        assertFalse(refused.getReason(), refused.getReason().contains("still starting")); //$NON-NLS-1$
        StopDecision timeout = StopDecision.afterClose(new Response(Kind.TIMEOUT,
            McpToolResult.error("The test instance \"t\" (port 1) did " //$NON-NLS-1$
                + "not answer within 20 s - stopped in the debugger? If 1 is the port of this RDi, correct -Diagentbridge.port of the launch " //$NON-NLS-1$
                + "configuration."))); //$NON-NLS-1$
        assertFalse(timeout.getReason(), timeout.getReason().contains("-Diagentbridge.port")); //$NON-NLS-1$
        // The port belongs to the instance, stop_launch has checked it: the
        // hint of the forwarding about another program does not fit.
        StopDecision rejected = StopDecision.afterClose(new Response(Kind.REJECTED,
            McpToolResult.error("The test instance \"t\" (port 1) answered with HTTP 404. Check -Diagentbridge.port of its launch " //$NON-NLS-1$
                + "configuration: the port may belong to another program."), //$NON-NLS-1$
            "The test instance \"t\" (port 1) answered with HTTP 404")); //$NON-NLS-1$
        assertEquals(Action.TERMINATE, rejected.getAction());
        assertEquals("its server rejected ui_close: The test instance \"t\" (port 1) answered with HTTP 404", rejected.getReason()); //$NON-NLS-1$
    }

    @Test
    public void testCloseResult() {

        McpToolResult closing = CloseResult.of(new ArrayList<String>());
        assertFalse(closing.isError());
        assertEquals("Closing the workbench of the test instance.", closing.getText()); //$NON-NLS-1$
        assertFalse(CloseResult.isUnsaved(closing));

        McpToolResult unsaved = CloseResult.of(Arrays.asList("editor hello.rpgle", "view Properties")); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue(unsaved.isError());
        assertTrue(CloseResult.isUnsaved(unsaved));
        assertEquals("Unsaved changes in the test instance: editor hello.rpgle, view Properties. Save or revert them, or stop it with force.", //$NON-NLS-1$
            unsaved.getText());
        assertFalse(CloseResult.isUnsaved(McpToolResult.error("UI thread busy for 10 s"))); //$NON-NLS-1$
    }

    private static LaunchInfo app(String aName, String aVmArguments) {
        return new LaunchInfo(aName, LaunchInfo.TYPE_ECLIPSE_APPLICATION, aVmArguments, "", null, false, false); //$NON-NLS-1$
    }

    private static List<RunningLaunch> none() {
        return Collections.unmodifiableList(new ArrayList<RunningLaunch>());
    }
}
