/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An entry of the error log of Eclipse, with its children (!SUBENTRY).
 */
public final class ErrorLogEntry {

    private final String plugin;
    private final int severity;
    private final int code;
    private final long time;
    private final List<ErrorLogEntry> children;
    // Builders: a stack trace may have thousands of lines.
    private StringBuilder message;
    private StringBuilder stack;
    private int stackLines;

    ErrorLogEntry(String aPlugin, int aSeverity, int aCode, long aTime) {
        plugin = aPlugin;
        severity = aSeverity;
        code = aCode;
        time = aTime;
        children = new ArrayList<ErrorLogEntry>();
    }

    public String getPlugin() {
        return plugin;
    }

    /**
     * Returns the severity, as IStatus names it: 0 OK, 1 info, 2 warning, 4
     * error, 8 cancel.
     *
     * @return severity
     */
    public int getSeverity() {
        return severity;
    }

    public int getCode() {
        return code;
    }

    /**
     * Returns the time of the entry.
     *
     * @return milliseconds since 1970, or 0, if the time cannot be read
     */
    public long getTime() {
        return time;
    }

    /**
     * Returns the message; the lines are separated by '\n', empty lines at its
     * end are left out.
     *
     * @return message, maybe empty
     */
    public String getMessage() {
        return message == null ? "" : withoutEmptyLinesAtTheEnd(message); //$NON-NLS-1$
    }

    /**
     * Returns the stack trace; the lines are separated by '\n', empty lines at
     * its end are left out.
     *
     * @return stack trace or <code>null</code>
     */
    public String getStack() {
        return stack == null ? null : withoutEmptyLinesAtTheEnd(stack);
    }

    public List<ErrorLogEntry> getChildren() {
        return Collections.unmodifiableList(children);
    }

    void appendMessage(String aLine) {
        if (message == null) {
            message = new StringBuilder(aLine);
        } else {
            message.append('\n').append(aLine);
        }
    }

    void appendStack(String aLine) {
        startStack();
        if (stackLines++ > 0) {
            stack.append('\n');
        }
        stack.append(aLine);
    }

    void startStack() {
        if (stack == null) {
            stack = new StringBuilder();
        }
    }

    void addChild(ErrorLogEntry aChild) {
        children.add(aChild);
    }

    /**
     * Equinox writes an empty line after each entry, and the parser appends it
     * like the empty lines inside a message or a stack trace.
     */
    private static String withoutEmptyLinesAtTheEnd(StringBuilder aText) {
        int end = aText.length();
        while (end > 0 && aText.charAt(end - 1) == '\n') {
            end--;
        }
        return aText.substring(0, end);
    }
}
