/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.forwarding;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Properties;

/**
 * Reads the token file, that all RDi instances of the user share:
 * '.iagentbridge/token' in the home directory. The path is the one of the core
 * plug-in (ServerSettings.getTokenFile()), whose package is not exported.
 */
public final class TokenFile {

    private static final String TOKEN_DIRECTORY = ".iagentbridge"; //$NON-NLS-1$
    private static final String TOKEN_FILE = "token"; //$NON-NLS-1$

    private TokenFile() {
    }

    /**
     * Returns the token file.
     *
     * @param aProperties - system properties with 'user.home'
     * @return token file
     */
    public static File get(Properties aProperties) {
        return new File(new File(aProperties.getProperty("user.home"), TOKEN_DIRECTORY), TOKEN_FILE); //$NON-NLS-1$
    }

    /**
     * Reads the token.
     *
     * @param aProperties - system properties with 'user.home'
     * @return token
     * @throws IOException if the file is missing, empty or cannot be read
     */
    public static String read(Properties aProperties) throws IOException {

        File file = get(aProperties);
        if (!file.isFile()) {
            throw new IOException("The token file is missing: " + file); //$NON-NLS-1$
        }

        String token = new String(Files.readAllBytes(file.toPath()), StandardCharsets.US_ASCII).trim();
        if (token.isEmpty()) {
            throw new IOException("The token file is empty: " + file); //$NON-NLS-1$
        }

        return token;
    }
}
