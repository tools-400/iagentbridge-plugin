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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import de.tools400.iagentbridge.ToolCollection;
import de.tools400.iagentbridge.ToolCollection.ToolError;
import de.tools400.iagentbridge.mcp.IMcpTool;
import de.tools400.iagentbridge.mcp.McpToolResult;

public class ToolCollectionTest {

    private static final String OBJECT_SCHEMA = "{\"type\":\"object\",\"properties\":{}}";

    private static final String NO_OBJECT_SCHEMA = "A tool of plug-in x is left out: the input schema of the tool is not of type 'object'.";

    @Test
    public void testAdd_keepsOrder() {

        ToolCollection tools = new ToolCollection();
        TestTool first = new TestTool("clean_up");
        TestTool second = new TestTool("format_rpg");

        assertTrue(tools.add(first, "core"));
        assertTrue(tools.add(second, "other"));

        assertEquals(Arrays.<IMcpTool> asList(first, second), tools.getTools());
        assertEquals(Arrays.asList("clean_up", "format_rpg"), tools.getNames());
        assertTrue(tools.getErrors().isEmpty());
    }

    @Test
    public void testAdd_duplicateName_firstWins() {

        ToolCollection tools = new ToolCollection();
        TestTool builtIn = new TestTool("clean_up");

        tools.add(builtIn, "de.tools400.iagentbridge.core.plugin");
        assertFalse(tools.add(new TestTool("clean_up"), "com.example.other"));

        assertEquals(1, tools.getTools().size());
        assertSame(builtIn, tools.getTools().get(0));
        assertError(tools, "Tool 'clean_up' of plug-in com.example.other is left out, because plug-in de.tools400.iagentbridge.core.plugin "
            + "already contributes a tool of that name.");
    }

    @Test
    public void testAdd_validNames() {

        ToolCollection tools = new ToolCollection();

        assertTrue(tools.add(new TestTool("a"), "x"));
        assertTrue(tools.add(new TestTool("Format-RPG_2"), "x"));
        assertTrue(tools.add(new TestTool("get_problems"), "x"));
        assertTrue(tools.add(new TestTool("import_projects"), "x"));
        assertTrue(tools.add(new TestTool("run_tests"), "x"));
        assertTrue(tools.add(new TestTool(repeat('n', 64)), "x"));

        assertTrue(tools.getErrors().isEmpty());
    }

    @Test
    public void testAdd_invalidNames() {

        assertLeftOut(new TestTool(null), "A tool of plug-in x is left out: the tool has no name.");
        assertInvalidName("");
        assertInvalidName("format rpg");
        assertInvalidName("rpg.format");
        assertInvalidName(repeat('n', 65));
    }

    @Test
    public void testAdd_noDescription() {
        assertLeftOut(new TestTool("t", null, OBJECT_SCHEMA), "A tool of plug-in x is left out: the tool has no description.");
        assertLeftOut(new TestTool("t", " ", OBJECT_SCHEMA), "A tool of plug-in x is left out: the tool has no description.");
    }

    @Test
    public void testAdd_invalidInputSchema() {
        assertLeftOut(new TestTool("t", "d", null), "A tool of plug-in x is left out: the tool has no input schema.");
        assertLeftOut(new TestTool("t", "d", "{}"), NO_OBJECT_SCHEMA);
        assertLeftOut(new TestTool("t", "d", "{\"type\":\"array\"}"), NO_OBJECT_SCHEMA);
        assertLeftOut(new TestTool("t", "d", "{\"type\":{}}"), NO_OBJECT_SCHEMA);
    }

    @Test
    public void testAdd_failsToDescribeItself() {

        ToolCollection tools = new ToolCollection();
        FailingTool tool = new FailingTool();

        assertFalse(tools.add(tool, "com.example.other"));

        assertTrue(tools.getTools().isEmpty());
        List<ToolError> errors = tools.getErrors();
        assertEquals(1, errors.size());
        assertEquals("Tool " + FailingTool.class.getName() + " of plug-in com.example.other is left out, because it failed to describe itself.",
            errors.get(0).getMessage());
        assertSame(tool.exception, errors.get(0).getCause());
    }

    @Test
    public void testAddError() {

        ToolCollection tools = new ToolCollection();
        IllegalStateException cause = new IllegalStateException("x");

        tools.addError("Some error.", cause);

        assertEquals("Some error.", tools.getErrors().get(0).getMessage());
        assertSame(cause, tools.getErrors().get(0).getCause());
    }

    private static void assertInvalidName(String aName) {
        assertLeftOut(new TestTool(aName),
            "A tool of plug-in x is left out: the name '" + aName + "' is invalid. A name consists of 1 to 64 letters, digits, '_' and '-'.");
    }

    private static void assertLeftOut(IMcpTool aTool, String anExpectedError) {

        ToolCollection tools = new ToolCollection();

        assertFalse(tools.add(aTool, "x"));

        assertTrue(tools.getTools().isEmpty());
        assertError(tools, anExpectedError);
    }

    private static void assertError(ToolCollection aTools, String anExpectedError) {

        List<ToolError> errors = aTools.getErrors();
        assertEquals(1, errors.size());
        assertEquals(anExpectedError, errors.get(0).getMessage());
        assertNull(errors.get(0).getCause());
    }

    private static String repeat(char aChar, int aCount) {

        StringBuilder text = new StringBuilder();
        for (int i = 0; i < aCount; i++) {
            text.append(aChar);
        }

        return text.toString();
    }

    private static class TestTool implements IMcpTool {

        private final String name;
        private final String description;
        private final String inputSchema;

        public TestTool(String aName) {
            this(aName, "A tool.", OBJECT_SCHEMA);
        }

        public TestTool(String aName, String aDescription, String anInputSchema) {
            name = aName;
            description = aDescription;
            inputSchema = anInputSchema;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public JsonObject getInputSchema() {
            return inputSchema == null ? null : new JsonParser().parse(inputSchema).getAsJsonObject();
        }

        @Override
        public McpToolResult execute(JsonObject anArguments) {
            return McpToolResult.success(name);
        }
    }

    private static class FailingTool extends TestTool {

        private final RuntimeException exception = new IllegalStateException("broken");

        public FailingTool() {
            super("failing");
        }

        @Override
        public JsonObject getInputSchema() {
            throw exception;
        }
    }
}
