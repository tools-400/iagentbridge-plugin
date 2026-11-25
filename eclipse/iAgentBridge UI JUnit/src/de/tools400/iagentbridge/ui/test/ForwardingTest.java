/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.Properties;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.gson.JsonObject;

import de.tools400.iagentbridge.mcp.McpToolResult;
import de.tools400.iagentbridge.ui.forwarding.Forwarding;
import de.tools400.iagentbridge.ui.forwarding.ForwardingClient;
import de.tools400.iagentbridge.ui.forwarding.TestInstance;
import de.tools400.iagentbridge.ui.forwarding.TokenFile;

/**
 * The decisions of the forwarding, before it calls a test instance.
 */
public class ForwardingTest {

    private File home;
    private Properties properties;
    private Forwarding forwarding;

    @Before
    public void setUp() throws IOException {
        home = Files.createTempDirectory("iagentbridge-ui-test").toFile();
        properties = new Properties();
        properties.setProperty("user.home", home.getPath());
        forwarding = new Forwarding(new ForwardingClient(), properties);
    }

    @After
    public void tearDown() {
        File tokenFile = TokenFile.get(properties);
        tokenFile.delete();
        tokenFile.getParentFile().delete();
        home.delete();
    }

    @Test
    public void testForward_none() {

        McpToolResult result = forwarding.forward(Collections.<TestInstance> emptyList(), Arrays.asList("iAgentBridge (de)", "iAgentBridge (en)"),
            "ui_snapshot", new JsonObject());

        assertTrue(result.isError());
        assertEquals("No test instance with the UI tools is running. Start one of the launch configurations with -Diagentbridge.uiTools=true: "
            + "\"iAgentBridge (de)\", \"iAgentBridge (en)\".", result.getText());
    }

    @Test
    public void testForward_noneWithoutConfigurations() {

        McpToolResult result = forwarding.forward(Collections.<TestInstance> emptyList(), Collections.<String> emptyList(), "ui_snapshot",
            new JsonObject());

        assertTrue(result.isError());
        assertTrue(result.getText(), result.getText().contains("-Diagentbridge.uiTools=true and -Diagentbridge.port=<port>"));
    }

    @Test
    public void testForward_several() {

        McpToolResult result = forwarding.forward(Arrays.asList(instance("a", "39196"), instance("b", "39197")), Collections.<String> emptyList(),
            "ui_snapshot", new JsonObject());

        assertTrue(result.isError());
        assertEquals("Several test instances with the UI tools are running: \"a\" (port 39196), \"b\" (port 39197). Stop all but one.",
            result.getText());
    }

    @Test
    public void testForward_noPort() {

        McpToolResult result = forwarding.forward(Arrays.asList(TestInstance.fromCommandLine("a", "java -Diagentbridge.uiTools=true")),
            Collections.<String> emptyList(), "ui_snapshot", new JsonObject());

        assertTrue(result.isError());
        assertTrue(result.getText(), result.getText().startsWith("The test instance \"a\" has no valid -Diagentbridge.port"));
    }

    @Test
    public void testForward_noTokenFile() {

        McpToolResult result = forwarding.forward(Arrays.asList(instance("a", "39196")), Collections.<String> emptyList(), "ui_snapshot",
            new JsonObject());

        assertTrue(result.isError());
        assertTrue(result.getText(), result.getText().startsWith("The token for the test instance \"a\" (port 39196) could not be read: "));
    }

    private static TestInstance instance(String aName, String aPort) {
        return TestInstance.fromCommandLine(aName, "java -Diagentbridge.uiTools=true -Diagentbridge.port=" + aPort);
    }
}
