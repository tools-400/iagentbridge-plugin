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

import de.tools400.iagentbridge.ui.model.Hints;

/**
 * A hint follows a text, whose end comes from outside, e.g. an answer of
 * another program, as a sentence of its own.
 */
public class HintsTest {

    @Test
    public void testAfter_endsTheSentence() {
        assertEquals("HTTP 500: Internal error. Check it.", Hints.after("HTTP 500: Internal error", "Check it.")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    @Test
    public void testAfter_sentenceEndsAlready() {
        assertEquals("The file is missing. Check it.", Hints.after("The file is missing.", "Check it.")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        assertEquals("Still starting? Check it.", Hints.after("Still starting?", "Check it.")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        assertEquals("Stop! Check it.", Hints.after("Stop!", "Check it.")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    @Test
    public void testAfter_trailingSpace() {
        assertEquals("Error. Check it.", Hints.after("Error \n", "Check it.")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    /**
     * A text, whose variable end is empty, e.g. the body of an HTTP answer,
     * ends with a colon and a space.
     */
    @Test
    public void testAfter_colonAtTheEnd() {
        assertEquals("HTTP 404. Check it.", Hints.after("HTTP 404: ", "Check it.")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        assertEquals("Failed. Check it.", Hints.after("Failed; ", "Check it.")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        assertEquals("Failed. Check it.", Hints.after("Failed, ", "Check it.")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    /**
     * A colon without a space after it belongs to the text, e.g. a drive at the
     * end of the message of an exception.
     */
    @Test
    public void testAfter_colonOfTheText() {
        assertEquals("Cannot access C:. Check it.", Hints.after("Cannot access C:", "Check it.")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        assertEquals("Cannot access C:.", Hints.sentence("Cannot access C:")); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testSentence() {
        assertEquals("Terminated \"t\", because 'force' is true.", Hints.sentence("Terminated \"t\", because 'force' is true")); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("Terminated.", Hints.sentence("Terminated. ")); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("No JSON object.", Hints.sentence("No JSON object: ")); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /**
     * The hint names, what the developer shall do.
     */
    @Test
    public void testAskDeveloper() {
        assertEquals("Ask the developer to save them: the UI tools cannot reach the RDi, in which the developer works.", //$NON-NLS-1$
            Hints.askDeveloper("save them")); //$NON-NLS-1$
    }
}
