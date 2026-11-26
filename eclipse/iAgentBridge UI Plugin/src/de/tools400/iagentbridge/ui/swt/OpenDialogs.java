/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.swt;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

/**
 * Closes the open dialogs, before the workbench closes: IWorkbench.close()
 * waits in an event loop for the operation of a dialog, e.g. the connect of RSE
 * with its progress dialog, which waits for the sign-on, so the test instance
 * would not end.
 */
public final class OpenDialogs {

    /**
     * Styles of a modal shell; only a modal dialog runs an event loop of its
     * own
     */
    private static final int MODAL = SWT.APPLICATION_MODAL | SWT.PRIMARY_MODAL | SWT.SYSTEM_MODAL;

    private OpenDialogs() {
    }

    /**
     * Closes the modal shells of a display like the close button of their title
     * bar, the children before their parent: a JFace dialog answers the event
     * Close with Cancel, but it returns OK, if its shell is only disposed
     * together with its parent. Shells, that are not modal, e.g. the hidden
     * shells of the workbench, stay. Runs in the UI thread.
     *
     * @param aDisplay - the display
     */
    public static void close(Display aDisplay) {

        if (aDisplay.isDisposed()) {
            return;
        }

        for (Shell shell : aDisplay.getShells()) {
            if (!shell.isDisposed() && shell.getParent() == null) {
                close(shell);
            }
        }
    }

    private static void close(Shell aShell) {

        // getShells() returns all descendants: visit the direct children only.
        for (Shell child : aShell.getShells()) {
            if (!child.isDisposed() && child.getParent() == aShell) {
                close(child);
            }
        }

        if (!aShell.isDisposed() && (aShell.getStyle() & MODAL) != 0) {
            aShell.close();
        }
    }
}
