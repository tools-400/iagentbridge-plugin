/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.tools;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CCombo;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Widget;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ibm.lpex.core.LpexWindow;

import de.tools400.iagentbridge.ui.swt.ActionRejectedException;
import de.tools400.iagentbridge.ui.swt.ActionTarget;
import de.tools400.iagentbridge.ui.swt.WidgetEvents;

/**
 * Tool 'ui_type': sets the text of a text field, an editable combo, a styled
 * text or the value of a spinner.
 */
public class UiTypeTool extends AbstractActionTool {

    private static final String DESCRIPTION = "Types a text into a field of the test instance of RDi: a text field, an editable combo, a " //$NON-NLS-1$
        + "StyledText (with 'clear' it replaces the text, without it inserts at the cursor; only text fields and StyledText can insert), " //$NON-NLS-1$
        + "or a spinner (the text is its integer value, as the snapshot shows it). After the text the field gets KeyUp and FocusOut, so " //$NON-NLS-1$
        + "that preference pages check the value like after typing. 'submit' sends DefaultSelection to the field, like ENTER in a field, " //$NON-NLS-1$
        + "that handles ENTER itself, e.g. a filter; to submit a dialog use ui_key with ENTER. Never into password fields, never into " //$NON-NLS-1$
        + "the LPEX editor (use ui_lpex), for read-only combos use ui_select." //$NON-NLS-1$
        + ACTION_SUFFIX;

    private static final String INPUT_SCHEMA = "{\"type\":\"object\",\"properties\":{" //$NON-NLS-1$
        + "\"ref\":{\"type\":\"string\",\"description\":\"Reference of the field from a snapshot, e.g. w17.\"}," //$NON-NLS-1$
        + "\"text\":{\"type\":\"string\",\"description\":\"The text; for a spinner its value as an integer.\"}," //$NON-NLS-1$
        + "\"clear\":{\"type\":\"boolean\",\"description\":\"Replace the text. Default: true; false inserts at the cursor.\"}," //$NON-NLS-1$
        + "\"submit\":{\"type\":\"boolean\",\"description\":\"Send DefaultSelection to the field afterwards. Default: false.\"}," //$NON-NLS-1$
        + SNAPSHOT_PROPERTY + "},\"required\":[\"ref\",\"text\"]}"; //$NON-NLS-1$

    @Override
    public String getName() {
        return "ui_type"; //$NON-NLS-1$
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
        String text = UiArguments.getOptionalRawString(anArguments, "text"); //$NON-NLS-1$
        if (text == null) {
            throw new IllegalArgumentException("Argument 'text' is missing."); //$NON-NLS-1$
        }
        boolean clear = UiArguments.getBoolean(anArguments, "clear", true); //$NON-NLS-1$
        boolean submit = UiArguments.getBoolean(anArguments, "submit", false); //$NON-NLS-1$

        return aContext -> {

            ActionTarget target = ActionTarget.resolve(ref);
            Widget widget = target.getWidget();
            if (widget instanceof LpexWindow) {
                throw new IllegalArgumentException(target.describe() + " is the LPEX editor. Use ui_lpex."); //$NON-NLS-1$
            }
            if (!(widget instanceof Text || widget instanceof Combo || widget instanceof CCombo || widget instanceof StyledText
                || widget instanceof Spinner)) {
                throw new IllegalArgumentException(target.describe() + " is no field. ui_type works on text fields, editable combos, " //$NON-NLS-1$
                    + "StyledText and spinners."); //$NON-NLS-1$
            }
            if (!clear && !(widget instanceof Text || widget instanceof StyledText)) {
                throw new IllegalArgumentException("'clear': false works on text fields and StyledText only, not on " + target.describe() + "."); //$NON-NLS-1$ //$NON-NLS-2$
            }
            checkEditable(target);
            int value = widget instanceof Spinner ? spinnerValue(text, ((Spinner)widget).getMinimum(), ((Spinner)widget).getMaximum()) : 0;
            target.check(true);
            aContext.record(target);

            Control control = (Control)widget;
            if (widget instanceof Spinner) {
                WidgetEvents.setSpinner((Spinner)widget, value);
            } else {
                setText(control, text, clear);
            }
            WidgetEvents.afterTyping(control, text);
            if (submit) {
                WidgetEvents.defaultSelection(control);
            }

            return "Typed into " + target.describe() + (control.isDisposed() ? "." : ", its value is now \"" + valueOf(control) + "\"."); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        };
    }

    private static void checkEditable(ActionTarget aTarget) throws ActionRejectedException {

        Widget widget = aTarget.getWidget();
        boolean editable;
        if (widget instanceof Text) {
            editable = ((Text)widget).getEditable();
        } else if (widget instanceof StyledText) {
            editable = ((StyledText)widget).getEditable();
        } else if (widget instanceof Combo) {
            editable = (widget.getStyle() & SWT.READ_ONLY) == 0;
        } else if (widget instanceof CCombo) {
            editable = ((CCombo)widget).getEditable();
        } else {
            editable = true;
        }

        if (!editable) {
            throw new ActionRejectedException(aTarget.describe() + " is read-only" //$NON-NLS-1$
                + (widget instanceof Combo || widget instanceof CCombo ? "; use ui_select." : ".")); //$NON-NLS-1$ //$NON-NLS-2$
        }
    }

    /**
     * Returns the value, that is typed into a spinner: an integer within its
     * range, as getSelection() and the snapshot show it.
     *
     * @param aText - the text to type
     * @param aMinimum - minimum of the spinner
     * @param aMaximum - maximum of the spinner
     * @return the value
     * @throws IllegalArgumentException if the text is no integer in the range
     */
    static int spinnerValue(String aText, int aMinimum, int aMaximum) {

        int value;
        try {
            value = Integer.parseInt(aText.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("A spinner takes an integer, not '" + aText + "'."); //$NON-NLS-1$ //$NON-NLS-2$
        }
        if (value < aMinimum || value > aMaximum) {
            throw new IllegalArgumentException("The value " + value + " is not in the range " + aMinimum + ".." + aMaximum + " of the spinner."); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        }

        return value;
    }

    private static void setText(Control aControl, String aText, boolean aClear) {
        if (aControl instanceof Text) {
            if (aClear) {
                ((Text)aControl).setText(aText);
            } else {
                ((Text)aControl).insert(aText);
            }
        } else if (aControl instanceof StyledText) {
            if (aClear) {
                ((StyledText)aControl).setText(aText);
            } else {
                ((StyledText)aControl).insert(aText);
            }
        } else if (aControl instanceof Combo) {
            ((Combo)aControl).setText(aText);
        } else {
            ((CCombo)aControl).setText(aText);
        }
    }

    private static String valueOf(Control aControl) {
        if (aControl instanceof Text) {
            return ((Text)aControl).getText();
        } else if (aControl instanceof StyledText) {
            return ((StyledText)aControl).getText();
        } else if (aControl instanceof Combo) {
            return ((Combo)aControl).getText();
        } else if (aControl instanceof CCombo) {
            return ((CCombo)aControl).getText();
        }
        return String.valueOf(((Spinner)aControl).getSelection());
    }
}
