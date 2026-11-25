/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.tools;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Item;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.swt.widgets.Widget;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import de.tools400.iagentbridge.ui.swt.ActionTarget;
import de.tools400.iagentbridge.ui.swt.WidgetEvents;

/**
 * Tool 'ui_click': clicks a button, a link, a tab, a tool item, a row of a
 * table or an item of a tree.
 */
public class UiClickTool extends AbstractActionTool {

    private static final String DESCRIPTION = "Clicks a widget of the test instance of RDi like the left mouse button: a push button, " //$NON-NLS-1$
        + "a checkbox, radio or toggle button (like a real click: it changes or selects it), a link (its first hyperlink), a tab, a tool " //$NON-NLS-1$
        + "item, a row of a table or an item of a tree (selects it; with 'double' it opens it like a double click). For context menus use " //$NON-NLS-1$
        + "ui_menu with 'ref', for keys ui_key, for text fields ui_type, for combos and lists ui_select." //$NON-NLS-1$
        + ACTION_SUFFIX;

    private static final String INPUT_SCHEMA = "{\"type\":\"object\",\"properties\":{" //$NON-NLS-1$
        + "\"ref\":{\"type\":\"string\",\"description\":\"Reference of the widget from a snapshot, e.g. w17.\"}," //$NON-NLS-1$
        + "\"double\":{\"type\":\"boolean\",\"description\":\"Double click a row of a table or an item of a tree. Default: false.\"}," //$NON-NLS-1$
        + SNAPSHOT_PROPERTY + "},\"required\":[\"ref\"]}"; //$NON-NLS-1$

    @Override
    public String getName() {
        return "ui_click"; //$NON-NLS-1$
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
        boolean doubleClick = UiArguments.getBoolean(anArguments, "double", false); //$NON-NLS-1$

        return aContext -> {

            ActionTarget target = ActionTarget.resolve(ref);
            Widget widget = target.getWidget();
            boolean item = widget instanceof TreeItem || widget instanceof TableItem;
            if (!(widget instanceof Button || widget instanceof Link || item || widget instanceof TabItem || widget instanceof CTabItem
                || widget instanceof ToolItem)) {
                throw new IllegalArgumentException(target.describe() + " cannot be clicked. ui_click works on buttons, links, tabs, tool items, " //$NON-NLS-1$
                    + "rows of tables and items of trees; use ui_type for text fields, ui_select for combos and lists, ui_lpex for the LPEX editor."); //$NON-NLS-1$
            }
            if (doubleClick && !item) {
                throw new IllegalArgumentException("'double' works on rows of tables and items of trees only, not on " + target.describe() + "."); //$NON-NLS-1$ //$NON-NLS-2$
            }
            target.check(false);
            aContext.record(target);

            String clicked = target.describe();
            if (widget instanceof Button) {
                Button button = (Button)widget;
                if ((button.getStyle() & SWT.RADIO) != 0) {
                    WidgetEvents.selectRadio(button);
                } else if ((button.getStyle() & (SWT.CHECK | SWT.TOGGLE)) != 0) {
                    WidgetEvents.setSelection(button, !button.getSelection());
                    return "Clicked " + clicked + ", now " + (button.isDisposed() || button.getSelection() ? "selected." : "not selected."); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                } else {
                    WidgetEvents.click(button);
                }
            } else if (widget instanceof Link) {
                return "Clicked the hyperlink \"" + WidgetEvents.clickLink((Link)widget) + "\" of " + clicked + "."; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            } else if (widget instanceof TreeItem) {
                if (doubleClick) {
                    WidgetEvents.doubleClickTreeItem((TreeItem)widget);
                } else {
                    WidgetEvents.selectTreeItem((TreeItem)widget, false);
                }
            } else if (widget instanceof TableItem) {
                if (doubleClick) {
                    WidgetEvents.doubleClickTableItem((TableItem)widget);
                } else {
                    WidgetEvents.selectTableItem((TableItem)widget, false);
                }
            } else if (widget instanceof ToolItem) {
                WidgetEvents.clickToolItem((ToolItem)widget);
            } else {
                WidgetEvents.selectTab((Item)widget);
            }

            return (doubleClick ? "Double clicked " : "Clicked ") + clicked + "."; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        };
    }
}
