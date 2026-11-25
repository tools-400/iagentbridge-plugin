/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.model;

/**
 * The text of lines of the LPEX editor for 'ui_lpex': a header, then one line
 * per element with its number, right aligned, and a marker at the cursor.
 */
public final class LpexLines {

    /** Default number of lines, that one call reads */
    public static final int DEFAULT_LINES = 200;

    /** Most lines, that one call reads */
    public static final int MAX_LINES = 500;

    private final StringBuilder text = new StringBuilder();
    private final int width;
    private final int cursor;

    /**
     * Creates the text.
     *
     * @param aHeader - the first line, e.g. name, number of lines and cursor
     * @param aLastNumber - the highest number of the lines, for the width of
     *        the numbers
     * @param aCursor - the number of the line with the cursor, or 0
     */
    public LpexLines(String aHeader, int aLastNumber, int aCursor) {
        text.append(aHeader).append('\n');
        width = String.valueOf(Math.max(1, aLastNumber)).length();
        cursor = aCursor;
    }

    /**
     * Adds a line.
     *
     * @param aNumber - number of the element
     * @param aText - its text
     */
    public void add(int aNumber, String aText) {

        String number = String.valueOf(aNumber);
        for (int i = number.length(); i < width; i++) {
            text.append(' ');
        }
        text.append(number).append(aNumber == cursor ? " >" : " |").append(aText.isEmpty() ? "" : " ").append(aText).append('\n'); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
    }

    /**
     * Adds a note, e.g. that more lines follow.
     *
     * @param aNote - the note
     */
    public void addNote(String aNote) {
        text.append(aNote).append('\n');
    }

    @Override
    public String toString() {
        return text.toString();
    }
}
