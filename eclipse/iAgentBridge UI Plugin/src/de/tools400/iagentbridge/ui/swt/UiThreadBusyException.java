/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.swt;

/**
 * The UI thread did not run a request within the timeout.
 */
public class UiThreadBusyException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param aTimeoutSeconds - the timeout, that has expired
     */
    public UiThreadBusyException(int aTimeoutSeconds) {
        super("UI thread busy for " + aTimeoutSeconds + " s"); //$NON-NLS-1$ //$NON-NLS-2$
    }
}
