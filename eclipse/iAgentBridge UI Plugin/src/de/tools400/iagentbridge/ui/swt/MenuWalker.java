/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.swt;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Item;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.TreeItem;

import de.tools400.iagentbridge.ui.model.ItemPath;

/**
 * Finds and selects menu items of a menu bar or a context menu, like SWTBot
 * 3.1.0 (MenuFinder, SWTBotMenu.click(), WaitForPopupMenu): a Show event lets
 * the menu managers of JFace and E4 fill a menu, before its items are read. No
 * menu is shown on the screen. Used in the UI thread only.
 */
public final class MenuWalker {

    private MenuWalker() {
    }

    /**
     * Prepares the context menu of a control like a right click: MenuDetect in
     * the middle of the control or of an item, then Show to the menu.
     *
     * @param aControl - the control
     * @param anItem - an item of the control, e.g. a tree item, or
     *        <code>null</code>
     * @return the context menu
     * @throws ActionRejectedException if the control has no context menu
     */
    public static Menu showContextMenu(Control aControl, Item anItem) throws ActionRejectedException {

        Rectangle bounds;
        if (anItem instanceof TreeItem) {
            bounds = ((TreeItem)anItem).getBounds();
        } else if (anItem instanceof TableItem) {
            bounds = ((TableItem)anItem).getBounds();
        } else {
            Point size = aControl.getSize();
            bounds = new Rectangle(0, 0, size.x, size.y);
        }
        Point center = aControl.toDisplay(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2);

        // Described before MenuDetect, whose listeners may dispose the control.
        String description = ActionTarget.of(aControl).describe();
        Event detect = WidgetEvents.event(aControl);
        detect.x = center.x;
        detect.y = center.y;
        detect.doit = true;
        WidgetEvents.notify(aControl, SWT.MenuDetect, detect);
        if (!detect.doit) {
            throw new ActionRejectedException(vetoed(description, anItem != null));
        }

        Menu menu = aControl.isDisposed() ? null : aControl.getMenu();
        if (menu == null) {
            throw new ActionRejectedException(noContextMenu(description));
        }
        WidgetEvents.notify(menu, SWT.Show, WidgetEvents.event(menu));

        return menu;
    }

