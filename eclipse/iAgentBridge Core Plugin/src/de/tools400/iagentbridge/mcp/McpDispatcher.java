/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.mcp;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/**
 * Handles the JSON-RPC messages of the Model Context Protocol (MCP): the life
 * cycle ('initialize', 'ping') and the tools ('tools/list', 'tools/call').
 * <p>
 * The class has no Eclipse dependencies, so that it can be tested outside of
 * RDi.
 */
public class McpDispatcher {

    /** Protocol version returned, when the client asks for an unknown one. */
    public static final String LATEST_PROTOCOL_VERSION = "2025-06-18"; //$NON-NLS-1$

    /** Protocol versions, whose features the server supports. */
    public static final List<String> SUPPORTED_PROTOCOL_VERSIONS = Collections
        .unmodifiableList(Arrays.asList(LATEST_PROTOCOL_VERSION, "2025-03-26", "2024-11-05")); //$NON-NLS-1$ //$NON-NLS-2$

    public static final int PARSE_ERROR = -32700;
    public static final int INVALID_REQUEST = -32600;
    public static final int METHOD_NOT_FOUND = -32601;
    public static final int INVALID_PARAMS = -32602;

    private static final String JSONRPC_VERSION = "2.0"; //$NON-NLS-1$

    private static final String METHOD_INITIALIZE = "initialize"; //$NON-NLS-1$
    private static final String METHOD_PING = "ping"; //$NON-NLS-1$
    private static final String METHOD_TOOLS_LIST = "tools/list"; //$NON-NLS-1$
    private static final String METHOD_TOOLS_CALL = "tools/call"; //$NON-NLS-1$

    private static final String KEY_JSONRPC = "jsonrpc"; //$NON-NLS-1$
    private static final String KEY_ID = "id"; //$NON-NLS-1$
    private static final String KEY_METHOD = "method"; //$NON-NLS-1$
    private static final String KEY_PARAMS = "params"; //$NON-NLS-1$
    private static final String KEY_RESULT = "result"; //$NON-NLS-1$
    private static final String KEY_ERROR = "error"; //$NON-NLS-1$
    private static final String KEY_NAME = "name"; //$NON-NLS-1$

    /** Keeps "id": null of error responses and does not escape '<', '=' etc. */
    private static final Gson GSON = new GsonBuilder().serializeNulls().disableHtmlEscaping().create();

    private final String serverName;
    private final String serverVersion;
    private final String instructions;
    private final Map<String, IMcpTool> tools;

    /**
     * Creates a dispatcher.
     *
     * @param aServerName - name of the server returned by 'initialize'
     * @param aServerVersion - version of the server returned by 'initialize'
     * @param anInstructions - instructions for the AI agent returned by
     *        'initialize' or <code>null</code>
     * @param aTools - tools of the server
     */
    public McpDispatcher(String aServerName, String aServerVersion, String anInstructions, List<IMcpTool> aTools) {
        serverName = aServerName;
        serverVersion = aServerVersion;
        instructions = anInstructions;
        tools = new LinkedHashMap<String, IMcpTool>();
        for (IMcpTool tool : aTools) {
            tools.put(tool.getName(), tool);
        }
    }

    /**
     * Handles one JSON-RPC message.
     *
     * @param aMessage - message as received from the client
     * @return response or <code>null</code>, if the message was a notification
     *         or a response of the client, which need no answer
     */
    public String handle(String aMessage) {

        JsonElement element;
        try {
            element = new JsonParser().parse(aMessage);
        } catch (JsonParseException e) {
            return error(JsonNull.INSTANCE, PARSE_ERROR, "Parse error: " + e.getMessage()); //$NON-NLS-1$
        }

        if (element == null || !element.isJsonObject()) {
            return error(JsonNull.INSTANCE, INVALID_REQUEST, "Invalid request: expected a JSON object (batches are not supported)."); //$NON-NLS-1$
        }

        JsonObject message = element.getAsJsonObject();
        JsonElement id = message.get(KEY_ID);
        String method = getString(message, KEY_METHOD);

        if (method == null) {
            if (message.has(KEY_RESULT) || message.has(KEY_ERROR)) {
                // Response of the client to a request of the server.
                return null;
            }
            return error(nullIfMissing(id), INVALID_REQUEST, "Invalid request: 'method' is missing."); //$NON-NLS-1$
        }

        if (!JSONRPC_VERSION.equals(getString(message, KEY_JSONRPC))) {
            return error(nullIfMissing(id), INVALID_REQUEST, "Invalid request: 'jsonrpc' must be \"2.0\"."); //$NON-NLS-1$
        }

        if (id == null) {
            // Notification, e.g. 'notifications/initialized'.
            return null;
        }

        if (id.isJsonNull()) {
            return error(id, INVALID_REQUEST, "Invalid request: 'id' must not be null."); //$NON-NLS-1$
        }

        JsonObject params = getObject(message, KEY_PARAMS);
        try {
            if (METHOD_INITIALIZE.equals(method)) {
                return response(id, initialize(params));
            } else if (METHOD_PING.equals(method)) {
                return response(id, new JsonObject());
            } else if (METHOD_TOOLS_LIST.equals(method)) {
                return response(id, listTools());
            } else if (METHOD_TOOLS_CALL.equals(method)) {
                return response(id, callTool(params));
            } else {
                return error(id, METHOD_NOT_FOUND, "Method not found: " + method); //$NON-NLS-1$
            }
        } catch (InvalidParamsException e) {
            return error(id, INVALID_PARAMS, e.getMessage());
        }
    }

