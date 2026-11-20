/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.tools;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ProjectScope;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jdt.ui.JavaUI;

/**
 * Tells, which clean-up profile applies to a project: the one of the project
 * settings or the one of the workspace.
 */
final class CleanUpProfiles {

    /** Key of the active profile in the preferences of 'org.eclipse.jdt.ui' */
    private static final String KEY_PROFILE = "cleanup_profile"; //$NON-NLS-1$

    private CleanUpProfiles() {
    }

    /**
     * Describes the clean-up profile, that applies to a project.
     *
     * @param aProject - Java project
     * @return name of the profile and where it is set, e.g. 'iSphere Clean Up
     *         Profile (Helios) (project settings)'
     */
    static String describe(IProject aProject) {

        String projectProfileId = new ProjectScope(aProject).getNode(JavaUI.ID_PLUGIN).get(KEY_PROFILE, null);
        String workspaceProfileId = InstanceScope.INSTANCE.getNode(JavaUI.ID_PLUGIN).get(KEY_PROFILE, null);

        return CleanUpProfileNames.describe(projectProfileId, workspaceProfileId);
    }
}
