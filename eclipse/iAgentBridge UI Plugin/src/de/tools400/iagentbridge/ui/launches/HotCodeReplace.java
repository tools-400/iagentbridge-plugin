/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.launches;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Map;
import java.util.WeakHashMap;

import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaHotCodeReplaceListener;
import org.eclipse.jdt.debug.core.JDIDebugModel;

/**
 * Remembers the last result of hot code replace of each debug target, after the
 * first debug launch of start_launch or the first call of list_launches. JDT
 * replaces the classes after the build, before get_problems returns.
 */
public final class HotCodeReplace {

    /**
     * Results of a run, that belong together, follow each other this closely
     */
    private static final long SAME_RUN_MILLIS = 2000;

    private static final Map<IDebugTarget, Result> RESULTS = new WeakHashMap<IDebugTarget, Result>();

    private static long registered;

    private HotCodeReplace() {
    }

    /**
     * Registers the listener, once.
     */
    public static synchronized void register() {
        if (registered != 0) {
            return;
        }
        registered = System.currentTimeMillis();
        JDIDebugModel.addHotCodeReplaceListener(new Listener());
    }

    /**
     * Describes the last result of hot code replace of a debug launch.
     *
     * @param aLaunch - the launch
     * @param aStartTime - its start in milliseconds since 1970, or 0
     * @return e.g. 'succeeded at 12:00:01', or <code>null</code> for a run
     *         launch
     */
    public static synchronized String describe(ILaunch aLaunch, long aStartTime) {

        for (IDebugTarget target : aLaunch.getDebugTargets()) {
            Result result = RESULTS.get(target);
            if (result != null) {
                return result.toString();
            }
        }

        if (aLaunch.getDebugTargets().length == 0) {
            return null;
        }
        return aStartTime != 0 && aStartTime >= registered ? "none yet" : "unknown - only results after the start of the listener are known"; //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static synchronized void add(IJavaDebugTarget aTarget, String aKind, String aReason) {

        long now = System.currentTimeMillis();
        Result previous = RESULTS.get(aTarget);
        boolean sameRun = previous != null && now - previous.time < SAME_RUN_MILLIS;
        RESULTS.put(aTarget, new Result(combine(sameRun ? previous.kind : null, aKind), now, aReason));
    }

    /**
     * Returns the kinds of results of one replacement, each once.
     *
     * @param aPrevious - the kinds so far, e.g. 'obsolete methods', or
     *        <code>null</code>
     * @param aKind - the new kind, e.g. 'succeeded'
     * @return e.g. 'obsolete methods, succeeded'
     */
    static String combine(String aPrevious, String aKind) {
        if (aPrevious == null) {
            return aKind;
        }
        return Arrays.asList(aPrevious.split(", ")).contains(aKind) ? aPrevious : aPrevious + ", " + aKind; //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static final class Result {

        private final String kind;
        private final long time;
        private final String reason;

        Result(String aKind, long aTime, String aReason) {
            kind = aKind;
            time = aTime;
            reason = aReason;
        }

        @Override
        public String toString() {
            return kind + " at " + new SimpleDateFormat("HH:mm:ss").format(Long.valueOf(time)) + (reason == null ? "" : ": " + reason); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        }
    }

    private static final class Listener implements IJavaHotCodeReplaceListener {

        @Override
        public void hotCodeReplaceSucceeded(IJavaDebugTarget aTarget) {
            add(aTarget, "succeeded", null); //$NON-NLS-1$
        }

        @Override
        public void hotCodeReplaceFailed(IJavaDebugTarget aTarget, DebugException anException) {
            add(aTarget, "failed", anException == null ? null : anException.getMessage()); //$NON-NLS-1$
        }

        @Override
        public void obsoleteMethods(IJavaDebugTarget aTarget) {
            add(aTarget, "obsolete methods", null); //$NON-NLS-1$
        }
    }
}
