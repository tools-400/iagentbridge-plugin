/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.tools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.ICoreRunnable;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchDelegate;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.jdt.core.IJavaModelMarker;
import org.eclipse.jdt.junit.launcher.JUnitLaunchConfigurationDelegate;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.osgi.util.NLS;

import com.google.gson.JsonObject;

import de.tools400.iagentbridge.Messages;
import de.tools400.iagentbridge.mcp.IMcpTool;
import de.tools400.iagentbridge.mcp.McpToolResult;
import de.tools400.iagentbridge.mcp.ToolArguments;
import de.tools400.iagentbridge.mcp.ToolJobs;

/**
 * Runs the tests of a JUnit or JUnit Plug-in Test launch configuration of RDi
 * and returns their results.
 */
public class RunTestsTool implements IMcpTool {

    private static final String ARGUMENT_LAUNCH = "launch"; //$NON-NLS-1$
    private static final String ARGUMENT_TIMEOUT_SECONDS = "timeoutSeconds"; //$NON-NLS-1$
    private static final String ARGUMENT_MAX_FAILURES = "maxFailures"; //$NON-NLS-1$

    private static final int DEFAULT_TIMEOUT_SECONDS = 600;
    private static final int DEFAULT_MAX_FAILURES = 20;

    private static final Set<String> RUN_MODE = Collections.singleton(ILaunchManager.RUN_MODE);

    private static final String CANCELED = "The test run was cancelled in RDi."; //$NON-NLS-1$

    private static final String DESCRIPTION = "Runs the tests of a JUnit or JUnit Plug-in Test launch configuration of RDi " //$NON-NLS-1$
        + "(Eclipse), like Run in RDi, waits for their end and returns the results: 'result' passed or failed, the number of " //$NON-NLS-1$
        + "tests run, of failures (failed assertions), errors (exceptions) and ignored tests, the duration and for each " //$NON-NLS-1$
        + "failed test the class, the method, expected and actual value of a failed comparison and the stack trace without " //$NON-NLS-1$
        + "the frames of JUnit, cut after 30 lines. A failure of a test class, e.g. in a BeforeClass method, has no method. " //$NON-NLS-1$
        + "Use it after you changed code or tests outside of RDi, to check that the tests pass. The tool runs the classes, " //$NON-NLS-1$
        + "that RDi has built: call get_problems first, which reads the changed files from disk and builds them. If the " //$NON-NLS-1$
        + "project of the launch configuration or a project it depends on has compile errors or files with unsaved changes " //$NON-NLS-1$
        + "in an RDi editor, the tool does not start the tests, because RDi would ask in a dialog. If the tests do not end " //$NON-NLS-1$
        + "within timeoutSeconds, the tool terminates the launch and fails with result 'timeout' and 'running', the tests " //$NON-NLS-1$
        + "that were still running. It also fails, if the launch ends before all tests ran (result 'aborted', e.g. the test " //$NON-NLS-1$
        + "VM could not start or a test called System.exit()), or if the user stops or cancels the test run in RDi."; //$NON-NLS-1$

    private static final String INPUT_SCHEMA = "{\"type\":\"object\",\"properties\":{" //$NON-NLS-1$
        + "\"launch\":{\"type\":\"string\",\"description\":\"Name of a JUnit or JUnit Plug-in Test launch configuration " //$NON-NLS-1$
        + "of RDi, as RDi shows it in Run > Run Configurations, e.g. 'iAgentBridge JUnit'.\"}," //$NON-NLS-1$
        + "\"timeoutSeconds\":{\"type\":\"integer\",\"minimum\":1,\"description\":\"Maximum duration of the test run in " //$NON-NLS-1$
        + "seconds, from the launch to the end of the last test, including the start of the test VM. Default: 600.\"}," //$NON-NLS-1$
        + "\"maxFailures\":{\"type\":\"integer\",\"minimum\":1,\"description\":\"Maximum number of failed tests returned. " //$NON-NLS-1$
        + "If more tests fail, truncated is true. Default: 20.\"}},\"required\":[\"launch\"]}"; //$NON-NLS-1$

