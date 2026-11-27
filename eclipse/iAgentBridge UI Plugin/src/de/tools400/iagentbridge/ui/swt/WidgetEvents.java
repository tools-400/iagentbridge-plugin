/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.swt;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CCombo;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Item;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.swt.widgets.Widget;

/**
 * Sends the events of a user action with notifyListeners, in the order of
 * SWTBot 3.1.0 (taken from its sources). Unlike SWTBot it never calls
 * setFocus(), forceFocus() or forceActive(), which would bring the test
 * instance to the front, and never Display.post(). All events of an action are
 * sent in one runnable of the UI thread. An event to a disposed widget is
 * skipped, e.g. after a click has closed its dialog. Used in the UI thread
 * only.
 */
public final class WidgetEvents {

    private static final Pattern LINK = Pattern.compile("<a(?:\\s+href\\s*=\\s*\"([^\"]*)\")?[^>]*>(.*?)</a>", Pattern.CASE_INSENSITIVE); //$NON-NLS-1$

    private WidgetEvents() {
    }

    /**
     * Clicks a push button: the events of SWTBotButton.click().
     *
     * @param aWidget - the button
     */
    public static void click(Widget aWidget) {
        notify(aWidget, SWT.MouseEnter, event(aWidget));
        notify(aWidget, SWT.MouseMove, event(aWidget));
        notify(aWidget, SWT.Activate, event(aWidget));
        notify(aWidget, SWT.FocusIn, event(aWidget));
        notify(aWidget, SWT.MouseDown, event(aWidget));
        notify(aWidget, SWT.MouseUp, event(aWidget));
        notify(aWidget, SWT.Selection, event(aWidget));
        notify(aWidget, SWT.MouseHover, event(aWidget));
        notify(aWidget, SWT.MouseMove, event(aWidget));
        notify(aWidget, SWT.MouseExit, event(aWidget));
        notify(aWidget, SWT.Deactivate, event(aWidget));
        notify(aWidget, SWT.FocusOut, event(aWidget));
    }

    /**
     * Sets the state of a checkbox or a toggle button and sends the events of a
     * click, like SWTBotCheckBox.select() and deselect().
     *
     * @param aButton - the button
     * @param aSelected - new state
     */
    public static void setSelection(Button aButton, boolean aSelected) {
        aButton.setSelection(aSelected);
        click(aButton);
    }

    /**
     * Selects a radio button and deselects the selected one of its group, like
     * SWTBotRadio.click().
     *
     * @param aButton - the radio button
     */
    public static void selectRadio(Button aButton) {

        if (aButton.getSelection()) {
            return;
        }

        Button other = selectedRadio(aButton);
        if (other != null) {
            notify(other, SWT.Deactivate, event(other));
            other.setSelection(false);
            notify(other, SWT.Selection, event(other));
        }

        notify(aButton, SWT.Activate, event(aButton));
        notify(aButton, SWT.MouseDown, mouseEvent(aButton, 1, SWT.NONE, 1));
        notify(aButton, SWT.MouseUp, mouseEvent(aButton, 1, SWT.BUTTON1, 1));
        aButton.setSelection(true);
        notify(aButton, SWT.Selection, event(aButton));
    }

    /**
     * Selects a tree item, like SWTBotTreeItem.select(): it replaces the
     * selection, or is added to it with Ctrl.
     *
     * @param anItem - the item
     * @param anAdd - <code>true</code>, to add it to the selection
     */
    public static void selectTreeItem(TreeItem anItem, boolean anAdd) {

        Tree tree = anItem.getParent();
        int stateMask = anAdd ? SWT.MOD1 : SWT.NONE;
        notify(tree, SWT.MouseEnter, itemMouseEvent(anItem, 0, SWT.NONE, 0));
        notify(tree, SWT.Activate, event(tree));
        notify(tree, SWT.FocusIn, event(tree));
        notify(tree, SWT.MouseDown, itemMouseEvent(anItem, 1, stateMask, 1));
        if (anAdd) {
            tree.select(anItem);
        } else {
            tree.setSelection(anItem);
        }
        notify(tree, SWT.Selection, itemEvent(anItem, stateMask | SWT.BUTTON1));
        notify(tree, SWT.MouseUp, itemMouseEvent(anItem, 1, stateMask | SWT.BUTTON1, 1));
    }

