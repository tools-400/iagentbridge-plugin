/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.After;
import org.junit.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import de.tools400.iagentbridge.mcp.IMcpTool;
import de.tools400.iagentbridge.mcp.McpDispatcher;
import de.tools400.iagentbridge.mcp.McpHttpServer;
import de.tools400.iagentbridge.mcp.McpToolResult;
import de.tools400.iagentbridge.ui.forwarding.ForwardingClient;
import de.tools400.iagentbridge.ui.forwarding.TestInstance;

/**
 * The forwarding client against a real server of iAgentBridge.
 */
public class ForwardingClientTest {

    private static final String TOKEN = "secret-token";

    private McpHttpServer server;
    private final List<JsonObject> calls = Collections.synchronizedList(new ArrayList<JsonObject>());

    @After
    public void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    public void testCall_textAndImage() throws IOException {

        start(new TestTool("ui_test", anArguments -> McpToolResult.success("snapshot").withImage(new byte[] { 1, 2, 3 }, "image/png")));
        JsonObject arguments = new JsonObject();
        arguments.addProperty("ref", "w1");

        McpToolResult result = new ForwardingClient().call(instance(server.getPort()), TOKEN, "ui_test", arguments);

        assertFalse(result.getText(), result.isError());
        assertEquals("Test instance \"t\" (port " + server.getPort() + "):\nsnapshot", result.getText());
        JsonArray content = result.toJson().getAsJsonArray("content");
        assertEquals(2, content.size());
        assertEquals("AQID", content.get(1).getAsJsonObject().get("data").getAsString());
        assertEquals("image/png", content.get(1).getAsJsonObject().get("mimeType").getAsString());

        assertEquals(1, calls.size());
        assertEquals("w1", calls.get(0).get("ref").getAsString());
        assertTrue(calls.get(0).get(ForwardingClient.ARGUMENT_FORWARDED).getAsBoolean());
        assertFalse("the arguments of the caller stay unchanged", arguments.has(ForwardingClient.ARGUMENT_FORWARDED));
    }

    @Test
    public void testCall_toolError() throws IOException {

        start(new TestTool("ui_test", anArguments -> McpToolResult.error("UI thread busy for 10 s")));

        McpToolResult result = new ForwardingClient().call(instance(server.getPort()), TOKEN, "ui_test", new JsonObject());

        assertTrue(result.isError());
        assertTrue(result.getText(), result.getText().endsWith("\nUI thread busy for 10 s"));
    }

    @Test
    public void testCall_wrongToken() throws IOException {

        start(new TestTool("ui_test", anArguments -> McpToolResult.success("never")));

        McpToolResult result = new ForwardingClient().call(instance(server.getPort()), "wrong-token", "ui_test", new JsonObject());

        assertTrue(result.isError());
        assertTrue(result.getText(), result.getText().contains("rejected the token (HTTP 401)"));
        assertTrue(calls.isEmpty());
    }

    @Test
    public void testCall_closedPort() throws IOException {

        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }

        McpToolResult result = new ForwardingClient().call(instance(port), TOKEN, "ui_test", new JsonObject());

        assertTrue(result.isError());
        assertEquals("The test instance \"t\" (port " + port + ") does not accept connections yet - still starting?", result.getText());
    }

    @Test
    public void testCall_timeout() throws IOException {

        start(new TestTool("ui_test", anArguments -> {
            sleep(2500);
            return McpToolResult.success("late");
        }));

        McpToolResult result = new ForwardingClient(2000, 1000).call(instance(server.getPort()), TOKEN, "ui_test", new JsonObject());

        assertTrue(result.isError());
        assertTrue(result.getText(), result.getText().contains("did not answer within 1 s - stopped in the debugger?"));
    }

    @Test
    public void testCall_unknownTool() throws IOException {

        start(new TestTool("other_tool", anArguments -> McpToolResult.success("other")));

        McpToolResult result = new ForwardingClient().call(instance(server.getPort()), TOKEN, "ui_snapshot", new JsonObject());

        assertTrue(result.isError());
        assertTrue(result.getText(), result.getText().contains("belongs to an RDi without the UI tools of iAgentBridge"));
    }

    /**
     * A launch with the UI tools and the port of the RDi, in which the
     * developer works: the RDi forwards to itself. The server handles one
     * request at a time, so the forwarded call waits until the first one timed
     * out; the mark 'forwarded' ends the chain there.
     */
    @Test
    public void testCall_toItself() throws IOException {

        start(new TestTool("loop", anArguments -> {
            if (anArguments.has(ForwardingClient.ARGUMENT_FORWARDED)) {
                return McpToolResult.error("This RDi got a forwarded call, but it is no test instance.");
            }
            return new ForwardingClient(2000, 1000).call(instance(server.getPort()), TOKEN, "loop", anArguments);
        }));

        JsonObject response = callWithoutMark(server.getPort(), "loop");

        JsonObject result = response.getAsJsonObject("result");
        assertTrue(result.get("isError").getAsBoolean());
        assertTrue(result.toString(), result.toString().contains("did not answer within 1 s"));

        sleep(1500);
        assertEquals("the forwarded call ran after the first one and stopped the chain", 2, calls.size());
        assertTrue(calls.get(1).has(ForwardingClient.ARGUMENT_FORWARDED));
        sleep(1500);
        assertEquals("no third call", 2, calls.size());
    }

    private void start(IMcpTool aTool) throws IOException {
        server = new McpHttpServer(new McpDispatcher("test-server", "1.0", null, Arrays.<IMcpTool> asList(aTool)), TOKEN);
        server.start(0);
    }

    private static TestInstance instance(int aPort) {
        return TestInstance.fromCommandLine("t", "java -Diagentbridge.uiTools=true -Diagentbridge.port=" + aPort);
    }

    /**
     * Calls a tool like Claude Code does: without the mark 'forwarded'.
     */
    private static JsonObject callWithoutMark(int aPort, String aToolName) throws IOException {

        String body = "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\",\"params\":{\"name\":\"" + aToolName + "\",\"arguments\":{}}}";
        HttpURLConnection connection = (HttpURLConnection)new URL("http://127.0.0.1:" + aPort + "/mcp").openConnection(Proxy.NO_PROXY);
        try {
            connection.setReadTimeout(10000);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json, text/event-stream");
            connection.setRequestProperty("Authorization", "Bearer " + TOKEN);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body.getBytes(StandardCharsets.UTF_8));
            }
            assertEquals(200, connection.getResponseCode());
            try (InputStream in = connection.getInputStream()) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int count;
                while ((count = in.read(buffer)) != -1) {
                    bytes.write(buffer, 0, count);
                }
                return new JsonParser().parse(new String(bytes.toByteArray(), StandardCharsets.UTF_8)).getAsJsonObject();
            }
        } finally {
            connection.disconnect();
        }
    }

    private static void sleep(long aMillis) {
        try {
            Thread.sleep(aMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Behavior of a test tool.
     */
    private interface Behavior {
        McpToolResult execute(JsonObject anArguments);
    }

    /**
     * A tool, that records its arguments and behaves as told.
     */
    private class TestTool implements IMcpTool {

        private final String name;
        private final Behavior behavior;

        TestTool(String aName, Behavior aBehavior) {
            name = aName;
            behavior = aBehavior;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDescription() {
            return "Test tool " + name;
        }

        @Override
        public JsonObject getInputSchema() {
            return new JsonParser().parse("{\"type\":\"object\",\"properties\":{}}").getAsJsonObject();
        }

        @Override
        public McpToolResult execute(JsonObject anArguments) {
            calls.add(anArguments);
            return behavior.execute(anArguments);
        }
    }
}
