/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.test;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import de.tools400.iagentbridge.ClientCommands;

public class ClientCommandsTest {

    @Test
    public void testSuggestServerName() {
        assertEquals("rdi096", ClientCommands.suggestServerName(9, 6));
        assertEquals("rdi099", ClientCommands.suggestServerName(9, 9));
        assertEquals("rdi100", ClientCommands.suggestServerName(10, 0));
    }

    @Test
    public void testSuggestServerName_unknownVersion() {
        assertEquals("iagentbridge", ClientCommands.suggestServerName(0, 0));
        assertEquals("iagentbridge", ClientCommands.suggestServerName(9, 10));
    }

    @Test
    public void testCreateClaudeCodeCommand_tokenFile() {
        assertEquals(
            "claude mcp add --scope user --transport http rdi096 http://127.0.0.1:39096/mcp"
                + " --header \"Authorization: Bearer $(Get-Content 'C:\\Users\\it''s me\\.iagentbridge\\token')\"",
            ClientCommands.createClaudeCodeCommand("rdi096", "http://127.0.0.1:39096/mcp", "C:\\Users\\it's me\\.iagentbridge\\token"));
    }

    @Test
    public void testCreateClaudeCodeCommand_tokenProperty() {
        assertEquals("claude mcp add --scope user --transport http rdi096 http://127.0.0.1:39196/mcp --header \"Authorization: Bearer <token>\"",
            ClientCommands.createClaudeCodeCommand("rdi096", "http://127.0.0.1:39196/mcp", null));
    }
}
