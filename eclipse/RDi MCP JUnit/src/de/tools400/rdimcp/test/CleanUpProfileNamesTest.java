/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.rdimcp.test;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import de.tools400.rdimcp.tools.CleanUpProfileNames;

public class CleanUpProfileNamesTest {

    private static final String ISPHERE_PROFILE = "_iSphere Clean Up Profile (Helios)";

    @Test
    public void testDescribe_projectSettings() {
        assertEquals("iSphere Clean Up Profile (Helios) (project settings)", CleanUpProfileNames.describe(ISPHERE_PROFILE, ISPHERE_PROFILE));
        assertEquals("Other (project settings)", CleanUpProfileNames.describe("_Other", ISPHERE_PROFILE));
    }

    @Test
    public void testDescribe_workspaceSettings() {
        assertEquals("iSphere Clean Up Profile (Helios) (workspace settings)", CleanUpProfileNames.describe(null, ISPHERE_PROFILE));
    }

    @Test
    public void testDescribe_builtInProfile() {
        assertEquals("Eclipse [built-in] (workspace settings)",
            CleanUpProfileNames.describe(null, "org.eclipse.jdt.ui.default.eclipse_clean_up_profile"));
        assertEquals("Eclipse [built-in] (project settings)",
            CleanUpProfileNames.describe("org.eclipse.jdt.ui.default.eclipse_clean_up_profile", null));
    }

    @Test
    public void testDescribe_default() {
        assertEquals("Eclipse [built-in] (default)", CleanUpProfileNames.describe(null, null));
    }

    @Test
    public void testDescribe_unknownProfileId() {
        assertEquals("some.profile (workspace settings)", CleanUpProfileNames.describe(null, "some.profile"));
    }
}
