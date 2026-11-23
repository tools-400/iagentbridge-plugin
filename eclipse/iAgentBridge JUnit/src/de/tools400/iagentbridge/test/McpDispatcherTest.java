/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import de.tools400.iagentbridge.mcp.IMcpTool;
import de.tools400.iagentbridge.mcp.McpDispatcher;
import de.tools400.iagentbridge.mcp.McpResponse;
import de.tools400.iagentbridge.mcp.McpToolResult;

public class McpDispatcherTest {

    private final McpDispatcher dispatcher = new McpDispatcher("test-server", "1.2.3", "Some instructions.",
        Arrays.<IMcpTool> asList(new EchoTool(), new FailingTool()));

    @Test
    public void testInitialize_supportedVersion() {

        JsonObject result = result(
            handle("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{}}}"));

        assertEquals("2025-03-26", result.get("protocolVersion").getAsString());
        assertEquals("test-server", result.getAsJsonObject("serverInfo").get("name").getAsString());
        assertEquals("1.2.3", result.getAsJsonObject("serverInfo").get("version").getAsString());
        assertFalse(result.getAsJsonObject("capabilities").getAsJsonObject("tools").get("listChanged").getAsBoolean());
        assertEquals("Some instructions.", result.get("instructions").getAsString());
    }

    /**
     * 2025-11-25 is the latest version, that the server supports. Claude Code
     * asks for it.
     */
    @Test
    public void testInitialize_latestVersion() {

        JsonObject result = result(
            handle("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-11-25\"}}"));

        assertEquals("2025-11-25", result.get("protocolVersion").getAsString());
        assertEquals("2025-11-25", McpDispatcher.LATEST_PROTOCOL_VERSION);
    }

    @Test
    public void testInitialize_olderVersion() {

        JsonObject result = result(
            handle("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-06-18\"}}"));

        assertEquals("2025-06-18", result.get("protocolVersion").getAsString());
    }

    @Test
    public void testInitialize_unknownVersion() {

        JsonObject result = result(
            handle("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2099-01-01\"}}"));

        assertEquals(McpDispatcher.LATEST_PROTOCOL_VERSION, result.get("protocolVersion").getAsString());
    }

    @Test
    public void testInitialize_withoutInstructions() {

        McpDispatcher noInstructions = new McpDispatcher("s", "1", null, Arrays.<IMcpTool> asList());
        JsonObject result = result(handle(noInstructions, "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\"}"));

        assertEquals(McpDispatcher.LATEST_PROTOCOL_VERSION, result.get("protocolVersion").getAsString());
        assertFalse(result.has("instructions"));
    }

    @Test
    public void testNotification() {
        assertNull(handle("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}"));
    }

    @Test
    public void testResponseOfClient() {
        assertNull(handle("{\"jsonrpc\":\"2.0\",\"id\":7,\"result\":{}}"));
    }

    @Test
    public void testPing_stringId() {

        JsonObject response = parse(handle("{\"jsonrpc\":\"2.0\",\"id\":\"abc\",\"method\":\"ping\"}"));

        assertEquals("2.0", response.get("jsonrpc").getAsString());
        assertEquals("abc", response.get("id").getAsString());
        assertTrue(response.getAsJsonObject("result").entrySet().isEmpty());
    }

    @Test
    public void testToolsList() {

        JsonArray tools = result(handle("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}")).getAsJsonArray("tools");

        assertEquals(2, tools.size());
        JsonObject echo = tools.get(0).getAsJsonObject();
        assertEquals("echo", echo.get("name").getAsString());
        assertEquals("Returns the text.", echo.get("description").getAsString());
        assertEquals("object", echo.getAsJsonObject("inputSchema").get("type").getAsString());
        assertEquals("failing", tools.get(1).getAsJsonObject().get("name").getAsString());
    }

    /**
     * A tool, that fails to describe itself, makes 'tools/list' fail with a
     * JSON-RPC error instead of an exception.
     */
    @Test
    public void testToolsList_internalError() {

        McpDispatcher broken = new McpDispatcher("s", "1", null, Arrays.<IMcpTool> asList(new BrokenTool()));
        JsonObject response = parse(handle(broken, "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}"));

        assertEquals(2, response.get("id").getAsInt());
        assertEquals(McpDispatcher.INTERNAL_ERROR, response.getAsJsonObject("error").get("code").getAsInt());
        assertEquals("Internal error: java.lang.IllegalStateException: no description",
            response.getAsJsonObject("error").get("message").getAsString());
    }

