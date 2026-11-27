/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.launches;

import static org.junit.Assert.assertEquals;

import java.util.Map;

import org.junit.Test;

/**
 * The attributes, with which start_launch starts a working copy of a launch
 * configuration. The test lies in the package of the class, because it calls a
 * package visible method.
 */
public class LaunchStarterTest {

    @Test
    public void testWorkingCopyAttributes_noValidation() {

        Map<String, Boolean> attributes = LaunchStarter.workingCopyAttributes();

        // The check of the plug-ins of PDE shows a dialog in this RDi.
        assertEquals(Boolean.FALSE, attributes.get("automaticValidate")); //$NON-NLS-1$
        // PDE of RDi 9.6 and 9.9 takes the splash screen from config.ini, not
        // from the attribute showSplash: -nosplash suppresses it.
        assertEquals(1, attributes.size());
    }

    @Test
    public void testProgramArguments_noSplash() {
        // The splash screen takes the focus of the operating system.
        assertEquals("-nosplash", LaunchStarter.programArguments(null)); //$NON-NLS-1$
        assertEquals("-nosplash", LaunchStarter.programArguments(" ")); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("-nosplash -os win32 -nl de", LaunchStarter.programArguments("-os win32 -nl de")); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testProgramArguments_noSplashAlready() {
        assertEquals("-nosplash -nl de", LaunchStarter.programArguments("-nosplash -nl de")); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("-nl de -nosplash", LaunchStarter.programArguments("-nl de -nosplash")); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testProgramArguments_similarArgumentIsNoSplash() {
        assertEquals("-nosplash -nosplashx", LaunchStarter.programArguments("-nosplashx")); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /**
     * In front, an open quote or -vmargs in the program arguments does not
     * swallow it.
     */
    @Test
    public void testProgramArguments_inFront() {
        assertEquals("-nosplash -data \"C:\\my ws", LaunchStarter.programArguments("-data \"C:\\my ws")); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("-nosplash -nl de -vmargs -Xmx1g", LaunchStarter.programArguments("-nl de -vmargs -Xmx1g")); //$NON-NLS-1$ //$NON-NLS-2$
    }
}
