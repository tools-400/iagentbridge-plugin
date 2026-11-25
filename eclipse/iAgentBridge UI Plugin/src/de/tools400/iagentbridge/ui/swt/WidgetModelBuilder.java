/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.swt;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CCombo;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DateTime;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Item;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Scale;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Slider;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.swt.widgets.TreeItem;

import com.ibm.lpex.core.LpexView;
import com.ibm.lpex.core.LpexWindow;

import de.tools400.iagentbridge.ui.model.LocatorBuilder;
import de.tools400.iagentbridge.ui.model.Mnemonics;
import de.tools400.iagentbridge.ui.model.WidgetNode;

/**
 * Builds the model of a snapshot from the widgets: one node per visible widget,
 * with its items. Plain containers (composites without a text) are left out;
 * their children appear one level up. Used in the UI thread only.
 */
public final class WidgetModelBuilder {

    /** Data key of SWTBot for the ID of a widget */
    public static final String SWTBOT_KEY = "org.eclipse.swtbot.widget.key"; //$NON-NLS-1$

    /** Number of rows of a table or a list in the snapshot */
    static final int MAX_ROWS = 50;

    private static final String SELECTED = "selected"; //$NON-NLS-1$

    private final Control focus;

    /**
     * Creates the builder.
     *
     * @param aFocus - control with the keyboard focus or <code>null</code>
     */
    public WidgetModelBuilder(Control aFocus) {
        focus = aFocus;
    }

    /**
     * Builds the node of a control and its visible descendants.
     *
     * @param aControl - the control, e.g. a shell
     * @return node of the control
     */
    public WidgetNode build(Control aControl) {

        WidgetNode node = describe(aControl);
        if (node == null) {
            node = WidgetNode.widget(typeName(aControl), null, WidgetRefs.of(aControl));
        }
        addContent(aControl, node);

        return node;
    }

    /**
     * Describes a single control with its locator, without its content, e.g.
     * for the journal of an action.
     *
     * @param aControl - the control
     * @return node of the control, without children
     */
    public WidgetNode describeOnly(Control aControl) {

        WidgetNode node = describe(aControl);
        if (node == null) {
            node = WidgetNode.widget(typeName(aControl), null, WidgetRefs.of(aControl));
        }

        return node;
    }

    private void addControl(Control aControl, WidgetNode aParent) {

        if (!aControl.getVisible()) {
            return;
        }

        WidgetNode node = describe(aControl);
        if (node == null) {
            addContent(aControl, aParent);
        } else {
            aParent.addChild(node);
            addContent(aControl, node);
        }
    }

    private void addContent(Control aControl, WidgetNode aNode) {

        if (aControl instanceof LpexWindow) {
            return;
        } else if (aControl instanceof Tree) {
            addTree((Tree)aControl, aNode);
        } else if (aControl instanceof Table) {
            addTable((Table)aControl, aNode);
        } else if (aControl instanceof List) {
            addList((List)aControl, aNode);
        } else if (aControl instanceof TabFolder) {
            TabFolder folder = (TabFolder)aControl;
            Set<TabItem> selection = new HashSet<TabItem>(Arrays.asList(folder.getSelection()));
            for (TabItem item : folder.getItems()) {
                aNode.addChild(item("tab", item, selection.contains(item))); //$NON-NLS-1$
            }
        } else if (aControl instanceof CTabFolder) {
            CTabFolder folder = (CTabFolder)aControl;
            for (CTabItem item : folder.getItems()) {
                aNode.addChild(item("tab", item, folder.getSelection() == item)); //$NON-NLS-1$
            }
        } else if (aControl instanceof ToolBar) {
            for (ToolItem item : ((ToolBar)aControl).getItems()) {
                if ((item.getStyle() & SWT.SEPARATOR) == 0) {
                    String text = item.getText().isEmpty() ? item.getToolTipText() : item.getText();
                    WidgetNode toolItem = WidgetNode.widget("toolitem", Mnemonics.remove(text), WidgetRefs.of(item)) //$NON-NLS-1$
                        .setItemText(WidgetRefs.itemText(item));
                    if (!item.isEnabled()) {
                        toolItem.addFlag("disabled"); //$NON-NLS-1$
                    }
                    if (item.getSelection()) {
                        toolItem.addFlag(SELECTED);
                    }
                    aNode.addChild(toolItem);
                }
            }
        }

        if (aControl instanceof Composite) {
            for (Control child : ((Composite)aControl).getChildren()) {
                addControl(child, aNode);
            }
        }
    }

