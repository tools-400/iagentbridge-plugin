/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.swt;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;

/**
 * Keeps the shells of the test instance behind the windows of the developer,
 * while an action runs.
 * <p>
 * A dialog, that an action opens, e.g. with a click, lies on top of all other
 * windows, although the test instance does not get the foreground: the keyboard
 * stays with the window of the developer. The guard remembers at the start of
 * an action, whether a window of another process is in the foreground. If so,
 * it puts the active shell of the test instance behind all windows, without
 * activating it, after each activation of a shell during the action and once
 * more at its end. It does so with asyncExec, after Shell.open() has finished.
 * <p>
 * SWT has no public API for it: the guard calls GetForegroundWindow,
 * GetWindowThreadProcessId and SetWindowPos of the internal class
 * org.eclipse.swt.internal.win32.OS by reflection. If they are missing, it does
 * nothing. Used in the UI thread only.
 */
public final class ForegroundGuard {

    private static final String OS_CLASS = "org.eclipse.swt.internal.win32.OS"; //$NON-NLS-1$

    /** HWND_BOTTOM: behind all other windows */
    private static final long HWND_BOTTOM = 1;

    /** SWP_NOSIZE | SWP_NOMOVE | SWP_NOACTIVATE */
    private static final int SWP_FLAGS = 0x1 | 0x2 | 0x10;

    private static final ForegroundGuard NONE = new ForegroundGuard(null, null, null);

    private final Display display;
    private final Method setWindowPos;
    private final Field handle;
    private final Listener listener;

    private ForegroundGuard(Display aDisplay, Method aSetWindowPos, Field aHandle) {
        display = aDisplay;
        setWindowPos = aSetWindowPos;
        handle = aHandle;
        if (aDisplay == null) {
            listener = null;
        } else {
            listener = anEvent -> {
                if (anEvent.widget instanceof Shell) {
                    display.asyncExec(this::toBack);
                }
            };
            aDisplay.addFilter(SWT.Activate, listener);
        }
    }

    /**
     * Starts a guard for an action.
     *
     * @param aDisplay - the display of the test instance
     * @return the guard; it does nothing, if the window in the foreground
     *         belongs to the test instance, or if the internal API is missing
     */
    public static ForegroundGuard start(Display aDisplay) {

        try {
            Class<?> os = Class.forName(OS_CLASS);
            Method getForeground = os.getMethod("GetForegroundWindow"); //$NON-NLS-1$
            Method getProcess = os.getMethod("GetWindowThreadProcessId", long.class, int[].class); //$NON-NLS-1$
            Method setWindowPos = os.getMethod("SetWindowPos", long.class, long.class, int.class, int.class, int.class, int.class, int.class); //$NON-NLS-1$
            Field handle = Control.class.getField("handle"); //$NON-NLS-1$

            long foreground = ((Number)getForeground.invoke(null)).longValue();
            Shell[] shells = aDisplay.getShells();
            if (foreground == 0 || shells.length == 0) {
                return NONE;
            }
            long ownWindow = ((Number)handle.get(shells[0])).longValue();
            if (process(getProcess, foreground) == process(getProcess, ownWindow)) {
                return NONE;
            }

            return new ForegroundGuard(aDisplay, setWindowPos, handle);

        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return NONE;
        }
    }

    /**
     * Stops the guard: puts the active shell of the test instance behind all
     * windows once more and stops watching.
     */
    public void stop() {
        if (listener == null || display.isDisposed()) {
            return;
        }
        display.removeFilter(SWT.Activate, listener);
        toBack();
    }

    /**
     * Returns whether the guard watches the activation of shells.
     *
     * @return <code>true</code>, if a window of another process was in the
     *         foreground at the start
     */
    public boolean isActive() {
        return listener != null;
    }

    /**
     * Puts the active shell of the test instance, and with it its owner, behind
     * all windows, without activating it.
     */
    private void toBack() {

        Shell active = display.getActiveShell();
        if (active == null || active.isDisposed()) {
            return;
        }

        try {
            setWindowPos.invoke(null, Long.valueOf(((Number)handle.get(active)).longValue()), Long.valueOf(HWND_BOTTOM), Integer.valueOf(0),
                Integer.valueOf(0), Integer.valueOf(0), Integer.valueOf(0), Integer.valueOf(SWP_FLAGS));
        } catch (ReflectiveOperationException | RuntimeException e) {
            // The guard is a help only; without it the shell stays on top.
        }
    }

    private static int process(Method aGetProcess, long aWindow) throws ReflectiveOperationException {
        int[] process = new int[1];
        aGetProcess.invoke(null, Long.valueOf(aWindow), process);
        return process[0];
    }
}
