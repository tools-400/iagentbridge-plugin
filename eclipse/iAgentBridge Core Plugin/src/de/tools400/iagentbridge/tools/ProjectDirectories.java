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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Finds the directories of Eclipse projects in the file system, like the wizard
 * 'Existing Projects into Workspace' with the option 'Search for nested
 * projects' turned off: a directory with a file '.project' is a project, and
 * its subdirectories are not searched.
 * <p>
 * The class has no Eclipse dependencies, so that it can be tested outside of
 * RDi.
 */
public final class ProjectDirectories {

    /** Name of the file, that describes an Eclipse project. */
    public static final String PROJECT_FILE = ".project"; //$NON-NLS-1$

    /**
     * Directories, that are not searched: the metadata of a workspace, like the
     * wizard, and the repository of Git, which contains no projects.
     */
    private static final Set<String> SKIPPED_DIRECTORIES = new HashSet<String>(Arrays.asList(".metadata", ".git")); //$NON-NLS-1$ //$NON-NLS-2$

    private ProjectDirectories() {
    }

    /**
     * Returns the directories of the projects in a directory: the directory
     * itself, if it is a project, otherwise the projects in its subdirectories,
     * sorted by path.
     *
     * @param aDirectory - directory, that is searched
     * @param aCanceled - tells, whether the search has been cancelled
     * @return canonical directories of the projects, empty, if there are none
     * @throws IllegalArgumentException if the path is not absolute, does not
     *         exist or is not a directory
     * @throws IOException if the canonical path of a directory cannot be
     *         determined
     * @throws InterruptedException if the search has been cancelled
     */
    public static List<File> find(File aDirectory, BooleanSupplier aCanceled) throws IOException, InterruptedException {

        if (!aDirectory.isAbsolute()) {
            // Relative to the current directory of RDi, not of the client
            throw new IllegalArgumentException("The path is not absolute."); //$NON-NLS-1$
        }
        if (!aDirectory.exists()) {
            throw new IllegalArgumentException("Directory not found."); //$NON-NLS-1$
        }
        if (!aDirectory.isDirectory()) {
            throw new IllegalArgumentException("Not a directory."); //$NON-NLS-1$
        }

        List<File> projects = new ArrayList<File>();
        collect(aDirectory.getCanonicalFile(), new HashSet<String>(), projects, aCanceled);

        return projects;
    }

    private static void collect(File aDirectory, Set<String> aVisited, List<File> aProjects, BooleanSupplier aCanceled)
        throws IOException, InterruptedException {

        if (aCanceled.getAsBoolean()) {
            throw new InterruptedException();
        }

        // A link may lead back to a directory, that has been searched.
        if (!aVisited.add(aDirectory.getPath())) {
            return;
        }

        if (new File(aDirectory, PROJECT_FILE).isFile()) {
            aProjects.add(aDirectory);
            return;
        }

        File[] children = aDirectory.listFiles();
        if (children == null) {
            // Not readable
            return;
        }

        Arrays.sort(children);
        for (File child : children) {
            if (child.isDirectory() && !SKIPPED_DIRECTORIES.contains(child.getName())) {
                collect(child.getCanonicalFile(), aVisited, aProjects, aCanceled);
            }
        }
    }
}
