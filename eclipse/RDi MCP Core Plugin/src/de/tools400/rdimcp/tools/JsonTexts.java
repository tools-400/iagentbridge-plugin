/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.rdimcp.tools;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

/**
 * Creates the JSON texts of the tools.
 */
final class JsonTexts {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private JsonTexts() {
    }

    /**
     * Formats a JSON element as readable text.
     *
     * @param anElement - JSON element
     * @return formatted text
     */
    static String toText(JsonElement anElement) {
        return GSON.toJson(anElement);
    }

    /**
     * Parses a JSON text, e.g. the input schema of a tool.
     *
     * @param aJson - JSON text
     * @return JSON element
     */
    static JsonElement parse(String aJson) {
        return new JsonParser().parse(aJson);
    }
}
