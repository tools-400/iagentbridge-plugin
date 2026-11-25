/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.model;

import java.util.concurrent.TimeUnit;

/**
 * The time, that a call of a UI tool has for all its work in the UI thread. All
 * steps of a call share it, so that the forwarding never gives up before the
 * test instance.
 */
public final class Deadline {

    private final int seconds;
    private final long end;

    private Deadline(int aSeconds) {
        seconds = aSeconds;
        end = System.nanoTime() + TimeUnit.SECONDS.toNanos(aSeconds);
    }

    /**
     * Returns a deadline, that ends in some seconds.
     *
     * @param aSeconds - seconds from now
     * @return the deadline
     */
    public static Deadline inSeconds(int aSeconds) {
        return new Deadline(aSeconds);
    }

    /**
     * Returns the seconds, that the deadline had at its start, for messages.
     *
     * @return seconds
     */
    public int getSeconds() {
        return seconds;
    }

    /**
     * Returns the time, that is left.
     *
     * @return milliseconds, at least 0
     */
    public long remainingMillis() {
        return Math.max(0, TimeUnit.NANOSECONDS.toMillis(end - System.nanoTime()));
    }

    /**
     * Returns whether the time is over.
     *
     * @return <code>true</code>, if no time is left
     */
    public boolean isExpired() {
        return remainingMillis() == 0;
    }
}
