/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.swt;

import de.tools400.iagentbridge.ui.model.Hints;

/**
 * A listener of an action failed, and the handler of the workbench took the
 * failure, so that it did not reach the action. The cause is the failure.
 */
public class ListenerFailedException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param aFailure - the failure of the listener
     */
    public ListenerFailedException(Throwable aFailure) {
        super(Hints.after("A listener of the action failed: " + aFailure, Hints.RAN_IN_PART), aFailure); //$NON-NLS-1$
    }
}
