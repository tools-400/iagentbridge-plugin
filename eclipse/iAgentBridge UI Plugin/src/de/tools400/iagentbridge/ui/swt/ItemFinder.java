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
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;

/**
 * Finds rows of a table and items of a tree by their texts, for 'ui_select'.
 * Used in the UI thread only.
 */
public final class ItemFinder {

    /** Most texts, that a message lists */
    private static final int MAX_LISTED = 30;

    private ItemFinder() {
    }

    /**
     * Returns the row of a table with a text in its first column. A virtual
     * table is searched in its visible rows only, like the snapshot does it, so
     * that no content provider loads data.
     *
     * @param aTable - the table
     * @param aText - text of the first column
     * @return the row
     * @throws IllegalArgumentException if no row has the text; the message
     *         names the searched rows
     */
    public static TableItem row(Table aTable, String aText) {

        int count = aTable.getItemCount();
        int first = 0;
        int last = count;
        boolean virtual = (aTable.getStyle() & SWT.VIRTUAL) != 0;
        if (virtual) {
            int itemHeight = Math.max(1, aTable.getItemHeight());
            first = Math.min(aTable.getTopIndex(), count);
            last = Math.min(count, first + aTable.getClientArea().height / itemHeight + 1);
        }

        List<String> texts = new ArrayList<String>();
        for (int i = first; i < last; i++) {
            TableItem item = aTable.getItem(i);
            if (aText.equals(item.getText())) {
                return item;
            }
            texts.add(item.getText());
        }

        throw new IllegalArgumentException("No row '" + aText + "'" //$NON-NLS-1$ //$NON-NLS-2$
            + (virtual ? " among the visible rows of the virtual table; use ui_click with the reference of a row" : "") + ". Rows: " //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            + listed(texts) + "."); //$NON-NLS-1$
    }

    /**
     * Returns the item of a path of texts in a tree.
     *
     * @param aTree - the tree
     * @param aPath - texts of the item and its parents, from the top
     * @return the item, or <code>null</code>, if a level is missing
     */
    public static TreeItem treeItem(Tree aTree, List<String> aPath) {

        TreeItem[] items = aTree.getItems();
        TreeItem found = null;
        for (String part : aPath) {
            found = null;
            for (TreeItem item : items) {
                if (part.equals(item.getText())) {
                    found = item;
                    break;
                }
            }
            if (found == null) {
                return null;
            }
            items = found.getItems();
        }

        return found;
    }

    /**
     * Describes the first missing level of a path, with the items of the level
     * above.
     *
     * @param aTree - the tree
     * @param aPath - texts of the item and its parents, from the top
     * @return the description, or <code>null</code>, if no level is missing
     */
    public static String missingTreeItem(Tree aTree, List<String> aPath) {

        TreeItem[] items = aTree.getItems();
        for (int i = 0; i < aPath.size(); i++) {
            TreeItem found = null;
            List<String> texts = new ArrayList<String>();
            for (TreeItem item : items) {
                // A viewer adds an empty item without data to an item, whose
                // children it has not created yet.
                if (!item.getText().isEmpty() || item.getData() != null) {
                    texts.add(item.getText());
                }
                if (found == null && aPath.get(i).equals(item.getText())) {
                    found = item;
                }
            }
            if (found == null) {
                return "No item '" + aPath.get(i) + "'" + (i == 0 ? " at the top" : " below '" + aPath.get(i - 1) + "'") + " of the tree. " //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
                    + "Items there: " + listed(texts) + "."; //$NON-NLS-1$ //$NON-NLS-2$
            }
            items = found.getItems();
        }

        return null;
    }

    /**
     * Lists texts for a message, at most 30 of them.
     *
     * @param aTexts - the texts
     * @return the texts in quotes, separated by commas
     */
    public static String listed(List<String> aTexts) {

        StringBuilder text = new StringBuilder();
        for (int i = 0; i < aTexts.size() && i < MAX_LISTED; i++) {
            text.append(i == 0 ? "" : ", ").append('\'').append(aTexts.get(i)).append('\''); //$NON-NLS-1$ //$NON-NLS-2$
        }
        if (aTexts.size() > MAX_LISTED) {
            text.append(", ... ").append(aTexts.size() - MAX_LISTED).append(" more"); //$NON-NLS-1$ //$NON-NLS-2$
        }

        return text.length() == 0 ? "none" : text.toString(); //$NON-NLS-1$
    }
}
