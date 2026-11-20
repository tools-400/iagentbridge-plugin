/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.mcp;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Reads the arguments of a tool.
 */
public final class ToolArguments {

    /** Path of Git Bash (MSYS) like '/c/dir/file' */
    private static final Pattern MSYS_PATH = Pattern.compile("/([a-zA-Z])(/.*)?"); //$NON-NLS-1$

    private ToolArguments() {
    }

    /**
     * Returns an argument, that is an array of strings. A single string is
     * accepted as an array with one element.
     *
     * @param anArguments - arguments of the tool
     * @param aName - name of the argument
     * @return values, never empty
     * @throws IllegalArgumentException if the argument is missing, empty or not
     *         an array of strings
     */
    public static List<String> getStringList(JsonObject anArguments, String aName) {

        JsonElement element = anArguments.get(aName);
        if (element == null || element.isJsonNull()) {
            throw new IllegalArgumentException("Argument '" + aName + "' is missing."); //$NON-NLS-1$ //$NON-NLS-2$
        }

        List<String> values = new ArrayList<String>();
        if (isString(element)) {
            values.add(element.getAsString());
        } else if (element.isJsonArray()) {
            for (JsonElement item : element.getAsJsonArray()) {
                if (!isString(item)) {
                    throw new IllegalArgumentException("Argument '" + aName + "' must contain strings only."); //$NON-NLS-1$ //$NON-NLS-2$
                }
                values.add(item.getAsString());
            }
        } else {
            throw new IllegalArgumentException("Argument '" + aName + "' must be an array of strings."); //$NON-NLS-1$ //$NON-NLS-2$
        }

        if (values.isEmpty()) {
            throw new IllegalArgumentException("Argument '" + aName + "' must not be empty."); //$NON-NLS-1$ //$NON-NLS-2$
        }

        return values;
    }

    /**
     * Converts a path of Git Bash like '/c/dir/file' into a Windows path like
     * 'C:/dir/file'. Other paths are returned trimmed, but unchanged.
     *
     * @param aPath - path passed by the client
     * @return path of the local file system
     */
    public static String toLocalPath(String aPath) {

        String path = aPath.trim();
        if (File.separatorChar != '\\') {
            return path;
        }

        Matcher matcher = MSYS_PATH.matcher(path);
        if (!matcher.matches()) {
            return path;
        }

        String rest = matcher.group(2);
        return matcher.group(1).toUpperCase(Locale.ENGLISH) + ":" + (rest == null ? "/" : rest); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static boolean isString(JsonElement anElement) {
        return anElement.isJsonPrimitive() && anElement.getAsJsonPrimitive().isString();
    }
}
