/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.model;

/**
 * Builds the locator of a widget in the style of SWTBot, e.g.
 * <code>button("OK")</code> or <code>textWithLabel("Port:")</code>. The
 * references of a snapshot are valid in one session only; the locators find the
 * widget again in a SWTBot test.
 */
public final class LocatorBuilder {

    private LocatorBuilder() {
    }

    /**
     * Returns the locator of a widget.
     *
     * @param aType - type of the widget in the snapshot, e.g. 'button', 'text'
     * @param aText - text of the widget, e.g. of a button, or <code>null</code>
     * @param aLabel - text of the label in front of a field or
     *        <code>null</code>
     * @param anId - value of the data key 'org.eclipse.swtbot.widget.key' or
     *        <code>null</code>
     * @return locator or <code>null</code>, if the widget has none
     */
    public static String locator(String aType, String aText, String aLabel, String anId) {

        if (anId != null) {
            return call("withId", anId); //$NON-NLS-1$
        }

        if (!isEmpty(aText)) {
            String method = buttonMethod(aType);
            if (method != null) {
                return call(method, aText);
            }
        }

        if (!isEmpty(aLabel)) {
            String method = withLabelMethod(aType);
            if (method != null) {
                return call(method, aLabel);
            }
        }

        return null;
    }

    private static String buttonMethod(String aType) {
        switch (aType) {
        case "button": //$NON-NLS-1$
            return "button"; //$NON-NLS-1$
        case "checkbox": //$NON-NLS-1$
            return "checkBox"; //$NON-NLS-1$
        case "radio": //$NON-NLS-1$
            return "radio"; //$NON-NLS-1$
        case "toggle": //$NON-NLS-1$
            return "toggleButton"; //$NON-NLS-1$
        default:
            return null;
        }
    }

    private static String withLabelMethod(String aType) {
        switch (aType) {
        case "button": //$NON-NLS-1$
            return "buttonWithLabel"; //$NON-NLS-1$
        case "checkbox": //$NON-NLS-1$
            return "checkBoxWithLabel"; //$NON-NLS-1$
        case "radio": //$NON-NLS-1$
            return "radioWithLabel"; //$NON-NLS-1$
        case "toggle": //$NON-NLS-1$
            return "toggleButtonWithLabel"; //$NON-NLS-1$
        case "text": //$NON-NLS-1$
            return "textWithLabel"; //$NON-NLS-1$
        case "combo": //$NON-NLS-1$
            return "comboBoxWithLabel"; //$NON-NLS-1$
        case "ccombo": //$NON-NLS-1$
            return "ccomboBoxWithLabel"; //$NON-NLS-1$
        case "spinner": //$NON-NLS-1$
            return "spinnerWithLabel"; //$NON-NLS-1$
        case "styledtext": //$NON-NLS-1$
            return "styledTextWithLabel"; //$NON-NLS-1$
        case "list": //$NON-NLS-1$
            return "listWithLabel"; //$NON-NLS-1$
        case "scale": //$NON-NLS-1$
            return "scaleWithLabel"; //$NON-NLS-1$
        case "slider": //$NON-NLS-1$
            return "sliderWithLabel"; //$NON-NLS-1$
        case "datetime": //$NON-NLS-1$
            return "dateTimeWithLabel"; //$NON-NLS-1$
        default:
            return null;
        }
    }

    /**
     * Returns the call of a method of SWTBot with a string argument, written as
     * a Java string literal, so that it fits into one line of the snapshot and
     * into the code of a test.
     */
    private static String call(String aMethod, String anArgument) {

        StringBuilder call = new StringBuilder(aMethod).append("(\""); //$NON-NLS-1$
        for (char c : anArgument.toCharArray()) {
            switch (c) {
            case '\\':
                call.append("\\\\"); //$NON-NLS-1$
                break;
            case '"':
                call.append("\\\""); //$NON-NLS-1$
                break;
            case '\n':
                call.append("\\n"); //$NON-NLS-1$
                break;
            case '\r':
                call.append("\\r"); //$NON-NLS-1$
                break;
            case '\t':
                call.append("\\t"); //$NON-NLS-1$
                break;
            default:
                call.append(c);
            }
        }

        return call.append("\")").toString(); //$NON-NLS-1$
    }

    private static boolean isEmpty(String aText) {
        return aText == null || aText.isEmpty();
    }
}
