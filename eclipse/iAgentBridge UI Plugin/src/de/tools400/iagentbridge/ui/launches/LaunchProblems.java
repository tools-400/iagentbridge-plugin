/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.launches;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.IFileBuffer;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Platform;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.osgi.framework.Bundle;

/**
 * Checks, what RDi asks about in a dialog, before it launches: errors in the
 * projects of the launch and files of them with unsaved changes in a file
 * buffer or in an editor. The projects are the ones, that PDE checks itself
 * (LaunchPluginValidator.getAffectedProjects, internal, by reflection): the
 * plug-ins of the configuration, the feature projects and External Plug-in
 * Libraries. Without that method all plug-in and feature projects count.
 */
public final class LaunchProblems {

    private static final String JAVA_PROBLEM = "org.eclipse.jdt.core.problem"; //$NON-NLS-1$
    private static final String PDE_PROBLEM = "org.eclipse.pde.core.problem"; //$NON-NLS-1$
    private static final String PLUGIN_NATURE = "org.eclipse.pde.PluginNature"; //$NON-NLS-1$
    private static final String FEATURE_NATURE = "org.eclipse.pde.FeatureNature"; //$NON-NLS-1$
    private static final int MAX_NAMES = 5;

    private LaunchProblems() {
    }

    /**
     * Checks the projects of a launch.
     *
     * @param aConfiguration - the launch configuration
     * @param aPdeProblems - <code>true</code>, if also the problems of PDE
     *        count (Eclipse Application)
     * @param aDirtyEditorFiles - the files of the editors with unsaved changes:
     *        a change on a form page of the editor of a MANIFEST.MF leaves its
     *        file buffer unchanged (tested in RDi 9.6)
     * @return the reason, why RDi would ask, or <code>null</code>
     * @throws CoreException if the markers cannot be read
     */
    public static String check(ILaunchConfiguration aConfiguration, boolean aPdeProblems, List<IResource> aDirtyEditorFiles) throws CoreException {

        Set<IProject> projects = affectedProjects(aConfiguration);

        List<String> withErrors = new ArrayList<String>();
        for (IProject project : projects) {
            if (project.isOpen() && (hasErrors(project, JAVA_PROBLEM) || aPdeProblems && hasErrors(project, PDE_PROBLEM))) {
                withErrors.add(project.getName());
            }
        }
        if (!withErrors.isEmpty()) {
            return "Projects of the launch have errors, about which RDi would ask in a dialog: " + names(withErrors) //$NON-NLS-1$
                + ". Fix them (get_problems shows them) or start the launch by hand."; //$NON-NLS-1$
        }

        List<IResource> dirty = new ArrayList<IResource>(aDirtyEditorFiles);
        for (IFileBuffer buffer : FileBuffers.getTextFileBufferManager().getFileBuffers()) {
            IResource resource = buffer.isDirty() ? resource(buffer.getLocation()) : null;
            if (resource != null) {
                dirty.add(resource);
            }
        }
        Set<String> unsaved = new LinkedHashSet<String>();
        for (IResource resource : dirty) {
            if (projects.contains(resource.getProject())) {
                unsaved.add(resource.getFullPath().toString());
            }
        }
        if (!unsaved.isEmpty()) {
            return "Files of the launch have unsaved changes in an editor, about which RDi would ask in a dialog: " //$NON-NLS-1$
                + names(new ArrayList<String>(unsaved)) + ". Save them first."; //$NON-NLS-1$
        }

        return null;
    }

    private static Set<IProject> affectedProjects(ILaunchConfiguration aConfiguration) {

        try {
            Bundle bundle = Platform.getBundle("org.eclipse.pde.launching"); //$NON-NLS-1$
            if (bundle != null) {
                Class<?> validator = bundle.loadClass("org.eclipse.pde.internal.launching.launcher.LaunchPluginValidator"); //$NON-NLS-1$
                Method method = validator.getMethod("getAffectedProjects", ILaunchConfiguration.class); //$NON-NLS-1$
                return new LinkedHashSet<IProject>(Arrays.asList((IProject[])method.invoke(null, aConfiguration)));
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            // All plug-in and feature projects.
        }

        Set<IProject> projects = new LinkedHashSet<IProject>();
        for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
            try {
                if (project.isOpen() && (project.hasNature(PLUGIN_NATURE) || project.hasNature(FEATURE_NATURE))) {
                    projects.add(project);
                }
            } catch (CoreException e) {
                // A project, whose natures cannot be read, is no plug-in.
            }
        }
        return projects;
    }

    private static boolean hasErrors(IProject aProject, String aType) throws CoreException {
        for (IMarker marker : aProject.findMarkers(aType, true, IResource.DEPTH_INFINITE)) {
            if (marker.getAttribute(IMarker.SEVERITY, IMarker.SEVERITY_INFO) == IMarker.SEVERITY_ERROR) {
                return true;
            }
        }
        return false;
    }

    private static IResource resource(IPath aLocation) {
        return aLocation == null ? null : ResourcesPlugin.getWorkspace().getRoot().findMember(aLocation);
    }

    private static String names(List<String> aNames) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < Math.min(MAX_NAMES, aNames.size()); i++) {
            text.append(i == 0 ? "" : ", ").append(aNames.get(i)); //$NON-NLS-1$ //$NON-NLS-2$
        }
        if (aNames.size() > MAX_NAMES) {
            text.append(" and ").append(aNames.size() - MAX_NAMES).append(" more"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        return text.toString();
    }
}
