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

    /**
     * The forwarding RDi adds its preference, unless the call has it.
     */
    @Test
    public void testWithKeepDialogsBehind() {
        JsonObject arguments = parse("{\"ref\":\"w17\"}");

        JsonObject forwarded = UiArguments.withKeepDialogsBehind(arguments, false);
        assertEquals("{\"ref\":\"w17\",\"keepDialogsBehind\":false}", forwarded.toString());
        assertFalse("the arguments of the caller stay unchanged", arguments.has("keepDialogsBehind"));

        JsonObject given = parse("{\"ref\":\"w17\",\"keepDialogsBehind\":true}");
        assertEquals("{\"ref\":\"w17\",\"keepDialogsBehind\":true}", UiArguments.withKeepDialogsBehind(given, false).toString());
    }

    @Test
    public void testForJournal() {
        JsonObject arguments = parse("{\"ref\":\"w17\",\"text\":\"39196\",\"snapshot\":false,\"forwarded\":true,\"keepDialogsBehind\":true}");

        assertEquals("{\"ref\":\"w17\",\"text\":\"39196\"}", UiArguments.forJournal(arguments));
        assertTrue("the arguments stay unchanged", arguments.has("forwarded"));
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

    @Test
    public void testGetRef() {
        assertEquals("w17", UiArguments.getRef(parse("{\"ref\":\" w17 \"}")));
        for (String arguments : new String[] { "{}", "{\"ref\":null}", "{\"ref\":\" \"}" }) {
            try {
                UiArguments.getRef(parse(arguments));
                fail("IllegalArgumentException expected for " + arguments);
            } catch (IllegalArgumentException e) {
                assertEquals("Argument 'ref' is missing.", e.getMessage());
            }
        }
    }

    @Test
    public void testGetRequiredBoolean() {
        assertTrue(UiArguments.getRequiredBoolean(parse("{\"checked\":true}"), "checked"));
        assertFalse(UiArguments.getRequiredBoolean(parse("{\"checked\":false}"), "checked"));
        try {
            UiArguments.getRequiredBoolean(parse("{}"), "checked");
            fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException e) {
            assertEquals("Argument 'checked' is missing.", e.getMessage());
        }
        try {
            UiArguments.getRequiredBoolean(parse("{\"checked\":\"yes\"}"), "checked");
            fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException e) {
            assertEquals("Argument 'checked' must be true or false.", e.getMessage());
        }
    }

    private static JsonObject parse(String aJson) {
        return new JsonParser().parse(aJson).getAsJsonObject();
    }
}
