/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.swt;

import java.lang.reflect.Method;

import org.eclipse.swt.widgets.Dialog;
import org.eclipse.swt.widgets.Display;

/**
 * Detects an open native dialog of the operating system, e.g. a FileDialog or a
 * MessageBox. Such a dialog is no SWT widget; a snapshot cannot read it.
 * <p>
 * SWT has no public API for it. The native dialogs of SWT register themselves
 * in open() with the internal method Display.getModalDialog(), which this class
 * calls by reflection; if the method is missing, it reports no dialog. Used in
 * the UI thread only.
 */
public final class NativeDialogs {

    private NativeDialogs() {
    }

    /**
     * Returns the open native dialog.
     *
     * @param aDisplay - the display
     * @return the dialog, e.g. a FileDialog, whose getText() is its title, or
     *         <code>null</code>, if no native dialog is open
     */
    public static Dialog find(Display aDisplay) {
        try {
            Method method = Display.class.getDeclaredMethod("getModalDialog"); //$NON-NLS-1$
            method.setAccessible(true);
            Object dialog = method.invoke(aDisplay);
            return dialog instanceof Dialog ? (Dialog)dialog : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
