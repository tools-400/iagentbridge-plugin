/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.model;

/**
 * A key with its modifiers: a character or a named key like ESC or F5.
 */
public final class KeyStroke {

    public static final String ESC = "ESC"; //$NON-NLS-1$
    public static final String ENTER = "ENTER"; //$NON-NLS-1$
    public static final String TAB = "TAB"; //$NON-NLS-1$
    public static final String SPACE = "SPACE"; //$NON-NLS-1$
    public static final String BACKSPACE = "BACKSPACE"; //$NON-NLS-1$
    public static final String DELETE = "DELETE"; //$NON-NLS-1$
    public static final String INSERT = "INSERT"; //$NON-NLS-1$
    public static final String HOME = "HOME"; //$NON-NLS-1$
    public static final String END = "END"; //$NON-NLS-1$
    public static final String PAGE_UP = "PAGE_UP"; //$NON-NLS-1$
    public static final String PAGE_DOWN = "PAGE_DOWN"; //$NON-NLS-1$
    public static final String UP = "UP"; //$NON-NLS-1$
    public static final String DOWN = "DOWN"; //$NON-NLS-1$
    public static final String LEFT = "LEFT"; //$NON-NLS-1$
    public static final String RIGHT = "RIGHT"; //$NON-NLS-1$

    /** Highest function key: F1 to F12 */
    public static final int MAX_FUNCTION_KEY = 12;

    private final String name;
    private final char character;
    private final boolean ctrl;
    private final boolean shift;
    private final boolean alt;

    private KeyStroke(String aName, char aCharacter, boolean aCtrl, boolean aShift, boolean anAlt) {
        name = aName;
        character = aCharacter;
        ctrl = aCtrl;
        shift = aShift;
        alt = anAlt;
    }

    static KeyStroke character(char aCharacter, boolean aCtrl, boolean aShift, boolean anAlt) {
        return new KeyStroke(null, aCharacter, aCtrl, aShift, anAlt);
    }

    static KeyStroke named(String aName, boolean aCtrl, boolean aShift, boolean anAlt) {
        return new KeyStroke(aName, '\0', aCtrl, aShift, anAlt);
    }

    /**
     * Returns the name of a named key.
     *
     * @return name like {@link #ESC} or 'F5', or <code>null</code> for a
     *         character
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the character of a character key.
     *
     * @return the character, or '\0' for a named key
     */
    public char getCharacter() {
        return character;
    }

    /**
     * Returns the number of a function key.
     *
     * @return 1 to 12 for F1 to F12, otherwise 0
     */
    public int getFunctionKey() {
        if (name != null && name.length() > 1 && name.charAt(0) == 'F' && Character.isDigit(name.charAt(1))) {
            return Integer.parseInt(name.substring(1));
        }
        return 0;
    }

    public boolean isCtrl() {
        return ctrl;
    }

    public boolean isShift() {
        return shift;
    }

    public boolean isAlt() {
        return alt;
    }

    /**
     * Returns whether this is a named key without modifiers.
     *
     * @param aName - name of the key, e.g. {@link #ESC}
     * @return <code>true</code>, if it is this key without Ctrl, Shift and Alt
     */
    public boolean is(String aName) {
        return aName.equals(name) && !ctrl && !shift && !alt;
    }

    @Override
    public String toString() {
        return (ctrl ? "Ctrl+" : "") + (shift ? "Shift+" : "") + (alt ? "Alt+" : "") + (name != null ? name : String.valueOf(character)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
    }
}
