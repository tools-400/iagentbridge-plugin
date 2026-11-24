/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.Properties;

/**
 * Settings of the MCP server: port, token and whether it is started, when RDi
 * starts.
 * <p>
 * The port is the one of the preferences, unless the system property
 * 'iagentbridge.port' sets it, e.g. in the VM arguments of a launch
 * configuration. The token is read from (or created in) the file
 * '.iagentbridge/token' of the home directory of the user, which all RDi
 * instances share, unless the system property 'iagentbridge.token' sets it. The
 * system property 'iagentbridge.serverEnabled' overrides the preference, that
 * tells whether the server is started, when RDi starts.
 * <p>
 * The class has no Eclipse dependencies, so that it can be tested outside of
 * RDi.
 */
public class ServerSettings {

    /** System property with the port of the server */
    public static final String PROPERTY_PORT = "iagentbridge.port"; //$NON-NLS-1$

    /** System property with the token of the server */
    public static final String PROPERTY_TOKEN = "iagentbridge.token"; //$NON-NLS-1$

    /**
     * System property, that tells whether the server is started when RDi
     * starts, e.g. 'false' in a test instance
     */
    public static final String PROPERTY_SERVER_ENABLED = "iagentbridge.serverEnabled"; //$NON-NLS-1$

    /** Default port, if the version of RDi is not known */
    public static final int DEFAULT_PORT = 39096;

    /** Base of the default port, to which the version of RDi is added */
    private static final int DEFAULT_PORT_BASE = 39000;

    /** Lowest valid port */
    public static final int MIN_PORT = 1;

    /** Highest valid port */
    public static final int MAX_PORT = 65535;

    private static final String TOKEN_DIRECTORY = ".iagentbridge"; //$NON-NLS-1$
    private static final String TOKEN_FILE = "token"; //$NON-NLS-1$
    private static final int TOKEN_BYTES = 32;

    private final Properties properties;
    private final int preferencePort;

    /**
     * Creates the settings.
     *
     * @param aProperties - system properties, with 'user.home'
     * @param aPreferencePort - port of the preferences
     */
    public ServerSettings(Properties aProperties, int aPreferencePort) {
        properties = aProperties;
        preferencePort = aPreferencePort;
    }

    /**
     * Returns the default port for a version of RDi, e.g. 39096 for RDi 9.6 and
     * 39099 for RDi 9.9, so that the RDi versions of a user do not compete for
     * the same port. The port matches the suggested name of the server, e.g.
     * 'rdi096'.
     *
     * @param aMajor - major version of RDi
     * @param aMinor - minor version of RDi
     * @return default port
     */
    public static int defaultPort(int aMajor, int aMinor) {

        if (aMajor < 1 || aMajor > 99 || aMinor < 0 || aMinor > 9) {
            return DEFAULT_PORT;
        }

        return DEFAULT_PORT_BASE + aMajor * 10 + aMinor;
    }

    /**
     * Returns the port of the server: the one of the system property or else
     * the one of the preferences.
     *
     * @return port
     * @throws IllegalArgumentException if the port is not valid
     */
    public int getPort() {

        if (!isPortSetBySystemProperty()) {
            checkPort(preferencePort, String.valueOf(preferencePort));
            return preferencePort;
        }

        String value = properties.getProperty(PROPERTY_PORT).trim();
        int port;
        try {
            port = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid value of " + PROPERTY_PORT + ": " + value); //$NON-NLS-1$ //$NON-NLS-2$
        }

        checkPort(port, value);

        return port;
    }

    /**
     * Returns whether the system property sets the port.
     *
     * @return <code>true</code>, if the port is set by the system property
     */
    public boolean isPortSetBySystemProperty() {
        return !isEmpty(properties.getProperty(PROPERTY_PORT));
    }

    /**
     * Returns whether the server is started, when RDi starts: the value of the
     * system property or else the one of the preferences.
     *
     * @param aPreferenceEnabled - value of the preferences
     * @return <code>true</code>, if the server is started
     * @throws IllegalArgumentException if the system property is neither 'true'
     *         nor 'false'
     */
    public boolean isServerEnabled(boolean aPreferenceEnabled) {

        if (!isServerEnabledSetBySystemProperty()) {
            return aPreferenceEnabled;
        }

        String value = properties.getProperty(PROPERTY_SERVER_ENABLED).trim();
        if (Boolean.TRUE.toString().equalsIgnoreCase(value)) {
            return true;
        }
        if (Boolean.FALSE.toString().equalsIgnoreCase(value)) {
            return false;
        }

        throw new IllegalArgumentException("Invalid value of " + PROPERTY_SERVER_ENABLED + ": " + value); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /**
     * Returns whether the system property tells, whether the server is started,
     * when RDi starts.
     *
     * @return <code>true</code>, if the system property is set
     */
    public boolean isServerEnabledSetBySystemProperty() {
        return !isEmpty(properties.getProperty(PROPERTY_SERVER_ENABLED));
    }

    /**
     * Returns the token of the server. Creates the token file, if there is
     * neither a property nor a token file.
     *
     * @return token
     * @throws IOException if the token file cannot be read or created
     */
    public String getToken() throws IOException {

        if (isTokenSetBySystemProperty()) {
            return properties.getProperty(PROPERTY_TOKEN).trim();
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
     * Replaces the token of the token file with a new random token. Clients
     * registered with the old token must be registered again.
     *
     * @return new token
     * @throws IOException if the token file cannot be written
     * @throws IllegalStateException if the system property sets the token
     */
    public String renewToken() throws IOException {

        if (isTokenSetBySystemProperty()) {
            throw new IllegalStateException("The token is set by the system property " + PROPERTY_TOKEN + "."); //$NON-NLS-1$ //$NON-NLS-2$
        }

        File file = getTokenFile();
        file.getParentFile().mkdirs();

        String token = createToken();
        Files.write(file.toPath(), token.getBytes(StandardCharsets.US_ASCII));

        return token;
    }

    /**
     * Returns whether the system property sets the token.
     *
     * @return <code>true</code>, if the token is set by the system property
     */
    public boolean isTokenSetBySystemProperty() {
        return !isEmpty(properties.getProperty(PROPERTY_TOKEN));
    }

    /**
     * Describes, where the token comes from.
     *
     * @return system property or path of the token file
     */
    public String getTokenSource() {

        if (isTokenSetBySystemProperty()) {
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

    private static void checkPort(int aPort, String aValue) {
        if (aPort < MIN_PORT || aPort > MAX_PORT) {
            throw new IllegalArgumentException("Invalid port: " + aValue); //$NON-NLS-1$
        }
    }

    private static boolean isEmpty(String aValue) {
        return aValue == null || aValue.trim().isEmpty();
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
