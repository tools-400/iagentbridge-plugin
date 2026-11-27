/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.tools;

import java.util.Collections;
import java.util.List;

import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;

import com.google.gson.JsonObject;

import de.tools400.iagentbridge.mcp.IMcpTool;
import de.tools400.iagentbridge.mcp.McpToolResult;
import de.tools400.iagentbridge.ui.forwarding.Forwarding;
import de.tools400.iagentbridge.ui.forwarding.ForwardingClient;
import de.tools400.iagentbridge.ui.forwarding.TestInstance;
import de.tools400.iagentbridge.ui.forwarding.TestInstanceFinder;
import de.tools400.iagentbridge.ui.model.Hints;
import de.tools400.iagentbridge.ui.model.PendingFailures;
import de.tools400.iagentbridge.ui.preferences.UiPreferences;
import de.tools400.iagentbridge.ui.swt.UiThreadBusyException;

/**
 * A UI tool: it works only in a test instance of RDi, that was started with
 * '-Diagentbridge.uiTools=true'. In any other RDi, e.g. the one, in which the
 * developer works, it never touches the user interface, but forwards the call
 * to the running test instance.
 */
public abstract class AbstractUiTool implements IMcpTool {

    /** Time, that the UI thread has to run a request */
    protected static final int UI_TIMEOUT_SECONDS = 10;

    /**
     * Text, when the display ended, e.g. after an action closed the last dialog
     * of a closing workbench
     */
    protected static final String WORKBENCH_ENDED = "The workbench of the test instance has ended. " + Hints.INSTANCE_RUNS; //$NON-NLS-1$

    /** Text, when the test instance shows no shell */
    protected static final String NO_VISIBLE_SHELL = "No shell of the test instance is visible. It may be starting or ending: " //$NON-NLS-1$
        + Hints.INSTANCE_RUNS;

    private static final PendingFailures PENDING_FAILURES = new PendingFailures();

    /** End of the description of every UI tool */
    protected static final String DESCRIPTION_SUFFIX = " Works on a test instance of RDi, that the developer started from the workspace " //$NON-NLS-1$
        + "with -Diagentbridge.uiTools=true; the RDi, in which the developer works, forwards the call to it. It never reads or changes " //$NON-NLS-1$
        + "the user interface of the RDi, in which the developer works. Password fields have no value. Native dialogs of Windows, e.g. " //$NON-NLS-1$
        + "file dialogs, are no widgets and cannot be read."; //$NON-NLS-1$

    @Override
    public final McpToolResult execute(JsonObject anArguments) throws Exception {

        if (isTestInstance()) {
            return executeInTestInstance(anArguments);
        }

        if (anArguments.has(ForwardingClient.ARGUMENT_FORWARDED)) {
            return McpToolResult.error("This RDi got a forwarded call of " + getName() //$NON-NLS-1$
                + ", but it is no test instance: -Diagentbridge.uiTools=true is missing. Check -Diagentbridge.port of the launch configuration " //$NON-NLS-1$
                + "of the test instance."); //$NON-NLS-1$
        }

        // The names of the launch configurations only help the message, that no
        // test instance runs.
        List<TestInstance> running = TestInstanceFinder.findRunning();
        List<String> configurations = running.isEmpty() ? TestInstanceFinder.findConfigurations() : Collections.<String> emptyList();

        ForwardingClient client = new ForwardingClient(ForwardingClient.CONNECT_TIMEOUT_MILLIS, getForwardingTimeoutMillis(anArguments));
        JsonObject arguments = UiArguments.withKeepDialogsBehind(anArguments, UiPreferences.isKeepDialogsBehind());
        return new Forwarding(client, System.getProperties()).forward(running, configurations, getName(), arguments);
    }

    /**
     * Returns the time, that the RDi, that forwards a call, waits for the
     * answer of the test instance. A tool, whose time in the test instance
     * depends on its arguments, checks them here, before the call is forwarded.
     *
     * @param anArguments - arguments of the tool
     * @return milliseconds
     * @throws IllegalArgumentException if an argument, that the time depends
     *         on, is wrong
     */
    protected int getForwardingTimeoutMillis(JsonObject anArguments) {
        return ForwardingClient.READ_TIMEOUT_MILLIS;
    }

    /**
     * Executes the tool in the test instance.
     *
     * @param anArguments - arguments of the tool
     * @return result
     * @throws Exception if the tool failed unexpectedly
     */
    protected abstract McpToolResult executeInTestInstance(JsonObject anArguments) throws Exception;

    /**
     * Returns whether this RDi is a test instance, that executes the UI tools.
     *
     * @return <code>true</code>, if the system property 'iagentbridge.uiTools'
     *         is 'true'
     */
    static boolean isTestInstance() {
        String value = System.getProperty(TestInstance.PROPERTY_UI_TOOLS);
        return value != null && Boolean.TRUE.toString().equalsIgnoreCase(value.trim());
    }

    protected static Display getDisplay() {
        return PlatformUI.getWorkbench().getDisplay();
    }

    /**
     * Returns the failures of earlier actions, that ended after their tool had
     * returned, and forgets them. Every UI tool puts them in front of its
     * result.
     *
     * @return one line per failure, or an empty string
     */
    protected static String takePendingFailures() {
        return PENDING_FAILURES.take();
    }

    /**
     * Returns the failures of earlier actions, that ended after their tool had
     * returned.
     *
     * @return the failures
     */
    protected static PendingFailures getPendingFailures() {
        return PENDING_FAILURES;
    }

    /**
     * Returns the result for a busy UI thread.
     *
     * @param anException - the exception
     * @return error result
     */
    protected static McpToolResult busy(UiThreadBusyException anException) {
        return McpToolResult.error(anException.getMessage()
            + " - a long operation, a thread stopped in the debugger or a native dialog of Windows (e.g. a file dialog) may block it. Try again later."); //$NON-NLS-1$
    }

    /**
     * Returns the text for a snapshot after an action or a wait, that the busy
     * UI thread did not take.
     *
     * @param aMessage - the message of the UiThreadBusyException
     * @return text
     */
    static String snapshotBusy(String aMessage) {
        return Hints.after("No snapshot: " + aMessage, "Take one later with ui_snapshot."); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /**
     * Returns the text for a snapshot after an action or a wait, that failed.
     *
     * @param anException - the failure
     * @return text
     */
    static String snapshotFailed(Exception anException) {
        return Hints.after("No snapshot: " + anException, "Take one with ui_snapshot."); //$NON-NLS-1$ //$NON-NLS-2$
    }
}
