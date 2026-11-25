/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.tools;

import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import de.tools400.iagentbridge.ui.forwarding.ForwardingClient;

/**
 * Reads the arguments of the UI tools, which ToolArguments of the core plug-in
 * does not cover: optional strings, booleans and the reference of a widget.
 */
public final class UiArguments {

    /** Argument of the actions: reference of the widget */
    public static final String ARGUMENT_REF = "ref"; //$NON-NLS-1$

    /** Argument of the actions: return the snapshot of the active shell */
    public static final String ARGUMENT_SNAPSHOT = "snapshot"; //$NON-NLS-1$

    /**
     * Argument, that the forwarding RDi adds: its preference, whether the
     * dialogs of actions are put behind the other windows. Not in the schemas.
     */
    public static final String ARGUMENT_KEEP_DIALOGS_BEHIND = "keepDialogsBehind"; //$NON-NLS-1$

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
     * Returns an optional string argument as it is, with its blanks, e.g. a
     * text to type.
     *
     * @param anArguments - the arguments
     * @param aName - name of the argument
     * @return value or <code>null</code>, if the argument is missing
     * @throws IllegalArgumentException if the argument is no string
     */
    public static String getOptionalRawString(JsonObject anArguments, String aName) {

        JsonElement element = anArguments.get(aName);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (!element.isJsonPrimitive() || !((JsonPrimitive)element).isString()) {
            throw new IllegalArgumentException("Argument '" + aName + "' must be a string."); //$NON-NLS-1$ //$NON-NLS-2$
        }

        return element.getAsString();
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
     * Returns a boolean argument, that must be there.
     *
     * @param anArguments - the arguments
     * @param aName - name of the argument
     * @return value
     * @throws IllegalArgumentException if the argument is missing or no boolean
     */
    public static boolean getRequiredBoolean(JsonObject anArguments, String aName) {

        JsonElement element = anArguments.get(aName);
        if (element == null || element.isJsonNull()) {
            throw new IllegalArgumentException("Argument '" + aName + "' is missing."); //$NON-NLS-1$ //$NON-NLS-2$
        }

        return getBoolean(anArguments, aName, false);
    }

    /**
     * Returns the reference of the widget of an action, without blanks around
     * it.
     *
     * @param anArguments - the arguments
     * @return reference, e.g. 'w17'
     * @throws IllegalArgumentException if the argument 'ref' is missing, empty
     *         or no string
     */
    public static String getRef(JsonObject anArguments) {

        String ref = getOptionalString(anArguments, ARGUMENT_REF);
        if (ref == null) {
            throw new IllegalArgumentException("Argument 'ref' is missing."); //$NON-NLS-1$
        }

        return ref;
    }

    /**
     * Returns the arguments of an action for the journal: without the
     * arguments, that do not describe the action ('forwarded', 'snapshot',
     * 'keepDialogsBehind').
     *
     * @param anArguments - the arguments
     * @return arguments as JSON
     */
    public static String forJournal(JsonObject anArguments) {

        JsonObject arguments = new JsonObject();
        for (Map.Entry<String, JsonElement> argument : anArguments.entrySet()) {
            if (!ForwardingClient.ARGUMENT_FORWARDED.equals(argument.getKey()) && !ARGUMENT_SNAPSHOT.equals(argument.getKey())
                && !ARGUMENT_KEEP_DIALOGS_BEHIND.equals(argument.getKey())) {
                arguments.add(argument.getKey(), argument.getValue());
            }
        }

        return arguments.toString();
    }

    /**
     * Returns the arguments for the test instance: a copy with the preference
     * of the forwarding RDi, whether the dialogs of actions are put behind the
     * other windows, unless the arguments have it already.
     *
     * @param anArguments - the arguments of the call
     * @param aKeepDialogsBehind - the preference of the forwarding RDi
     * @return the arguments to forward
     */
    public static JsonObject withKeepDialogsBehind(JsonObject anArguments, boolean aKeepDialogsBehind) {

        JsonObject arguments = new JsonObject();
        for (Map.Entry<String, JsonElement> argument : anArguments.entrySet()) {
            arguments.add(argument.getKey(), argument.getValue());
        }
        if (!arguments.has(ARGUMENT_KEEP_DIALOGS_BEHIND)) {
            arguments.addProperty(ARGUMENT_KEEP_DIALOGS_BEHIND, Boolean.valueOf(aKeepDialogsBehind));
        }

        return arguments;
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
