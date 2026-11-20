/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.rdimcp;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Plugin;
import org.eclipse.core.runtime.Status;
import org.osgi.framework.BundleContext;

import de.tools400.rdimcp.mcp.IMcpTool;
import de.tools400.rdimcp.mcp.McpDispatcher;
import de.tools400.rdimcp.mcp.McpHttpServer;
import de.tools400.rdimcp.tools.CleanUpTool;
import de.tools400.rdimcp.tools.WorkspaceInfoTool;

/**
 * The activator class controls the plug-in life cycle and the MCP server.
 */
public class RdiMcpPlugin extends Plugin {

    /** The plug-in ID */
    public static final String PLUGIN_ID = "de.tools400.rdimcp.core.plugin"; //$NON-NLS-1$

    /** Name of the server returned to the MCP client */
    public static final String SERVER_NAME = "rdi-mcp-server"; //$NON-NLS-1$

    private static final String INSTRUCTIONS = "Tools of a running IBM Rational Developer for i (RDi, Eclipse) instance. " //$NON-NLS-1$
        + "clean_up runs 'Source > Clean Up' with the clean-up profile of RDi on Java files of the RDi workspace. " //$NON-NLS-1$
        + "workspace_info tells which RDi, workspace and projects this server belongs to."; //$NON-NLS-1$

    /** The shared instance */
    private static RdiMcpPlugin plugin;

    private McpHttpServer server;

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
    public static RdiMcpPlugin getDefault() {
        return plugin;
    }

    /**
     * Starts the MCP server, unless it is already running. Errors are written
     * to the error log.
     */
    public synchronized void startServer() {

        if (server != null) {
            return;
        }

        try {

            ServerSettings settings = new ServerSettings(System.getProperties());
            int port = settings.getPort();
            String url = "http://127.0.0.1:" + port + McpHttpServer.PATH; //$NON-NLS-1$

            List<IMcpTool> tools = Arrays.<IMcpTool> asList(new CleanUpTool(), new WorkspaceInfoTool(url));
            McpDispatcher dispatcher = new McpDispatcher(SERVER_NAME, getBundle().getVersion().toString(), INSTRUCTIONS, tools);

            McpHttpServer httpServer = new McpHttpServer(dispatcher, settings.getToken());
            httpServer.start(port);
            server = httpServer;

            logInfo("RDi MCP server listening on " + url + ", token: " + settings.getTokenSource()); //$NON-NLS-1$ //$NON-NLS-2$

        } catch (IOException e) {
            logError("Could not start the RDi MCP server: " + e.getMessage(), e); //$NON-NLS-1$
        } catch (RuntimeException e) {
            logError("Could not start the RDi MCP server: " + e.getMessage(), e); //$NON-NLS-1$
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
    }

    private void logInfo(String aMessage) {
        getLog().log(new Status(IStatus.INFO, PLUGIN_ID, aMessage));
    }

    private void logError(String aMessage, Throwable aThrowable) {
        getLog().log(new Status(IStatus.ERROR, PLUGIN_ID, aMessage, aThrowable));
    }
}
