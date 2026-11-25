/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.mcp;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Result of a tool: a text for the AI agent, optional images, e.g. screenshots,
 * and the information, whether the tool failed.
 */
public final class McpToolResult {

    private final String text;
    private final boolean error;
    private final List<Image> images;

    private McpToolResult(String aText, boolean anError, List<Image> anImages) {
        text = aText;
        error = anError;
        images = anImages;
    }

    /**
     * Creates the result of a tool, that succeeded.
     *
     * @param aText - text returned to the client
     * @return result
     */
    public static McpToolResult success(String aText) {
        return new McpToolResult(aText, false, Collections.<Image> emptyList());
    }

    /**
     * Creates the result of a tool, that failed.
     *
     * @param aText - text returned to the client
     * @return result
     */
    public static McpToolResult error(String aText) {
        return new McpToolResult(aText, true, Collections.<Image> emptyList());
    }

    /**
     * Returns a new result with the text, the state and the images of this
     * result and one more image, e.g. a screenshot. The client shows the images
     * to the AI agent after the text.
     *
     * @param aData - bytes of the image, e.g. of a PNG file; the result keeps a
     *        copy
     * @param aMimeType - MIME type of the image, e.g. 'image/png'
     * @return new result
     * @throws IllegalArgumentException if the data or the MIME type is missing
     * @since 1.1.0
     */
    public McpToolResult withImage(byte[] aData, String aMimeType) {

        if (aData == null) {
            throw new IllegalArgumentException("The data of the image is missing."); //$NON-NLS-1$
        }
        if (aMimeType == null || aMimeType.trim().isEmpty()) {
            throw new IllegalArgumentException("The MIME type of the image is missing."); //$NON-NLS-1$
        }

        List<Image> newImages = new ArrayList<Image>(images);
        newImages.add(new Image(aData.clone(), aMimeType.trim()));

        return new McpToolResult(text, error, Collections.unmodifiableList(newImages));
    }

    public String getText() {
        return text;
    }

    public boolean isError() {
        return error;
    }

    /**
     * Returns the result in the form of the 'tools/call' response.
     *
     * @return result object with 'content' and 'isError'
     */
    public JsonObject toJson() {

        JsonObject textContent = new JsonObject();
        textContent.addProperty("type", "text"); //$NON-NLS-1$ //$NON-NLS-2$
        textContent.addProperty("text", text == null ? "" : text); //$NON-NLS-1$ //$NON-NLS-2$

        JsonArray content = new JsonArray();
        content.add(textContent);

        for (Image image : images) {
            JsonObject imageContent = new JsonObject();
            imageContent.addProperty("type", "image"); //$NON-NLS-1$ //$NON-NLS-2$
            imageContent.addProperty("data", Base64.getEncoder().encodeToString(image.data)); //$NON-NLS-1$
            imageContent.addProperty("mimeType", image.mimeType); //$NON-NLS-1$
            content.add(imageContent);
        }

        JsonObject result = new JsonObject();
        result.add("content", content); //$NON-NLS-1$
        result.addProperty("isError", Boolean.valueOf(error)); //$NON-NLS-1$

        return result;
    }

    /**
     * An image of the result.
     */
    private static final class Image {

        private final byte[] data;
        private final String mimeType;

        private Image(byte[] aData, String aMimeType) {
            data = aData;
            mimeType = aMimeType;
        }
    }
}
