/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.rdimcp.mcp;

import com.google.gson.JsonObject;

/**
 * A tool of the MCP server, which the client lists with 'tools/list' and calls
 * with 'tools/call'.
 */
public interface IMcpTool {

    /**
     * Returns the name of the tool, which the client uses to call it.
     *
     * @return name of the tool
     */
    String getName();

    /**
     * Returns the description of the tool, which tells the AI agent, what the
     * tool does and when to use it.
     *
     * @return description of the tool
     */
    String getDescription();

    /**
     * Returns the JSON schema of the arguments of the tool.
     *
     * @return JSON schema of type 'object'
     */
    JsonObject getInputSchema();

    /**
     * Executes the tool.
     *
     * @param anArguments - arguments passed by the client, never
     *        <code>null</code>
     * @return result of the tool
     * @throws IllegalArgumentException if the arguments are invalid; the
     *         message is returned to the client as a tool error
     * @throws Exception if the tool failed
     */
    McpToolResult execute(JsonObject anArguments) throws Exception;
}
