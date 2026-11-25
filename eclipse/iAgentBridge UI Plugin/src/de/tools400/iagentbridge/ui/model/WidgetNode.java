/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A line of the snapshot: a widget, an item of a widget (tree item, table row,
 * tab, tool item) or a note. It has no SWT types, so that the text of the
 * snapshot can be tested outside of RDi.
 */
public final class WidgetNode {

    /** Type of a tool bar, which the snapshot hides unless asked for */
    public static final String TYPE_TOOLBAR = "toolbar"; //$NON-NLS-1$

    private final String type;
    private final String text;
    private final String ref;
    private final Map<String, Value> values;
    private final List<String> flags;
    private final List<WidgetNode> children;
    private String locator;
    private String tooltip;

    private WidgetNode(String aType, String aText, String aRef) {
        type = aType;
        text = aText;
        ref = aRef;
        values = new LinkedHashMap<String, Value>();
        flags = new ArrayList<String>();
        children = new ArrayList<WidgetNode>();
    }

    /**
     * Creates the node of a widget or of an item.
     *
     * @param aType - type, e.g. 'button', 'text', 'item', 'row'
     * @param aText - text of the widget, e.g. of a button, or <code>null</code>
     * @param aRef - reference of the widget, e.g. 'w17', or <code>null</code>
     * @return node
     */
    public static WidgetNode widget(String aType, String aText, String aRef) {
        return new WidgetNode(aType, aText, aRef);
    }

    /**
     * Creates a note, a line, that is written as it is, e.g. '... 12 more
     * rows'.
     *
     * @param aText - text of the note
     * @return node
     */
    public static WidgetNode note(String aText) {
        return new WidgetNode(null, aText, null);
    }

    /**
     * Adds a value, that is written in quotes, e.g. label="Port:".
     *
     * @param aName - name of the value
     * @param aValue - the value; <code>null</code> is ignored
     * @return this node
     */
    public WidgetNode addValue(String aName, String aValue) {
        if (aValue != null) {
            values.put(aName, new Value(aValue, true));
        }
        return this;
    }

    /**
     * Adds a value, that is written without quotes, e.g. elements=5.
     *
     * @param aName - name of the value
     * @param aValue - the value; <code>null</code> is ignored
     * @return this node
     */
    public WidgetNode addPlainValue(String aName, String aValue) {
        if (aValue != null) {
            values.put(aName, new Value(aValue, false));
        }
        return this;
    }

    /**
     * Adds a flag, e.g. 'disabled' or 'selected'.
     *
     * @param aFlag - the flag
     * @return this node
     */
    public WidgetNode addFlag(String aFlag) {
        flags.add(aFlag);
        return this;
    }

    public WidgetNode setLocator(String aLocator) {
        locator = aLocator;
        return this;
    }

    public WidgetNode setTooltip(String aTooltip) {
        tooltip = aTooltip;
        return this;
    }

    public WidgetNode addChild(WidgetNode aChild) {
        children.add(aChild);
        return this;
    }

    public boolean isNote() {
        return type == null;
    }

    public String getType() {
        return type;
    }

    public String getText() {
        return text;
    }

    public String getRef() {
        return ref;
    }

    public Map<String, Value> getValues() {
        return Collections.unmodifiableMap(values);
    }

    public List<String> getFlags() {
        return Collections.unmodifiableList(flags);
    }

    public String getLocator() {
        return locator;
    }

    public String getTooltip() {
        return tooltip;
    }

    public List<WidgetNode> getChildren() {
        return Collections.unmodifiableList(children);
    }

    /**
     * A value of a node and whether it is written in quotes.
     */
    public static final class Value {

        private final String text;
        private final boolean quoted;

        private Value(String aText, boolean aQuoted) {
            text = aText;
            quoted = aQuoted;
        }

        public String getText() {
            return text;
        }

        public boolean isQuoted() {
            return quoted;
        }
    }
}
