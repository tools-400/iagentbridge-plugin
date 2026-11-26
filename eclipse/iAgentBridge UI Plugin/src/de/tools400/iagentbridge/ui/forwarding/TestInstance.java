/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.forwarding;

import java.util.Map;

/**
 * An instance of RDi with iAgentBridge, started from the workspace, or a launch
 * configuration for one: the name of the launch configuration, the port of its
 * server, if the command line or the VM arguments set it, its token, and
 * whether it executes the UI tools (a test instance).
 * <p>
 * The names of the system properties are those of the core plug-in
 * (ServerSettings), whose package is not exported.
 */
public final class TestInstance {

    /** System property, that makes an instance execute the UI tools */
    public static final String PROPERTY_UI_TOOLS = "iagentbridge.uiTools"; //$NON-NLS-1$

    /**
     * System property with the port of the server
     * (ServerSettings.PROPERTY_PORT)
     */
    static final String PROPERTY_PORT = "iagentbridge.port"; //$NON-NLS-1$

    /**
     * System property with the token of the server
     * (ServerSettings.PROPERTY_TOKEN)
     */
    static final String PROPERTY_TOKEN = "iagentbridge.token"; //$NON-NLS-1$

    private final String name;
    private final Integer port;
    private final String token;
    private final boolean uiTools;

    private TestInstance(String aName, Integer aPort, String aToken, boolean aUiTools) {
        name = aName;
        port = aPort;
        token = aToken;
        uiTools = aUiTools;
    }

    /**
     * Returns the test instance, that a process with the command line runs.
     *
     * @param aName - name of the launch configuration
     * @param aCommandLine - command line of the process
     * @return test instance or <code>null</code>, if the command line has no
     *         '-Diagentbridge.uiTools=true'
     */
    public static TestInstance fromCommandLine(String aName, String aCommandLine) {
        TestInstance instance = ofCommandLine(aName, aCommandLine);
        return instance.hasUiTools() ? instance : null;
    }

    /**
     * Returns the instance, that a process with the command line runs, with or
     * without the UI tools.
     *
     * @param aName - name of the launch configuration
     * @param aCommandLine - command line of the process
     * @return the instance; its port is <code>null</code>, if the command line
     *         sets no valid port
     */
    public static TestInstance ofCommandLine(String aName, String aCommandLine) {

        Map<String, String> properties = CommandLine.systemProperties(aCommandLine);
        boolean uiTools = Boolean.TRUE.toString().equalsIgnoreCase(trim(properties.get(PROPERTY_UI_TOOLS)));

        Integer port = null;
        String portValue = trim(properties.get(PROPERTY_PORT));
        if (portValue != null) {
            try {
                int value = Integer.parseInt(portValue);
                if (value > 0 && value <= 65535) {
                    port = Integer.valueOf(value);
                }
            } catch (NumberFormatException e) {
                // An invalid port counts as missing.
            }
        }

        String token = trim(properties.get(PROPERTY_TOKEN));
        if (token != null && token.isEmpty()) {
            token = null;
        }

        return new TestInstance(aName, port, token, uiTools);
    }

    /**
     * Returns whether the instance executes the UI tools
     * ('-Diagentbridge.uiTools=true').
     *
     * @return <code>true</code> for a test instance
     */
    public boolean hasUiTools() {
        return uiTools;
    }

    public String getName() {
        return name;
    }

    /**
     * Returns the port of the server of the test instance.
     *
     * @return port or <code>null</code>, if the command line sets no valid port
     */
    public Integer getPort() {
        return port;
    }

    /**
     * Returns the token, that the command line sets.
     *
     * @return token or <code>null</code>, if the instance reads the token file
     */
    public String getToken() {
        return token;
    }

    /**
     * Returns the name and the port, e.g. '"iAgentBridge (de)" (port 39196)'.
     *
     * @return description of the instance
     */
    public String describe() {
        return "\"" + name + "\"" + (port == null ? "" : " (port " + port + ")"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
    }

    private static String trim(String aValue) {
        return aValue == null ? null : aValue.trim();
    }
}
