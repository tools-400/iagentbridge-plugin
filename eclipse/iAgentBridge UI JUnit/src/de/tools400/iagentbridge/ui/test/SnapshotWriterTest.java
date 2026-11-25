/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

import de.tools400.iagentbridge.ui.model.SnapshotWriter;
import de.tools400.iagentbridge.ui.model.WidgetNode;
import de.tools400.iagentbridge.ui.model.WorkbenchState;

public class SnapshotWriterTest {

    @Test
    public void testWrite_dialog() {

        WorkbenchState state = new WorkbenchState();
        state.setActiveShell("Preferences");
        state.setFocus("tree [w2]");
        state.setPerspective("Resource", "org.eclipse.ui.resourcePerspective");
        state.setActivePart("Project Explorer");
        state.addView("Project Explorer", "org.eclipse.ui.navigator.ProjectExplorer");
        state.addEditor("spike.rpgle", true);
        state.addShell(WidgetNode.widget("shell", "RDi", "w9"));
        state.addShell(WidgetNode.widget("shell", "Preferences", "w1").addFlag("modal"));

        WidgetNode shell = WidgetNode.widget("shell", "Preferences", "w1").addFlag("modal").addFlag("active");
        WidgetNode tree = WidgetNode.widget("tree", null, "w2").addFlag("focus");
        tree.addChild(WidgetNode.widget("item", "General", "w3").addFlag("collapsed"));
        tree.addChild(WidgetNode.widget("item", "iAgentBridge", "w4").addFlag("selected"));
        shell.addChild(tree);
        WidgetNode group = WidgetNode.widget("group", "Connection", "w10");
        group.addChild(WidgetNode.widget("text", null, "w11").addValue("label", "Port:").addValue("value", "39196")
            .setLocator("textWithLabel(\"Port:\")").setTooltip("Port of the server"));
        group.addChild(WidgetNode.widget("button", "Start", "w12").addFlag("disabled").setLocator("button(\"Start\")"));
        shell.addChild(group);
        shell.addChild(WidgetNode.widget("button", "Apply and Close", "w20").addFlag("default").setLocator("button(\"Apply and Close\")"));

        String text = new SnapshotWriter(400, false).write(state, Collections.singletonList(shell));

        assertEquals(lines("display: activeShell=\"Preferences\" focus=tree [w2]",
            "workbench: perspective \"Resource\" (org.eclipse.ui.resourcePerspective), activePart \"Project Explorer\"",
            "views: \"Project Explorer\" (org.eclipse.ui.navigator.ProjectExplorer)", "editors: \"spike.rpgle\" dirty",
            "shells: \"RDi\" [w9], \"Preferences\" [w1] modal", "shell \"Preferences\" [w1] modal active", "  tree [w2] focus",
            "    item \"General\" [w3] collapsed", "    item \"iAgentBridge\" [w4] selected", "  group \"Connection\" [w10]",
            "    text [w11] label=\"Port:\" value=\"39196\" locator=textWithLabel(\"Port:\") tooltip=\"Port of the server\"",
            "    button \"Start\" [w12] disabled locator=button(\"Start\")",
            "  button \"Apply and Close\" [w20] default locator=button(\"Apply and Close\")", "(8 nodes)"), text);
    }

    @Test
    public void testWrite_withoutWorkbench() {

        WorkbenchState state = new WorkbenchState();

        String text = new SnapshotWriter(400, false).write(state, Collections.singletonList(WidgetNode.widget("shell", "Login", "w1")));

        assertEquals(lines("display: activeShell=none focus=none", "shell \"Login\" [w1]", "(1 nodes)"), text);
    }

    @Test
    public void testWrite_noEditors() {

        WorkbenchState state = new WorkbenchState();
        state.setPerspective("Resource", "resource");

        String text = new SnapshotWriter(400, false).write(state, Collections.<WidgetNode> emptyList());

        assertEquals(lines("display: activeShell=none focus=none", "workbench: perspective \"Resource\" (resource), activePart none", "views: none",
            "editors: none", "(0 nodes)"), text);
    }

