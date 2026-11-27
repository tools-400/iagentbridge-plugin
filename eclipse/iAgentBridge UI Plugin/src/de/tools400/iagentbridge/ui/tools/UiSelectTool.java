/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.tools;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CCombo;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import de.tools400.iagentbridge.mcp.ToolArguments;
import de.tools400.iagentbridge.ui.model.Deadline;
import de.tools400.iagentbridge.ui.model.ItemPath;
import de.tools400.iagentbridge.ui.swt.ActionRejectedException;
import de.tools400.iagentbridge.ui.swt.ActionTarget;
import de.tools400.iagentbridge.ui.swt.ItemFinder;
import de.tools400.iagentbridge.ui.swt.UiThread;
import de.tools400.iagentbridge.ui.swt.WidgetEvents;

/**
 * Tool 'ui_select': selects entries of a combo or a list, rows of a table or
 * items of a tree by their texts; in a tree by paths, whose levels are expanded
 * one after the other.
 */
public class UiSelectTool extends AbstractActionTool {

    private static final String DESCRIPTION = "Selects by their texts in the test instance of RDi: an entry of a combo (also a read-only " //$NON-NLS-1$
        + "one), entries of a list, rows of a table (text of the first column) or items of a tree by paths like 'General > Editors': " //$NON-NLS-1$
        + "each level of a path is expanded, and the tool waits, until the next item of the path is there, as viewers like the one of " //$NON-NLS-1$
        + "Remote Systems create children later; for a missing item it waits up to 20 s. Several values only, if the widget allows several " //$NON-NLS-1$
        + "selections. In a virtual table only " //$NON-NLS-1$
        + "the visible rows are searched; otherwise use ui_click with the reference of a row." //$NON-NLS-1$
        + ACTION_SUFFIX;

    private static final String INPUT_SCHEMA = "{\"type\":\"object\",\"properties\":{" //$NON-NLS-1$
        + "\"ref\":{\"type\":\"string\",\"description\":\"Reference of the combo, list, table or tree from a snapshot, e.g. w17.\"}," //$NON-NLS-1$
        + "\"values\":{\"type\":\"array\",\"items\":{\"type\":\"string\"},\"description\":\"Texts of the entries, rows or paths of tree " //$NON-NLS-1$
        + "items, e.g. ['General > Editors']; in a path of a tree '\\\\>' is a '>' in a text.\"}," //$NON-NLS-1$
        + SNAPSHOT_PROPERTY + "},\"required\":[\"ref\",\"values\"]}"; //$NON-NLS-1$

    /** Time between two searches for the next item of a path */
    private static final long POLL_MILLIS = 100;

