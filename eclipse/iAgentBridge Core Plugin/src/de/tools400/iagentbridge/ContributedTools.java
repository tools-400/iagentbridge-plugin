/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.Platform;
import org.osgi.framework.Bundle;

import de.tools400.iagentbridge.mcp.IMcpTool;

/**
 * Reads the tools and the instructions for the AI agent, that other plug-ins
 * contribute with the extension point
 * <code>de.tools400.iagentbridge.core.plugin.tools</code>.
 */
final class ContributedTools {

    /** ID of the extension point */
    static final String EXTENSION_POINT_ID = IAgentBridgePlugin.PLUGIN_ID + ".tools"; //$NON-NLS-1$

    private static final String ELEMENT_TOOL = "tool"; //$NON-NLS-1$
    private static final String ATTRIBUTE_CLASS = "class"; //$NON-NLS-1$
    private static final String ELEMENT_INSTRUCTIONS = "instructions"; //$NON-NLS-1$
    private static final String ATTRIBUTE_FILE = "file"; //$NON-NLS-1$

    /** Byte order mark, that an editor may write at the start of a file */
    private static final char BYTE_ORDER_MARK = '\uFEFF';

    private ContributedTools() {
    }

    /**
     * Creates the contributed tools and adds them to the given tools, and then
     * the contributed instructions. A tool, that cannot be created, and
     * instructions, that cannot be read, are left out and an error is recorded.
     *
     * @param aTools - the tools of the server
     */
    static void addTo(ToolCollection aTools) {

        IConfigurationElement[] elements = Platform.getExtensionRegistry().getConfigurationElementsFor(EXTENSION_POINT_ID);
        addTools(elements, aTools);
        addInstructions(elements, aTools);
    }

    private static void addTools(IConfigurationElement[] anElements, ToolCollection aTools) {

        for (IConfigurationElement element : anElements) {

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

    /**
     * Adds the instructions of the plug-ins. They count only, if a tool of the
     * plug-in was added, so they are read after all tools.
     */
    private static void addInstructions(IConfigurationElement[] anElements, ToolCollection aTools) {

        for (IConfigurationElement element : anElements) {

            if (!ELEMENT_INSTRUCTIONS.equals(element.getName())) {
                continue;
            }

            String contributor = element.getContributor().getName();
            String file = element.getAttribute(ATTRIBUTE_FILE);
            String instructions = "The instructions file " + file + " of plug-in " + contributor; //$NON-NLS-1$ //$NON-NLS-2$
            try {
                String text = read(contributor, file);
                if (text == null) {
                    aTools.addError(instructions + " is left out, because it does not exist.", null); //$NON-NLS-1$
                } else {
                    aTools.addInstructions(text, contributor);
                }
            } catch (IOException e) {
                aTools.addError(instructions + " is left out, because it could not be read.", e); //$NON-NLS-1$
            } catch (RuntimeException e) {
                aTools.addError(instructions + " is left out, because it could not be read.", e); //$NON-NLS-1$
            }
        }
    }

    /**
     * Reads a file of a plug-in as UTF-8.
     *
     * @return the text, or <code>null</code>, if the plug-in or the file does
     *         not exist
     */
    private static String read(String aContributor, String aFile) throws IOException {

        Bundle bundle = Platform.getBundle(aContributor);
        URL url = bundle == null || aFile == null ? null : bundle.getEntry(aFile);
        if (url == null) {
            return null;
        }

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (InputStream in = url.openStream()) {
            byte[] buffer = new byte[8192];
            for (int count = in.read(buffer); count != -1; count = in.read(buffer)) {
                bytes.write(buffer, 0, count);
            }
        }

        String text = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        return !text.isEmpty() && text.charAt(0) == BYTE_ORDER_MARK ? text.substring(1) : text;
    }
}
