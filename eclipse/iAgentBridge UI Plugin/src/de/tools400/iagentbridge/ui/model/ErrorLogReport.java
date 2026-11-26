/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.model;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * The text of ui_error_log: the newest entries of the log since a time, with a
 * minimum severity, with their messages, stack traces and children, cut to a
 * readable size.
 */
public final class ErrorLogReport {

    /** Entries, that the tool returns at most */
    public static final int MAX_ENTRIES = 100;

    /** Entries, that the tool returns by default */
    public static final int DEFAULT_ENTRIES = 20;

    private static final int MAX_MESSAGE = 2000;
    private static final int MAX_STACK_LINES = 30;
    private static final int MAX_CHILDREN = 20;
    private static final String INDENT = "  "; //$NON-NLS-1$

    private final long since;
    private final boolean sinceStart;
    private final Severity severity;
    private final int maxEntries;

    /**
     * Creates the report.
     *
     * @param aSince - first time, in milliseconds since 1970
     * @param aSinceStart - <code>true</code>, if the time is the start of the
     *        test instance
     * @param aSeverity - the minimum severity
     * @param aMaxEntries - newest entries, that are shown at most
     */
    public ErrorLogReport(long aSince, boolean aSinceStart, Severity aSeverity, int aMaxEntries) {
        since = aSince;
        sinceStart = aSinceStart;
        severity = aSeverity;
        maxEntries = aMaxEntries;
    }