    /**
     * Double clicks a tree item, like SWTBotTreeItem.doubleClick().
     *
     * @param anItem - the item
     */
    public static void doubleClickTreeItem(TreeItem anItem) {

        Tree tree = anItem.getParent();
        notify(tree, SWT.MouseEnter, itemMouseEvent(anItem, 0, SWT.NONE, 0));
        notify(tree, SWT.Activate, event(tree));
        notify(tree, SWT.FocusIn, event(tree));
        notify(tree, SWT.MouseDown, itemMouseEvent(anItem, 1, SWT.NONE, 1));
        tree.setSelection(anItem);
        notify(tree, SWT.Selection, itemEvent(anItem, SWT.BUTTON1));
        doubleClickEnd(tree, anItem);
    }

    /**
     * Expands a tree item, like SWTBotTreeItem.expand(): the Expand event lets
     * a viewer create the children, then the item is expanded.
     *
     * @param anItem - the item
     */
    public static void expand(TreeItem anItem) {

        if (anItem.getExpanded()) {
            return;
        }

        Tree tree = anItem.getParent();
        notify(tree, SWT.Activate, event(tree));
        notify(tree, SWT.FocusIn, event(tree));
        notify(tree, SWT.MouseDown, itemMouseEvent(anItem, 1, SWT.NONE, 1));
        notify(tree, SWT.Expand, itemEvent(anItem, SWT.NONE));
        if (!anItem.isDisposed()) {
            anItem.setExpanded(true);
        }
        notify(tree, SWT.MouseUp, itemMouseEvent(anItem, 1, SWT.BUTTON1, 1));
    }

    /**
     * Selects a row of a table, like SWTBotTableItem.select(): it replaces the
     * selection, or is added to it with Ctrl.
     *
     * @param anItem - the row
     * @param anAdd - <code>true</code>, to add it to the selection
     */
    public static void selectTableItem(TableItem anItem, boolean anAdd) {

        Table table = anItem.getParent();
        int stateMask = anAdd ? SWT.MOD1 : SWT.NONE;
        if (anAdd) {
            table.select(table.indexOf(anItem));
        } else {
            table.setSelection(anItem);
        }
        notify(table, SWT.MouseEnter, itemMouseEvent(anItem, 0, SWT.NONE, 0));
        notify(table, SWT.Activate, event(table));
        notify(table, SWT.FocusIn, event(table));
        notify(table, SWT.MouseDown, itemMouseEvent(anItem, 1, stateMask, 1));
        notify(table, SWT.Selection, itemEvent(anItem, stateMask | SWT.BUTTON1));
        notify(table, SWT.MouseUp, itemMouseEvent(anItem, 1, stateMask | SWT.BUTTON1, 1));
    }

    /**
     * Double clicks a row of a table, like SWTBotTableItem.doubleClick().
     *
     * @param anItem - the row
     */
    public static void doubleClickTableItem(TableItem anItem) {

        Table table = anItem.getParent();
        notify(table, SWT.MouseEnter, itemMouseEvent(anItem, 0, SWT.NONE, 0));
        notify(table, SWT.Activate, event(table));
        if (table.getSelectionCount() != 1 || table.getSelection()[0] != anItem) {
            table.setSelection(anItem);
        }
        notify(table, SWT.FocusIn, event(table));
        notify(table, SWT.MouseDown, itemMouseEvent(anItem, 1, SWT.NONE, 1));
        notify(table, SWT.Selection, itemEvent(anItem, SWT.NONE));
        doubleClickEnd(table, anItem);
    }

