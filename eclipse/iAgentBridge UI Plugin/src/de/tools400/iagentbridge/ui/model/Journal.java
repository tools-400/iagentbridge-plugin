/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * The actions of the UI tools in a test instance, with the locators of their
 * targets, for SWTBot tests. Holds the newest actions only.
 */
public final class Journal {

    /** Number of actions, that the journal of a test instance holds */
    public static final int DEFAULT_CAPACITY = 1000;

    private final int capacity;
    private final Deque<JournalEntry> entries = new ArrayDeque<JournalEntry>();
    private int next = 1;

    /**
     * Creates a journal.
     *
     * @param aCapacity - number of actions, after which the oldest ones are
     *        dropped
     */
    public Journal(int aCapacity) {
        capacity = aCapacity;
    }

    /**
     * Adds an action. Its result is {@link JournalEntry#PENDING}, until it is
     * set.
     *
     * @param aTool - name of the tool
     * @param anArguments - arguments as JSON, without those, that do not
     *        describe the action
     * @param aShell - title of the shell of the target
     * @param aTargetType - type of the target, e.g. 'button' or 'item'
     * @param aTargetText - text of the target
     * @param aLocator - locator of the target or of the control of an item
     * @param aPath - texts of an item and its parents, or <code>null</code>
     * @return the entry
     */
    public synchronized JournalEntry add(String aTool, String anArguments, String aShell, String aTargetType, String aTargetText, String aLocator,
        List<String> aPath) {

        JournalEntry entry = new JournalEntry(next++, System.currentTimeMillis(), aTool, anArguments, aShell, aTargetType, aTargetText, aLocator,
            aPath);
        entries.addLast(entry);
        while (entries.size() > capacity) {
            entries.removeFirst();
        }

        return entry;
    }

    /**
     * Returns the actions, the oldest first.
     *
     * @return copy of the entries
     */
    public synchronized List<JournalEntry> getEntries() {
        return new ArrayList<JournalEntry>(entries);
    }
}
