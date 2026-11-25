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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.swt.widgets.Display;

/**
 * Runs code in the UI thread without blocking the thread of the MCP server for
 * longer than a timeout. The server handles one request at a time, so a tool,
 * that waits for a busy UI thread with Display.syncExec, would block it
 * completely.
 */
public final class UiThread {

    private UiThread() {
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

        AtomicReference<T> result = new AtomicReference<T>();
        AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        AtomicBoolean expired = new AtomicBoolean();
        CountDownLatch done = new CountDownLatch(1);

        aDisplay.asyncExec(() -> {
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

        if (!done.await(aTimeoutSeconds, TimeUnit.SECONDS)) {
            expired.set(true);
            throw new UiThreadBusyException(aTimeoutSeconds);
        }

        Throwable e = failure.get();
        if (e instanceof Exception) {
            throw (Exception)e;
        }
        if (e instanceof Error) {
            throw (Error)e;
        }

        return result.get();
    }
}
