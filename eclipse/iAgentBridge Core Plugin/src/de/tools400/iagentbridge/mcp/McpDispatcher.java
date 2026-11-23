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
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

/**
 * Handles the JSON-RPC messages of the Model Context Protocol (MCP): the life
 * cycle ('initialize', 'ping') and the tools ('tools/list', 'tools/call').
 * Batches of messages are accepted, as protocol version 2025-03-26 requires.
 * <p>
 * The class has no Eclipse dependencies, so that it can be tested outside of
 * RDi.
 */
public class McpDispatcher {

    /** Protocol version returned, when the client asks for an unknown one. */
    public static final String LATEST_PROTOCOL_VERSION = "2025-11-25"; //$NON-NLS-1$

    /** Protocol versions, whose features the server supports. */
    public static final List<String> SUPPORTED_PROTOCOL_VERSIONS = Collections
        .unmodifiableList(Arrays.asList(LATEST_PROTOCOL_VERSION, "2025-06-18", "2025-03-26", "2024-11-05")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

    public static final int PARSE_ERROR = -32700;
    public static final int INVALID_REQUEST = -32600;
    public static final int METHOD_NOT_FOUND = -32601;
    public static final int INVALID_PARAMS = -32602;
    public static final int INTERNAL_ERROR = -32603;

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
    private static final String KEY_ARGUMENTS = "arguments"; //$NON-NLS-1$

    /** Keeps null values of the tools and does not escape '<', '=' etc. */
    private static final Gson GSON = new GsonBuilder().serializeNulls().disableHtmlEscaping().create();

    private final String serverName;
    private final String serverVersion;
    private final String instructions;
    private final Map<String, IMcpTool> tools;
    private final IMcpErrorLog errorLog;

    /**
     * Creates a dispatcher, that writes no error log.
     *
     * @param aServerName - name of the server returned by 'initialize'
     * @param aServerVersion - version of the server returned by 'initialize'
     * @param anInstructions - instructions for the AI agent returned by
     *        'initialize' or <code>null</code>
     * @param aTools - tools of the server
     */
    public McpDispatcher(String aServerName, String aServerVersion, String anInstructions, List<IMcpTool> aTools) {
        this(aServerName, aServerVersion, anInstructions, aTools, null);
    }

    /**
     * Creates a dispatcher.
     *
     * @param aServerName - name of the server returned by 'initialize'
     * @param aServerVersion - version of the server returned by 'initialize'
     * @param anInstructions - instructions for the AI agent returned by
     *        'initialize' or <code>null</code>
     * @param aTools - tools of the server
     * @param anErrorLog - log of the internal errors ('-32603') or
     *        <code>null</code>
     */
    public McpDispatcher(String aServerName, String aServerVersion, String anInstructions, List<IMcpTool> aTools, IMcpErrorLog anErrorLog) {
        serverName = aServerName;
        serverVersion = aServerVersion;
        instructions = anInstructions;
        tools = new LinkedHashMap<String, IMcpTool>();
        for (IMcpTool tool : aTools) {
            tools.put(tool.getName(), tool);
        }
        errorLog = anErrorLog;
    }

    /**
     * Handles one JSON-RPC message or a batch of messages.
     *
     * @param aMessage - message as received from the client
     * @return response of the dispatcher
     */
    public McpResponse handle(String aMessage) {

        JsonElement element;
        try {
            element = new JsonParser().parse(aMessage);
        } catch (JsonParseException e) {
            return toResponse(error(null, PARSE_ERROR, "Parse error: " + e.getMessage())); //$NON-NLS-1$
        }

        if (element != null && element.isJsonArray()) {
            return handleBatch(element.getAsJsonArray());
        }

        return toResponse(handleMessage(element));
    }

    /**
     * Handles a batch of messages. The responses are returned as an array. Like
     * a single message, the batch is rejected, when none of its responses has
     * an 'id', because it contains invalid messages, but no request.
     */
    private McpResponse handleBatch(JsonArray aBatch) {

        if (aBatch.size() == 0) {
            return toResponse(error(null, INVALID_REQUEST, "Invalid request: the batch is empty.")); //$NON-NLS-1$
        }

        JsonArray responses = new JsonArray();
        boolean hasAnswers = false;
        for (JsonElement message : aBatch) {
            JsonObject response = handleMessage(message);
            if (response != null) {
                responses.add(response);
                if (response.has(KEY_ID)) {
                    hasAnswers = true;
                }
            }
        }

        if (responses.size() == 0) {
            return McpResponse.noAnswer();
        }

        String json = GSON.toJson(responses);
        if (!hasAnswers) {
            return McpResponse.rejected(json);
        }

        return McpResponse.answer(json);
    }

    /**
     * Handles one message.
     *
     * @return response or <code>null</code>, if the message was a notification
     *         or a response of the client, which need no answer
     */
    private JsonObject handleMessage(JsonElement aMessage) {

        if (aMessage == null || !aMessage.isJsonObject()) {
            return error(null, INVALID_REQUEST, "Invalid request: expected a JSON object."); //$NON-NLS-1$
        }

        JsonObject message = aMessage.getAsJsonObject();
        JsonElement id = message.get(KEY_ID);
        JsonElement validId = isValidId(id) ? id : null;
        String method = getString(message, KEY_METHOD);

        if (method == null) {
            if (message.has(KEY_RESULT) || message.has(KEY_ERROR)) {
                // Response of the client to a request of the server.
                return null;
            }
            return error(validId, INVALID_REQUEST, "Invalid request: 'method' is missing."); //$NON-NLS-1$
        }

        if (!JSONRPC_VERSION.equals(getString(message, KEY_JSONRPC))) {
            return error(validId, INVALID_REQUEST, "Invalid request: 'jsonrpc' must be \"2.0\"."); //$NON-NLS-1$
        }

        if (id == null) {
            // Notification, e.g. 'notifications/initialized'.
            return null;
        }

        if (validId == null) {
            return error(null, INVALID_REQUEST, "Invalid request: 'id' must be a string or a number."); //$NON-NLS-1$
        }

        return handleRequest(validId, method, getObject(message, KEY_PARAMS));
    }

    /**
     * Handles a request.
     *
     * @return response
     */
    private JsonObject handleRequest(JsonElement anId, String aMethod, JsonObject aParams) {

        try {
            if (METHOD_INITIALIZE.equals(aMethod)) {
                return response(anId, initialize(aParams));
            } else if (METHOD_PING.equals(aMethod)) {
                return response(anId, new JsonObject());
            } else if (METHOD_TOOLS_LIST.equals(aMethod)) {
                return response(anId, listTools());
            } else if (METHOD_TOOLS_CALL.equals(aMethod)) {
                return response(anId, callTool(aParams));
            } else {
                return error(anId, METHOD_NOT_FOUND, "Method not found: " + aMethod); //$NON-NLS-1$
            }
        } catch (InvalidParamsException e) {
            return error(anId, INVALID_PARAMS, e.getMessage());
        } catch (RuntimeException e) {
            // E.g. a tool, that fails to describe itself.
            return internalError(anId, aMethod, e);
        } catch (LinkageError e) {
            // E.g. a tool, that refers to a class, that is missing in this
            // version of RDi.
            return internalError(anId, aMethod, e);
        }
    }

    /**
     * Writes an internal error to the error log and returns the error response
     * '-32603'.
     */
    private JsonObject internalError(JsonElement anId, String aMethod, Throwable aThrowable) {

        String description = describe(aThrowable);
        if (errorLog != null) {
            errorLog.logError("MCP request '" + aMethod + "' failed: " + description, aThrowable); //$NON-NLS-1$ //$NON-NLS-2$
        }

        return error(anId, INTERNAL_ERROR, "Internal error: " + description); //$NON-NLS-1$
    }

    /**
     * Returns whether the ID of a request is valid: a string or a number.
     */
    private static boolean isValidId(JsonElement anId) {

        if (anId == null || !anId.isJsonPrimitive()) {
            return false;
        }

        JsonPrimitive id = anId.getAsJsonPrimitive();

        return id.isString() || id.isNumber();
    }

    /**
     * Returns the response of the dispatcher to a single message. An error
     * response without 'id' rejects the message.
     *
     * @param aResponse - response or <code>null</code>, if there is none
     * @return response of the dispatcher
     */
    private static McpResponse toResponse(JsonObject aResponse) {

        if (aResponse == null) {
            return McpResponse.noAnswer();
        }

        String json = GSON.toJson(aResponse);
        if (!aResponse.has(KEY_ID)) {
            return McpResponse.rejected(json);
        }

        return McpResponse.answer(json);
    }

    /**
     * Checks the value of the HTTP header 'MCP-Protocol-Version' of a message.
     * After 'initialize' the client sends the negotiated protocol version in
     * this header, and the server must answer a version, that it does not
     * support, with '400 Bad Request'.
     * <p>
     * A message without the header is accepted, because the server then assumes
     * version 2025-03-26, which it supports. 'initialize' is accepted with any
     * header, because it negotiates the version.
     *
     * @param aMessage - message as received from the client
     * @param aProtocolVersion - value of the header or <code>null</code>
     * @return error response to send with '400 Bad Request' or
     *         <code>null</code>, if the message may be handled
     */
    public static String checkProtocolVersionHeader(String aMessage, String aProtocolVersion) {

        if (aProtocolVersion == null || SUPPORTED_PROTOCOL_VERSIONS.contains(aProtocolVersion.trim())) {
            return null;
        }

        if (METHOD_INITIALIZE.equals(getMethod(aMessage))) {
            return null;
        }

        return GSON.toJson(error(null, INVALID_REQUEST, "Unsupported protocol version in the header MCP-Protocol-Version: " //$NON-NLS-1$
            + aProtocolVersion.trim() + ". Supported versions: " + String.join(", ", SUPPORTED_PROTOCOL_VERSIONS) + ".")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    /**
     * Returns the method of a message.
     *
     * @return method or <code>null</code>, if the message is no JSON object
     *         with a method
     */
    private static String getMethod(String aMessage) {

        JsonElement element;
        try {
            element = new JsonParser().parse(aMessage);
        } catch (JsonParseException e) {
            return null;
        }

        if (element == null || !element.isJsonObject()) {
            return null;
        }

        return getString(element.getAsJsonObject(), KEY_METHOD);
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

        JsonObject arguments = getArguments(aParams);

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

    /**
     * Returns the arguments of a tool call. Missing arguments are returned as
     * an empty object.
     *
     * @throws InvalidParamsException if the arguments are no object
     */
    private static JsonObject getArguments(JsonObject aParams) throws InvalidParamsException {

        JsonElement arguments = aParams.get(KEY_ARGUMENTS);
        if (arguments == null || arguments.isJsonNull()) {
            return new JsonObject();
        }

        if (!arguments.isJsonObject()) {
            throw new InvalidParamsException("Invalid params: 'arguments' must be an object."); //$NON-NLS-1$
        }

        return arguments.getAsJsonObject();
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

    private static JsonObject response(JsonElement anId, JsonObject aResult) {

        JsonObject response = new JsonObject();
        response.addProperty(KEY_JSONRPC, JSONRPC_VERSION);
        response.add(KEY_ID, anId);
        response.add(KEY_RESULT, aResult);

        return response;
    }

    /**
     * Creates an error response.
     *
     * @param anId - ID of the request or <code>null</code>, if it is not known;
     *        then the response has no 'id', as the MCP schema demands, not
     *        "id": null of JSON-RPC
     * @param aCode - error code
     * @param aMessage - error message
     * @return error response
     */
    private static JsonObject error(JsonElement anId, int aCode, String aMessage) {

        JsonObject error = new JsonObject();
        error.addProperty("code", Integer.valueOf(aCode)); //$NON-NLS-1$
        error.addProperty("message", aMessage); //$NON-NLS-1$

        JsonObject response = new JsonObject();
        response.addProperty(KEY_JSONRPC, JSONRPC_VERSION);
        if (anId != null) {
            response.add(KEY_ID, anId);
        }
        response.add(KEY_ERROR, error);

        return response;
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
