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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import de.tools400.iagentbridge.mcp.McpToolResult;
import de.tools400.iagentbridge.mcp.ToolArguments;
import de.tools400.iagentbridge.ui.model.SnapshotWriter;
import de.tools400.iagentbridge.ui.model.WidgetNode;
import de.tools400.iagentbridge.ui.model.WorkbenchState;
import de.tools400.iagentbridge.ui.swt.ShellPicker;
import de.tools400.iagentbridge.ui.swt.UiThread;
import de.tools400.iagentbridge.ui.swt.UiThreadBusyException;
import de.tools400.iagentbridge.ui.swt.WidgetModelBuilder;
import de.tools400.iagentbridge.ui.swt.WidgetRefs;
import de.tools400.iagentbridge.ui.swt.WorkbenchStateBuilder;

/**
 * Tool 'ui_snapshot': the state of the workbench and the widget tree of a shell
 * of the test instance as indented text, with references for the other UI tools
 * and locators for SWTBot tests.
 */
public class UiSnapshotTool extends AbstractUiTool {

    private static final String DESCRIPTION = "Returns the user interface of the test instance of RDi as indented text: first the " //$NON-NLS-1$
        + "workbench (active shell, focus, perspective, views, editors with 'dirty', shells, an open native dialog), then the widget tree " //$NON-NLS-1$
        + "of the active shell, one line per widget: type, text, reference like [w17], label, value, state (disabled, selected, focus, " //$NON-NLS-1$
        + "default, ...), a locator in the style of SWTBot and the tooltip. Tables show their columns and the first 50 rows, trees their " //$NON-NLS-1$
        + "expanded items, the LPEX editor its name, number of lines and cursor. A reference stays valid, as long as the widget lives." //$NON-NLS-1$
        + DESCRIPTION_SUFFIX;

    private static final String INPUT_SCHEMA = "{\"type\":\"object\",\"properties\":{" //$NON-NLS-1$
        + "\"shell\":{\"type\":\"string\",\"description\":\"Reference or title of a shell, or 'all' for all visible shells. " //$NON-NLS-1$
        + "Default: the active shell.\"}," //$NON-NLS-1$
        + "\"ref\":{\"type\":\"string\",\"description\":\"Reference of a widget, e.g. w17: only its sub tree.\"}," //$NON-NLS-1$
        + "\"maxNodes\":{\"type\":\"integer\",\"description\":\"Number of widgets, after which the snapshot is cut. Default: 400.\"}," //$NON-NLS-1$
        + "\"toolItems\":{\"type\":\"boolean\",\"description\":\"Also show the tool bars and their items. Default: false.\"}}}"; //$NON-NLS-1$

    private static final int DEFAULT_MAX_NODES = 400;

    @Override
    public String getName() {
        return "ui_snapshot"; //$NON-NLS-1$
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    public JsonObject getInputSchema() {
        return new JsonParser().parse(INPUT_SCHEMA).getAsJsonObject();
    }

    @Override
    protected McpToolResult executeInTestInstance(JsonObject anArguments) throws Exception {

        String shell = UiArguments.getOptionalString(anArguments, "shell"); //$NON-NLS-1$
        String ref = UiArguments.getOptionalString(anArguments, "ref"); //$NON-NLS-1$
        UiArguments.checkShellOrRef(shell, ref);
        int maxNodes = ToolArguments.getInt(anArguments, "maxNodes", DEFAULT_MAX_NODES, 1); //$NON-NLS-1$
        boolean toolItems = UiArguments.getBoolean(anArguments, "toolItems", false); //$NON-NLS-1$

        Display display = getDisplay();
        Snapshot snapshot;
        try {
            snapshot = UiThread.read(display, UI_TIMEOUT_SECONDS, () -> take(display, shell, ref));
        } catch (UiThreadBusyException e) {
            return busy(e);
        }

        return McpToolResult.success(new SnapshotWriter(maxNodes, toolItems).write(snapshot.state, snapshot.roots));
    }

    /**
     * Takes the snapshot in the UI thread.
     */
    private static Snapshot take(Display aDisplay, String aShell, String aRef) {

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
    private static final class Snapshot {

        private final WorkbenchState state;
        private final List<WidgetNode> roots;

        private Snapshot(WorkbenchState aState, List<WidgetNode> aRoots) {
            state = aState;
            roots = aRoots;
        }
    }
}
