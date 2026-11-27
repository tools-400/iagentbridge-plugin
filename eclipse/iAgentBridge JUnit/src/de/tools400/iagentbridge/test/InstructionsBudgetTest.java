/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.test;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import de.tools400.iagentbridge.IAgentBridgePlugin;

/**
 * The instructions of the server for the AI agent. Claude Code reads the first
 * 4096 characters of all instructions of a server; the instructions of the core
 * have a budget of 1000 characters, so that the instructions of other plug-ins,
 * e.g. the 3000 characters of the UI plug-in, fit after them.
 */
public class InstructionsBudgetTest {

    private static final int CORE_BUDGET = 1000;

    @Test
    public void testFitsBudget() {

        int length = IAgentBridgePlugin.INSTRUCTIONS.length();

        assertTrue("The instructions have " + length + " characters, more than " + CORE_BUDGET + ".", length <= CORE_BUDGET); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }
}
