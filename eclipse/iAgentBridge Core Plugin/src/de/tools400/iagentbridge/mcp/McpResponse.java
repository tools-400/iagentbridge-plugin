/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.mcp;

/**
 * Response of the dispatcher to a message of the client: the JSON text to send,
 * if any, and whether the message was rejected.
 */
public final class McpResponse {

    private final String json;
    private final boolean rejected;

    private McpResponse(String aJson, boolean aRejected) {
        json = aJson;
        rejected = aRejected;
    }

    /**
     * Creates the response to a message, that needs no answer: a notification
     * or a response of the client.
     *
     * @return response without JSON text
     */
    static McpResponse noAnswer() {
        return new McpResponse(null, false);
    }

    /**
     * Creates the response to one or more requests.
     *
     * @param aJson - JSON-RPC response or array of responses
     * @return response
     */
    static McpResponse answer(String aJson) {
        return new McpResponse(aJson, false);
    }

    /**
     * Creates the response to a message, that is rejected, because it is no
     * valid request, notification or response.
     *
     * @param aJson - JSON-RPC error response without 'id' or array of them
     * @return response
     */
    static McpResponse rejected(String aJson) {
        return new McpResponse(aJson, true);
    }

    /**
     * Returns the JSON text to send to the client.
     *
     * @return JSON text or <code>null</code>, if nothing is sent
     */
    public String getJson() {
        return json;
    }

    /**
     * Returns whether the message was rejected.
     *
     * @return <code>true</code>, if the message is no valid request,
     *         notification or response
     */
    public boolean isRejected() {
        return rejected;
    }
}
