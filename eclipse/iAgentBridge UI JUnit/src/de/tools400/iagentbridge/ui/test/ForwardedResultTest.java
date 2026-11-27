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

import org.junit.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import de.tools400.iagentbridge.mcp.McpToolResult;
import de.tools400.iagentbridge.ui.forwarding.ForwardedResult;
import de.tools400.iagentbridge.ui.forwarding.TestInstance;

public class ForwardedResultTest {

    private static final TestInstance INSTANCE = TestInstance.fromCommandLine("iAgentBridge (de)",
        "java -Diagentbridge.uiTools=true -Diagentbridge.port=39196");

    @Test
    public void testToToolResult_textAndImage() {

        McpToolResult result = ForwardedResult.toToolResult(parse("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"content\":["
            + "{\"type\":\"text\",\"text\":\"shell \\\"S\\\" [w1]\"},{\"type\":\"image\",\"data\":\"AQID\",\"mimeType\":\"image/png\"}],"
            + "\"isError\":false}}"), INSTANCE);

        assertFalse(result.isError());
        assertEquals("Test instance \"iAgentBridge (de)\" (port 39196):\nshell \"S\" [w1]", result.getText());
        JsonArray content = result.toJson().getAsJsonArray("content");
        assertEquals(2, content.size());
        JsonObject image = content.get(1).getAsJsonObject();
        assertEquals("image", image.get("type").getAsString());
        assertEquals("AQID", image.get("data").getAsString());
        assertEquals("image/png", image.get("mimeType").getAsString());
    }

    @Test
    public void testToToolResult_toolError() {

        McpToolResult result = ForwardedResult.toToolResult(
            parse(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"UI thread busy for 10 s\"}],\"isError\":true}}"),
            INSTANCE);

        assertTrue(result.isError());
        assertEquals("Test instance \"iAgentBridge (de)\" (port 39196):\nUI thread busy for 10 s", result.getText());
    }

    @Test
    public void testToToolResult_unknownTool() {

        McpToolResult result = ForwardedResult
            .toToolResult(parse("{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32602,\"message\":\"Unknown tool: ui_snapshot\"}}"), INSTANCE);

        assertTrue(result.isError());
        assertTrue(result.getText(), result.getText().startsWith("Test instance \"iAgentBridge (de)\" (port 39196):\nUnknown tool: ui_snapshot\n"));
        assertTrue(result.getText(), result.getText().contains("Port 39196 belongs to an RDi without the UI tools of iAgentBridge."));
    }

    @Test
    public void testToToolResult_otherError() {

        McpToolResult result = ForwardedResult
            .toToolResult(parse("{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32603,\"message\":\"Internal error\"}}"), INSTANCE);

        assertTrue(result.isError());
        assertEquals("Test instance \"iAgentBridge (de)\" (port 39196):\nInternal error", result.getText());
    }

    @Test
    public void testToToolResult_invalid() {
        assertTrue(ForwardedResult.toToolResult(parse("{\"jsonrpc\":\"2.0\",\"id\":1}"), INSTANCE).isError());
        assertTrue(ForwardedResult.toToolResult(parse("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}"), INSTANCE).isError());

        String hint = ". Check -Diagentbridge.port of its launch configuration: the port may belong to another program.";
        assertEquals("Test instance \"iAgentBridge (de)\" (port 39196):\nInvalid response: {\"jsonrpc\":\"2.0\",\"id\":1}" + hint,
            ForwardedResult.toToolResult(parse("{\"jsonrpc\":\"2.0\",\"id\":1}"), INSTANCE).getText());
        assertEquals("Test instance \"iAgentBridge (de)\" (port 39196):\nInvalid response without content: "
            + "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}" + hint,
            ForwardedResult.toToolResult(parse("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}"), INSTANCE).getText());
    }

    private static JsonObject parse(String aJson) {
        return new JsonParser().parse(aJson).getAsJsonObject();
    }
}
