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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.junit.Before;
import org.junit.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import de.tools400.iagentbridge.mcp.McpToolResult;

/**
 * The flow of an action without RDi: failures of earlier actions, a failing
 * snapshot and the guard for the foreground. The test lies in the package of
 * the tools, because it calls their package visible method execute(). The
 * actions run without a display.
 */
public class AbstractActionToolTest {

    private static final String EARLIER = "The earlier action ui_click w17 failed: java.lang.IllegalStateException: listener failed. The action may " //$NON-NLS-1$
        + "have run in part: check its result in a snapshot before you repeat it.\n"; //$NON-NLS-1$

    /** A snapshot of a wait, without regular expression characters */
    private static final String SNAPSHOT = "shell Preferences w1 modal active"; //$NON-NLS-1$

    /** The hint after the end of the workbench or the display */
    private static final String INSTANCE_RUNS = "list_launches shows, whether the test instance runs; start_launch starts it again."; //$NON-NLS-1$

    @Before
    public void setUp() {
        AbstractUiTool.takePendingFailures();
    }

    @Test
    public void testWrongArgumentReportsEarlierFailures() throws Exception {

        addEarlierFailure();
        McpToolResult result = new TestTool(aContext -> "Clicked").execute(arguments("{\"snapshot\":\"no\",\"keepDialogsBehind\":false}"), null, //$NON-NLS-1$ //$NON-NLS-2$
            Runnable::run, () -> null);

        assertTrue(result.isError());
        assertTrue(result.getText(), result.getText().startsWith(EARLIER));
        assertTrue(result.getText(), result.getText().contains("'snapshot'")); //$NON-NLS-1$
    }

    @Test
    public void testFailingActionKeepsEarlierFailures() throws Exception {

        addEarlierFailure();
        TestTool tool = new TestTool(aContext -> {
            throw new IllegalStateException("listener of this action failed"); //$NON-NLS-1$
        });
        try {
            tool.execute(arguments("{\"snapshot\":false,\"keepDialogsBehind\":false}"), null, Runnable::run, () -> null); //$NON-NLS-1$
            fail("The exception of the action is expected."); //$NON-NLS-1$
        } catch (IllegalStateException e) {
            assertEquals("listener of this action failed", e.getMessage()); //$NON-NLS-1$
        }

        assertEquals(EARLIER, AbstractUiTool.takePendingFailures());
    }

    @Test
    public void testFailingSnapshotReturnsResult() throws Exception {

        addEarlierFailure();
        int[] clicks = new int[1];
        TestTool tool = new TestTool(aContext -> {
            clicks[0]++;
            return "Clicked button \"OK\" [w20]."; //$NON-NLS-1$
        });
        // Without a display the snapshot fails.
        McpToolResult result = tool.execute(arguments("{\"keepDialogsBehind\":false}"), null, Runnable::run, () -> null); //$NON-NLS-1$

        assertEquals(1, clicks[0]);
        assertFalse(result.getText(), result.isError());
        assertTrue(result.getText(), result.getText().startsWith(EARLIER + "Clicked button \"OK\" [w20].\nNo snapshot: ")); //$NON-NLS-1$
        assertTrue(result.getText(), result.getText().endsWith(". Take one with ui_snapshot.")); //$NON-NLS-1$
    }

    @Test
    public void testBusySnapshotReturnsResult() throws Exception {

        AtomicInteger calls = new AtomicInteger();
        // The UI thread runs the action, but not the snapshot.
        Executor ui = aRunnable -> {
            if (calls.incrementAndGet() == 1) {
                aRunnable.run();
            }
        };
        McpToolResult result = new TestTool(aContext -> "Clicked button \"OK\" [w20].", 1).execute(arguments("{\"keepDialogsBehind\":false}"), //$NON-NLS-1$ //$NON-NLS-2$
            null, ui, () -> null);

        assertFalse(result.getText(), result.isError());
        assertEquals("Clicked button \"OK\" [w20].\nNo snapshot: UI thread busy for 1 s. Take one later with ui_snapshot.", result.getText()); //$NON-NLS-1$
    }

    @Test
    public void testStepNotExecuted() throws Exception {

        AbstractActionTool.Action action = new AbstractActionTool.Action() {

            @Override
            public String perform(AbstractActionTool.ActionContext aContext) {
                return "Clicked"; //$NON-NLS-1$
            }

            @Override
            public void prelude(AbstractActionTool.ActionEnvironment anEnvironment) throws Exception {
                AbstractActionTool.step(anEnvironment, "Expanding 'General'", () -> null); //$NON-NLS-1$
            }
        };
        // The UI thread runs nothing.
        Executor busy = aRunnable -> {
            // Never runs.
        };
        McpToolResult result = new TestTool(action, 1).execute(arguments("{\"keepDialogsBehind\":false}"), null, busy, () -> null); //$NON-NLS-1$

        assertTrue(result.isError());
        assertEquals("Expanding 'General' not executed - UI thread busy for 1 s. The action will not run; repeat it, when the test instance " //$NON-NLS-1$
            + "answers again.", result.getText()); //$NON-NLS-1$
    }

