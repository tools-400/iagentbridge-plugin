/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge;

import java.io.IOException;
import java.net.BindException;
import java.util.List;

import org.eclipse.core.runtime.IProduct;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.osgi.util.NLS;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.Version;

import de.tools400.iagentbridge.mcp.McpDispatcher;
import de.tools400.iagentbridge.mcp.McpHttpServer;
import de.tools400.iagentbridge.preferences.Preferences;
import de.tools400.iagentbridge.tools.CleanUpTool;
import de.tools400.iagentbridge.tools.GetProblemsTool;
import de.tools400.iagentbridge.tools.ImportProjectsTool;
import de.tools400.iagentbridge.tools.RunTestsTool;
import de.tools400.iagentbridge.tools.WorkspaceInfoTool;

/**
 * The activator class controls the plug-in life cycle and the MCP server.
 */
public class IAgentBridgePlugin extends AbstractUIPlugin {

    /** The plug-in ID */
    public static final String PLUGIN_ID = "de.tools400.iagentbridge.core.plugin"; //$NON-NLS-1$

    /** Name of the server returned to the MCP client */
    public static final String SERVER_NAME = "iagentbridge"; //$NON-NLS-1$

    private static final String INSTRUCTIONS = "Tools of a running IBM Rational Developer for i (RDi, Eclipse) instance. " //$NON-NLS-1$
        + "clean_up runs 'Source > Clean Up' with the clean-up profile of RDi on Java files of the RDi workspace. " //$NON-NLS-1$
        + "get_problems builds projects of the RDi workspace and returns their compile errors and warnings. " //$NON-NLS-1$
        + "import_projects imports existing projects, e.g. of a Git working copy, into the RDi workspace. " //$NON-NLS-1$
        + "run_tests runs a JUnit or JUnit Plug-in Test launch configuration of RDi and returns the results of the tests. " //$NON-NLS-1$
        + "workspace_info tells which RDi, workspace and projects this server belongs to. " //$NON-NLS-1$
        + "Other installed plug-ins may add further tools, which describe themselves."; //$NON-NLS-1$

    /** The shared instance */
    private static IAgentBridgePlugin plugin;

    private McpHttpServer server;
    private String serverUrl;
    private String serverError;

    @Override
    public void start(BundleContext aContext) throws Exception {
        super.start(aContext);
        plugin = this;
    }

    @Override
    public void stop(BundleContext aContext) throws Exception {
        stopServer();
        plugin = null;
        super.stop(aContext);
    }

    /**
     * Returns the shared instance.
     *
     * @return the shared instance
     */
    public static IAgentBridgePlugin getDefault() {
        return plugin;
    }

    /**
     * Returns the settings of the server: the port of the preferences, unless a
     * system property sets it, and the token.
     *
     * @return settings of the server
     */
    public ServerSettings getServerSettings() {
        return new ServerSettings(System.getProperties(), Preferences.getInstance().getPort());
    }

    /**
     * Starts the MCP server, when RDi starts, if the system property
     * 'iagentbridge.serverEnabled' or else the preferences say so and it is not
     * already running. Errors are written to the error log.
     */
    public synchronized void startServer() {

        try {
            if (!getServerSettings().isServerEnabled(Preferences.getInstance().isServerEnabled())) {
                return;
            }
        } catch (IllegalArgumentException e) {
            setServerError(e);
            return;
        }

        startServerNow();
    }

