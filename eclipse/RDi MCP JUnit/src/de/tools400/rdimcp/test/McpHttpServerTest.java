/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.rdimcp.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import de.tools400.rdimcp.mcp.IMcpTool;
import de.tools400.rdimcp.mcp.McpDispatcher;
import de.tools400.rdimcp.mcp.McpHttpServer;

public class McpHttpServerTest {

    private static final String TOKEN = "secret-token";
    private static final String PING = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}";

    private McpHttpServer server;

    @Before
    public void setUp() throws IOException {
        McpDispatcher dispatcher = new McpDispatcher("test-server", "1.0", null, Arrays.<IMcpTool> asList(new McpDispatcherTest.EchoTool()));
        server = new McpHttpServer(dispatcher, TOKEN);
        server.start(0);
    }

    @After
    public void tearDown() {
        server.stop();
    }

    @Test
    public void testPost_request() throws IOException {

        Response response = send("POST", McpHttpServer.PATH, "Bearer " + TOKEN, null,
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"echo\",\"arguments\":{\"text\":\"Grüße\"}}}");

        assertEquals(200, response.status);
        assertTrue(response.contentType, response.contentType.startsWith("application/json"));
        JsonObject result = new JsonParser().parse(response.body).getAsJsonObject().getAsJsonObject("result");
        assertEquals("Grüße", result.getAsJsonArray("content").get(0).getAsJsonObject().get("text").getAsString());
    }

    @Test
    public void testPost_notification() throws IOException {

        Response response = send("POST", McpHttpServer.PATH, "Bearer " + TOKEN, null,
            "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}");

        assertEquals(202, response.status);
        assertEquals("", response.body);
    }

    @Test
    public void testMissingToken() throws IOException {
        assertEquals(401, send("POST", McpHttpServer.PATH, null, null, PING).status);
    }

    @Test
    public void testWrongToken() throws IOException {
        assertEquals(401, send("POST", McpHttpServer.PATH, "Bearer wrong", null, PING).status);
    }

    @Test
    public void testGet() throws IOException {

        Response response = send("GET", McpHttpServer.PATH, "Bearer " + TOKEN, null, null);

        assertEquals(405, response.status);
        assertEquals("POST", response.allow);
    }

    @Test
    public void testForeignOrigin() throws IOException {
        assertEquals(403, send("POST", McpHttpServer.PATH, "Bearer " + TOKEN, "http://evil.example.com", PING).status);
    }

    @Test
    public void testLocalOrigin() throws IOException {
        assertEquals(200, send("POST", McpHttpServer.PATH, "Bearer " + TOKEN, "http://localhost:6274", PING).status);
    }

    @Test
    public void testWrongPath() throws IOException {
        assertEquals(404, send("POST", McpHttpServer.PATH + "x", "Bearer " + TOKEN, null, PING).status);
    }

    @Test
    public void testIsAllowedOrigin() {
        assertTrue(McpHttpServer.isAllowedOrigin(null));
        assertTrue(McpHttpServer.isAllowedOrigin("http://127.0.0.1:39096"));
        assertTrue(McpHttpServer.isAllowedOrigin("https://LOCALHOST"));
        assertTrue(McpHttpServer.isAllowedOrigin("http://[::1]:8080/"));
        assertFalse(McpHttpServer.isAllowedOrigin("null"));
        assertFalse(McpHttpServer.isAllowedOrigin("http://localhost.evil.com"));
        assertFalse(McpHttpServer.isAllowedOrigin("http://127.0.0.1.evil.com:80"));
    }

    @Test
    public void testIsAuthorized() {
        assertTrue(server.isAuthorized("Bearer " + TOKEN));
        assertTrue(server.isAuthorized("bearer  " + TOKEN + " "));
        assertFalse(server.isAuthorized(null));
        assertFalse(server.isAuthorized(TOKEN));
        assertFalse(server.isAuthorized("Basic " + TOKEN));
        assertFalse(server.isAuthorized("Bearer " + TOKEN + "x"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmptyToken() {
        new McpHttpServer(null, " ");
    }

    @Test
    public void testStop() {

        assertTrue(server.getPort() > 0);
        server.stop();
        assertEquals(-1, server.getPort());
    }

    /**
     * Sends a raw HTTP request. HttpURLConnection is not used, because it
     * silently drops the restricted header 'Origin'.
     */
    private Response send(String aMethod, String aPath, String anAuthorization, String anOrigin, String aBody) throws IOException {

        byte[] body = aBody == null ? new byte[0] : aBody.getBytes(StandardCharsets.UTF_8);

        StringBuilder request = new StringBuilder();
        request.append(aMethod).append(" ").append(aPath).append(" HTTP/1.1\r\n");
        request.append("Host: 127.0.0.1:").append(server.getPort()).append("\r\n");
        request.append("Accept: application/json, text/event-stream\r\n");
        request.append("Connection: close\r\n");
        if (anAuthorization != null) {
            request.append("Authorization: ").append(anAuthorization).append("\r\n");
        }
        if (anOrigin != null) {
            request.append("Origin: ").append(anOrigin).append("\r\n");
        }
        if (aBody != null) {
            request.append("Content-Type: application/json\r\n");
        }
        request.append("Content-Length: ").append(body.length).append("\r\n\r\n");

        byte[] raw;
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), server.getPort())) {
            socket.setSoTimeout(10000);
            OutputStream out = socket.getOutputStream();
            out.write(request.toString().getBytes(StandardCharsets.US_ASCII));
            out.write(body);
            out.flush();
            raw = read(socket.getInputStream());
        }

        String text = new String(raw, StandardCharsets.UTF_8);
        int endOfHeaders = text.indexOf("\r\n\r\n");
        String[] headerLines = text.substring(0, endOfHeaders).split("\r\n");

        Response response = new Response();
        response.status = Integer.parseInt(headerLines[0].split(" ")[1]);
        for (int i = 1; i < headerLines.length; i++) {
            int colon = headerLines[i].indexOf(':');
            response.headers.put(headerLines[i].substring(0, colon).trim().toLowerCase(Locale.ENGLISH), headerLines[i].substring(colon + 1).trim());
        }
        response.contentType = response.headers.get("content-type");
        response.allow = response.headers.get("allow");
        response.body = text.substring(endOfHeaders + 4);

        return response;
    }

    private static byte[] read(InputStream anInput) throws IOException {

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = anInput.read(buffer)) != -1) {
            bytes.write(buffer, 0, count);
        }

        return bytes.toByteArray();
    }

    private static class Response {
        int status;
        Map<String, String> headers = new HashMap<String, String>();
        String contentType;
        String allow;
        String body;
    }
}
