/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.tools;

/**
 * Describes the clean-up profile, that applies to a project, with the rule of
 * JDT (CleanUpPreferenceUtil.loadOptions): a project with the key
 * 'cleanup_profile' uses its own settings, every other project the profile of
 * the workspace and without that the built-in profile.
 * <p>
 * The class has no Eclipse dependencies, so that it can be tested outside of
 * RDi.
 */
public final class CleanUpProfileNames {

    /** ID of the built-in profile 'Eclipse [built-in]' */
    public static final String BUILT_IN_PROFILE_ID = "org.eclipse.jdt.ui.default.eclipse_clean_up_profile"; //$NON-NLS-1$

    private static final String BUILT_IN_PROFILE_NAME = "Eclipse [built-in]"; //$NON-NLS-1$

    /** Prefix of the IDs of profiles created by the user */
    private static final String USER_PROFILE_PREFIX = "_"; //$NON-NLS-1$

    private CleanUpProfileNames() {
    }

    /**
     * Describes the clean-up profile, that applies to a project.
     *
     * @param aProjectProfileId - value of 'cleanup_profile' in the project
     *        settings or <code>null</code>
     * @param aWorkspaceProfileId - value of 'cleanup_profile' in the workspace
     *        settings or <code>null</code>
     * @return name of the profile and where it is set, e.g. 'iSphere Clean Up
     *         Profile (Helios) (project settings)'
     */
    public static String describe(String aProjectProfileId, String aWorkspaceProfileId) {

        if (aProjectProfileId != null) {
            return toName(aProjectProfileId) + " (project settings)"; //$NON-NLS-1$
        }

        if (aWorkspaceProfileId != null) {
            return toName(aWorkspaceProfileId) + " (workspace settings)"; //$NON-NLS-1$
        }

        return BUILT_IN_PROFILE_NAME + " (default)"; //$NON-NLS-1$
    }

    private static String toName(String aProfileId) {

        if (BUILT_IN_PROFILE_ID.equals(aProfileId)) {
            return BUILT_IN_PROFILE_NAME;
        }

        if (aProfileId.startsWith(USER_PROFILE_PREFIX)) {
            return aProfileId.substring(USER_PROFILE_PREFIX.length());
        }

        return aProfileId;
    }
}
