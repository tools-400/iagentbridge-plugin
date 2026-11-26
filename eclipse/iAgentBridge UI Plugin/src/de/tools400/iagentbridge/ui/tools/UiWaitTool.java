/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.tools;

import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Widget;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import de.tools400.iagentbridge.mcp.McpToolResult;
import de.tools400.iagentbridge.ui.model.Deadline;
import de.tools400.iagentbridge.ui.model.WaitCondition;
import de.tools400.iagentbridge.ui.model.WaitCondition.Kind;
import de.tools400.iagentbridge.ui.swt.ShellPicker;
import de.tools400.iagentbridge.ui.swt.UiThread;
import de.tools400.iagentbridge.ui.swt.UiThreadBusyException;
import de.tools400.iagentbridge.ui.swt.WidgetModelBuilder;
import de.tools400.iagentbridge.ui.swt.WidgetRefs;

/**
 * Tool 'ui_wait': waits for a shell, a text, the end of a shell or a widget, or
 * the end of all jobs, and returns the snapshot of the active shell.
 */
public class UiWaitTool extends AbstractUiTool {

    private static final String DESCRIPTION = "Waits in the test instance of RDi for exactly one condition: 'shell' - a visible shell " //$NON-NLS-1$
        + "with this title; 'text' - a text in the active shell, searched in the same texts as the snapshot shows them (tables and lists " //$NON-NLS-1$
        + "only in their first 50 rows, password fields never); 'gone' - a reference or the title of a shell, that is closed or hidden; " //$NON-NLS-1$
        + "'jobsIdle' - no job runs. Returns the snapshot of the active shell, or an error with it after 'timeoutSeconds'. While it " //$NON-NLS-1$
        + "waits, the server answers no other call, also not the one of the RDi, in which the developer works." //$NON-NLS-1$
        + DESCRIPTION_SUFFIX;

    private static final String INPUT_SCHEMA = "{\"type\":\"object\",\"properties\":{" //$NON-NLS-1$
        + "\"shell\":{\"type\":\"string\",\"description\":\"Title of a shell, that becomes visible.\"}," //$NON-NLS-1$
        + "\"text\":{\"type\":\"string\",\"description\":\"Text, that appears in the active shell.\"}," //$NON-NLS-1$
        + "\"gone\":{\"type\":\"string\",\"description\":\"Reference of a widget or title of a shell, that disappears.\"}," //$NON-NLS-1$
        + "\"jobsIdle\":{\"type\":\"boolean\",\"description\":\"true: wait, until no job runs.\"}," //$NON-NLS-1$
        + "\"timeoutSeconds\":{\"type\":\"integer\",\"description\":\"Time to wait, 1 to 60 seconds. Default: 10.\"}}}"; //$NON-NLS-1$

    private static final long MIN_INTERVAL_MILLIS = 250;

    /** Time of the snapshot after the wait */
    private static final int SNAPSHOT_RESERVE_SECONDS = 10;

    /** Reserve of the forwarding for the snapshot and the transfer */
    private static final int FORWARDING_RESERVE_SECONDS = 20;

