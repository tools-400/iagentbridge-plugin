/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.tools;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.eclipse.swt.widgets.Display;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import de.tools400.iagentbridge.mcp.McpToolResult;
import de.tools400.iagentbridge.ui.model.Deadline;
import de.tools400.iagentbridge.ui.model.Journal;
import de.tools400.iagentbridge.ui.model.JournalEntry;
import de.tools400.iagentbridge.ui.preferences.UiPreferences;
import de.tools400.iagentbridge.ui.swt.ActionRejectedException;
import de.tools400.iagentbridge.ui.swt.ActionTarget;
import de.tools400.iagentbridge.ui.swt.ForegroundGuard;
import de.tools400.iagentbridge.ui.swt.ListenerFailedException;
import de.tools400.iagentbridge.ui.swt.ListenerFailures;
import de.tools400.iagentbridge.ui.swt.UiThread;
import de.tools400.iagentbridge.ui.swt.UiThread.Outcome;
import de.tools400.iagentbridge.ui.swt.UiThreadBusyException;

/**
 * A UI tool, that acts in the test instance: it checks its target, records the
 * action in the journal and sends the events in one runnable of the UI thread,
 * and then returns the snapshot of the active shell.
 * <ol>
 * <li>{@link #prepare(JsonObject)} checks the arguments outside of the UI
 * thread.</li>
 * <li>The action runs with {@link UiThread#act}: it may open a modal dialog and
 * not return, while the UI thread answers.</li>
 * <li>A snapshot of the active shell follows, unless the argument 'snapshot' is
 * false. It runs after all code, that the action passed to the UI thread with
 * asyncExec.</li>
 * </ol>
 * All work of a call in the UI thread shares one deadline, so that the
 * forwarding never gives up before the test instance.
 */
public abstract class AbstractActionTool extends AbstractUiTool {

    /** Time, that an action has for all its work in the UI thread */
    protected static final int ACTION_DEADLINE_SECONDS = 20;

    /** End of the description of every action */
    protected static final String ACTION_SUFFIX = " Returns the snapshot of the active shell afterwards, unless 'snapshot' is false. " //$NON-NLS-1$
        + "The events are sent like SWTBot does it, without the mouse and the keyboard of the operating system." //$NON-NLS-1$
        + DESCRIPTION_SUFFIX;

    /** Schema of the argument 'snapshot', a property of every action */
    protected static final String SNAPSHOT_PROPERTY = "\"snapshot\":{\"type\":\"boolean\",\"description\":" //$NON-NLS-1$
        + "\"Return the snapshot of the active shell after the action. Default: true.\"}"; //$NON-NLS-1$

    private static final long ACTION_WAIT_MILLIS = 1000;

    private static final Journal JOURNAL = new Journal(Journal.DEFAULT_CAPACITY);

    @Override
    protected final McpToolResult executeInTestInstance(JsonObject anArguments) throws Exception {

        Display display = getDisplay();
        return execute(anArguments, display, UiThread.executor(display), () -> startGuard(display));
    }

    /**
     * Starts the guard for the foreground. Runs in the UI thread.
     *
     * @return the code, that stops the guard in the UI thread, or
     *         <code>null</code>, if the guard is not needed
     */
    private static Runnable startGuard(Display aDisplay) {
        ForegroundGuard guard = ForegroundGuard.start(aDisplay);
        return guard.isActive() ? guard::stop : null;
    }

    /**
     * Runs the tool.
     *
     * @param anArguments - arguments of the tool
     * @param aDisplay - display of the test instance
     * @param anUi - passes code to the UI thread
     * @param aGuard - starts the guard for the foreground in the UI thread, if
     *        the dialogs of actions are kept behind the other windows, and
     *        returns the code, that stops it, or <code>null</code>
     * @return result of the tool
     * @throws Exception exception of the action
     */
    McpToolResult execute(JsonObject anArguments, Display aDisplay, Executor anUi, Supplier<Runnable> aGuard) throws Exception {

        Action action;
        boolean snapshot;
        boolean keepDialogsBehind;
        try {
            action = prepare(anArguments);
            snapshot = UiArguments.getBoolean(anArguments, UiArguments.ARGUMENT_SNAPSHOT, isSnapshotDefault());
            keepDialogsBehind = isKeepDialogsBehind(anArguments);
        } catch (IllegalArgumentException e) {
            return error(e.getMessage());
        }

        ActionEnvironment environment = new ActionEnvironment(aDisplay, anUi, Deadline.inSeconds(getDeadlineSeconds(anArguments)),
            keepDialogsBehind ? aGuard : () -> null);
        try {
            action.prelude(environment);
        } catch (ActionRejectedException | IllegalArgumentException | ListenerFailedException e) {
            return error(e.getMessage());
        } catch (UiThreadBusyException e) {
            return error("Not executed - " + e.getMessage() + ". The action will not run; repeat it, when the test instance answers again."); //$NON-NLS-1$ //$NON-NLS-2$
        }

        GuardSlot guard = new GuardSlot(anUi);
        try {
            return act(action, new ActionContext(getName(), UiArguments.forJournal(anArguments)), guard, snapshot, environment);
        } finally {
            guard.end();
        }
    }

