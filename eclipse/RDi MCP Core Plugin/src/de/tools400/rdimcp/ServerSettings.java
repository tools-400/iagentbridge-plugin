/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.rdimcp;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.Properties;

/**
 * Settings of the MCP server: port and token. Both can be set with system
 * properties, e.g. in eclipse.ini after '-vmargs'. Without the property, the
 * token is read from (or created in) the file '.rdimcp/token' of the home
 * directory of the user, which all RDi instances share.
 * <p>
 * The class has no Eclipse dependencies, so that it can be tested outside of
 * RDi.
 */
public class ServerSettings {

    /** System property with the port of the server */
    public static final String PROPERTY_PORT = "rdimcp.port"; //$NON-NLS-1$

    /** System property with the token of the server */
    public static final String PROPERTY_TOKEN = "rdimcp.token"; //$NON-NLS-1$

    /** Default port */
    public static final int DEFAULT_PORT = 39096;

    private static final String TOKEN_DIRECTORY = ".rdimcp"; //$NON-NLS-1$
    private static final String TOKEN_FILE = "token"; //$NON-NLS-1$
    private static final int TOKEN_BYTES = 32;

    private final Properties properties;

    /**
     * Creates the settings.
     *
     * @param aProperties - system properties, with 'user.home'
     */
    public ServerSettings(Properties aProperties) {
        properties = aProperties;
    }

    /**
     * Returns the port of the server.
     *
     * @return port
     * @throws IllegalArgumentException if the property is not a valid port
     */
    public int getPort() {

        String value = properties.getProperty(PROPERTY_PORT);
        if (value == null || value.trim().isEmpty()) {
            return DEFAULT_PORT;
        }

        int port;
        try {
            port = Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid value of " + PROPERTY_PORT + ": " + value); //$NON-NLS-1$ //$NON-NLS-2$
        }

        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Invalid value of " + PROPERTY_PORT + ": " + value); //$NON-NLS-1$ //$NON-NLS-2$
        }

        return port;
    }

    /**
     * Returns the token of the server. Creates the token file, if there is
     * neither a property nor a token file.
     *
     * @return token
     * @throws IOException if the token file cannot be read or created
     */
    public String getToken() throws IOException {

        String value = properties.getProperty(PROPERTY_TOKEN);
        if (value != null && !value.trim().isEmpty()) {
            return value.trim();
        }

        File file = getTokenFile();
        String token = readToken(file);
        if (token != null) {
            return token;
        }

        file.getParentFile().mkdirs();
        try {
            Files.write(file.toPath(), createToken().getBytes(StandardCharsets.US_ASCII), StandardOpenOption.CREATE_NEW);
        } catch (FileAlreadyExistsException e) {
            // Created by another RDi instance in the meantime.
        }

        token = readToken(file);
        if (token == null) {
            throw new IOException("The token file is empty: " + file); //$NON-NLS-1$
        }

        return token;
    }

    /**
     * Describes, where the token comes from.
     *
     * @return system property or path of the token file
     */
    public String getTokenSource() {

        String value = properties.getProperty(PROPERTY_TOKEN);
        if (value != null && !value.trim().isEmpty()) {
            return "system property " + PROPERTY_TOKEN; //$NON-NLS-1$
        }

        return getTokenFile().getPath();
    }

    /**
     * Returns the token file in the home directory of the user.
     *
     * @return token file
     */
    public File getTokenFile() {
        return new File(new File(properties.getProperty("user.home"), TOKEN_DIRECTORY), TOKEN_FILE); //$NON-NLS-1$
    }

    private static String readToken(File aFile) throws IOException {

        if (!aFile.isFile()) {
            return null;
        }

        String token = new String(Files.readAllBytes(aFile.toPath()), StandardCharsets.US_ASCII).trim();
        if (token.isEmpty()) {
            return null;
        }

        return token;
    }

    private static String createToken() {

        byte[] bytes = new byte[TOKEN_BYTES];
        new SecureRandom().nextBytes(bytes);

        StringBuilder token = new StringBuilder();
        for (byte b : bytes) {
            token.append(String.format("%02x", Integer.valueOf(b & 0xff))); //$NON-NLS-1$
        }

        return token.toString();
    }
}
