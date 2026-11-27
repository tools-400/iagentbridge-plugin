/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.tools;

import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Item;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.swt.widgets.Widget;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import de.tools400.iagentbridge.mcp.ToolArguments;
import de.tools400.iagentbridge.ui.model.ItemPath;
import de.tools400.iagentbridge.ui.swt.ActionRejectedException;
import de.tools400.iagentbridge.ui.swt.ActionTarget;
import de.tools400.iagentbridge.ui.swt.MenuWalker;
import de.tools400.iagentbridge.ui.swt.ShellPicker;
import de.tools400.iagentbridge.ui.swt.WidgetEvents;

/**
 * Tool 'ui_menu': selects an item of the menu bar of the active shell or of the
 * context menu of a widget.
 */
public class UiMenuTool extends AbstractActionTool {

    private static final String DESCRIPTION = "Selects a menu item in the test instance of RDi: without 'ref' in the menu bar of the " //$NON-NLS-1$
        + "active shell, e.g. 'Window > Preferences...', with 'ref' in the context menu of that widget; a row or a tree item is selected " //$NON-NLS-1$
        + "first, like a right click does it. The texts are compared without mnemonic and shortcut; '...' at the end does not matter. " //$NON-NLS-1$
        + "Menu texts depend on the language of the test instance; ui_command does not. If a part of the path is missing, the error " //$NON-NLS-1$
        + "names the items of that menu." //$NON-NLS-1$
        + ACTION_SUFFIX;

    private static final String INPUT_SCHEMA = "{\"type\":\"object\",\"properties\":{" //$NON-NLS-1$
        + "\"path\":{\"type\":\"string\",\"description\":\"Texts of the menu items, separated by '>', e.g. 'Window > Preferences...'; " //$NON-NLS-1$
        + "'\\\\>' is a '>' in a text.\"}," //$NON-NLS-1$
        + "\"ref\":{\"type\":\"string\",\"description\":\"Reference of a widget, row or tree item, whose context menu is used. " //$NON-NLS-1$
        + "Default: the menu bar of the active shell.\"}," //$NON-NLS-1$
        + SNAPSHOT_PROPERTY + "},\"required\":[\"path\"]}"; //$NON-NLS-1$

    @Override
    public String getName() {
        return "ui_menu"; //$NON-NLS-1$
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

        String path = ToolArguments.getString(anArguments, "path"); //$NON-NLS-1$
        List<String> parts = ItemPath.parse(path);
        String ref = UiArguments.getOptionalString(anArguments, "ref"); //$NON-NLS-1$

        if (ref == null) {
            return aContext -> {

                Shell shell = ShellPicker.activeShell(getDisplay());
                if (shell == null) {
                    throw new ActionRejectedException(NO_VISIBLE_SHELL);
                }
                Menu bar = shell.getMenuBar();
                if (bar == null) {
                    throw new ActionRejectedException("The active shell \"" + shell.getText() + "\" has no menu bar. Give 'ref' for a context " //$NON-NLS-1$ //$NON-NLS-2$
                        + "menu, or use ui_command."); //$NON-NLS-1$
                }
                ActionTarget.of(shell).check(false);
                aContext.record(shell.getText(), "menu", path); //$NON-NLS-1$

                MenuWalker.select(MenuWalker.find(bar, parts));

                return "Selected the menu item '" + path + "'."; //$NON-NLS-1$ //$NON-NLS-2$
            };
        }

        return aContext -> {

            ActionTarget target = ActionTarget.resolve(ref);
            Widget widget = target.getWidget();
            if (!(widget instanceof TreeItem || widget instanceof TableItem || widget == target.getControl())) {
                throw new IllegalArgumentException(target.describe() + " has no context menu of its own. Give the reference of its control."); //$NON-NLS-1$
            }
            target.check(false);
            aContext.record(target);

            Item item = null;
            if (widget instanceof TreeItem) {
                item = (TreeItem)widget;
                WidgetEvents.selectTreeItem((TreeItem)item, false);
            } else if (widget instanceof TableItem) {
                item = (TableItem)widget;
                WidgetEvents.selectTableItem((TableItem)item, false);
            }

            Menu popup = MenuWalker.showContextMenu(target.getControl(), item);
            MenuItem menuItem;
            try {
                menuItem = MenuWalker.find(popup, parts);
            } catch (ActionRejectedException e) {
                WidgetEvents.notify(popup, SWT.Hide, WidgetEvents.event(popup));
                throw e;
            }
            MenuWalker.select(menuItem);

            return "Selected the item '" + path + "' of the context menu of " + target.describe() + "."; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        };
    }
}
