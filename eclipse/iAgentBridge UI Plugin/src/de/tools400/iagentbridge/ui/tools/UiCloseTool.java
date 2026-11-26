/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.tools;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.ISaveablePart;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IViewReference;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import de.tools400.iagentbridge.mcp.McpToolResult;
import de.tools400.iagentbridge.ui.launches.CloseResult;
import de.tools400.iagentbridge.ui.swt.OpenDialogs;
import de.tools400.iagentbridge.ui.swt.UiThread;
import de.tools400.iagentbridge.ui.swt.UiThreadBusyException;

/**
 * Tool 'ui_close': closes the workbench of the test instance, unless an editor
 * or a view has unsaved changes. stop_launch calls it and waits for the end of
 * the test instance.
 */
public class UiCloseTool extends AbstractUiTool {

    private static final String DESCRIPTION = "Closes the workbench of the test instance of RDi without asking, after a check, that no " //$NON-NLS-1$
        + "editor or view has unsaved changes; otherwise it names them and does nothing. Open dialogs, e.g. a sign-on, are closed " //$NON-NLS-1$
        + "first like with Cancel. Returns at once; the test instance ends a few seconds later. stop_launch does the same and waits for " //$NON-NLS-1$
        + "the end." //$NON-NLS-1$
        + DESCRIPTION_SUFFIX;

    @Override
    public String getName() {
        return "ui_close"; //$NON-NLS-1$
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    public JsonObject getInputSchema() {
        return new JsonParser().parse("{\"type\":\"object\",\"properties\":{}}").getAsJsonObject(); //$NON-NLS-1$
    }

    @Override
    protected McpToolResult executeInTestInstance(JsonObject anArguments) throws Exception {

        Display display = getDisplay();
        List<String> unsaved;
        try {
            unsaved = UiThread.read(display, UI_TIMEOUT_SECONDS, UiCloseTool::unsavedParts);
        } catch (UiThreadBusyException e) {
            return busy(e);
        }

        if (unsaved.isEmpty()) {
            display.asyncExec(() -> {
                // IWorkbench.close() would wait in an event loop for the
                // operation of an open dialog, e.g. a sign-on, which waits for
                // the dialog: cancel the dialogs first.
                OpenDialogs.close(display);
                display.asyncExec(() -> PlatformUI.getWorkbench().close());
            });
        }

        // A failure of an earlier action would end with the test instance.
        String earlier = takePendingFailures();
        McpToolResult result = CloseResult.of(unsaved);
        return result.isError() ? McpToolResult.error(earlier + result.getText()) : McpToolResult.success(earlier + result.getText());
    }

    /**
     * Returns the editors and the views with unsaved changes. Runs in the UI
     * thread.
     */
    private static List<String> unsavedParts() {

        List<String> unsaved = new ArrayList<String>();
        for (IWorkbenchWindow window : PlatformUI.getWorkbench().getWorkbenchWindows()) {
            for (IWorkbenchPage page : window.getPages()) {
                for (IEditorPart editor : page.getDirtyEditors()) {
                    unsaved.add("editor " + editor.getTitle()); //$NON-NLS-1$
                }
                for (IViewReference reference : page.getViewReferences()) {
                    IViewPart view = reference.getView(false);
                    if (view instanceof ISaveablePart && ((ISaveablePart)view).isDirty()) {
                        unsaved.add("view " + reference.getPartName()); //$NON-NLS-1$
                    }
                }
            }
        }

        return unsaved;
    }
}
