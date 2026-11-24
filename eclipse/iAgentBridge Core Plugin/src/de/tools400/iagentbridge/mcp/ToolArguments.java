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
     * Returns an argument, that is a string.
     *
     * @param anArguments - arguments of the tool
     * @param aName - name of the argument
     * @return value, never empty or blank
     * @throws IllegalArgumentException if the argument is missing, blank or not
     *         a string
     * @since 1.1.0
     */
    public static String getString(JsonObject anArguments, String aName) {

        JsonElement element = anArguments.get(aName);
        if (isMissing(element)) {
            throw new IllegalArgumentException("Argument '" + aName + "' is missing."); //$NON-NLS-1$ //$NON-NLS-2$
        }

        if (!isString(element)) {
            throw new IllegalArgumentException("Argument '" + aName + "' must be a string."); //$NON-NLS-1$ //$NON-NLS-2$
        }

        String value = element.getAsString();
        if (value.trim().isEmpty()) {
            throw new IllegalArgumentException("Argument '" + aName + "' must not be empty."); //$NON-NLS-1$ //$NON-NLS-2$
        }

        return value;
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
        if (isMissing(element)) {
            throw new IllegalArgumentException("Argument '" + aName + "' is missing."); //$NON-NLS-1$ //$NON-NLS-2$
        }

        List<String> values = toStringList(element, aName);
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Argument '" + aName + "' must not be empty."); //$NON-NLS-1$ //$NON-NLS-2$
        }

        return values;
    }

    /**
     * Returns an optional argument, that is an array of strings. A single
     * string is accepted as an array with one element.
     *
     * @param anArguments - arguments of the tool
     * @param aName - name of the argument
     * @return values or an empty list, if the argument is missing
     * @throws IllegalArgumentException if the argument is not an array of
     *         strings
     * @since 1.1.0
     */
    public static List<String> getOptionalStringList(JsonObject anArguments, String aName) {

        JsonElement element = anArguments.get(aName);
        if (isMissing(element)) {
            return new ArrayList<String>();
        }

        return toStringList(element, aName);
    }

    /**
     * Returns an optional argument, that is one of the given strings.
     *
     * @param anArguments - arguments of the tool
     * @param aName - name of the argument
     * @param aDefault - value, if the argument is missing; one of the allowed
     *        values
     * @param anAllowedValues - values, that the argument may have
     * @return value of the argument or the default value
     * @throws IllegalArgumentException if the argument is not one of the
     *         allowed values
     * @throws IllegalStateException if the default value is not one of the
     *         allowed values
     * @since 1.1.0
     */
    public static String getChoice(JsonObject anArguments, String aName, String aDefault, String... anAllowedValues) {

        // An error of the tool, not of the client. An IllegalArgumentException
        // would reach the client as an error in its arguments.
        if (!contains(anAllowedValues, aDefault)) {
            throw new IllegalStateException("The default value '" + aDefault + "' of argument '" + aName //$NON-NLS-1$ //$NON-NLS-2$
                + "' is not one of the allowed values."); //$NON-NLS-1$
        }

        JsonElement element = anArguments.get(aName);
        if (isMissing(element)) {
            return aDefault;
        }

        if (isString(element) && contains(anAllowedValues, element.getAsString())) {
            return element.getAsString();
        }

        StringBuilder values = new StringBuilder();
        for (String allowedValue : anAllowedValues) {
            if (values.length() > 0) {
                values.append(", "); //$NON-NLS-1$
            }
            values.append(allowedValue);
        }

        throw new IllegalArgumentException("Argument '" + aName + "' must be one of: " + values + "."); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    /**
     * Returns an optional argument, that is an integer.
     *
     * @param anArguments - arguments of the tool
     * @param aName - name of the argument
     * @param aDefault - value, if the argument is missing
     * @param aMinimum - smallest value allowed
     * @return value of the argument or the default value
     * @throws IllegalArgumentException if the argument is no integer or smaller
     *         than the minimum
     * @since 1.1.0
     */
    public static int getInt(JsonObject anArguments, String aName, int aDefault, int aMinimum) {

        JsonElement element = anArguments.get(aName);
        if (isMissing(element)) {
            return aDefault;
        }

        // A double, not a BigDecimal: a huge exponent like 1e999999999 becomes
        // infinite instead of a number with a billion digits.
        double value = Double.NaN;
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            value = element.getAsDouble();
        }

        if (value != Math.rint(value) || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Argument '" + aName + "' must be an integer."); //$NON-NLS-1$ //$NON-NLS-2$
        }

        if (value < aMinimum) {
            throw new IllegalArgumentException("Argument '" + aName + "' must be at least " + aMinimum + "."); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        }

        return (int)value;
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

    private static boolean contains(String[] aValues, String aValue) {

        for (String value : aValues) {
            if (value.equals(aValue)) {
                return true;
            }
        }

        return false;
    }

    private static boolean isMissing(JsonElement anElement) {
        return anElement == null || anElement.isJsonNull();
    }

    private static List<String> toStringList(JsonElement anElement, String aName) {

        List<String> values = new ArrayList<String>();
        if (isString(anElement)) {
            values.add(anElement.getAsString());
        } else if (anElement.isJsonArray()) {
            for (JsonElement item : anElement.getAsJsonArray()) {
                if (!isString(item)) {
                    throw new IllegalArgumentException("Argument '" + aName + "' must contain strings only."); //$NON-NLS-1$ //$NON-NLS-2$
                }
                values.add(item.getAsString());
            }
        } else {
            throw new IllegalArgumentException("Argument '" + aName + "' must be an array of strings."); //$NON-NLS-1$ //$NON-NLS-2$
        }

        return values;
    }

    private static boolean isString(JsonElement anElement) {
        return anElement.isJsonPrimitive() && anElement.getAsJsonPrimitive().isString();
    }
}