    @Override
    public String getName() {
        return "run_tests"; //$NON-NLS-1$
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    public JsonObject getInputSchema() {
        return JsonTexts.parse(INPUT_SCHEMA).getAsJsonObject();
    }

    @Override
    public McpToolResult execute(JsonObject anArguments) throws CoreException, InterruptedException {

        String name = ToolArguments.getString(anArguments, ARGUMENT_LAUNCH);
        int timeoutSeconds = ToolArguments.getInt(anArguments, ARGUMENT_TIMEOUT_SECONDS, DEFAULT_TIMEOUT_SECONDS, 1);
        int maxFailures = ToolArguments.getInt(anArguments, ARGUMENT_MAX_FAILURES, DEFAULT_MAX_FAILURES, 1);

        ILaunchConfiguration configuration = findConfiguration(name);

        String reason = getReasonForDialog(configuration);
        if (reason != null) {
            return McpToolResult.error("The tests were not started, because RDi would ask in a dialog first. " + reason); //$NON-NLS-1$
        }

        TestRun testRun = new TestRun(configuration, timeoutSeconds, maxFailures);
        IStatus result = runJob(NLS.bind(Messages.Task_Running_tests_A, name), testRun);
        if (result.getSeverity() == IStatus.CANCEL) {
            return McpToolResult.error(CANCELED);
        }
        if (result.matches(IStatus.ERROR)) {
            return McpToolResult.error("The launch failed: " + StatusMessages.getMessages(result)); //$NON-NLS-1$
        }

        return testRun.getReport().toToolResult();
    }

    /**
     * Returns the JUnit launch configuration of the given name.
     *
     * @throws IllegalArgumentException if there is no launch configuration of
     *         that name or if it is no JUnit launch configuration
     */
    private static ILaunchConfiguration findConfiguration(String aName) throws CoreException {

        ILaunchConfiguration[] configurations = DebugPlugin.getDefault().getLaunchManager().getLaunchConfigurations();
        Map<String, Boolean> junitTypes = new HashMap<String, Boolean>();
        for (ILaunchConfiguration configuration : configurations) {
            if (configuration.getName().equals(aName)) {
                if (isJUnit(configuration.getType(), junitTypes)) {
                    return configuration;
                }
                throw new IllegalArgumentException("Launch configuration '" + aName + "' is of type '" + configuration.getType().getName() //$NON-NLS-1$ //$NON-NLS-2$
                    + "', not of type JUnit or JUnit Plug-in Test. " + listJUnitConfigurations(configurations, junitTypes)); //$NON-NLS-1$
            }
        }

        throw new IllegalArgumentException("Launch configuration '" + aName + "' not found. " //$NON-NLS-1$ //$NON-NLS-2$
            + listJUnitConfigurations(configurations, junitTypes));
    }

    private static String listJUnitConfigurations(ILaunchConfiguration[] aConfigurations, Map<String, Boolean> aJUnitTypes) throws CoreException {

        Set<String> names = new TreeSet<String>();
        for (ILaunchConfiguration configuration : aConfigurations) {
            if (isJUnit(configuration.getType(), aJUnitTypes)) {
                names.add(configuration.getName());
            }
        }

        if (names.isEmpty()) {
            return "RDi has no JUnit launch configurations."; //$NON-NLS-1$
        }

        return "JUnit launch configurations of RDi: " + String.join(", ", names) + "."; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    /**
     * Returns whether a launch configuration type runs JUnit tests: whether its
     * delegate is the one of JDT or a subclass, e.g. the one of a JUnit Plug-in
     * Test. The constants of the attributes of a JUnit launch configuration are
     * not API in RDi 9.6.
     *
     * @param aType - launch configuration type
     * @param aCache - the types checked so far, by ID
     */
    private static boolean isJUnit(ILaunchConfigurationType aType, Map<String, Boolean> aCache) throws CoreException {

        Boolean junit = aCache.get(aType.getIdentifier());
        if (junit == null) {
            junit = Boolean.FALSE;
            for (ILaunchDelegate delegate : aType.getDelegates(RUN_MODE)) {
                if (delegate.getDelegate() instanceof JUnitLaunchConfigurationDelegate) {
                    junit = Boolean.TRUE;
                }
            }
            aCache.put(aType.getIdentifier(), junit);
        }

        return junit.booleanValue();
    }

    /**
     * Returns, why RDi would ask in a dialog before the launch: compile errors
     * or files with unsaved changes in the project of the launch configuration
     * or in the projects, that it references. JDT checks the same projects, PDE
     * checks all plug-ins of the workspace, that a JUnit Plug-in Test launches.
     *
     * @return reason or <code>null</code>
     */
    private static String getReasonForDialog(ILaunchConfiguration aConfiguration) throws CoreException {

        IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
        String projectName = aConfiguration.getAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, ""); //$NON-NLS-1$
        IProject project = WorkspaceProjects.findOpenProject(root, projectName);
        if (project == null) {
            // The launch reports a missing project.
            return null;
        }

        Set<IProject> projects = WorkspaceProjects.withReferencedProjects(Collections.singleton(project));
        List<String> reasons = new ArrayList<String>();
        for (IProject referenced : projects) {
            int errors = countCompileErrors(referenced);
            if (errors > 0) {
                reasons.add("Project '" + referenced.getName() + "' has compile errors (" + errors //$NON-NLS-1$ //$NON-NLS-2$
                    + "). Fix them first; get_problems returns them."); //$NON-NLS-1$
            }
        }
        for (IFile file : WorkspaceFiles.getFilesWithUnsavedChanges()) {
            if (projects.contains(file.getProject())) {
                reasons.add(WorkspaceFiles.getPath(file) + " has unsaved changes in an RDi editor. Save or discard them in RDi first."); //$NON-NLS-1$
            }
        }

        if (reasons.isEmpty()) {
            return null;
        }

        return String.join(" ", reasons); //$NON-NLS-1$
    }

    /**
     * Counts the compile errors of a project, like JDT before a launch: the
     * problems of the Java compiler with the severity error.
     */
    private static int countCompileErrors(IProject aProject) throws CoreException {

        int count = 0;
        for (IMarker marker : aProject.findMarkers(IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER, false, IResource.DEPTH_INFINITE)) {
            if (marker.getAttribute(IMarker.SEVERITY, IMarker.SEVERITY_INFO) == IMarker.SEVERITY_ERROR) {
                count++;
            }
        }

        return count;
    }

    /**
     * Runs the tests in a job, which the view Progress shows and in which the
     * user can cancel them, and waits for its end.
     *
     * @return result of the job
     */
    private static IStatus runJob(String aName, TestRun aTestRun) throws InterruptedException {

        // The cast avoids "create(...) is ambiguous" in RDi 9.8 and 9.9.
        Job job = Job.create(aName, (ICoreRunnable)monitor -> aTestRun.run(monitor));
        job.setPriority(Job.LONG);

        return ToolJobs.runAndWait(job);
    }
}
