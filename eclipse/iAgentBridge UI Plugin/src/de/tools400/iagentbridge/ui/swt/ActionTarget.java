/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.swt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.swt.widgets.Widget;

import de.tools400.iagentbridge.ui.model.Journal;
import de.tools400.iagentbridge.ui.model.JournalEntry;
import de.tools400.iagentbridge.ui.model.Mnemonics;
import de.tools400.iagentbridge.ui.model.WidgetNode;

/**
 * The widget of an action: a control, or an item of a tree, a table, a tab
 * folder or a tool bar, whose control decides about visibility and blocks. Used
 * in the UI thread only.
 */
public final class ActionTarget {

    /**
     * Key, under which the workbench keeps the part of a tab of a part stack
     * (AbstractPartRenderer.OWNING_ME of E4)
     */
    private static final String PART_OF_TAB = "modelElement"; //$NON-NLS-1$

    private static Predicate<Control> visibility = Control::isVisible;

    private final Widget widget;
    private final Control control;
    private String description;

    private ActionTarget(Widget aWidget, Control aControl) {
        widget = aWidget;
        control = aControl;
    }

    /**
     * Replaces the test, whether a control is visible. Only for checks outside
     * of RDi, whose shells are never opened.
     *
     * @param aVisibility - the test
     */
    public static void setVisibilityTest(Predicate<Control> aVisibility) {
        visibility = aVisibility;
    }

    /**
     * Returns the target of a reference.
     *
     * @param aRef - reference of a snapshot, e.g. 'w17'
     * @return the target
     * @throws IllegalArgumentException if the reference is unknown or outdated,
     *         if its item shows another text than in the last snapshot, or if
     *         its widget is no control and no item, that an action can use
     */
    public static ActionTarget resolve(String aRef) {

        Widget widget = WidgetRefs.get(aRef);
        WidgetRefs.checkText(aRef, widget);

        return of(widget);
    }

    /**
     * Returns the target of a widget.
     *
     * @param aWidget - a control or an item of a tree, table, tab folder or
     *        tool bar
     * @return the target
     * @throws IllegalArgumentException if the widget is no such control or item
     */
    public static ActionTarget of(Widget aWidget) {

        Control control;
        if (aWidget instanceof Control) {
            control = (Control)aWidget;
        } else if (aWidget instanceof TreeItem) {
            control = ((TreeItem)aWidget).getParent();
        } else if (aWidget instanceof TableItem) {
            control = ((TableItem)aWidget).getParent();
        } else if (aWidget instanceof TabItem) {
            control = ((TabItem)aWidget).getParent();
        } else if (aWidget instanceof CTabItem) {
            control = ((CTabItem)aWidget).getParent();
        } else if (aWidget instanceof ToolItem) {
            control = ((ToolItem)aWidget).getParent();
        } else {
            throw new IllegalArgumentException(noActionTarget(WidgetRefs.of(aWidget), aWidget.getClass().getSimpleName()));
        }

        return new ActionTarget(aWidget, control);
    }

    /**
     * Returns the text for a widget, that no action can use.
     *
     * @param aRef - the reference of the widget
     * @param aType - the simple name of its class
     * @return text
     */
    static String noActionTarget(String aRef, String aType) {
        return "Ref " + aRef + " is a " + aType //$NON-NLS-1$ //$NON-NLS-2$
            + ", which no action can use. Give the reference of a control, a row, an item, a tab or a tool item."; //$NON-NLS-1$
    }

    public Widget getWidget() {
        return widget;
    }

    /**
     * Returns the control: the widget itself, or the control of an item.
     *
     * @return the control
     */
    public Control getControl() {
        return control;
    }

    public Shell getShell() {
        return control.getShell();
    }

    /**
     * Checks, that a user could operate the target: visible, enabled, not
     * blocked by a dialog, and - for typing - no password field.
     *
     * @param aTyping - <code>true</code>, if the action types into the target
     * @throws ActionRejectedException if the target cannot be operated
     */
    public void check(boolean aTyping) throws ActionRejectedException {

        if (!visibility.test(control)) {
            throw new ActionRejectedException(notVisible(describe()));
        }
        if (!control.isEnabled() || widget instanceof ToolItem && !((ToolItem)widget).isEnabled()) {
            throw new ActionRejectedException(disabled(describe()));
        }

        String lock = ShellLock.reason(control);
        if (lock != null) {
            throw new ActionRejectedException(lock);
        }

        if (aTyping && WidgetModelBuilder.isPassword(control)) {
            throw new ActionRejectedException(describe() + " is a password field. The UI tools never type passwords; " //$NON-NLS-1$
                + "the developer enters them by hand."); //$NON-NLS-1$
        }
    }

    /**
     * Returns the text for a target, that is not visible.
     *
     * @param aDescription - the description of the target
     * @return text
     */
    static String notVisible(String aDescription) {
        return aDescription + " is not visible. Make it visible first, e.g. select its tab or expand its section; then take a new snapshot."; //$NON-NLS-1$
    }