    /**
     * Returns whether the dialogs of actions are kept behind the other windows:
     * as the RDi, that forwards the call, says, or else as the preferences of
     * the test instance say.
     */
    private static boolean isKeepDialogsBehind(JsonObject anArguments) {

        JsonElement forwarded = anArguments.get(UiArguments.ARGUMENT_KEEP_DIALOGS_BEHIND);
        if (forwarded == null || forwarded.isJsonNull()) {
            return UiPreferences.isKeepDialogsBehind();
        }

        return UiArguments.getBoolean(anArguments, UiArguments.ARGUMENT_KEEP_DIALOGS_BEHIND, false);
    }

    /**
     * Runs the action and returns its result with the snapshot. If the dialogs
     * of actions are kept behind the other windows, the guard starts in the
     * runnable of the action; the caller stops it. The failures of earlier
     * actions are taken only for a result, so that they stay for the next tool,
     * if this one ends with an exception, and before the snapshot, so that the
     * failure of this action, that ends during the snapshot, is reported by the
     * next tool, not as an earlier one by this tool.
     */
    private static McpToolResult act(Action anAction, ActionContext aContext, GuardSlot aGuard, boolean aSnapshot, ActionEnvironment anEnvironment)
        throws Exception {

        Display display = anEnvironment.getDisplay();
        Executor ui = anEnvironment.getUi();
        Deadline deadline = anEnvironment.getDeadline();
        Outcome<String> outcome;
        try {
            outcome = UiThread.act(ui, deadline, ACTION_WAIT_MILLIS, () -> {
                aGuard.start(anEnvironment);
                return ListenerFailures.run(display, () -> anAction.perform(aContext));
            }, aContext::lateEnd);
        } catch (ListenerFailedException e) {
            aContext.failed(e.getCause());
            StringBuilder text = new StringBuilder(e.getMessage());
            appendSnapshot(text, aSnapshot, display, ui, deadline);
            return error(text.toString());
        } catch (ActionRejectedException | IllegalArgumentException e) {
            // Thrown before the action is recorded: nothing was done. Thrown
            // after it - ESC, that did not close its shell, a missing item of a
            // context menu, a command, that is not enabled - the journal shows
            // the error.
            aContext.failed(e);
            return error(e.getMessage());
        } catch (Exception | LinkageError e) {
            aContext.failed(e);
            throw e;
        }

        StringBuilder text = new StringBuilder();
        switch (outcome.getKind()) {
        case DONE:
            aContext.done();
            text.append(outcome.getValue());
            break;
        case DIALOG_OPEN:
            text.append("The action has not returned yet - a dialog is open. Operate or close the dialog; the next UI tool reports a later " //$NON-NLS-1$
                + "failure of the action."); //$NON-NLS-1$
            break;
        case STILL_RUNNING:
            return error("UI thread busy for " + outcome.getSeconds() + " s - the action is still running. Do not repeat it; take a snapshot later."); //$NON-NLS-1$ //$NON-NLS-2$
        default:
            return error("Not executed - UI thread busy for " + outcome.getSeconds() //$NON-NLS-1$
                + " s. The action will not run; repeat it, when the test instance answers again."); //$NON-NLS-1$
        }

        String pending = takePendingFailures();
        appendSnapshot(text, aSnapshot, display, ui, deadline);

        return McpToolResult.success(pending + text);
    }

    /**
     * Returns an error with the failures of earlier actions in front.
     */
    private static McpToolResult error(String aText) {
        return McpToolResult.error(takePendingFailures() + aText);
    }

