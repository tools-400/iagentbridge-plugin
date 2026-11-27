/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Test;

/**
 * The instructions of the UI plug-in for the AI agent, that the server returns
 * after its own instructions. Claude Code reads the first 4096 characters of
 * all instructions of a server, before version 2.1.296 the first 2048. The
 * instructions of the core have a budget of 1000 characters (see
 * InstructionsBudgetTest of the core), those of the UI plug-in 3000, so that
 * both fit into 4096 with the empty line between them.
 */
public class UiInstructionsTest {

    /** The file, relative to a project of the folder 'eclipse' */
    private static final String FILE = "../iAgentBridge UI Plugin/instructions/ui-tools.txt"; //$NON-NLS-1$

    private static final int UI_BUDGET = 3000;
    private static final int CORE_BUDGET = 1000;
    private static final int OLD_CLIENT_LIMIT = 2048;

    @Test
    public void testFitsBudget() throws IOException {

        String text = read();

        assertTrue("The instructions have " + text.length() + " characters, more than " + UI_BUDGET + ".", text.length() <= UI_BUDGET); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    @Test
    public void testRulesFirst_readByOldClients() throws IOException {

        String text = read();
        int endOfRules = text.indexOf("After a UI change"); //$NON-NLS-1$

        assertTrue("The rules must come before the question and the steps.", endOfRules > 0 && endOfRules < text.indexOf("1. ")); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue("The rules end at " + endOfRules + "; a client, that reads " + OLD_CLIENT_LIMIT //$NON-NLS-1$ //$NON-NLS-2$
            + " characters, would cut them after the instructions of the core.", CORE_BUDGET + 2 + endOfRules <= OLD_CLIENT_LIMIT); //$NON-NLS-1$
    }

    @Test
    public void testProductIndependent() throws IOException {

        String text = read();

        for (String name : new String[] { "iSphere", "RPGUnit", "Rapid Fire", "JCRCMDS", "IFS Projects", "iRPGFormatter", "rdi096", "3909", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$
            "3919", "iAgentBridge (de)" }) { //$NON-NLS-1$ //$NON-NLS-2$
            assertFalse("The instructions name '" + name + "'.", text.contains(name)); //$NON-NLS-1$ //$NON-NLS-2$
        }
    }

    @Test
    public void testAscii() throws IOException {

        String text = read();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            assertTrue("Character " + (int)c + " at " + i + " is not ASCII.", c == '\n' || c >= ' ' && c < 127); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        }
    }

    /**
     * Reads the instructions like the core: UTF-8, line ends as '\n', without
     * the white space at the start and the end.
     */
    private static String read() throws IOException {

        byte[] bytes = Files.readAllBytes(new File(FILE).toPath());
        return new String(bytes, StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n').trim(); //$NON-NLS-1$ //$NON-NLS-2$
    }
}
