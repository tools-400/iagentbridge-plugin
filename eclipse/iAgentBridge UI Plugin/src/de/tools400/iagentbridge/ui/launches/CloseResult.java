/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.launches;

import java.util.List;

import de.tools400.iagentbridge.mcp.McpToolResult;

/**
 * The result of ui_close, which the test instance returns and stop_launch
 * decides on: closing, or unsaved changes.
 */
public final class CloseResult {

    /** Start of the result, if a part has unsaved changes */
    private static final String UNSAVED = "Unsaved changes in the test instance: "; //$NON-NLS-1$

    private CloseResult() {
    }

    /**
     * Returns the result of ui_close.
     *
     * @param anUnsaved - the parts with unsaved changes, e.g. 'editor
     *        hello.rpgle'
     * @return the result
     */
    public static McpToolResult of(List<String> anUnsaved) {

        if (anUnsaved.isEmpty()) {
            return McpToolResult.success("Closing the workbench of the test instance."); //$NON-NLS-1$
        }

        return McpToolResult.error(UNSAVED + String.join(", ", anUnsaved) + ". Save or revert them, or stop it with force."); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /**
     * Returns whether a result of ui_close says, that parts have unsaved
     * changes.
     *
     * @param aResult - result of ui_close
     * @return <code>true</code>, if ui_close did not close because of unsaved
     *         changes
     */
    public static boolean isUnsaved(McpToolResult aResult) {
        // A forwarded result starts with a line, that names the test instance.
        return aResult.isError() && (aResult.getText().startsWith(UNSAVED) || aResult.getText().contains("\n" + UNSAVED)); //$NON-NLS-1$
    }
}
