/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.mcp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * HTTP server of the 'Streamable HTTP' transport of MCP. It listens on the
 * loopback address only, requires the bearer token of the server, rejects
 * protocol versions, that it does not support, in the header
 * 'MCP-Protocol-Version' and answers every request with a single JSON response
 * (no SSE streams). A message, that is no valid request, notification or
 * response, is answered with '400 Bad Request'.
 * <p>
 * Requests are handled one after the other by a single thread, so that two
 * tools never run at the same time.
 * <p>
 * The class has no Eclipse dependencies, so that it can be tested outside of
 * RDi.
 */
public class McpHttpServer {

    /** Path of the MCP endpoint */
    public static final String PATH = "/mcp"; //$NON-NLS-1$

    /** Maximum size of a request in bytes */
    public static final int MAX_REQUEST_SIZE = 4 * 1024 * 1024;

    /** HTTP header with the negotiated protocol version */
    public static final String HEADER_PROTOCOL_VERSION = "MCP-Protocol-Version"; //$NON-NLS-1$

    private static final String BEARER = "Bearer "; //$NON-NLS-1$
    private static final String POST = "POST"; //$NON-NLS-1$
    private static final String THREAD_NAME = "iAgentBridge Server"; //$NON-NLS-1$

    private static final Pattern LOCAL_ORIGIN = Pattern.compile("https?://(localhost|127\\.0\\.0\\.1|\\[::1\\])(:\\d+)?/?", //$NON-NLS-1$
        Pattern.CASE_INSENSITIVE);

    private final McpDispatcher dispatcher;
    private final byte[] token;

    private HttpServer server;
    private ExecutorService executor;

    /**
     * Creates the server.
     *
     * @param aDispatcher - dispatcher, that handles the MCP messages
     * @param aToken - token, that the client must send as 'Authorization:
     *        Bearer &lt;token&gt;'
     */
    public McpHttpServer(McpDispatcher aDispatcher, String aToken) {

        if (aToken == null || aToken.trim().isEmpty()) {
            throw new IllegalArgumentException("The token must not be empty."); //$NON-NLS-1$
        }

        dispatcher = aDispatcher;
        token = aToken.trim().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Starts the server on the loopback address.
     *
     * @param aPort - port or 0 for a free port
     * @throws IOException if the port cannot be bound
     */
    public synchronized void start(int aPort) throws IOException {

        if (server != null) {
            throw new IllegalStateException("The server is already running."); //$NON-NLS-1$
        }

        HttpServer httpServer = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), aPort), 0);
        httpServer.createContext(PATH, this::handleExchange);

        executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, THREAD_NAME);
            thread.setDaemon(true);
            return thread;
        });
        httpServer.setExecutor(executor);
        httpServer.start();

        server = httpServer;
    }

    /**
     * Returns the port, the server is listening on.
     *
     * @return port or -1, if the server is not running
     */
    public synchronized int getPort() {

        if (server == null) {
            return -1;
        }

        return server.getAddress().getPort();
    }

    /**
     * Stops the server.
     */
    public synchronized void stop() {

        if (server == null) {
            return;
        }

        server.stop(0);
        executor.shutdownNow();
        server = null;
        executor = null;
    }

    /**
     * Returns whether a request with the given 'Origin' header is allowed. MCP
     * servers must check it against DNS rebinding attacks. Requests without the
     * header (no browser) and requests of local pages are allowed.
     *
     * @param anOrigin - value of the 'Origin' header or <code>null</code>
     * @return <code>true</code>, if the request is allowed
     */
    public static boolean isAllowedOrigin(String anOrigin) {

        if (anOrigin == null) {
            return true;
        }

        return LOCAL_ORIGIN.matcher(anOrigin.trim()).matches();
    }

    /**
     * Returns whether the 'Authorization' header carries the token of the
     * server.
     *
     * @param anAuthorization - value of the 'Authorization' header or
     *        <code>null</code>
     * @return <code>true</code>, if the token matches
     */
    public boolean isAuthorized(String anAuthorization) {

        if (anAuthorization == null) {
            return false;
        }

        String value = anAuthorization.trim();
        if (!value.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            return false;
        }

        byte[] givenToken = value.substring(BEARER.length()).trim().getBytes(StandardCharsets.UTF_8);

        return MessageDigest.isEqual(token, givenToken);
    }

    private void handleExchange(HttpExchange anExchange) throws IOException {

        try {

            if (!PATH.equals(anExchange.getRequestURI().getPath())) {
                sendStatus(anExchange, 404);
                return;
            }

            if (!isAllowedOrigin(anExchange.getRequestHeaders().getFirst("Origin"))) { //$NON-NLS-1$
                sendStatus(anExchange, 403);
                return;
            }

            if (!isAuthorized(anExchange.getRequestHeaders().getFirst("Authorization"))) { //$NON-NLS-1$
                sendStatus(anExchange, 401);
                return;
            }

            if (!POST.equals(anExchange.getRequestMethod())) {
                // No SSE stream for GET and no session to DELETE.
                anExchange.getResponseHeaders().set("Allow", POST); //$NON-NLS-1$
                sendStatus(anExchange, 405);
                return;
            }

            String request = readRequest(anExchange.getRequestBody());
            if (request == null) {
                sendStatus(anExchange, 413);
                return;
            }

            String versionError = McpDispatcher.checkProtocolVersionHeader(request, anExchange.getRequestHeaders().getFirst(HEADER_PROTOCOL_VERSION));
            if (versionError != null) {
                sendJson(anExchange, 400, versionError);
                return;
            }

            McpResponse response = dispatcher.handle(request);
            if (response.getJson() == null) {
                sendStatus(anExchange, 202);
                return;
            }

            sendJson(anExchange, response.isRejected() ? 400 : 200, response.getJson());

        } catch (RuntimeException e) {
            sendInternalError(anExchange);
        } finally {
            anExchange.close();
        }
    }

    private static void sendStatus(HttpExchange anExchange, int aStatus) throws IOException {
        anExchange.sendResponseHeaders(aStatus, -1);
    }

    private static void sendJson(HttpExchange anExchange, int aStatus, String aJson) throws IOException {

        byte[] bytes = aJson.getBytes(StandardCharsets.UTF_8);
        anExchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8"); //$NON-NLS-1$ //$NON-NLS-2$
        anExchange.sendResponseHeaders(aStatus, bytes.length);
        try (OutputStream out = anExchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static void sendInternalError(HttpExchange anExchange) {
        try {
            sendStatus(anExchange, 500);
        } catch (IOException e) {
            // Response headers already sent.
        }
    }

    /**
     * Reads the request body as UTF-8.
     *
     * @return request or <code>null</code>, if it exceeds the maximum size
     */
    private static String readRequest(InputStream anInput) throws IOException {

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = anInput.read(buffer)) != -1) {
            bytes.write(buffer, 0, count);
            if (bytes.size() > MAX_REQUEST_SIZE) {
                return null;
            }
        }

        return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
    }
}
