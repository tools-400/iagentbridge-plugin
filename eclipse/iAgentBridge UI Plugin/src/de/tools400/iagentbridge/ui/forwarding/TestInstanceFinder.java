/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.forwarding;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.model.IProcess;

/**
 * Finds the test instances with the UI tools among the launches of this RDi:
 * running launches of the type Eclipse Application, whose process has
 * '-Diagentbridge.uiTools=true' in its command line. The command line shows,
 * what was really started, with all variables resolved.
 */
public final class TestInstanceFinder {

    /** Type of the launch configurations 'Eclipse Application' of PDE */
    private static final String TYPE_ECLIPSE_APPLICATION = "org.eclipse.pde.ui.RuntimeWorkbench"; //$NON-NLS-1$

    /** Attribute with the VM arguments (IJavaLaunchConfigurationConstants) */
    private static final String ATTR_VM_ARGUMENTS = "org.eclipse.jdt.launching.VM_ARGUMENTS"; //$NON-NLS-1$

    private TestInstanceFinder() {
    }

    /**
     * Returns the running test instances with the UI tools.
     *
     * @return test instances
     */
    public static List<TestInstance> findRunning() {

        List<TestInstance> instances = new ArrayList<TestInstance>();
        for (ILaunch launch : getLaunchManager().getLaunches()) {
            ILaunchConfiguration configuration = launch.getLaunchConfiguration();
            if (launch.isTerminated() || configuration == null || !isEclipseApplication(configuration)) {
                continue;
            }
            for (IProcess process : launch.getProcesses()) {
                String commandLine = process.getAttribute(IProcess.ATTR_CMDLINE);
                if (!process.isTerminated() && commandLine != null) {
                    TestInstance instance = TestInstance.fromCommandLine(configuration.getName(), commandLine);
                    if (instance != null) {
                        instances.add(instance);
                    }
                }
            }
        }

        return instances;
    }

    /**
     * Returns the names of the launch configurations of the type Eclipse
     * Application, whose VM arguments contain '-Diagentbridge.uiTools=true'.
     *
     * @return names of the launch configurations
     */
    public static List<String> findConfigurations() {

        List<String> names = new ArrayList<String>();
        ILaunchManager manager = getLaunchManager();
        ILaunchConfigurationType type = manager.getLaunchConfigurationType(TYPE_ECLIPSE_APPLICATION);
        if (type == null) {
            return names;
        }

        try {
            for (ILaunchConfiguration configuration : manager.getLaunchConfigurations(type)) {
                String vmArguments = configuration.getAttribute(ATTR_VM_ARGUMENTS, ""); //$NON-NLS-1$
                if (vmArguments.contains("-D" + TestInstance.PROPERTY_UI_TOOLS + "=true")) { //$NON-NLS-1$ //$NON-NLS-2$
                    names.add(configuration.getName());
                }
            }
        } catch (CoreException e) {
            // The names only help the message, that no test instance runs.
        }

        return names;
    }

    private static boolean isEclipseApplication(ILaunchConfiguration aConfiguration) {
        try {
            return TYPE_ECLIPSE_APPLICATION.equals(aConfiguration.getType().getIdentifier());
        } catch (CoreException e) {
            return false;
        }
    }

    private static ILaunchManager getLaunchManager() {
        return DebugPlugin.getDefault().getLaunchManager();
    }
}
