/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.preferences;

import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.ui.preferences.ScopedPreferenceStore;

/**
 * The preferences of the UI tools.
 */
public final class UiPreferences {

    /** ID of the plug-in, the qualifier of its preferences */
    public static final String PLUGIN_ID = "de.tools400.iagentbridge.ui.plugin"; //$NON-NLS-1$

    /** Preference: where the dialogs of the test instance lie */
    public static final String DIALOGS = "dialogs"; //$NON-NLS-1$

    /** Dialogs, that an action opens, are put behind all other windows */
    public static final String DIALOGS_BACKGROUND = "background"; //$NON-NLS-1$

    /** Dialogs, that an action opens, stay on top of the other windows */
    public static final String DIALOGS_FOREGROUND = "foreground"; //$NON-NLS-1$

    private static ScopedPreferenceStore store;

    private UiPreferences() {
    }

    /**
     * Returns the store of the preferences of the UI tools, with their
     * defaults. There is one store only, because every store registers a
     * listener at the preferences, that stays.
     *
     * @return the store
     */
    public static synchronized IPreferenceStore getStore() {
        if (store == null) {
            store = new ScopedPreferenceStore(InstanceScope.INSTANCE, PLUGIN_ID);
            store.setDefault(DIALOGS, DIALOGS_BACKGROUND);
        }
        return store;
    }

    /**
     * Returns whether the dialogs, that an action opens, are put behind all
     * other windows.
     *
     * @return <code>true</code>, unless the preference says 'foreground'
     */
    public static boolean isKeepDialogsBehind() {
        return !DIALOGS_FOREGROUND.equals(getStore().getString(DIALOGS));
    }
}
