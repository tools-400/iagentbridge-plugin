/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.rdimcp.tools;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.internal.corext.fix.CleanUpRefactoring;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.ui.cleanup.ICleanUp;
import org.eclipse.ltk.core.refactoring.CheckConditionsOperation;
import org.eclipse.ltk.core.refactoring.PerformRefactoringOperation;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.RefactoringStatusEntry;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import de.tools400.rdimcp.mcp.IMcpTool;
import de.tools400.rdimcp.mcp.McpToolResult;
import de.tools400.rdimcp.mcp.ToolArguments;

/**
 * Runs 'Source > Clean Up' of RDi on Java files, with the clean-up profile,
 * that applies to their projects: the profile of the project settings or else
 * the profile of the workspace. This is what the menu does, when the wizard
 * uses the configured profiles.
 */
public class CleanUpTool implements IMcpTool {

    private static final String ARGUMENT_FILES = "files"; //$NON-NLS-1$

    private static final String DESCRIPTION = "Runs 'Source > Clean Up' of this RDi (Eclipse) on Java files and saves them. " //$NON-NLS-1$
        + "It uses the clean-up profile, that applies in RDi: the one of the project settings or else the one of the workspace, " //$NON-NLS-1$
        + "including the formatter. The files must belong to an open Java project of the RDi workspace (see workspace_info) " //$NON-NLS-1$
        + "and must not have unsaved changes in an RDi editor. Returns for each file, whether Clean Up changed it."; //$NON-NLS-1$

    private static final String INPUT_SCHEMA = "{\"type\":\"object\",\"properties\":{\"files\":{\"type\":\"array\"," //$NON-NLS-1$
        + "\"items\":{\"type\":\"string\"},\"description\":\"Absolute paths of the .java files in the file system, " //$NON-NLS-1$
        + "e.g. C:/workspaces/project/src/a/B.java\"}},\"required\":[\"files\"]}"; //$NON-NLS-1$