    /**
     * Checks or unchecks an item of a tree or a table with SWT.CHECK, like
     * SWTBotTreeItem.check() and SWTBotTableItem.check().
     *
     * @param anItem - a TreeItem or a TableItem
     * @param aChecked - new state
     */
    public static void setChecked(Item anItem, boolean aChecked) {

        Control parent;
        if (anItem instanceof TreeItem) {
            ((TreeItem)anItem).setChecked(aChecked);
            parent = ((TreeItem)anItem).getParent();
        } else {
            ((TableItem)anItem).setChecked(aChecked);
            parent = ((TableItem)anItem).getParent();
        }

        Event event = itemEvent(anItem, SWT.NONE);
        event.detail = SWT.CHECK;
        notify(parent, SWT.Selection, event);
    }

    /**
     * Selects a tab, like SWTBotTabItem.activate() and
     * SWTBotCTabItem.activate(). Unlike SWTBot, the event names the item.
     *
     * @param anItem - a TabItem or a CTabItem
     */
    public static void selectTab(Item anItem) {

        Control folder;
        if (anItem instanceof TabItem) {
            folder = ((TabItem)anItem).getParent();
            ((TabItem)anItem).getParent().setSelection((TabItem)anItem);
        } else {
            folder = ((CTabItem)anItem).getParent();
            ((CTabItem)anItem).getParent().setSelection((CTabItem)anItem);
        }

        notify(folder, SWT.Selection, itemEvent(anItem, SWT.NONE));
    }

    /**
     * Clicks a tool item, like SWTBotToolbarPushButton.click(). A check item
     * changes its state. A radio item is selected; the radio item of its group,
     * that was selected before, gets Deactivate, is deselected and gets
     * Selection, like SWTBotToolbarRadioButton does it.
     *
     * @param anItem - the tool item
     */
    public static void clickToolItem(ToolItem anItem) {

        if ((anItem.getStyle() & SWT.CHECK) != 0) {
            anItem.setSelection(!anItem.getSelection());
        } else if ((anItem.getStyle() & SWT.RADIO) != 0) {
            ToolItem other = selectedRadio(anItem);
            if (other != null) {
                notify(other, SWT.Deactivate, event(other));
                other.setSelection(false);
                notify(other, SWT.Selection, event(other));
            }
            anItem.setSelection(true);
        }

        notify(anItem, SWT.MouseEnter, event(anItem));
        notify(anItem, SWT.MouseMove, event(anItem));
        notify(anItem, SWT.Activate, event(anItem));
        notify(anItem, SWT.MouseDown, event(anItem));
        notify(anItem, SWT.MouseUp, event(anItem));
        notify(anItem, SWT.Selection, event(anItem));
        notify(anItem, SWT.MouseHover, event(anItem));
        notify(anItem, SWT.MouseMove, event(anItem));
        notify(anItem, SWT.MouseExit, event(anItem));
        notify(anItem, SWT.Deactivate, event(anItem));
        notify(anItem, SWT.FocusOut, event(anItem));
    }

    /**
     * Clicks the first hyperlink of a link, like SWTBotLink.click(). The
     * Selection event carries its href, or its text, if it has none. SWTBot
     * reads the internal field 'ids'; this method reads the public text.
     *
     * @param aLink - the link
     * @return the text of the clicked hyperlink
     */
    public static String clickLink(Link aLink) {

        Matcher matcher = LINK.matcher(aLink.getText());
        if (!matcher.find()) {
            throw new IllegalArgumentException(noHyperlink());
        }
        String text = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);

