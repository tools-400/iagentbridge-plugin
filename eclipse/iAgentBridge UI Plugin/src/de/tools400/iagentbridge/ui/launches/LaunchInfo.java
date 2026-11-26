/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.launches;

import de.tools400.iagentbridge.ui.forwarding.TestInstance;

/**
 * What start_launch checks of a launch configuration, without Eclipse types:
 * name, type, arguments, workspace and the question, whether the workspace is
 * cleared.
 */
public final class LaunchInfo {

    /** Type of an Eclipse Application of PDE */
    public static final String TYPE_ECLIPSE_APPLICATION = "org.eclipse.pde.ui.RuntimeWorkbench"; //$NON-NLS-1$

    /** Type of a JUnit Plug-in Test of PDE */
    public static final String TYPE_JUNIT_PLUGIN_TEST = "org.eclipse.pde.ui.JunitLaunchConfig"; //$NON-NLS-1$

    private final String name;
    private final String type;
    private final String vmArguments;
    private final String programArguments;
    private final String location;
    private final boolean clearWorkspace;
    private final boolean askClear;

    /**
     * Creates the information.
     *
     * @param aName - name of the configuration
     * @param aType - ID of its type
     * @param aVmArguments - VM arguments, unresolved
     * @param aProgramArguments - program arguments, unresolved
     * @param aLocation - attribute 'location', the workspace, unresolved, or
     *        <code>null</code>
     * @param aClearWorkspace - attribute 'clearws'
     * @param anAskClear - attribute 'askclear'
     */
    public LaunchInfo(String aName, String aType, String aVmArguments, String aProgramArguments, String aLocation, boolean aClearWorkspace,
        boolean anAskClear) {
        name = aName;
        type = aType;
        vmArguments = aVmArguments == null ? "" : aVmArguments; //$NON-NLS-1$
        programArguments = aProgramArguments == null ? "" : aProgramArguments; //$NON-NLS-1$
        location = aLocation;
        clearWorkspace = aClearWorkspace;
        askClear = anAskClear;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getVmArguments() {
        return vmArguments;
    }

    public String getProgramArguments() {
        return programArguments;
    }

    /**
     * Returns the attribute 'location', the workspace, unresolved.
     *
     * @return location or <code>null</code>
     */
    public String getLocation() {
        return location;
    }

    /**
     * Returns whether the launch asks in a dialog, whether its workspace shall
     * be cleared.
     *
     * @return <code>true</code>, if it would ask
     */
    public boolean asksToClear() {
        return clearWorkspace && askClear;
    }

    /**
     * Returns whether start_launch may start the type.
     *
     * @return <code>true</code> for an Eclipse Application or a JUnit Plug-in
     *         Test
     */
    public boolean isAllowedType() {
        return TYPE_ECLIPSE_APPLICATION.equals(type) || TYPE_JUNIT_PLUGIN_TEST.equals(type);
    }

    /**
     * Returns the instance, that the VM arguments describe: its port and
     * whether it has the UI tools.
     *
     * @return the instance; its port is <code>null</code> without a valid
     *         '-Diagentbridge.port'
     */
    public TestInstance getInstance() {
        return TestInstance.ofCommandLine(name, vmArguments);
    }

    /**
     * Describes the port of iAgentBridge, that the VM arguments set.
     *
     * @return e.g. 'port 39196', or why it is not known
     */
    public String describePort() {
        Integer port = getInstance().getPort();
        if (port != null) {
            return "port " + port; //$NON-NLS-1$
        }
        // JDT resolves a variable only at the start.
        return vmArguments.contains("-Diagentbridge.port=${") ? "port only known after the start" : "without a valid -Diagentbridge.port"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }
}