    private void addTree(Tree aTree, WidgetNode aNode) {

        addColumns(aNode, aTree.getColumns());
        addTreeItems(aTree.getItems(), new HashSet<TreeItem>(Arrays.asList(aTree.getSelection())), Math.max(1, aTree.getColumnCount()),
            (aTree.getStyle() & SWT.CHECK) != 0, aNode);
    }

    private void addTreeItems(TreeItem[] anItems, Set<TreeItem> aSelection, int aColumns, boolean aCheck, WidgetNode aParent) {

        for (TreeItem item : anItems) {
            WidgetNode node = WidgetNode.widget("item", rowText(item, aColumns), WidgetRefs.of(item)).setItemText(WidgetRefs.itemText(item)); //$NON-NLS-1$
            boolean hasChildren = item.getItemCount() > 0;
            if (hasChildren) {
                node.addFlag(item.getExpanded() ? "expanded" : "collapsed"); //$NON-NLS-1$ //$NON-NLS-2$
            }
            if (aSelection.contains(item)) {
                node.addFlag(SELECTED);
            }
            if (aCheck && item.getChecked()) {
                node.addFlag("checked"); //$NON-NLS-1$
            }
            aParent.addChild(node);
            if (hasChildren && item.getExpanded()) {
                addTreeItems(item.getItems(), aSelection, aColumns, aCheck, node);
            }
        }
    }

