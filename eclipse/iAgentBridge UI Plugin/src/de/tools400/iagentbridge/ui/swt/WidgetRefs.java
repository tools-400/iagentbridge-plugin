/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.swt;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.swt.widgets.Item;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.swt.widgets.Widget;

/**
 * References of widgets in snapshots, e.g. 'w17'. A reference stays valid, as
 * long as the widget lives, also in later snapshots. Used in the UI thread
 * only.
 * <p>
 * A viewer reuses its tree items and table rows, when it is refreshed: an item
 * then shows other data under the same reference. So a snapshot remembers the
 * text of each item, that it shows, and an action checks it with
 * {@link #checkText(String, Widget)}.
 */
public final class WidgetRefs {

    private static final Map<Widget, String> REFS = new WeakHashMap<Widget, String>();
    private static final Map<String, WeakReference<Widget>> WIDGETS = new HashMap<String, WeakReference<Widget>>();
    /** Written outside of the UI thread, when a snapshot is written */
    private static final Map<String, String> SHOWN_TEXTS = new ConcurrentHashMap<String, String>();
    private static int next = 1;

    private WidgetRefs() {
    }

    /**
     * Returns the reference of a widget and assigns one, if it has none yet.
     *
     * @param aWidget - the widget
     * @return reference, e.g. 'w17'
     */
    public static String of(Widget aWidget) {

        String ref = REFS.get(aWidget);
        if (ref == null) {
            ref = "w" + next++; //$NON-NLS-1$
            REFS.put(aWidget, ref);
            WIDGETS.put(ref, new WeakReference<Widget>(aWidget));
        }

        return ref;
    }

    /**
     * Returns whether a reference was assigned, also to a widget, that is
     * disposed meanwhile. References are numbered in the order of assignment.
     *
     * @param aRef - reference, e.g. 'w17'
     * @return <code>false</code>, if no snapshot gave this reference
     */
    public static boolean wasAssigned(String aRef) {

        if (!aRef.matches("w[0-9]{1,9}")) { //$NON-NLS-1$
            return false;
        }

        int number = Integer.parseInt(aRef.substring(1));
        return number >= 1 && number < next;
    }

    /**
     * Remembers the texts of the items, that a snapshot showed, for
     * {@link #checkText(String, Widget)}. Called after the snapshot was
     * written, also outside of the UI thread.
     *
     * @param aShownItems - references and texts of the items, see
     *        {@link #itemText(Item)}
     */
    public static void remember(Map<String, String> aShownItems) {
        SHOWN_TEXTS.putAll(aShownItems);
    }

    /**
     * Checks, that an item shows the text, that the last snapshot showed.
     *
     * @param aRef - the reference
     * @param aWidget - its widget
     * @throws IllegalArgumentException if the item shows another text now, e.g.
     *         after its viewer was refreshed
     */
    public static void checkText(String aRef, Widget aWidget) {

        String shown = SHOWN_TEXTS.get(aRef);
        if (shown == null || !(aWidget instanceof Item)) {
            return;
        }

        String now = itemText((Item)aWidget);
        if (!shown.equals(now)) {
            throw new IllegalArgumentException("Ref " + aRef + " now shows \"" + now + "\" instead of \"" + shown //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                + "\" - its viewer was refreshed. Take a new snapshot."); //$NON-NLS-1$
        }
    }

    /**
     * Returns the text of an item, that an action checks: of a tree item or a
     * table row the texts of all columns.
     *
     * @param anItem - the item
     * @return text
     */
    public static String itemText(Item anItem) {

        int columns;
        if (anItem instanceof TreeItem) {
            columns = ((TreeItem)anItem).getParent().getColumnCount();
        } else if (anItem instanceof TableItem) {
            columns = ((TableItem)anItem).getParent().getColumnCount();
        } else {
            return anItem.getText();
        }

        StringBuilder text = new StringBuilder();
        for (int i = 0; i < Math.max(1, columns); i++) {
            text.append(i == 0 ? "" : " | ").append(anItem instanceof TreeItem ? ((TreeItem)anItem).getText(i) : ((TableItem)anItem).getText(i)); //$NON-NLS-1$ //$NON-NLS-2$
        }

        return text.toString();
    }

    /**
     * Returns the widget of a reference.
     *
     * @param aRef - the reference
     * @return the widget
     * @throws IllegalArgumentException if the widget does not exist any more or
     *         the reference is unknown
     */
    public static Widget get(String aRef) {

        WeakReference<Widget> reference = WIDGETS.get(aRef);
        Widget widget = reference == null ? null : reference.get();
        if (widget == null || widget.isDisposed()) {
            throw new IllegalArgumentException("Ref " + aRef + " is unknown or outdated, take a new snapshot."); //$NON-NLS-1$ //$NON-NLS-2$
        }

        return widget;
    }

    /**
     * Forgets the references of disposed widgets.
     */
    public static void purge() {

        Iterator<Map.Entry<String, WeakReference<Widget>>> widgets = WIDGETS.entrySet().iterator();
        while (widgets.hasNext()) {
            Map.Entry<String, WeakReference<Widget>> entry = widgets.next();
            Widget widget = entry.getValue().get();
            if (widget == null || widget.isDisposed()) {
                SHOWN_TEXTS.remove(entry.getKey());
                widgets.remove();
            }
        }

        Iterator<Widget> keys = REFS.keySet().iterator();
        while (keys.hasNext()) {
            Widget widget = keys.next();
            if (widget == null || widget.isDisposed()) {
                keys.remove();
            }
        }
    }
}
