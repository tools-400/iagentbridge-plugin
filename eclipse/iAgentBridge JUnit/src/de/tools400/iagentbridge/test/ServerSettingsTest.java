/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Properties;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import de.tools400.iagentbridge.ServerSettings;

public class ServerSettingsTest {

    private static final int PREFERENCE_PORT = 39196;

    private File home;
    private Properties properties;

    @Before
    public void setUp() throws IOException {
        home = Files.createTempDirectory("iagentbridge-test").toFile();
        properties = new Properties();
        properties.setProperty("user.home", home.getPath());
    }

    @After
    public void tearDown() {
        delete(home);
    }

    @Test
    public void testGetPort_preference() {

        ServerSettings settings = new ServerSettings(properties, PREFERENCE_PORT);

        assertEquals(PREFERENCE_PORT, settings.getPort());
        assertFalse(settings.isPortSetBySystemProperty());
    }

    @Test
    public void testGetPort_property() {

        properties.setProperty(ServerSettings.PROPERTY_PORT, " 39098 ");
        ServerSettings settings = new ServerSettings(properties, PREFERENCE_PORT);

        assertEquals(39098, settings.getPort());
        assertTrue(settings.isPortSetBySystemProperty());
    }

    @Test
    public void testGetPort_emptyProperty() {
        properties.setProperty(ServerSettings.PROPERTY_PORT, " ");
        assertEquals(PREFERENCE_PORT, new ServerSettings(properties, PREFERENCE_PORT).getPort());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetPort_notANumber() {
        properties.setProperty(ServerSettings.PROPERTY_PORT, "abc");
        new ServerSettings(properties, PREFERENCE_PORT).getPort();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetPort_propertyOutOfRange() {
        properties.setProperty(ServerSettings.PROPERTY_PORT, "70000");
        new ServerSettings(properties, PREFERENCE_PORT).getPort();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetPort_preferenceOutOfRange() {
        new ServerSettings(properties, 0).getPort();
    }

    @Test
    public void testGetToken_property() throws IOException {

        properties.setProperty(ServerSettings.PROPERTY_TOKEN, " abc ");
        ServerSettings settings = new ServerSettings(properties, PREFERENCE_PORT);

        assertEquals("abc", settings.getToken());
        assertTrue(settings.isTokenSetBySystemProperty());
        assertEquals("system property iagentbridge.token", settings.getTokenSource());
        assertFalse(settings.getTokenFile().exists());
    }

    @Test
    public void testGetToken_created() throws IOException {

        ServerSettings settings = new ServerSettings(properties, PREFERENCE_PORT);
        String token = settings.getToken();

        assertTrue(token, token.matches("[0-9a-f]{64}"));
        assertFalse(settings.isTokenSetBySystemProperty());
        assertEquals(new File(new File(home, ".iagentbridge"), "token"), settings.getTokenFile());
        assertEquals(settings.getTokenFile().getPath(), settings.getTokenSource());
        assertEquals(token, new ServerSettings(properties, PREFERENCE_PORT).getToken());
    }

    @Test
    public void testGetToken_existingFile() throws IOException {

        File file = new ServerSettings(properties, PREFERENCE_PORT).getTokenFile();
        file.getParentFile().mkdirs();
        Files.write(file.toPath(), " 1234\r\n".getBytes(StandardCharsets.US_ASCII));

        assertEquals("1234", new ServerSettings(properties, PREFERENCE_PORT).getToken());
    }

    @Test
    public void testRenewToken() throws IOException {

        ServerSettings settings = new ServerSettings(properties, PREFERENCE_PORT);
        String oldToken = settings.getToken();

        String newToken = settings.renewToken();

        assertTrue(newToken, newToken.matches("[0-9a-f]{64}"));
        assertNotEquals(oldToken, newToken);
        assertEquals(newToken, new ServerSettings(properties, PREFERENCE_PORT).getToken());
    }

    @Test
    public void testRenewToken_withoutTokenFile() throws IOException {

        ServerSettings settings = new ServerSettings(properties, PREFERENCE_PORT);
        String token = settings.renewToken();

        assertEquals(token, settings.getToken());
    }

    @Test(expected = IllegalStateException.class)
    public void testRenewToken_property() throws IOException {
        properties.setProperty(ServerSettings.PROPERTY_TOKEN, "abc");
        new ServerSettings(properties, PREFERENCE_PORT).renewToken();
    }

    private static void delete(File aFile) {

        File[] children = aFile.listFiles();
        if (children != null) {
            for (File child : children) {
                delete(child);
            }
        }

        aFile.delete();
    }
}
