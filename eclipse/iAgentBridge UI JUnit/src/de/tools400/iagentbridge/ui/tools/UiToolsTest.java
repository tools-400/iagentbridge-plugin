/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import de.tools400.iagentbridge.mcp.McpToolResult;

/**
 * Checks of single tools without RDi: their arguments and what they do without
 * a widget. The test lies in the package of the tools, because it calls their
 * package visible and protected methods.
 */
public class UiToolsTest {

    @Test
    public void testSelectEntryWithGreaterThan() {
        // '<none>' is the text of an entry of a combo, no path of a tree.
        assertNotNull(new UiSelectTool().prepare(arguments("{\"ref\":\"w1\",\"values\":[\"<none>\"]}"))); //$NON-NLS-1$
    }

    @Test
    public void testWaitForTheEndOfAnUnknownRef() throws Exception {

        McpToolResult result = new UiWaitTool().execute(arguments("{\"gone\":\"w99999\",\"timeoutSeconds\":1}"), null, Runnable::run); //$NON-NLS-1$

        assertTrue(result.getText(), result.isError());
        assertTrue(result.getText(), result.getText().startsWith("Ref w99999 is unknown")); //$NON-NLS-1$
    }

    @Test
    public void testLpexArguments() {

        assertNotNull("read", new UiLpexTool().prepare(arguments("{\"from\":1,\"to\":500}"))); //$NON-NLS-1$ //$NON-NLS-2$
        assertNotNull("write", new UiLpexTool().prepare(arguments("{\"line\":3,\"column\":5,\"text\":\"x\"}"))); //$NON-NLS-1$ //$NON-NLS-2$
        assertLpexError("{\"column\":3}", "Argument 'column' needs 'line'."); //$NON-NLS-1$ //$NON-NLS-2$
        assertLpexError("{\"from\":1,\"to\":501}", "At most 500 lines per call."); //$NON-NLS-1$ //$NON-NLS-2$
        assertLpexError("{\"from\":10,\"to\":9}", "Argument 'to' must be at least 10."); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static void assertLpexError(String anArguments, String aMessage) {
        try {
            new UiLpexTool().prepare(arguments(anArguments));
            fail("IllegalArgumentException expected for " + anArguments); //$NON-NLS-1$
        } catch (IllegalArgumentException e) {
            assertEquals(aMessage, e.getMessage());
        }
    }

    @Test
    public void testSpinnerValue() {

        assertEquals(42, UiTypeTool.spinnerValue(" 42 ", 0, 100));
        assertEquals(0, UiTypeTool.spinnerValue("0", 0, 100));
        assertEquals(100, UiTypeTool.spinnerValue("100", 0, 100));
        assertSpinnerError("101", "The value 101 is not in the range 0..100 of the spinner."); //$NON-NLS-1$ //$NON-NLS-2$
        assertSpinnerError("-1", "The value -1 is not in the range 0..100 of the spinner."); //$NON-NLS-1$ //$NON-NLS-2$
        assertSpinnerError("4x", "A spinner takes an integer, not '4x'."); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static void assertSpinnerError(String aText, String aMessage) {
        try {
            UiTypeTool.spinnerValue(aText, 0, 100);
            fail("IllegalArgumentException expected for " + aText); //$NON-NLS-1$
        } catch (IllegalArgumentException e) {
            assertEquals(aMessage, e.getMessage());
        }
    }

    private static JsonObject arguments(String aJson) {
        return new JsonParser().parse(aJson).getAsJsonObject();
    }
}
