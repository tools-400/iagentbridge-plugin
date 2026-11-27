/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.swt;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * The error texts of the classes, that act on widgets, with the hint, what the
 * AI agent does next. The test lies in the package of the classes, because it
 * calls their package visible methods; no widget and no display is created.
 */
public class SwtHintsTest {

    private static final String BUTTON = "button \"OK\" [w20]"; //$NON-NLS-1$

    @Test
    public void testActionTarget() {
        assertEquals("Ref w17 is a TableColumn, which no action can use. Give the reference of a control, a row, an item, a tab or a tool " //$NON-NLS-1$
            + "item.", ActionTarget.noActionTarget("w17", "TableColumn")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        assertEquals("button \"OK\" [w20] is not visible. Make it visible first, e.g. select its tab or expand its section; then take a new " //$NON-NLS-1$
            + "snapshot.", ActionTarget.notVisible(BUTTON)); //$NON-NLS-1$
        assertEquals("button \"OK\" [w20] is disabled. Do first, what enables it, e.g. fill in a required field or select an element; then " //$NON-NLS-1$
            + "take a new snapshot.", ActionTarget.disabled(BUTTON)); //$NON-NLS-1$
    }

    @Test
    public void testMenuWalker() {
        assertEquals("The context menu of tree [w5] was vetoed. Give the reference of a row or an item, or use ui_command.", //$NON-NLS-1$
            MenuWalker.vetoed("tree [w5]", false)); //$NON-NLS-1$
        assertEquals("The context menu of tree item \"src\" [w6] was vetoed. Use the menu bar (ui_menu without 'ref') or ui_command.", //$NON-NLS-1$
            MenuWalker.vetoed("tree item \"src\" [w6]", true)); //$NON-NLS-1$
        assertEquals("tree [w5] has no context menu. Use the menu bar (ui_menu without 'ref') or ui_command.", //$NON-NLS-1$
            MenuWalker.noContextMenu("tree [w5]")); //$NON-NLS-1$
        assertEquals("The menu item 'Delete' is disabled. It may depend on the selection or the active part: select the right element " //$NON-NLS-1$
            + "first, then repeat.", MenuWalker.disabledItem("Delete")); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testWidgetEvents() {
        assertEquals("The link has no hyperlink <a>...</a>. Nothing was clicked; use another widget of the snapshot.", //$NON-NLS-1$
            WidgetEvents.noHyperlink());
    }

    @Test
    public void testLpexEdits() {
        assertEquals("The document \"HELLO.RPGLE\" is read-only; no text was inserted. If it was opened for browsing, open the source for " //$NON-NLS-1$
            + "editing instead and repeat; otherwise ask the developer, why it is read-only.", LpexEdits.readOnly("HELLO.RPGLE")); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testUiThread() {
        assertEquals("The display of the test instance has ended. list_launches shows, whether the test instance runs; start_launch " //$NON-NLS-1$
            + "starts it again.", UiThread.displayEnded()); //$NON-NLS-1$
    }

    @Test
    public void testListenerFailed() {
        assertEquals(
            "A listener of the action failed: java.lang.IllegalStateException: listener failed. The action may have run in part: " //$NON-NLS-1$
                + "check its result in a snapshot before you repeat it.", //$NON-NLS-1$
            new ListenerFailedException(new IllegalStateException("listener failed.")).getMessage()); //$NON-NLS-1$
    }
}
