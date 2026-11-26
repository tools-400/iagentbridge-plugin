/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.tools;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import de.tools400.iagentbridge.mcp.IMcpTool;
import de.tools400.iagentbridge.mcp.McpToolResult;
import de.tools400.iagentbridge.mcp.ToolArguments;
import de.tools400.iagentbridge.ui.forwarding.Forwarding;
import de.tools400.iagentbridge.ui.forwarding.ForwardingClient;
import de.tools400.iagentbridge.ui.forwarding.ForwardingClient.Kind;
import de.tools400.iagentbridge.ui.forwarding.ForwardingClient.Response;
import de.tools400.iagentbridge.ui.forwarding.TestInstance;
import de.tools400.iagentbridge.ui.launches.HotCodeReplace;
import de.tools400.iagentbridge.ui.launches.Identity;
import de.tools400.iagentbridge.ui.launches.LaunchInfo;
import de.tools400.iagentbridge.ui.launches.LaunchProblems;
import de.tools400.iagentbridge.ui.launches.LaunchStarter;
import de.tools400.iagentbridge.ui.launches.LaunchStarter.LaunchException;
import de.tools400.iagentbridge.ui.launches.Launches;
import de.tools400.iagentbridge.ui.launches.PortProbe;
import de.tools400.iagentbridge.ui.launches.RunningLaunch;
import de.tools400.iagentbridge.ui.launches.StartChecks;
import de.tools400.iagentbridge.ui.launches.WorkspaceLock;
import de.tools400.iagentbridge.ui.model.Deadline;
import de.tools400.iagentbridge.ui.swt.UiThread;
import de.tools400.iagentbridge.ui.swt.UiThreadBusyException;

/**
 * Tool 'start_launch': starts a test instance or a JUnit Plug-in Test from the
 * RDi, in which the developer works, without a dialog of RDi, and waits, until
 * the server of the test instance answers.
 */
public class StartLaunchTool implements IMcpTool {

    private static final String DESCRIPTION = "Starts a launch configuration of the RDi, in which the developer works, without a build: " //$NON-NLS-1$
        + "an Eclipse Application, e.g. the test instance 'iAgentBridge (de)', or a JUnit Plug-in Test. With -Diagentbridge.port in its VM " //$NON-NLS-1$
        + "arguments it waits, until that port answers, and names a dialog, that the test instance shows; otherwise, until the process " //$NON-NLS-1$
        + "runs. Before the start it checks, what RDi would ask about in a dialog - errors in the projects of the launch, files with unsaved " //$NON-NLS-1$
        + "changes, a question for a value or for clearing the workspace - and rejects then. Build first with get_problems. In debug mode " //$NON-NLS-1$
        + "hot code replace takes over changed method bodies after get_problems; list_launches shows its result. While it waits, the " //$NON-NLS-1$
        + "server answers no other call."; //$NON-NLS-1$

    private static final String INPUT_SCHEMA = "{\"type\":\"object\",\"properties\":{" //$NON-NLS-1$
        + "\"name\":{\"type\":\"string\",\"description\":\"Name of the launch configuration, e.g. 'iAgentBridge (de)'. list_launches lists them.\"}," //$NON-NLS-1$
        + "\"mode\":{\"type\":\"string\",\"enum\":[\"run\",\"debug\"],\"description\":\"Default: run.\"}," //$NON-NLS-1$
        + "\"timeoutSeconds\":{\"type\":\"integer\",\"description\":\"Time for the whole call, 1 to 600 seconds. Default: 180.\"}}," //$NON-NLS-1$
        + "\"required\":[\"name\"]}"; //$NON-NLS-1$

    private static final String MODE_RUN = "run"; //$NON-NLS-1$
    private static final String MODE_DEBUG = "debug"; //$NON-NLS-1$
    private static final int DEFAULT_TIMEOUT_SECONDS = 180;
    private static final long POLL_MILLIS = 500;
    private static final int SNAPSHOT_TIMEOUT_MILLIS = 15000;

    /** Line of a snapshot with the shells */
    private static final String SHELLS = "shells: "; //$NON-NLS-1$

    /** A shell of that line: title in quotes, reference and flags */
    private static final Pattern SHELL = Pattern.compile("(\"(?:[^\"\\\\]|\\\\.)*\" \\[w\\d+\\])((?: [a-z]+)*)"); //$NON-NLS-1$

