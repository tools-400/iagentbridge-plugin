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

import java.util.List;
import java.util.Locale;

import org.junit.Test;

import de.tools400.iagentbridge.ui.model.KeyParser;
import de.tools400.iagentbridge.ui.model.KeyStroke;

public class KeyParserTest {

    @Test
    public void testParse_names() {
        List<KeyStroke> keys = KeyParser.parse("esc Enter RETURN Space DEL pgdn F5 F12");

        assertEquals(8, keys.size());
        assertTrue(keys.get(0).is(KeyStroke.ESC));
        assertTrue(keys.get(1).is(KeyStroke.ENTER));
        assertTrue(keys.get(2).is(KeyStroke.ENTER));
        assertTrue(keys.get(3).is(KeyStroke.SPACE));
        assertTrue(keys.get(4).is(KeyStroke.DELETE));
        assertTrue(keys.get(5).is(KeyStroke.PAGE_DOWN));
        assertEquals(5, keys.get(6).getFunctionKey());
        assertEquals(12, keys.get(7).getFunctionKey());
        assertEquals(0, keys.get(0).getFunctionKey());
    }

    @Test
    public void testParse_modifiers() {
        List<KeyStroke> keys = KeyParser.parse("Ctrl+S ctrl+shift+F10 Alt+x Control+Home");

        KeyStroke save = keys.get(0);
        assertEquals('S', save.getCharacter());
        assertNull(save.getName());
        assertTrue(save.isCtrl());
        assertFalse(save.isShift());
        assertFalse("Ctrl+S is not plain S", save.is("S"));

        KeyStroke f10 = keys.get(1);
        assertEquals(10, f10.getFunctionKey());
        assertTrue(f10.isCtrl() && f10.isShift() && !f10.isAlt());
        assertFalse("ENTER with Ctrl is not ENTER", KeyParser.parse("Ctrl+Enter").get(0).is(KeyStroke.ENTER));

        assertTrue(keys.get(2).isAlt());
        assertEquals("Ctrl+HOME", keys.get(3).toString());
    }

    @Test
    public void testParse_characters() {
        List<KeyStroke> keys = KeyParser.parse("a 1 + Ctrl++ -");

        assertEquals('a', keys.get(0).getCharacter());
        assertEquals('1', keys.get(1).getCharacter());
        assertEquals('+', keys.get(2).getCharacter());
        assertEquals('+', keys.get(3).getCharacter());
        assertTrue(keys.get(3).isCtrl());
        assertEquals('-', keys.get(4).getCharacter());
    }

    @Test
    public void testParse_errors() {
        assertError("", "Argument 'keys' is empty.");
        assertError("Escape2", "Unknown key 'Escape2' in 'Escape2'.");
        assertError("Win+E", "Unknown modifier 'Win' in 'Win+E'.");
        assertError("F13", "Unknown key 'F13' in 'F13'.");
    }

    @Test
    public void testParse_turkishLocale() {
        Locale saved = Locale.getDefault();
        try {
            // In Turkish 'i'.toUpperCase() is a dotted capital I.
            Locale.setDefault(new Locale("tr", "TR"));
            assertTrue(KeyParser.parse("Shift+Tab").get(0).isShift());
            assertEquals(1, KeyParser.parse("insert").size());
            assertEquals(1, KeyParser.parse("Right").size());
        } finally {
            Locale.setDefault(saved);
        }
    }

    private static void assertError(String aKeys, String aStart) {
        try {
            KeyParser.parse(aKeys);
            fail("IllegalArgumentException expected for " + aKeys);
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().startsWith(aStart));
        }
    }
}
