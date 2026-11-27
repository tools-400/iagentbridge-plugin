/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.tools;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ibm.lpex.core.LpexView;
import com.ibm.lpex.core.LpexWindow;

import de.tools400.iagentbridge.mcp.ToolArguments;
import de.tools400.iagentbridge.ui.model.LpexLines;
import de.tools400.iagentbridge.ui.swt.ActionRejectedException;
import de.tools400.iagentbridge.ui.swt.ActionTarget;
import de.tools400.iagentbridge.ui.swt.LpexEdits;
import de.tools400.iagentbridge.ui.swt.ShellPicker;
import de.tools400.iagentbridge.ui.swt.WidgetRefs;

/**
 * Tool 'ui_lpex': reads lines of the LPEX editor, sets its cursor and inserts
 * text at the cursor.
 */
public class UiLpexTool extends AbstractActionTool {

    private static final String DESCRIPTION = "Reads and writes the LPEX editor of the test instance of RDi, e.g. an RPG source. Without " //$NON-NLS-1$
        + "'line' and 'text' it returns the lines 'from' to 'to' with their numbers, the cursor and the number of changes. 'line' and " //$NON-NLS-1$
        + "'column' set the cursor, 'text' inserts text at the cursor, always, also in replace mode; then it returns the lines around " //$NON-NLS-1$
        + "the cursor. Keys like ENTER or Ctrl+S go with ui_key to the reference of the LPEX editor. DELETE is no key of LPEX in RDi: " //$NON-NLS-1$
        + "use ui_command with org.eclipse.ui.edit.delete." //$NON-NLS-1$
        + DESCRIPTION_SUFFIX;

    private static final String INPUT_SCHEMA = "{\"type\":\"object\",\"properties\":{" //$NON-NLS-1$
        + "\"ref\":{\"type\":\"string\",\"description\":\"Reference of the LPEX editor from a snapshot. Default: the LPEX editor, " //$NON-NLS-1$
        + "that is visible in the active shell.\"}," //$NON-NLS-1$
        + "\"from\":{\"type\":\"integer\",\"description\":\"First line to read. Default: 1.\"}," //$NON-NLS-1$
        + "\"to\":{\"type\":\"integer\",\"description\":\"Last line to read, at most 500 lines per call. Default: from + 199.\"}," //$NON-NLS-1$
        + "\"line\":{\"type\":\"integer\",\"description\":\"Line, to which the cursor goes.\"}," //$NON-NLS-1$
        + "\"column\":{\"type\":\"integer\",\"description\":\"Column, to which the cursor goes, with 'line'. Default: 1.\"}," //$NON-NLS-1$
        + "\"text\":{\"type\":\"string\",\"description\":\"Text, that is inserted at the cursor, also in replace mode.\"}," //$NON-NLS-1$
        + SNAPSHOT_PROPERTY.replace("Default: true.", "Default: false.") + "}}"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

    /** Lines before and after the cursor, that a write returns */
    private static final int CONTEXT_LINES = 2;

