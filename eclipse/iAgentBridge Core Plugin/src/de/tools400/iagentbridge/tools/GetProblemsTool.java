/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.tools;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IBuildConfiguration;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IJavaModelMarker;

import com.google.gson.JsonObject;

import de.tools400.iagentbridge.Messages;
import de.tools400.iagentbridge.mcp.IMcpTool;
import de.tools400.iagentbridge.mcp.McpToolResult;
import de.tools400.iagentbridge.mcp.ToolArguments;
import de.tools400.iagentbridge.tools.ProblemReport.Problem;
import de.tools400.iagentbridge.tools.ProblemReport.Severity;

/**
 * Returns the problems, that RDi shows in the view Problems, e.g. compile
 * errors of Java files and errors in MANIFEST.MF, after a build of the
 * projects.
 */
public class GetProblemsTool implements IMcpTool {

    private static final String ARGUMENT_PROJECTS = "projects"; //$NON-NLS-1$
    private static final String ARGUMENT_FILES = "files"; //$NON-NLS-1$
    private static final String ARGUMENT_BUILD = "build"; //$NON-NLS-1$
    private static final String ARGUMENT_MIN_SEVERITY = "minSeverity"; //$NON-NLS-1$
    private static final String ARGUMENT_MAX_PROBLEMS = "maxProblems"; //$NON-NLS-1$

    private static final String BUILD_INCREMENTAL = "incremental"; //$NON-NLS-1$
    private static final String BUILD_FULL = "full"; //$NON-NLS-1$
    private static final String BUILD_NONE = "none"; //$NON-NLS-1$

    private static final int DEFAULT_MAX_PROBLEMS = 200;

    private static final String CANCELED = "The refresh or the build was cancelled in RDi."; //$NON-NLS-1$

    private static final String DESCRIPTION = "Returns the errors and warnings, that RDi (Eclipse) shows in the view Problems " //$NON-NLS-1$
        + "for projects or files of the RDi workspace: compile errors and warnings of Java files, errors of the build path " //$NON-NLS-1$
        + "and of the plug-in development tools, e.g. in MANIFEST.MF, plugin.xml and build.properties. Use it after you " //$NON-NLS-1$
        + "changed files outside of RDi, to check that the code compiles. By default the tool reads the projects from disk " //$NON-NLS-1$
        + "and builds the changes, together with the projects they depend on, before it reads the problems. Without " //$NON-NLS-1$
        + "'projects' and 'files' it checks all open projects of the workspace (see workspace_info). Returns the number of " //$NON-NLS-1$
        + "problems per severity and the problems sorted by severity, file and line, each with file, project, line, message " //$NON-NLS-1$
        + "and marker type. 'warnings' names files with unsaved changes in an RDi editor: their problems are those of the " //$NON-NLS-1$
        + "file on disk. With 'files' the tool returns only the problems of these files and of their projects, e.g. errors " //$NON-NLS-1$
        + "of the build path; errors, that a change causes in other files, e.g. in the callers of a changed method, need " //$NON-NLS-1$
        + "'projects'. If reading from disk or the build fails, e.g. because a builder of a project fails, 'warnings' names " //$NON-NLS-1$
        + "the error, and the problems may be incomplete. The user can cancel the refresh and the build in RDi; then the " //$NON-NLS-1$
        + "tool fails."; //$NON-NLS-1$

    private static final String INPUT_SCHEMA = "{\"type\":\"object\",\"properties\":{" //$NON-NLS-1$
        + "\"projects\":{\"type\":\"array\",\"items\":{\"type\":\"string\"},\"description\":\"Names of open projects " //$NON-NLS-1$
        + "of the RDi workspace. Default: all open projects.\"}," //$NON-NLS-1$
        + "\"files\":{\"type\":\"array\",\"items\":{\"type\":\"string\"},\"description\":\"Absolute paths of files in " //$NON-NLS-1$
        + "the file system, e.g. C:/workspaces/project/src/a/B.java. Returns only the problems of these files and of " //$NON-NLS-1$
        + "their projects; their projects are built. Do not combine with projects.\"}," //$NON-NLS-1$
        + "\"build\":{\"type\":\"string\",\"enum\":[\"incremental\",\"full\",\"none\"],\"description\":\"Build before " //$NON-NLS-1$
        + "the problems are read: incremental (default) reads the projects from disk and builds the changes, full " //$NON-NLS-1$
        + "rebuilds the projects, none reads the problems as RDi shows them now.\"}," //$NON-NLS-1$
        + "\"minSeverity\":{\"type\":\"string\",\"enum\":[\"error\",\"warning\",\"info\"],\"description\":\"Lowest " //$NON-NLS-1$
        + "severity of the problems returned. Default: warning.\"}," //$NON-NLS-1$
        + "\"maxProblems\":{\"type\":\"integer\",\"minimum\":1,\"description\":\"Maximum number of problems returned. " //$NON-NLS-1$
        + "If there are more, truncated is true. Default: 200.\"}}}"; //$NON-NLS-1$