    @Override
    public String getName() {
        return "ui_select"; //$NON-NLS-1$
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
    protected Action prepare(JsonObject anArguments) {

        String ref = UiArguments.getRef(anArguments);
        List<String> values = ToolArguments.getStringList(anArguments, "values"); //$NON-NLS-1$

        return new Action() {

            @Override
            public void prelude(ActionEnvironment anEnvironment) throws Exception {
                expandPaths(ref, values, anEnvironment);
            }

            @Override
            public String perform(ActionContext aContext) throws Exception {
                return select(aContext, ref, values);
            }
        };
    }

    /**
     * Returns the values as paths of tree items. Only the values for a tree are
     * paths; the entries of other widgets may contain a '>'.
     */
    private static List<List<String>> paths(List<String> aValues) {

        List<List<String>> paths = new ArrayList<List<String>>();
        for (String value : aValues) {
            paths.add(ItemPath.parse(value));
        }

        return paths;
    }

    /**
     * Expands the levels of the paths of a tree, one step for each level, and
     * waits between them for the next item of the path. For other widgets it
     * does nothing.
     */
    private static void expandPaths(String aRef, List<String> aValues, ActionEnvironment anEnvironment) throws Exception {

        Executor ui = anEnvironment.getUi();
        Deadline deadline = anEnvironment.getDeadline();
        boolean tree = UiThread.read(ui, deadline, () -> Boolean.valueOf(ActionTarget.resolve(aRef).getWidget() instanceof Tree)).booleanValue();
        if (!tree) {
            return;
        }

        for (List<String> path : paths(aValues)) {
            for (int level = 0; level < path.size(); level++) {
                List<String> part = path.subList(0, level + 1);
                awaitItem(aRef, part, ui, deadline);
                if (level < path.size() - 1) {
                    step(anEnvironment, "Expanding '" + part.get(level) + "'", () -> { //$NON-NLS-1$ //$NON-NLS-2$
                        ActionTarget target = ActionTarget.resolve(aRef);
                        target.check(false);
                        TreeItem item = ItemFinder.treeItem((Tree)target.getWidget(), part);
                        if (item == null) {
                            throw new ActionRejectedException(ItemFinder.missingTreeItem((Tree)target.getWidget(), part));
                        }
                        WidgetEvents.expand(item);
                        return null;
                    });
                }
            }
        }
    }

    /**
     * Waits, until the item of a path is there; the UI thread is free between
     * the searches, so that a viewer can create it.
     */
    private static void awaitItem(String aRef, List<String> aPath, Executor anUi, Deadline aDeadline) throws Exception {

        while (true) {
            String missing = UiThread.read(anUi, aDeadline, () -> {
                TreeItem item = ItemFinder.treeItem((Tree)ActionTarget.resolve(aRef).getWidget(), aPath);
                return item == null ? ItemFinder.missingTreeItem((Tree)ActionTarget.resolve(aRef).getWidget(), aPath) : null;
            });
            if (missing == null) {
                return;
            }
            if (aDeadline.remainingMillis() <= POLL_MILLIS) {
                throw new ActionRejectedException(missing);
            }
            Thread.sleep(POLL_MILLIS);
        }
    }

    private static String select(ActionContext aContext, String aRef, List<String> aValues) throws Exception {

        ActionTarget target = ActionTarget.resolve(aRef);
        Control control = target.getControl();
        if (target.getWidget() != control) {
            throw new IllegalArgumentException(target.describe() + " is an item. Give the reference of its combo, list, table or tree."); //$NON-NLS-1$
        }
        boolean several = aValues.size() > 1;
        if (several && (control instanceof Combo || control instanceof CCombo || (control.getStyle() & SWT.MULTI) == 0)) {
            throw new IllegalArgumentException(target.describe() + " allows one selection only."); //$NON-NLS-1$
        }

        if (control instanceof Combo || control instanceof CCombo) {
            String[] items = control instanceof Combo ? ((Combo)control).getItems() : ((CCombo)control).getItems();
            int index = indexOf(items, aValues.get(0), target);
            target.check(false);
            aContext.record(target);
            if (control instanceof Combo) {
                WidgetEvents.selectCombo((Combo)control, index);
            } else {
                WidgetEvents.selectCCombo((CCombo)control, index);
            }
        } else if (control instanceof org.eclipse.swt.widgets.List) {
            org.eclipse.swt.widgets.List list = (org.eclipse.swt.widgets.List)control;
            int[] indices = new int[aValues.size()];
            for (int i = 0; i < indices.length; i++) {
                indices[i] = indexOf(list.getItems(), aValues.get(i), target);
            }
            target.check(false);
            aContext.record(target);
            WidgetEvents.selectList(list, indices);
        } else if (control instanceof Table) {
            List<TableItem> rows = new ArrayList<TableItem>();
            for (String value : aValues) {
                rows.add(ItemFinder.row((Table)control, value));
            }
            target.check(false);
            aContext.record(target);
            for (int i = 0; i < rows.size(); i++) {
                WidgetEvents.selectTableItem(rows.get(i), i > 0);
            }
        } else if (control instanceof Tree) {
            List<TreeItem> items = new ArrayList<TreeItem>();
            for (List<String> path : paths(aValues)) {
                TreeItem item = ItemFinder.treeItem((Tree)control, path);
                if (item == null) {
                    throw new ActionRejectedException(ItemFinder.missingTreeItem((Tree)control, path));
                }
                items.add(item);
            }
            target.check(false);
            // The tree is described before the events, which may dispose it.
            target.describe();
            aContext.record(ActionTarget.of(items.get(0)));
            for (int i = 0; i < items.size(); i++) {
                WidgetEvents.selectTreeItem(items.get(i), i > 0);
            }
        } else {
            throw new IllegalArgumentException(noEntries(target.describe()));
        }

        return "Selected " + ItemFinder.listed(aValues) + " in " + target.describe() + "."; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    /**
     * Returns the text for a target without entries to select.
     *
     * @param aDescription - the description of the target
     * @return text
     */
    static String noEntries(String aDescription) {
        return aDescription + " has no entries to select. ui_select works on combos, lists, tables and trees. Use ui_type for text " //$NON-NLS-1$
            + "fields, ui_click for buttons, tabs and links."; //$NON-NLS-1$
    }

    private static int indexOf(String[] anItems, String aValue, ActionTarget aTarget) {
        int index = Arrays.asList(anItems).indexOf(aValue);
        if (index < 0) {
            throw new IllegalArgumentException(
                "No entry '" + aValue + "' in " + aTarget.describe() + ". It has: " + ItemFinder.listed(Arrays.asList(anItems)) + "."); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        }
        return index;
    }

}