        notify(aLink, SWT.MouseEnter, mouseEvent(aLink, 0, SWT.NONE, 0));
        notify(aLink, SWT.Activate, event(aLink));
        notify(aLink, SWT.FocusIn, event(aLink));
        notify(aLink, SWT.MouseDown, mouseEvent(aLink, 1, SWT.NONE, 1));
        Event selection = event(aLink);
        selection.text = text;
        notify(aLink, SWT.Selection, selection);
        notify(aLink, SWT.MouseUp, mouseEvent(aLink, 1, SWT.BUTTON1, 1));

        return matcher.group(2);
    }

    /**
     * Returns the text for a link without a hyperlink.
     *
     * @return text
     */
    static String noHyperlink() {
        return "The link has no hyperlink <a>...</a>. Nothing was clicked; use another widget of the snapshot."; //$NON-NLS-1$
    }

    /**
     * Selects an entry of a combo, like SWTBotCombo.setSelection().
     *
     * @param aCombo - the combo
     * @param anIndex - index of the entry
     */
    public static void selectCombo(Combo aCombo, int anIndex) {

        if ((aCombo.getStyle() & SWT.READ_ONLY) == 0 && aCombo.getSelectionIndex() == anIndex) {
            aCombo.deselect(anIndex);
        }
        aCombo.select(anIndex);
        notify(aCombo, SWT.Selection, event(aCombo));
    }

    /**
     * Selects an entry of a CCombo, like SWTBotCCombo.setSelection().
     *
     * @param aCombo - the combo
     * @param anIndex - index of the entry
     */
    public static void selectCCombo(CCombo aCombo, int anIndex) {
        aCombo.select(anIndex);
        notify(aCombo, SWT.Selection, event(aCombo));
    }

    /**
     * Selects entries of a list, like SWTBotList.select(): the first one
     * replaces the selection, the others are added with Ctrl.
     *
     * @param aList - the list
     * @param anIndices - indices of the entries
     */
    public static void selectList(List aList, int[] anIndices) {

        for (int i = 0; i < anIndices.length; i++) {
            boolean first = i == 0;
            if (first) {
                aList.setSelection(anIndices[i]);
                notify(aList, SWT.MouseEnter, event(aList));
                notify(aList, SWT.Activate, event(aList));
                notify(aList, SWT.FocusIn, event(aList));
            } else {
                aList.select(anIndices[i]);
            }
            int stateMask = first ? SWT.NONE : SWT.MOD1;
            notify(aList, SWT.MouseDown, mouseEvent(aList, 1, stateMask, 1));
            Event selection = event(aList);
            selection.stateMask = stateMask;
            notify(aList, SWT.Selection, selection);
            notify(aList, SWT.MouseUp, mouseEvent(aList, 1, stateMask | SWT.BUTTON1, 1));
        }
    }

    /**
     * Sends the events, with which typing into a field ends, after its text was
     * set: KeyUp of the last character - JFace field editors with
     * VALIDATE_ON_KEY_STROKE check the value then, not on Modify - and
     * FocusOut, on which field editors with VALIDATE_ON_FOCUS_LOST check it.
     *
     * @param aControl - the field
     * @param aText - the typed text
     */
    public static void afterTyping(Control aControl, String aText) {

        Event keyUp = event(aControl);
        if (!aText.isEmpty()) {
            keyUp.character = aText.charAt(aText.length() - 1);
            keyUp.keyCode = Character.toLowerCase(keyUp.character);
        }
        notify(aControl, SWT.KeyUp, keyUp);
        notify(aControl, SWT.FocusOut, event(aControl));
    }

    /**
     * Sends DefaultSelection to a field, as SWT does it for ENTER in a single
     * line field.
     *
     * @param aControl - the field
     */
    public static void defaultSelection(Control aControl) {
        notify(aControl, SWT.DefaultSelection, event(aControl));
    }

    /**
     * Sets the value of a spinner, like SWTBotSpinner.setSelection().
     *
     * @param aSpinner - the spinner
     * @param aValue - the value
     */
    public static void setSpinner(Spinner aSpinner, int aValue) {
        aSpinner.setSelection(aValue);
        notify(aSpinner, SWT.Selection, event(aSpinner));
    }

    private static void doubleClickEnd(Control aControl, Item anItem) {
        notify(aControl, SWT.MouseUp, itemMouseEvent(anItem, 1, SWT.BUTTON1, 1));
        notify(aControl, SWT.MouseDown, itemMouseEvent(anItem, 1, SWT.NONE, 2));
        notify(aControl, SWT.MouseDoubleClick, itemMouseEvent(anItem, 1, SWT.NONE, 2));
        notify(aControl, SWT.DefaultSelection, itemEvent(anItem, SWT.BUTTON1));
        notify(aControl, SWT.MouseUp, itemMouseEvent(anItem, 1, SWT.BUTTON1, 2));
        notify(aControl, SWT.MouseExit, itemMouseEvent(anItem, 0, SWT.NONE, 0));
        notify(aControl, SWT.Deactivate, event(aControl));
        notify(aControl, SWT.FocusOut, event(aControl));
    }

    /**
     * Sends an event, unless the widget is disposed meanwhile.
     */
    public static void notify(Widget aWidget, int aType, Event anEvent) {
        if (aWidget.isDisposed()) {
            return;
        }
        anEvent.type = aType;
        aWidget.notifyListeners(aType, anEvent);
    }

    /**
     * Returns an event for a widget, like SWTBot's createEvent(). For a widget,
     * that is disposed meanwhile, e.g. a button, whose click closed its dialog,
     * the event has no display; {@link #notify} skips it anyway.
     *
     * @param aWidget - the widget
     * @return the event
     */
    public static Event event(Widget aWidget) {
        Event event = new Event();
        event.time = (int)System.currentTimeMillis();
        event.widget = aWidget;
        event.display = aWidget.isDisposed() ? null : aWidget.getDisplay();
        return event;
    }

    private static Event itemEvent(Item anItem, int aStateMask) {
        Event event = event(anItem);
        event.item = anItem;
        event.stateMask = aStateMask;
        return event;
    }

    private static Event mouseEvent(Widget aWidget, int aButton, int aStateMask, int aCount) {
        Event event = event(aWidget);
        event.button = aButton;
        event.stateMask = aStateMask;
        event.count = aCount;
        return event;
    }

    /**
     * A mouse event in the middle of an item of a tree or a table, like
     * SWTBot's createMouseEvent() of the item.
     */
    private static Event itemMouseEvent(Item anItem, int aButton, int aStateMask, int aCount) {

        Event event = mouseEvent(anItem, aButton, aStateMask, aCount);
        event.item = anItem;
        if (anItem.isDisposed()) {
            return event;
        }
        Rectangle bounds = anItem instanceof TreeItem ? ((TreeItem)anItem).getBounds() : ((TableItem)anItem).getBounds();
        event.x = bounds.x + bounds.width / 2;
        event.y = bounds.y + bounds.height / 2;

        return event;
    }

    /**
     * Returns the selected radio button of the group of a radio button, like
     * SWTBotRadio: its siblings, unless the parent has SWT.NO_RADIO_GROUP.
     */
    private static Button selectedRadio(Button aButton) {

        if ((aButton.getParent().getStyle() & SWT.NO_RADIO_GROUP) != 0) {
            return null;
        }

        for (Control sibling : aButton.getParent().getChildren()) {
            if (sibling != aButton && sibling instanceof Button && (sibling.getStyle() & SWT.RADIO) != 0 && ((Button)sibling).getSelection()) {
                return (Button)sibling;
            }
        }

        return null;
    }

    /**
     * Returns the other selected item of the radio group of a tool item: the
     * radio items next to it.
     */
    private static ToolItem selectedRadio(ToolItem anItem) {

        ToolItem[] items = anItem.getParent().getItems();
        int index = anItem.getParent().indexOf(anItem);
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