    @Override
    public String getName() {
        return "ui_lpex"; //$NON-NLS-1$
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
    protected boolean isSnapshotDefault() {
        return false;
    }

    @Override
    protected Action prepare(JsonObject anArguments) {

        String ref = UiArguments.getOptionalString(anArguments, "ref"); //$NON-NLS-1$
        int line = ToolArguments.getInt(anArguments, "line", 0, 1); //$NON-NLS-1$
        int column = ToolArguments.getInt(anArguments, "column", 1, 1); //$NON-NLS-1$
        String text = UiArguments.getOptionalRawString(anArguments, "text"); //$NON-NLS-1$
        if (anArguments.has("column") && line == 0) { //$NON-NLS-1$
            throw new IllegalArgumentException("Argument 'column' needs 'line'."); //$NON-NLS-1$
        }

        if (line > 0 || text != null) {
            return aContext -> write(aContext, ref, line, column, text);
        }

        int from = ToolArguments.getInt(anArguments, "from", 1, 1); //$NON-NLS-1$
        int to = ToolArguments.getInt(anArguments, "to", from + LpexLines.DEFAULT_LINES - 1, from); //$NON-NLS-1$
        if (to - from + 1 > LpexLines.MAX_LINES) {
            throw new IllegalArgumentException("At most " + LpexLines.MAX_LINES + " lines per call."); //$NON-NLS-1$ //$NON-NLS-2$
        }

        return aContext -> read(ref, from, to);
    }

    private static String read(String aRef, int aFrom, int aTo) throws ActionRejectedException {

        LpexWindow window = find(aRef);
        LpexView view = view(window);
        int last = Math.min(aTo, view.elements());

        LpexLines lines = new LpexLines(header(window, view), last, view.currentElement());
        for (int element = aFrom; element <= last; element++) {
            if (!view.show(element)) {
                lines.add(element, view.elementText(element));
            }
        }
        if (last < view.elements()) {
            lines.addNote("... " + (view.elements() - last) + " more lines"); //$NON-NLS-1$ //$NON-NLS-2$
        }

        return lines.toString();
    }

    private static String write(ActionContext aContext, String aRef, int aLine, int aColumn, String aText) throws ActionRejectedException {

        LpexWindow window = find(aRef);
        LpexView view = view(window);
        if (aLine > view.elements()) {
            throw new IllegalArgumentException("Line " + aLine + " does not exist; the editor has " + view.elements() + " lines."); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        }

        if (aText != null) {
            LpexEdits.checkWritable(view);
        }

        ActionTarget target = ActionTarget.of(window);
        target.check(false);
        aContext.record(target);

        if (aLine > 0) {
            view.jump(aLine, aColumn);
        }
        if (aText != null) {
            LpexEdits.insert(view, aText);
        }
        view.doDefaultCommand("screenShow"); //$NON-NLS-1$

        int cursor = view.currentElement();
        return read(WidgetRefs.of(window), Math.max(1, cursor - CONTEXT_LINES), Math.max(1, cursor + CONTEXT_LINES));
    }

    /**
     * Returns the LPEX editor of a reference, or the only one, that is visible
     * in the active shell.
     */
    private static LpexWindow find(String aRef) throws ActionRejectedException {

        if (aRef != null) {
            Control control = ActionTarget.resolve(aRef).getControl();
            if (!(control instanceof LpexWindow)) {
                throw new IllegalArgumentException("Ref " + aRef + " is no LPEX editor."); //$NON-NLS-1$ //$NON-NLS-2$
            }
            return (LpexWindow)control;
        }

        Shell shell = ShellPicker.activeShell(getDisplay());
        List<LpexWindow> windows = new ArrayList<LpexWindow>();
        if (shell != null) {
            collect(shell, windows);
        }
        if (windows.isEmpty()) {
            throw new ActionRejectedException(noEditor());
        }
        if (windows.size() > 1) {
            StringBuilder refs = new StringBuilder();
            for (LpexWindow window : windows) {
                refs.append(refs.length() == 0 ? "" : ", ").append(WidgetRefs.of(window)); //$NON-NLS-1$ //$NON-NLS-2$
            }
            throw new ActionRejectedException("Several LPEX editors are visible: " + refs + ". Give 'ref'."); //$NON-NLS-1$ //$NON-NLS-2$
        }

        return windows.get(0);
    }

    /**
     * Returns the text for an active shell without an LPEX editor.
     *
     * @return text
     */
    static String noEditor() {
        return "No LPEX editor is visible in the active shell. If a dialog is the active shell, close it first or give 'ref' of the editor; " //$NON-NLS-1$
            + "otherwise open the source, e.g. with ui_click and 'double', or select its editor tab."; //$NON-NLS-1$
    }

    private static void collect(Control aControl, List<LpexWindow> aWindows) {
        if (!aControl.isVisible()) {
            return;
        }
        if (aControl instanceof LpexWindow) {
            aWindows.add((LpexWindow)aControl);
        } else if (aControl instanceof Composite) {
            for (Control child : ((Composite)aControl).getChildren()) {
                collect(child, aWindows);
            }
        }
    }

    private static LpexView view(LpexWindow aWindow) throws ActionRejectedException {
        LpexView view = aWindow.getLpexView();
        if (view == null) {
            throw new ActionRejectedException(noDocument(WidgetRefs.of(aWindow)));
        }
        return view;
    }

    /**
     * Returns the text for an LPEX editor without a document.
     *
     * @param aRef - the reference of the editor
     * @return text
     */
    static String noDocument(String aRef) {
        return "The LPEX editor [" + aRef + "] shows no document. Wait, until the source is loaded, e.g. with ui_wait, then repeat."; //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static String header(LpexWindow aWindow, LpexView aView) {
        return "LPEX \"" + aView.query("name") + "\" [" + WidgetRefs.of(aWindow) + "]: " + aView.elements() + " lines, cursor " //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
            + aView.currentElement() + ":" + aView.query("position") + ", changes " + aView.query("changes") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            + ", insert mode " + aView.query("insertMode"); //$NON-NLS-1$ //$NON-NLS-2$
    }
}
