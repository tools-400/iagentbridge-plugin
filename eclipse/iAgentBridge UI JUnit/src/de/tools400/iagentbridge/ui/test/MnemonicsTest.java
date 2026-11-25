/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import de.tools400.iagentbridge.ui.model.Mnemonics;

public class MnemonicsTest {

    @Test
    public void testRemove_mnemonic() {
        assertEquals("File", Mnemonics.remove("&File"));
        assertEquals("Port:", Mnemonics.remove("P&ort:"));
        assertEquals("OK", Mnemonics.remove("OK"));
        assertEquals("", Mnemonics.remove(""));
        assertNull(Mnemonics.remove(null));
    }

    @Test
    public void testRemove_ampersand() {
        assertEquals("Save & Close", Mnemonics.remove("Save && &Close"));
        assertEquals("Search & Replace", Mnemonics.remove("Search && Replace"));
        assertEquals("&", Mnemonics.remove("&&"));
        assertEquals("a&&b", Mnemonics.remove("a&&&&b"));
        assertEquals("&x", Mnemonics.remove("&&&x"));
    }
}