    /**
     * An internal error is written to the error log with its cause.
     */
    @Test
    public void testInternalError_logged() {

        final List<String> messages = new ArrayList<String>();
        final List<Throwable> causes = new ArrayList<Throwable>();
        McpDispatcher broken = new McpDispatcher("s", "1", null, Arrays.<IMcpTool> asList(new BrokenTool()), (aMessage, aThrowable) -> {
            messages.add(aMessage);
            causes.add(aThrowable);
        });

        handle(broken, "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}");

        assertEquals(Arrays.asList("MCP request 'tools/list' failed: java.lang.IllegalStateException: no description"), messages);
        assertEquals(1, causes.size());
        assertEquals("no description", causes.get(0).getMessage());
    }

    /**
     * An exception of a tool is returned as a tool error and written to the
     * error log with its cause.
     */
    @Test
    public void testToolException_logged() {

        final List<String> messages = new ArrayList<String>();
        final List<Throwable> causes = new ArrayList<Throwable>();
        McpDispatcher logged = new McpDispatcher("s", "1", null, Arrays.<IMcpTool> asList(new FailingTool()), (aMessage, aThrowable) -> {
            messages.add(aMessage);
            causes.add(aThrowable);
        });

        JsonObject result = result(handle(logged, "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"failing\"}}"));

        assertTrue(result.get("isError").getAsBoolean());
        assertEquals(Arrays.asList("MCP tool 'failing' failed: java.lang.IllegalStateException: broken\nCaused by: java.io.IOException: disk full"),
            messages);
        assertEquals(1, causes.size());
        assertEquals("broken", causes.get(0).getMessage());
    }

    /**
     * Invalid arguments of a tool and errors of the client are not written to
     * the error log.
     */
    @Test
    public void testOtherErrors_notLogged() {

        final List<String> messages = new ArrayList<String>();
        McpDispatcher logged = new McpDispatcher("s", "1", null, Arrays.<IMcpTool> asList(new EchoTool()),
            (aMessage, aThrowable) -> messages.add(aMessage));

        handle(logged, "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\"echo\"}}");
        handle(logged, "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"unknown\"}}");
        handle(logged, "{\"jsonrpc\":");

        assertTrue(messages.toString(), messages.isEmpty());
    }

    /**
     * A tool, that refers to a missing class, makes 'tools/list' fail with a
     * JSON-RPC error instead of an error.
     */
    @Test
    public void testToolsList_linkageError() {

        McpDispatcher broken = new McpDispatcher("s", "1", null,
            Arrays.<IMcpTool> asList(new BrokenTool(new NoClassDefFoundError("org/eclipse/Missing"))));
        JsonObject response = parse(handle(broken, "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}"));

        assertEquals(2, response.get("id").getAsInt());
        assertEquals(McpDispatcher.INTERNAL_ERROR, response.getAsJsonObject("error").get("code").getAsInt());
        assertEquals("Internal error: java.lang.NoClassDefFoundError: org/eclipse/Missing",
            response.getAsJsonObject("error").get("message").getAsString());
    }

    @Test
    public void testToolsCall_success() {

        JsonObject result = result(
            handle("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"echo\",\"arguments\":{\"text\":\"a<b='c'\"}}}"));

        assertFalse(result.get("isError").getAsBoolean());
        JsonObject content = result.getAsJsonArray("content").get(0).getAsJsonObject();
        assertEquals("text", content.get("type").getAsString());
        assertEquals("a<b='c'", content.get("text").getAsString());
    }

    @Test
    public void testToolsCall_noHtmlEscaping() {

        String response = handle(
            "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"echo\",\"arguments\":{\"text\":\"a<b='c'\"}}}");

        assertTrue(response, response.contains("a<b='c'"));
    }

    @Test
    public void testToolsCall_illegalArgument() {

        JsonObject result = result(handle("{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\",\"params\":{\"name\":\"echo\"}}"));

        assertTrue(result.get("isError").getAsBoolean());
        assertEquals("Argument 'text' is missing.", text(result));
    }

    @Test
    public void testToolsCall_argumentsNull() {

        JsonObject result = result(
            handle("{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\",\"params\":{\"name\":\"echo\",\"arguments\":null}}"));

        assertTrue(result.get("isError").getAsBoolean());
        assertEquals("Argument 'text' is missing.", text(result));
    }