    /**
     * Runs a step of the prelude of an action in the UI thread, like the action
     * itself, with a guard for the foreground, if the dialogs are kept behind
     * the other windows. A step, that does not end within the wait, ends the
     * action.
     *
     * @param anEnvironment - the environment of the action
     * @param aDescription - the step, for messages, e.g. "Expanding 'General'"
     * @param aStep - the step, that runs in the UI thread
     * @return result of the step
     * @throws ActionRejectedException if the step did not end within the wait,
     *         e.g. because it opened a dialog
     * @throws Exception exception of the step
     */
    protected static <T> T step(ActionEnvironment anEnvironment, String aDescription, Callable<T> aStep) throws Exception {

        Display display = anEnvironment.getDisplay();
        GuardSlot guard = new GuardSlot(anEnvironment.getUi());
        try {
            Outcome<T> outcome = UiThread.act(anEnvironment.getUi(), anEnvironment.getDeadline(), ACTION_WAIT_MILLIS, () -> {
                guard.start(anEnvironment);
                return ListenerFailures.run(display, aStep);
            }, aFailure -> {
                if (aFailure != null) {
                    getPendingFailures().add(aDescription, unwrap(aFailure));
                }
            });
            switch (outcome.getKind()) {
            case DONE:
                return outcome.getValue();
            case DIALOG_OPEN:
                throw new ActionRejectedException(aDescription + " has not returned yet - a dialog is open. Take a snapshot."); //$NON-NLS-1$
            case STILL_RUNNING:
                throw new ActionRejectedException(aDescription + ": UI thread busy for " + outcome.getSeconds() //$NON-NLS-1$
                    + " s - it is still running. Take a snapshot later."); //$NON-NLS-1$
            default:
                throw new ActionRejectedException(aDescription + " not executed - UI thread busy for " + outcome.getSeconds() //$NON-NLS-1$
                    + " s. The action will not run; repeat it, when the test instance answers again."); //$NON-NLS-1$
            }
        } finally {
            guard.end();
        }
    }

    /**
     * Checks the arguments outside of the UI thread and returns the action.
     *
     * @param anArguments - arguments of the tool
     * @return the action, that runs in the UI thread
     * @throws IllegalArgumentException if an argument is wrong
     */
    protected abstract Action prepare(JsonObject anArguments);

    /**
     * Returns whether the tool returns the snapshot of the active shell, if the
     * argument 'snapshot' is missing.
     *
     * @return <code>true</code> by default
     */
    protected boolean isSnapshotDefault() {
        return true;
    }

    /**
     * Returns the deadline of a call.
     *
     * @param anArguments - arguments of the tool
     * @return seconds, that all work of the call in the UI thread may take
     */
    protected int getDeadlineSeconds(JsonObject anArguments) {
        return ACTION_DEADLINE_SECONDS;
    }

    /**
     * Returns the failure of a listener, that ListenerFailures caught, or the
     * failure itself.
     */
    private static Throwable unwrap(Throwable aFailure) {
        return aFailure instanceof ListenerFailedException && aFailure.getCause() != null ? aFailure.getCause() : aFailure;
    }

    private static void appendSnapshot(StringBuilder aText, boolean aSnapshot, Display aDisplay, Executor anUi, Deadline aDeadline) throws Exception {

        try {
            if (aSnapshot) {
                Snapshots.Snapshot model = UiThread.read(anUi, aDeadline, () -> Snapshots.take(aDisplay, null, null));
                aText.append('\n').append(model.write(Snapshots.DEFAULT_MAX_NODES, false));
            } else {
                // Lets the code run, that the action passed with asyncExec.
                UiThread.read(anUi, aDeadline, () -> null);
            }
        } catch (UiThreadBusyException e) {
            aText.append('\n').append(snapshotBusy(e.getMessage()));
        } catch (RejectedExecutionException e) {
            aText.append('\n').append(WORKBENCH_ENDED);
        } catch (Exception e) {
            // The action ran: its result counts, not the snapshot.
            aText.append('\n').append(snapshotFailed(e));
        }
    }

    /**
     * The guard for the foreground of a call or a step. The action starts it in
     * the UI thread, the call stops it at its end. If the call has ended
     * before, because the action began only shortly before the deadline, the
     * action stops it at once, so that no guard stays.
     */
    private static final class GuardSlot {

        private static final Runnable ENDED = () -> {
            // The call has ended.
        };

        private final Executor ui;
        private final AtomicReference<Runnable> stop = new AtomicReference<Runnable>();

        GuardSlot(Executor anUi) {
            ui = anUi;
        }

        /**
         * Starts the guard, if the environment asks for it. Runs in the UI
         * thread.
         */
        void start(ActionEnvironment anEnvironment) {
            Runnable started = anEnvironment.startGuard();
            if (started != null && !stop.compareAndSet(null, started)) {
                started.run();
            }
        }

