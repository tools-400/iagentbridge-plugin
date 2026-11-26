/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import de.tools400.iagentbridge.ui.model.ErrorLogEntry;
import de.tools400.iagentbridge.ui.model.ErrorLogParser;
import de.tools400.iagentbridge.ui.model.ErrorLogReport;
import de.tools400.iagentbridge.ui.model.ErrorLogReport.Severity;

/**
 * The error log of a test instance: the format of the file .metadata/.log, as
 * Equinox writes it, the filters and the text of ui_error_log.
 */
public class ErrorLogTest {

    private static final String SESSION_1 = "2026-11-26 11:00:00.000"; //$NON-NLS-1$
    private static final String SESSION_2 = "2026-11-26 11:52:48.287"; //$NON-NLS-1$

    private static final List<String> LOG = Arrays.asList( //
        "!SESSION " + SESSION_1 + " -----------------------------------------------", //$NON-NLS-1$ //$NON-NLS-2$
        "eclipse.buildId=unknown", //$NON-NLS-1$
        "java.version=1.8.0_351", //$NON-NLS-1$
        "", //$NON-NLS-1$
        "!ENTRY old.bundle 4 0 2026-11-26 11:00:05.000", //$NON-NLS-1$
        "!MESSAGE an error of the session before", //$NON-NLS-1$
        "", //$NON-NLS-1$
        "!SESSION " + SESSION_2 + " -----------------------------------------------", //$NON-NLS-1$ //$NON-NLS-2$
        "eclipse.buildId=unknown", //$NON-NLS-1$
        "BootLoader constants: OS=win32, ARCH=x86_64, WS=win32, NL=de_DE", //$NON-NLS-1$
        "Command-line arguments:  -os win32 -ws win32", //$NON-NLS-1$
        "", //$NON-NLS-1$
        "!ENTRY org.eclipse.core.resources 2 10035 2026-11-26 11:52:50.100", //$NON-NLS-1$
        "!MESSAGE The workspace exited with unsaved changes in the previous session.", //$NON-NLS-1$
        "", //$NON-NLS-1$
        "!ENTRY test.bundle 4 4 2026-11-26 11:53:00.181", //$NON-NLS-1$
        "!MESSAGE line one", //$NON-NLS-1$
        "line two ä", //$NON-NLS-1$
        "!STACK 1", //$NON-NLS-1$
        "java.lang.IllegalStateException: outer", //$NON-NLS-1$
        "\tat A.b(A.java:1)", //$NON-NLS-1$
        "Caused by: java.lang.RuntimeException: inner cause", //$NON-NLS-1$
        "\t... 2 more", //$NON-NLS-1$
        "!SUBENTRY 1 child.bundle 2 7 2026-11-26 11:53:00.181", //$NON-NLS-1$
        "!MESSAGE child message", //$NON-NLS-1$
        "", //$NON-NLS-1$
        "!ENTRY com.ibm.etools.stacktool 0 0 2026-11-26 11:53:01.000", //$NON-NLS-1$
        "!MESSAGE plugin is early starting...", //$NON-NLS-1$
        "", //$NON-NLS-1$
        "!ENTRY info.bundle 1 0 2026-11-26 11:53:02.000", //$NON-NLS-1$
        "!MESSAGE an info", //$NON-NLS-1$
        "" //$NON-NLS-1$
    );

    @Test
    public void testParse() {

        List<ErrorLogEntry> entries = ErrorLogParser.parse(LOG);

        assertEquals(5, entries.size());
        ErrorLogEntry error = entries.get(2);
        assertEquals("test.bundle", error.getPlugin()); //$NON-NLS-1$
        assertEquals(4, error.getSeverity());
        assertEquals(4, error.getCode());
        assertEquals(ErrorLogParser.parseTime("2026-11-26 11:53:00.181"), error.getTime()); //$NON-NLS-1$
        assertEquals("line one\nline two ä", error.getMessage()); //$NON-NLS-1$
        assertEquals("java.lang.IllegalStateException: outer\n\tat A.b(A.java:1)\nCaused by: java.lang.RuntimeException: inner cause\n\t... 2 more", //$NON-NLS-1$
            error.getStack());
        assertEquals(1, error.getChildren().size());
        ErrorLogEntry child = error.getChildren().get(0);
        assertEquals("child.bundle", child.getPlugin()); //$NON-NLS-1$
        assertEquals(2, child.getSeverity());
        assertEquals("child message", child.getMessage()); //$NON-NLS-1$
        assertNull(child.getStack());
        assertEquals(0, entries.get(3).getSeverity());
        assertEquals("plugin is early starting...", entries.get(3).getMessage()); //$NON-NLS-1$
    }

