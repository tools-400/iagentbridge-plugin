/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.preferences;

import org.eclipse.jface.preference.IPreferenceStore;

import de.tools400.iagentbridge.IAgentBridgePlugin;
import de.tools400.iagentbridge.ServerSettings;

/**
 * Preferences of the iAgentBridge plug-in. They are stored per workspace, so
 * that every RDi version, which has a workspace of its own, can use its own
 * port.
 */
public final class Preferences {

    private static Preferences instance;

    private IPreferenceStore preferenceStore;

    public static final String SERVER_ENABLED = "serverEnabled"; //$NON-NLS-1$
    public static final String PORT = "port"; //$NON-NLS-1$

    private static final boolean DEFAULT_SERVER_ENABLED = true;

    private Preferences() {
        // Singleton class
    }

    public synchronized static Preferences getInstance() {
        if (instance == null) {
            instance = new Preferences();
            instance.preferenceStore = IAgentBridgePlugin.getDefault().getPreferenceStore();
        }
        return instance;
    }

    public IPreferenceStore getPreferenceStore() {
        return preferenceStore;
    }

    public void initializeDefaultPreferences() {
        preferenceStore.setDefault(SERVER_ENABLED, DEFAULT_SERVER_ENABLED);
        // The default port depends on the version of RDi, e.g. 39096 for 9.6.
        preferenceStore.setDefault(PORT, IAgentBridgePlugin.getDefault().getDefaultPort());
    }

    /*
     * Preferences: GETTER
     */

    public boolean isServerEnabled() {
        return preferenceStore.getBoolean(SERVER_ENABLED);
    }

    public int getPort() {

        int port = preferenceStore.getInt(PORT);
        if (port < ServerSettings.MIN_PORT || port > ServerSettings.MAX_PORT) {
            return preferenceStore.getDefaultInt(PORT);
        }

        return port;
    }
}
