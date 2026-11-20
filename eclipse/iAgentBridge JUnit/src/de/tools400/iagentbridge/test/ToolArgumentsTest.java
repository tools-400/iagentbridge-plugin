/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.util.Arrays;

import org.junit.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import de.tools400.iagentbridge.mcp.ToolArguments;

public class ToolArgumentsTest {

    @Test
    public void testGetStringList_array() {
        assertEquals(Arrays.asList("a", "b"), ToolArguments.getStringList(arguments("{\"files\":[\"a\",\"b\"]}"), "files"));
    }

    @Test
    public void testGetStringList_singleString() {
        assertEquals(Arrays.asList("a"), ToolArguments.getStringList(arguments("{\"files\":\"a\"}"), "files"));
    }

    @Test
    public void testGetStringList_invalid() {
        assertInvalid("{}", "Argument 'files' is missing.");
        assertInvalid("{\"files\":null}", "Argument 'files' is missing.");
        assertInvalid("{\"files\":[]}", "Argument 'files' must not be empty.");
        assertInvalid("{\"files\":[\"a\",1]}", "Argument 'files' must contain strings only.");
        assertInvalid("{\"files\":{}}", "Argument 'files' must be an array of strings.");
    }

    @Test
    public void testToLocalPath_gitBash() {

        assumeTrue(File.separatorChar == '\\');

        assertEquals("C:/workspaces/x/A.java", ToolArguments.toLocalPath(" /c/workspaces/x/A.java "));
        assertEquals("D:/", ToolArguments.toLocalPath("/d"));
    }

    @Test
    public void testToLocalPath_unchanged() {
        assertEquals("C:\\workspaces\\x\\A.java", ToolArguments.toLocalPath("C:\\workspaces\\x\\A.java"));
        assertEquals("C:/workspaces/x/A.java", ToolArguments.toLocalPath("C:/workspaces/x/A.java"));
        assertEquals("/cd/x", ToolArguments.toLocalPath("/cd/x"));
    }

    private static JsonObject arguments(String aJson) {
        return new JsonParser().parse(aJson).getAsJsonObject();
    }

    private static void assertInvalid(String aJson, String anExpectedMessage) {
        try {
            ToolArguments.getStringList(arguments(aJson), "files");
            fail("IllegalArgumentException expected for: " + aJson);
        } catch (IllegalArgumentException e) {
            assertEquals(anExpectedMessage, e.getMessage());
        }
    }
}