    /**
     * Returns the text for a target, that is disabled.
     *
     * @param aDescription - the description of the target
     * @return text
     */
    static String disabled(String aDescription) {
        return aDescription + " is disabled. Do first, what enables it, e.g. fill in a required field or select an element; then take a new " //$NON-NLS-1$
            + "snapshot."; //$NON-NLS-1$
    }

    /**
     * Activates the view or the editor of the workbench, that contains the
     * target, like a click into it does it: its folder gets Selection with the
     * tab of the part, and the workbench activates the part. Events alone do
     * not activate it, so actions of a context menu would use the selection of
     * another part. Only in a window of the workbench, not in a dialog, and
     * only tabs of parts, not the tabs of the pages of an editor with pages.
     * The tool bar of a view lies in the top right of its folder: an action
     * there activates the part, that the folder shows.
     */
    public void activatePart() {

        if (control.getShell().getParent() != null) {
            return;
        }

        Control child = control;
        for (Composite parent = control.getParent(); parent != null; parent = parent.getParent()) {
            if (parent instanceof CTabFolder) {
                CTabItem tab = partTab((CTabFolder)parent, child);
                if (tab != null) {
                    WidgetEvents.selectTab(tab);
                    return;
                }
            }
            child = parent;
        }
    }

    /**
     * Returns the tab of a part of a folder, that contains a child of the
     * folder: the tab of the child, or the tab, that the folder shows, if the
     * child is the top right of the folder.
     */
    private static CTabItem partTab(CTabFolder aFolder, Control aChild) {

        CTabItem tab = null;
        if (aFolder.getTopRight() == aChild) {
            tab = aFolder.getSelection();
        } else {
            for (CTabItem item : aFolder.getItems()) {
                if (item.getControl() == aChild) {
                    tab = item;
                }
            }
        }

        return tab != null && tab.getData(PART_OF_TAB) != null ? tab : null;
    }

    /**
     * Describes the target for messages, e.g. 'button "OK" [w20]'. After an
     * action, that closed the dialog of the target or dropped its item, it is
     * the description from before the action.
     *
     * @return description
     */
    public String describe() {

        if (widget.isDisposed() || control.isDisposed()) {
            return description == null ? "a disposed widget" : description; //$NON-NLS-1$
        }

        String text = getText();
        description = getType() + (text == null || text.isEmpty() ? "" : " \"" + text + "\"") + " [" + WidgetRefs.of(widget) + "]"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
        return description;
    }

    /**
     * Adds the action to the journal, with the locator of the control and - for
     * an item - the path of its texts.
     *
     * @param aJournal - the journal
     * @param aTool - name of the tool
     * @param anArguments - arguments for the journal
     * @return the entry, whose result is pending
     */
    public JournalEntry record(Journal aJournal, String aTool, String anArguments) {

        WidgetNode node = new WidgetModelBuilder(null).describeOnly(control);
        return aJournal.add(aTool, anArguments, getShell().getText(), getType(), getText(), node.getLocator(), getPath());
    }

    /**
     * Returns the type of the target, as the snapshot names it.
     */
    private String getType() {
        if (widget instanceof TreeItem) {
            return "item"; //$NON-NLS-1$
        } else if (widget instanceof TableItem) {
            return "row"; //$NON-NLS-1$
        } else if (widget instanceof TabItem || widget instanceof CTabItem) {
            return "tab"; //$NON-NLS-1$
        } else if (widget instanceof ToolItem) {
            return "toolitem"; //$NON-NLS-1$
        }
        return WidgetModelBuilder.typeName(control);
    }

    /**
     * Returns the text of the target, without mnemonic.
     */
    private String getText() {
        if (widget instanceof TreeItem) {
            return ((TreeItem)widget).getText();
        } else if (widget instanceof TableItem) {
            return ((TableItem)widget).getText();
        } else if (widget instanceof TabItem) {
            return Mnemonics.remove(((TabItem)widget).getText());
        } else if (widget instanceof CTabItem) {
            return Mnemonics.remove(((CTabItem)widget).getText());
        } else if (widget instanceof ToolItem) {
            ToolItem item = (ToolItem)widget;
            return item.getText().isEmpty() ? item.getToolTipText() : Mnemonics.remove(item.getText());
        }
        return new WidgetModelBuilder(null).describeOnly(control).getText();
    }

    /**
     * Returns the texts of an item and its parents; for a tree item from the
     * root.
     */
    private List<String> getPath() {

        if (widget instanceof TreeItem) {
            List<String> path = new ArrayList<String>();
            for (TreeItem item = (TreeItem)widget; item != null; item = item.getParentItem()) {
                path.add(0, item.getText());
            }
            return path;
        }
        if (widget instanceof Control) {
            return Collections.emptyList();
        }

        String text = getText();
        return text == null ? Collections.<String> emptyList() : Collections.singletonList(text);
    }
}
