/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.tools;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * Reads the optional arguments of the UI tools, which ToolArguments of the core
 * plug-in does not cover: an optional string and a boolean.
 */
public final class UiArguments {

    private UiArguments() {
    }

    /**
     * Returns an optional string argument.
     *
     * @param anArguments - the arguments
     * @param aName - name of the argument
     * @return value or <code>null</code>, if the argument is missing or empty
     * @throws IllegalArgumentException if the argument is no string
     */
    public static String getOptionalString(JsonObject anArguments, String aName) {

        JsonElement element = anArguments.get(aName);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (!element.isJsonPrimitive() || !((JsonPrimitive)element).isString()) {
            throw new IllegalArgumentException("Argument '" + aName + "' must be a string."); //$NON-NLS-1$ //$NON-NLS-2$
        }

        String value = element.getAsString().trim();
        return value.isEmpty() ? null : value;
    }

    /**
     * Returns an optional boolean argument.
     *
     * @param anArguments - the arguments
     * @param aName - name of the argument
     * @param aDefault - value, if the argument is missing
     * @return value
     * @throws IllegalArgumentException if the argument is no boolean
     */
    public static boolean getBoolean(JsonObject anArguments, String aName, boolean aDefault) {

        JsonElement element = anArguments.get(aName);
        if (element == null || element.isJsonNull()) {
            return aDefault;
        }
        if (!element.isJsonPrimitive() || !((JsonPrimitive)element).isBoolean()) {
            throw new IllegalArgumentException("Argument '" + aName + "' must be true or false."); //$NON-NLS-1$ //$NON-NLS-2$
        }

        return element.getAsBoolean();
    }

    /**
     * Checks, that at most one of the arguments 'shell' and 'ref' is given.
     *
     * @param aShell - value of 'shell' or <code>null</code>
     * @param aRef - value of 'ref' or <code>null</code>
     * @throws IllegalArgumentException if both are given
     */
    public static void checkShellOrRef(String aShell, String aRef) {
        if (aShell != null && aRef != null) {
            throw new IllegalArgumentException("Use either the argument 'shell' or the argument 'ref', not both."); //$NON-NLS-1$
        }
    }
}
