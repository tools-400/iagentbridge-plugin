/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui;

import org.eclipse.osgi.util.NLS;

/**
 * Texts of the user interface of the UI plug-in. The texts for the agent, e.g.
 * the descriptions of the tools, are English and not translated.
 */
public class Messages extends NLS {

    private static final String BUNDLE_NAME = "de.tools400.iagentbridge.ui.messages"; //$NON-NLS-1$

    public static String Description_preference_page;
    public static String Label_Dialogs_of_the_test_instance;
    public static String Label_Dialogs_background;
    public static String Label_Dialogs_foreground;
    public static String Note_Setting_is_forwarded;

    static {
        NLS.initializeMessages(BUNDLE_NAME, Messages.class);
    }

    private Messages() {
    }
}
