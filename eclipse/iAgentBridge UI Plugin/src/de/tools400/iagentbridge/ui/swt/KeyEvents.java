/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.swt;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;

import com.ibm.lpex.core.LpexWindow;

import de.tools400.iagentbridge.ui.model.KeyStroke;

/**
 * Sends keys to a control as KeyDown and KeyUp events with notifyListeners,
 * never with Display.post. Native fields of the operating system ignore such
 * events; controls, that draw themselves, like StyledText and LPEX, act on
 * them. Used in the UI thread only.
 */
public final class KeyEvents {

    private static final Map<String, Integer> KEY_CODES = new HashMap<String, Integer>();
    private static final Map<String, Character> CHARACTERS = new HashMap<String, Character>();

    static {
        key(KeyStroke.ESC, SWT.ESC, SWT.ESC);
        key(KeyStroke.ENTER, SWT.CR, SWT.CR);
        key(KeyStroke.TAB, SWT.TAB, SWT.TAB);
        key(KeyStroke.SPACE, ' ', ' ');
        key(KeyStroke.BACKSPACE, SWT.BS, SWT.BS);
        key(KeyStroke.DELETE, SWT.DEL, SWT.DEL);
        key(KeyStroke.INSERT, SWT.INSERT, '\0');
        key(KeyStroke.HOME, SWT.HOME, '\0');
        key(KeyStroke.END, SWT.END, '\0');
        key(KeyStroke.PAGE_UP, SWT.PAGE_UP, '\0');
        key(KeyStroke.PAGE_DOWN, SWT.PAGE_DOWN, '\0');
        key(KeyStroke.UP, SWT.ARROW_UP, '\0');
        key(KeyStroke.DOWN, SWT.ARROW_DOWN, '\0');
        key(KeyStroke.LEFT, SWT.ARROW_LEFT, '\0');
        key(KeyStroke.RIGHT, SWT.ARROW_RIGHT, '\0');
    }

    private KeyEvents() {
    }

    private static void key(String aName, int aKeyCode, char aCharacter) {
        KEY_CODES.put(aName, Integer.valueOf(aKeyCode));
        CHARACTERS.put(aName, Character.valueOf(aCharacter));
    }

    /**
     * Returns the control, that takes the keys of a control: for the LPEX
     * editor its text window, because the LpexWindow itself has no key
     * listener.
     *
     * @param aControl - the control
     * @return the control, that gets the key events
     */
    public static Control keyTarget(Control aControl) {
        if (aControl instanceof LpexWindow) {
            return ((LpexWindow)aControl).textWindow();
        }
        return aControl;
    }

    /**
     * Sends a key as KeyDown and KeyUp.
     *
     * @param aControl - the control, that gets the events
     * @param aKey - the key
     */
    public static void send(Control aControl, KeyStroke aKey) {

        Event down = event(aControl, aKey);
        WidgetEvents.notify(aControl, SWT.KeyDown, down);

        Event up = event(aControl, aKey);
        WidgetEvents.notify(aControl, SWT.KeyUp, up);
    }

    /**
     * Returns the event of a key, as SWT on Windows fills it: the key code of a
     * letter is the lower case letter; with Ctrl the character of a letter is
     * its control character, e.g. Ctrl+S gives 0x13.
     */
    static Event event(Control aControl, KeyStroke aKey) {

        Event event = WidgetEvents.event(aControl);
        event.stateMask = (aKey.isCtrl() ? SWT.CTRL : 0) | (aKey.isShift() ? SWT.SHIFT : 0) | (aKey.isAlt() ? SWT.ALT : 0);

        if (aKey.getName() == null) {
            char character = aKey.getCharacter();
            char lower = Character.toLowerCase(character);
            event.keyCode = lower;
            if (aKey.isCtrl() && lower >= 'a' && lower <= 'z') {
                event.character = (char)(lower - 'a' + 1);
            } else if (aKey.isShift()) {
                event.character = Character.toUpperCase(character);
            } else {
                event.character = character;
            }
        } else if (aKey.getFunctionKey() > 0) {
            event.keyCode = SWT.F1 + aKey.getFunctionKey() - 1;
        } else {
            event.keyCode = KEY_CODES.get(aKey.getName()).intValue();
            event.character = CHARACTERS.get(aKey.getName()).charValue();
        }

        return event;
    }
}