    @Test
    public void testToolsCall_argumentsNotAnObject() {

        JsonObject response = parse(
            handle("{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\",\"params\":{\"name\":\"echo\",\"arguments\":[\"a\"]}}"));

        assertEquals(4, response.get("id").getAsInt());
        assertEquals(McpDispatcher.INVALID_PARAMS, response.getAsJsonObject("error").get("code").getAsInt());
        assertEquals("Invalid params: 'arguments' must be an object.", response.getAsJsonObject("error").get("message").getAsString());
    }

    @Test
    public void testToolsCall_exception() {

        JsonObject result = result(
            handle("{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tools/call\",\"params\":{\"name\":\"failing\",\"arguments\":{}}}"));

        assertTrue(result.get("isError").getAsBoolean());
        assertEquals("java.lang.IllegalStateException: broken\nCaused by: java.io.IOException: disk full", text(result));
    }

    @Test
    public void testToolsCall_unknownTool() {

        JsonObject error = error(handle("{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"tools/call\",\"params\":{\"name\":\"unknown\"}}"));

        assertEquals(McpDispatcher.INVALID_PARAMS, error.get("code").getAsInt());
        assertEquals("Unknown tool: unknown", error.get("message").getAsString());
    }

    @Test
    public void testToolsCall_missingName() {

        JsonObject error = error(handle("{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"tools/call\",\"params\":{}}"));

        assertEquals(McpDispatcher.INVALID_PARAMS, error.get("code").getAsInt());
    }

    /**
     * An error response, whose request is unknown, has no 'id' (MCP schema),
     * not "id": null.
     */
    @Test
    public void testParseError() {

        JsonObject response = parse(handle("{\"jsonrpc\":"));

        assertFalse(response.has("id"));
        assertEquals(McpDispatcher.PARSE_ERROR, response.getAsJsonObject("error").get("code").getAsInt());
    }

    @Test
    public void testNotAnObject() {

        JsonObject response = parse(handle("\"ping\""));

        assertFalse(response.has("id"));
        assertEquals(McpDispatcher.INVALID_REQUEST, response.getAsJsonObject("error").get("code").getAsInt());
    }

    /**
     * Protocol version 2025-03-26 requires the server to accept JSON-RPC
     * batches.
     */
    @Test
    public void testBatch() {

        JsonArray responses = parseArray(handle("[{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"},"
            + "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"},{\"jsonrpc\":\"2.0\",\"id\":\"b\",\"method\":\"tools/list\"}]"));

