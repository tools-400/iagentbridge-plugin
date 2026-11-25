/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.swt;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import org.eclipse.swt.widgets.Display;

/**
 * Catches the exceptions and errors of the listeners of an action.
 * <p>
 * Since the SWT of RDi 9.8, notifyListeners passes an exception of a listener
 * to the runtime exception handler of the display and an error to its error
 * handler. The workbench sets handlers, that do not throw them again, so that
 * the action would not see them. For the time of the action this class puts
 * handlers in front, that hold the failures of the own calls of the action and
 * pass every failure on to the handler before, so that the workbench still
 * handles it.
 * <p>
 * A failure comes from a nested event loop and is not held, if a frame of
 * Display.readAndDispatch, Synchronizer.runAsyncMessages or Display.runTimer
 * lies on the stack above the depth, that the action had at its start: then it
 * was thrown e.g. by the next action in a dialog, that the action opened, or by
 * other code of the UI thread, also in the loop of a native dialog, which does
 * not call readAndDispatch. The depth, not the name of a method, finds the
 * frame of the action, because all actions run the same code. Nothing is held,
 * while a native dialog is open: then the action waits in Windows, and the loop
 * of the dialog runs other code without such a frame, e.g. the Settings
 * listeners.
 * <p>
 * The SWT of RDi 9.6 has no such handlers: there the exception reaches the
 * action anyway. The handlers are set by reflection, because the plug-in is
 * built against the SWT of RDi 9.6. Used in the UI thread only.
 */
public final class ListenerFailures {

    private static final String[][] NESTED_LOOPS = { //
        { "org.eclipse.swt.widgets.Display", "readAndDispatch" }, //$NON-NLS-1$ //$NON-NLS-2$
        { "org.eclipse.swt.widgets.Synchronizer", "runAsyncMessages" }, //$NON-NLS-1$ //$NON-NLS-2$
        { "org.eclipse.swt.widgets.Display", "runTimer" } }; //$NON-NLS-1$ //$NON-NLS-2$

    private ListenerFailures() {
    }

    /**
     * Runs an action with the handlers in front.
     *
     * @param aDisplay - the display
     * @param anAction - the action
     * @return result of the action
     * @throws ListenerFailedException if a listener of the own calls of the
     *         action failed, and the failure did not reach the action
     * @throws Exception exception of the action
     */
    @SuppressWarnings("unchecked")
    public static <T> T run(Display aDisplay, Callable<T> anAction) throws Exception {

        if (aDisplay == null) {
            // Tests outside of RDi, without a display.
            return anAction.call();
        }

        Method getRuntime;
        Method setRuntime;
        Method getError;
        Method setError;
        try {
            getRuntime = Display.class.getMethod("getRuntimeExceptionHandler"); //$NON-NLS-1$
            setRuntime = Display.class.getMethod("setRuntimeExceptionHandler", Consumer.class); //$NON-NLS-1$
            getError = Display.class.getMethod("getErrorHandler"); //$NON-NLS-1$
            setError = Display.class.getMethod("setErrorHandler", Consumer.class); //$NON-NLS-1$
        } catch (NoSuchMethodException e) {
            // The SWT of RDi 9.6: the failure reaches the action.
            return anAction.call();
        }

        int depth = new Throwable().getStackTrace().length;
        List<Throwable> held = new ArrayList<Throwable>();
        Consumer<RuntimeException> runtimeBefore = (Consumer<RuntimeException>)getRuntime.invoke(aDisplay);
        Consumer<Error> errorBefore = (Consumer<Error>)getError.invoke(aDisplay);
        Consumer<RuntimeException> runtimeHandler = anException -> {
            hold(aDisplay, anException, depth, held);
            runtimeBefore.accept(anException);
        };
        Consumer<Error> errorHandler = anError -> {
            hold(aDisplay, anError, depth, held);
            errorBefore.accept(anError);
        };

        setRuntime.invoke(aDisplay, runtimeHandler);
        setError.invoke(aDisplay, errorHandler);
        T result;
        try {
            result = anAction.call();
        } finally {
            setRuntime.invoke(aDisplay, runtimeBefore);
            setError.invoke(aDisplay, errorBefore);
        }

        if (!held.isEmpty()) {
            throw new ListenerFailedException(held.get(0));
        }

        return result;
    }

    /**
     * Holds a failure, unless it comes from a nested event loop or from the
     * loop of a native dialog.
     */
    private static void hold(Display aDisplay, Throwable aFailure, int aDepth, List<Throwable> aHeld) {

        if (NativeDialogs.find(aDisplay) != null) {
            return;
        }

        StackTraceElement[] stack = new Throwable().getStackTrace();
        for (int i = 0; i < stack.length - aDepth; i++) {
            for (String[] loop : NESTED_LOOPS) {
                if (loop[0].equals(stack[i].getClassName()) && loop[1].equals(stack[i].getMethodName())) {
                    return;
                }
            }
        }

        if (!aHeld.contains(aFailure)) {
            aHeld.add(aFailure);
        }
    }
}
