/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.swt;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.widgets.Display;

import de.tools400.iagentbridge.ui.model.Deadline;
import de.tools400.iagentbridge.ui.model.Hints;

/**
 * Runs code in the UI thread without blocking the thread of the MCP server for
 * longer than a timeout. The server handles one request at a time, so a tool,
 * that waits for a busy UI thread with Display.syncExec, would block it
 * completely.
 * <p>
 * The methods with an Executor use no type of SWT: the executor passes the code
 * to the UI thread, e.g. Display.asyncExec. So they can be tested outside of
 * RDi.
 */
public final class UiThread {

    private static final int WAITING = 0;
    private static final int STARTED = 1;
    private static final int FINISHED = 2;
    private static final int EXPIRED = 3;
    private static final int ABANDONED = 4;

    private UiThread() {
    }

    /**
     * Returns the executor, that passes code to the UI thread with asyncExec.
     * After the end of the display, e.g. when an action closed the last dialog
     * of a closing workbench, it throws a RejectedExecutionException, which the
     * tools handle without types of SWT.
     *
     * @param aDisplay - the display
     * @return executor
     */
    public static Executor executor(Display aDisplay) {
        return aRunnable -> {
            try {
                aDisplay.asyncExec(aRunnable);
            } catch (SWTException e) {
                if (e.code == SWT.ERROR_DEVICE_DISPOSED) {
                    throw new RejectedExecutionException(displayEnded(), e);
                }
                throw e;
            }
        };
    }

    /**
     * Returns the text for the end of the display.
     *
     * @return text
     */
    static String displayEnded() {
        return "The display of the test instance has ended. " + Hints.INSTANCE_RUNS; //$NON-NLS-1$
    }

    /**
     * Runs code in the UI thread and returns its result. The code is passed
     * with asyncExec; this thread waits at most the timeout for it. Code, that
     * gets its turn after the timeout, is skipped.
     *
     * @param aDisplay - the display
     * @param aTimeoutSeconds - timeout
     * @param aCallable - code, that runs in the UI thread
     * @return result of the code
     * @throws UiThreadBusyException if the UI thread did not run the code
     *         within the timeout
     * @throws Exception exception of the code
     */
    public static <T> T read(Display aDisplay, int aTimeoutSeconds, Callable<T> aCallable) throws Exception {

        if (aDisplay.getThread() == Thread.currentThread()) {
            return aCallable.call();
        }

        return read(executor(aDisplay), Deadline.inSeconds(aTimeoutSeconds), aCallable);
    }

    /**
     * Runs code in the UI thread and returns its result, within the time left
     * of a deadline. Code, that gets its turn after the deadline, is skipped.
     *
     * @param anExecutor - passes the code to the UI thread
     * @param aDeadline - deadline of the call
     * @param aCallable - code, that runs in the UI thread
     * @return result of the code
     * @throws UiThreadBusyException if the UI thread did not run the code
     *         before the deadline
     * @throws Exception exception of the code
     */
    public static <T> T read(Executor anExecutor, Deadline aDeadline, Callable<T> aCallable) throws Exception {

        AtomicReference<T> result = new AtomicReference<T>();
        AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        AtomicBoolean expired = new AtomicBoolean();
        CountDownLatch done = new CountDownLatch(1);

        anExecutor.execute(() -> {
            if (expired.get()) {
                return;
            }
            try {
                result.set(aCallable.call());
            } catch (Throwable e) {
                failure.set(e);
            } finally {
                done.countDown();
            }
        });

        if (!done.await(aDeadline.remainingMillis(), TimeUnit.MILLISECONDS)) {
            expired.set(true);
            throw new UiThreadBusyException(aDeadline.getSeconds());
        }

        return valueOrFailure(result, failure);
    }

