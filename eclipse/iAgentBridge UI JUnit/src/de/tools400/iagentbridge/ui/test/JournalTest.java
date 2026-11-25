/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import de.tools400.iagentbridge.ui.model.Journal;
import de.tools400.iagentbridge.ui.model.JournalEntry;
import de.tools400.iagentbridge.ui.model.PendingFailures;

public class JournalTest {

    @Test
    public void testAdd() {
        Journal journal = new Journal(Journal.DEFAULT_CAPACITY);

        JournalEntry click = journal.add("ui_click", "{\"ref\":\"w20\"}", "Preferences", "button", "Apply and Close", "button(\"Apply and Close\")",
            null);
        JournalEntry select = journal.add("ui_select", "{\"ref\":\"w2\",\"values\":[\"General > Editors\"]}", "Preferences", "item", "Editors",
            "tree()", Arrays.asList("General", "Editors"));

        List<JournalEntry> entries = journal.getEntries();
        assertEquals(2, entries.size());
        assertEquals(1, click.getNumber());
        assertEquals(2, select.getNumber());
        assertEquals("button(\"Apply and Close\")", click.getLocator());
        assertTrue(click.getPath().isEmpty());
        assertEquals(Arrays.asList("General", "Editors"), select.getPath());
        assertTrue(click.getTime() > 0);
    }

    /**
     * An action is pending, until its result is set: ok, or the error of an
     * action, that ended after a dialog was closed.
     */
    @Test
    public void testResult() {
        Journal journal = new Journal(Journal.DEFAULT_CAPACITY);

        JournalEntry entry = journal.add("ui_command", "{}", "RDi", "command", "org.eclipse.ui.window.preferences", null, null);
        assertEquals(JournalEntry.PENDING, entry.getResult());

        entry.setResult(JournalEntry.OK);
        assertEquals(JournalEntry.OK, entry.getResult());

        entry.setResult("java.lang.IllegalStateException: failed");
        assertEquals("java.lang.IllegalStateException: failed", journal.getEntries().get(0).getResult());
    }

    @Test
    public void testCapacity() {
        Journal journal = new Journal(3);

        for (int i = 0; i < 5; i++) {
            journal.add("ui_click", "{}", "s", "button", "b" + i, null, null);
        }

        List<JournalEntry> entries = journal.getEntries();
        assertEquals(3, entries.size());
        assertEquals("the oldest are dropped", 3, entries.get(0).getNumber());
        assertEquals(5, entries.get(2).getNumber());
    }

    @Test
    public void testPendingFailures() {
        PendingFailures failures = new PendingFailures();
        assertEquals("", failures.take());

        failures.add("ui_click w17", new IllegalStateException("listener failed"));
        failures.add("ui_command org.eclipse.ui.window.preferences", new NoClassDefFoundError("com/example/Missing"));

        assertEquals(
            "The earlier action ui_click w17 failed: java.lang.IllegalStateException: listener failed\n"
                + "The earlier action ui_command org.eclipse.ui.window.preferences failed: java.lang.NoClassDefFoundError: com/example/Missing\n",
            failures.take());
        assertEquals("taken once", "", failures.take());
    }
}