        /**
         * Stops the guard in the UI thread at the end of the call.
         */
        void end() {
            Runnable started = stop.getAndSet(ENDED);
            if (started != null && started != ENDED) {
                try {
                    ui.execute(started);
                } catch (RejectedExecutionException e) {
                    // The display has ended, and the guard with it.
                }
            }
        }
    }

    /**
     * An action, that runs in the UI thread.
     */
    @FunctionalInterface
    protected interface Action {

        /**
         * Checks the target, records the action and sends the events. Runs in
         * the UI thread.
         *
         * @param aContext - the context, that records the action
         * @return a short text about what was done, the first line of the
         *         result
         * @throws ActionRejectedException if the target cannot be operated.
         *         Thrown before the action is recorded, nothing was done.
         *         Thrown after it, e.g. by ui_menu for a missing item of a
         *         context menu or by ui_command for a command, that is not
         *         enabled, the journal shows the error, and what the action did
         *         before stays, e.g. the selection or the active part.
         * @throws Exception if the action fails
         */
        String perform(ActionContext aContext) throws Exception;

        /**
         * Prepares the action in steps of their own, before it runs, e.g.
         * expands the levels of a path in a tree, whose children a viewer
         * creates later, when the UI thread is free. Runs in the thread of the
         * server; each step uses the UI thread with
         * {@link AbstractActionTool#step}. By default nothing.
         *
         * @param anEnvironment - the environment of the action
         * @throws ActionRejectedException if a step is not possible
         * @throws UiThreadBusyException if the UI thread does not answer
         * @throws Exception if a step fails
         */
        default void prelude(ActionEnvironment anEnvironment) throws Exception {
            // No prelude.
        }
    }

    /**
     * What an action and the steps of its prelude work with: the display, the
     * UI thread, the deadline of the call and whether the dialogs of actions
     * are kept behind the other windows.
     */
    protected static final class ActionEnvironment {

        private final Display display;
        private final Executor ui;
        private final Deadline deadline;
        private final Supplier<Runnable> guard;

        ActionEnvironment(Display aDisplay, Executor anUi, Deadline aDeadline, Supplier<Runnable> aGuard) {
            display = aDisplay;
            ui = anUi;
            deadline = aDeadline;
            guard = aGuard;
        }

        public Display getDisplay() {
            return display;
        }

        /**
         * Returns the executor, that passes code to the UI thread.
         *
         * @return executor
         */
        public Executor getUi() {
            return ui;
        }

        public Deadline getDeadline() {
            return deadline;
        }

        /**
         * Starts a guard for the foreground, if the dialogs of actions are kept
         * behind the other windows. Runs in the UI thread.
         *
         * @return the code, that stops the guard in the UI thread, or
         *         <code>null</code>
         */
        Runnable startGuard() {
            return guard.get();
        }
    }

    /**
     * Records an action in the journal and keeps track of its end.
     */
    protected static final class ActionContext {

        private final String tool;
        private final String arguments;
        private volatile JournalEntry entry;
        private volatile String description;

        ActionContext(String aTool, String anArguments) {
            tool = aTool;
            arguments = anArguments;
            description = aTool;
        }

        /**
         * Records the action on a widget in the journal and activates the view
         * or editor of the widget, like a click into it. Called in the UI
         * thread, after the checks and before the events.
         *
         * @param aTarget - the target
         */
        public void record(ActionTarget aTarget) {
            entry = aTarget.record(JOURNAL, tool, arguments);
            description = tool + " " + aTarget.describe(); //$NON-NLS-1$
            aTarget.activatePart();
        }

        /**
         * Records an action without a widget, e.g. a command, in the journal.
         *
         * @param aShell - title of the active shell
         * @param aType - type of the target, e.g. 'command'
         * @param aText - text of the target, e.g. the ID of the command
         */
        public void record(String aShell, String aType, String aText) {
            entry = JOURNAL.add(tool, arguments, aShell, aType, aText, null, null);
            description = tool + " " + aText; //$NON-NLS-1$
        }

        void done() {
            setResult(JournalEntry.OK);
        }

        void failed(Throwable aFailure) {
            setResult(aFailure.toString());
        }

        /**
         * The action ended after its tool had returned.
         */
        void lateEnd(Throwable aFailure) {
            if (aFailure == null) {
                setResult(JournalEntry.OK);
            } else {
                Throwable failure = unwrap(aFailure);
                setResult(failure.toString());
                getPendingFailures().add(description, failure);
            }
        }

        private void setResult(String aResult) {
            JournalEntry current = entry;
            if (current != null) {
                current.setResult(aResult);
            }
        }
    }
}
