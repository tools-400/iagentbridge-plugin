/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.launches;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.tools400.iagentbridge.ui.model.Hints;

/**
 * The checks of start_launch before the launch, without Eclipse types. Each
 * would otherwise end in a dialog of the RDi, in which the developer works, or
 * in a test instance, that the UI tools cannot reach.
 */
public final class StartChecks {

    /**
     * Variables, that ask for a value in a dialog, e.g. ${string_prompt:Name}
     */
    private static final Pattern PROMPT = Pattern.compile("\\$\\{\\w*_prompt(:[^}]*)?\\}"); //$NON-NLS-1$

    private StartChecks() {
    }

    /**
     * Checks, whether a launch configuration may start now.
     *
     * @param anInfo - the configuration
     * @param aRunning - the launches, that run
     * @param aWaiting - name of a start, that still waits, or <code>null</code>
     * @param aPortAnswers - <code>true</code>, if the port of the configuration
     *        answers already
     * @return the reason, why it may not start, or <code>null</code>
     */
    public static String check(LaunchInfo anInfo, List<RunningLaunch> aRunning, String aWaiting, boolean aPortAnswers) {

        String name = quote(anInfo.getName());
        if (!anInfo.isAllowedType()) {
            return name + " is a launch configuration of the type " + anInfo.getType() //$NON-NLS-1$
                + ". start_launch starts only Eclipse Applications and JUnit Plug-in Tests."; //$NON-NLS-1$
        }

        String location = anInfo.getLocation() == null ? "" : anInfo.getLocation(); //$NON-NLS-1$
        Matcher prompt = PROMPT.matcher(anInfo.getVmArguments() + " " + anInfo.getProgramArguments() + " " + location); //$NON-NLS-1$ //$NON-NLS-2$
        if (prompt.find()) {
            return name + " asks for a value at the start (" + prompt.group() //$NON-NLS-1$
                + "), which would open a dialog in this RDi. " + Hints.askDeveloper("start it by hand"); //$NON-NLS-1$ //$NON-NLS-2$
        }

        if (anInfo.asksToClear()) {
            return name + " asks, whether its workspace shall be cleared, which would open a dialog in this RDi. " //$NON-NLS-1$
                + Hints.askDeveloper("start it by hand or to turn off the question in the launch configuration"); //$NON-NLS-1$
        }

        if (aWaiting != null) {
            return "A start of " + quote(aWaiting) + " is still waiting - probably for an answer in a dialog of RDi. " //$NON-NLS-1$ //$NON-NLS-2$
                + Hints.askDeveloper("answer the dialog"); //$NON-NLS-1$
        }

        boolean uiTools = anInfo.getInstance().hasUiTools();
        for (RunningLaunch launch : aRunning) {
            if (launch.getName().equals(anInfo.getName())) {
                return name + " runs already. Stop it with stop_launch first."; //$NON-NLS-1$
            }
            if (uiTools && launch.hasUiTools()) {
                return "The test instance " + quote(launch.getName()) //$NON-NLS-1$
                    + " with the UI tools runs already; the UI tools work with one only. Stop it with stop_launch first."; //$NON-NLS-1$
            }
        }

        Integer port = anInfo.getInstance().getPort();
        if (aPortAnswers && port != null) {
            return "Port " + port + " of " + name + " is in use by another process. " + Hints.FOREIGN_PROCESS; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        }

        return null;
    }

    /**
     * Checks the workspace of a launch configuration, after
     * {@link #check(LaunchInfo, List, String, boolean)} found nothing: a
     * workspace, that another process has locked, cannot be used by a second
     * instance, and PDE reports it in a dialog (read in the code of
     * LauncherUtils, not tested).
     *
     * @param anInfo - the configuration
     * @param aWorkspace - its workspace, resolved
     * @param aLocked - <code>true</code>, if the workspace is locked
     * @return the reason, why it may not start, or <code>null</code>
     */
    public static String checkWorkspace(LaunchInfo anInfo, String aWorkspace, boolean aLocked) {
        if (aLocked) {
            return quote(anInfo.getName()) + " uses the workspace " + aWorkspace //$NON-NLS-1$
                + ", which another process has locked. " + Hints.FOREIGN_PROCESS; //$NON-NLS-1$
        }
        return null;
    }

    /**
     * Returns whether a text contains a variable, that asks for a value in a
     * dialog.
     *
     * @param aText - the text, e.g. an attribute of a launch configuration
     * @return <code>true</code>, if it contains e.g. ${string_prompt}
     */
    public static boolean hasPrompt(String aText) {
        return PROMPT.matcher(aText).find();
    }

    private static String quote(String aName) {
        return "\"" + aName + "\""; //$NON-NLS-1$ //$NON-NLS-2$
    }
}
