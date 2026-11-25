/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * What 'ui_wait' waits for: a shell, a text in the active shell, the end of a
 * shell or a widget, or idle jobs.
 */
public final class WaitCondition {

    /** Default time to wait */
    public static final int DEFAULT_TIMEOUT_SECONDS = 10;

    /** Longest time to wait: the MCP server answers no other call meanwhile */
    public static final int MAX_TIMEOUT_SECONDS = 60;

    /**
     * Kind of the condition.
     */
    public enum Kind {
        /** A visible shell with a title */
        SHELL,
        /** A text in the active shell */
        TEXT,
        /** A shell or a widget is gone */
        GONE,
        /** No job runs */
        JOBS_IDLE
    }

    private static final String[] NAMES = { "shell", "text", "gone", "jobsIdle" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

    private final Kind kind;
    private final String value;
    private final int timeoutSeconds;

    private WaitCondition(Kind aKind, String aValue, int aTimeoutSeconds) {
        kind = aKind;
        value = aValue;
        timeoutSeconds = aTimeoutSeconds;
    }

    /**
     * Reads the condition from the arguments of 'ui_wait'.
     *
     * @param anArguments - the arguments
     * @return the condition
     * @throws IllegalArgumentException if not exactly one condition is given,
     *         or the timeout is wrong
     */
    public static WaitCondition parse(JsonObject anArguments) {

        int timeout = timeoutSeconds(anArguments);

        Kind kind = null;
        String value = null;
        for (int i = 0; i < NAMES.length; i++) {
            JsonElement element = anArguments.get(NAMES[i]);
            if (element == null || element.isJsonNull()) {
                continue;
            }
            if (kind != null) {
                throw new IllegalArgumentException("Give exactly one of 'shell', 'text', 'gone' and 'jobsIdle'."); //$NON-NLS-1$
            }
            kind = Kind.values()[i];
            if (kind == Kind.JOBS_IDLE) {
                if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean() || !element.getAsBoolean()) {
                    throw new IllegalArgumentException("Argument 'jobsIdle' must be true."); //$NON-NLS-1$
                }
            } else {
                if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString() || element.getAsString().trim().isEmpty()) {
                    throw new IllegalArgumentException("Argument '" + NAMES[i] + "' must be a string, that is not empty."); //$NON-NLS-1$ //$NON-NLS-2$
                }
                value = element.getAsString().trim();
            }
        }

        if (kind == null) {
            throw new IllegalArgumentException("Give one of 'shell', 'text', 'gone' and 'jobsIdle'."); //$NON-NLS-1$
        }

        return new WaitCondition(kind, value, timeout);
    }

    /**
     * Reads the argument 'timeoutSeconds'. The RDi, that forwards the call,
     * checks it, too, because its wait for the answer follows from it.
     *
     * @param anArguments - the arguments
     * @return seconds, 1 to 60
     * @throws IllegalArgumentException if the value is no integer from 1 to 60
     */
    public static int timeoutSeconds(JsonObject anArguments) {

        JsonElement element = anArguments.get("timeoutSeconds"); //$NON-NLS-1$
        if (element == null || element.isJsonNull()) {
            return DEFAULT_TIMEOUT_SECONDS;
        }

        double seconds = element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber() ? element.getAsDouble() : Double.NaN;
        if (seconds != Math.rint(seconds) || seconds < 1 || seconds > MAX_TIMEOUT_SECONDS) {
            throw new IllegalArgumentException("Argument 'timeoutSeconds' must be an integer from 1 to " + MAX_TIMEOUT_SECONDS + "."); //$NON-NLS-1$ //$NON-NLS-2$
        }

        return (int)seconds;
    }

    public Kind getKind() {
        return kind;
    }

    /**
     * Returns the title, the text or the reference or title of the condition.
     *
     * @return value, or <code>null</code> for {@link Kind#JOBS_IDLE}
     */
    public String getValue() {
        return value;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    /**
     * Returns whether a node of a snapshot or one of its descendants shows a
     * text: in its text, its values like label and value, or its tooltip.
     * Password fields have no value in the model, so their content is never
     * found.
     *
     * @param aNode - the node
     * @param aText - the text
     * @return <code>true</code>, if the text is contained
     */
    public static boolean contains(WidgetNode aNode, String aText) {

        if (contains(aNode.getText(), aText) || contains(aNode.getTooltip(), aText)) {
            return true;
        }
        for (WidgetNode.Value value : aNode.getValues().values()) {
            if (contains(value.getText(), aText)) {
                return true;
            }
        }
        for (WidgetNode child : aNode.getChildren()) {
            if (contains(child, aText)) {
                return true;
            }
        }

        return false;
    }

    private static boolean contains(String aValue, String aText) {
        return aValue != null && aValue.contains(aText);
    }

    /**
     * Describes the condition for the result.
     *
     * @return description
     */
    public String describe() {
        switch (kind) {
        case SHELL:
            return "the shell \"" + value + "\""; //$NON-NLS-1$ //$NON-NLS-2$
        case TEXT:
            return "the text \"" + value + "\" in the active shell"; //$NON-NLS-1$ //$NON-NLS-2$
        case GONE:
            return "the end of " + (value.matches("w[0-9]+") ? value : "the shell \"" + value + "\""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        default:
            return "the end of all jobs"; //$NON-NLS-1$
        }
    }
}