    @Override
    public String getName() {
        return "get_problems"; //$NON-NLS-1$
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
    public McpToolResult execute(JsonObject anArguments) throws CoreException, IOException, InterruptedException {

        List<String> projectNames = ToolArguments.getOptionalStringList(anArguments, ARGUMENT_PROJECTS);
        List<String> paths = ToolArguments.getOptionalStringList(anArguments, ARGUMENT_FILES);
        if (!projectNames.isEmpty() && !paths.isEmpty()) {
            throw new IllegalArgumentException("Pass either 'projects' or 'files', not both."); //$NON-NLS-1$
        }

        String build = ToolArguments.getChoice(anArguments, ARGUMENT_BUILD, BUILD_INCREMENTAL, BUILD_INCREMENTAL, BUILD_FULL, BUILD_NONE);
        Severity minSeverity = Severity.fromLabel(ToolArguments.getChoice(anArguments, ARGUMENT_MIN_SEVERITY, Severity.WARNING.getLabel(),
            Severity.ERROR.getLabel(), Severity.WARNING.getLabel(), Severity.INFO.getLabel()));
        int maxProblems = ToolArguments.getInt(anArguments, ARGUMENT_MAX_PROBLEMS, DEFAULT_MAX_PROBLEMS, 1);

        IWorkspace workspace = ResourcesPlugin.getWorkspace();
        IWorkspaceRoot root = workspace.getRoot();

        Set<IFile> files = findFiles(root, paths);
        Set<IProject> projects;
        if (files.isEmpty()) {
            projects = findProjects(root, projectNames);
        } else {
            projects = new LinkedHashSet<IProject>();
            for (IFile file : files) {
                projects.add(file.getProject());
            }
        }

        ProblemReport report = new ProblemReport(build, minSeverity, maxProblems);
        if (!BUILD_NONE.equals(build)) {
            int kind = BUILD_FULL.equals(build) ? IncrementalProjectBuilder.FULL_BUILD : IncrementalProjectBuilder.INCREMENTAL_BUILD;
            // Two jobs: PDE changes the class path of a project, whose
            // MANIFEST.MF changed, only after the refresh, often in a job of
            // its own. The build job is scheduled after it and runs after it.
            IStatus refreshResult = runJob(Messages.Task_Refreshing_projects, monitor -> refresh(projects, monitor));
            if (refreshResult.getSeverity() == IStatus.CANCEL) {
                return McpToolResult.error(CANCELED);
            }
            IStatus buildResult = runJob(Messages.Task_Building_projects, monitor -> build(workspace, projects, kind, monitor));
            if (buildResult.getSeverity() == IStatus.CANCEL) {
                return McpToolResult.error(CANCELED);
            }
            addJobError(report, "Reading the projects from disk failed", refreshResult); //$NON-NLS-1$
            addJobError(report, "The build failed", buildResult); //$NON-NLS-1$
        }

        // Without the automatic build, that may still be running or that the
        // refresh has started, some markers would be missing.
        Job.getJobManager().join(ResourcesPlugin.FAMILY_AUTO_BUILD, new NullProgressMonitor());

        if (files.isEmpty()) {
            for (IProject project : projects) {
                addProblems(report, project.findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_INFINITE));
            }
        } else {
            // A broken build path stops the Java builder. It removes the
            // problems of all files and adds one to the project.
            for (IProject project : projects) {
                addProblems(report, project.findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_ZERO));
            }
            for (IFile file : files) {
                addProblems(report, file.findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_ZERO));
            }
        }

        for (IFile file : WorkspaceFiles.getFilesWithUnsavedChanges()) {
            if (files.isEmpty() ? projects.contains(file.getProject()) : files.contains(file)) {
                report
                    .addWarning(WorkspaceFiles.getPath(file) + " has unsaved changes in an RDi editor. Its problems are those of the file on disk."); //$NON-NLS-1$
            }
        }

        return report.toToolResult();
    }

    private static Set<IFile> findFiles(IWorkspaceRoot aRoot, List<String> aPaths) throws IOException {

        Set<IFile> files = new LinkedHashSet<IFile>();
        for (String path : aPaths) {
            try {
                files.add(WorkspaceFiles.findFile(aRoot, path));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(path + ": " + e.getMessage(), e); //$NON-NLS-1$
            }
        }

        return files;
    }

    /**
     * Returns the open projects of the given names or all open projects, if
     * there are no names.
     */
    private static Set<IProject> findProjects(IWorkspaceRoot aRoot, List<String> aNames) {

        Set<IProject> projects = new LinkedHashSet<IProject>();
        if (aNames.isEmpty()) {
            for (IProject project : aRoot.getProjects()) {
                if (project.isOpen()) {
                    projects.add(project);
                }
            }
            return projects;
        }

        for (String name : aNames) {
            IProject project = WorkspaceProjects.findOpenProject(aRoot, name);
            if (project == null) {
                throw new IllegalArgumentException("Project '" + name + "' is not an open project of the RDi workspace " //$NON-NLS-1$ //$NON-NLS-2$
                    + aRoot.getLocation().toOSString() + "."); //$NON-NLS-1$
            }
            projects.add(project);
        }

        return projects;
    }

    /**
     * Runs an operation in a job with the rule of the workspace root, which the
     * view Progress shows and in which the user can cancel it, and waits for
     * its end.
     *
     * @return result of the job
     */
    private static IStatus runJob(String aName, IWorkspaceRunnable anOperation) throws InterruptedException {

        IWorkspace workspace = ResourcesPlugin.getWorkspace();
        Job job = Job.create(aName, monitor -> workspace.run(anOperation, workspace.getRoot(), IWorkspace.AVOID_UPDATE, monitor));
        job.setRule(workspace.getRoot());
        job.setPriority(Job.BUILD);

        // The client gets the error in the warnings.
        return ToolJobs.runAndWait(job);
    }

    /**
     * Names the error of a job in the warnings. The problems are still useful:
     * after an error, e.g. of a builder, the build goes on with the other
     * builders and projects.
     */
    private static void addJobError(ProblemReport aReport, String aText, IStatus aResult) {

        if (aResult.matches(IStatus.ERROR)) {
            aReport.addWarning(aText + ", the problems may be incomplete: " + StatusMessages.getMessages(aResult)); //$NON-NLS-1$
        }
    }

    /**
     * Reads the projects and the projects they depend on from disk, because the
     * client changes files outside of RDi.
     */
    private static void refresh(Set<IProject> aProjects, IProgressMonitor aMonitor) throws CoreException {

        Set<IProject> projects = WorkspaceProjects.withReferencedProjects(aProjects);
        SubMonitor progress = SubMonitor.convert(aMonitor, projects.size());
        for (IProject project : projects) {
            project.refreshLocal(IResource.DEPTH_INFINITE, progress.split(1));
        }
    }

    /**
     * Builds the projects and the projects they depend on.
     */
    private static void build(IWorkspace aWorkspace, Set<IProject> aProjects, int aKind, IProgressMonitor aMonitor) throws CoreException {

        List<IBuildConfiguration> configurations = new ArrayList<IBuildConfiguration>();
        for (IProject project : aProjects) {
            configurations.add(project.getActiveBuildConfig());
        }

        aWorkspace.build(configurations.toArray(new IBuildConfiguration[configurations.size()]), aKind, true, aMonitor);
    }

    private static void addProblems(ProblemReport aReport, IMarker[] aMarkers) {

        for (IMarker marker : aMarkers) {
            try {
                IResource resource = marker.getResource();
                Severity severity = toSeverity(marker.getAttribute(IMarker.SEVERITY, IMarker.SEVERITY_INFO));
                String file = WorkspaceFiles.getPath(resource);
                String project = resource.getProject().getName();
                int line = marker.getAttribute(IMarker.LINE_NUMBER, -1);
                String message = marker.getAttribute(IMarker.MESSAGE, ""); //$NON-NLS-1$
                String type = marker.getType();
                Integer id = getJavaProblemId(marker);
                aReport.add(new Problem(severity, file, project, line, message, type, id));
            } catch (CoreException e) {
                // The marker has been deleted in the meantime.
            }
        }
    }

    private static Severity toSeverity(int aMarkerSeverity) {

        switch (aMarkerSeverity) {
        case IMarker.SEVERITY_ERROR:
            return Severity.ERROR;
        case IMarker.SEVERITY_WARNING:
            return Severity.WARNING;
        default:
            return Severity.INFO;
        }
    }

    private static Integer getJavaProblemId(IMarker aMarker) throws CoreException {

        if (!aMarker.isSubtypeOf(IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER)) {
            return null;
        }

        Object id = aMarker.getAttribute(IJavaModelMarker.ID);
        if (id instanceof Integer) {
            return (Integer)id;
        }

        return null;
    }
}
