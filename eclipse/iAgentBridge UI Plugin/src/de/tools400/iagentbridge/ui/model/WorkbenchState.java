/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * State of the workbench at the top of a snapshot: active shell, focus,
 * perspective, views, editors, shells and an open native dialog. It has no SWT
 * types, so that the text of the snapshot can be tested outside of RDi.
 */
public final class WorkbenchState {

    private String activeShell;
    private String focus;
    private String perspectiveLabel;
    private String perspectiveId;
    private String activePart;
    private final List<Part> views = new ArrayList<Part>();
    private final List<Part> editors = new ArrayList<Part>();
    private final List<WidgetNode> shells = new ArrayList<WidgetNode>();
    private String nativeDialog;
    private boolean nativeDialogOpen;

    /**
     * Sets the title of the shell, that the operating system shows as active,
     * or <code>null</code>, if RDi is not in the foreground.
     */
    public void setActiveShell(String aTitle) {
        activeShell = aTitle;
    }

    /**
     * Sets the control with the focus, e.g. 'text [w12]', or <code>null</code>.
     */
    public void setFocus(String aFocus) {
        focus = aFocus;
    }

    public void setPerspective(String aLabel, String anId) {
        perspectiveLabel = aLabel;
        perspectiveId = anId;
    }

    public void setActivePart(String aName) {
        activePart = aName;
    }

    public void addView(String aTitle, String anId) {
        views.add(new Part(aTitle, anId, false));
    }

    public void addEditor(String aTitle, boolean aDirty) {
        editors.add(new Part(aTitle, null, aDirty));
    }

    /**
     * Adds a visible shell, as a node with title, reference and flags.
     */
    public void addShell(WidgetNode aShell) {
        shells.add(aShell);
    }

    /**
     * Records an open native dialog of the operating system, e.g. a file
     * dialog, which the snapshot cannot read.
     *
     * @param aTitle - title of the dialog or <code>null</code>, if it is not
     *        known
     */
    public void setNativeDialog(String aTitle) {
        nativeDialogOpen = true;
        nativeDialog = aTitle;
    }

    public String getActiveShell() {
        return activeShell;
    }

    public String getFocus() {
        return focus;
    }

    public String getPerspectiveLabel() {
        return perspectiveLabel;
    }

    public String getPerspectiveId() {
        return perspectiveId;
    }

    public String getActivePart() {
        return activePart;
    }

    public List<Part> getViews() {
        return Collections.unmodifiableList(views);
    }

    public List<Part> getEditors() {
        return Collections.unmodifiableList(editors);
    }

    public List<WidgetNode> getShells() {
        return Collections.unmodifiableList(shells);
    }

    public boolean isNativeDialogOpen() {
        return nativeDialogOpen;
    }

    public String getNativeDialog() {
        return nativeDialog;
    }

    /**
     * A view or an editor of the workbench page.
     */
    public static final class Part {

        private final String title;
        private final String id;
        private final boolean dirty;

        private Part(String aTitle, String anId, boolean aDirty) {
            title = aTitle;
            id = anId;
            dirty = aDirty;
        }

        public String getTitle() {
            return title;
        }

        /**
         * Returns the ID of a view.
         *
         * @return ID or <code>null</code> for an editor
         */
        public String getId() {
            return id;
        }

        /**
         * Returns whether an editor has unsaved changes.
         *
         * @return <code>true</code>, if the editor is dirty
         */
        public boolean isDirty() {
            return dirty;
        }
    }
}
