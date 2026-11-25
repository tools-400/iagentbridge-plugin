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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import de.tools400.iagentbridge.mcp.McpToolResult;

public class McpToolResultTest {

    private static final byte[] PNG = { 1, 2, 3 };
    private static final String PNG_BASE64 = "AQID";
    private static final String MIME_PNG = "image/png";

    @Test
    public void testToJson_text() {

        JsonObject json = McpToolResult.success("hello").toJson();

        JsonArray content = json.getAsJsonArray("content");
        assertEquals(1, content.size());
        assertText("hello", content.get(0).getAsJsonObject());
        assertFalse(json.get("isError").getAsBoolean());
    }

    @Test
    public void testWithImage() {

        JsonObject json = McpToolResult.success("shot").withImage(PNG, MIME_PNG).toJson();

        JsonArray content = json.getAsJsonArray("content");
        assertEquals(2, content.size());
        assertText("shot", content.get(0).getAsJsonObject());
        assertImage(PNG_BASE64, MIME_PNG, content.get(1).getAsJsonObject());
        assertFalse(json.get("isError").getAsBoolean());
    }

    @Test
    public void testWithImage_twice() {

        JsonObject json = McpToolResult.success("two").withImage(PNG, MIME_PNG).withImage(new byte[] { -1 }, "image/jpeg").toJson();

        JsonArray content = json.getAsJsonArray("content");
        assertEquals(3, content.size());
        assertImage(PNG_BASE64, MIME_PNG, content.get(1).getAsJsonObject());
        assertImage("/w==", "image/jpeg", content.get(2).getAsJsonObject());
    }

    @Test
    public void testWithImage_keepsOriginal() {

        McpToolResult original = McpToolResult.success("text");
        McpToolResult withImage = original.withImage(PNG, MIME_PNG);

        assertEquals(1, original.toJson().getAsJsonArray("content").size());
        assertEquals(2, withImage.toJson().getAsJsonArray("content").size());
        assertEquals("text", withImage.getText());
    }

    @Test
    public void testWithImage_error() {

        JsonObject json = McpToolResult.error("failed").withImage(PNG, MIME_PNG).toJson();

        assertTrue(json.get("isError").getAsBoolean());
        assertEquals(2, json.getAsJsonArray("content").size());
    }

    @Test
    public void testWithImage_copiesData() {

        byte[] data = { 1, 2, 3 };
        McpToolResult result = McpToolResult.success("copy").withImage(data, MIME_PNG);
        data[0] = 9;

        assertImage(PNG_BASE64, MIME_PNG, result.toJson().getAsJsonArray("content").get(1).getAsJsonObject());
    }

    @Test
    public void testWithImage_invalid() {

        McpToolResult result = McpToolResult.success("invalid");
        try {
            result.withImage(null, MIME_PNG);
            fail("IllegalArgumentException expected for missing data");
        } catch (IllegalArgumentException e) {
            // expected
        }
        try {
            result.withImage(PNG, null);
            fail("IllegalArgumentException expected for a missing MIME type");
        } catch (IllegalArgumentException e) {
            // expected
        }
        try {
            result.withImage(PNG, " ");
            fail("IllegalArgumentException expected for an empty MIME type");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    private static void assertText(String anExpected, JsonObject aContent) {
        assertEquals("text", aContent.get("type").getAsString());
        assertEquals(anExpected, aContent.get("text").getAsString());
    }

    private static void assertImage(String anExpectedData, String anExpectedMimeType, JsonObject aContent) {
        assertEquals("image", aContent.get("type").getAsString());
        assertEquals(anExpectedData, aContent.get("data").getAsString());
        assertEquals(anExpectedMimeType, aContent.get("mimeType").getAsString());
    }
}
