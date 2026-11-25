/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.swt;

/**
 * An action is not possible in the current state of the user interface, e.g.
 * its widget is disabled or a modal dialog blocks its shell. Nothing was done.
 */
public class ActionRejectedException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param aReason - why the action is not possible
     */
    public ActionRejectedException(String aReason) {
        super(aReason);
    }
}
