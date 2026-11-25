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

import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Widget;

import de.tools400.iagentbridge.ui.model.SnapshotWriter;
import de.tools400.iagentbridge.ui.model.WidgetNode;
import de.tools400.iagentbridge.ui.model.WorkbenchState;
import de.tools400.iagentbridge.ui.swt.ShellPicker;
import de.tools400.iagentbridge.ui.swt.WidgetModelBuilder;
import de.tools400.iagentbridge.ui.swt.WidgetRefs;
import de.tools400.iagentbridge.ui.swt.WorkbenchStateBuilder;

/**
 * Snapshots of the user interface, for 'ui_snapshot' and after each action: the
 * model is taken in the UI thread, the text is written outside of it.
 */
public final class Snapshots {

    /** Number of widgets, after which a snapshot is cut, by default */
    public static final int DEFAULT_MAX_NODES = 400;

    private Snapshots() {
    }

    /**
     * Takes the model of a snapshot. Runs in the UI thread.
     *
     * @param aDisplay - the display
     * @param aShell - reference or title of a shell, 'all' or <code>null</code>
     *        for the active shell
     * @param aRef - reference of a control, whose sub tree is taken, or
     *        <code>null</code>
     * @return the model
     */
    public static Snapshot take(Display aDisplay, String aShell, String aRef) {

        WidgetRefs.purge();
        WorkbenchState state = WorkbenchStateBuilder.build(aDisplay);
        WidgetModelBuilder builder = new WidgetModelBuilder(aDisplay.getFocusControl());

        List<WidgetNode> roots = new ArrayList<WidgetNode>();
        if (aRef != null) {
            Widget widget = WidgetRefs.get(aRef);
            if (!(widget instanceof Control)) {
                throw new IllegalArgumentException("Ref " + aRef + " is an item, not a widget. Use the reference of its tree, table or folder."); //$NON-NLS-1$ //$NON-NLS-2$
            }
            roots.add(builder.build((Control)widget));
        } else if (ShellPicker.ALL.equals(aShell)) {
            for (Shell shell : ShellPicker.visibleShells(aDisplay)) {
                roots.add(builder.build(shell));
            }
        } else {
            Shell shell = aShell == null ? ShellPicker.activeShell(aDisplay) : ShellPicker.find(aDisplay, aShell);
            if (shell == null) {
                roots.add(WidgetNode.note("no visible shell")); //$NON-NLS-1$
            } else {
                roots.add(builder.build(shell));
            }
        }

        return new Snapshot(state, roots);
    }

    /**
     * The model of a snapshot.
     */
    public static final class Snapshot {

        private final WorkbenchState state;
        private final List<WidgetNode> roots;

        private Snapshot(WorkbenchState aState, List<WidgetNode> aRoots) {
            state = aState;
            roots = aRoots;
        }

        /**
         * Writes the snapshot as text and remembers the texts of the items,
         * that it shows, so that an action can check them. Runs outside of the
         * UI thread.
         *
         * @param aMaxNodes - number of widgets, after which the text is cut
         * @param aToolItems - <code>true</code>, to show the tool bars
         * @return text
         */
        public String write(int aMaxNodes, boolean aToolItems) {
            SnapshotWriter writer = new SnapshotWriter(aMaxNodes, aToolItems);
            String text = writer.write(state, roots);
            WidgetRefs.remember(writer.getShownItems());
            return text;
        }
    }
}
