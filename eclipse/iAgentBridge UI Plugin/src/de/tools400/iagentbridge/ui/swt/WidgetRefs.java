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

import org.eclipse.swt.widgets.Widget;

/**
 * References of widgets in snapshots, e.g. 'w17'. A reference stays valid, as
 * long as the widget lives, also in later snapshots. Used in the UI thread
 * only.
 */
public final class WidgetRefs {

    private static final Map<Widget, String> REFS = new WeakHashMap<Widget, String>();
    private static final Map<String, WeakReference<Widget>> WIDGETS = new HashMap<String, WeakReference<Widget>>();
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

        Iterator<WeakReference<Widget>> widgets = WIDGETS.values().iterator();
        while (widgets.hasNext()) {
            Widget widget = widgets.next().get();
            if (widget == null || widget.isDisposed()) {
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
