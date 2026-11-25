/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.tools;

import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.core.commands.Command;
import org.eclipse.core.commands.NotEnabledException;
import org.eclipse.core.commands.NotHandledException;
import org.eclipse.core.commands.ParameterizedCommand;
import org.eclipse.core.commands.common.NotDefinedException;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.commands.ICommandService;
import org.eclipse.ui.handlers.IHandlerService;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import de.tools400.iagentbridge.mcp.ToolArguments;
import de.tools400.iagentbridge.ui.swt.ActionRejectedException;
import de.tools400.iagentbridge.ui.swt.ActionTarget;

/**
 * Tool 'ui_command': executes an Eclipse command with the handler service of
 * the workbench window, like a menu item or a shortcut of the workbench.
 */
public class UiCommandTool extends AbstractActionTool {

    private static final String DESCRIPTION = "Executes an Eclipse command in the test instance of RDi, like a menu item or a shortcut " //$NON-NLS-1$
        + "of the workbench does it, e.g. 'org.eclipse.ui.window.preferences' (Preferences), 'org.eclipse.ui.file.save' (Save), " //$NON-NLS-1$
        + "'org.eclipse.ui.views.showView' with the parameter 'org.eclipse.ui.views.showView.viewId'. It does not depend on the language " //$NON-NLS-1$
        + "of the menus. Fails, if the command is unknown, or has no enabled handler in the current state of the workbench." //$NON-NLS-1$
        + ACTION_SUFFIX;

    private static final String INPUT_SCHEMA = "{\"type\":\"object\",\"properties\":{" //$NON-NLS-1$
        + "\"id\":{\"type\":\"string\",\"description\":\"ID of the command.\"}," //$NON-NLS-1$
        + "\"parameters\":{\"type\":\"object\",\"description\":\"Parameters of the command: IDs and values, as strings.\"}," //$NON-NLS-1$
        + SNAPSHOT_PROPERTY + "},\"required\":[\"id\"]}"; //$NON-NLS-1$

    @Override
    public String getName() {
        return "ui_command"; //$NON-NLS-1$
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

        String id = ToolArguments.getString(anArguments, "id"); //$NON-NLS-1$
        Map<String, String> parameters = parameters(anArguments);

        return aContext -> {

            IWorkbench workbench = PlatformUI.getWorkbench();
            IWorkbenchWindow window = workbench.getActiveWorkbenchWindow();
            if (window == null && workbench.getWorkbenchWindowCount() > 0) {
                window = workbench.getWorkbenchWindows()[0];
            }
            if (window == null) {
                throw new ActionRejectedException("The test instance has no workbench window."); //$NON-NLS-1$
            }
            ActionTarget.of(window.getShell()).check(false);

            Command command = window.getService(ICommandService.class).getCommand(id);
            if (!command.isDefined()) {
                throw new IllegalArgumentException("Unknown command '" + id + "'."); //$NON-NLS-1$ //$NON-NLS-2$
            }
            ParameterizedCommand call = parameters.isEmpty() ? new ParameterizedCommand(command, null)
                : ParameterizedCommand.generateCommand(command, parameters);
            if (call == null) {
                throw new IllegalArgumentException("The parameters do not fit the command '" + id + "'."); //$NON-NLS-1$ //$NON-NLS-2$
            }

            aContext.record(window.getShell().getText(), "command", id); //$NON-NLS-1$
            Object result;
            try {
                result = window.getService(IHandlerService.class).executeCommand(call, null);
            } catch (NotDefinedException e) {
                throw new ActionRejectedException("The command '" + id + "' is not defined: " + e.getMessage()); //$NON-NLS-1$ //$NON-NLS-2$
            } catch (NotEnabledException e) {
                throw new ActionRejectedException("The command '" + id + "' is not enabled in the current state of the workbench. " //$NON-NLS-1$ //$NON-NLS-2$
                    + "Commands, that depend on the active part, e.g. Save, may be disabled, while the test instance is in the " //$NON-NLS-1$
                    + "background; in the LPEX editor use ui_key with Ctrl+S."); //$NON-NLS-1$
            } catch (NotHandledException e) {
                throw new ActionRejectedException("The command '" + id + "' has no active handler in the current state of the workbench."); //$NON-NLS-1$ //$NON-NLS-2$
            }

            return "Executed the command '" + id + "'" + (result == null ? "" : ", result: " + result) + "."; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
        };
    }

    /**
     * Returns the parameters of the command.
     */
    private static Map<String, String> parameters(JsonObject anArguments) {

        Map<String, String> parameters = new LinkedHashMap<String, String>();
        JsonElement element = anArguments.get("parameters"); //$NON-NLS-1$
        if (element == null || element.isJsonNull()) {
            return parameters;
        }
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException("Argument 'parameters' must be an object."); //$NON-NLS-1$
        }

        for (Map.Entry<String, JsonElement> parameter : element.getAsJsonObject().entrySet()) {
            JsonElement value = parameter.getValue();
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("The value of the parameter '" + parameter.getKey() + "' must be a string."); //$NON-NLS-1$ //$NON-NLS-2$
            }
            parameters.put(parameter.getKey(), value.getAsString());
        }

        return parameters;
    }
}