    @Test
    public void testWrite_valuesAndNotes() {

        WidgetNode table = WidgetNode.widget("table", null, "w5");
        table.addChild(WidgetNode.note("columns \"Name | Size\""));
        table.addChild(WidgetNode.widget("row", "a | 1", "w6").addFlag("selected"));
        table.addChild(WidgetNode.note("... 12 more rows"));
        WidgetNode lpex = WidgetNode.widget("lpex", null, "w7").addValue("name", "spike.rpgle").addPlainValue("elements", "5").addPlainValue("cursor",
            "1:1");

        String text = new SnapshotWriter(400, false).write(new WorkbenchState(), Arrays.asList(table, lpex));

        assertEquals(lines("display: activeShell=none focus=none", "table [w5]", "  columns \"Name | Size\"", "  row \"a | 1\" [w6] selected",
            "  ... 12 more rows", "lpex [w7] name=\"spike.rpgle\" elements=5 cursor=1:1", "(3 nodes)"), text);
    }

    @Test
    public void testWrite_password() {

        WidgetNode password = WidgetNode.widget("text", null, "w8").addValue("label", "Password:").addFlag("password");

        String text = new SnapshotWriter(400, false).write(new WorkbenchState(), Collections.singletonList(password));

        assertTrue(text, text.contains("text [w8] label=\"Password:\" password\n"));
    }

    @Test
    public void testWrite_nativeDialog() {

        WorkbenchState state = new WorkbenchState();
        state.setNativeDialog("Open File");
        WorkbenchState unknown = new WorkbenchState();
        unknown.setNativeDialog(null);

        String text = new SnapshotWriter(400, false).write(state, Collections.<WidgetNode> emptyList());
        String unknownText = new SnapshotWriter(400, false).write(unknown, Collections.<WidgetNode> emptyList());

        assertTrue(text, text.contains("\nnative dialog open: \"Open File\" - not readable\n"));
        assertTrue(unknownText, unknownText.contains("\nnative dialog open - not readable\n"));
    }

    @Test
    public void testWrite_maxNodes() {

        WidgetNode shell = WidgetNode.widget("shell", "S", "w1");
        shell.addChild(WidgetNode.widget("button", "A", "w2"));
        shell.addChild(WidgetNode.widget("button", "B", "w3"));
        shell.addChild(WidgetNode.widget("button", "C", "w4"));

        String text = new SnapshotWriter(2, false).write(new WorkbenchState(), Collections.singletonList(shell));

        assertEquals(lines("display: activeShell=none focus=none", "shell \"S\" [w1]", "  button \"A\" [w2]",
            "... cut after 2 nodes, use the argument ref or maxNodes", "(2 nodes)"), text);
    }

    @Test
    public void testWrite_toolBars() {

        WidgetNode folder = WidgetNode.widget("ctabfolder", null, "w1");
        WidgetNode toolBar = WidgetNode.widget(WidgetNode.TYPE_TOOLBAR, null, "w2");
        toolBar.addChild(WidgetNode.widget("toolitem", "Minimize", "w3"));
        folder.addChild(toolBar);
        folder.addChild(WidgetNode.widget("tree", null, "w4"));

        String hidden = new SnapshotWriter(400, false).write(new WorkbenchState(), Collections.singletonList(folder));
        String shown = new SnapshotWriter(400, true).write(new WorkbenchState(), Collections.singletonList(folder));

        assertEquals(lines("display: activeShell=none focus=none", "ctabfolder [w1]", "  tree [w4]",
            "(1 tool bars hidden, use the argument toolItems to show them)", "(2 nodes)"), hidden);
        assertEquals(lines("display: activeShell=none focus=none", "ctabfolder [w1]", "  toolbar [w2]", "    toolitem \"Minimize\" [w3]",
            "  tree [w4]", "(4 nodes)"), shown);
    }

    @Test
    public void testQuote() {

        assertEquals("\"a \\\"b\\\" c\\\\d\\ne\\tf\"", SnapshotWriter.quote("a \"b\" c\\d\r\ne\tf"));
        assertEquals("\"\"", SnapshotWriter.quote(null));

        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 250; i++) {
            longText.append('x');
        }
        String quoted = SnapshotWriter.quote(longText.toString());
        assertEquals(1 + SnapshotWriter.MAX_TEXT + 3 + 1, quoted.length());
        assertTrue(quoted, quoted.endsWith("...\""));
    }

    private static String lines(String... aLines) {
        StringBuilder text = new StringBuilder();
        for (String line : aLines) {
            text.append(line).append('\n');
        }
        return text.toString();
    }
}
