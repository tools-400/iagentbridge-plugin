/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import de.tools400.iagentbridge.mcp.IMcpTool;

/**
 * Collects the tools of the server: the tools of iAgentBridge and the tools,
 * that other plug-ins contribute. A tool, that cannot describe itself, whose
 * name is invalid or already taken, is left out and an error is recorded, so
 * that a faulty tool does not break the server.
 * <p>
 * The class has no Eclipse dependencies, so that it can be tested outside of
 * RDi.
 */
public final class ToolCollection {

    /** Name of a tool: letters, digits, '_' and '-', at most 64 characters */
    private static final Pattern TOOL_NAME = Pattern.compile("[A-Za-z0-9_-]{1,64}"); //$NON-NLS-1$

    private final Map<String, IMcpTool> tools;
    private final Map<String, String> contributors;
    private final List<ToolError> errors;

    public ToolCollection() {
        tools = new LinkedHashMap<String, IMcpTool>();
        contributors = new LinkedHashMap<String, String>();
        errors = new ArrayList<ToolError>();
    }

    /**
     * Adds a tool, unless it cannot describe itself or its name is invalid or
     * already taken. Then the tool is left out and an error is recorded.
     *
     * @param aTool - the tool
     * @param aContributor - the ID of the plug-in, that contributes the tool,
     *        for the error messages
     * @return <code>true</code>, if the tool was added
     */
    public boolean add(IMcpTool aTool, String aContributor) {

        String name;
        try {
            name = aTool.getName();
            checkName(name);
            checkDescription(aTool.getDescription());
            checkInputSchema(aTool.getInputSchema());
        } catch (IllegalArgumentException e) {
            addError("A tool of plug-in " + aContributor + " is left out: " + e.getMessage(), null); //$NON-NLS-1$ //$NON-NLS-2$
            return false;
        } catch (RuntimeException e) {
            addError(failedToDescribe(aTool, aContributor), e);
            return false;
        } catch (LinkageError e) {
            addError(failedToDescribe(aTool, aContributor), e);
            return false;
        }

        String owner = contributors.get(name);
        if (owner != null) {
            addError("Tool '" + name + "' of plug-in " + aContributor + " is left out, because plug-in " + owner //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                + " already contributes a tool of that name.", null); //$NON-NLS-1$
            return false;
        }

        tools.put(name, aTool);
        contributors.put(name, aContributor);

        return true;
    }

    /**
     * Records an error, e.g. of a contributed tool, that could not be created.
     *
     * @param aMessage - message for the error log
     * @param aCause - the cause or <code>null</code>
     */
    public void addError(String aMessage, Throwable aCause) {
        errors.add(new ToolError(aMessage, aCause));
    }

    /**
     * Returns the tools in the order, in which they were added.
     *
     * @return tools
     */
    public List<IMcpTool> getTools() {
        return new ArrayList<IMcpTool>(tools.values());
    }

    /**
     * Returns the names of the tools in the order, in which they were added.
     *
     * @return names of the tools
     */
    public List<String> getNames() {
        return new ArrayList<String>(tools.keySet());
    }

    /**
     * Returns the errors of the tools, that were left out.
     *
     * @return errors, empty if all tools were added
     */
    public List<ToolError> getErrors() {
        return new ArrayList<ToolError>(errors);
    }

    private static String failedToDescribe(IMcpTool aTool, String aContributor) {
        return "Tool " + aTool.getClass().getName() + " of plug-in " + aContributor + " is left out, because it failed to describe itself."; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    private static void checkName(String aName) {

        if (aName == null) {
            throw new IllegalArgumentException("the tool has no name."); //$NON-NLS-1$
        }

        if (!TOOL_NAME.matcher(aName).matches()) {
            throw new IllegalArgumentException("the name '" + aName //$NON-NLS-1$
                + "' is invalid. A name consists of 1 to 64 letters, digits, '_' and '-'."); //$NON-NLS-1$
        }
    }

    private static void checkDescription(String aDescription) {

        if (aDescription == null || aDescription.trim().length() == 0) {
            throw new IllegalArgumentException("the tool has no description."); //$NON-NLS-1$
        }
    }

    private static void checkInputSchema(JsonObject anInputSchema) {

        if (anInputSchema == null) {
            throw new IllegalArgumentException("the tool has no input schema."); //$NON-NLS-1$
        }

        JsonElement type = anInputSchema.get("type"); //$NON-NLS-1$
        if (type == null || !type.isJsonPrimitive() || !"object".equals(type.getAsString())) { //$NON-NLS-1$
            throw new IllegalArgumentException("the input schema of the tool is not of type 'object'."); //$NON-NLS-1$
        }
    }

    /**
     * An error of a tool, that was left out.
     */
    public static final class ToolError {

        private final String message;
        private final Throwable cause;

        private ToolError(String aMessage, Throwable aCause) {
            message = aMessage;
            cause = aCause;
        }

        public String getMessage() {
            return message;
        }

        /**
         * Returns the cause of the error.
         *
         * @return the cause or <code>null</code>
         */
        public Throwable getCause() {
            return cause;
        }
    }
}
