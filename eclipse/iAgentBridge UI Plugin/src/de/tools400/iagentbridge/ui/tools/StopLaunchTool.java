/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.tools;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.ILaunch;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import de.tools400.iagentbridge.mcp.IMcpTool;
import de.tools400.iagentbridge.mcp.McpToolResult;
import de.tools400.iagentbridge.ui.forwarding.Forwarding;
import de.tools400.iagentbridge.ui.forwarding.ForwardingClient;
import de.tools400.iagentbridge.ui.forwarding.ForwardingClient.Response;
import de.tools400.iagentbridge.ui.forwarding.TestInstance;
import de.tools400.iagentbridge.ui.launches.Identity;
import de.tools400.iagentbridge.ui.launches.LaunchStarter;
import de.tools400.iagentbridge.ui.launches.Launches;
import de.tools400.iagentbridge.ui.launches.RunningLaunch;
import de.tools400.iagentbridge.ui.launches.StopDecision;
import de.tools400.iagentbridge.ui.launches.StopDecision.Action;
import de.tools400.iagentbridge.ui.model.Deadline;
import de.tools400.iagentbridge.ui.model.Hints;

/**
 * Tool 'stop_launch': ends a launch. A test instance with the UI tools closes
 * its workbench itself (ui_close), unless it has unsaved changes; everything
 * else ends with ILaunch.terminate().
 */
public class StopLaunchTool implements IMcpTool {

    private static final String DESCRIPTION = "Ends a launch, that start_launch or the developer started. A test instance with the UI " //$NON-NLS-1$
        + "tools closes its workbench itself, so that it ends cleanly; if an editor or a view has unsaved changes, it rejects and names " //$NON-NLS-1$
        + "them, unless 'force' is true. Everything else - without the UI tools, stopped in the debugger, not answering, its port used " //$NON-NLS-1$
        + "by another process, or still running after the time - ends with terminate(). Without 'name' it ends the only test instance " //$NON-NLS-1$
        + "with the UI tools."; //$NON-NLS-1$

    private static final String INPUT_SCHEMA = "{\"type\":\"object\",\"properties\":{" //$NON-NLS-1$
        + "\"name\":{\"type\":\"string\",\"description\":\"Name of the launch configuration. Default: the only test instance with the UI tools.\"}," //$NON-NLS-1$
        + "\"force\":{\"type\":\"boolean\",\"description\":\"true: end it with terminate() at once, also with unsaved changes. Default: false.\"}," //$NON-NLS-1$
        + "\"timeoutSeconds\":{\"type\":\"integer\",\"description\":\"Time to wait for the end after closing, 1 to 600 seconds. Default: 60.\"}}}"; //$NON-NLS-1$

    private static final int DEFAULT_TIMEOUT_SECONDS = 60;
    private static final int TERMINATE_WAIT_SECONDS = 10;
    private static final int CLOSE_TIMEOUT_MILLIS = 20000;
    private static final long POLL_MILLIS = 300;