    /**
     * Runs an action in the UI thread. Unlike {@link #read}, an action may open
     * a modal dialog, whose event loop runs, until the user or the next tool
     * closes it; then the action does not return for a long time, while the UI
     * thread is not busy.
     * <ul>
     * <li>The action ends within the wait: <code>DONE</code>.</li>
     * <li>Otherwise a probe is passed to the UI thread. It gets its turn, while
     * the action has not returned: <code>DIALOG_OPEN</code>. It gets its turn
     * after the end of the action: DONE.</li>
     * <li>The probe gets no turn before the deadline: the action has started
     * and still runs - <code>STILL_RUNNING</code> - or has not started and
     * never runs - <code>NOT_EXECUTED</code>, so that a repeated call does not
     * act twice.</li>
     * </ul>
     * A state, that changes with compareAndSet only, decides between start and
     * expiry of the action.
     *
     * @param anExecutor - passes the code to the UI thread, in the order of the
     *        calls
     * @param aDeadline - deadline of the call
     * @param aWaitMillis - time to wait for the end of the action, before the
     *        probe is passed
     * @param anAction - the action
     * @param aLateEnd - called in the UI thread, when an action ends, after
     *        this method returned DIALOG_OPEN or STILL_RUNNING, with the
     *        exception of the action or <code>null</code>
     * @return outcome; with DONE the result of the action
     * @throws Exception exception of an action, that ended within the call
     */
    public static <T> Outcome<T> act(Executor anExecutor, Deadline aDeadline, long aWaitMillis, Callable<T> anAction, Consumer<Throwable> aLateEnd)
        throws Exception {

        AtomicInteger state = new AtomicInteger(WAITING);
        AtomicReference<T> result = new AtomicReference<T>();
        AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        CountDownLatch done = new CountDownLatch(1);

        anExecutor.execute(() -> {
            if (!state.compareAndSet(WAITING, STARTED)) {
                return;
            }
            Throwable thrown = null;
            try {
                result.set(anAction.call());
            } catch (Throwable e) {
                thrown = e;
                failure.set(e);
            }
            if (!state.compareAndSet(STARTED, FINISHED)) {
                // The call has returned already.
                aLateEnd.accept(thrown);
            }
            done.countDown();
        });

        if (done.await(Math.min(aWaitMillis, aDeadline.remainingMillis()), TimeUnit.MILLISECONDS)) {
            return Outcome.done(valueOrFailure(result, failure));
        }

        CountDownLatch probe = new CountDownLatch(1);
        anExecutor.execute(probe::countDown);
        boolean answered = probe.await(aDeadline.remainingMillis(), TimeUnit.MILLISECONDS);

        if (state.compareAndSet(WAITING, EXPIRED)) {
            return Outcome.notExecuted(aDeadline.getSeconds());
        }
        if (state.compareAndSet(STARTED, ABANDONED)) {
            return answered ? Outcome.<T> dialogOpen() : Outcome.<T> stillRunning(aDeadline.getSeconds());
        }

        // FINISHED: the action has ended meanwhile.
        done.await();
        return Outcome.done(valueOrFailure(result, failure));
    }

    private static <T> T valueOrFailure(AtomicReference<T> aResult, AtomicReference<Throwable> aFailure) throws Exception {

        Throwable e = aFailure.get();
        if (e instanceof Exception) {
            throw (Exception)e;
        }
        if (e instanceof Error) {
            throw (Error)e;
        }

        return aResult.get();
    }

    /**
     * The outcome of an action.
     *
     * @param <T> - type of the result of the action
     */
    public static final class Outcome<T> {

        /**
         * Kind of the outcome.
         */
        public enum Kind {
            /** The action has ended. */
            DONE,
            /** The action has not returned, but the UI thread answers. */
            DIALOG_OPEN,
            /** The action has started and the UI thread does not answer. */
            STILL_RUNNING,
            /** The action has not started and never runs. */
            NOT_EXECUTED
        }

        private final Kind kind;
        private final T value;
        private final int seconds;

        private Outcome(Kind aKind, T aValue, int aSeconds) {
            kind = aKind;
            value = aValue;
            seconds = aSeconds;
        }

        static <T> Outcome<T> done(T aValue) {
            return new Outcome<T>(Kind.DONE, aValue, 0);
        }

        static <T> Outcome<T> dialogOpen() {
            return new Outcome<T>(Kind.DIALOG_OPEN, null, 0);
        }

        static <T> Outcome<T> stillRunning(int aSeconds) {
            return new Outcome<T>(Kind.STILL_RUNNING, null, aSeconds);
        }

        static <T> Outcome<T> notExecuted(int aSeconds) {
            return new Outcome<T>(Kind.NOT_EXECUTED, null, aSeconds);
        }

        public Kind getKind() {
            return kind;
        }

        /**
         * Returns the result of the action.
         *
         * @return result, if the kind is DONE, otherwise <code>null</code>
         */
        public T getValue() {
            return value;
        }

        /**
         * Returns the seconds of the deadline, for STILL_RUNNING and
         * NOT_EXECUTED.
         *
         * @return seconds
         */
        public int getSeconds() {
            return seconds;
        }
    }
}