    /**
     * Returns the time of the argument 'since' other than 'session'.
     *
     * @param aSince - e.g. '2026-11-26T08:00:00', in the time zone of the
     *        computer
     * @return milliseconds since 1970
     * @throws IllegalArgumentException if the text is no such time
     */
    public static long parseSince(String aSince) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss"); //$NON-NLS-1$
        format.setLenient(false);
        // The whole text: a zone like 'Z' or '+05:00' would be ignored.
        ParsePosition position = new ParsePosition(0);
        Date time = format.parse(aSince, position);
        if (time == null || position.getIndex() != aSince.length()) {
            throw new IllegalArgumentException("Argument 'since' must be 'session' or a time like 2026-11-26T08:00:00, not '" + aSince + "'."); //$NON-NLS-1$ //$NON-NLS-2$
        }
        return time.getTime();
    }

    /**
     * Writes the report.
     *
     * @param anEntries - all entries of the log
     * @param anEarlierInBackup - <code>true</code>, if a backup of the log
     *        holds earlier entries of this session
     * @return text
     */
    public String write(List<ErrorLogEntry> anEntries, boolean anEarlierInBackup) {

        List<ErrorLogEntry> matching = new ArrayList<ErrorLogEntry>();
        for (ErrorLogEntry entry : anEntries) {
            if (entry.getTime() >= since && severity.accepts(entry.getSeverity())) {
                matching.add(entry);
            }
        }

        String period = sinceStart ? "since the start of the test instance (" + ErrorLogParser.formatTime(since) + ")" //$NON-NLS-1$ //$NON-NLS-2$
            : "since " + ErrorLogParser.formatTime(since); //$NON-NLS-1$
        // The first line says, what follows, also whether a backup holds
        // earlier entries: a long result may be cut at its end.
        String backup = anEarlierInBackup
            ? "; earlier entries of this session are in the files .bak_*.log beside the log, which this tool does not read" //$NON-NLS-1$
            : ""; //$NON-NLS-1$
        StringBuilder text = new StringBuilder();
        if (matching.isEmpty()) {
            text.append("No entries ").append(period).append(", ").append(severity.describe()).append(backup).append(".\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        } else {
            int shown = Math.min(maxEntries, matching.size());
            text.append(matching.size()).append(matching.size() == 1 ? " entry " : " entries ").append(period).append(", ") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .append(severity.describe()).append(shown < matching.size() ? ", the last " : ", ").append(shown).append(" shown") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .append(backup).append(":\n"); //$NON-NLS-1$
            for (ErrorLogEntry entry : matching.subList(matching.size() - shown, matching.size())) {
                text.append(ErrorLogParser.formatTime(entry.getTime())).append(' ');
                writeEntry(text, entry, INDENT);
            }
        }

        return text.toString();
    }

    private static void writeEntry(StringBuilder aText, ErrorLogEntry anEntry, String anIndent) {

        aText.append(severityName(anEntry.getSeverity())).append(' ').append(anEntry.getPlugin()).append(" (code ").append(anEntry.getCode()) //$NON-NLS-1$
            .append("): "); //$NON-NLS-1$
        String message = anEntry.getMessage();
        if (message.length() > MAX_MESSAGE) {
            message = message.substring(0, MAX_MESSAGE) + "... (" + message.length() + " characters)"; //$NON-NLS-1$ //$NON-NLS-2$
        }
        String[] lines = message.split("\n", -1); //$NON-NLS-1$
        aText.append(lines[0]).append('\n');
        for (int i = 1; i < lines.length; i++) {
            aText.append(anIndent).append(lines[i]).append('\n');
        }

        if (anEntry.getStack() != null && !anEntry.getStack().isEmpty()) {
            String[] stack = anEntry.getStack().split("\n", -1); //$NON-NLS-1$
            for (int i = 0; i < Math.min(MAX_STACK_LINES, stack.length); i++) {
                aText.append(anIndent).append(stack[i]).append('\n');
            }
            if (stack.length > MAX_STACK_LINES) {
                aText.append(anIndent).append("... ").append(stack.length - MAX_STACK_LINES).append(" more lines\n"); //$NON-NLS-1$ //$NON-NLS-2$
            }
        }

        List<ErrorLogEntry> children = anEntry.getChildren();
        for (ErrorLogEntry child : children.subList(0, Math.min(MAX_CHILDREN, children.size()))) {
            aText.append(anIndent);
            writeEntry(aText, child, anIndent + INDENT);
        }
        if (children.size() > MAX_CHILDREN) {
            aText.append(anIndent).append("... ").append(children.size() - MAX_CHILDREN).append(" more child entries\n"); //$NON-NLS-1$ //$NON-NLS-2$
        }
    }

    private static String severityName(int aSeverity) {
        switch (aSeverity) {
        case 0:
            return "OK"; //$NON-NLS-1$
        case 1:
            return "INFO"; //$NON-NLS-1$
        case 2:
            return "WARNING"; //$NON-NLS-1$
        case 4:
            return "ERROR"; //$NON-NLS-1$
        case 8:
            return "CANCEL"; //$NON-NLS-1$
        default:
            return "SEVERITY " + aSeverity; //$NON-NLS-1$
        }
    }

    /**
     * The minimum severity of the argument 'severity'.
     */
    public enum Severity {

        /** Errors only */
        ERROR ("errors"), //$NON-NLS-1$

        /** Errors and warnings */
        WARNING ("errors and warnings"), //$NON-NLS-1$

        /** All entries, also OK and cancel */
        INFO ("all severities"); //$NON-NLS-1$

        private final String description;

        Severity(String aDescription) {
            description = aDescription;
        }

        /**
         * Returns the severity of the argument.
         *
         * @param aValue - 'error', 'warning', 'info' or <code>null</code> for
         *        the default 'warning'
         * @return the severity
         * @throws IllegalArgumentException if the value is unknown
         */
        public static Severity parse(String aValue) {
            if (aValue == null) {
                return WARNING;
            }
            for (Severity value : values()) {
                if (value.name().toLowerCase(Locale.ENGLISH).equals(aValue)) {
                    return value;
                }
            }
            throw new IllegalArgumentException("Argument 'severity' must be error, warning or info, not '" + aValue + "'."); //$NON-NLS-1$ //$NON-NLS-2$
        }

        /**
         * Returns whether an entry of a severity is shown.
         *
         * @param aSeverity - severity of the entry, as IStatus names it
         * @return <code>true</code>, if it is shown
         */
        public boolean accepts(int aSeverity) {
            switch (this) {
            case ERROR:
                return aSeverity == 4;
            case WARNING:
                return aSeverity == 2 || aSeverity == 4;
            default:
                return true;
            }
        }

        String describe() {
            return description;
        }
    }
}
