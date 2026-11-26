/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.test;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.Arrays;

import org.junit.After;
import org.junit.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import de.tools400.iagentbridge.mcp.IMcpTool;
import de.tools400.iagentbridge.mcp.McpDispatcher;
import de.tools400.iagentbridge.mcp.McpHttpServer;
import de.tools400.iagentbridge.mcp.McpToolResult;
import de.tools400.iagentbridge.ui.forwarding.TestInstance;
import de.tools400.iagentbridge.ui.launches.Identity;

/**
 * Who answers at the port of a launch: Identity against a real McpHttpServer,
 * whose tool workspace_info returns a workspace.
 */
public class IdentityTest {

    private static final String TOKEN = "identity-token"; //$NON-NLS-1$
    private static final String WORKSPACE = "C:\\workspaces\\runtime-test"; //$NON-NLS-1$

    private McpHttpServer server;

    @After
    public void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    public void testSameOrOtherWorkspace() throws IOException {
        start();
        assertEquals(Identity.Result.SAME, Identity.check(instance("iAgentBridge (de)"), WORKSPACE.toLowerCase())); //$NON-NLS-1$
        assertEquals(Identity.Result.OTHER, Identity.check(instance("iAgentBridge (de)"), "C:\\workspaces\\other")); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testBraceInTheName() throws IOException {
        start();
        // The answer starts with the line 'Test instance "Test {de}" (port
        // N):'.
        assertEquals(Identity.Result.SAME, Identity.check(instance("Test {de}"), WORKSPACE)); //$NON-NLS-1$
    }

    @Test
    public void testWithoutLocation() throws IOException {
        start();
        // The workspace of the launch configuration is not known.
        assertEquals(Identity.Result.UNKNOWN, Identity.check(instance("iAgentBridge (de)"), null)); //$NON-NLS-1$
    }

    @Test
    public void testNoAnswer() throws IOException {
        start();
        TestInstance instance = instance("iAgentBridge (de)"); //$NON-NLS-1$
        server.stop();
        server = null;
        assertEquals(Identity.Result.NO_ANSWER, Identity.check(instance, WORKSPACE));
    }

    private void start() throws IOException {
        server = new McpHttpServer(new McpDispatcher("test-server", "1.0", null, Arrays.<IMcpTool> asList(new WorkspaceInfo())), TOKEN); //$NON-NLS-1$ //$NON-NLS-2$
        server.start(0);
    }

    private TestInstance instance(String aName) {
        return TestInstance.fromCommandLine(aName,
            "java -Diagentbridge.uiTools=true -Diagentbridge.port=" + server.getPort() + " -Diagentbridge.token=" + TOKEN); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /**
     * Answers like the tool workspace_info of the core.
     */
    private static final class WorkspaceInfo implements IMcpTool {

        @Override
        public String getName() {
            return "workspace_info"; //$NON-NLS-1$
        }

        @Override
        public String getDescription() {
            return "Test tool workspace_info"; //$NON-NLS-1$
        }

        @Override
        public JsonObject getInputSchema() {
            return new JsonParser().parse("{\"type\":\"object\",\"properties\":{}}").getAsJsonObject(); //$NON-NLS-1$
        }

        @Override
        public McpToolResult execute(JsonObject anArguments) {
            JsonObject answer = new JsonObject();
            answer.addProperty("product", "IBM Rational Developer for i"); //$NON-NLS-1$ //$NON-NLS-2$
            answer.addProperty("workspace", WORKSPACE); //$NON-NLS-1$
            return McpToolResult.success(answer.toString());
        }
    }
}
