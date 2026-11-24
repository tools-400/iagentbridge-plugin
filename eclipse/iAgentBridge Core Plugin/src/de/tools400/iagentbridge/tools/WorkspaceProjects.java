/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.tools;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.runtime.CoreException;

/**
 * Finds the projects of the RDi workspace, that a tool works with.
 */
final class WorkspaceProjects {

    private WorkspaceProjects() {
    }

    /**
     * Returns the open project of the given name.
     *
     * @param aRoot - root of the workspace
     * @param aName - name of the project
     * @return project or <code>null</code>, if there is no open project of that
     *         name
     */
    static IProject findOpenProject(IWorkspaceRoot aRoot, String aName) {

        // IWorkspaceRoot.getProject() throws an exception for a name of more
        // than one segment like 'a/b'.
        for (IProject project : aRoot.getProjects()) {
            if (project.isOpen() && project.getName().equals(aName)) {
                return project;
            }
        }

        return null;
    }

    /**
     * Returns the projects and the open projects, that they reference directly
     * or indirectly, e.g. the projects on the Java build path, which JDT sets
     * as dynamic references.
     *
     * @param aProjects - open projects
     * @return the projects and the projects, that they reference
     * @throws CoreException if the references of a project cannot be read
     */
    static Set<IProject> withReferencedProjects(Set<IProject> aProjects) throws CoreException {

        Set<IProject> projects = new LinkedHashSet<IProject>(aProjects);
        List<IProject> pending = new ArrayList<IProject>(aProjects);
        while (!pending.isEmpty()) {
            IProject project = pending.remove(pending.size() - 1);
            for (IProject referenced : project.getReferencedProjects()) {
                if (referenced.isOpen() && projects.add(referenced)) {
                    pending.add(referenced);
                }
            }
        }

        return projects;
    }
}
