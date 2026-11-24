/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.tools;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import de.tools400.iagentbridge.mcp.McpToolResult;

/**
 * Result of the tool import_projects: for each project, whether it has been
 * imported, was already in the workspace or could not be imported.
 * <p>
 * The class has no Eclipse dependencies, so that it can be tested outside of
 * RDi.
 */
public final class ImportReport {

    private final List<Entry> entries;
    private boolean canceled;
    private String error;

    /**
     * Creates an empty report.
     */
    public ImportReport() {
        entries = new ArrayList<Entry>();
    }

    /**
     * Adds a project, that has been imported.
     *
     * @param aProject - name of the project
     * @param aLocation - location of the project
     */
    public void addImported(String aProject, String aLocation) {
        entries.add(new Entry(Result.IMPORTED, aProject, aLocation, null));
    }

    /**
     * Adds a project, that was already in the workspace at the same location.
     *
     * @param aProject - name of the project in the workspace
     * @param aLocation - location of the project
     * @param aMessage - note, e.g. that the project is closed, or
     *        <code>null</code>
     */
    public void addExisting(String aProject, String aLocation, String aMessage) {
        entries.add(new Entry(Result.EXISTING, aProject, aLocation, aMessage));
    }

    /**
     * Adds a directory, that could not be imported.
     *
     * @param aProject - name of the project or <code>null</code>, if it is not
     *        known
     * @param aLocation - directory of the project or directory, that has been
     *        searched
     * @param aMessage - reason
     */
    public void addFailed(String aProject, String aLocation, String aMessage) {
        entries.add(new Entry(Result.FAILED, aProject, aLocation, aMessage));
    }

    /**
     * Tells the report, that the user has cancelled the import. The projects of
     * the report have been handled before.
     */
    public void setCanceled() {
        canceled = true;
    }

    /**
     * Tells the report, that the import has ended with an error. The projects
     * of the report have been handled before.
     *
     * @param aMessage - error
     */
    public void setError(String aMessage) {
        error = aMessage;
    }

    /**
     * Tells, whether all projects have been imported or were already in the
     * workspace.
     *
     * @return <code>true</code>, if no project failed and the import has
     *         neither been cancelled nor ended with an error
     */
    public boolean isCompleted() {
        return !canceled && error == null && count(Result.FAILED) == 0;
    }

    /**
     * Returns the report as JSON object.
     *
     * @return the report with 'counts', 'projects', 'cancelled', if the import
     *         has been cancelled, and 'error', if it has ended with an error
     */
    public JsonObject toJson() {

        JsonObject counts = new JsonObject();
        for (Result result : Result.values()) {
            counts.addProperty(result.getLabel(), Integer.valueOf(count(result)));
        }

        JsonArray projectArray = new JsonArray();
        for (Entry entry : entries) {
            projectArray.add(entry.toJson());
        }

        JsonObject report = new JsonObject();
        report.add("counts", counts); //$NON-NLS-1$
        report.add("projects", projectArray); //$NON-NLS-1$
        if (canceled) {
            report.addProperty("cancelled", Boolean.TRUE); //$NON-NLS-1$
        }
        if (error != null) {
            report.addProperty("error", error); //$NON-NLS-1$
        }

        return report;
    }

    /**
     * Returns the report as result of the tool.
     *
     * @return the report as text; an error, unless the import is complete
     */
    public McpToolResult toToolResult() {

        String text = JsonTexts.toText(toJson());
        if (!isCompleted()) {
            return McpToolResult.error(text);
        }

        return McpToolResult.success(text);
    }

    private int count(Result aResult) {

        int count = 0;
        for (Entry entry : entries) {
            if (entry.result == aResult) {
                count++;
            }
        }

        return count;
    }

    /**
     * Result of the import of a project.
     */
    private enum Result {
        IMPORTED ("imported"), //$NON-NLS-1$
        EXISTING ("existing"), //$NON-NLS-1$
        FAILED ("failed"); //$NON-NLS-1$

        private final String label;

        private Result(String aLabel) {
            label = aLabel;
        }

        private String getLabel() {
            return label;
        }
    }

    /**
     * A project of the report.
     */
    private static final class Entry {

        private final Result result;
        private final String project;
        private final String location;
        private final String message;

        private Entry(Result aResult, String aProject, String aLocation, String aMessage) {
            result = aResult;
            project = aProject;
            location = aLocation;
            message = aMessage;
        }

        private JsonObject toJson() {

            JsonObject json = new JsonObject();
            json.addProperty("result", result.getLabel()); //$NON-NLS-1$
            if (project != null) {
                json.addProperty("project", project); //$NON-NLS-1$
            }
            json.addProperty("location", location); //$NON-NLS-1$
            if (message != null) {
                json.addProperty("message", message); //$NON-NLS-1$
            }

            return json;
        }
    }
}
