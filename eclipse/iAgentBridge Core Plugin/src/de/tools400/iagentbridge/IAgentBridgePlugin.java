/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.runtime.IProduct;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Version;

import de.tools400.iagentbridge.mcp.IMcpTool;
import de.tools400.iagentbridge.mcp.McpDispatcher;
import de.tools400.iagentbridge.mcp.McpHttpServer;
import de.tools400.iagentbridge.preferences.Preferences;
import de.tools400.iagentbridge.tools.CleanUpTool;
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
        + "workspace_info tells which RDi, workspace and projects this server belongs to."; //$NON-NLS-1$

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
     * Starts the MCP server, if it is enabled in the preferences and not
     * already running. Errors are written to the error log.
     */
    public synchronized void startServer() {

        if (server != null || !Preferences.getInstance().isServerEnabled()) {
            return;
        }

        serverError = null;

        try {

            ServerSettings settings = getServerSettings();
            int port = settings.getPort();
            String url = "http://127.0.0.1:" + port + McpHttpServer.PATH; //$NON-NLS-1$

            List<IMcpTool> tools = Arrays.<IMcpTool> asList(new CleanUpTool(), new WorkspaceInfoTool(url));
            McpDispatcher dispatcher = new McpDispatcher(SERVER_NAME, getBundle().getVersion().toString(), INSTRUCTIONS, tools);

            McpHttpServer httpServer = new McpHttpServer(dispatcher, settings.getToken());
            httpServer.start(port);
            server = httpServer;
            serverUrl = url;

            logInfo("iAgentBridge server listening on " + url + ", token: " + settings.getTokenSource()); //$NON-NLS-1$ //$NON-NLS-2$

        } catch (IOException e) {
            setServerError(e);
        } catch (RuntimeException e) {
            setServerError(e);
        }
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
     * Restarts the MCP server, e.g. after the preferences have been changed.
     */
    public synchronized void restartServer() {
        stopServer();
        serverError = null;
        startServer();
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

        IProduct product = Platform.getProduct();
        if (product == null || product.getDefiningBundle() == null) {
            return ClientCommands.DEFAULT_SERVER_NAME;
        }

        Version version = product.getDefiningBundle().getVersion();

        return ClientCommands.suggestServerName(version.getMajor(), version.getMinor());
    }

    private void setServerError(Exception anException) {
        serverError = anException.getMessage() == null ? anException.getClass().getName() : anException.getMessage();
        logError("Could not start the iAgentBridge server: " + serverError, anException); //$NON-NLS-1$
    }

    private void logInfo(String aMessage) {
        getLog().log(new Status(IStatus.INFO, PLUGIN_ID, aMessage));
    }

    private void logError(String aMessage, Throwable aThrowable) {
        getLog().log(new Status(IStatus.ERROR, PLUGIN_ID, aMessage, aThrowable));
    }
}