    @Override
    public String getName() {
        return "stop_launch"; //$NON-NLS-1$
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    public JsonObject getInputSchema() {
        return new JsonParser().parse(INPUT_SCHEMA).getAsJsonObject();
    }

    @Override
    public McpToolResult execute(JsonObject anArguments) throws Exception {

        if (AbstractUiTool.isTestInstance()) {
            return McpToolResult.error("stop_launch works in the RDi, in which the developer works, not in a test instance."); //$NON-NLS-1$
        }

        String name = UiArguments.getOptionalString(anArguments, "name"); //$NON-NLS-1$
        boolean force = UiArguments.getBoolean(anArguments, "force", false); //$NON-NLS-1$
        int timeoutSeconds = LaunchToolSupport.timeoutSeconds(anArguments, DEFAULT_TIMEOUT_SECONDS);

        ILaunch launch = select(name);
        if (launch == null) {
            return McpToolResult.error(notRunning(name));
        }

        String quoted = LaunchToolSupport.quote(launch.getLaunchConfiguration().getName());
        RunningLaunch running = Launches.describe(launch);
        TestInstance instance = Launches.instance(launch);
        Identity.Result identity = StopDecision.needsIdentity(running, force)
            ? Identity.check(instance, Launches.workspace(launch.getLaunchConfiguration())) : Identity.Result.UNKNOWN;

        long start = System.currentTimeMillis();
        StopDecision decision = StopDecision.before(running, force, identity);
        if (decision.getAction() == Action.CLOSE) {
            decision = close(instance);
        }

        switch (decision.getAction()) {
        case REJECT:
            return McpToolResult.error(decision.getReason());
        case WAIT:
            if (waitForEnd(launch, Deadline.inSeconds(timeoutSeconds))) {
                return McpToolResult.success("Closed " + quoted + "; it ended after " + LaunchToolSupport.secondsSince(start) + " s."); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            }
            return terminated(launch, quoted + " did not end within " + timeoutSeconds + " s after closing its workbench and was terminated."); //$NON-NLS-1$ //$NON-NLS-2$
        default:
            return terminated(launch, terminatedBecause(quoted, decision.getReason()));
        }
    }

    /**
     * Returns the text for a launch, that is terminated.
     *
     * @param aQuoted - the name of the launch configuration in quotes
     * @param aReason - the reason of the decision
     * @return text
     */
    static String terminatedBecause(String aQuoted, String aReason) {
        return Hints.sentence("Terminated " + aQuoted + ", because " + aReason); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /**
     * Terminates a launch and says, whether it ended.
     */
    private static McpToolResult terminated(ILaunch aLaunch, String aText) throws DebugException, InterruptedException {
        aLaunch.terminate();
        if (waitForEnd(aLaunch, Deadline.inSeconds(TERMINATE_WAIT_SECONDS))) {
            return McpToolResult.success(aText);
        }
        return McpToolResult.error(stillRuns(aText));
    }

    /**
     * Returns the text for a launch, whose process still runs after
     * terminate().
     *
     * @param aText - the text about the termination
     * @return text
     */
    static String stillRuns(String aText) {
        return aText + " Its process still runs " + TERMINATE_WAIT_SECONDS //$NON-NLS-1$
            + " s later; the view Debug of RDi shows it. Ask the developer to end it there; list_launches shows, whether it runs."; //$NON-NLS-1$
    }

    private static StopDecision close(TestInstance anInstance) {
        try {
            Response response = new ForwardingClient(ForwardingClient.CONNECT_TIMEOUT_MILLIS, CLOSE_TIMEOUT_MILLIS).send(anInstance,
                Forwarding.token(anInstance, System.getProperties()), "ui_close", new JsonObject()); //$NON-NLS-1$
            return StopDecision.afterClose(response);
        } catch (IOException e) {
            return StopDecision
                .afterClose(new Response(ForwardingClient.Kind.REJECTED, McpToolResult.error("the token could not be read: " + e.getMessage()))); //$NON-NLS-1$
        }
    }

    /**
     * Returns the launch of a name, or without a name the only test instance
     * with the UI tools.
     */
    private static ILaunch select(String aName) {

        List<ILaunch> candidates = new ArrayList<ILaunch>();
        for (ILaunch launch : Launches.running()) {
            if (aName == null ? Launches.instance(launch).hasUiTools() : aName.equals(launch.getLaunchConfiguration().getName())) {
                candidates.add(launch);
            }
        }
        if (aName == null && candidates.size() > 1) {
            StringBuilder names = new StringBuilder();
            for (ILaunch launch : candidates) {
                names.append(names.length() == 0 ? "" : ", ").append('"').append(launch.getLaunchConfiguration().getName()).append('"'); //$NON-NLS-1$ //$NON-NLS-2$
            }
            throw new IllegalArgumentException("Several test instances with the UI tools run: " + names + ". Give 'name'."); //$NON-NLS-1$ //$NON-NLS-2$
        }

        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private static String notRunning(String aName) {
        String waiting = LaunchStarter.getWaiting();
        if (waiting != null && (aName == null || aName.equals(waiting))) {
            return startWaiting(waiting);
        }
        return aName == null ? "No test instance with the UI tools runs." : "No launch \"" + aName + "\" runs. list_launches lists them."; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    /**
     * Returns the text for a start, that still waits.
     *
     * @param aWaiting - the name of the launch configuration, whose start waits
     * @return text
     */
    static String startWaiting(String aWaiting) {
        return "The start of \"" + aWaiting + "\" still waits for an answer in a dialog of RDi. " + Hints.askDeveloper("answer the dialog") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            + " Then repeat stop_launch."; //$NON-NLS-1$
    }

    private static boolean waitForEnd(ILaunch aLaunch, Deadline aDeadline) throws InterruptedException {
        while (!aLaunch.isTerminated() && !Launches.hasEnded(aLaunch)) {
            if (aDeadline.isExpired()) {
                return false;
            }
            Thread.sleep(POLL_MILLIS);
        }
        return true;
    }
}
