/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.tools;

import java.text.SimpleDateFormat;
import java.util.List;

import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import de.tools400.iagentbridge.mcp.IMcpTool;
import de.tools400.iagentbridge.mcp.McpToolResult;
import de.tools400.iagentbridge.ui.launches.HotCodeReplace;
import de.tools400.iagentbridge.ui.launches.LaunchInfo;
import de.tools400.iagentbridge.ui.launches.LaunchStarter;
import de.tools400.iagentbridge.ui.launches.Launches;
import de.tools400.iagentbridge.ui.launches.RunningLaunch;

/**
 * Tool 'list_launches': the Eclipse Applications and JUnit Plug-in Tests, that
 * run, and the launch configurations, that start_launch can start.
 */
public class ListLaunchesTool implements IMcpTool {

    private static final String DESCRIPTION = "Lists the Eclipse Applications and JUnit Plug-in Tests, that run in the RDi, in which the " //$NON-NLS-1$
        + "developer works - name, mode, port of iAgentBridge, whether it runs the UI tools, start, and in debug mode whether a thread is " //$NON-NLS-1$
        + "stopped in the debugger and the last result of hot code replace -, a start, that still waits for a dialog of RDi, and the " //$NON-NLS-1$
        + "launch configurations, that start_launch can start."; //$NON-NLS-1$

    @Override
    public String getName() {
        return "list_launches"; //$NON-NLS-1$
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    public JsonObject getInputSchema() {
        return new JsonParser().parse("{\"type\":\"object\",\"properties\":{}}").getAsJsonObject(); //$NON-NLS-1$
    }

    @Override
    public McpToolResult execute(JsonObject anArguments) throws Exception {

        if (AbstractUiTool.isTestInstance()) {
            return McpToolResult.error("list_launches works in the RDi, in which the developer works, not in a test instance."); //$NON-NLS-1$
        }
        HotCodeReplace.register();

        StringBuilder text = new StringBuilder();
        List<ILaunch> running = Launches.running();
        text.append(running.isEmpty() ? "No launch runs.\n" : "Running:\n"); //$NON-NLS-1$ //$NON-NLS-2$
        for (ILaunch launch : running) {
            RunningLaunch info = Launches.describe(launch);
            text.append("  \"").append(info.getName()).append("\" ").append(typeLabel(Launches.info(launch.getLaunchConfiguration()))) //$NON-NLS-1$ //$NON-NLS-2$
                .append(", ").append(info.getMode()); //$NON-NLS-1$
            if (info.getPort() != null) {
                text.append(", port ").append(info.getPort()); //$NON-NLS-1$
            }
            if (info.hasUiTools()) {
                text.append(", UI tools"); //$NON-NLS-1$
            }
            if (info.getStartTime() != 0) {
                text.append(", started ").append(new SimpleDateFormat("HH:mm:ss").format(Long.valueOf(info.getStartTime()))); //$NON-NLS-1$ //$NON-NLS-2$
            }
            if (info.isSuspended()) {
                text.append(", stopped in the debugger"); //$NON-NLS-1$
            }
            if (info.getHotCodeReplace() != null) {
                text.append(", hot code replace: ").append(info.getHotCodeReplace()); //$NON-NLS-1$
            }
            text.append('\n');
        }

        String waiting = LaunchStarter.getWaiting();
        if (waiting != null) {
            text.append("The start of \"").append(waiting).append("\" still waits - probably for an answer in a dialog of RDi.\n"); //$NON-NLS-1$ //$NON-NLS-2$
        }

        text.append("Launch configurations, that start_launch can start:\n"); //$NON-NLS-1$
        for (ILaunchConfiguration configuration : Launches.configurations()) {
            LaunchInfo info = Launches.info(configuration);
            text.append("  \"").append(info.getName()).append("\" ").append(typeLabel(info)); //$NON-NLS-1$ //$NON-NLS-2$
            if (info.getInstance().hasUiTools()) {
                text.append(", test instance with the UI tools, ").append(info.describePort()); //$NON-NLS-1$
            }
            text.append('\n');
        }

        return McpToolResult.success(text.toString());
    }

    private static String typeLabel(LaunchInfo anInfo) {
        return LaunchInfo.TYPE_ECLIPSE_APPLICATION.equals(anInfo.getType()) ? "Eclipse Application" : "JUnit Plug-in Test"; //$NON-NLS-1$ //$NON-NLS-2$
    }
}
