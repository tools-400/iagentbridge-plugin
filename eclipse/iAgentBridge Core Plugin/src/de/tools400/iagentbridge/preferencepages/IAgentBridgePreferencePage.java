/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.preferencepages;

import java.io.IOException;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.IntegerFieldEditor;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import de.tools400.iagentbridge.ClientCommands;
import de.tools400.iagentbridge.IAgentBridgePlugin;
import de.tools400.iagentbridge.Messages;
import de.tools400.iagentbridge.ServerSettings;
import de.tools400.iagentbridge.mcp.McpHttpServer;
import de.tools400.iagentbridge.preferences.Preferences;

/**
 * Preference page of the MCP server: start, port, status, token and the
 * command, that registers the server in Claude Code.
 */
public class IAgentBridgePreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

    private static final int NUM_COLUMNS = 3;
    private static final int COMMAND_WIDTH_HINT = 400;

    /** Black circle, colored by the status of the server */
    private static final String STATUS_SYMBOL = "●"; //$NON-NLS-1$

    private Label statusSymbol;
    private Label statusLabel;
    private Button startButton;
    private Text tokenText;
    private Button newTokenButton;
    private Text commandText;

    public IAgentBridgePreferencePage() {
        super(GRID);
        setPreferenceStore(Preferences.getInstance().getPreferenceStore());
        setDescription(Messages.Description_preference_page);
    }

    public void init(IWorkbench aWorkbench) {
    }

    @Override
    protected void createFieldEditors() {

        ServerSettings settings = IAgentBridgePlugin.getDefault().getServerSettings();

        Group server = createGroup(getFieldEditorParent(), Messages.Label_Group_Server);

        BooleanFieldEditor enabled = new BooleanFieldEditor(Preferences.SERVER_ENABLED, Messages.Label_Start_server, server);
        enabled.getDescriptionControl(server).setToolTipText(Messages.Tooltip_Start_server);
        addField(enabled);

        IntegerFieldEditor port = new IntegerFieldEditor(Preferences.PORT, Messages.Label_Port_colon, server);
        port.setValidRange(ServerSettings.MIN_PORT, ServerSettings.MAX_PORT);
        port.getLabelControl(server).setToolTipText(Messages.Tooltip_Port);
        port.getTextControl(server).setToolTipText(Messages.Tooltip_Port);
        addField(port);

        if (settings.isPortSetBySystemProperty()) {
            port.setEnabled(false, server);
            Label portInfo = new Label(server, SWT.NONE);
            portInfo.setText(NLS.bind(Messages.Label_Port_set_by_system_property_A_B, ServerSettings.PROPERTY_PORT,
                System.getProperty(ServerSettings.PROPERTY_PORT).trim()));
            portInfo.setLayoutData(createSpanData(NUM_COLUMNS));
        }

        // FieldEditor.createControl() replaces the layout of its parent.
        server.setLayout(new GridLayout(NUM_COLUMNS, false));

        Group connection = createGroup(getFieldEditorParent(), Messages.Label_Group_Connection);
        connection.setLayout(new GridLayout(NUM_COLUMNS, false));

        new Label(connection, SWT.NONE).setText(Messages.Label_Status_colon);
        Composite status = new Composite(connection, SWT.NONE);
        GridLayout statusLayout = new GridLayout(2, false);
        statusLayout.marginWidth = 0;
        statusLayout.marginHeight = 0;
        status.setLayout(statusLayout);
        status.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        statusSymbol = new Label(status, SWT.NONE);
        statusSymbol.setText(STATUS_SYMBOL);
        statusLabel = new Label(status, SWT.NONE);
        statusLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        startButton = new Button(connection, SWT.PUSH);
        startButton.setText(Messages.Label_Start);
        startButton.setToolTipText(Messages.Tooltip_Start);
        startButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
        startButton.addListener(SWT.Selection, event -> startServer());

        new Label(connection, SWT.NONE).setText(Messages.Label_Token_colon);
        tokenText = new Text(connection, SWT.BORDER | SWT.READ_ONLY);
        tokenText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        newTokenButton = new Button(connection, SWT.PUSH);
        newTokenButton.setText(Messages.Label_New_token);
        newTokenButton.setToolTipText(Messages.Tooltip_New_token);
        newTokenButton.addListener(SWT.Selection, event -> renewToken());

        Label commandLabel = new Label(connection, SWT.NONE);
        commandLabel.setText(Messages.Label_Command_for_Claude_Code_colon);
        commandLabel.setLayoutData(createSpanData(NUM_COLUMNS));

        commandText = new Text(connection, SWT.BORDER | SWT.READ_ONLY | SWT.WRAP | SWT.MULTI);
        GridData commandData = createSpanData(NUM_COLUMNS - 1);
        commandData.widthHint = COMMAND_WIDTH_HINT;
        commandText.setLayoutData(commandData);
        commandText.setToolTipText(Messages.Tooltip_Command_for_Claude_Code);
        Button copyButton = new Button(connection, SWT.PUSH);
        copyButton.setText(Messages.Label_Copy);
        copyButton.setToolTipText(Messages.Tooltip_Copy);
        copyButton.setLayoutData(new GridData(SWT.FILL, SWT.TOP, false, false));
        copyButton.addListener(SWT.Selection, event -> copyCommand());

        updateConnection();
    }

    /**
     * Saves the preferences. A running server is restarted, if its port has
     * been changed. The check box only tells, whether the server is started,
     * when RDi starts: it neither starts nor stops the server now.
     */
    @Override
    public boolean performOk() {

        int portBefore = Preferences.getInstance().getPort();

        boolean result = super.performOk();

        IAgentBridgePlugin plugin = IAgentBridgePlugin.getDefault();
        if (plugin.getServerUrl() != null && portBefore != Preferences.getInstance().getPort()) {
            plugin.restartServer();
        }

        updateConnection();

        return result;
    }

    /**
     * Saves the preferences, e.g. a changed port, and starts the server.
     */
    private void startServer() {

        if (!isValid()) {
            return;
        }

        performOk();
        IAgentBridgePlugin.getDefault().startServerNow();
        updateConnection();
    }

    private void renewToken() {

        if (!MessageDialog.openQuestion(getShell(), Messages.Title_New_token, Messages.Question_New_token)) {
            return;
        }

        IAgentBridgePlugin plugin = IAgentBridgePlugin.getDefault();
        try {
            plugin.getServerSettings().renewToken();
        } catch (IOException e) {
            MessageDialog.openError(getShell(), Messages.Title_New_token, NLS.bind(Messages.Error_New_token_A, e.getMessage()));
            return;
        }

        if (plugin.getServerUrl() != null) {
            plugin.restartServer();
        }

        updateConnection();
    }

    private void copyCommand() {

        Clipboard clipboard = new Clipboard(getShell().getDisplay());
        try {
            clipboard.setContents(new Object[] { commandText.getText() }, new Transfer[] { TextTransfer.getInstance() });
        } finally {
            clipboard.dispose();
        }
    }

    /**
     * Shows status, token source and command of the running server, or of the
     * saved settings, if the server is not running.
     */
    private void updateConnection() {

        IAgentBridgePlugin plugin = IAgentBridgePlugin.getDefault();
        ServerSettings settings = plugin.getServerSettings();

        String url = plugin.getServerUrl();
        startButton.setEnabled(url == null);
        if (url != null) {
            setStatus(SWT.COLOR_DARK_GREEN, SWT.COLOR_WIDGET_FOREGROUND, NLS.bind(Messages.Status_Running_on_A, url));
        } else if (plugin.getServerError() != null) {
            setStatus(SWT.COLOR_RED, SWT.COLOR_RED, NLS.bind(Messages.Status_Error_A, plugin.getServerError()));
        } else {
            setStatus(SWT.COLOR_DARK_GRAY, SWT.COLOR_WIDGET_FOREGROUND, Messages.Status_Stopped);
        }

        String tokenFile = null;
        if (settings.isTokenSetBySystemProperty()) {
            tokenText.setText(NLS.bind(Messages.Label_Token_set_by_system_property_A, ServerSettings.PROPERTY_TOKEN));
            newTokenButton.setEnabled(false);
        } else {
            tokenFile = settings.getTokenFile().getPath();
            tokenText.setText(tokenFile);
            newTokenButton.setEnabled(true);
        }

        if (url == null) {
            url = "http://127.0.0.1:" + getPortForCommand(settings) + McpHttpServer.PATH; //$NON-NLS-1$
        }

        commandText.setText(ClientCommands.createClaudeCodeCommand(plugin.suggestServerName(), url, tokenFile));
        statusLabel.getParent().layout(true);
    }

    /**
     * Shows the status: a circle in the color of the status (green: running,
     * gray: stopped, red: error) and the text.
     */
    private void setStatus(int aSymbolColor, int aTextColor, String aText) {
        statusSymbol.setForeground(statusSymbol.getDisplay().getSystemColor(aSymbolColor));
        statusLabel.setForeground(statusLabel.getDisplay().getSystemColor(aTextColor));
        statusLabel.setText(aText);
    }

    private static String getPortForCommand(ServerSettings aSettings) {
        try {
            return String.valueOf(aSettings.getPort());
        } catch (IllegalArgumentException e) {
            return String.valueOf(IAgentBridgePlugin.getDefault().getDefaultPort());
        }
    }

    private Group createGroup(Composite aParent, String aText) {

        Group group = new Group(aParent, SWT.NONE);
        group.setText(aText);
        GridData groupData = new GridData(SWT.FILL, SWT.FILL, true, false);
        groupData.horizontalSpan = NUM_COLUMNS;
        group.setLayoutData(groupData);

        return group;
    }

    private static GridData createSpanData(int aSpan) {

        GridData data = new GridData(SWT.FILL, SWT.CENTER, true, false);
        data.horizontalSpan = aSpan;

        return data;
    }
}
