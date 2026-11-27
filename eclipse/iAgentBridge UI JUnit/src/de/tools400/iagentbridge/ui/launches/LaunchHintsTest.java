/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.launches;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.junit.Test;

/**
 * The error texts of start_launch, that need Eclipse launches, with the hint,
 * what the AI agent does next. The test lies in the package of the classes,
 * because it calls their package visible methods.
 */
public class LaunchHintsTest {

    private static final String DE = "iAgentBridge (de)"; //$NON-NLS-1$

    @Test
    public void testLaunchProblems() {
        assertEquals(
            "Files of the launch have unsaved changes in an editor, about which RDi would ask in a dialog: /p/a.rpgle, " //$NON-NLS-1$
                + "/p/b.rpgle. Ask the developer to save them: the UI tools cannot reach the RDi, in which the developer works.", //$NON-NLS-1$
            LaunchProblems.unsavedFiles(Arrays.asList("/p/a.rpgle", "/p/b.rpgle"))); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testLaunchStarter() {
        assertEquals("RDi is probably waiting for an answer in a dialog: the start of \"iAgentBridge (de)\" did not return within the " //$NON-NLS-1$
            + "time. Until it is answered, start_launch starts nothing. Ask the developer to answer the dialog: the UI tools cannot reach " //$NON-NLS-1$
            + "the RDi, in which the developer works.", LaunchStarter.noReturn(DE)); //$NON-NLS-1$
        assertEquals(
            "The start of \"iAgentBridge (de)\" failed: java.lang.IllegalStateException: no delegate. Check the launch " //$NON-NLS-1$
                + "configuration, or ask the developer to start it by hand.", //$NON-NLS-1$
            LaunchStarter.failed(DE, new IllegalStateException("no delegate"))); //$NON-NLS-1$
        // A text from outside, that ends a sentence already, gets no second
        // period.
        assertEquals(
            "The start of \"iAgentBridge (de)\" failed: java.lang.IllegalStateException: Launch configuration is not valid. Check the " //$NON-NLS-1$
                + "launch configuration, or ask the developer to start it by hand.", //$NON-NLS-1$
            LaunchStarter.failed(DE, new IllegalStateException("Launch configuration is not valid."))); //$NON-NLS-1$
        assertEquals("RDi did not start \"iAgentBridge (de)\" (a dialog cancelled?). Ask the developer, which dialog RDi showed, then " //$NON-NLS-1$
            + "repeat start_launch.", LaunchStarter.notStarted(DE)); //$NON-NLS-1$
    }
}