    private void addTable(Table aTable, WidgetNode aNode) {

        addColumns(aNode, aTable.getColumns());

        int count = aTable.getItemCount();
        int first = 0;
        int last = Math.min(count, MAX_ROWS);
        boolean virtual = (aTable.getStyle() & SWT.VIRTUAL) != 0;
        if (virtual) {
            // Only the visible rows: other rows would make the content provider
            // load data, maybe from the host.
            int itemHeight = Math.max(1, aTable.getItemHeight());
            first = Math.min(aTable.getTopIndex(), count);
            last = Math.min(count, first + Math.min(MAX_ROWS, aTable.getClientArea().height / itemHeight + 1));
        }

        Set<TableItem> selection = new HashSet<TableItem>(Arrays.asList(aTable.getSelection()));
        int columns = Math.max(1, aTable.getColumnCount());
        boolean check = (aTable.getStyle() & SWT.CHECK) != 0;
        for (int i = first; i < last; i++) {
            TableItem item = aTable.getItem(i);
            WidgetNode row = WidgetNode.widget("row", rowText(item, columns), WidgetRefs.of(item)).setItemText(WidgetRefs.itemText(item)); //$NON-NLS-1$
            if (selection.contains(item)) {
                row.addFlag(SELECTED);
            }
            if (check && item.getChecked()) {
                row.addFlag("checked"); //$NON-NLS-1$
            }
            aNode.addChild(row);
        }

        if (virtual && (first > 0 || last < count)) {
            aNode.addChild(
                WidgetNode.note("... rows " + (first + 1) + " to " + last + " of " + count + " shown (virtual table: only the visible rows)")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        } else if (last < count) {
            aNode.addChild(WidgetNode.note("... " + (count - last) + " more rows")); //$NON-NLS-1$ //$NON-NLS-2$
        }
    }

    private static void addList(List aList, WidgetNode aNode) {

        String[] items = aList.getItems();
        Set<Integer> selection = new HashSet<Integer>();
        for (int index : aList.getSelectionIndices()) {
            selection.add(Integer.valueOf(index));
        }

        int last = Math.min(items.length, MAX_ROWS);
        for (int i = 0; i < last; i++) {
            WidgetNode item = WidgetNode.widget("item", items[i], null); //$NON-NLS-1$
            if (selection.contains(Integer.valueOf(i))) {
                item.addFlag(SELECTED);
            }
            aNode.addChild(item);
        }

        if (last < items.length) {
            aNode.addChild(WidgetNode.note("... " + (items.length - last) + " more items")); //$NON-NLS-1$ //$NON-NLS-2$
        }
    }

    private static void addColumns(WidgetNode aNode, Item[] aColumns) {

        if (aColumns.length == 0) {
            return;
        }

        StringBuilder text = new StringBuilder();
        for (Item column : aColumns) {
            if (text.length() > 0) {
                text.append(" | "); //$NON-NLS-1$
            }
            text.append(column instanceof TreeColumn ? ((TreeColumn)column).getText() : ((TableColumn)column).getText());
        }
        aNode.addChild(WidgetNode.widget("columns", text.toString(), null)); //$NON-NLS-1$
    }

    private static String rowText(Item anItem, int aColumns) {

        StringBuilder text = new StringBuilder();
        for (int i = 0; i < aColumns; i++) {
            if (i > 0) {
                text.append(" | "); //$NON-NLS-1$
            }
            text.append(anItem instanceof TreeItem ? ((TreeItem)anItem).getText(i) : ((TableItem)anItem).getText(i));
        }

        return text.toString();
    }

    private static WidgetNode item(String aType, Item anItem, boolean aSelected) {
        WidgetNode node = WidgetNode.widget(aType, Mnemonics.remove(anItem.getText()), WidgetRefs.of(anItem))
            .setItemText(WidgetRefs.itemText(anItem));
        if (aSelected) {
            node.addFlag(SELECTED);
        }
        return node;
    }

    /**
     * Describes a control, or returns <code>null</code> for a plain container,
     * whose children appear one level up.
     */
    private WidgetNode describe(Control aControl) {

        if (isPlainContainer(aControl) || isEmptyLabel(aControl)) {
            return null;
        }

        String type = typeName(aControl);
        String text = text(aControl);
        WidgetNode node = WidgetNode.widget(type, text, WidgetRefs.of(aControl));
        if (isImageLabel(aControl)) {
            node.addFlag("image"); //$NON-NLS-1$
        }

        String label = label(aControl);
        node.addValue("label", label); //$NON-NLS-1$

        if (isPassword(aControl)) {
            node.addFlag("password"); //$NON-NLS-1$
        } else {
            node.addValue("value", value(aControl)); //$NON-NLS-1$
            node.addPlainValue("range", range(aControl)); //$NON-NLS-1$
        }

        if (aControl instanceof LpexWindow) {
            addLpex((LpexWindow)aControl, node);
        }

        if (!aControl.isEnabled()) {
            node.addFlag("disabled"); //$NON-NLS-1$
        }
        if (aControl instanceof Button) {
            Button button = (Button)aControl;
            if ((button.getStyle() & (SWT.CHECK | SWT.RADIO | SWT.TOGGLE)) != 0 && button.getSelection()) {
                node.addFlag(SELECTED);
            }
            if (button.getShell().getDefaultButton() == button) {
                node.addFlag("default"); //$NON-NLS-1$
            }
        }
        if (aControl instanceof Shell) {
            Shell shell = (Shell)aControl;
            if (ShellPicker.isModal(shell)) {
                node.addFlag("modal"); //$NON-NLS-1$
            }
            if (shell.getDisplay().getActiveShell() == shell) {
                node.addFlag("active"); //$NON-NLS-1$
            }
        }
        if (aControl == focus) {
            node.addFlag("focus"); //$NON-NLS-1$
        }

        Object id = aControl.getData(SWTBOT_KEY);
        node.setLocator(LocatorBuilder.locator(type, text, label, id == null ? null : id.toString()));

        String tooltip = aControl.getToolTipText();
        if (tooltip != null && !tooltip.equals(text)) {
            node.setTooltip(tooltip);
        }

        return node;
    }

    /**
     * Returns whether a control is a label without text, image and tooltip,
     * e.g. a spacer, which the snapshot leaves out.
     */
    private static boolean isEmptyLabel(Control aControl) {
        if (!(aControl instanceof Label || aControl instanceof CLabel)) {
            return false;
        }
        String text = text(aControl);
        String tooltip = aControl.getToolTipText();
        return (text == null || text.isEmpty()) && !isImageLabel(aControl) && (tooltip == null || tooltip.isEmpty());
    }

    /**
     * Returns whether a control is a label, that shows an image, e.g. the icon
     * of a message dialog.
     */
    private static boolean isImageLabel(Control aControl) {
        if (aControl instanceof Label) {
            return ((Label)aControl).getImage() != null;
        }
        if (aControl instanceof CLabel) {
            return ((CLabel)aControl).getImage() != null;
        }
        return false;
    }

    private static boolean isPlainContainer(Control aControl) {

        if (!(aControl instanceof Composite) || aControl.getData(SWTBOT_KEY) != null) {
            return false;
        }

        // CLabel and DateTime are composites, too, but widgets with a content.
        return !(aControl instanceof Shell || aControl instanceof Group || aControl instanceof TabFolder || aControl instanceof CTabFolder
            || aControl instanceof Tree || aControl instanceof Table || aControl instanceof Combo || aControl instanceof CCombo
            || aControl instanceof Spinner || aControl instanceof ToolBar || aControl instanceof StyledText || aControl instanceof LpexWindow
            || aControl instanceof CLabel || aControl instanceof DateTime || "org.eclipse.swt.browser.Browser".equals(aControl.getClass().getName())); //$NON-NLS-1$
    }

    /**
     * Returns the type of a control in the snapshot: the name of its nearest
     * SWT class, e.g. 'text', or of the kind of button, e.g. 'checkbox'.
     *
     * @param aControl - the control
     * @return type
     */
    public static String typeName(Control aControl) {

        if (aControl instanceof Button) {
            int style = aControl.getStyle();
            if ((style & SWT.CHECK) != 0) {
                return "checkbox"; //$NON-NLS-1$
            } else if ((style & SWT.RADIO) != 0) {
                return "radio"; //$NON-NLS-1$
            } else if ((style & SWT.TOGGLE) != 0) {
                return "toggle"; //$NON-NLS-1$
            }
            return "button"; //$NON-NLS-1$
        }
        if (aControl instanceof LpexWindow) {
            return "lpex"; //$NON-NLS-1$
        }

        Class<?> swtClass = aControl.getClass();
        while (!swtClass.getName().startsWith("org.eclipse.swt.")) { //$NON-NLS-1$
            swtClass = swtClass.getSuperclass();
        }
        String name = swtClass.getSimpleName().toLowerCase();

        return swtClass == aControl.getClass() ? name : name + "(" + aControl.getClass().getSimpleName() + ")"; //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static String text(Control aControl) {
        if (aControl instanceof Button) {
            return Mnemonics.remove(((Button)aControl).getText());
        } else if (aControl instanceof Label) {
            return Mnemonics.remove(((Label)aControl).getText());
        } else if (aControl instanceof CLabel) {
            return Mnemonics.remove(((CLabel)aControl).getText());
        } else if (aControl instanceof Group) {
            return Mnemonics.remove(((Group)aControl).getText());
        } else if (aControl instanceof Link) {
            return ((Link)aControl).getText();
        } else if (aControl instanceof Shell) {
            return ((Shell)aControl).getText();
        }
        return null;
    }

    private static String value(Control aControl) {
        if (aControl instanceof Text) {
            return ((Text)aControl).getText();
        } else if (aControl instanceof Combo) {
            return ((Combo)aControl).getText();
        } else if (aControl instanceof CCombo) {
            return ((CCombo)aControl).getText();
        } else if (aControl instanceof Spinner) {
            return String.valueOf(((Spinner)aControl).getSelection());
        } else if (aControl instanceof StyledText) {
            return ((StyledText)aControl).getText();
        } else if (aControl instanceof DateTime) {
            DateTime dateTime = (DateTime)aControl;
            if ((dateTime.getStyle() & SWT.TIME) != 0) {
                return String.format("%02d:%02d:%02d", Integer.valueOf(dateTime.getHours()), Integer.valueOf(dateTime.getMinutes()), //$NON-NLS-1$
                    Integer.valueOf(dateTime.getSeconds()));
            }
            return String.format("%04d-%02d-%02d", Integer.valueOf(dateTime.getYear()), Integer.valueOf(dateTime.getMonth() + 1), //$NON-NLS-1$
                Integer.valueOf(dateTime.getDay()));
        } else if (aControl instanceof ProgressBar) {
            return String.valueOf(((ProgressBar)aControl).getSelection());
        } else if (aControl instanceof Scale) {
            return String.valueOf(((Scale)aControl).getSelection());
        } else if (aControl instanceof Slider) {
            return String.valueOf(((Slider)aControl).getSelection());
        }
        return null;
    }

    /**
     * Returns the range of the value of a progress bar, scale or slider, e.g.
     * '0..100'. The value of a slider ends at its maximum minus its thumb.
     */
    private static String range(Control aControl) {
        if (aControl instanceof ProgressBar) {
            return range(((ProgressBar)aControl).getMinimum(), ((ProgressBar)aControl).getMaximum());
        } else if (aControl instanceof Scale) {
            return range(((Scale)aControl).getMinimum(), ((Scale)aControl).getMaximum());
        } else if (aControl instanceof Slider) {
            Slider slider = (Slider)aControl;
            return range(slider.getMinimum(), slider.getMaximum() - slider.getThumb());
        }
        return null;
    }

    private static String range(int aMinimum, int aMaximum) {
        return aMinimum + ".." + aMaximum; //$NON-NLS-1$
    }

    /**
     * Returns whether a control is a password field: the style SWT.PASSWORD or
     * an echo character, which e.g. the sign-on dialog of RSE sets without the
     * style.
     */
    static boolean isPassword(Control aControl) {
        if (!(aControl instanceof Text)) {
            return false;
        }
        Text text = (Text)aControl;
        return (text.getStyle() & SWT.PASSWORD) != 0 || text.getEchoChar() != '\0';
    }

    private static void addLpex(LpexWindow aWindow, WidgetNode aNode) {

        LpexView view = aWindow.getLpexView();
        if (view == null) {
            aNode.addFlag("noView"); //$NON-NLS-1$
            return;
        }

        aNode.addValue("name", view.query("name")); //$NON-NLS-1$ //$NON-NLS-2$
        aNode.addPlainValue("elements", String.valueOf(view.elements())); //$NON-NLS-1$
        aNode.addPlainValue("cursor", view.query("element") + ":" + view.query("position")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        aNode.addPlainValue("changes", view.query("changes")); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /**
     * Returns the text of the label in front of a field, or of a button without
     * a text, where SWTBot's '...WithLabel' finds it.
     */
    private static String label(Control aControl) {

        boolean buttonWithoutText = aControl instanceof Button && ((Button)aControl).getText().isEmpty();
        if (!(aControl instanceof Text || aControl instanceof Combo || aControl instanceof CCombo || aControl instanceof Spinner
            || aControl instanceof StyledText || aControl instanceof List || aControl instanceof Scale || aControl instanceof Slider
            || aControl instanceof DateTime || buttonWithoutText)) {
            return null;
        }

        Control[] siblings = aControl.getParent().getChildren();
        for (int i = Arrays.asList(siblings).indexOf(aControl) - 1; i >= 0; i--) {
            Control sibling = siblings[i];
            if (sibling instanceof Label && (sibling.getStyle() & SWT.SEPARATOR) == 0) {
                return Mnemonics.remove(((Label)sibling).getText());
            }
            if (sibling instanceof CLabel) {
                return Mnemonics.remove(((CLabel)sibling).getText());
            }
            if (sibling.isVisible() && !(sibling instanceof Composite)) {
                return null;
            }
        }

        return null;
    }
}