    /**
     * Returns the text for a context menu, that a listener of MenuDetect
     * vetoed.
     *
     * @param aDescription - the description of the control
     * @param anItem - <code>true</code>, if the menu was asked for a row or an
     *        item
     * @return text
     */
    static String vetoed(String aDescription, boolean anItem) {
        return "The context menu of " + aDescription + " was vetoed. " //$NON-NLS-1$ //$NON-NLS-2$
            + (anItem ? "Use the menu bar (ui_menu without 'ref') or ui_command." : "Give the reference of a row or an item, or use ui_command."); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /**
     * Returns the text for a control without a context menu.
     *
     * @param aDescription - the description of the control
     * @return text
     */
    static String noContextMenu(String aDescription) {
        return aDescription + " has no context menu. Use the menu bar (ui_menu without 'ref') or ui_command."; //$NON-NLS-1$
    }

    /**
     * Finds the menu item of a path. Each menu on the way gets Arm on its item
     * and Show; on a failure the menus are hidden again.
     *
     * @param aRoot - a menu bar or a context menu
     * @param aPath - the texts of the items, e.g. ["Window", "Preferences"]
     * @return the menu item, enabled and without a submenu
     * @throws ActionRejectedException if the path does not lead to an enabled
     *         menu item; the message names the items of the menu, where the
     *         path ends
     */
    public static MenuItem find(Menu aRoot, List<String> aPath) throws ActionRejectedException {

        List<Menu> shown = new ArrayList<Menu>();
        Menu menu = aRoot;
        for (int i = 0; i < aPath.size(); i++) {
            String part = aPath.get(i);
            MenuItem item = item(menu, part);
            if (item == null) {
                hide(shown);
                throw new ActionRejectedException("No menu item '" + part + "'. The menu has: " + items(menu) + "."); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            }
            WidgetEvents.notify(item, SWT.Arm, WidgetEvents.event(item));

            Menu submenu = item.isDisposed() ? null : item.getMenu();
            boolean last = i == aPath.size() - 1;
            if (!last) {
                if (submenu == null) {
                    hide(shown);
                    throw new ActionRejectedException("The menu item '" + part + "' has no submenu."); //$NON-NLS-1$ //$NON-NLS-2$
                }
                WidgetEvents.notify(submenu, SWT.Show, WidgetEvents.event(submenu));
                shown.add(submenu);
                menu = submenu;
            } else if (submenu != null) {
                hide(shown);
                throw new ActionRejectedException("The menu item '" + part + "' is a submenu with: " + items(submenu) //$NON-NLS-1$ //$NON-NLS-2$
                    + ". Add one of them to the path."); //$NON-NLS-1$
            } else if (!item.isEnabled()) {
                hide(shown);
                throw new ActionRejectedException(disabledItem(part));
            } else {
                return item;
            }
        }

        throw new IllegalStateException("empty path"); //$NON-NLS-1$
    }

    /**
     * Returns the text for a menu item, that is disabled.
     *
     * @param aPart - the text of the menu item in the path
     * @return text
     */
    static String disabledItem(String aPart) {
        return "The menu item '" + aPart + "' is disabled. It may depend on the selection or the active part: select the right element " //$NON-NLS-1$ //$NON-NLS-2$
            + "first, then repeat."; //$NON-NLS-1$
    }

    /**
     * Selects a menu item like SWTBotMenu.click(), in one runnable: first the
     * menus from the item upwards are hidden, without the menu bar, so that E4
     * disposes hidden menus only after the selection; then a check or radio
     * item changes its state; then Selection.
     *
     * @param anItem - the menu item
     */
    public static void select(MenuItem anItem) {

        for (Menu menu = anItem.getParent(); menu != null && (menu.getStyle() & SWT.BAR) == 0; menu = menu.getParentMenu()) {
            menu.setVisible(false);
            WidgetEvents.notify(menu, SWT.Hide, WidgetEvents.event(menu));
        }

        if ((anItem.getStyle() & SWT.CHECK) != 0) {
            anItem.setSelection(!anItem.getSelection());
        } else if ((anItem.getStyle() & SWT.RADIO) != 0) {
            selectRadio(anItem);
        }

        WidgetEvents.notify(anItem, SWT.Selection, WidgetEvents.event(anItem));
    }

    private static MenuItem item(Menu aMenu, String aPart) {
        for (MenuItem item : aMenu.getItems()) {
            if (!item.isDisposed() && (item.getStyle() & SWT.SEPARATOR) == 0 && ItemPath.matchesMenu(item.getText(), aPart)) {
                return item;
            }
        }
        return null;
    }

    /**
     * Returns the texts of the items of a menu, for messages.
     */
    private static String items(Menu aMenu) {

        StringBuilder text = new StringBuilder();
        for (MenuItem item : aMenu.getItems()) {
            if (item.isDisposed() || (item.getStyle() & SWT.SEPARATOR) != 0) {
                continue;
            }
            text.append(text.length() == 0 ? "" : ", ").append('\'').append(ItemPath.menuText(item.getText())).append('\''); //$NON-NLS-1$ //$NON-NLS-2$
            if (!item.isEnabled()) {
                text.append(" (disabled)"); //$NON-NLS-1$
            }
            if (item.getMenu() != null) {
                text.append(" >"); //$NON-NLS-1$
            }
        }

        return text.length() == 0 ? "no items" : text.toString(); //$NON-NLS-1$
    }

    private static void hide(List<Menu> aShown) {
        for (int i = aShown.size() - 1; i >= 0; i--) {
            WidgetEvents.notify(aShown.get(i), SWT.Hide, WidgetEvents.event(aShown.get(i)));
        }
    }

    /**
     * Selects a radio menu item. The radio item of its group, that was selected
     * before, gets Deactivate, is deselected and gets Selection, like
     * SWTBotMenu does it.
     */
    private static void selectRadio(MenuItem anItem) {

        MenuItem other = selectedRadio(anItem);
        if (other != null) {
            WidgetEvents.notify(other, SWT.Deactivate, WidgetEvents.event(other));
            other.setSelection(false);
            WidgetEvents.notify(other, SWT.Selection, WidgetEvents.event(other));
        }
        anItem.setSelection(true);
    }

    /**
     * Returns the other selected item of the radio group of a menu item: the
     * radio items next to it, unless the menu has the style NO_RADIO_GROUP.
     */
    private static MenuItem selectedRadio(MenuItem anItem) {

        Menu menu = anItem.getParent();
        if ((menu.getStyle() & SWT.NO_RADIO_GROUP) != 0) {
            return null;
        }

        MenuItem[] items = menu.getItems();
        int index = menu.indexOf(anItem);
        for (int i = index - 1; i >= 0 && (items[i].getStyle() & SWT.RADIO) != 0; i--) {
            if (items[i].getSelection()) {
                return items[i];
            }
        }
        for (int i = index + 1; i < items.length && (items[i].getStyle() & SWT.RADIO) != 0; i++) {
            if (items[i].getSelection()) {
                return items[i];
            }
        }

        return null;
    }
}
