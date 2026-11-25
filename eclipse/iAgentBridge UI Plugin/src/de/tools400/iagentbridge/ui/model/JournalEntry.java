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
 * An action in the journal: what was done to which widget, with its locator in
 * the style of SWTBot, and how it ended.
 */
public final class JournalEntry {

    /** Result of an action, that ended without an error */
    public static final String OK = "ok"; //$NON-NLS-1$

    /** Result of an action, that had not returned, when its tool returned */
    public static final String PENDING = "pending"; //$NON-NLS-1$

    private final int number;
    private final long time;
    private final String tool;
    private final String arguments;
    private final String shell;
    private final String targetType;
    private final String targetText;
    private final String locator;
    private final List<String> path;
    private String result;

    JournalEntry(int aNumber, long aTime, String aTool, String anArguments, String aShell, String aTargetType, String aTargetText, String aLocator,
        List<String> aPath) {
        number = aNumber;
        time = aTime;
        tool = aTool;
        arguments = anArguments;
        shell = aShell;
        targetType = aTargetType;
        targetText = aTargetText;
        locator = aLocator;
        path = aPath == null ? Collections.<String> emptyList() : Collections.unmodifiableList(new ArrayList<String>(aPath));
        result = PENDING;
    }

    public int getNumber() {
        return number;
    }

    /**
     * Returns the time of the action.
     *
     * @return milliseconds since 1970
     */
    public long getTime() {
        return time;
    }

    public String getTool() {
        return tool;
    }

    /**
     * Returns the arguments of the tool as JSON, without the arguments, that do
     * not describe the action.
     *
     * @return arguments
     */
    public String getArguments() {
        return arguments;
    }

    /**
     * Returns the title of the shell of the target.
     *
     * @return title or <code>null</code>
     */
    public String getShell() {
        return shell;
    }

    public String getTargetType() {
        return targetType;
    }

    public String getTargetText() {
        return targetText;
    }

    /**
     * Returns the locator of the target or of the control of an item.
     *
     * @return locator or <code>null</code>
     */
    public String getLocator() {
        return locator;
    }

    /**
     * Returns the texts of an item and its parents, e.g. of a tree item.
     *
     * @return path, empty for a widget
     */
    public List<String> getPath() {
        return path;
    }

    /**
     * Returns how the action ended: {@link #OK}, {@link #PENDING} or the text
     * of its error.
     *
     * @return result
     */
    public synchronized String getResult() {
        return result;
    }

    /**
     * Sets how the action ended.
     *
     * @param aResult - {@link #OK}, {@link #PENDING} or the text of an error
     */
    public synchronized void setResult(String aResult) {
        result = aResult;
    }
}
