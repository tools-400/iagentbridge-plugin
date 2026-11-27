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
import java.net.InetAddress;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        assertEquals("The test instance \"t\" (port " + port + ") does not accept connections yet - still starting? Nothing was sent: repeat it "
            + "in a few seconds; list_launches shows, whether the test instance runs.", result.getText());
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
        assertEquals(
            "The test instance \"t\" (port " + server.getPort() + ") did not answer within 1 s - stopped in the debugger? "
                + "list_launches shows, whether it is. If " + server.getPort() + " is the port of this RDi, correct -Diagentbridge.port of the "
                + "launch configuration. If it was an action, it may have run: check its result in a snapshot before you repeat it.",
            result.getText());
    }

    @Test
    public void testCall_unknownTool() throws IOException {

        start(new TestTool("other_tool", anArguments -> McpToolResult.success("other")));

        McpToolResult result = new ForwardingClient().call(instance(server.getPort()), TOKEN, "ui_snapshot", new JsonObject());

        assertTrue(result.isError());
        assertTrue(result.getText(), result.getText().contains("belongs to an RDi without the UI tools of iAgentBridge"));
    }

    /**
     * The kind of the outcome of a call, so that stop_launch decides without
     * comparing the texts of messages.
     */
    @Test
    public void testSend_kinds() throws IOException {

        start(new TestTool("ui_test", anArguments -> McpToolResult.error("UI thread busy for 10 s")));
        ForwardingClient.Response answered = new ForwardingClient().send(instance(server.getPort()), TOKEN, "ui_test", new JsonObject());
        assertEquals(ForwardingClient.Kind.ANSWERED, answered.getKind());
        assertTrue(answered.getResult().getText().endsWith("\nUI thread busy for 10 s"));

        ForwardingClient.Response rejected = new ForwardingClient().send(instance(server.getPort()), "wrong-token", "ui_test", new JsonObject());
        assertEquals(ForwardingClient.Kind.REJECTED, rejected.getKind());
        server.stop();
        server = null;

        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        assertEquals(ForwardingClient.Kind.REFUSED, new ForwardingClient().send(instance(port), TOKEN, "ui_test", new JsonObject()).getKind());

        AtomicInteger requests = new AtomicInteger();
        try (ServerSocket socket = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))) {
            Thread acceptor = new Thread(() -> closeWithoutAnswer(socket, requests), "closeWithoutAnswer");
            acceptor.setDaemon(true);
            acceptor.start();
            assertEquals(ForwardingClient.Kind.BROKEN,
                new ForwardingClient(2000, 5000).send(instance(socket.getLocalPort()), TOKEN, "ui_close", new JsonObject()).getKind());
        }

        start(new TestTool("ui_test", anArguments -> {
            sleep(2500);
            return McpToolResult.success("late");
        }));
        assertEquals(ForwardingClient.Kind.TIMEOUT,
            new ForwardingClient(2000, 1000).send(instance(server.getPort()), TOKEN, "ui_test", new JsonObject()).getKind());
    }

    /**
     * A server, that closes the connection without an answer, gets the call
     * only once. HttpURLConnection would send the POST again silently, which
     * would be a second click of an action.
     */
    @Test
    public void testCall_closedWithoutAnswer_sentOnce() throws IOException {

        AtomicInteger requests = new AtomicInteger();
        try (ServerSocket socket = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))) {
            Thread acceptor = new Thread(() -> closeWithoutAnswer(socket, requests), "closeWithoutAnswer");
            acceptor.setDaemon(true);
            acceptor.start();

            McpToolResult result = new ForwardingClient(2000, 5000).call(instance(socket.getLocalPort()), TOKEN, "ui_click", new JsonObject());

            assertTrue(result.isError());
            sleep(300);
            assertEquals("requests", 1, requests.get());
            assertTrue(result.getText(),
                result.getText().startsWith("The call of the test instance \"t\" (port " + socket.getLocalPort() + ") failed: "));
            assertTrue(result.getText(),
                result.getText().endsWith(". If it was an action, it may have run: check its result in a snapshot before you repeat it."));
        }
    }

    /**
     * Another program at the port: it answers with an HTTP error, with no JSON
     * object or with invalid JSON.
     */
    @Test
    public void testCall_otherProgramAtThePort() throws IOException {

        String hint = ". Check -Diagentbridge.port of its launch configuration: the port may belong to another program.";
        try (ServerSocket socket = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))) {
            String answered = "The test instance \"t\" (port " + socket.getLocalPort() + ") answered with ";
            assertEquals(answered + "HTTP 404" + hint, callAnswering(socket, "404 Not Found", ""));
            assertEquals(answered + "HTTP 503: Busy" + hint, callAnswering(socket, "503 Service Unavailable", "Busy"));
            assertEquals(answered + "no JSON object: [1]" + hint, callAnswering(socket, "200 OK", "[1]"));
            String invalid = callAnswering(socket, "200 OK", "{");
            assertTrue(invalid, invalid.startsWith(answered + "invalid JSON: "));
            assertTrue(invalid, invalid.endsWith(hint));
        }
    }

    /**
     * The server of iAgentBridge in the test instance answers a call with 500,
     * if it fails, and with 413, if the call is too large: the port is right.
     */
    @Test
    public void testCall_statusOfTheServerOfTheTestInstance() throws IOException {

        try (ServerSocket socket = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))) {
            String answered = "The test instance \"t\" (port " + socket.getLocalPort() + ") answered with ";
            assertEquals(answered + "HTTP 500: Internal error. Its server failed. If it was an action, it may have run: check its result in a "
                + "snapshot before you repeat it.", callAnswering(socket, "500 Internal Server Error", "Internal error"));
            assertEquals(answered + "HTTP 413. The arguments of the call are too large: send them in smaller parts, e.g. a shorter text with "
                + "ui_type or ui_lpex.", callAnswering(socket, "413 Payload Too Large", ""));
        }
    }

    /**
     * The reason of a rejected call, without the hint for the agent, so that
     * stop_launch can give its own.
     */
    @Test
    public void testSend_reasonWithoutHint() throws IOException {

        try (ServerSocket socket = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))) {
            ForwardingClient.Response response = sendAnswering(socket, "404 Not Found", "");
            assertEquals(ForwardingClient.Kind.REJECTED, response.getKind());
            assertEquals("The test instance \"t\" (port " + socket.getLocalPort() + ") answered with HTTP 404", response.getReason());
        }
    }

    /**
     * Calls a tool at a socket, that answers the call with a status and a body,
     * and returns the text of the error.
     */
    private static String callAnswering(ServerSocket aSocket, String aStatus, String aBody) {

        McpToolResult result = sendAnswering(aSocket, aStatus, aBody).getResult();
        assertTrue(result.getText(), result.isError());
        return result.getText();
    }

    /**
     * Sends a call to a socket, that answers it with a status and a body.
     */
    private static ForwardingClient.Response sendAnswering(ServerSocket aSocket, String aStatus, String aBody) {

        Thread answerer = new Thread(() -> answer(aSocket, aStatus, aBody), "answer");
        answerer.setDaemon(true);
        answerer.start();

        return new ForwardingClient(2000, 5000).send(instance(aSocket.getLocalPort()), TOKEN, "ui_test", new JsonObject());
    }

    /**
     * Accepts one connection, reads the request and answers with a status and a
     * body.
     */
    private static void answer(ServerSocket aSocket, String aStatus, String aBody) {
        try (Socket connection = aSocket.accept()) {
            readRequest(connection.getInputStream());
            byte[] body = aBody.getBytes(StandardCharsets.UTF_8);
            String head = "HTTP/1.1 " + aStatus + "\r\nContent-Type: application/json\r\nContent-Length: " + body.length
                + "\r\nConnection: close\r\n\r\n";
            OutputStream out = connection.getOutputStream();
            out.write(head.getBytes(StandardCharsets.US_ASCII));
            out.write(body);
            out.flush();
        } catch (IOException e) {
            // The test fails on the result.
        }
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

    /**
     * Accepts connections, reads the request and closes the connection without
     * an answer.
     */
    private static void closeWithoutAnswer(ServerSocket aSocket, AtomicInteger aRequests) {
        while (!aSocket.isClosed()) {
            try (Socket connection = aSocket.accept()) {
                readRequest(connection.getInputStream());
                aRequests.incrementAndGet();
            } catch (IOException e) {
                // The socket is closed at the end of the test.
            }
        }
    }

    /**
     * Reads the head and the body of an HTTP request.
     */
    private static void readRequest(InputStream anInput) throws IOException {

        StringBuilder head = new StringBuilder();
        int c;
        while (!head.toString().endsWith("\r\n\r\n") && (c = anInput.read()) != -1) {
            head.append((char)c);
        }

        Matcher length = Pattern.compile("(?i)content-length: *(\\d+)").matcher(head);
        int remaining = length.find() ? Integer.parseInt(length.group(1)) : 0;
        while (remaining > 0 && anInput.read() != -1) {
            remaining--;
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
