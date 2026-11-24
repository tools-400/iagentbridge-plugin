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
import java.util.List;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.IFileBuffer;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.runtime.IPath;

import de.tools400.iagentbridge.mcp.ToolArguments;

/**
 * Finds the files of the RDi workspace, that belong to the paths, which a
 * client passes to a tool, tells, which files have unsaved changes in an editor
 * of RDi, and returns the paths of resources for the client.
 */
final class WorkspaceFiles {

    private WorkspaceFiles() {
    }

    /**
     * Returns the file of an open project of the RDi workspace, that is stored
     * at a path of the local file system.
     *
     * @param aRoot - root of the workspace
     * @param aPath - absolute path passed by the client, also a path of Git
     *        Bash like '/c/dir/file'
     * @return file of the workspace
     * @throws IllegalArgumentException if the file does not exist or does not
     *         belong to an open project of the workspace
     * @throws IOException if the canonical path cannot be determined
     */
    static IFile findFile(IWorkspaceRoot aRoot, String aPath) throws IOException {

        File localFile = new File(ToolArguments.toLocalPath(aPath));
        if (!localFile.isFile()) {
            throw new IllegalArgumentException("File not found."); //$NON-NLS-1$
        }

        for (IFile file : aRoot.findFilesForLocationURI(localFile.getCanonicalFile().toURI())) {
            if (file.getProject().isOpen()) {
                return file;
            }
        }

        throw new IllegalArgumentException("The file does not belong to an open project of the RDi workspace " //$NON-NLS-1$
            + aRoot.getLocation().toOSString() + "."); //$NON-NLS-1$
    }

    /**
     * Tells, whether a file has unsaved changes in an editor of RDi.
     *
     * @param aFile - file of the workspace
     * @return <code>true</code>, if the file has unsaved changes
     */
    static boolean hasUnsavedChanges(IFile aFile) {

        ITextFileBuffer buffer = FileBuffers.getTextFileBufferManager().getTextFileBuffer(aFile.getFullPath(), LocationKind.IFILE);

        return buffer != null && buffer.isDirty();
    }

    /**
     * Returns the files of the workspace, that have unsaved changes in an
     * editor of RDi.
     *
     * @return files with unsaved changes
     */
    static List<IFile> getFilesWithUnsavedChanges() {

        List<IFile> files = new ArrayList<IFile>();
        for (IFileBuffer buffer : FileBuffers.getTextFileBufferManager().getFileBuffers()) {
            if (buffer.isDirty()) {
                IFile file = FileBuffers.getWorkspaceFileAtLocation(buffer.getLocation());
                if (file != null) {
                    files.add(file);
                }
            }
        }

        return files;
    }

    /**
     * Returns the path of a resource, that a tool returns to the client.
     *
     * @param aResource - file, folder or project
     * @return absolute path in the local file system or, if the resource has
     *         none, its path in the workspace
     */
    static String getPath(IResource aResource) {

        IPath location = aResource.getLocation();
        if (location == null) {
            return aResource.getFullPath().toString();
        }

        return location.toString();
    }
}