    @Test
    public void testGuardStartedAfterTheEndOfTheCallIsStopped() throws Exception {

        CountDownLatch callEnded = new CountDownLatch(1);
        CountDownLatch stopped = new CountDownLatch(1);
        Executor threads = aRunnable -> new Thread(aRunnable).start();
        // The action starts the guard only after the call has ended.
        Supplier<Runnable> guard = () -> {
            await(callEnded);
            return stopped::countDown;
        };

        McpToolResult result = new TestTool(aContext -> "Clicked").execute(arguments("{\"snapshot\":false,\"keepDialogsBehind\":true}"), null, //$NON-NLS-1$ //$NON-NLS-2$
            threads, guard);
        callEnded.countDown();

        assertTrue(result.getText(), result.getText().contains("a dialog is open")); //$NON-NLS-1$
        assertTrue("The guard is stopped.", stopped.await(5, TimeUnit.SECONDS)); //$NON-NLS-1$
    }

    @Test
    public void testOwnLateFailureIsNotInTheOwnResult() throws Exception {

        CountDownLatch dialogClosed = new CountDownLatch(1);
        AtomicReference<Thread> actionThread = new AtomicReference<Thread>();
        AtomicInteger calls = new AtomicInteger();
        // 1st call: the action, 2nd: the probe, 3rd: the step after the action,
        // while which the dialog closes.
        Executor threads = aRunnable -> {
            int call = calls.incrementAndGet();
            Thread thread = new Thread(() -> {
                if (call == 3) {
                    dialogClosed.countDown();
                    join(actionThread.get());
                }
                aRunnable.run();
            });
            if (call == 1) {
                actionThread.set(thread);
            }
            thread.start();
        };
        TestTool tool = new TestTool(aContext -> {
            await(dialogClosed);
            throw new IllegalStateException("own failure"); //$NON-NLS-1$
        });

        McpToolResult result = tool.execute(arguments("{\"snapshot\":false,\"keepDialogsBehind\":false}"), null, threads, () -> null); //$NON-NLS-1$

        assertEquals("The action has not returned yet - a dialog is open. Operate or close the dialog; the next UI tool reports a later " //$NON-NLS-1$
            + "failure of the action.", result.getText()); //$NON-NLS-1$
        assertEquals("The earlier action ui_test failed: java.lang.IllegalStateException: own failure. The action may have run in part: " //$NON-NLS-1$
            + "check its result in a snapshot before you repeat it.\n", AbstractUiTool.takePendingFailures()); //$NON-NLS-1$
    }

    @Test
    public void testActionThatEndsTheWorkbench() throws Exception {

        AtomicInteger calls = new AtomicInteger();
        // After the action, e.g. ESC on the last dialog of a closing
        // workbench, the display has ended and rejects the code of the snapshot
        // and of the end of the guard.
        Executor ui = aRunnable -> {
            if (calls.incrementAndGet() > 1) {
                throw new RejectedExecutionException("The display has ended."); //$NON-NLS-1$
            }
            aRunnable.run();
        };
        McpToolResult result = new TestTool(aContext -> "Pressed ESC: closed shell \"Kennwort eingeben\" [w4].") //$NON-NLS-1$
            .execute(arguments("{\"keepDialogsBehind\":true}"), null, ui, () -> () -> { //$NON-NLS-1$
                // Stops the guard.
            });

        assertFalse(result.getText(), result.isError());
        assertEquals("Pressed ESC: closed shell \"Kennwort eingeben\" [w4].\nThe workbench of the test instance has ended. " + INSTANCE_RUNS, //$NON-NLS-1$
            result.getText());
    }

    @Test
    public void testWaitAfterTheEndOfTheWorkbench() throws Exception {

        Executor ended = aRunnable -> {
            throw new RejectedExecutionException("The display has ended."); //$NON-NLS-1$
        };
        McpToolResult result = new UiWaitTool().execute(arguments("{\"shell\":\"Preferences\",\"timeoutSeconds\":1}"), null, ended); //$NON-NLS-1$

        assertTrue(result.isError());
        assertEquals("Waiting for the shell \"Preferences\" stopped. The workbench of the test instance has ended. " + INSTANCE_RUNS, //$NON-NLS-1$
            result.getText());
    }

