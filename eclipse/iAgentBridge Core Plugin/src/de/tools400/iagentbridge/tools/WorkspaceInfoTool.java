/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.tools;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProduct;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.core.JavaCore;
import org.osgi.framework.Bundle;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import de.tools400.iagentbridge.mcp.IMcpTool;
import de.tools400.iagentbridge.mcp.McpToolResult;

/**
 * Describes the RDi instance, the server belongs to: product, Eclipse, Java,
 * workspace and the open Java projects with their clean-up profiles.
 */
public class WorkspaceInfoTool implements IMcpTool {

    private static final String DESCRIPTION = "Describes the RDi (Eclipse) instance, this MCP server runs in: product and version, " //$NON-NLS-1$
        + "Eclipse platform, Java, workspace location, server URL and the open Java projects with their locations and the " //$NON-NLS-1$
        + "clean-up profile, that applies to them. Use it to check, which RDi and workspace you are talking to."; //$NON-NLS-1$

    private static final String UNKNOWN = "unknown"; //$NON-NLS-1$

    private final String serverUrl;

    /**
     * Creates the tool.
     *
     * @param aServerUrl - URL of the MCP server
     */
    public WorkspaceInfoTool(String aServerUrl) {
        serverUrl = aServerUrl;
    }

    @Override
    public String getName() {
        return "workspace_info"; //$NON-NLS-1$
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    public JsonObject getInputSchema() {
        return JsonTexts.parse("{\"type\":\"object\",\"properties\":{}}").getAsJsonObject(); //$NON-NLS-1$
    }

    @Override
    public McpToolResult execute(JsonObject anArguments) throws CoreException {

        IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();

        JsonObject info = new JsonObject();
        IProduct product = Platform.getProduct();
        info.addProperty("product", product == null ? UNKNOWN : product.getName()); //$NON-NLS-1$
        info.addProperty("productVersion", product == null ? UNKNOWN : getVersion(product.getDefiningBundle())); //$NON-NLS-1$
        info.addProperty("eclipsePlatform", getVersion(Platform.getBundle("org.eclipse.platform"))); //$NON-NLS-1$ //$NON-NLS-2$
        info.addProperty("java", System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
        info.addProperty("workspace", root.getLocation().toOSString()); //$NON-NLS-1$
        info.addProperty("serverUrl", serverUrl); //$NON-NLS-1$

        JsonArray projects = new JsonArray();
        for (IProject project : root.getProjects()) {
            if (project.isOpen() && project.hasNature(JavaCore.NATURE_ID)) {
                JsonObject projectInfo = new JsonObject();
                projectInfo.addProperty("name", project.getName()); //$NON-NLS-1$
                projectInfo.addProperty("location", project.getLocation() == null ? UNKNOWN : project.getLocation().toOSString()); //$NON-NLS-1$
                projectInfo.addProperty("cleanUpProfile", CleanUpProfiles.describe(project)); //$NON-NLS-1$
                projects.add(projectInfo);
            }
        }
        info.add("javaProjects", projects); //$NON-NLS-1$

        return McpToolResult.success(JsonTexts.toText(info));
    }

    private static String getVersion(Bundle aBundle) {

        if (aBundle == null) {
            return UNKNOWN;
        }

        return aBundle.getVersion().toString();
    }
}
