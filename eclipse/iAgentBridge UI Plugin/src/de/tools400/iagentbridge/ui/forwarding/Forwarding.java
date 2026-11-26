/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.forwarding;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

import com.google.gson.JsonObject;

import de.tools400.iagentbridge.mcp.McpToolResult;

/**
 * Forwards a call of a UI tool from the RDi, in which the developer works, to
 * the one running test instance with the UI tools.
 */
public final class Forwarding {

    private final ForwardingClient client;
    private final Properties properties;

    /**
     * Creates the forwarding.
     *
     * @param aClient - client, that calls the test instance
     * @param aProperties - system properties with 'user.home', for the token
     *        file
     */
    public Forwarding(ForwardingClient aClient, Properties aProperties) {
        client = aClient;
        properties = aProperties;
    }

    /**
     * Forwards a call.
     *
     * @param aRunning - the running test instances with the UI tools
     * @param aConfigurations - names of the launch configurations with
     *        '-Diagentbridge.uiTools=true', for the message, that none runs
     * @param aToolName - name of the tool
     * @param anArguments - arguments of the tool
     * @return result of the tool in the test instance or an error result
     */
    public McpToolResult forward(List<TestInstance> aRunning, List<String> aConfigurations, String aToolName, JsonObject anArguments) {

        if (aRunning.isEmpty()) {
            return McpToolResult.error(noTestInstance(aConfigurations));
        }

        if (aRunning.size() > 1) {
            StringBuilder message = new StringBuilder("Several test instances with the UI tools are running: "); //$NON-NLS-1$
            for (int i = 0; i < aRunning.size(); i++) {
                message.append(i == 0 ? "" : ", ").append(aRunning.get(i).describe()); //$NON-NLS-1$ //$NON-NLS-2$
            }
            return McpToolResult.error(message.append(". Stop all but one.").toString()); //$NON-NLS-1$
        }

        TestInstance instance = aRunning.get(0);
        if (instance.getPort() == null) {
            return McpToolResult.error("The test instance " + instance.describe() //$NON-NLS-1$
                + " has no valid -Diagentbridge.port in its command line. Add it to the VM arguments of the launch configuration."); //$NON-NLS-1$
        }

        String token;
        try {
            token = token(instance, properties);
        } catch (IOException e) {
            return McpToolResult.error("The token for the test instance " + instance.describe() + " could not be read: " + e.getMessage()); //$NON-NLS-1$ //$NON-NLS-2$
        }

        return client.call(instance, token, aToolName, anArguments);
    }

    /**
     * Returns the token of an instance: the one of its command line, or else
     * the one of the token file.
     *
     * @param anInstance - the instance
     * @param aProperties - system properties with 'user.home', for the token
     *        file
     * @return the token
     * @throws IOException if the token file cannot be read
     */
    public static String token(TestInstance anInstance, Properties aProperties) throws IOException {
        String token = anInstance.getToken();
        return token != null ? token : TokenFile.read(aProperties);
    }

    private static String noTestInstance(List<String> aConfigurations) {

        StringBuilder message = new StringBuilder("No test instance with the UI tools is running. "); //$NON-NLS-1$
        if (aConfigurations.isEmpty()) {
            return message.append("Start an Eclipse Application with the VM arguments -Diagentbridge.uiTools=true and -Diagentbridge.port=<port>.") //$NON-NLS-1$
                .toString();
        }

        message.append("Start one of the launch configurations with -Diagentbridge.uiTools=true: "); //$NON-NLS-1$
        for (int i = 0; i < aConfigurations.size(); i++) {
            message.append(i == 0 ? "" : ", ").append('"').append(aConfigurations.get(i)).append('"'); //$NON-NLS-1$ //$NON-NLS-2$
        }
        return message.append('.').toString();
    }
}
