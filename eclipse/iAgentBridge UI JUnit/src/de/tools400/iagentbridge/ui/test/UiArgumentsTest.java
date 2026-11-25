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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import de.tools400.iagentbridge.ui.tools.UiArguments;

public class UiArgumentsTest {

    @Test
    public void testGetOptionalString() {
        JsonObject arguments = parse("{\"shell\":\" Preferences \",\"empty\":\" \",\"nothing\":null,\"number\":1}");

        assertEquals("Preferences", UiArguments.getOptionalString(arguments, "shell"));
        assertNull(UiArguments.getOptionalString(arguments, "empty"));
        assertNull(UiArguments.getOptionalString(arguments, "nothing"));
        assertNull(UiArguments.getOptionalString(arguments, "missing"));
        try {
            UiArguments.getOptionalString(arguments, "number");
            fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException e) {
            assertEquals("Argument 'number' must be a string.", e.getMessage());
        }
    }

    @Test
    public void testGetBoolean() {
        JsonObject arguments = parse("{\"yes\":true,\"no\":false,\"text\":\"true\"}");

        assertTrue(UiArguments.getBoolean(arguments, "yes", false));
        assertFalse(UiArguments.getBoolean(arguments, "no", true));
        assertTrue(UiArguments.getBoolean(arguments, "missing", true));
        try {
            UiArguments.getBoolean(arguments, "text", false);
            fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException e) {
            assertEquals("Argument 'text' must be true or false.", e.getMessage());
        }
    }

    @Test
    public void testCheckShellOrRef() {
        UiArguments.checkShellOrRef("Preferences", null);
        UiArguments.checkShellOrRef(null, "w1");
        UiArguments.checkShellOrRef(null, null);
        try {
            UiArguments.checkShellOrRef("Preferences", "w1");
            fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException e) {
            assertEquals("Use either the argument 'shell' or the argument 'ref', not both.", e.getMessage());
        }
    }

    private static JsonObject parse(String aJson) {
        return new JsonParser().parse(aJson).getAsJsonObject();
    }
}
