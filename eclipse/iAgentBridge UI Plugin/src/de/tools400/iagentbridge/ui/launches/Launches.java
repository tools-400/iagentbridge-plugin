/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.launches;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.variables.VariablesPlugin;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.debug.core.model.IThread;

import de.tools400.iagentbridge.ui.forwarding.TestInstance;

/**
 * The launch configurations and launches of the two types, that start_launch
 * starts: Eclipse Applications and JUnit Plug-in Tests.
 */
public final class Launches {

    private static final String ATTR_VM_ARGUMENTS = "org.eclipse.jdt.launching.VM_ARGUMENTS"; //$NON-NLS-1$
    /** Attribute of JDT with the program arguments of a configuration */
    static final String ATTR_PROGRAM_ARGUMENTS = "org.eclipse.jdt.launching.PROGRAM_ARGUMENTS"; //$NON-NLS-1$
    private static final String ATTR_LOCATION = "location"; //$NON-NLS-1$
    private static final String ATTR_CLEAR_WORKSPACE = "clearws"; //$NON-NLS-1$
    private static final String ATTR_ASK_CLEAR = "askclear"; //$NON-NLS-1$

    /**
     * Attribute of a launch with the time of its start
     * (DebugPlugin.ATTR_LAUNCH_TIMESTAMP)
     */
    private static final String ATTR_LAUNCH_TIMESTAMP = "org.eclipse.debug.core.launch.timestamp"; //$NON-NLS-1$

    private Launches() {
    }

    /**
     * Returns the launch configuration of a name.
     *
     * @param aName - name of the configuration
     * @return the configuration
     * @throws IllegalArgumentException if there is none
     * @throws CoreException if the configurations cannot be read
     */
    public static ILaunchConfiguration find(String aName) throws CoreException {
        for (ILaunchConfiguration configuration : getManager().getLaunchConfigurations()) {
            if (configuration.getName().equals(aName)) {
                return configuration;
            }
        }
        throw new IllegalArgumentException("There is no launch configuration \"" + aName + "\". list_launches lists them."); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /**
     * Returns the launch configurations, that start_launch can start.
     *
     * @return configurations of the two types
     * @throws CoreException if the configurations cannot be read
     */
    public static List<ILaunchConfiguration> configurations() throws CoreException {
        List<ILaunchConfiguration> configurations = new ArrayList<ILaunchConfiguration>();
        for (ILaunchConfiguration configuration : getManager().getLaunchConfigurations()) {
            if (info(configuration).isAllowedType()) {
                configurations.add(configuration);
            }
        }
        return configurations;
    }

    /**
     * Returns what start_launch checks of a configuration.
     *
     * @param aConfiguration - the configuration
     * @return the information
     * @throws CoreException if an attribute cannot be read
     */
    public static LaunchInfo info(ILaunchConfiguration aConfiguration) throws CoreException {
        return new LaunchInfo(aConfiguration.getName(), aConfiguration.getType().getIdentifier(), aConfiguration.getAttribute(ATTR_VM_ARGUMENTS, ""), //$NON-NLS-1$
            aConfiguration.getAttribute(ATTR_PROGRAM_ARGUMENTS, ""), aConfiguration.getAttribute(ATTR_LOCATION, (String)null), //$NON-NLS-1$
            aConfiguration.getAttribute(ATTR_CLEAR_WORKSPACE, false), aConfiguration.getAttribute(ATTR_ASK_CLEAR, true));
    }

    /**
     * Returns the workspace of a launch configuration, with its variables
     * resolved.
     *
     * @param aConfiguration - the configuration
     * @return the path or <code>null</code>, if it has none or it contains a
     *         variable, that would ask for a value
     */
    public static String workspace(ILaunchConfiguration aConfiguration) {
        try {
            String location = aConfiguration.getAttribute(ATTR_LOCATION, (String)null);
            if (location == null || StartChecks.hasPrompt(location)) {
                // A variable like ${folder_prompt} would open a dialog.
                return null;
            }
            return VariablesPlugin.getDefault().getStringVariableManager().performStringSubstitution(location);
        } catch (CoreException e) {
            return null;
        }
    }

    /**
     * Returns the launches of the two types, that run.
     *
     * @return launches with a process, that runs
     */
    public static List<ILaunch> running() {
        List<ILaunch> launches = new ArrayList<ILaunch>();
        for (ILaunch launch : getManager().getLaunches()) {
            if (!launch.isTerminated() && isAllowed(launch) && commandLine(launch) != null) {
                launches.add(launch);
            }
        }
        return launches;
    }

    /**
     * Describes a launch without Eclipse types.
     *
     * @param aLaunch - the launch
     * @return the description
     */
    public static RunningLaunch describe(ILaunch aLaunch) {
        TestInstance instance = instance(aLaunch);
        long start = startTime(aLaunch);
        return new RunningLaunch(aLaunch.getLaunchConfiguration().getName(), aLaunch.getLaunchMode(), instance.getPort(), instance.hasUiTools(),
            start, isSuspended(aLaunch), HotCodeReplace.describe(aLaunch, start));
    }

    /**
     * Returns the instance, that the command line of the launch describes.
     *
     * @param aLaunch - the launch
     * @return the instance; its port is <code>null</code> without
     *         '-Diagentbridge.port'
     */
    public static TestInstance instance(ILaunch aLaunch) {
        String commandLine = commandLine(aLaunch);
        return TestInstance.ofCommandLine(aLaunch.getLaunchConfiguration().getName(), commandLine == null ? "" : commandLine); //$NON-NLS-1$
    }

    /**
     * Returns, whether all processes of a launch ended.
     *
     * @param aLaunch - the launch
     * @return <code>true</code>, if no process runs
     */
    public static boolean hasEnded(ILaunch aLaunch) {
        for (IProcess process : aLaunch.getProcesses()) {
            if (!process.isTerminated()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the exit code of the first process of a launch.
     *
     * @param aLaunch - the launch
     * @return exit code or <code>null</code>
     */
    public static Integer exitValue(ILaunch aLaunch) {
        for (IProcess process : aLaunch.getProcesses()) {
            try {
                return Integer.valueOf(process.getExitValue());
            } catch (DebugException e) {
                // It runs.
            }
        }
        return null;
    }

    /**
     * Returns, whether a thread of a debug launch is suspended.
     *
     * @param aLaunch - the launch
     * @return <code>true</code>, if one is
     */
    public static boolean isSuspended(ILaunch aLaunch) {
        for (IDebugTarget target : aLaunch.getDebugTargets()) {
            try {
                for (IThread thread : target.getThreads()) {
                    if (thread.isSuspended()) {
                        return true;
                    }
                }
            } catch (DebugException e) {
                // A target, that ended meanwhile.
            }
        }
        return false;
    }

    private static String commandLine(ILaunch aLaunch) {
        for (IProcess process : aLaunch.getProcesses()) {
            String commandLine = process.getAttribute(IProcess.ATTR_CMDLINE);
            if (!process.isTerminated() && commandLine != null) {
                return commandLine;
            }
        }
        return null;
    }

    private static long startTime(ILaunch aLaunch) {
        try {
            String time = aLaunch.getAttribute(ATTR_LAUNCH_TIMESTAMP);
            return time == null ? 0 : Long.parseLong(time);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static boolean isAllowed(ILaunch aLaunch) {
        try {
            return aLaunch.getLaunchConfiguration() != null && info(aLaunch.getLaunchConfiguration()).isAllowedType();
        } catch (CoreException e) {
            return false;
        }
    }

    private static ILaunchManager getManager() {
        return DebugPlugin.getDefault().getLaunchManager();
    }
}
