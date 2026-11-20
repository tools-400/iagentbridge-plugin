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

import java.util.Arrays;

import org.junit.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import de.tools400.iagentbridge.mcp.IMcpTool;
import de.tools400.iagentbridge.mcp.McpDispatcher;
import de.tools400.iagentbridge.mcp.McpToolResult;

public class McpDispatcherTest {

    private final McpDispatcher dispatcher = new McpDispatcher("test-server", "1.2.3", "Some instructions.",
        Arrays.<IMcpTool> asList(new EchoTool(), new FailingTool()));

    @Test
    public void testInitialize_supportedVersion() {

        JsonObject result = result(dispatcher
            .handle("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{}}}"));

        assertEquals("2025-03-26", result.get("protocolVersion").getAsString());
        assertEquals("test-server", result.getAsJsonObject("serverInfo").get("name").getAsString());
        assertEquals("1.2.3", result.getAsJsonObject("serverInfo").get("version").getAsString());
        assertFalse(result.getAsJsonObject("capabilities").getAsJsonObject("tools").get("listChanged").getAsBoolean());
        assertEquals("Some instructions.", result.get("instructions").getAsString());
    }

    @Test
    public void testInitialize_unknownVersion() {

        JsonObject result = result(
            dispatcher.handle("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2099-01-01\"}}"));

        assertEquals(McpDispatcher.LATEST_PROTOCOL_VERSION, result.get("protocolVersion").getAsString());
    }

    @Test
    public void testInitialize_withoutInstructions() {

        McpDispatcher noInstructions = new McpDispatcher("s", "1", null, Arrays.<IMcpTool> asList());
        JsonObject result = result(noInstructions.handle("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\"}"));

        assertEquals(McpDispatcher.LATEST_PROTOCOL_VERSION, result.get("protocolVersion").getAsString());
        assertFalse(result.has("instructions"));
    }

    @Test
    public void testNotification() {
        assertNull(dispatcher.handle("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}"));
    }

    @Test
    public void testResponseOfClient() {
        assertNull(dispatcher.handle("{\"jsonrpc\":\"2.0\",\"id\":7,\"result\":{}}"));
    }

    @Test
    public void testPing_stringId() {

        JsonObject response = parse(dispatcher.handle("{\"jsonrpc\":\"2.0\",\"id\":\"abc\",\"method\":\"ping\"}"));

        assertEquals("2.0", response.get("jsonrpc").getAsString());
        assertEquals("abc", response.get("id").getAsString());
        assertTrue(response.getAsJsonObject("result").entrySet().isEmpty());
    }

    @Test
    public void testToolsList() {

        JsonArray tools = result(dispatcher.handle("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}")).getAsJsonArray("tools");

        assertEquals(2, tools.size());
        JsonObject echo = tools.get(0).getAsJsonObject();
        assertEquals("echo", echo.get("name").getAsString());
        assertEquals("Returns the text.", echo.get("description").getAsString());
        assertEquals("object", echo.getAsJsonObject("inputSchema").get("type").getAsString());
        assertEquals("failing", tools.get(1).getAsJsonObject().get("name").getAsString());
    }

    @Test
    public void testToolsCall_success() {

        JsonObject result = result(dispatcher
            .handle("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"echo\",\"arguments\":{\"text\":\"a<b='c'\"}}}"));

        assertFalse(result.get("isError").getAsBoolean());
        JsonObject content = result.getAsJsonArray("content").get(0).getAsJsonObject();
        assertEquals("text", content.get("type").getAsString());
        assertEquals("a<b='c'", content.get("text").getAsString());
    }

    @Test
    public void testToolsCall_noHtmlEscaping() {

        String response = dispatcher
            .handle("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"echo\",\"arguments\":{\"text\":\"a<b='c'\"}}}");

        assertTrue(response, response.contains("a<b='c'"));
    }

    @Test
    public void testToolsCall_illegalArgument() {

        JsonObject result = result(dispatcher.handle("{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\",\"params\":{\"name\":\"echo\"}}"));

        assertTrue(result.get("isError").getAsBoolean());
        assertEquals("Argument 'text' is missing.", text(result));
    }

    @Test
    public void testToolsCall_exception() {

        JsonObject result = result(
            dispatcher.handle("{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tools/call\",\"params\":{\"name\":\"failing\",\"arguments\":{}}}"));

        assertTrue(result.get("isError").getAsBoolean());
        assertEquals("java.lang.IllegalStateException: broken\nCaused by: java.io.IOException: disk full", text(result));
    }

    @Test
    public void testToolsCall_unknownTool() {

        JsonObject error = error(dispatcher.handle("{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"tools/call\",\"params\":{\"name\":\"unknown\"}}"));

        assertEquals(McpDispatcher.INVALID_PARAMS, error.get("code").getAsInt());
        assertEquals("Unknown tool: unknown", error.get("message").getAsString());
    }

    @Test
    public void testToolsCall_missingName() {

        JsonObject error = error(dispatcher.handle("{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"tools/call\",\"params\":{}}"));

        assertEquals(McpDispatcher.INVALID_PARAMS, error.get("code").getAsInt());
    }

    @Test
    public void testParseError() {

        JsonObject response = parse(dispatcher.handle("{\"jsonrpc\":"));

        assertTrue(response.has("id"));
        assertTrue(response.get("id").isJsonNull());
        assertEquals(McpDispatcher.PARSE_ERROR, response.getAsJsonObject("error").get("code").getAsInt());
    }

    @Test
    public void testBatch() {

        JsonObject response = parse(dispatcher.handle("[{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}]"));

        assertTrue(response.get("id").isJsonNull());
        assertEquals(McpDispatcher.INVALID_REQUEST, response.getAsJsonObject("error").get("code").getAsInt());
    }

    @Test
    public void testMethodNotFound() {

        JsonObject response = parse(dispatcher.handle("{\"jsonrpc\":\"2.0\",\"id\":8,\"method\":\"resources/list\"}"));

        assertEquals(8, response.get("id").getAsInt());
        assertEquals(McpDispatcher.METHOD_NOT_FOUND, response.getAsJsonObject("error").get("code").getAsInt());
    }

    @Test
    public void testWrongJsonRpcVersion() {

        JsonObject error = error(dispatcher.handle("{\"jsonrpc\":\"1.0\",\"id\":9,\"method\":\"ping\"}"));

        assertEquals(McpDispatcher.INVALID_REQUEST, error.get("code").getAsInt());
    }

    @Test
    public void testNullId() {

        JsonObject response = parse(dispatcher.handle("{\"jsonrpc\":\"2.0\",\"id\":null,\"method\":\"ping\"}"));

        assertTrue(response.get("id").isJsonNull());
        assertEquals(McpDispatcher.INVALID_REQUEST, response.getAsJsonObject("error").get("code").getAsInt());
    }

    @Test
    public void testMissingMethod() {

        JsonObject error = error(dispatcher.handle("{\"jsonrpc\":\"2.0\",\"id\":10}"));

        assertEquals(McpDispatcher.INVALID_REQUEST, error.get("code").getAsInt());
    }

    private static JsonObject parse(String aResponse) {
        return new JsonParser().parse(aResponse).getAsJsonObject();
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
}