    @Override
    public String getName() {
        return "clean_up"; //$NON-NLS-1$
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
    public McpToolResult execute(JsonObject anArguments) throws CoreException {

        List<String> paths = ToolArguments.getStringList(anArguments, ARGUMENT_FILES);
        IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();

        List<Target> targets = new ArrayList<Target>();
        Set<ICompilationUnit> units = new LinkedHashSet<ICompilationUnit>();
        for (String path : paths) {
            Target target = new Target(path);
            targets.add(target);
            try {
                prepare(root, target);
                units.add(target.unit);
            } catch (IllegalArgumentException e) {
                target.error = e.getMessage();
            } catch (CoreException e) {
                target.error = e.getMessage();
            } catch (IOException e) {
                target.error = e.getMessage();
            }
        }

        List<String> messages = new ArrayList<String>();
        if (!units.isEmpty()) {
            messages = runCleanUp(units);
        }

        JsonArray results = new JsonArray();
        int errorCount = 0;
        for (Target target : targets) {
            JsonObject result = new JsonObject();
            result.addProperty("file", target.path); //$NON-NLS-1$
            if (target.error == null) {
                result.addProperty("project", target.file.getProject().getName()); //$NON-NLS-1$
                result.addProperty("profile", CleanUpProfiles.describe(target.file.getProject())); //$NON-NLS-1$
                result.addProperty("changed", Boolean.valueOf(isChanged(target))); //$NON-NLS-1$
            } else {
                result.addProperty("error", target.error); //$NON-NLS-1$
                errorCount++;
            }
            results.add(result);
        }

        JsonObject response = new JsonObject();
        response.addProperty("workspace", root.getLocation().toOSString()); //$NON-NLS-1$
        response.add("results", results); //$NON-NLS-1$
        if (!messages.isEmpty()) {
            JsonArray messageArray = new JsonArray();
            for (String message : messages) {
                messageArray.add(new JsonPrimitive(message));
            }
            response.add("messages", messageArray); //$NON-NLS-1$
        }

        String text = JsonTexts.toText(response);
        if (errorCount > 0) {
            return McpToolResult.error(text);
        }

        return McpToolResult.success(text);
    }

    /**
     * Finds the compilation unit of a file and remembers its content.
     */
    private static void prepare(IWorkspaceRoot aRoot, Target aTarget) throws CoreException, IOException {

        File localFile = new File(ToolArguments.toLocalPath(aTarget.path));
        if (!localFile.isFile()) {
            throw new IllegalArgumentException("File not found."); //$NON-NLS-1$
        }

        IFile file = findFile(aRoot, localFile.getCanonicalFile());
        if (file == null) {
            throw new IllegalArgumentException("The file does not belong to an open project of the RDi workspace " //$NON-NLS-1$
                + aRoot.getLocation().toOSString() + "."); //$NON-NLS-1$
        }

        // The file may have been changed outside of RDi.
        file.refreshLocal(IResource.DEPTH_ZERO, new NullProgressMonitor());

        IJavaElement element = JavaCore.create(file);
        if (!(element instanceof ICompilationUnit) || !element.exists()) {
            throw new IllegalArgumentException("The file is not a Java source file on the build path of project " //$NON-NLS-1$
                + file.getProject().getName() + "."); //$NON-NLS-1$
        }

        ITextFileBuffer buffer = FileBuffers.getTextFileBufferManager().getTextFileBuffer(file.getFullPath(), LocationKind.IFILE);
        if (buffer != null && buffer.isDirty()) {
            throw new IllegalArgumentException("The file has unsaved changes in an RDi editor."); //$NON-NLS-1$
        }

        aTarget.file = file;
        aTarget.unit = (ICompilationUnit)element;
        aTarget.before = readContents(file);
    }

    private static IFile findFile(IWorkspaceRoot aRoot, File aFile) {

        for (IFile file : aRoot.findFilesForLocationURI(aFile.toURI())) {
            if (file.getProject().isOpen()) {
                return file;
            }
        }

        return null;
    }

    /**
     * Runs Clean Up like the menu with the configured profiles.
     *
     * @return messages of the condition checks, e.g. about compile errors
     */
    private static List<String> runCleanUp(Set<ICompilationUnit> aUnits) throws CoreException {

        CleanUpRefactoring refactoring = new CleanUpRefactoring();
        refactoring.setUseOptionsFromProfile(true);
        for (ICompilationUnit unit : aUnits) {
            refactoring.addCompilationUnit(unit);
        }
        for (ICleanUp cleanUp : JavaPlugin.getDefault().getCleanUpRegistry().createCleanUps()) {
            refactoring.addCleanUp(cleanUp);
        }

        PerformRefactoringOperation operation = new PerformRefactoringOperation(refactoring, CheckConditionsOperation.ALL_CONDITIONS);
        ResourcesPlugin.getWorkspace().run(operation, refactoring.getSchedulingRule(), IWorkspace.AVOID_UPDATE, new NullProgressMonitor());

        List<String> messages = new ArrayList<String>();
        addMessages(messages, operation.getConditionStatus());
        addMessages(messages, operation.getValidationStatus());

        return messages;
    }

    private static void addMessages(List<String> aMessages, RefactoringStatus aStatus) {

        if (aStatus == null) {
            return;
        }

        if (aStatus.hasFatalError()) {
            throw new IllegalStateException("Clean Up failed: " + aStatus.getMessageMatchingSeverity(RefactoringStatus.FATAL)); //$NON-NLS-1$
        }

        for (RefactoringStatusEntry entry : aStatus.getEntries()) {
            if (entry.getSeverity() >= RefactoringStatus.WARNING) {
                aMessages.add(entry.getMessage());
            }
        }
    }

    private static boolean isChanged(Target aTarget) {

        try {
            return !Arrays.equals(aTarget.before, readContents(aTarget.file));
        } catch (CoreException e) {
            return true;
        } catch (IOException e) {
            return true;
        }
    }

    private static byte[] readContents(IFile aFile) throws CoreException, IOException {

        try (InputStream in = aFile.getContents(true)) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) {
                bytes.write(buffer, 0, count);
            }
            return bytes.toByteArray();
        }
    }

    /**
     * A file passed by the client.
     */
    private static class Target {

        private final String path;
        private IFile file;
        private ICompilationUnit unit;
        private byte[] before;
        private String error;

        public Target(String aPath) {
            path = aPath;
        }
    }
}