        assertEquals(2, responses.size());
        assertEquals(1, responses.get(0).getAsJsonObject().get("id").getAsInt());
        assertTrue(responses.get(0).getAsJsonObject().has("result"));
        assertEquals("b", responses.get(1).getAsJsonObject().get("id").getAsString());
        assertEquals(2, responses.get(1).getAsJsonObject().getAsJsonObject("result").getAsJsonArray("tools").size());
    }

    @Test
    public void testBatch_notificationsOnly() {
        assertNull(handle("[{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"},{\"jsonrpc\":\"2.0\",\"id\":7,\"result\":{}}]"));
    }

    @Test
    public void testBatch_empty() {

        JsonObject response = parse(handle("[]"));

        assertFalse(response.has("id"));
        assertEquals(McpDispatcher.INVALID_REQUEST, response.getAsJsonObject("error").get("code").getAsInt());
    }

    @Test
    public void testBatch_invalidElement() {

        JsonArray responses = parseArray(handle("[1,{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"ping\"}]"));

        assertEquals(2, responses.size());
        JsonObject invalid = responses.get(0).getAsJsonObject();
        assertFalse(invalid.has("id"));
        assertEquals(McpDispatcher.INVALID_REQUEST, invalid.getAsJsonObject("error").get("code").getAsInt());
        assertEquals(2, responses.get(1).getAsJsonObject().get("id").getAsInt());
    }

    @Test
    public void testBatch_invalidOnly() {

        JsonArray responses = parseArray(handle("[1,{\"jsonrpc\":\"1.0\",\"method\":\"notifications/initialized\"}]"));

        assertEquals(2, responses.size());
        for (int i = 0; i < responses.size(); i++) {
            JsonObject invalid = responses.get(i).getAsJsonObject();
            assertFalse(invalid.has("id"));
            assertEquals(McpDispatcher.INVALID_REQUEST, invalid.getAsJsonObject("error").get("code").getAsInt());
        }
    }

    @Test
    public void testMethodNotFound() {

        JsonObject response = parse(handle("{\"jsonrpc\":\"2.0\",\"id\":8,\"method\":\"resources/list\"}"));

        assertEquals(8, response.get("id").getAsInt());
        assertEquals(McpDispatcher.METHOD_NOT_FOUND, response.getAsJsonObject("error").get("code").getAsInt());
    }

    @Test
    public void testWrongJsonRpcVersion() {

        JsonObject response = parse(handle("{\"jsonrpc\":\"1.0\",\"id\":9,\"method\":\"ping\"}"));

        assertEquals(9, response.get("id").getAsInt());
        assertEquals(McpDispatcher.INVALID_REQUEST, response.getAsJsonObject("error").get("code").getAsInt());
    }

    @Test
    public void testWrongJsonRpcVersion_notification() {

        JsonObject response = parse(handle("{\"jsonrpc\":\"1.0\",\"method\":\"notifications/initialized\"}"));

        assertFalse(response.has("id"));
        assertEquals(McpDispatcher.INVALID_REQUEST, response.getAsJsonObject("error").get("code").getAsInt());
    }

    @Test
    public void testNullId() {

        JsonObject response = parse(handle("{\"jsonrpc\":\"2.0\",\"id\":null,\"method\":\"ping\"}"));

        assertFalse(response.has("id"));
        assertEquals(McpDispatcher.INVALID_REQUEST, response.getAsJsonObject("error").get("code").getAsInt());
    }

    /**
     * The ID of a request must be a string or a number.
     */
    @Test
    public void testInvalidId() {

        for (String id : new String[] { "true", "{\"x\":1}", "[1]" }) {
            JsonObject response = parse(handle("{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"method\":\"ping\"}"));
            assertFalse(id, response.has("id"));
            assertFalse(id, response.has("result"));
            assertEquals(id, McpDispatcher.INVALID_REQUEST, response.getAsJsonObject("error").get("code").getAsInt());
        }
    }

    @Test
    public void testMissingMethod() {

        JsonObject response = parse(handle("{\"jsonrpc\":\"2.0\",\"id\":10}"));

        assertEquals(10, response.get("id").getAsInt());
        assertEquals(McpDispatcher.INVALID_REQUEST, response.getAsJsonObject("error").get("code").getAsInt());
    }

    @Test
    public void testMissingMethod_noId() {

        JsonObject response = parse(handle("{\"jsonrpc\":\"2.0\"}"));

        assertFalse(response.has("id"));
        assertEquals(McpDispatcher.INVALID_REQUEST, response.getAsJsonObject("error").get("code").getAsInt());
    }

    /**
     * A message is rejected, when the error response has no 'id'. The HTTP
     * server answers it with '400 Bad Request'.
     */
    @Test
    public void testRejected() {

        assertRejected(false, false, "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}");
        assertRejected(false, false, "[{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}]");
        assertRejected(false, true, "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}");
        assertRejected(false, true, "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"unknown\"}");
        assertRejected(false, true, "[1,{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"ping\"}]");
        assertRejected(true, true, "{\"jsonrpc\":");
        assertRejected(true, true, "[]");
        assertRejected(true, true, "{\"jsonrpc\":\"1.0\",\"method\":\"notifications/initialized\"}");
        assertRejected(true, true, "{\"jsonrpc\":\"2.0\",\"id\":true,\"method\":\"ping\"}");
        assertRejected(true, true, "[1]");
        assertRejected(true, true, "[{\"jsonrpc\":\"1.0\",\"method\":\"notifications/initialized\"}]");
        assertRejected(true, true, "[{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"},1]");
    }

    private void assertRejected(boolean aRejected, boolean aJson, String aMessage) {

        McpResponse response = dispatcher.handle(aMessage);

        assertEquals(aMessage, aRejected, response.isRejected());
        assertEquals(aMessage, aJson, response.getJson() != null);
    }

    @Test
    public void testCheckProtocolVersionHeader_accepted() {

        String ping = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}";

        assertNull(McpDispatcher.checkProtocolVersionHeader(ping, null));
        for (String version : McpDispatcher.SUPPORTED_PROTOCOL_VERSIONS) {
            assertNull(version, McpDispatcher.checkProtocolVersionHeader(ping, version));
        }
        assertNull(McpDispatcher.checkProtocolVersionHeader(ping, " " + McpDispatcher.LATEST_PROTOCOL_VERSION + " "));
    }

    @Test
    public void testCheckProtocolVersionHeader_initialize() {
        assertNull(McpDispatcher.checkProtocolVersionHeader(
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2099-01-01\"}}", "2099-01-01"));
    }

    @Test
    public void testCheckProtocolVersionHeader_unsupported() {

        JsonObject response = parse(McpDispatcher.checkProtocolVersionHeader("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}", "2099-01-01"));

        assertFalse(response.has("id"));
        assertEquals(McpDispatcher.INVALID_REQUEST, response.getAsJsonObject("error").get("code").getAsInt());
        assertEquals(
            "Unsupported protocol version in the header MCP-Protocol-Version: 2099-01-01."
                + " Supported versions: 2025-11-25, 2025-06-18, 2025-03-26, 2024-11-05.",
            response.getAsJsonObject("error").get("message").getAsString());
    }

    @Test
    public void testCheckProtocolVersionHeader_noJson() {
        assertTrue(McpDispatcher.checkProtocolVersionHeader("{no json", "2099-01-01").contains("2099-01-01"));
        assertTrue(McpDispatcher.checkProtocolVersionHeader("[{\"method\":\"initialize\"}]", "2099-01-01").contains("2099-01-01"));
    }

    /**
     * Returns the JSON text, that the dispatcher sends for a message.
     *
     * @return JSON text or <code>null</code>, if nothing is sent
     */
    private String handle(String aMessage) {
        return handle(dispatcher, aMessage);
    }

    private static String handle(McpDispatcher aDispatcher, String aMessage) {
        return aDispatcher.handle(aMessage).getJson();
    }

    private static JsonObject parse(String aResponse) {
        return new JsonParser().parse(aResponse).getAsJsonObject();
    }

    private static JsonArray parseArray(String aResponse) {
        return new JsonParser().parse(aResponse).getAsJsonArray();
    }

    private static JsonObject result(String aResponse) {

        JsonObject response = parse(aResponse);
        assertFalse(aResponse, response.has("error"));

        return response.getAsJsonObject("result");
    }

    private static JsonObject error(String aResponse) {

        JsonObject response = parse(aResponse);
        assertFalse(aResponse, response.has("result"));

        return response.getAsJsonObject("error");
    }

    private static String text(JsonObject aResult) {
        return aResult.getAsJsonArray("content").get(0).getAsJsonObject().get("text").getAsString();
    }

    /**
     * Returns the argument 'text'.
     */
    static class EchoTool implements IMcpTool {

        @Override
        public String getName() {
            return "echo";
        }

        @Override
        public String getDescription() {
            return "Returns the text.";
        }

        @Override
        public JsonObject getInputSchema() {
            return new JsonParser().parse("{\"type\":\"object\",\"properties\":{\"text\":{\"type\":\"string\"}},\"required\":[\"text\"]}")
                .getAsJsonObject();
        }

        @Override
        public McpToolResult execute(JsonObject anArguments) {

            if (!anArguments.has("text")) {
                throw new IllegalArgumentException("Argument 'text' is missing.");
            }

            return McpToolResult.success(anArguments.get("text").getAsString());
        }
    }

    /**
     * Fails with an exception, that has a cause.
     */
    static class FailingTool implements IMcpTool {

        @Override
        public String getName() {
            return "failing";
        }

        @Override
        public String getDescription() {
            return "Fails.";
        }

        @Override
        public JsonObject getInputSchema() {
            return new JsonParser().parse("{\"type\":\"object\"}").getAsJsonObject();
        }

        @Override
        public McpToolResult execute(JsonObject anArguments) {
            throw new IllegalStateException("broken", new java.io.IOException("disk full"));
        }
    }

    /**
     * Fails to describe itself.
     */
    static class BrokenTool implements IMcpTool {

        private final Throwable failure;

        BrokenTool() {
            this(new IllegalStateException("no description"));
        }

        BrokenTool(Throwable aFailure) {
            failure = aFailure;
        }

        @Override
        public String getName() {
            return "broken";
        }

        @Override
        public String getDescription() {
            if (failure instanceof Error) {
                throw (Error)failure;
            }
            throw (RuntimeException)failure;
        }

        @Override
        public JsonObject getInputSchema() {
            return new JsonParser().parse("{\"type\":\"object\"}").getAsJsonObject();
        }

        @Override
        public McpToolResult execute(JsonObject anArguments) {
            return McpToolResult.success("");
        }
    }
}
