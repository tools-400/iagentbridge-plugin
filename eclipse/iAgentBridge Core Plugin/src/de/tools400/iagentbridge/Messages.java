/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge;

import org.eclipse.osgi.util.NLS;

public class Messages extends NLS {
    private static final String BUNDLE_NAME = "de.tools400.iagentbridge.messages"; //$NON-NLS-1$

    public static String Description_preference_page;

    public static String Label_Group_Server;
    public static String Label_Start_server;
    public static String Tooltip_Start_server;
    public static String Label_Start_server_set_by_system_property_A_B;
    public static String Label_Port_colon;
    public static String Tooltip_Port;
    public static String Label_Port_set_by_system_property_A_B;

    public static String Label_Group_Connection;
    public static String Label_Status_colon;
    public static String Status_Running_on_A;
    public static String Status_Stopped;
    public static String Status_Error_A;
    public static String Error_Port_in_use_A;
    public static String Label_Start;
    public static String Tooltip_Start;
    public static String Label_Token_colon;
    public static String Label_Token_set_by_system_property_A;
    public static String Label_New_token;
    public static String Tooltip_New_token;
    public static String Title_New_token;
    public static String Question_New_token;
    public static String Error_New_token_A;
    public static String Label_Command_for_Claude_Code_colon;
    public static String Tooltip_Command_for_Claude_Code;
    public static String Label_Copy;
    public static String Tooltip_Copy;

    public static String Task_Refreshing_projects;
    public static String Task_Building_projects;
    public static String Task_Running_tests_A;
    public static String Task_Importing_projects;

    static {
        // initialize resource bundle
        NLS.initializeMessages(BUNDLE_NAME, Messages.class);
    }

    private Messages() {
    }
}
