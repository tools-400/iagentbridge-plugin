/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.tools;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.runtime.Platform;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import de.tools400.iagentbridge.mcp.McpToolResult;
import de.tools400.iagentbridge.mcp.ToolArguments;
import de.tools400.iagentbridge.ui.model.ErrorLogParser;
import de.tools400.iagentbridge.ui.model.ErrorLogReport;
import de.tools400.iagentbridge.ui.model.ErrorLogReport.Severity;

/**
 * Tool 'ui_error_log': returns the entries of the error log (.metadata/.log) of
 * the test instance. It reads the file in the thread of the server, without the
 * UI thread, so it answers also, when the user interface hangs.
 */
public class UiErrorLogTool extends AbstractUiTool {

    private static final String DESCRIPTION = "Returns the entries of the error log (.metadata/.log) of the test instance of RDi, " //$NON-NLS-1$
        + "with their stack traces and children: exceptions, that only the log shows, e.g. of a listener or a job. By default the errors " //$NON-NLS-1$
        + "and warnings since the start of the test instance, the newest 20. Works also, when the user interface of the test instance hangs." //$NON-NLS-1$
        + DESCRIPTION_SUFFIX;

    private static final String INPUT_SCHEMA = "{\"type\":\"object\",\"properties\":{" //$NON-NLS-1$
        + "\"since\":{\"type\":\"string\",\"description\":\"'session' for the entries since the start of the test instance, or a time " //$NON-NLS-1$
        + "like 2026-11-26T08:00:00 in the time zone of the computer. Default: session.\"}," //$NON-NLS-1$
        + "\"severity\":{\"type\":\"string\",\"enum\":[\"error\",\"warning\",\"info\"],\"description\":\"error: errors only; warning: " //$NON-NLS-1$
        + "errors and warnings; info: all entries. Default: warning.\"}," //$NON-NLS-1$
        + "\"maxEntries\":{\"type\":\"integer\",\"description\":\"Newest entries, that are returned, 1 to 100. Default: 20.\"}}}"; //$NON-NLS-1$

    private static final String SINCE_SESSION = "session"; //$NON-NLS-1$

    private static final String BACKUP_PREFIX = ".bak_"; //$NON-NLS-1$

    @Override
    public String getName() {
        return "ui_error_log"; //$NON-NLS-1$
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    public JsonObject getInputSchema() {
        return new JsonParser().parse(INPUT_SCHEMA).getAsJsonObject();
    }

    @Override
    protected McpToolResult executeInTestInstance(JsonObject anArguments) throws Exception {
        return execute(anArguments, Platform.getLogFileLocation().toFile(), startTime());
    }

    /**
     * Returns the entries of a log.
     *
     * @param anArguments - arguments of the tool
     * @param aLog - the file of the log
     * @param aStartTime - start of the test instance, in milliseconds since
     *        1970
     * @return the entries as text
     * @throws IllegalArgumentException if an argument is wrong
     * @throws IOException if the log cannot be read
     */
    McpToolResult execute(JsonObject anArguments, File aLog, long aStartTime) throws IOException {

        String since = UiArguments.getOptionalString(anArguments, "since"); //$NON-NLS-1$
        Severity severity = Severity.parse(UiArguments.getOptionalString(anArguments, "severity")); //$NON-NLS-1$
        int maxEntries = ToolArguments.getInt(anArguments, "maxEntries", ErrorLogReport.DEFAULT_ENTRIES, 1); //$NON-NLS-1$
        if (maxEntries > ErrorLogReport.MAX_ENTRIES) {
            throw new IllegalArgumentException("Argument 'maxEntries' must be at most " + ErrorLogReport.MAX_ENTRIES + "."); //$NON-NLS-1$ //$NON-NLS-2$
        }
        boolean sinceStart = since == null || SINCE_SESSION.equals(since);
        long sinceTime = sinceStart ? aStartTime : ErrorLogReport.parseSince(since);

        ErrorLogReport report = new ErrorLogReport(sinceTime, sinceStart, severity, maxEntries);
        String text = report.write(ErrorLogParser.parse(read(aLog)), isEarlierInBackup(aLog, aStartTime));
        // A failure of an earlier action is not in the log.
        return McpToolResult.success(takePendingFailures() + text);
    }

    /**
     * Returns the start of the test instance: the system property, that the
     * launcher of Eclipse sets and the log writes in !SESSION, or else the
     * start of the JVM.
     */
    private static long startTime() {
        try {
            return Long.parseLong(System.getProperty("eclipse.startTime")); //$NON-NLS-1$
        } catch (NumberFormatException e) {
            return ManagementFactory.getRuntimeMXBean().getStartTime();
        }
    }

    /**
     * Returns whether a backup of the log, that a rotation created, holds
     * entries of this session: it has the !SESSION of this start. A new log
     * begins with this !SESSION also without a rotation.
     */
    private static boolean isEarlierInBackup(File aLog, long aStartTime) throws IOException {

        File[] files = aLog.getParentFile() == null ? null : aLog.getParentFile().listFiles();
        if (files == null) {
            return false;
        }

        for (File file : files) {
            if (file.getName().startsWith(BACKUP_PREFIX) && file.getName().endsWith(".log") //$NON-NLS-1$
                && ErrorLogParser.sessions(read(file)).contains(Long.valueOf(aStartTime))) {
                return true;
            }
        }

        return false;
    }

    private static List<String> read(File aFile) throws IOException {
        if (!aFile.isFile()) {
            return Collections.emptyList();
        }
        // NIO: while the file is open, Equinox can still rename the log at a
        // rotation; an open FileInputStream lets File.renameTo() fail (tested
        // with the Java of RDi 9.6).
        return Files.readAllLines(aFile.toPath(), StandardCharsets.UTF_8);
    }
}
