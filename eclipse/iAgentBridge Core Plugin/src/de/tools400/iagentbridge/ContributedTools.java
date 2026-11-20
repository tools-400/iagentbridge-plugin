/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.Platform;

import de.tools400.iagentbridge.mcp.IMcpTool;

/**
 * Reads the tools, that other plug-ins contribute with the extension point
 * <code>de.tools400.iagentbridge.core.plugin.tools</code>.
 */
final class ContributedTools {

    /** ID of the extension point */
    static final String EXTENSION_POINT_ID = IAgentBridgePlugin.PLUGIN_ID + ".tools"; //$NON-NLS-1$

    private static final String ELEMENT_TOOL = "tool"; //$NON-NLS-1$
    private static final String ATTRIBUTE_CLASS = "class"; //$NON-NLS-1$

    private ContributedTools() {
    }

    /**
     * Creates the contributed tools and adds them to the given tools. A tool,
     * that cannot be created, is left out and an error is recorded.
     *
     * @param aTools - the tools of the server
     */
    static void addTo(ToolCollection aTools) {

        IConfigurationElement[] elements = Platform.getExtensionRegistry().getConfigurationElementsFor(EXTENSION_POINT_ID);
        for (IConfigurationElement element : elements) {

            if (!ELEMENT_TOOL.equals(element.getName())) {
                continue;
            }

            String contributor = element.getContributor().getName();
            String tool = "Tool " + element.getAttribute(ATTRIBUTE_CLASS) + " of plug-in " + contributor; //$NON-NLS-1$ //$NON-NLS-2$
            try {
                Object object = element.createExecutableExtension(ATTRIBUTE_CLASS);
                if (object instanceof IMcpTool) {
                    aTools.add((IMcpTool)object, contributor);
                } else {
                    aTools.addError(tool + " is left out, because it does not implement " + IMcpTool.class.getName() + ".", null); //$NON-NLS-1$ //$NON-NLS-2$
                }
            } catch (CoreException e) {
                aTools.addError(tool + " is left out, because it could not be created.", e); //$NON-NLS-1$
            } catch (RuntimeException e) {
                aTools.addError(tool + " is left out, because it could not be created.", e); //$NON-NLS-1$
            } catch (LinkageError e) {
                aTools.addError(tool + " is left out, because it could not be loaded.", e); //$NON-NLS-1$
            }
        }
    }
}
