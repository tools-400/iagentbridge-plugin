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
import java.util.Collections;
import java.util.function.Consumer;

import org.junit.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import de.tools400.iagentbridge.mcp.ToolArguments;

public class ToolArgumentsTest {

    @Test
    public void testGetString() {
        assertEquals("iAgentBridge JUnit", ToolArguments.getString(arguments("{\"launch\":\"iAgentBridge JUnit\"}"), "launch"));
        assertEquals(" a ", ToolArguments.getString(arguments("{\"launch\":\" a \"}"), "launch"));
    }

    @Test
    public void testGetString_invalid() {
        assertInvalid(a -> ToolArguments.getString(a, "launch"), "{}", "Argument 'launch' is missing.");
        assertInvalid(a -> ToolArguments.getString(a, "launch"), "{\"launch\":null}", "Argument 'launch' is missing.");
        assertInvalid(a -> ToolArguments.getString(a, "launch"), "{\"launch\":\"\"}", "Argument 'launch' must not be empty.");
        assertInvalid(a -> ToolArguments.getString(a, "launch"), "{\"launch\":\" \"}", "Argument 'launch' must not be empty.");
        assertInvalid(a -> ToolArguments.getString(a, "launch"), "{\"launch\":5}", "Argument 'launch' must be a string.");
        assertInvalid(a -> ToolArguments.getString(a, "launch"), "{\"launch\":[\"a\"]}", "Argument 'launch' must be a string.");
    }

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
    public void testGetOptionalStringList() {

        assertEquals(Arrays.asList("a", "b"), ToolArguments.getOptionalStringList(arguments("{\"projects\":[\"a\",\"b\"]}"), "projects"));
        assertEquals(Arrays.asList("a"), ToolArguments.getOptionalStringList(arguments("{\"projects\":\"a\"}"), "projects"));
        assertEquals(Collections.emptyList(), ToolArguments.getOptionalStringList(arguments("{}"), "projects"));
        assertEquals(Collections.emptyList(), ToolArguments.getOptionalStringList(arguments("{\"projects\":null}"), "projects"));
        assertEquals(Collections.emptyList(), ToolArguments.getOptionalStringList(arguments("{\"projects\":[]}"), "projects"));
    }

    @Test
    public void testGetOptionalStringList_invalid() {

        assertInvalid(a -> ToolArguments.getOptionalStringList(a, "projects"), "{\"projects\":[\"a\",1]}",
            "Argument 'projects' must contain strings only.");
        assertInvalid(a -> ToolArguments.getOptionalStringList(a, "projects"), "{\"projects\":5}",
            "Argument 'projects' must be an array of strings.");
    }

    @Test
    public void testGetChoice() {

        assertEquals("full", ToolArguments.getChoice(arguments("{\"build\":\"full\"}"), "build", "incremental", "incremental", "full", "none"));
        assertEquals("incremental", ToolArguments.getChoice(arguments("{}"), "build", "incremental", "incremental", "full", "none"));
        assertEquals("incremental", ToolArguments.getChoice(arguments("{\"build\":null}"), "build", "incremental", "incremental", "full"));
    }

    @Test
    public void testGetChoice_invalid() {

        String message = "Argument 'build' must be one of: incremental, full, none.";
        assertInvalid(a -> ToolArguments.getChoice(a, "build", "incremental", "incremental", "full", "none"), "{\"build\":\"clean\"}", message);
        assertInvalid(a -> ToolArguments.getChoice(a, "build", "incremental", "incremental", "full", "none"), "{\"build\":\"Full\"}", message);
        assertInvalid(a -> ToolArguments.getChoice(a, "build", "incremental", "incremental", "full", "none"), "{\"build\":[\"full\"]}", message);
        assertInvalid(a -> ToolArguments.getChoice(a, "build", "incremental", "incremental", "full", "none"), "{\"build\":1}", message);
    }

    @Test
    public void testGetChoice_defaultNotAllowed() {

        String message = "The default value 'fast' of argument 'mode' is not one of the allowed values.";
        assertDefaultNotAllowed("{}", message);
        assertDefaultNotAllowed("{\"mode\":\"safe\"}", message);
    }

    @Test
    public void testGetInt() {

        assertEquals(50, ToolArguments.getInt(arguments("{\"maxProblems\":50}"), "maxProblems", 200, 1));
        assertEquals(50, ToolArguments.getInt(arguments("{\"maxProblems\":50.0}"), "maxProblems", 200, 1));
        assertEquals(1, ToolArguments.getInt(arguments("{\"maxProblems\":1}"), "maxProblems", 200, 1));
        assertEquals(Integer.MAX_VALUE, ToolArguments.getInt(arguments("{\"maxProblems\":2147483647}"), "maxProblems", 200, 1));
        assertEquals(200, ToolArguments.getInt(arguments("{}"), "maxProblems", 200, 1));
        assertEquals(200, ToolArguments.getInt(arguments("{\"maxProblems\":null}"), "maxProblems", 200, 1));
    }

    @Test
    public void testGetInt_invalid() {

        String noInteger = "Argument 'maxProblems' must be an integer.";
        assertInvalid(a -> ToolArguments.getInt(a, "maxProblems", 200, 1), "{\"maxProblems\":2.5}", noInteger);
        assertInvalid(a -> ToolArguments.getInt(a, "maxProblems", 200, 1), "{\"maxProblems\":\"50\"}", noInteger);
        assertInvalid(a -> ToolArguments.getInt(a, "maxProblems", 200, 1), "{\"maxProblems\":true}", noInteger);
        assertInvalid(a -> ToolArguments.getInt(a, "maxProblems", 200, 1), "{\"maxProblems\":[50]}", noInteger);
        assertInvalid(a -> ToolArguments.getInt(a, "maxProblems", 200, 1), "{\"maxProblems\":2147483648}", noInteger);
        assertInvalid(a -> ToolArguments.getInt(a, "maxProblems", 200, 1), "{\"maxProblems\":1e999999999}", noInteger);
        assertInvalid(a -> ToolArguments.getInt(a, "maxProblems", 200, 1), "{\"maxProblems\":0}", "Argument 'maxProblems' must be at least 1.");
        assertInvalid(a -> ToolArguments.getInt(a, "maxProblems", 200, 1), "{\"maxProblems\":-5}", "Argument 'maxProblems' must be at least 1.");
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

    private static void assertDefaultNotAllowed(String aJson, String anExpectedMessage) {
        try {
            ToolArguments.getChoice(arguments(aJson), "mode", "fast", "safe");
            fail("IllegalStateException expected for: " + aJson);
        } catch (IllegalStateException e) {
            assertEquals(anExpectedMessage, e.getMessage());
        }
    }

    private static void assertInvalid(String aJson, String anExpectedMessage) {
        assertInvalid(a -> ToolArguments.getStringList(a, "files"), aJson, anExpectedMessage);
    }

    private static void assertInvalid(Consumer<JsonObject> aReader, String aJson, String anExpectedMessage) {
        try {
            aReader.accept(arguments(aJson));
            fail("IllegalArgumentException expected for: " + aJson);
        } catch (IllegalArgumentException e) {
            assertEquals(anExpectedMessage, e.getMessage());
        }
    }
}
