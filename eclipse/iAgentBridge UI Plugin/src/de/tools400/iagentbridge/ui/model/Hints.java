/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.model;

/**
 * Hints, that several error texts of the UI tools and the launch tools share:
 * they tell the AI agent, what to do next.
 */
public final class Hints {

    /** How to check, whether an action ran */
    private static final String CHECK_RESULT = "check its result in a snapshot before you repeat it."; //$NON-NLS-1$

    /**
     * After a call, that may have run in the test instance, although no answer
     * came
     */
    public static final String CHECK_BEFORE_REPEATING = "If it was an action, it may have run: " + CHECK_RESULT; //$NON-NLS-1$

    /** After an action, that failed, while it ran */
    public static final String RAN_IN_PART = "The action may have run in part: " + CHECK_RESULT; //$NON-NLS-1$

    /** After the end of the workbench or the display of the test instance */
    public static final String INSTANCE_RUNS = "list_launches shows, whether the test instance runs; start_launch starts it again."; //$NON-NLS-1$

    /** When the test instance does not accept connections */
    public static final String NOT_SENT = "Nothing was sent: repeat it in a few seconds; list_launches shows, whether the test instance runs."; //$NON-NLS-1$

    /** When the port answers, but not like a test instance with the UI tools */
    public static final String CHECK_PORT = "Check -Diagentbridge.port of its launch configuration: the port may belong to another program."; //$NON-NLS-1$

    /**
     * When another process uses a port or a workspace: a launch, that the agent
     * did not start, belongs to the developer.
     */
    public static final String FOREIGN_PROCESS = "list_launches shows, whether a launch uses it. Stop such a launch with stop_launch only, " //$NON-NLS-1$
        + "if you started it; otherwise ask the developer to end it or the other process."; //$NON-NLS-1$

    private Hints() {
    }

    /**
     * Appends a hint to a text, whose end comes from outside, e.g. the answer
     * of another program, so that the hint is a sentence of its own, see
     * {@link #sentence(String)}.
     *
     * @param aText - the text
     * @param aHint - the hint
     * @return text and hint
     */
    public static String after(String aText, String aHint) {
        String text = sentence(aText);
        return text.isEmpty() ? aHint : text + " " + aHint; //$NON-NLS-1$
    }

    /**
     * Ends a text as a sentence: white space at its end is removed, together
     * with a colon, semicolon or comma before it, e.g. of a text, whose
     * variable end is empty; a colon without white space after it belongs to
     * the text, e.g. a drive. A period is added, unless the text ends a
     * sentence already.
     *
     * @param aText - the text
     * @return the sentence, or an empty string
     */
    public static String sentence(String aText) {

        String text = aText.replaceAll("(?:[:;,]?\\s+)+$", ""); //$NON-NLS-1$ //$NON-NLS-2$
        if (text.isEmpty()) {
            return text;
        }

        char last = text.charAt(text.length() - 1);
        return last == '.' || last == '?' || last == '!' ? text : text + "."; //$NON-NLS-1$
    }

    /**
     * Returns the hint for what must be done in the RDi, in which the developer
     * works.
     *
     * @param anAction - what the developer shall do, e.g. 'answer the dialog'
     * @return hint
     */
    public static String askDeveloper(String anAction) {
        return "Ask the developer to " + anAction + ": the UI tools cannot reach the RDi, in which the developer works."; //$NON-NLS-1$ //$NON-NLS-2$
    }
}
