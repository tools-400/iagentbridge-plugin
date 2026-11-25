/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.swt;

import java.util.List;

import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Dialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IPerspectiveDescriptor;
import org.eclipse.ui.IViewReference;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

import de.tools400.iagentbridge.ui.model.WidgetNode;
import de.tools400.iagentbridge.ui.model.WorkbenchState;

/**
 * Collects the state of the workbench for the top of a snapshot. Used in the UI
 * thread only.
 */
public final class WorkbenchStateBuilder {

    private WorkbenchStateBuilder() {
    }

    /**
     * Collects the state.
     *
     * @param aDisplay - the display
     * @return state of the workbench
     */
    public static WorkbenchState build(Display aDisplay) {

        WorkbenchState state = new WorkbenchState();

        Shell activeShell = aDisplay.getActiveShell();
        state.setActiveShell(activeShell == null ? null : activeShell.getText());

        Control focus = aDisplay.getFocusControl();
        if (focus != null) {
            state.setFocus(WidgetModelBuilder.typeName(focus) + " [" + WidgetRefs.of(focus) + "]"); //$NON-NLS-1$ //$NON-NLS-2$
        }

        IWorkbenchPage page = getActivePage();
        if (page != null) {
            IPerspectiveDescriptor perspective = page.getPerspective();
            if (perspective != null) {
                state.setPerspective(perspective.getLabel(), perspective.getId());
            }
            IWorkbenchPartReference activePart = page.getActivePartReference();
            state.setActivePart(activePart == null ? null : activePart.getPartName());
            for (IViewReference view : page.getViewReferences()) {
                state.addView(view.getPartName(), view.getId());
            }
            for (IEditorReference editor : page.getEditorReferences()) {
                state.addEditor(editor.getPartName(), editor.isDirty());
            }
        }

        List<Shell> shells = ShellPicker.visibleShells(aDisplay);
        for (Shell shell : shells) {
            WidgetNode node = WidgetNode.widget("shell", shell.getText(), WidgetRefs.of(shell)); //$NON-NLS-1$
            if (ShellPicker.isModal(shell)) {
                node.addFlag("modal"); //$NON-NLS-1$
            }
            state.addShell(node);
        }

        Dialog nativeDialog = NativeDialogs.find(aDisplay);
        if (nativeDialog != null) {
            state.setNativeDialog(nativeDialog.getText());
        }

        return state;
    }

    /**
     * Returns the active page of the active workbench window, or of the first
     * one, when RDi is not in the foreground.
     */
    private static IWorkbenchPage getActivePage() {

        IWorkbench workbench = PlatformUI.getWorkbench();
        IWorkbenchWindow window = workbench.getActiveWorkbenchWindow();
        if (window == null && workbench.getWorkbenchWindowCount() > 0) {
            window = workbench.getWorkbenchWindows()[0];
        }

        return window == null ? null : window.getActivePage();
    }
}
