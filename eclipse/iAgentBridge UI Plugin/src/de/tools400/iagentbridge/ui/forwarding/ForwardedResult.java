/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.forwarding;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import de.tools400.iagentbridge.mcp.McpToolResult;

/**
 * Turns the JSON-RPC response of a test instance to 'tools/call' into the
 * result of the forwarding tool: the texts and images of the test instance
 * unchanged, after a first line, that names the test instance.
 */
public final class ForwardedResult {

    /** JSON-RPC error code of invalid parameters, e.g. an unknown tool */
    private static final int INVALID_PARAMS = -32602;

    private static final String UNKNOWN_TOOL = "Unknown tool"; //$NON-NLS-1$

    private ForwardedResult() {
    }

    /**
     * Returns the result of the tool.
     *
     * @param aResponse - JSON-RPC response of the test instance
     * @param anInstance - the test instance
     * @return result with the texts and images of the test instance
     */
    public static McpToolResult toToolResult(JsonObject aResponse, TestInstance anInstance) {

        String heading = "Test instance " + anInstance.describe() + ":\n"; //$NON-NLS-1$ //$NON-NLS-2$

        JsonElement error = aResponse.get("error"); //$NON-NLS-1$
        if (error != null && error.isJsonObject()) {
            return toError(error.getAsJsonObject(), heading, anInstance);
        }

        JsonElement result = aResponse.get("result"); //$NON-NLS-1$
        if (result == null || !result.isJsonObject()) {
            return McpToolResult.error(heading + "Invalid response: " + aResponse); //$NON-NLS-1$
        }

        JsonObject resultObject = result.getAsJsonObject();
        JsonElement content = resultObject.get("content"); //$NON-NLS-1$
        if (content == null || !content.isJsonArray()) {
            return McpToolResult.error(heading + "Invalid response without content: " + aResponse); //$NON-NLS-1$
        }

        StringBuilder text = new StringBuilder(heading);
        List<JsonObject> images = new ArrayList<JsonObject>();
        boolean firstText = true;
        for (JsonElement element : (JsonArray)content) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject item = element.getAsJsonObject();
            String type = getString(item, "type"); //$NON-NLS-1$
            if ("text".equals(type)) { //$NON-NLS-1$
                if (!firstText) {
                    text.append('\n');
                }
                text.append(getString(item, "text")); //$NON-NLS-1$
                firstText = false;
            } else if ("image".equals(type)) { //$NON-NLS-1$
                images.add(item);
            }
        }

        JsonElement isError = resultObject.get("isError"); //$NON-NLS-1$
        boolean failed = isError != null && isError.isJsonPrimitive() && isError.getAsBoolean();
        McpToolResult toolResult = failed ? McpToolResult.error(text.toString()) : McpToolResult.success(text.toString());

        for (JsonObject image : images) {
            String data = getString(image, "data"); //$NON-NLS-1$
            String mimeType = getString(image, "mimeType"); //$NON-NLS-1$
            if (!data.isEmpty() && !mimeType.isEmpty()) {
                toolResult = toolResult.withImage(Base64.getDecoder().decode(data), mimeType);
            }
        }

        return toolResult;
    }

    private static McpToolResult toError(JsonObject anError, String aHeading, TestInstance anInstance) {

        JsonElement code = anError.get("code"); //$NON-NLS-1$
        String message = getString(anError, "message"); //$NON-NLS-1$
        StringBuilder text = new StringBuilder(aHeading).append(message);
        if (code != null && code.isJsonPrimitive() && code.getAsInt() == INVALID_PARAMS && message.startsWith(UNKNOWN_TOOL)) {
            text.append("\nPort ").append(anInstance.getPort()) //$NON-NLS-1$
                .append(" belongs to an RDi without the UI tools of iAgentBridge. Check -Diagentbridge.port of the launch configuration."); //$NON-NLS-1$
        }

        return McpToolResult.error(text.toString());
    }

    private static String getString(JsonObject anObject, String aName) {
        JsonElement element = anObject.get(aName);
        if (element == null || !element.isJsonPrimitive()) {
            return ""; //$NON-NLS-1$
        }
        return element.getAsString();
    }
}
