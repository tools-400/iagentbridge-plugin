/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import de.tools400.iagentbridge.ui.model.LocatorBuilder;

public class LocatorBuilderTest {

    @Test
    public void testLocator_id() {
        assertEquals("withId(\"iagentbridge.port\")", LocatorBuilder.locator("text", null, "Port:", "iagentbridge.port"));
        assertEquals("withId(\"ok\")", LocatorBuilder.locator("button", "OK", null, "ok"));
    }

    @Test
    public void testLocator_buttons() {
        assertEquals("button(\"OK\")", LocatorBuilder.locator("button", "OK", null, null));
        assertEquals("checkBox(\"Start the server\")", LocatorBuilder.locator("checkbox", "Start the server", null, null));
        assertEquals("radio(\"Run\")", LocatorBuilder.locator("radio", "Run", null, null));
        assertEquals("toggleButton(\"Bold\")", LocatorBuilder.locator("toggle", "Bold", null, null));
        assertNull(LocatorBuilder.locator("button", null, null, null));
        assertNull(LocatorBuilder.locator("button", "", null, null));
    }

    @Test
    public void testLocator_fieldsWithLabel() {
        assertEquals("textWithLabel(\"Port:\")", LocatorBuilder.locator("text", null, "Port:", null));
        assertEquals("comboBoxWithLabel(\"Mode:\")", LocatorBuilder.locator("combo", null, "Mode:", null));
        assertEquals("ccomboBoxWithLabel(\"Mode:\")", LocatorBuilder.locator("ccombo", null, "Mode:", null));
        assertEquals("spinnerWithLabel(\"Count:\")", LocatorBuilder.locator("spinner", null, "Count:", null));
        assertEquals("styledTextWithLabel(\"Source:\")", LocatorBuilder.locator("styledtext", null, "Source:", null));
        assertNull(LocatorBuilder.locator("text", null, null, null));
        assertNull(LocatorBuilder.locator("text", null, "", null));
    }

    @Test
    public void testLocator_listsAndRangesWithLabel() {
        assertEquals("listWithLabel(\"Filter strings:\")", LocatorBuilder.locator("list", null, "Filter strings:", null));
        assertEquals("scaleWithLabel(\"Zoom:\")", LocatorBuilder.locator("scale", null, "Zoom:", null));
        assertEquals("sliderWithLabel(\"Position:\")", LocatorBuilder.locator("slider", null, "Position:", null));
        assertEquals("dateTimeWithLabel(\"Date:\")", LocatorBuilder.locator("datetime", null, "Date:", null));
        assertNull(LocatorBuilder.locator("progressbar", null, "Progress:", null));
    }

    @Test
    public void testLocator_buttonsWithLabel() {
        assertEquals("checkBoxWithLabel(\"Keep indentation:\")", LocatorBuilder.locator("checkbox", null, "Keep indentation:", null));
        assertEquals("radioWithLabel(\"Mode:\")", LocatorBuilder.locator("radio", "", "Mode:", null));
        assertEquals("buttonWithLabel(\"Folder:\")", LocatorBuilder.locator("button", null, "Folder:", null));
        assertEquals("toggleButtonWithLabel(\"Bold:\")", LocatorBuilder.locator("toggle", null, "Bold:", null));
        assertEquals("the text of the button wins", "checkBox(\"On\")", LocatorBuilder.locator("checkbox", "On", "Keep indentation:", null));
    }

    @Test
    public void testLocator_others() {
        assertNull(LocatorBuilder.locator("tree", null, null, null));
        assertNull(LocatorBuilder.locator("label", "Port:", null, null));
    }

    @Test
    public void testLocator_lineBreaks() {
        assertEquals("button(\"Line 1\\nLine 2\\tTab\\r\")", LocatorBuilder.locator("button", "Line 1\nLine 2\tTab\r", null, null));
    }

    @Test
    public void testLocator_quotes() {
        assertEquals("button(\"Say \\\"Hi\\\" \\\\ bye\")", LocatorBuilder.locator("button", "Say \"Hi\" \\ bye", null, null));
    }
}
