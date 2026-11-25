/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.model;

import java.util.List;
import java.util.Map;

/**
 * Writes a snapshot as indented text: first the state of the workbench, then
 * one line per widget. Indented text needs fewer tokens than JSON and is easier
 * to read for an AI agent.
 */
public final class SnapshotWriter {

    /** Texts are cut after this number of characters */
    public static final int MAX_TEXT = 200;

    private static final String INDENT = "  "; //$NON-NLS-1$
    private static final String NONE = "none"; //$NON-NLS-1$
    private static final String SEPARATOR = ", "; //$NON-NLS-1$

    private final int maxNodes;
    private final boolean toolItems;
    private StringBuilder out;
    private int nodes;
    private int hiddenToolBars;
    private boolean cut;

    /**
     * Creates the writer.
     *
     * @param aMaxNodes - number of widgets, after which the snapshot is cut
     * @param aToolItems - whether tool bars and their items are written
     */
    public SnapshotWriter(int aMaxNodes, boolean aToolItems) {
        maxNodes = aMaxNodes;
        toolItems = aToolItems;
    }

    /**
     * Writes the snapshot.
     *
     * @param aState - state of the workbench
     * @param aRoots - widgets at the top, e.g. one shell
     * @return text of the snapshot
     */
    public String write(WorkbenchState aState, List<WidgetNode> aRoots) {

        out = new StringBuilder();
        nodes = 0;
        hiddenToolBars = 0;
        cut = false;

        writeState(aState);
        for (WidgetNode root : aRoots) {
            writeNode(root, 0);
        }

        if (cut) {
            out.append("... cut after ").append(maxNodes).append(" nodes, use the argument ref or maxNodes\n"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        if (hiddenToolBars > 0) {
            out.append('(').append(hiddenToolBars).append(" tool bars hidden, use the argument toolItems to show them)\n"); //$NON-NLS-1$
        }
        out.append('(').append(nodes).append(" nodes)\n"); //$NON-NLS-1$

        return out.toString();
    }

    private void writeState(WorkbenchState aState) {

        out.append("display: activeShell=").append(aState.getActiveShell() == null ? NONE : quote(aState.getActiveShell())) //$NON-NLS-1$
            .append(" focus=").append(aState.getFocus() == null ? NONE : aState.getFocus()).append('\n'); //$NON-NLS-1$

        if (aState.getPerspectiveLabel() != null) {
            out.append("workbench: perspective ").append(quote(aState.getPerspectiveLabel())).append(" (") //$NON-NLS-1$ //$NON-NLS-2$
                .append(aState.getPerspectiveId()).append("), activePart ") //$NON-NLS-1$
                .append(aState.getActivePart() == null ? NONE : quote(aState.getActivePart())).append('\n');

            out.append("views: "); //$NON-NLS-1$
            if (aState.getViews().isEmpty()) {
                out.append(NONE);
            }
            for (int i = 0; i < aState.getViews().size(); i++) {
                WorkbenchState.Part view = aState.getViews().get(i);
                out.append(i == 0 ? "" : SEPARATOR).append(quote(view.getTitle())).append(" (").append(view.getId()).append(')'); //$NON-NLS-1$ //$NON-NLS-2$
            }
            out.append('\n');

            out.append("editors: "); //$NON-NLS-1$
            if (aState.getEditors().isEmpty()) {
                out.append(NONE);
            }
            for (int i = 0; i < aState.getEditors().size(); i++) {
                WorkbenchState.Part editor = aState.getEditors().get(i);
                out.append(i == 0 ? "" : SEPARATOR).append(quote(editor.getTitle())); //$NON-NLS-1$
                if (editor.isDirty()) {
                    out.append(" dirty"); //$NON-NLS-1$
                }
            }
            out.append('\n');
        }

        if (!aState.getShells().isEmpty()) {
            out.append("shells: "); //$NON-NLS-1$
            for (int i = 0; i < aState.getShells().size(); i++) {
                WidgetNode shell = aState.getShells().get(i);
                out.append(i == 0 ? "" : SEPARATOR).append(quote(shell.getText())).append(" [").append(shell.getRef()).append(']'); //$NON-NLS-1$ //$NON-NLS-2$
                for (String flag : shell.getFlags()) {
                    out.append(' ').append(flag);
                }
            }
            out.append('\n');
        }

        if (aState.isNativeDialogOpen()) {
            out.append("native dialog open"); //$NON-NLS-1$
            if (aState.getNativeDialog() != null) {
                out.append(": ").append(quote(aState.getNativeDialog())); //$NON-NLS-1$
            }
            out.append(" - not readable\n"); //$NON-NLS-1$
        }
    }

    private void writeNode(WidgetNode aNode, int anIndent) {

        if (cut) {
            return;
        }

        if (aNode.isNote()) {
            indent(anIndent).append(aNode.getText()).append('\n');
            return;
        }

        if (WidgetNode.TYPE_TOOLBAR.equals(aNode.getType()) && !toolItems) {
            hiddenToolBars++;
            return;
        }

        if (nodes >= maxNodes) {
            cut = true;
            return;
        }
        nodes++;

        indent(anIndent).append(aNode.getType());
        if (aNode.getText() != null && !aNode.getText().isEmpty()) {
            out.append(' ').append(quote(aNode.getText()));
        }
        if (aNode.getRef() != null) {
            out.append(" [").append(aNode.getRef()).append(']'); //$NON-NLS-1$
        }
        for (Map.Entry<String, WidgetNode.Value> value : aNode.getValues().entrySet()) {
            String text = value.getValue().getText();
            out.append(' ').append(value.getKey()).append('=').append(value.getValue().isQuoted() ? quote(text) : text);
        }
        for (String flag : aNode.getFlags()) {
            out.append(' ').append(flag);
        }
        if (aNode.getLocator() != null) {
            out.append(" locator=").append(aNode.getLocator()); //$NON-NLS-1$
        }
        if (aNode.getTooltip() != null && !aNode.getTooltip().isEmpty()) {
            out.append(" tooltip=").append(quote(aNode.getTooltip())); //$NON-NLS-1$
        }
        out.append('\n');

        for (WidgetNode child : aNode.getChildren()) {
            writeNode(child, anIndent + 1);
        }
    }

    private StringBuilder indent(int anIndent) {
        for (int i = 0; i < anIndent; i++) {
            out.append(INDENT);
        }
        return out;
    }

    /**
     * Returns a text in quotes, with '"', '\', line breaks and tabs escaped,
     * cut after {@link #MAX_TEXT} characters.
     *
     * @param aText - the text or <code>null</code>
     * @return the text in quotes
     */
    public static String quote(String aText) {

        if (aText == null) {
            return "\"\""; //$NON-NLS-1$
        }

        String text = aText.length() > MAX_TEXT ? aText.substring(0, MAX_TEXT) + "..." : aText; //$NON-NLS-1$
        StringBuilder quoted = new StringBuilder(text.length() + 2).append('"');
        for (char c : text.toCharArray()) {
            switch (c) {
            case '"':
                quoted.append("\\\""); //$NON-NLS-1$
                break;
            case '\\':
                quoted.append("\\\\"); //$NON-NLS-1$
                break;
            case '\n':
                quoted.append("\\n"); //$NON-NLS-1$
                break;
            case '\t':
                quoted.append("\\t"); //$NON-NLS-1$
                break;
            case '\r':
                break;
            default:
                quoted.append(c);
            }
        }
        return quoted.append('"').toString();
    }
}
