/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.test;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import de.tools400.iagentbridge.ui.model.LpexLines;

/**
 * The lines of the LPEX editor, that ui_lpex returns: numbers as wide as the
 * last line, the cursor line marked with '>', notes as they are.
 */
public class LpexLinesTest {

    @Test
    public void testLines() {
        LpexLines lines = new LpexLines("LPEX \"HELLO.RPGLE\" [w12]: 120 lines", 120, 99);
        lines.add(98, "       dcl-s name char(10);");
        lines.add(99, "");
        lines.add(100, "       *inlr = *on;");
        lines.addNote("... 20 more lines");

        assertEquals("LPEX \"HELLO.RPGLE\" [w12]: 120 lines\n" //
            + " 98 |        dcl-s name char(10);\n" //
            + " 99 >\n" //
            + "100 |        *inlr = *on;\n" //
            + "... 20 more lines\n", lines.toString());
    }

    @Test
    public void testCursorOutside() {
        LpexLines lines = new LpexLines("LPEX \"A\" [w1]: 2 lines", 2, 5);
        lines.add(1, "first");
        lines.add(2, "second");

        assertEquals("LPEX \"A\" [w1]: 2 lines\n1 | first\n2 | second\n", lines.toString());
    }
}
