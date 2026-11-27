/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.tools;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Item;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.swt.widgets.Widget;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import de.tools400.iagentbridge.ui.swt.ActionTarget;
import de.tools400.iagentbridge.ui.swt.WidgetEvents;

/**
 * Tool 'ui_check': checks or unchecks a checkbox, a toggle button, a radio
 * button or a row or item with a check box.
 */
public class UiCheckTool extends AbstractActionTool {

    private static final String DESCRIPTION = "Sets the state of a checkbox, a toggle button, a radio button (only true: select another " //$NON-NLS-1$
        + "one to deselect it) or of the check box of a row of a table or an item of a tree in the test instance of RDi. If the state is " //$NON-NLS-1$
        + "right already, nothing is done." //$NON-NLS-1$
        + ACTION_SUFFIX;

    private static final String INPUT_SCHEMA = "{\"type\":\"object\",\"properties\":{" //$NON-NLS-1$
        + "\"ref\":{\"type\":\"string\",\"description\":\"Reference of the widget, row or tree item from a snapshot, e.g. w17.\"}," //$NON-NLS-1$
        + "\"checked\":{\"type\":\"boolean\",\"description\":\"The new state.\"}," //$NON-NLS-1$
        + SNAPSHOT_PROPERTY + "},\"required\":[\"ref\",\"checked\"]}"; //$NON-NLS-1$

    @Override
    public String getName() {
        return "ui_check"; //$NON-NLS-1$
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
        boolean checked = UiArguments.getRequiredBoolean(anArguments, "checked"); //$NON-NLS-1$

        return aContext -> {

            ActionTarget target = ActionTarget.resolve(ref);
            Widget widget = target.getWidget();
            boolean radio = widget instanceof Button && (widget.getStyle() & SWT.RADIO) != 0;
            boolean button = widget instanceof Button && (widget.getStyle() & (SWT.CHECK | SWT.TOGGLE | SWT.RADIO)) != 0;
            boolean item = (widget instanceof TreeItem || widget instanceof TableItem) && (target.getControl().getStyle() & SWT.CHECK) != 0;
            if (!button && !item) {
                throw new IllegalArgumentException(noState(target.describe()));
            }
            if (radio && !checked) {
                throw new IllegalArgumentException("A radio button cannot be unchecked; select another radio button of its group."); //$NON-NLS-1$
            }
            target.check(false);
            aContext.record(target);

            boolean current = button ? ((Button)widget).getSelection()
                : widget instanceof TreeItem ? ((TreeItem)widget).getChecked() : ((TableItem)widget).getChecked();
            String state = checked ? "checked" : "unchecked"; //$NON-NLS-1$ //$NON-NLS-2$
            if (current == checked) {
                return target.describe() + " is " + state + " already; nothing done."; //$NON-NLS-1$ //$NON-NLS-2$
            }

            if (radio) {
                WidgetEvents.selectRadio((Button)widget);
            } else if (button) {
                WidgetEvents.setSelection((Button)widget, checked);
            } else {
                WidgetEvents.setChecked((Item)widget, checked);
            }

            return "Set " + target.describe() + " to " + state + "."; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        };
    }

    /**
     * Returns the text for a target without a state to check.
     *
     * @param aDescription - the description of the target
     * @return text
     */
    static String noState(String aDescription) {
        return aDescription + " has no state to check. ui_check works on checkboxes, toggle and radio buttons and on rows and tree " //$NON-NLS-1$
            + "items of a table or tree with check boxes. Use ui_click for other buttons, ui_select with the reference of the table or tree " //$NON-NLS-1$
            + "for rows and items."; //$NON-NLS-1$
    }
}
