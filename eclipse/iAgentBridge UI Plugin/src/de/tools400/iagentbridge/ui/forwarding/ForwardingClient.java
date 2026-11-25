/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.forwarding;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import de.tools400.iagentbridge.mcp.McpToolResult;

/**
 * Calls a tool of a test instance over HTTP: 'tools/call' with the same name
 * and arguments, plus the argument 'forwarded'. The server of iAgentBridge
 * keeps no session, so no 'initialize' is needed.
 */
public final class ForwardingClient {

    /**
     * Argument, that marks a forwarded call. An RDi without the UI tools, that
     * gets such a call, rejects it instead of forwarding it again.
     */
    public static final String ARGUMENT_FORWARDED = "forwarded"; //$NON-NLS-1$

    /** Default time to connect */
    public static final int CONNECT_TIMEOUT_MILLIS = 2000;

    /**
     * Default time to wait for the answer: 10 s of the UI thread plus reserve
     */
    public static final int READ_TIMEOUT_MILLIS = 30000;

    private static final String PROTOCOL_VERSION = "2025-06-18"; //$NON-NLS-1$
    private static final int MAX_ERROR_BODY = 500;

    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;

    /**
     * Creates the client with the default timeouts.
     */
    public ForwardingClient() {
        this(CONNECT_TIMEOUT_MILLIS, READ_TIMEOUT_MILLIS);
    }

    /**
     * Creates the client.
     *
     * @param aConnectTimeoutMillis - time to connect
     * @param aReadTimeoutMillis - time to wait for the answer
     */
    public ForwardingClient(int aConnectTimeoutMillis, int aReadTimeoutMillis) {
        connectTimeoutMillis = aConnectTimeoutMillis;
        readTimeoutMillis = aReadTimeoutMillis;
    }

    /**
     * Calls a tool of a test instance.
     *
     * @param anInstance - the test instance, with a port
     * @param aToken - token of the test instance
     * @param aToolName - name of the tool
     * @param anArguments - arguments of the tool
     * @return result of the tool in the test instance, or an error result, if
     *         the test instance could not be reached
     */
    public McpToolResult call(TestInstance anInstance, String aToken, String aToolName, JsonObject anArguments) {

        JsonObject arguments = new JsonParser().parse(anArguments.toString()).getAsJsonObject();
        arguments.addProperty(ARGUMENT_FORWARDED, Boolean.TRUE);

        JsonObject params = new JsonObject();
        params.addProperty("name", aToolName); //$NON-NLS-1$
        params.add("arguments", arguments); //$NON-NLS-1$

        JsonObject request = new JsonObject();
        request.addProperty("jsonrpc", "2.0"); //$NON-NLS-1$ //$NON-NLS-2$
        request.addProperty("id", Integer.valueOf(1)); //$NON-NLS-1$
        request.addProperty("method", "tools/call"); //$NON-NLS-1$ //$NON-NLS-2$
        request.add("params", params); //$NON-NLS-1$

        int port = anInstance.getPort().intValue();
        HttpURLConnection connection = null;
        try {
            // Never through a proxy: the test instance runs on this computer.
            connection = (HttpURLConnection)new URL("http://127.0.0.1:" + port + "/mcp").openConnection(Proxy.NO_PROXY); //$NON-NLS-1$ //$NON-NLS-2$
            connection.setConnectTimeout(connectTimeoutMillis);
            connection.setReadTimeout(readTimeoutMillis);
            connection.setRequestMethod("POST"); //$NON-NLS-1$
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json"); //$NON-NLS-1$ //$NON-NLS-2$
            connection.setRequestProperty("Accept", "application/json, text/event-stream"); //$NON-NLS-1$ //$NON-NLS-2$
            connection.setRequestProperty("Authorization", "Bearer " + aToken); //$NON-NLS-1$ //$NON-NLS-2$
            connection.setRequestProperty("MCP-Protocol-Version", PROTOCOL_VERSION); //$NON-NLS-1$

            try (OutputStream out = connection.getOutputStream()) {
                out.write(request.toString().getBytes(StandardCharsets.UTF_8));
            }

            int status = connection.getResponseCode();
            if (status == HttpURLConnection.HTTP_UNAUTHORIZED) {
                return McpToolResult.error("The test instance " + anInstance.describe() //$NON-NLS-1$
                    + " rejected the token (HTTP 401). Does its launch configuration set another token with -Diagentbridge.token?"); //$NON-NLS-1$
            }
            if (status != HttpURLConnection.HTTP_OK) {
                return McpToolResult.error("The test instance " + anInstance.describe() + " answered with HTTP " + status + ": " //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    + cut(read(connection.getErrorStream())));
            }

            String body = read(connection.getInputStream());
            JsonElement response = new JsonParser().parse(body);
            if (!response.isJsonObject()) {
                return McpToolResult.error("The test instance " + anInstance.describe() + " answered with no JSON object: " + cut(body)); //$NON-NLS-1$ //$NON-NLS-2$
            }

            return ForwardedResult.toToolResult(response.getAsJsonObject(), anInstance);

        } catch (ConnectException e) {
            return notAccepting(anInstance);
        } catch (SocketTimeoutException e) {
            if (isConnectTimeout(e)) {
                return notAccepting(anInstance);
            }
            return McpToolResult.error("The test instance " + anInstance.describe() + " did not answer within " + readTimeoutMillis / 1000 //$NON-NLS-1$ //$NON-NLS-2$
                + " s - stopped in the debugger? If " + port + " is the port of this RDi, correct -Diagentbridge.port of the launch configuration."); //$NON-NLS-1$ //$NON-NLS-2$
        } catch (IOException e) {
            return McpToolResult.error("The call of the test instance " + anInstance.describe() + " failed: " + e); //$NON-NLS-1$ //$NON-NLS-2$
        } catch (JsonParseException e) {
            return McpToolResult.error("The test instance " + anInstance.describe() + " answered with invalid JSON: " + e.getMessage()); //$NON-NLS-1$ //$NON-NLS-2$
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static McpToolResult notAccepting(TestInstance anInstance) {
        return McpToolResult.error("The test instance " + anInstance.describe() + " does not accept connections yet - still starting?"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /**
     * Tells a timeout of the connect from one of the answer: Java reports the
     * first one as 'connect timed out', the second one as 'Read timed out'.
     */
    private static boolean isConnectTimeout(SocketTimeoutException anException) {
        String message = anException.getMessage();
        return message != null && message.toLowerCase().contains("connect"); //$NON-NLS-1$
    }

    private static String read(InputStream anInput) throws IOException {

        if (anInput == null) {
            return ""; //$NON-NLS-1$
        }

        try (InputStream in = anInput) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) {
                bytes.write(buffer, 0, count);
            }
            return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static String cut(String aText) {
        return aText.length() > MAX_ERROR_BODY ? aText.substring(0, MAX_ERROR_BODY) + "..." : aText; //$NON-NLS-1$
    }
}