    @Test
    public void testSessions() {
        assertEquals(Arrays.asList(Long.valueOf(ErrorLogParser.parseTime(SESSION_1)), Long.valueOf(ErrorLogParser.parseTime(SESSION_2))),
            ErrorLogParser.sessions(LOG));
        assertTrue(ErrorLogParser.sessions(new ArrayList<String>()).isEmpty());
    }

    @Test
    public void testParse_withoutSession() {
        List<ErrorLogEntry> entries = ErrorLogParser.parse(Arrays.asList("!ENTRY a.b 4 0 2026-11-26 11:00:00.000", "!MESSAGE m")); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals(1, entries.size());
        assertEquals("m", entries.get(0).getMessage()); //$NON-NLS-1$
    }

    @Test
    public void testSeverity() {
        assertTrue(Severity.ERROR.accepts(4));
        assertFalse(Severity.ERROR.accepts(2));
        assertTrue(Severity.WARNING.accepts(2));
        assertTrue(Severity.WARNING.accepts(4));
        assertFalse(Severity.WARNING.accepts(1));
        assertFalse(Severity.WARNING.accepts(0));
        assertTrue(Severity.INFO.accepts(0));
        assertTrue(Severity.INFO.accepts(1));
        assertTrue(Severity.INFO.accepts(8));
        assertEquals(Severity.WARNING, Severity.parse(null));
        assertEquals(Severity.ERROR, Severity.parse("error")); //$NON-NLS-1$
        try {
            Severity.parse("fatal"); //$NON-NLS-1$
            fail("IllegalArgumentException expected"); //$NON-NLS-1$
        } catch (IllegalArgumentException e) {
            assertEquals("Argument 'severity' must be error, warning or info, not 'fatal'.", e.getMessage()); //$NON-NLS-1$
        }
    }

    @Test
    public void testWrite_session() {

        long start = ErrorLogParser.parseTime(SESSION_2);
        String text = new ErrorLogReport(start, true, Severity.WARNING, 20).write(ErrorLogParser.parse(LOG), false);

        assertEquals("2 entries since the start of the test instance (2026-11-26 11:52:48.287), errors and warnings, 2 shown:\n" //$NON-NLS-1$
            + "2026-11-26 11:52:50.100 WARNING org.eclipse.core.resources (code 10035): The workspace exited with unsaved changes in the previous session.\n" //$NON-NLS-1$
            + "2026-11-26 11:53:00.181 ERROR test.bundle (code 4): line one\n" //$NON-NLS-1$
            + "  line two ä\n" //$NON-NLS-1$
            + "  java.lang.IllegalStateException: outer\n" //$NON-NLS-1$
            + "  \tat A.b(A.java:1)\n" //$NON-NLS-1$
            + "  Caused by: java.lang.RuntimeException: inner cause\n" //$NON-NLS-1$
            + "  \t... 2 more\n" //$NON-NLS-1$
            + "  WARNING child.bundle (code 7): child message\n", text); //$NON-NLS-1$
    }

    @Test
    public void testWrite_maxEntriesAndInfo() {

        long start = ErrorLogParser.parseTime(SESSION_2);
        String text = new ErrorLogReport(start, true, Severity.INFO, 2).write(ErrorLogParser.parse(LOG), false);

        assertTrue(text,
            text.startsWith("4 entries since the start of the test instance (2026-11-26 11:52:48.287), all severities, the last 2 shown:\n" //$NON-NLS-1$
                + "2026-11-26 11:53:01.000 OK com.ibm.etools.stacktool (code 0): plugin is early starting...\n" //$NON-NLS-1$
                + "2026-11-26 11:53:02.000 INFO info.bundle (code 0): an info\n")); //$NON-NLS-1$
    }