    /**
     * Starts the MCP server now, unless it is already running, whether or not
     * it is started when RDi starts. Errors are written to the error log.
     */
    public synchronized void startServerNow() {

        if (server != null) {
            return;
        }

        serverError = null;
        int port = -1;

        try {

            ServerSettings settings = getServerSettings();
            port = settings.getPort();
            String url = "http://127.0.0.1:" + port + McpHttpServer.PATH; //$NON-NLS-1$

            ToolCollection tools = createTools(url);
            McpDispatcher dispatcher = new McpDispatcher(SERVER_NAME, getBundle().getVersion().toString(), INSTRUCTIONS, tools.getTools(),
                this::logError);

            McpHttpServer httpServer = new McpHttpServer(dispatcher, settings.getToken());
            httpServer.start(port);
            server = httpServer;
            serverUrl = url;

            logInfo("iAgentBridge server listening on " + url + ", token: " + settings.getTokenSource() + ", tools: " //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                + join(tools.getNames()));

        } catch (BindException e) {
            setServerError(NLS.bind(Messages.Error_Port_in_use_A, Integer.toString(port)), e);
        } catch (IOException e) {
            setServerError(e);
        } catch (RuntimeException e) {
            setServerError(e);
        }
    }

    /**
     * Creates the tools of the server: the tools of iAgentBridge and the tools,
     * that other plug-ins contribute. The errors of the tools, that are left
     * out, are written to the error log.
     */
    private ToolCollection createTools(String aUrl) {

        ToolCollection tools = new ToolCollection();
        tools.add(new CleanUpTool(), PLUGIN_ID);
        tools.add(new GetProblemsTool(), PLUGIN_ID);
        tools.add(new ImportProjectsTool(), PLUGIN_ID);
        tools.add(new RunTestsTool(), PLUGIN_ID);
        tools.add(new WorkspaceInfoTool(aUrl), PLUGIN_ID);
        ContributedTools.addTo(tools);

        for (ToolCollection.ToolError error : tools.getErrors()) {
            logError(error.getMessage(), error.getCause());
        }

        return tools;
    }

    private static String join(List<String> aNames) {

        StringBuilder text = new StringBuilder();
        for (String name : aNames) {
            if (text.length() > 0) {
                text.append(", "); //$NON-NLS-1$
            }
            text.append(name);
        }

        return text.toString();
    }

    /**
     * Stops the MCP server.
     */
    public synchronized void stopServer() {

        if (server == null) {
            return;
        }

        server.stop();
        server = null;
        serverUrl = null;
    }

    /**
     * Restarts the MCP server, e.g. after its port or token has been changed.
     */
    public synchronized void restartServer() {
        stopServer();
        startServerNow();
    }

    /**
     * Returns the URL of the running server.
     *
     * @return URL or <code>null</code>, if the server is not running
     */
    public synchronized String getServerUrl() {
        return serverUrl;
    }

    /**
     * Returns the error, that prevented the last start of the server.
     *
     * @return error message or <code>null</code>
     */
    public synchronized String getServerError() {
        return serverError;
    }

    /**
     * Suggests the name of the server in an MCP client, e.g. 'rdi096' for RDi
     * 9.6.
     *
     * @return name of the server
     */
    public String suggestServerName() {

        Version version = getRdiVersion();
        if (version == null) {
            return ClientCommands.DEFAULT_SERVER_NAME;
        }

        return ClientCommands.suggestServerName(version.getMajor(), version.getMinor());
    }

    /**
     * Returns the default port of the server for this version of RDi, e.g.
     * 39096 for RDi 9.6.
     *
     * @return default port
     */
    public int getDefaultPort() {

        Version version = getRdiVersion();
        if (version == null) {
            return ServerSettings.DEFAULT_PORT;
        }

        return ServerSettings.defaultPort(version.getMajor(), version.getMinor());
    }

    /**
     * Returns the version of RDi: the version of the bundle, that defines the
     * product, e.g. 9.6.13 for RDi 9.6.0.13.
     *
     * @return version or <code>null</code>, if it is not known
     */
    private static Version getRdiVersion() {

        IProduct product = Platform.getProduct();
        if (product == null || product.getDefiningBundle() == null) {
            return null;
        }

        return product.getDefiningBundle().getVersion();
    }

    /**
     * Returns the version of the plug-in, as assigned to "Bundle-Version" in
     * "MANIFEST.MF".
     * 
     * @return Version of the plug-in.
     */
    public String getVersion() {
        String version = getBundle().getHeaders().get(Constants.BUNDLE_VERSION);
        if (version == null) {
            version = "0.0.0"; //$NON-NLS-1$
        }

        return version;
    }

    private void setServerError(Exception anException) {
        setServerError(anException.getMessage() == null ? anException.getClass().getName() : anException.getMessage(), anException);
    }

    private void setServerError(String aMessage, Exception anException) {
        serverError = aMessage;
        logError("Could not start the iAgentBridge server: " + serverError, anException); //$NON-NLS-1$
    }

    private void logInfo(String aMessage) {
        getLog().log(new Status(IStatus.INFO, PLUGIN_ID, aMessage));
    }

    private void logError(String aMessage, Throwable aThrowable) {
        getLog().log(new Status(IStatus.ERROR, PLUGIN_ID, aMessage, aThrowable));
    }
}
