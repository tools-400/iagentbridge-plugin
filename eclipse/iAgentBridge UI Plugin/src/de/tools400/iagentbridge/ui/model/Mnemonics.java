/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.model;

/**
 * Removes the mnemonics from the text of a widget, e.g. of a button or a label.
 */
public final class Mnemonics {

    private Mnemonics() {
    }

    /**
     * Returns the text of a widget, as SWT shows it: a single '&amp;' marks the
     * mnemonic and is left out, '&amp;&amp;' is shown as '&amp;'.
     *
     * @param aText - text of the widget or <code>null</code>
     * @return text without mnemonic or <code>null</code>
     */
    public static String remove(String aText) {

        if (aText == null || aText.indexOf('&') < 0) {
            return aText;
        }

        StringBuilder text = new StringBuilder(aText.length());
        for (int i = 0; i < aText.length(); i++) {
            char c = aText.charAt(i);
            if (c != '&') {
                text.append(c);
            } else if (i + 1 < aText.length() && aText.charAt(i + 1) == '&') {
                text.append('&');
                i++;
            }
        }

        return text.toString();
    }
}
