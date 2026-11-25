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
 * A path of menu items or tree items like "Window > Preferences..." or "General
 * > Editors". The parts are separated by '>'; '\>' is a '>' in a name. Menu
 * texts are compared without mnemonic, without the shortcut after a tab and
 * without "..." at the end.
 */
public final class ItemPath {

    private static final String ELLIPSIS = "…"; //$NON-NLS-1$

    private ItemPath() {
    }

    /**
     * Splits a path into its parts.
     *
     * @param aPath - the path
     * @return the parts, trimmed
     * @throws IllegalArgumentException if the path or a part is empty
     */
    public static List<String> parse(String aPath) {

        if (aPath == null || aPath.trim().isEmpty()) {
            throw new IllegalArgumentException("The path is empty."); //$NON-NLS-1$
        }

        List<String> parts = new ArrayList<String>();
        StringBuilder part = new StringBuilder();
        for (int i = 0; i < aPath.length(); i++) {
            char c = aPath.charAt(i);
            if (c == '\\' && i + 1 < aPath.length() && aPath.charAt(i + 1) == '>') {
                part.append('>');
                i++;
            } else if (c == '>') {
                parts.add(checked(part.toString(), aPath));
                part.setLength(0);
            } else {
                part.append(c);
            }
        }
        parts.add(checked(part.toString(), aPath));

        return Collections.unmodifiableList(parts);
    }

    private static String checked(String aPart, String aPath) {
        String part = aPart.trim();
        if (part.isEmpty()) {
            throw new IllegalArgumentException("The path '" + aPath + "' has an empty part."); //$NON-NLS-1$ //$NON-NLS-2$
        }
        return part;
    }

    /**
     * Returns the text of a menu item, as a path names it: without mnemonic,
     * without the shortcut after a tab and without "..." at the end.
     *
     * @param aText - text of the menu item
     * @return the text for comparisons
     */
    public static String menuText(String aText) {
        return withoutShortcutAndDots(Mnemonics.remove(aText));
    }

    /**
     * Returns a text without the shortcut after a tab and without "..." at the
     * end.
     */
    private static String withoutShortcutAndDots(String aText) {

        String text = aText;
        int tab = text.indexOf('\t');
        if (tab >= 0) {
            text = text.substring(0, tab);
        }
        text = text.trim();
        if (text.endsWith("...")) { //$NON-NLS-1$
            text = text.substring(0, text.length() - 3).trim();
        } else if (text.endsWith(ELLIPSIS)) {
            text = text.substring(0, text.length() - 1).trim();
        }

        return text;
    }

    /**
     * Returns whether a menu item matches a part of a path. The part is a text,
     * as the snapshots and the messages show the item: without mnemonic, so an
     * '&' in it is a character of the text.
     *
     * @param aMenuText - text of the menu item
     * @param aPart - part of the path
     * @return <code>true</code>, if both are equal as menu texts
     */
    public static boolean matchesMenu(String aMenuText, String aPart) {
        return menuText(aMenuText).equals(withoutShortcutAndDots(aPart));
    }
}
