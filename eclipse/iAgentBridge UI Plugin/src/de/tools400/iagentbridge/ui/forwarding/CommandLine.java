/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.forwarding;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the command line of a process, as the debug framework renders it: the
 * arguments separated by blanks, an argument with blanks in double quotes and a
 * double quote in it escaped with a backslash.
 */
public final class CommandLine {

    private static final String SYSTEM_PROPERTY_PREFIX = "-D"; //$NON-NLS-1$

    private CommandLine() {
    }

    /**
     * Splits a command line into its arguments.
     *
     * @param aCommandLine - the command line
     * @return arguments without the quotes
     */
    public static List<String> split(String aCommandLine) {

        List<String> arguments = new ArrayList<String>();
        StringBuilder argument = new StringBuilder();
        boolean inArgument = false;
        boolean quoted = false;

        for (int i = 0; i < aCommandLine.length(); i++) {
            char c = aCommandLine.charAt(i);
            if (c == '\\' && i + 1 < aCommandLine.length() && aCommandLine.charAt(i + 1) == '"') {
                argument.append('"');
                inArgument = true;
                i++;
            } else if (c == '"') {
                quoted = !quoted;
                inArgument = true;
            } else if (Character.isWhitespace(c) && !quoted) {
                if (inArgument) {
                    arguments.add(argument.toString());
                    argument.setLength(0);
                    inArgument = false;
                }
            } else {
                argument.append(c);
                inArgument = true;
            }
        }
        if (inArgument) {
            arguments.add(argument.toString());
        }

        return arguments;
    }

    /**
     * Returns the system properties of a command line, the arguments
     * '-Dname=value'. An argument '-Dname' has the value "".
     *
     * @param aCommandLine - the command line
     * @return name and value of each system property, the last one wins
     */
    public static Map<String, String> systemProperties(String aCommandLine) {

        Map<String, String> properties = new LinkedHashMap<String, String>();
        for (String argument : split(aCommandLine)) {
            if (argument.startsWith(SYSTEM_PROPERTY_PREFIX) && argument.length() > SYSTEM_PROPERTY_PREFIX.length()) {
                String property = argument.substring(SYSTEM_PROPERTY_PREFIX.length());
                int equals = property.indexOf('=');
                if (equals < 0) {
                    properties.put(property, ""); //$NON-NLS-1$
                } else {
                    properties.put(property.substring(0, equals), property.substring(equals + 1));
                }
            }
        }

        return properties;
    }
}