    /**
     * A UI thread, that runs no check, ends the wait after its time and the
     * reserve for the snapshot: 11 s.
     */
    @Test
    public void testWaitWithBusyUiThread() throws Exception {

        Executor busy = aRunnable -> {
            // Never runs.
        };
        McpToolResult result = new UiWaitTool().execute(arguments("{\"shell\":\"Preferences\",\"timeoutSeconds\":1}"), null, busy); //$NON-NLS-1$

        assertTrue(result.isError());
        assertEquals("Waiting for the shell \"Preferences\": UI thread busy for 11 s. Try again later; ui_error_log answers also, while the " //$NON-NLS-1$
            + "UI thread is busy.", result.getText()); //$NON-NLS-1$
    }

    @Test
    public void testFailingWaitKeepsEarlierFailures() throws Exception {

        addEarlierFailure();
        try {
            // Without a display the check fails.
            new UiWaitTool().execute(arguments("{\"shell\":\"Preferences\",\"timeoutSeconds\":1}"), null, Runnable::run); //$NON-NLS-1$
            fail("The exception of the check is expected."); //$NON-NLS-1$
        } catch (Exception e) {
            // Expected.
        }

        assertEquals(EARLIER, AbstractUiTool.takePendingFailures());
    }

    /**
     * A wait through execute() with a check and a snapshot of the test in place
     * of the display: the line about the condition fits the snapshot.
     */
    @Test
    public void testWaitMet() throws Exception {

        McpToolResult result = new UiWaitTool().execute(arguments("{\"shell\":\"Preferences\",\"timeoutSeconds\":1}"), Runnable::run, //$NON-NLS-1$
            aCondition -> Boolean.TRUE, () -> SNAPSHOT);

        assertFalse(result.isError());
        assertTrue(result.getText(), result.getText().matches("Waited [0-9.]+ s for the shell \"Preferences\"\\.\n" + SNAPSHOT)); //$NON-NLS-1$
    }

    @Test
    public void testWaitTimeoutWithSnapshot() throws Exception {

        McpToolResult result = new UiWaitTool().execute(arguments("{\"shell\":\"Preferences\",\"timeoutSeconds\":1}"), Runnable::run, //$NON-NLS-1$
            aCondition -> Boolean.FALSE, () -> SNAPSHOT);

        assertTrue(result.isError());
        assertTrue(result.getText(), result.getText().matches("Timeout after [0-9.]+ s waiting for the shell \"Preferences\"\\. See the snapshot; " //$NON-NLS-1$
            + "ui_error_log may show, why nothing happened\\.\n" + SNAPSHOT)); //$NON-NLS-1$
    }

    /**
     * The snapshot after the wait fails: the line about the condition does not
     * point to it.
     */
    @Test
    public void testWaitTimeoutWithoutSnapshot() throws Exception {

        McpToolResult result = new UiWaitTool().execute(arguments("{\"shell\":\"Preferences\",\"timeoutSeconds\":1}"), Runnable::run, //$NON-NLS-1$
            aCondition -> Boolean.FALSE, () -> {
                throw new IllegalStateException("Widget is disposed"); //$NON-NLS-1$
            });

        assertTrue(result.isError());
        assertTrue(result.getText(),
            result.getText()
                .matches("Timeout after [0-9.]+ s waiting for the shell \"Preferences\"\\. ui_error_log may " //$NON-NLS-1$
                    + "show, why nothing happened\\.\nNo snapshot: java\\.lang\\.IllegalStateException: Widget is disposed\\. Take one with " //$NON-NLS-1$
                    + "ui_snapshot\\.")); //$NON-NLS-1$
    }

    private static void addEarlierFailure() {
        AbstractUiTool.getPendingFailures().add("ui_click w17", new IllegalStateException("listener failed")); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static JsonObject arguments(String aJson) {
        return new JsonParser().parse(aJson).getAsJsonObject();
    }

    private static void join(Thread aThread) {
        try {
            aThread.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void await(CountDownLatch aLatch) {
        try {
            aLatch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * A tool, whose action the test gives.
     */
    private static final class TestTool extends AbstractActionTool {

        private final Action action;
        private final int deadlineSeconds;

        TestTool(Action anAction) {
            this(anAction, ACTION_DEADLINE_SECONDS);
        }

        TestTool(Action anAction, int aDeadlineSeconds) {
            action = anAction;
            deadlineSeconds = aDeadlineSeconds;
        }

        @Override
        protected int getDeadlineSeconds(JsonObject anArguments) {
            return deadlineSeconds;
        }

        @Override
        public String getName() {
            return "ui_test"; //$NON-NLS-1$
        }

        @Override
        public String getDescription() {
            return ""; //$NON-NLS-1$
        }

        @Override
        public JsonObject getInputSchema() {
            return new JsonObject();
        }

        @Override
        protected Action prepare(JsonObject anArguments) {
            return action;
        }
    }
}
