/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.tools;

import java.util.Locale;

import com.google.gson.JsonObject;

import de.tools400.iagentbridge.mcp.ToolArguments;

/**
 * Arguments and texts, that start_launch and stop_launch share.
 */
final class LaunchToolSupport {

    /** Seconds, that a launch tool waits at most */
    static final int MAX_TIMEOUT_SECONDS = 600;

    private LaunchToolSupport() {
    }

    /**
     * Returns the argument 'timeoutSeconds'.
     *
     * @param anArguments - arguments of the tool
     * @param aDefault - value, if the argument is missing
     * @return 1 to 600
     * @throws IllegalArgumentException if the argument is no integer or out of
     *         range
     */
    static int timeoutSeconds(JsonObject anArguments, int aDefault) {
        int seconds = ToolArguments.getInt(anArguments, "timeoutSeconds", aDefault, 1); //$NON-NLS-1$
        if (seconds > MAX_TIMEOUT_SECONDS) {
            throw new IllegalArgumentException("Argument 'timeoutSeconds' must be at most " + MAX_TIMEOUT_SECONDS + "."); //$NON-NLS-1$ //$NON-NLS-2$
        }
        return seconds;
    }

    /**
     * Returns the seconds since a start, e.g. '2.5'.
     *
     * @param aStart - start in milliseconds since 1970
     * @return seconds with one decimal
     */
    static String secondsSince(long aStart) {
        return String.format(Locale.ROOT, "%.1f", Double.valueOf((System.currentTimeMillis() - aStart) / 1000.0)); //$NON-NLS-1$
    }

    /**
     * Returns the name of a launch configuration in quotes.
     *
     * @param aName - the name
     * @return e.g. '"iAgentBridge (de)"'
     */
    static String quote(String aName) {
        return "\"" + aName + "\""; //$NON-NLS-1$ //$NON-NLS-2$
    }

    /**
     * Ends a text with a period, unless it ends with a punctuation mark.
     *
     * @param aText - the text, e.g. a reason
     * @return the text with a period
     */
    static String sentence(String aText) {
        String text = aText.trim();
        return text.endsWith(".") || text.endsWith("?") || text.endsWith("!") ? text : text + "."; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
    }
}
