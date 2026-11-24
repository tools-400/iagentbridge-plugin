/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.tools;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.ICoreRunnable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;

import com.google.gson.JsonObject;

import de.tools400.iagentbridge.Messages;
import de.tools400.iagentbridge.mcp.IMcpTool;
import de.tools400.iagentbridge.mcp.McpToolResult;
import de.tools400.iagentbridge.mcp.ToolArguments;
import de.tools400.iagentbridge.mcp.ToolJobs;

/**
 * Imports existing projects into the RDi workspace, like the wizard 'Existing
 * Projects into Workspace' without copying them.
 */
public class ImportProjectsTool implements IMcpTool {

    private static final String ARGUMENT_PATHS = "paths"; //$NON-NLS-1$

    private static final String DESCRIPTION = "Imports existing Eclipse projects into the RDi (Eclipse) workspace, like " //$NON-NLS-1$
        + "File > Import > General > Existing Projects into Workspace without copying them: the projects stay where they are. " //$NON-NLS-1$
        + "Use it, when a project, that you want to build, test or check with the other tools, is not in the workspace yet " //$NON-NLS-1$
        + "(see workspace_info), e.g. a project of a Git working copy. Each path is the directory of a project, which contains " //$NON-NLS-1$
        + "the file .project, or a directory, below which the tool searches projects like the wizard: it does not search the " //$NON-NLS-1$
        + "directories of the projects it finds, .metadata and .git. Returns for each project 'imported', 'existing' (already " //$NON-NLS-1$
        + "in the workspace at that location) or 'failed' with the reason, e.g. because a project of that name is already in " //$NON-NLS-1$
        + "the workspace at another location. The tool fails, if a project could not be imported or no project was found, " //$NON-NLS-1$
        + "and returns the other projects all the same. Afterwards RDi builds the new projects, if 'Build Automatically' is on; " //$NON-NLS-1$
        + "call get_problems to build them and get their problems. The user can cancel the import in RDi."; //$NON-NLS-1$

    private static final String INPUT_SCHEMA = "{\"type\":\"object\",\"properties\":{" //$NON-NLS-1$
        + "\"paths\":{\"type\":\"array\",\"items\":{\"type\":\"string\"},\"description\":\"Absolute paths of directories in " //$NON-NLS-1$
        + "the file system: directories of projects, e.g. C:/git/repo/my.project, or directories, below which the tool " //$NON-NLS-1$
        + "searches projects, e.g. C:/git/repo.\"}},\"required\":[\"paths\"]}"; //$NON-NLS-1$