    private JsonObject initialize(JsonObject aParams) {

        String requestedVersion = aParams == null ? null : getString(aParams, "protocolVersion"); //$NON-NLS-1$
        String protocolVersion = SUPPORTED_PROTOCOL_VERSIONS.contains(requestedVersion) ? requestedVersion : LATEST_PROTOCOL_VERSION;

        JsonObject toolsCapability = new JsonObject();
        toolsCapability.addProperty("listChanged", Boolean.FALSE); //$NON-NLS-1$

        JsonObject capabilities = new JsonObject();
        capabilities.add("tools", toolsCapability); //$NON-NLS-1$

        JsonObject serverInfo = new JsonObject();
        serverInfo.addProperty(KEY_NAME, serverName);
        serverInfo.addProperty("version", serverVersion); //$NON-NLS-1$

        JsonObject result = new JsonObject();
        result.addProperty("protocolVersion", protocolVersion); //$NON-NLS-1$
        result.add("capabilities", capabilities); //$NON-NLS-1$
        result.add("serverInfo", serverInfo); //$NON-NLS-1$
        if (instructions != null) {
            result.addProperty("instructions", instructions); //$NON-NLS-1$
        }

        return result;
    }

    private JsonObject listTools() {

        JsonArray toolList = new JsonArray();
        for (IMcpTool tool : tools.values()) {
            JsonObject description = new JsonObject();
            description.addProperty(KEY_NAME, tool.getName());
            description.addProperty("description", tool.getDescription()); //$NON-NLS-1$
            description.add("inputSchema", tool.getInputSchema()); //$NON-NLS-1$
            toolList.add(description);
        }

        JsonObject result = new JsonObject();
        result.add("tools", toolList); //$NON-NLS-1$

        return result;
    }

    private JsonObject callTool(JsonObject aParams) throws InvalidParamsException {

        String name = aParams == null ? null : getString(aParams, KEY_NAME);
        if (name == null) {
            throw new InvalidParamsException("Invalid params: the name of the tool is missing."); //$NON-NLS-1$
        }

        IMcpTool tool = tools.get(name);
        if (tool == null) {
            throw new InvalidParamsException("Unknown tool: " + name); //$NON-NLS-1$
        }

        JsonObject arguments = getObject(aParams, "arguments"); //$NON-NLS-1$
        if (arguments == null) {
            arguments = new JsonObject();
        }

        McpToolResult result;
        try {
            result = tool.execute(arguments);
        } catch (IllegalArgumentException e) {
            result = McpToolResult.error(e.getMessage());
        } catch (Exception e) {
            result = McpToolResult.error(describe(e));
        } catch (LinkageError e) {
            // E.g. an internal class of Eclipse, that is missing in this
            // version of RDi.
            result = McpToolResult.error(describe(e));
        }

        if (result == null) {
            result = McpToolResult.error("Tool '" + name + "' returned no result."); //$NON-NLS-1$ //$NON-NLS-2$
        }

        return result.toJson();
    }

    private static String describe(Throwable aThrowable) {

        StringBuilder text = new StringBuilder();
        Throwable throwable = aThrowable;
        while (throwable != null) {
            if (text.length() > 0) {
                text.append("\nCaused by: "); //$NON-NLS-1$
            }
            text.append(throwable.getClass().getName());
            if (throwable.getMessage() != null) {
                text.append(": ").append(throwable.getMessage()); //$NON-NLS-1$
            }
            throwable = throwable.getCause() == throwable ? null : throwable.getCause();
        }

        return text.toString();
    }

    private static String response(JsonElement anId, JsonObject aResult) {

        JsonObject response = new JsonObject();
        response.addProperty(KEY_JSONRPC, JSONRPC_VERSION);
        response.add(KEY_ID, anId);
        response.add(KEY_RESULT, aResult);

        return GSON.toJson(response);
    }

    private static String error(JsonElement anId, int aCode, String aMessage) {

        JsonObject error = new JsonObject();
        error.addProperty("code", Integer.valueOf(aCode)); //$NON-NLS-1$
        error.addProperty("message", aMessage); //$NON-NLS-1$

        JsonObject response = new JsonObject();
        response.addProperty(KEY_JSONRPC, JSONRPC_VERSION);
        response.add(KEY_ID, anId);
        response.add(KEY_ERROR, error);

        return GSON.toJson(response);
    }

    private static JsonElement nullIfMissing(JsonElement anId) {
        return anId == null ? JsonNull.INSTANCE : anId;
    }

    private static String getString(JsonObject anObject, String aKey) {

        JsonElement element = anObject.get(aKey);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            return null;
        }

        return element.getAsString();
    }

    private static JsonObject getObject(JsonObject anObject, String aKey) {

        JsonElement element = anObject.get(aKey);
        if (element == null || !element.isJsonObject()) {
            return null;
        }

        return element.getAsJsonObject();
    }

    /**
     * Thrown, when the parameters of a request are invalid.
     */
    private static class InvalidParamsException extends Exception {

        private static final long serialVersionUID = 1L;

        public InvalidParamsException(String aMessage) {
            super(aMessage);
        }
    }
}
