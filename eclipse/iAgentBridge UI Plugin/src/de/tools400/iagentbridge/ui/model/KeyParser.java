/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parses keys like "ESC", "Ctrl+S" or "Shift+F10 a", separated by blanks. A key
 * is a single character or a name; modifiers are joined with '+'.
 */
public final class KeyParser {

    /** Names of the keys, with their alternative spellings */
    private static final Map<String, String> NAMES = new HashMap<String, String>();

    static {
        name(KeyStroke.ESC, "ESC", "ESCAPE"); //$NON-NLS-1$ //$NON-NLS-2$
        name(KeyStroke.ENTER, "ENTER", "RETURN", "CR"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        name(KeyStroke.TAB, "TAB"); //$NON-NLS-1$
        name(KeyStroke.SPACE, "SPACE"); //$NON-NLS-1$
        name(KeyStroke.BACKSPACE, "BACKSPACE", "BS"); //$NON-NLS-1$ //$NON-NLS-2$
        name(KeyStroke.DELETE, "DELETE", "DEL"); //$NON-NLS-1$ //$NON-NLS-2$
        name(KeyStroke.INSERT, "INSERT", "INS"); //$NON-NLS-1$ //$NON-NLS-2$
        name(KeyStroke.HOME, "HOME"); //$NON-NLS-1$
        name(KeyStroke.END, "END"); //$NON-NLS-1$
        name(KeyStroke.PAGE_UP, "PAGE_UP", "PAGEUP", "PGUP"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        name(KeyStroke.PAGE_DOWN, "PAGE_DOWN", "PAGEDOWN", "PGDN"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        name(KeyStroke.UP, "UP", "ARROW_UP"); //$NON-NLS-1$ //$NON-NLS-2$
        name(KeyStroke.DOWN, "DOWN", "ARROW_DOWN"); //$NON-NLS-1$ //$NON-NLS-2$
        name(KeyStroke.LEFT, "LEFT", "ARROW_LEFT"); //$NON-NLS-1$ //$NON-NLS-2$
        name(KeyStroke.RIGHT, "RIGHT", "ARROW_RIGHT"); //$NON-NLS-1$ //$NON-NLS-2$
        for (int i = 1; i <= KeyStroke.MAX_FUNCTION_KEY; i++) {
            name("F" + i, "F" + i); //$NON-NLS-1$ //$NON-NLS-2$
        }
    }

    private KeyParser() {
    }

    private static void name(String aName, String... aSpellings) {
        for (String spelling : aSpellings) {
            NAMES.put(spelling, aName);
        }
    }

    /**
     * Parses keys.
     *
     * @param aKeys - keys separated by blanks, e.g. "Ctrl+A Delete"
     * @return the keys in their order
     * @throws IllegalArgumentException if a key or a modifier is unknown
     */
    public static List<KeyStroke> parse(String aKeys) {

        String keys = aKeys == null ? "" : aKeys.trim(); //$NON-NLS-1$
        if (keys.isEmpty()) {
            throw new IllegalArgumentException("Argument 'keys' is empty."); //$NON-NLS-1$
        }

        List<KeyStroke> strokes = new ArrayList<KeyStroke>();
        for (String token : keys.split("\\s+")) { //$NON-NLS-1$
            strokes.add(parseStroke(token));
        }

        return Collections.unmodifiableList(strokes);
    }

    private static KeyStroke parseStroke(String aToken) {

        // "+" alone and "Ctrl++" are the key '+'.
        List<String> parts = new ArrayList<String>(Arrays.asList(aToken.split("\\+", -1))); //$NON-NLS-1$
        String key;
        if (aToken.endsWith("++") || aToken.equals("+")) { //$NON-NLS-1$ //$NON-NLS-2$
            key = "+"; //$NON-NLS-1$
            parts = parts.subList(0, Math.max(0, parts.size() - 2));
        } else {
            key = parts.remove(parts.size() - 1);
        }

        boolean ctrl = false;
        boolean shift = false;
        boolean alt = false;
        for (String modifier : parts) {
            switch (modifier.toUpperCase(Locale.ENGLISH)) {
            case "CTRL": //$NON-NLS-1$
            case "CONTROL": //$NON-NLS-1$
                ctrl = true;
                break;
            case "SHIFT": //$NON-NLS-1$
                shift = true;
                break;
            case "ALT": //$NON-NLS-1$
                alt = true;
                break;
            default:
                throw new IllegalArgumentException("Unknown modifier '" + modifier + "' in '" + aToken + "'. Use Ctrl, Shift and Alt."); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            }
        }

        if (key.length() == 1) {
            return KeyStroke.character(key.charAt(0), ctrl, shift, alt);
        }

        String name = NAMES.get(key.toUpperCase(Locale.ENGLISH));
        if (name == null) {
            throw new IllegalArgumentException("Unknown key '" + key + "' in '" + aToken + "'. Use a single character or a name like ESC, ENTER, " //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                + "TAB, SPACE, BACKSPACE, DELETE, INSERT, HOME, END, PAGE_UP, PAGE_DOWN, UP, DOWN, LEFT, RIGHT, F1 to F12."); //$NON-NLS-1$
        }

        return KeyStroke.named(name, ctrl, shift, alt);
    }
}
