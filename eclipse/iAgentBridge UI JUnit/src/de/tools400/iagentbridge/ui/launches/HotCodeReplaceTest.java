/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.launches;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * The text of the results of hot code replace, that belong to one replacement.
 * The test lies in the package of the class, because it calls a package visible
 * method.
 */
public class HotCodeReplaceTest {

    @Test
    public void testCombine() {
        assertEquals("succeeded", HotCodeReplace.combine(null, "succeeded")); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("obsolete methods, succeeded", HotCodeReplace.combine("obsolete methods", "succeeded")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        // Replacements, that follow each other quickly, do not let the text
        // grow.
        assertEquals("obsolete methods, succeeded", HotCodeReplace.combine("obsolete methods, succeeded", "obsolete methods")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        assertEquals("obsolete methods, succeeded", HotCodeReplace.combine("obsolete methods, succeeded", "succeeded")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        assertEquals("succeeded, failed", HotCodeReplace.combine("succeeded", "failed")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }
}