    @Override
    public String getName() {
        return "start_launch"; //$NON-NLS-1$
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
    public McpToolResult execute(JsonObject anArguments) throws Exception {

        if (AbstractUiTool.isTestInstance()) {
            return McpToolResult.error("start_launch works in the RDi, in which the developer works, not in a test instance."); //$NON-NLS-1$
        }

        String name = ToolArguments.getString(anArguments, "name"); //$NON-NLS-1$
        String mode = ToolArguments.getChoice(anArguments, "mode", MODE_RUN, MODE_RUN, MODE_DEBUG); //$NON-NLS-1$
        Deadline deadline = Deadline.inSeconds(LaunchToolSupport.timeoutSeconds(anArguments, DEFAULT_TIMEOUT_SECONDS));
        long start = System.currentTimeMillis();

        ILaunchConfiguration configuration = Launches.find(name);
        String rejected = check(configuration);
        if (rejected != null) {
            return McpToolResult.error(rejected);
        }

        if (MODE_DEBUG.equals(mode)) {
            HotCodeReplace.register();
        }

        ILaunch launch;
        try {
            launch = LaunchStarter.launch(configuration, mode, deadline);
        } catch (LaunchException e) {
            return McpToolResult.error(e.getMessage());
        }

        String quoted = LaunchToolSupport.quote(name);
        // The port of the process, with the variables of the VM arguments
        // resolved.
        TestInstance instance = Launches.instance(launch);
        Integer port = instance.getPort();
        if (port == null) {
            if (Launches.hasEnded(launch)) {
                return McpToolResult.error(ended(quoted, launch, "")); //$NON-NLS-1$
            }
            return McpToolResult.success("Started " + quoted + " in " + mode + " mode; its process runs." + debugNote(mode)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        }

        String failure = waitForPort(launch, quoted, port.intValue(), deadline);
        if (failure != null) {
            return McpToolResult.error(failure);
        }

        if (Identity.check(instance, Launches.workspace(configuration)) == Identity.Result.OTHER) {
            launch.terminate();
            return McpToolResult.error("Port " + port + " of " + quoted //$NON-NLS-1$ //$NON-NLS-2$
                + " is in use by another process, so the new instance has no server. It was stopped; stop the other process first."); //$NON-NLS-1$
        }

        return McpToolResult.success("Started " + quoted + " in " + mode + " mode in " + LaunchToolSupport.secondsSince(start) + " s; port " //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            + port + " answers." + dialogNote(instance) + debugNote(mode)); //$NON-NLS-1$
    }

    /**
     * Checks, what RDi would ask about in a dialog, before the start.
     *
     * @return the reason, why it may not start, or <code>null</code>
     */
    private static String check(ILaunchConfiguration aConfiguration) throws Exception {

        LaunchInfo info = Launches.info(aConfiguration);
        // A port with a variable is only known after the start.
        Integer port = info.getInstance().getPort();
        List<RunningLaunch> running = new ArrayList<RunningLaunch>();
        for (ILaunch launch : Launches.running()) {
            running.add(Launches.describe(launch));
        }

        String rejected = StartChecks.check(info, running, LaunchStarter.getWaiting(), port != null && PortProbe.answers(port.intValue()));
        if (rejected == null) {
            String workspace = Launches.workspace(aConfiguration);
            if (workspace != null) {
                rejected = StartChecks.checkWorkspace(info, workspace, WorkspaceLock.isLocked(workspace));
            }
        }
        if (rejected == null) {
            List<IResource> dirtyEditorFiles;
            try {
                dirtyEditorFiles = UiThread.read(PlatformUI.getWorkbench().getDisplay(), AbstractUiTool.UI_TIMEOUT_SECONDS,
                    StartLaunchTool::dirtyEditorFiles);
            } catch (UiThreadBusyException e) {
                return "The UI thread of this RDi is busy, so start_launch cannot check the editors with unsaved changes. Try again later."; //$NON-NLS-1$
            }
            rejected = LaunchProblems.check(aConfiguration, LaunchInfo.TYPE_ECLIPSE_APPLICATION.equals(info.getType()), dirtyEditorFiles);
        }

        return rejected;
    }

    /**
     * Returns the files of the editors with unsaved changes, about which RDi
     * asks in the dialog 'Save and Launch'. Runs in the UI thread.
     */
    private static List<IResource> dirtyEditorFiles() {

        List<IResource> files = new ArrayList<IResource>();
        for (IWorkbenchWindow window : PlatformUI.getWorkbench().getWorkbenchWindows()) {
            for (IWorkbenchPage page : window.getPages()) {
                for (IEditorPart editor : page.getDirtyEditors()) {
                    IFile file = editor.getEditorInput().getAdapter(IFile.class);
                    if (file != null) {
                        files.add(file);
                    }
                }
            }
        }

        return files;
    }

    /**
     * Waits, until the port answers.
     *
     * @return the failure or <code>null</code>
     */
    private static String waitForPort(ILaunch aLaunch, String aQuoted, int aPort, Deadline aDeadline) throws InterruptedException {

        while (!PortProbe.answers(aPort)) {
            if (Launches.hasEnded(aLaunch)) {
                return ended(aQuoted, aLaunch, ", before port " + aPort + " answered"); //$NON-NLS-1$ //$NON-NLS-2$
            }
            if (aDeadline.isExpired()) {
                return aQuoted + " runs, but port " + aPort + " did not answer within " + aDeadline.getSeconds() //$NON-NLS-1$ //$NON-NLS-2$
                    + " s. Maybe its server is turned off in its workspace (iagentbridge.serverEnabled). The launch keeps running."; //$NON-NLS-1$
            }
            Thread.sleep(POLL_MILLIS);
        }

        return null;
    }

    private static String ended(String aQuoted, ILaunch aLaunch, String aWhen) {
        return "The process of " + aQuoted + " ended with exit code " + Launches.exitValue(aLaunch) + aWhen //$NON-NLS-1$ //$NON-NLS-2$
            + ". The view Console of RDi shows its output."; //$NON-NLS-1$
    }

    /**
     * Names a modal dialog of the test instance, e.g. a sign-on, that the
     * developer must answer.
     */
    private static String dialogNote(TestInstance anInstance) {

        if (!anInstance.hasUiTools()) {
            return ""; //$NON-NLS-1$
        }
        try {
            JsonObject arguments = new JsonObject();
            arguments.addProperty("maxNodes", Integer.valueOf(1)); //$NON-NLS-1$
            Response response = new ForwardingClient(ForwardingClient.CONNECT_TIMEOUT_MILLIS, SNAPSHOT_TIMEOUT_MILLIS).send(anInstance,
                Forwarding.token(anInstance, System.getProperties()), "ui_snapshot", arguments); //$NON-NLS-1$
            if (response.getKind() != Kind.ANSWERED || response.getResult().isError()) {
                return ""; //$NON-NLS-1$
            }
            return modalDialogNote(response.getResult().getText());
        } catch (IOException e) {
            // Without a token no note.
        }
        return ""; //$NON-NLS-1$
    }

    /**
     * Returns the note about the modal shells of a snapshot.
     *
     * @param aSnapshot - text of a snapshot
     * @return sentence, that starts with a blank, or an empty string
     */
    static String modalDialogNote(String aSnapshot) {
        String modalShells = modalShells(aSnapshot);
        if (modalShells == null) {
            return ""; //$NON-NLS-1$
        }
        return " The test instance shows a modal dialog, which blocks the UI tools until it is closed (a sign-on is for the developer): " //$NON-NLS-1$
            + modalShells + "."; //$NON-NLS-1$
    }

    /**
     * Returns the modal shells of the line 'shells:' of a snapshot.
     *
     * @param aSnapshot - text of a snapshot
     * @return titles and references, e.g.
     *         <code>"Kennwort eingeben" [w4]</code>, or <code>null</code>, if
     *         no shell is modal
     */
    static String modalShells(String aSnapshot) {

        for (String line : aSnapshot.split("\n")) { //$NON-NLS-1$
            if (line.startsWith(SHELLS)) {
                StringBuilder modal = new StringBuilder();
                Matcher shell = SHELL.matcher(line.substring(SHELLS.length()));
                while (shell.find()) {
                    if ((shell.group(2) + " ").contains(" modal ")) { //$NON-NLS-1$ //$NON-NLS-2$
                        modal.append(modal.length() == 0 ? "" : ", ").append(shell.group(1)); //$NON-NLS-1$ //$NON-NLS-2$
                    }
                }
                return modal.length() == 0 ? null : modal.toString();
            }
        }

        return null;
    }

    private static String debugNote(String aMode) {
        if (!MODE_DEBUG.equals(aMode)) {
            return ""; //$NON-NLS-1$
        }
        return " Hot code replace takes over changed method bodies after get_problems; list_launches shows its result. After a change of " //$NON-NLS-1$
            + "the class hierarchy, signatures or new fields JDT may fail and show a dialog in this RDi: then stop_launch and start_launch."; //$NON-NLS-1$
    }
}