    @Override
    public String getName() {
        return "import_projects"; //$NON-NLS-1$
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
    public McpToolResult execute(JsonObject anArguments) throws InterruptedException {

        List<String> paths = ToolArguments.getStringList(anArguments, ARGUMENT_PATHS);

        ImportReport report = new ImportReport();
        // No scheduling rule: RDi can save and build, while the tool searches
        // the projects. Only the import holds the rule of the workspace root.
        // The cast avoids "create(...) is ambiguous" in RDi 9.8 and 9.9.
        Job job = Job.create(Messages.Task_Importing_projects, (ICoreRunnable)monitor -> searchAndImport(paths, report, monitor));
        job.setPriority(Job.LONG);
        IStatus result = ToolJobs.runAndWait(job);
        if (result.getSeverity() == IStatus.CANCEL) {
            report.setCanceled();
        } else if (result.matches(IStatus.ERROR)) {
            report.setError("The import failed: " + StatusMessages.getMessages(result)); //$NON-NLS-1$
        }

        return report.toToolResult();
    }

    /**
     * Searches the projects of the paths and imports them with the rule of the
     * workspace root.
     */
    private static void searchAndImport(List<String> aPaths, ImportReport aReport, IProgressMonitor aMonitor) throws CoreException {

        SubMonitor progress = SubMonitor.convert(aMonitor, 2);

        List<Search> searches = new ArrayList<Search>();
        SubMonitor searchProgress = SubMonitor.convert(progress.split(1), aPaths.size());
        for (String path : aPaths) {
            searches.add(search(path, searchProgress.split(1)));
        }

        IWorkspace workspace = ResourcesPlugin.getWorkspace();
        workspace.run(monitor -> importFound(searches, aReport, monitor), workspace.getRoot(), IWorkspace.AVOID_UPDATE, progress.split(1));
    }

    /**
     * Searches the project of a directory or the projects below it.
     */
    private static Search search(String aPath, IProgressMonitor aMonitor) {

        File directory = new File(ToolArguments.toLocalPath(aPath));
        // A relative path would be shown relative to the current directory of
        // RDi.
        String location = directory.isAbsolute() ? toPath(directory) : aPath;
        try {
            List<File> projectDirectories = ProjectDirectories.find(directory, aMonitor::isCanceled);
            if (projectDirectories.isEmpty()) {
                return new Search(location, null, "No project found: neither the directory nor a directory below it contains a file " //$NON-NLS-1$
                    + ProjectDirectories.PROJECT_FILE + "."); //$NON-NLS-1$
            }
            return new Search(location, projectDirectories, null);
        } catch (InterruptedException e) {
            throw new OperationCanceledException();
        } catch (IllegalArgumentException e) {
            return new Search(location, null, e.getMessage());
        } catch (IOException e) {
            return new Search(location, null, "The directory could not be searched: " + e.getLocalizedMessage()); //$NON-NLS-1$
        }
    }

    /**
     * Imports the projects, that have been found, in the order of the paths.
     */
    private static void importFound(List<Search> aSearches, ImportReport aReport, IProgressMonitor aMonitor) {

        SubMonitor progress = SubMonitor.convert(aMonitor, aSearches.size());
        for (Search search : aSearches) {
            SubMonitor searchProgress = SubMonitor.convert(progress.split(1), search.directories.size());
            if (search.failure != null) {
                aReport.addFailed(null, search.location, search.failure);
            }
            for (File projectDirectory : search.directories) {
                importProject(projectDirectory, aReport, searchProgress.split(1));
            }
        }
    }

    /**
     * Imports a project, like the wizard: the project gets the name of its file
     * .project and stays at its location.
     *
     * @param aDirectory - canonical directory of the project
     */
    private static void importProject(File aDirectory, ImportReport aReport, IProgressMonitor aMonitor) {

        IWorkspace workspace = ResourcesPlugin.getWorkspace();
        IWorkspaceRoot root = workspace.getRoot();
        String location = toPath(aDirectory);

        IProject existing = findProjectAt(root, aDirectory);
        if (existing != null) {
            aReport.addExisting(existing.getName(), location, existing.isOpen() ? null : "The project is closed in RDi."); //$NON-NLS-1$
            return;
        }

        String name = null;
        try {
            IProjectDescription description = workspace.loadProjectDescription(new Path(location).append(ProjectDirectories.PROJECT_FILE));
            name = description.getName();

            IStatus nameStatus = workspace.validateName(name, IResource.PROJECT);
            if (!nameStatus.isOK()) {
                aReport.addFailed(name, location, nameStatus.getMessage());
                return;
            }

            IProject project = root.getProject(name);
            if (project.exists()) {
                aReport.addFailed(name, location, "A project of that name is already in the RDi workspace at " //$NON-NLS-1$
                    + WorkspaceFiles.getPath(project) + "."); //$NON-NLS-1$
                return;
            }

            // loadProjectDescription() sets the location to the directory,
            // unless it is the default location: the directory of the
            // workspace and the name of the project. The wizard takes it so.
            if (description.getLocationURI() != null) {
                IStatus locationStatus = workspace.validateProjectLocationURI(project, description.getLocationURI());
                if (!locationStatus.isOK()) {
                    aReport.addFailed(name, location, locationStatus.getMessage());
                    return;
                }
            }

            SubMonitor progress = SubMonitor.convert(aMonitor, 2);
            project.create(description, progress.split(1));
            try {
                // Unlike the wizard without BACKGROUND_REFRESH: the next tool
                // finds all files of the project.
                project.open(progress.split(1));
            } catch (OperationCanceledException e) {
                aReport.addFailed(name, location, "The import was cancelled after RDi had created the project; it may be closed."); //$NON-NLS-1$
                throw e;
            }
            aReport.addImported(name, WorkspaceFiles.getPath(project));

        } catch (CoreException e) {
            aReport.addFailed(name, location, StatusMessages.getMessages(e.getStatus()));
        }
    }

    /**
     * Returns the project of the workspace, that is stored in a directory.
     *
     * @return project or <code>null</code>
     */
    private static IProject findProjectAt(IWorkspaceRoot aRoot, File aDirectory) {

        for (IContainer container : aRoot.findContainersForLocationURI(aDirectory.toURI())) {
            if (container instanceof IProject) {
                return (IProject)container;
            }
        }

        return null;
    }

    private static String toPath(File aFile) {
        return Path.fromOSString(aFile.getAbsolutePath()).toString();
    }

    /**
     * Result of the search of a path: the directories of the projects or the
     * reason, why there are none.
     */
    private static final class Search {

        private final String location;
        private final List<File> directories;
        private final String failure;

        /**
         * Creates a result.
         *
         * @param aLocation - path, that has been searched
         * @param aDirectories - directories of the projects or
         *        <code>null</code>, if the search failed
         * @param aFailure - reason, why there are no projects, or
         *        <code>null</code>
         */
        private Search(String aLocation, List<File> aDirectories, String aFailure) {
            location = aLocation;
            directories = aDirectories == null ? Collections.<File> emptyList() : aDirectories;
            failure = aFailure;
        }
    }
}
