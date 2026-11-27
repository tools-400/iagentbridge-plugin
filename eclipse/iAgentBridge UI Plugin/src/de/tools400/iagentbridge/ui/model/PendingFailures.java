/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Failures of actions, that ended after their tool had returned, e.g. after a
 * dialog, that the action opened, was closed. The next UI tool reports them in
 * its first lines.
 */
public final class PendingFailures {

    private final List<String> messages = new ArrayList<String>();

    /**
     * Adds the failure of an action.
     *
     * @param anAction - the action, e.g. 'ui_click w17'
     * @param aFailure - its exception
     */
    public synchronized void add(String anAction, Throwable aFailure) {
        messages.add(Hints.after("The earlier action " + anAction + " failed: " + aFailure, Hints.RAN_IN_PART)); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /**
     * Returns the failures and forgets them.
     *
     * @return one line per failure, each ending with a line feed, or an empty
     *         string
     */
    public synchronized String take() {

        StringBuilder text = new StringBuilder();
        for (String message : messages) {
            text.append(message).append('\n');
        }
        messages.clear();

        return text.toString();
    }
}
