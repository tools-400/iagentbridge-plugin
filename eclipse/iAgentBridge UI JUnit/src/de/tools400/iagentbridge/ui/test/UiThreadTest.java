/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import de.tools400.iagentbridge.ui.model.Deadline;
import de.tools400.iagentbridge.ui.swt.UiThread;
import de.tools400.iagentbridge.ui.swt.UiThread.Outcome;
import de.tools400.iagentbridge.ui.swt.UiThreadBusyException;

/**
 * UiThread with a thread of its own in place of the UI thread: it runs the
 * passed code in order, and code may run a nested loop like a modal dialog.
 */
public class UiThreadTest {

    private static final RuntimeException NO_LATE_END = new IllegalStateException("not called");

    private FakeUiThread ui;
    private final AtomicReference<Throwable> lateEnd = new AtomicReference<Throwable>(NO_LATE_END);
    private final CountDownLatch lateEndCalled = new CountDownLatch(1);

    @Before
    public void setUp() {
        ui = new FakeUiThread();
    }

    @After
    public void tearDown() {
        ui.stop();
    }

    @Test
    public void testAct_done() throws Exception {

        Outcome<String> outcome = UiThread.act(ui, Deadline.inSeconds(5), 1000, () -> "clicked", this::lateEnd);

        assertEquals(Outcome.Kind.DONE, outcome.getKind());
        assertEquals("clicked", outcome.getValue());
    }

    @Test
    public void testAct_exception() throws Exception {

        try {
            UiThread.act(ui, Deadline.inSeconds(5), 1000, () -> {
                throw new IllegalArgumentException("not a checkbox");
            }, this::lateEnd);
            fail("exception expected");
        } catch (IllegalArgumentException e) {
            assertEquals("not a checkbox", e.getMessage());
        }
    }

    /**
     * The UI thread is busy, when the action is passed. The probe gets its turn
     * after the action, which has ended then without a dialog.
     */
    @Test
    public void testAct_doneAfterProbe() throws Exception {

        ui.execute(() -> sleep(1500));

        Outcome<String> outcome = UiThread.act(ui, Deadline.inSeconds(5), 200, () -> "clicked", this::lateEnd);

        assertEquals(Outcome.Kind.DONE, outcome.getKind());
        assertEquals("clicked", outcome.getValue());
        assertSame("no late end", NO_LATE_END, lateEnd.get());
    }

    /**
     * The action opens a dialog: its event loop runs the probe.
     */
    @Test
    public void testAct_dialogOpen() throws Exception {

        AtomicBoolean closed = new AtomicBoolean();

        Outcome<String> outcome = UiThread.act(ui, Deadline.inSeconds(5), 200, () -> {
            ui.runLoopUntil(closed);
            return "closed";
        }, this::lateEnd);

        assertEquals(Outcome.Kind.DIALOG_OPEN, outcome.getKind());
        assertNull(outcome.getValue());

        closed.set(true);
        assertTrue(lateEndCalled.await(5, TimeUnit.SECONDS));
        assertNull("the action ended without an exception", lateEnd.get());
    }

    @Test
    public void testAct_dialogOpen_failsLater() throws Exception {

        AtomicBoolean closed = new AtomicBoolean();

        Outcome<String> outcome = UiThread.act(ui, Deadline.inSeconds(5), 200, () -> {
            ui.runLoopUntil(closed);
            throw new IllegalStateException("listener failed after the dialog");
        }, this::lateEnd);

        assertEquals(Outcome.Kind.DIALOG_OPEN, outcome.getKind());

        closed.set(true);
        assertTrue(lateEndCalled.await(5, TimeUnit.SECONDS));
        assertEquals("listener failed after the dialog", lateEnd.get().getMessage());
    }

    /**
     * The UI thread is busy longer than the deadline: the action never runs,
     * also not after the UI thread is free again. A repeated call does not act
     * twice.
     */
    @Test
    public void testAct_notExecuted() throws Exception {

        AtomicInteger runs = new AtomicInteger();
        ui.execute(() -> sleep(2500));

        Outcome<String> outcome = UiThread.act(ui, Deadline.inSeconds(1), 200, () -> {
            runs.incrementAndGet();
            return "clicked";
        }, this::lateEnd);

        assertEquals(Outcome.Kind.NOT_EXECUTED, outcome.getKind());
        assertEquals(1, outcome.getSeconds());

        ui.awaitIdle();
        assertEquals("the action never runs", 0, runs.get());
        assertSame("no late end", NO_LATE_END, lateEnd.get());
    }

    /**
     * The action itself keeps the UI thread busy longer than the deadline.
     */
    @Test
    public void testAct_stillRunning() throws Exception {

        Outcome<String> outcome = UiThread.act(ui, Deadline.inSeconds(1), 200, () -> {
            sleep(2000);
            return "slow";
        }, this::lateEnd);

        assertEquals(Outcome.Kind.STILL_RUNNING, outcome.getKind());
        assertEquals(1, outcome.getSeconds());

        assertTrue(lateEndCalled.await(5, TimeUnit.SECONDS));
        assertNull(lateEnd.get());
    }

    @Test
    public void testRead_busy() throws Exception {

        AtomicInteger runs = new AtomicInteger();
        ui.execute(() -> sleep(2000));

        try {
            UiThread.read(ui, Deadline.inSeconds(1), () -> runs.incrementAndGet());
            fail("UiThreadBusyException expected");
        } catch (UiThreadBusyException e) {
            assertEquals("UI thread busy for 1 s", e.getMessage());
        }

        ui.awaitIdle();
        assertEquals("code after the deadline is skipped", 0, runs.get());
    }

    private void lateEnd(Throwable aThrowable) {
        lateEnd.set(aThrowable);
        lateEndCalled.countDown();
    }

    private static void sleep(long aMillis) {
        try {
            Thread.sleep(aMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * A thread in place of the UI thread: runs the passed code in order, like
     * Display.asyncExec.
     */
    private static final class FakeUiThread implements Executor {

        private final BlockingQueue<Runnable> queue = new LinkedBlockingQueue<Runnable>();
        private final Thread thread;
        private volatile boolean stopped;

        FakeUiThread() {
            thread = new Thread(() -> {
                while (!stopped) {
                    runNext(100);
                }
            }, "FakeUiThread");
            thread.setDaemon(true);
            thread.start();
        }

        @Override
        public void execute(Runnable aRunnable) {
            queue.add(aRunnable);
        }

        /**
         * A nested event loop, like the one of a modal dialog.
         */
        void runLoopUntil(AtomicBoolean aClosed) {
            while (!aClosed.get() && !stopped) {
                runNext(20);
            }
        }

        /**
         * Waits, until all code, that is passed so far, has run.
         */
        void awaitIdle() throws InterruptedException {
            CountDownLatch idle = new CountDownLatch(1);
            execute(idle::countDown);
            assertTrue(idle.await(10, TimeUnit.SECONDS));
        }

        void stop() {
            stopped = true;
        }

        private void runNext(long aMillis) {
            try {
                Runnable next = queue.poll(aMillis, TimeUnit.MILLISECONDS);
                if (next != null) {
                    next.run();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
