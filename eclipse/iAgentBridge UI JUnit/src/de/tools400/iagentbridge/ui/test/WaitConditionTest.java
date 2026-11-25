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

import de.tools400.iagentbridge.ui.model.WaitCondition;
import de.tools400.iagentbridge.ui.model.WidgetNode;

public class WaitConditionTest {

    @Test
    public void testParse() {
        WaitCondition shell = WaitCondition.parse(parse("{\"shell\":\" Preferences \",\"timeoutSeconds\":30}"));
        assertEquals(WaitCondition.Kind.SHELL, shell.getKind());
        assertEquals("Preferences", shell.getValue());
        assertEquals(30, shell.getTimeoutSeconds());

        WaitCondition jobs = WaitCondition.parse(parse("{\"jobsIdle\":true}"));
        assertEquals(WaitCondition.Kind.JOBS_IDLE, jobs.getKind());
        assertNull(jobs.getValue());
        assertEquals(WaitCondition.DEFAULT_TIMEOUT_SECONDS, jobs.getTimeoutSeconds());

        assertEquals("the end of w17", WaitCondition.parse(parse("{\"gone\":\"w17\"}")).describe());
        assertEquals("the end of the shell \"Neues Token\"", WaitCondition.parse(parse("{\"gone\":\"Neues Token\"}")).describe());
    }

    @Test
    public void testParse_errors() {
        assertError("{}", "Give one of 'shell', 'text', 'gone' and 'jobsIdle'.");
        assertError("{\"shell\":\"a\",\"text\":\"b\"}", "Give exactly one of 'shell', 'text', 'gone' and 'jobsIdle'.");
        assertError("{\"jobsIdle\":false}", "Argument 'jobsIdle' must be true.");
        assertError("{\"text\":\" \"}", "Argument 'text' must be a string, that is not empty.");
        assertError("{\"text\":\"a\",\"timeoutSeconds\":61}", "Argument 'timeoutSeconds' must be an integer from 1 to 60.");
        assertError("{\"text\":\"a\",\"timeoutSeconds\":0}", "Argument 'timeoutSeconds' must be an integer from 1 to 60.");
        assertError("{\"text\":\"a\",\"timeoutSeconds\":1.5}", "Argument 'timeoutSeconds' must be an integer from 1 to 60.");
    }

    /**
     * The RDi, that forwards the call, checks the timeout, before it forwards:
     * its wait for the answer follows from it.
     */
    @Test
    public void testTimeoutSeconds() {
        assertEquals(10, WaitCondition.timeoutSeconds(parse("{}")));
        assertEquals(60, WaitCondition.timeoutSeconds(parse("{\"timeoutSeconds\":60}")));
        try {
            WaitCondition.timeoutSeconds(parse("{\"timeoutSeconds\":\"5\"}"));
            fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException e) {
            assertEquals("Argument 'timeoutSeconds' must be an integer from 1 to 60.", e.getMessage());
        }
    }

    @Test
    public void testContains() {
        WidgetNode shell = WidgetNode.widget("shell", "Preferences", "w1");
        WidgetNode field = WidgetNode.widget("text", null, "w2");
        field.addValue("label", "Port:");
        field.addValue("value", "39196");
        WidgetNode password = WidgetNode.widget("password", null, "w3");
        WidgetNode message = WidgetNode.widget("label", "Value must be an Integer", "w4");
        message.setTooltip("tip text");
        shell.addChild(field).addChild(password).addChild(message);

        assertTrue(WaitCondition.contains(shell, "Preferences"));
        assertTrue(WaitCondition.contains(shell, "Port"));
        assertTrue(WaitCondition.contains(shell, "391"));
        assertTrue(WaitCondition.contains(shell, "must be an Integer"));
        assertTrue(WaitCondition.contains(shell, "tip text"));
        assertFalse(WaitCondition.contains(shell, "secret"));
    }

    private static void assertError(String aJson, String aMessage) {
        try {
            WaitCondition.parse(parse(aJson));
            fail("IllegalArgumentException expected for " + aJson);
        } catch (IllegalArgumentException e) {
            assertEquals(aMessage, e.getMessage());
        }
    }

    private static JsonObject parse(String aJson) {
        return new JsonParser().parse(aJson).getAsJsonObject();
    }
}