    @Override
    public String getName() {
        return "ui_wait"; //$NON-NLS-1$
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
    protected int getForwardingTimeoutMillis(JsonObject anArguments) {
        return (int)TimeUnit.SECONDS.toMillis(WaitCondition.timeoutSeconds(anArguments) + FORWARDING_RESERVE_SECONDS);
    }

    @Override
    protected McpToolResult executeInTestInstance(JsonObject anArguments) throws Exception {

        Display display = getDisplay();
        return execute(anArguments, display, UiThread.executor(display));
    }

    /**
     * Waits for the condition.
     *
     * @param anArguments - arguments of the tool
     * @param aDisplay - display of the test instance
     * @param anUi - passes code to the UI thread
     * @return result of the tool
     * @throws Exception exception of a check
     */
    McpToolResult execute(JsonObject anArguments, Display aDisplay, Executor anUi) throws Exception {

        WaitCondition condition = WaitCondition.parse(anArguments);

        Deadline wait = Deadline.inSeconds(condition.getTimeoutSeconds());
        Deadline total = Deadline.inSeconds(condition.getTimeoutSeconds() + SNAPSHOT_RESERVE_SECONDS);
        long start = System.nanoTime();

        boolean met;
        try {
            met = await(condition, aDisplay, anUi, wait, total);
        } catch (UiThreadBusyException e) {
            return McpToolResult.error(takePendingFailures() + "Waiting for " + condition.describe() + ": " + e.getMessage() + "."); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        } catch (RejectedExecutionException e) {
            return McpToolResult.error(takePendingFailures() + "Waiting for " + condition.describe() + " stopped. " + WORKBENCH_ENDED); //$NON-NLS-1$ //$NON-NLS-2$
        } catch (IllegalArgumentException e) {
            return McpToolResult.error(takePendingFailures() + e.getMessage());
        }

        String seconds = String.format(Locale.ROOT, "%.1f", Double.valueOf((System.nanoTime() - start) / 1e9)); //$NON-NLS-1$
        StringBuilder text = new StringBuilder();
        if (met) {
            text.append("Waited ").append(seconds).append(" s for ").append(condition.describe()).append('.'); //$NON-NLS-1$ //$NON-NLS-2$
        } else {
            text.append("Timeout after ").append(seconds).append(" s waiting for ").append(condition.describe()).append('.'); //$NON-NLS-1$ //$NON-NLS-2$
        }

        try {
            Snapshots.Snapshot snapshot = UiThread.read(anUi, total, () -> Snapshots.take(aDisplay, null, null));
            text.append('\n').append(snapshot.write(Snapshots.DEFAULT_MAX_NODES, false));
        } catch (UiThreadBusyException e) {
            text.append("\nNo snapshot: ").append(e.getMessage()).append('.'); //$NON-NLS-1$
        } catch (RejectedExecutionException e) {
            text.append('\n').append(WORKBENCH_ENDED);
        } catch (Exception e) {
            // The wait is over: its result counts, not the snapshot.
            text.append("\nNo snapshot: ").append(e).append('.'); //$NON-NLS-1$
        }

        // Taken now, so that they stay for the next tool, if a check fails.
        String result = takePendingFailures() + text;
        return met ? McpToolResult.success(result) : McpToolResult.error(result);
    }

    /**
     * Checks the condition until it is met or the wait is over. The interval
     * grows with the time, that a check takes in the UI thread, so that the
     * checks never keep it busy for more than a third of the time.
     */
    private static boolean await(WaitCondition aCondition, Display aDisplay, Executor anUi, Deadline aWait, Deadline aTotal) throws Exception {

        while (true) {
            long start = System.nanoTime();
            boolean met;
            if (aCondition.getKind() == Kind.JOBS_IDLE) {
                met = Job.getJobManager().isIdle();
            } else {
                met = UiThread.read(anUi, aTotal, () -> isMet(aCondition, aDisplay)).booleanValue();
            }
            long took = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

            if (met) {
                return true;
            }
            if (aWait.isExpired()) {
                return false;
            }
            Thread.sleep(Math.min(Math.max(MIN_INTERVAL_MILLIS, 2 * took), aWait.remainingMillis()));
        }
    }

    /**
     * Checks the condition in the UI thread.
     */
    private static Boolean isMet(WaitCondition aCondition, Display aDisplay) {

        String value = aCondition.getValue();
        switch (aCondition.getKind()) {
        case SHELL:
            return Boolean.valueOf(findShell(aDisplay, value) != null);
        case TEXT:
            Shell active = ShellPicker.activeShell(aDisplay);
            return Boolean.valueOf(active != null && WaitCondition.contains(new WidgetModelBuilder(null).build(active), value));
        default:
            if (value.matches("w[0-9]+")) { //$NON-NLS-1$
                return Boolean.valueOf(isGone(value));
            }
            return Boolean.valueOf(findShell(aDisplay, value) == null);
        }
    }

    private static Shell findShell(Display aDisplay, String aTitle) {
        for (Shell shell : ShellPicker.visibleShells(aDisplay)) {
            if (aTitle.equals(shell.getText())) {
                return shell;
            }
        }
        return null;
    }

    private static boolean isGone(String aRef) {

        if (!WidgetRefs.wasAssigned(aRef)) {
            throw new IllegalArgumentException("Ref " + aRef + " is unknown. Take a snapshot."); //$NON-NLS-1$ //$NON-NLS-2$
        }
        try {
            Widget widget = WidgetRefs.get(aRef);
            return widget instanceof Control && !((Control)widget).isVisible();
        } catch (IllegalArgumentException e) {
            // The widget is disposed.
            return true;
        }
    }
}
