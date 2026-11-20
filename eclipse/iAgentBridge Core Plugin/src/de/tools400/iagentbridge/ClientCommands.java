/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge;

/**
 * Creates the commands, that register the server in an MCP client.
 * <p>
 * The class has no Eclipse dependencies, so that it can be tested outside of
 * RDi.
 */
public final class ClientCommands {

    /** Name of the server, if the version of RDi is not known */
    public static final String DEFAULT_SERVER_NAME = "iagentbridge"; //$NON-NLS-1$

    private ClientCommands() {
    }

    /**
     * Suggests the name of the server in the MCP client, e.g. 'rdi096' for RDi
     * 9.6.
     *
     * @param aMajor - major version of RDi
     * @param aMinor - minor version of RDi
     * @return name of the server
     */
    public static String suggestServerName(int aMajor, int aMinor) {

        if (aMajor < 1 || aMajor > 99 || aMinor < 0 || aMinor > 9) {
            return DEFAULT_SERVER_NAME;
        }

        return String.format("rdi%02d%d", Integer.valueOf(aMajor), Integer.valueOf(aMinor)); //$NON-NLS-1$
    }

    /**
     * Creates the PowerShell command, that registers the server in Claude Code
     * for all projects of the user. The command reads the token from the token
     * file, so that the token itself is not shown.
     *
     * @param aServerName - name of the server in Claude Code
     * @param aUrl - URL of the server
     * @param aTokenFile - path of the token file or <code>null</code>, if the
     *        token is set by a system property
     * @return command
     */
    public static String createClaudeCodeCommand(String aServerName, String aUrl, String aTokenFile) {

        String token;
        if (aTokenFile == null) {
            token = "<token>"; //$NON-NLS-1$
        } else {
            token = "$(Get-Content '" + aTokenFile.replace("'", "''") + "')"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        }

        return "claude mcp add --scope user --transport http " + aServerName + " " + aUrl //$NON-NLS-1$ //$NON-NLS-2$
            + " --header \"Authorization: Bearer " + token + "\""; //$NON-NLS-1$ //$NON-NLS-2$
    }
}
