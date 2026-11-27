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
import de.tools400.iagentbridge.ui.model.Hints;

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

    /**
     * The server of iAgentBridge answers a call, that is too large, with 413.
     */
    private static final String TOO_LARGE = "The arguments of the call are too large: send them in smaller parts, e.g. a shorter text with " //$NON-NLS-1$
        + "ui_type or ui_lpex."; //$NON-NLS-1$

    /**
     * The server of iAgentBridge answers with 500, if it fails outside of a
     * tool; it writes no entry to the error log then.
     */
    private static final String SERVER_FAILED = "Its server failed. " + Hints.CHECK_BEFORE_REPEATING; //$NON-NLS-1$

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
        return send(anInstance, aToken, aToolName, anArguments).getResult();
    }

    /**
     * Calls a tool of a test instance and tells, how the call ended.
     *
     * @param anInstance - the test instance, with a port
     * @param aToken - token of the test instance
     * @param aToolName - name of the tool
     * @param anArguments - arguments of the tool
     * @return the kind of the outcome and the result
     */
    public Response send(TestInstance anInstance, String aToken, String aToolName, JsonObject anArguments) {

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

            // A fixed length streams the body: then HttpURLConnection never
            // sends the POST again, when the server closes the connection
            // without an answer. A second call would be a second click.
            byte[] requestBytes = request.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(requestBytes.length);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(requestBytes);
            }

            int status = connection.getResponseCode();
            if (status == HttpURLConnection.HTTP_UNAUTHORIZED) {
                return new Response(Kind.REJECTED, McpToolResult.error("The test instance " + anInstance.describe() //$NON-NLS-1$
                    + " rejected the token (HTTP 401). Does its launch configuration set another token with -Diagentbridge.token?")); //$NON-NLS-1$
            }
            if (status != HttpURLConnection.HTTP_OK) {
                return rejected(withBody("The test instance " + anInstance.describe() + " answered with HTTP " + status, //$NON-NLS-1$ //$NON-NLS-2$
                    read(connection.getErrorStream())), statusHint(status));
            }

            String body = read(connection.getInputStream());
            JsonElement response = new JsonParser().parse(body);
            if (!response.isJsonObject()) {
                return rejected(withBody("The test instance " + anInstance.describe() + " answered with no JSON object", body), Hints.CHECK_PORT); //$NON-NLS-1$ //$NON-NLS-2$
            }

            return new Response(Kind.ANSWERED, ForwardedResult.toToolResult(response.getAsJsonObject(), anInstance));

        } catch (ConnectException e) {
            return new Response(Kind.REFUSED, notAccepting(anInstance));
        } catch (SocketTimeoutException e) {
            if (isConnectTimeout(e)) {
                return new Response(Kind.REFUSED, notAccepting(anInstance));
            }
            return new Response(Kind.TIMEOUT,
                McpToolResult.error("The test instance " + anInstance.describe() + " did not answer within " //$NON-NLS-1$ //$NON-NLS-2$
                    + readTimeoutMillis / 1000 + " s - stopped in the debugger? list_launches shows, whether it is. If " + port //$NON-NLS-1$
                    + " is the port of this RDi, correct -Diagentbridge.port of the launch configuration. " + Hints.CHECK_BEFORE_REPEATING)); //$NON-NLS-1$
        } catch (IOException e) {
            return new Response(Kind.BROKEN,
                McpToolResult.error(Hints.after("The call of the test instance " + anInstance.describe() + " failed: " + e, //$NON-NLS-1$ //$NON-NLS-2$
                    Hints.CHECK_BEFORE_REPEATING)));
        } catch (JsonParseException e) {
            return rejected("The test instance " + anInstance.describe() + " answered with invalid JSON: " + e.getMessage(), Hints.CHECK_PORT); //$NON-NLS-1$ //$NON-NLS-2$
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Returns the outcome of a call, that the server rejected: the reason
     * without the hint for the agent, so that stop_launch can give its own.
     */
    private static Response rejected(String aReason, String aHint) {
        return new Response(Kind.REJECTED, McpToolResult.error(Hints.after(aReason, aHint)), aReason);
    }

    /**
     * Returns the hint for an HTTP status: the server of iAgentBridge answers a
     * call with 413 and 500 only, so other statuses come from another program.
     */
    private static String statusHint(int aStatus) {
        switch (aStatus) {
        case HttpURLConnection.HTTP_ENTITY_TOO_LARGE:
            return TOO_LARGE;
        case HttpURLConnection.HTTP_INTERNAL_ERROR:
            return SERVER_FAILED;
        default:
            return Hints.CHECK_PORT;
        }
    }

    private static String withBody(String aText, String aBody) {
        return aBody.isEmpty() ? aText : aText + ": " + cut(aBody); //$NON-NLS-1$
    }

    private static McpToolResult notAccepting(TestInstance anInstance) {
        return McpToolResult.error("The test instance " + anInstance.describe() + " does not accept connections yet - still starting? " //$NON-NLS-1$ //$NON-NLS-2$
            + Hints.NOT_SENT);
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

    /**
     * How a call of a test instance ended.
     */
    public enum Kind {

        /** The test instance answered; its tool may have failed. */
        ANSWERED,

        /** No connection: nothing listens at the port, or not yet. */
        REFUSED,

        /** The connection broke after the call was sent, e.g. at a shutdown. */
        BROKEN,

        /** No answer within the time of the client. */
        TIMEOUT,

        /**
         * The server rejected the call, e.g. the token, or answered no JSON.
         */
        REJECTED
    }

    /**
     * The outcome of a call: its kind and the result.
     */
    public static final class Response {

        private final Kind kind;
        private final McpToolResult result;
        private final String reason;

        /**
         * Creates the outcome, whose reason is the text of the result.
         *
         * @param aKind - how the call ended
         * @param aResult - the result
         */
        public Response(Kind aKind, McpToolResult aResult) {
            this(aKind, aResult, aResult.getText());
        }

        /**
         * Creates the outcome.
         *
         * @param aKind - how the call ended
         * @param aResult - the result
         * @param aReason - the reason without the hint for the agent
         */
        public Response(Kind aKind, McpToolResult aResult, String aReason) {
            kind = aKind;
            result = aResult;
            reason = aReason;
        }

        public Kind getKind() {
            return kind;
        }

        public McpToolResult getResult() {
            return result;
        }

        /**
         * Returns, why the call failed, without the hint for the agent.
         *
         * @return the reason
         */
        public String getReason() {
            return reason;
        }
    }
}