    @Test
    public void testWrite_sinceTimeAndBackup() {

        long since = ErrorLogParser.parseTime("2026-11-26 11:53:00.000"); //$NON-NLS-1$
        String text = new ErrorLogReport(since, false, Severity.ERROR, 20).write(ErrorLogParser.parse(LOG), true);

        // The first line: a long result may be cut at its end.
        assertTrue(text, text.startsWith("1 entry since 2026-11-26 11:53:00.000, errors, 1 shown; earlier entries of this session are in " //$NON-NLS-1$
            + "the files .bak_*.log beside the log, which this tool does not read:\n")); //$NON-NLS-1$
        assertEquals(
            "No entries since 2026-11-26 13:00:00.000, errors; earlier entries of this session are in the files .bak_*.log beside the " //$NON-NLS-1$
                + "log, which this tool does not read.\n", //$NON-NLS-1$
            new ErrorLogReport(ErrorLogParser.parseTime("2026-11-26 13:00:00.000"), false, Severity.ERROR, 20).write(ErrorLogParser.parse(LOG), //$NON-NLS-1$
                true));
    }

    @Test
    public void testWrite_childrenCut() {
        List<String> lines = new ArrayList<String>(Arrays.asList("!ENTRY a.b 4 0 2026-11-26 11:00:00.000", "!MESSAGE parent")); //$NON-NLS-1$ //$NON-NLS-2$
        for (int i = 0; i < 25; i++) {
            lines.add("!SUBENTRY 1 c.d 4 0 2026-11-26 11:00:00.000"); //$NON-NLS-1$
            lines.add("!MESSAGE child " + i); //$NON-NLS-1$
        }
        String text = new ErrorLogReport(0, false, Severity.WARNING, 20).write(ErrorLogParser.parse(lines), false);

        assertTrue(text, text.contains("child 19\n  ... 5 more child entries\n")); //$NON-NLS-1$
        assertFalse(text, text.contains("child 20")); //$NON-NLS-1$
    }

    @Test
    public void testParse_subentriesTwoLevelsDeep() {
        List<ErrorLogEntry> entries = ErrorLogParser.parse(Arrays.asList( //
            "!ENTRY a.b 4 0 2026-11-26 11:00:00.000", //$NON-NLS-1$
            "!MESSAGE parent", //$NON-NLS-1$
            "!SUBENTRY 1 c.d 4 0 2026-11-26 11:00:00.000", //$NON-NLS-1$
            "!MESSAGE child", //$NON-NLS-1$
            "!SUBENTRY 2 e.f 4 0 2026-11-26 11:00:00.000", //$NON-NLS-1$
            "!MESSAGE grandchild", //$NON-NLS-1$
            "!SUBENTRY 1 g.h 2 0 2026-11-26 11:00:00.000", //$NON-NLS-1$
            "!MESSAGE second child", //$NON-NLS-1$
            "" //$NON-NLS-1$
        ));

        ErrorLogEntry parent = entries.get(0);
        assertEquals(2, parent.getChildren().size());
        assertEquals("child", parent.getChildren().get(0).getMessage()); //$NON-NLS-1$
        assertEquals("grandchild", parent.getChildren().get(0).getChildren().get(0).getMessage()); //$NON-NLS-1$
        assertEquals("second child", parent.getChildren().get(1).getMessage()); //$NON-NLS-1$
    }

    @Test
    public void testWrite_none() {
        long start = ErrorLogParser.parseTime("2026-11-26 13:00:00.000"); //$NON-NLS-1$
        assertEquals("No entries since the start of the test instance (2026-11-26 13:00:00.000), errors and warnings.\n", //$NON-NLS-1$
            new ErrorLogReport(start, true, Severity.WARNING, 20).write(ErrorLogParser.parse(LOG), false));
    }

