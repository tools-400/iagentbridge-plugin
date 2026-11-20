/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.rdimcp.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Result of a tool: a text for the AI agent and the information, whether the
 * tool failed.
 */
public final class McpToolResult {

    private final String text;
    private final boolean error;

    private McpToolResult(String aText, boolean anError) {
        text = aText;
        error = anError;
    }

    /**
     * Creates the result of a tool, that succeeded.
     *
     * @param aText - text returned to the client
     * @return result
     */
    public static McpToolResult success(String aText) {
        return new McpToolResult(aText, false);
    }

    /**
     * Creates the result of a tool, that failed.
     *
     * @param aText - text returned to the client
     * @return result
     */
    public static McpToolResult error(String aText) {
        return new McpToolResult(aText, true);
    }

    public String getText() {
        return text;
    }

    public boolean isError() {
        return error;
    }

    /**
     * Returns the result in the form of the 'tools/call' response.
     *
     * @return result object with 'content' and 'isError'
     */
    public JsonObject toJson() {

        JsonObject textContent = new JsonObject();
        textContent.addProperty("type", "text"); //$NON-NLS-1$ //$NON-NLS-2$
        textContent.addProperty("text", text == null ? "" : text); //$NON-NLS-1$ //$NON-NLS-2$

        JsonArray content = new JsonArray();
        content.add(textContent);

        JsonObject result = new JsonObject();
        result.add("content", content); //$NON-NLS-1$
        result.addProperty("isError", Boolean.valueOf(error)); //$NON-NLS-1$

        return result;
    }
}
