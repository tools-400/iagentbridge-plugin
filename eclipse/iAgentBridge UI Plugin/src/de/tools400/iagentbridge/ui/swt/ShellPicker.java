/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.swt;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Widget;

/**
 * Finds shells: the active one, one by reference or title, all visible ones.
 * Used in the UI thread only.
 */
public final class ShellPicker {

    /** Value of the argument 'shell' for all visible shells */
    public static final String ALL = "all"; //$NON-NLS-1$

    private static final int MODAL = SWT.APPLICATION_MODAL | SWT.PRIMARY_MODAL | SWT.SYSTEM_MODAL;

    private ShellPicker() {
    }

    /**
     * Returns the active shell: the one, that the operating system shows as
     * active, or - if RDi is not in the foreground - the deepest visible shell,
     * modal shells first.
     *
     * @param aDisplay - the display
     * @return active shell or <code>null</code>, if no shell is visible
     */
    public static Shell activeShell(Display aDisplay) {

        Shell active = aDisplay.getActiveShell();
        if (active != null && active.isVisible()) {
            return active;
        }

        Shell best = null;
        int bestScore = -1;
        for (Shell shell : visibleShells(aDisplay)) {
            int depth = 0;
            for (Composite parent = shell.getParent(); parent != null; parent = parent.getParent()) {
                depth++;
            }
            int score = depth * 2 + (isModal(shell) ? 1 : 0);
            if (score > bestScore) {
                best = shell;
                bestScore = score;
            }
        }

        return best;
    }

    /**
     * Returns a visible shell by its reference or its title.
     *
     * @param aDisplay - the display
     * @param aRefOrTitle - reference, e.g. 'w1', or title of the shell
     * @return the shell
     * @throws IllegalArgumentException if no visible shell has the reference or
     *         the title
     */
    public static Shell find(Display aDisplay, String aRefOrTitle) {

        for (Shell shell : visibleShells(aDisplay)) {
            if (aRefOrTitle.equals(shell.getText())) {
                return shell;
            }
        }

        if (aRefOrTitle.matches("w[0-9]+")) { //$NON-NLS-1$
            Widget widget = WidgetRefs.get(aRefOrTitle);
            if (widget instanceof Shell && ((Shell)widget).isVisible()) {
                return (Shell)widget;
            }
        }

        throw new IllegalArgumentException("No visible shell '" + aRefOrTitle + "'. Take a snapshot with shell 'all' to see the shells."); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /**
     * Returns the visible shells.
     *
     * @param aDisplay - the display
     * @return visible shells
     */
    public static List<Shell> visibleShells(Display aDisplay) {

        List<Shell> shells = new ArrayList<Shell>();
        for (Shell shell : aDisplay.getShells()) {
            if (!shell.isDisposed() && shell.isVisible()) {
                shells.add(shell);
            }
        }

        return shells;
    }

    /**
     * Returns whether a shell is modal.
     *
     * @param aShell - the shell
     * @return <code>true</code>, if it is modal
     */
    public static boolean isModal(Shell aShell) {
        return (aShell.getStyle() & MODAL) != 0;
    }
}
