/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.swt;

import java.lang.reflect.Method;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Dialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

/**
 * Tells, whether a modal dialog blocks the shell of a control, so that a user
 * could not operate it. Events sent with notifyListeners would reach it anyway.
 * <ul>
 * <li>A native dialog of the operating system, e.g. a FileDialog, blocks all
 * shells. The internal method Control.isActive() of SWT does not see this for
 * the parent shell of the native dialog.</li>
 * <li>Otherwise SWT decides with Control.isActive(), called by reflection: it
 * covers the styles APPLICATION_MODAL, SYSTEM_MODAL and PRIMARY_MODAL.
 * Shell.isEnabled() does not show the block.</li>
 * <li>If the method is missing, a shell is free, if it is the deepest visible
 * shell with APPLICATION_MODAL or SYSTEM_MODAL, or one of its descendants.</li>
 * </ul>
 * Used in the UI thread only.
 */
public final class ShellLock {

    private static final int APPLICATION_OR_SYSTEM_MODAL = SWT.APPLICATION_MODAL | SWT.SYSTEM_MODAL;

    private ShellLock() {
    }

    /**
     * Returns why a control cannot be operated, because a dialog blocks it.
     *
     * @param aControl - the control
     * @return the reason or <code>null</code>, if no dialog blocks it
     */
    public static String reason(Control aControl) {

        Display display = aControl.getDisplay();
        Dialog nativeDialog = NativeDialogs.find(display);
        if (nativeDialog != null) {
            return "The native dialog \"" + nativeDialog.getText() + "\" is open. It blocks the test instance, and the UI tools " //$NON-NLS-1$ //$NON-NLS-2$
                + "cannot operate it; it must be closed by hand."; //$NON-NLS-1$
        }

        Boolean active = isActive(aControl);
        if (active == null) {
            active = Boolean.valueOf(isFreeByModalShells(aControl));
        }
        if (active.booleanValue()) {
            return null;
        }

        Shell blocking = deepestModalShell(display);
        return "The shell \"" + aControl.getShell().getText() + "\" is blocked by the modal dialog " //$NON-NLS-1$ //$NON-NLS-2$
            + (blocking == null ? "" : "\"" + blocking.getText() + "\" ") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            + "[" + (blocking == null ? "?" : WidgetRefs.of(blocking)) + "]. Operate or close the dialog first."; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    /**
     * Calls the internal method Control.isActive() of SWT.
     *
     * @return its result or <code>null</code>, if the method is missing
     */
    private static Boolean isActive(Control aControl) {
        try {
            Method method = Control.class.getDeclaredMethod("isActive"); //$NON-NLS-1$
            method.setAccessible(true);
            return (Boolean)method.invoke(aControl);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /**
     * The rule with public API: a shell is free, if it is the deepest visible
     * application or system modal shell, or one of its descendants, or if no
     * such shell is visible.
     */
    private static boolean isFreeByModalShells(Control aControl) {

        Shell modal = deepestModalShell(aControl.getDisplay());
        if (modal == null) {
            return true;
        }

        for (Composite shell = aControl.getShell(); shell != null; shell = shell.getParent()) {
            if (shell == modal) {
                return true;
            }
        }

        return false;
    }

    private static Shell deepestModalShell(Display aDisplay) {

        Shell deepest = null;
        int deepestDepth = -1;
        for (Shell shell : ShellPicker.visibleShells(aDisplay)) {
            if ((shell.getStyle() & APPLICATION_OR_SYSTEM_MODAL) == 0) {
                continue;
            }
            int depth = 0;
            for (Composite parent = shell.getParent(); parent != null; parent = parent.getParent()) {
                depth++;
            }
            if (depth > deepestDepth) {
                deepest = shell;
                deepestDepth = depth;
            }
        }

        return deepest;
    }
}
