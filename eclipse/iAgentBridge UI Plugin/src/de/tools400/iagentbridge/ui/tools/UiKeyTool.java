/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.tools;

import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import de.tools400.iagentbridge.mcp.ToolArguments;
import de.tools400.iagentbridge.ui.model.KeyParser;
import de.tools400.iagentbridge.ui.model.KeyStroke;
import de.tools400.iagentbridge.ui.swt.ActionRejectedException;
import de.tools400.iagentbridge.ui.swt.ActionTarget;
import de.tools400.iagentbridge.ui.swt.KeyEvents;
import de.tools400.iagentbridge.ui.swt.ShellPicker;
import de.tools400.iagentbridge.ui.swt.WidgetEvents;

/**
 * Tool 'ui_key': ESC and ENTER for the active shell, or keys for a widget.
 */
public class UiKeyTool extends AbstractActionTool {

    private static final String DESCRIPTION = "Sends keys in the test instance of RDi. Without 'ref' only ESC or ENTER: ESC closes the " //$NON-NLS-1$
        + "active dialog like the key, ENTER clicks its default button. With 'ref' the keys go to that widget as key events: they work in " //$NON-NLS-1$
        + "widgets, that draw themselves, like the LPEX editor and StyledText, but native text fields ignore them (use ui_type). Keys, " //$NON-NLS-1$
        + "that the workbench handles, go with ui_command: e.g. Ctrl+S in a view, or DELETE in the LPEX editor with " //$NON-NLS-1$
        + "org.eclipse.ui.edit.delete." //$NON-NLS-1$
        + ACTION_SUFFIX;

    private static final String INPUT_SCHEMA = "{\"type\":\"object\",\"properties\":{" //$NON-NLS-1$
        + "\"keys\":{\"type\":\"string\",\"description\":\"Keys separated by blanks, e.g. 'ESC', 'ENTER', 'Ctrl+S', 'Shift+F10 a'. " //$NON-NLS-1$
        + "A key is a character or one of ESC, ENTER, TAB, SPACE, BACKSPACE, DELETE, INSERT, HOME, END, PAGE_UP, PAGE_DOWN, UP, DOWN, " //$NON-NLS-1$
        + "LEFT, RIGHT, F1 to F12; modifiers Ctrl, Shift, Alt.\"}," //$NON-NLS-1$
        + "\"ref\":{\"type\":\"string\",\"description\":\"Reference of the widget, that gets the keys. Without it only ESC or ENTER " //$NON-NLS-1$
        + "for the active shell.\"}," //$NON-NLS-1$
        + SNAPSHOT_PROPERTY + "},\"required\":[\"keys\"]}"; //$NON-NLS-1$

    @Override
    public String getName() {
        return "ui_key"; //$NON-NLS-1$
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

        List<KeyStroke> keys = KeyParser.parse(ToolArguments.getString(anArguments, "keys")); //$NON-NLS-1$
        String ref = UiArguments.getOptionalString(anArguments, "ref"); //$NON-NLS-1$

        if (ref == null) {
            if (keys.size() != 1 || !(keys.get(0).is(KeyStroke.ESC) || keys.get(0).is(KeyStroke.ENTER))) {
                throw new IllegalArgumentException("Without 'ref' only ESC or ENTER for the active shell. For other keys give the reference " //$NON-NLS-1$
                    + "of the widget, that gets them; for shortcuts of the workbench use ui_command."); //$NON-NLS-1$
            }
            return keys.get(0).is(KeyStroke.ESC) ? UiKeyTool::escape : UiKeyTool::enter;
        }

        return aContext -> {

            ActionTarget target = ActionTarget.resolve(ref);
            if (!(target.getWidget() instanceof Control)) {
                throw new IllegalArgumentException(target.describe() + " is an item. Give the reference of its tree, table or tool bar."); //$NON-NLS-1$
            }
            target.check(true);
            aContext.record(target);

            Control control = KeyEvents.keyTarget(target.getControl());
            for (KeyStroke key : keys) {
                KeyEvents.send(control, key);
            }

            return "Sent " + keys.size() + (keys.size() == 1 ? " key" : " keys") + " to " + target.describe() + "."; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
        };
    }

    /**
     * ESC closes the active shell like the key: Shell.traverse sends the
     * Traverse event to the shell and closes it, without input of the operating
     * system.
     */
    private static String escape(ActionContext aContext) throws ActionRejectedException {

        Shell shell = activeShell();
        ActionTarget target = ActionTarget.of(shell);
        target.check(false);
        aContext.record(target);

        String description = target.describe();
        boolean traversed = shell.traverse(SWT.TRAVERSE_ESCAPE);
        if (!traversed || !shell.isDisposed() && shell.isVisible()) {
            throw new ActionRejectedException(escapeFailed(description));
        }

        return "Pressed ESC: closed " + description + "."; //$NON-NLS-1$ //$NON-NLS-2$
    }

    /**
     * Returns the text for a shell, that ESC did not close.
     *
     * @param aDescription - the description of the shell
     * @return text
     */
    static String escapeFailed(String aDescription) {
        return "ESC did not close " + aDescription + ". If it is a dialog, close it with its Cancel or Close button (ui_click); ESC closes " //$NON-NLS-1$ //$NON-NLS-2$
            + "no workbench window."; //$NON-NLS-1$
    }

    /**
     * ENTER clicks the default button of the active shell with the events of a
     * click, not with Shell.traverse, which sends BM_CLICK to Windows: that may
     * fail for a dialog, that is not active.
     */
    private static String enter(ActionContext aContext) throws ActionRejectedException {

        Shell shell = activeShell();
        Button button = shell.getDefaultButton();
        if (button == null) {
            throw new ActionRejectedException("The shell \"" + shell.getText() + "\" has no default button. Use ui_click."); //$NON-NLS-1$ //$NON-NLS-2$
        }

        ActionTarget target = ActionTarget.of(button);
        target.check(false);
        aContext.record(target);

        String description = target.describe();
        WidgetEvents.click(button);

        return "Pressed ENTER: clicked the default button " + description + "."; //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static Shell activeShell() throws ActionRejectedException {

        Shell shell = ShellPicker.activeShell(getDisplay());
        if (shell == null) {
            throw new ActionRejectedException(NO_VISIBLE_SHELL);
        }

        return shell;
    }
}
