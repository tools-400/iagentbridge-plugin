/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.model;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the error log of Eclipse (.metadata/.log), as Equinox writes it:
 * !SESSION before the first entry of a start and at the beginning of every file
 * after a rotation, followed by a header up to the first entry; !ENTRY with
 * plug-in, severity, code and time; !MESSAGE with the lines up to the next line
 * with '!'; !STACK with the stack trace; !SUBENTRY for children; an empty line
 * after each entry. Messages and stack traces may contain empty lines too, so
 * only !ENTRY, !SUBENTRY and !SESSION end an entry.
 */
public final class ErrorLogParser {

    private static final String TIME_PATTERN = "yyyy-MM-dd HH:mm:ss.SSS"; //$NON-NLS-1$

    private static final String SESSION = "!SESSION "; //$NON-NLS-1$
    private static final String ENTRY = "!ENTRY "; //$NON-NLS-1$
    private static final String SUBENTRY = "!SUBENTRY "; //$NON-NLS-1$
    private static final String MESSAGE = "!MESSAGE"; //$NON-NLS-1$
    private static final String STACK = "!STACK"; //$NON-NLS-1$

    private enum Part {
        NONE,
        MESSAGE,
        STACK
    }

    private ErrorLogParser() {
    }

    /**
     * Returns the entries of the log.
     *
     * @param aLines - lines of the file
     * @return entries in the order of the file
     */
    public static List<ErrorLogEntry> parse(List<String> aLines) {

        List<ErrorLogEntry> entries = new ArrayList<ErrorLogEntry>();
        List<ErrorLogEntry> levels = new ArrayList<ErrorLogEntry>();
        ErrorLogEntry current = null;
        Part part = Part.NONE;

        for (String line : aLines) {
            if (line.startsWith(SESSION)) {
                current = null;
                part = Part.NONE;
            } else if (line.startsWith(ENTRY)) {
                current = entry(line.substring(ENTRY.length()));
                entries.add(current);
                levels.clear();
                levels.add(current);
                part = Part.NONE;
            } else if (line.startsWith(SUBENTRY) && !levels.isEmpty()) {
                String rest = line.substring(SUBENTRY.length()).trim();
                int blank = rest.indexOf(' ');
                int depth = blank < 0 ? 1 : toInt(rest.substring(0, blank), 1);
                current = entry(blank < 0 ? "" : rest.substring(blank + 1)); //$NON-NLS-1$
                int parent = Math.max(0, Math.min(depth - 1, levels.size() - 1));
                levels.get(parent).addChild(current);
                while (levels.size() > parent + 1) {
                    levels.remove(levels.size() - 1);
                }
                levels.add(current);
                part = Part.NONE;
            } else if (line.startsWith(MESSAGE) && current != null) {
                current.appendMessage(line.substring(MESSAGE.length()).trim());
                part = Part.MESSAGE;
            } else if (line.startsWith(STACK) && current != null) {
                current.startStack();
                part = Part.STACK;
            } else if (current != null && part == Part.MESSAGE) {
                current.appendMessage(line);
            } else if (current != null && part == Part.STACK) {
                current.appendStack(line);
            }
        }

        return entries;
    }

    /**
     * Returns the start times of the sessions of the log, in the order of the
     * file.
     *
     * @param aLines - lines of the file
     * @return times of the !SESSION lines in milliseconds
     */
    public static List<Long> sessions(List<String> aLines) {

        List<Long> sessions = new ArrayList<Long>();
        for (String line : aLines) {
            if (line.startsWith(SESSION)) {
                String[] words = line.substring(SESSION.length()).trim().split(" +"); //$NON-NLS-1$
                if (words.length >= 2) {
                    sessions.add(Long.valueOf(parseTime(words[0] + " " + words[1]))); //$NON-NLS-1$
                }
            }
        }

        return sessions;
    }

    /**
     * Returns the time of the log, e.g. '2026-11-26 11:53:00.181', in the time
     * zone of the computer.
     *
     * @param aTime - the time
     * @return milliseconds since 1970, or 0, if the text is no time
     */
    public static long parseTime(String aTime) {
        try {
            SimpleDateFormat format = new SimpleDateFormat(TIME_PATTERN);
            format.setLenient(false);
            return format.parse(aTime).getTime();
        } catch (ParseException e) {
            return 0;
        }
    }

    /**
     * Returns a time as the log writes it.
     *
     * @param aTime - milliseconds since 1970
     * @return e.g. '2026-11-26 11:53:00.181'
     */
    public static String formatTime(long aTime) {
        return new SimpleDateFormat(TIME_PATTERN).format(Long.valueOf(aTime));
    }

    /**
     * Creates an entry from the words after !ENTRY: plug-in, severity, code,
     * date and time.
     */
    private static ErrorLogEntry entry(String aWords) {

        String[] words = aWords.trim().split(" +"); //$NON-NLS-1$
        String plugin = words.length > 0 ? words[0] : ""; //$NON-NLS-1$
        int severity = words.length > 1 ? toInt(words[1], 0) : 0;
        int code = words.length > 2 ? toInt(words[2], 0) : 0;
        long time = words.length > 4 ? parseTime(words[3] + " " + words[4]) : 0; //$NON-NLS-1$

        return new ErrorLogEntry(plugin, severity, code, time);
    }

    private static int toInt(String aText, int aDefault) {
        try {
            return Integer.parseInt(aText);
        } catch (NumberFormatException e) {
            return aDefault;
        }
    }
}
