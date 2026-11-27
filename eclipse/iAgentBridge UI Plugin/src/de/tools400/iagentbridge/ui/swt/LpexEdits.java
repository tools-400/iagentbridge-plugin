/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.swt;

import com.ibm.lpex.core.LpexView;

/**
 * Inserts text into an LPEX editor for the tool ui_lpex. Used in the UI thread
 * only.
 */
public final class LpexEdits {

    private LpexEdits() {
    }

    /**
     * Checks, that text can be inserted into the document of an editor.
     *
     * @param aView - the view of the editor
     * @throws ActionRejectedException if the document is read-only
     */
    public static void checkWritable(LpexView aView) throws ActionRejectedException {
        if (aView.queryOn("readonly")) { //$NON-NLS-1$
            throw new ActionRejectedException(readOnly(aView.query("name"))); //$NON-NLS-1$
        }
    }

    /**
     * Returns the text for a document, that is read-only.
     *
     * @param aName - the name of the document
     * @return text
     */
    static String readOnly(String aName) {
        return "The document \"" + aName + "\" is read-only; no text was inserted. If it was opened for browsing, open the source for editing " //$NON-NLS-1$ //$NON-NLS-2$
            + "instead and repeat; otherwise ask the developer, why it is read-only."; //$NON-NLS-1$
    }

    /**
     * Inserts text at the cursor, also in replace mode. The insertion is closed
     * like the change of a key, so that the editor counts it and an undo takes
     * it back on its own, and parsed at once, so that the parser of the
     * document, e.g. the one of RPG, colors it without a further key.
     *
     * @param aView - the view of the editor
     * @param aText - the text
     */
    public static void insert(LpexView aView, String aText) {
        aView.doDefaultCommand("insertText " + aText); //$NON-NLS-1$
        aView.doDefaultCommand("undo check"); //$NON-NLS-1$
        aView.doDefaultCommand("parse"); //$NON-NLS-1$
    }
}
