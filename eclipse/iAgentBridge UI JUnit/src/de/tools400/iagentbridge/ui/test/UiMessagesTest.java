/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import org.junit.Test;

import de.tools400.iagentbridge.ui.Messages;

/**
 * The texts of the user interface of the UI plug-in: both languages have
 * exactly the keys of Messages, and no text is empty.
 */
public class UiMessagesTest {

    @Test
    public void testKeys() throws IOException {

        Set<String> fields = new TreeSet<String>();
        for (Field field : Messages.class.getDeclaredFields()) {
            if (Modifier.isPublic(field.getModifiers()) && Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
                fields.add(field.getName());
            }
        }

        for (String file : new String[] { "messages.properties", "messages_de.properties" }) {
            Properties texts = load(file);
            assertEquals(file, fields, new TreeSet<String>(texts.stringPropertyNames()));
            for (String key : texts.stringPropertyNames()) {
                assertFalse(file + ": " + key, texts.getProperty(key).trim().isEmpty());
            }
        }
    }

    private static Properties load(String aFile) throws IOException {
        try (InputStream in = Messages.class.getResourceAsStream(aFile)) {
            assertNotNull(aFile, in);
            Properties texts = new Properties();
            texts.load(in);
            return texts;
        }
    }
}