    @Test
    public void testWrite_cuts() {

        StringBuilder longMessage = new StringBuilder();
        for (int i = 0; i < 2100; i++) {
            longMessage.append('x');
        }
        List<String> lines = new ArrayList<String>(Arrays.asList("!ENTRY a.b 4 0 2026-11-26 11:00:00.000", "!MESSAGE " + longMessage, "!STACK 0")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        for (int i = 0; i < 40; i++) {
            lines.add("\tat A.m" + i + "(A.java)"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        String text = new ErrorLogReport(0, false, Severity.WARNING, 20).write(ErrorLogParser.parse(lines), false);

        assertTrue(text, text.contains("x... (2100 characters)\n")); //$NON-NLS-1$
        assertTrue(text, text.contains("\tat A.m29(A.java)\n  ... 10 more lines\n")); //$NON-NLS-1$
        assertFalse(text, text.contains("A.m30")); //$NON-NLS-1$
    }

    @Test
    public void testParseSince() {
        assertEquals(ErrorLogParser.parseTime("2026-11-26 08:00:00.000"), ErrorLogReport.parseSince("2026-11-26T08:00:00")); //$NON-NLS-1$ //$NON-NLS-2$
        try {
            ErrorLogReport.parseSince("yesterday"); //$NON-NLS-1$
            fail("IllegalArgumentException expected"); //$NON-NLS-1$
        } catch (IllegalArgumentException e) {
            assertEquals("Argument 'since' must be 'session' or a time like 2026-11-26T08:00:00, not 'yesterday'.", e.getMessage()); //$NON-NLS-1$
        }
    }

    @Test
    public void testParseSince_textAfterTheTime() {
        for (String since : new String[] { "2026-11-26T08:00:00Z", "2026-11-26T08:00:00+05:00", "2026-11-26T08:00:00.999", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            "2026-11-26T08:00:00 tomorrow" }) { //$NON-NLS-1$
            try {
                ErrorLogReport.parseSince(since);
                fail("IllegalArgumentException expected for " + since); //$NON-NLS-1$
            } catch (IllegalArgumentException e) {
                assertTrue(e.getMessage(), e.getMessage().endsWith("not '" + since + "'.")); //$NON-NLS-1$ //$NON-NLS-2$
            }
        }
    }

    @Test
    public void testParse_blankLinesInMessageAndStack() {
        List<ErrorLogEntry> entries = ErrorLogParser.parse(Arrays.asList( //
            "!ENTRY a.bundle 4 0 2026-11-26 12:00:01.000", //$NON-NLS-1$
            "!MESSAGE Unit test ended unexpected with error message:", //$NON-NLS-1$
            "", //$NON-NLS-1$
            "CPF9898 second paragraph", //$NON-NLS-1$
            "!STACK 0", //$NON-NLS-1$
            "java.lang.IllegalStateException: x", //$NON-NLS-1$
            "", //$NON-NLS-1$
            "\tat a.B.c(B.java:1)", //$NON-NLS-1$
            "", //$NON-NLS-1$
            "!ENTRY b.bundle 2 0 2026-11-26 12:00:02.000", //$NON-NLS-1$
            "!MESSAGE next", //$NON-NLS-1$
            "" //$NON-NLS-1$
        ));

        assertEquals(2, entries.size());
        assertEquals("Unit test ended unexpected with error message:\n\nCPF9898 second paragraph", entries.get(0).getMessage()); //$NON-NLS-1$
        assertEquals("java.lang.IllegalStateException: x\n\n\tat a.B.c(B.java:1)", entries.get(0).getStack()); //$NON-NLS-1$
        assertEquals("next", entries.get(1).getMessage()); //$NON-NLS-1$
    }

    @Test
    public void testParse_longStackInLinearTime() {
        List<String> lines = new ArrayList<String>();
        lines.add("!ENTRY a.bundle 4 0 2026-11-26 12:00:01.000"); //$NON-NLS-1$
        lines.add("!MESSAGE java.lang.StackOverflowError"); //$NON-NLS-1$
        lines.add("!STACK 0"); //$NON-NLS-1$
        for (int i = 0; i < 20000; i++) {
            lines.add("\tat de.tools400.Recursion.call(Recursion.java:" + i + ")"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        lines.add(""); //$NON-NLS-1$

        long start = System.currentTimeMillis();
        List<ErrorLogEntry> entries = ErrorLogParser.parse(lines);
        long millis = System.currentTimeMillis() - start;

        assertEquals(20000, entries.get(0).getStack().split("\n").length); //$NON-NLS-1$
        assertTrue("20 000 lines of a stack took " + millis + " ms", millis < 1000); //$NON-NLS-1$ //$NON-NLS-2$
    }
}
