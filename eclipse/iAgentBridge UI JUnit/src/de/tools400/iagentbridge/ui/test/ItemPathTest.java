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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;

import org.junit.Test;

import de.tools400.iagentbridge.ui.model.ItemPath;

public class ItemPathTest {

    @Test
    public void testParse() {
        assertEquals(Arrays.asList("Window", "Preferences..."), ItemPath.parse("Window > Preferences..."));
        assertEquals(Arrays.asList("General", "Editors", "Text Editors"), ItemPath.parse("General>Editors >  Text Editors"));
        assertEquals(Arrays.asList("Refresh"), ItemPath.parse(" Refresh "));
        assertEquals("an escaped '>' is part of a name", Arrays.asList("a > b", "c"), ItemPath.parse("a \\> b > c"));
        assertEquals("a backslash alone stays", Arrays.asList("C:\\temp"), ItemPath.parse("C:\\temp"));
    }

    @Test
    public void testParse_errors() {
        assertError("", "The path is empty.");
        assertError(" ", "The path is empty.");
        assertError("Window > ", "The path 'Window > ' has an empty part.");
        assertError("> Window", "The path '> Window' has an empty part.");
    }

    @Test
    public void testMenuText() {
        assertEquals("Preferences", ItemPath.menuText("&Preferences..."));
        assertEquals("Save", ItemPath.menuText("&Save\tCtrl+S"));
        assertEquals("Open File", ItemPath.menuText("Open F&ile\u2026"));
        assertEquals("Save & Close", ItemPath.menuText("Save && &Close"));
    }

    @Test
    public void testMatchesMenu() {
        assertTrue(ItemPath.matchesMenu("&Preferences...", "Preferences..."));
        assertTrue(ItemPath.matchesMenu("&Preferences...", "Preferences"));
        assertTrue(ItemPath.matchesMenu("Benutzer&vorgaben", "Benutzervorgaben..."));
        assertTrue(ItemPath.matchesMenu("&Save\tCtrl+S", "Save"));
        assertFalse(ItemPath.matchesMenu("Save &As...", "Save"));
        assertFalse("case matters", ItemPath.matchesMenu("&Window", "window"));
    }

    @Test
    public void testMatchesMenu_ampersand() {
        // Menu and messages show 'Save && &Close' as 'Save & Close'.
        assertTrue(ItemPath.matchesMenu("Save && &Close", "Save & Close"));
        assertFalse(ItemPath.matchesMenu("Save && &Close", "Save  Close"));
    }

    private static void assertError(String aPath, String aMessage) {
        try {
            ItemPath.parse(aPath);
            fail("IllegalArgumentException expected for '" + aPath + "'");
        } catch (IllegalArgumentException e) {
            assertEquals(aMessage, e.getMessage());
        }
    }
}
