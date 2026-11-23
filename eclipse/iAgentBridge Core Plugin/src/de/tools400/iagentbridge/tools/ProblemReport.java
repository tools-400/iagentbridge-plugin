/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.tools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import de.tools400.iagentbridge.mcp.McpToolResult;

/**
 * Result of the tool get_problems: the problems sorted by severity, file and
 * line, their number per severity and the warnings of the tool.
 * <p>
 * The class has no Eclipse dependencies, so that it can be tested outside of
 * RDi.
 */
public final class ProblemReport {

    private static final Comparator<Problem> ORDER = (problem1, problem2) -> {

        int result = problem1.severity.compareTo(problem2.severity);
        if (result == 0) {
            result = problem1.file.compareTo(problem2.file);
        }
        if (result == 0) {
            result = Integer.compare(problem1.line, problem2.line);
        }

        return result;
    };

    private final String build;
    private final Severity minSeverity;
    private final int maxProblems;
    private final List<Problem> problems;
    private final List<String> warnings;

    /**
     * Creates an empty report.
     *
     * @param aBuild - build, that ran before the problems were read, e.g.
     *        'incremental'
     * @param aMinSeverity - lowest severity of the problems, that the report
     *        contains
     * @param aMaxProblems - maximum number of problems in the report
     */
    public ProblemReport(String aBuild, Severity aMinSeverity, int aMaxProblems) {
        build = aBuild;
        minSeverity = aMinSeverity;
        maxProblems = aMaxProblems;
        problems = new ArrayList<Problem>();
        warnings = new ArrayList<String>();
    }

    /**
     * Adds a problem, unless its severity is lower than the minimum severity.
     *
     * @param aProblem - problem
     */
    public void add(Problem aProblem) {

        if (aProblem.severity.compareTo(minSeverity) <= 0) {
            problems.add(aProblem);
        }
    }

    /**
     * Adds a warning of the tool, e.g. about a file with unsaved changes.
     *
     * @param aWarning - text of the warning
     */
    public void addWarning(String aWarning) {
        warnings.add(aWarning);
    }

    /**
     * Returns the report as JSON object.
     *
     * @return the report with 'build', 'counts', 'truncated', 'problems' and
     *         'warnings', if there are any
     */
    public JsonObject toJson() {

        List<Problem> sorted = new ArrayList<Problem>(problems);
        Collections.sort(sorted, ORDER);

        JsonObject counts = new JsonObject();
        for (Severity severity : Severity.values()) {
            if (severity.compareTo(minSeverity) <= 0) {
                counts.addProperty(severity.getLabel(), Integer.valueOf(count(severity)));
            }
        }

        JsonArray problemArray = new JsonArray();
        for (Problem problem : sorted.subList(0, Math.min(sorted.size(), maxProblems))) {
            problemArray.add(problem.toJson());
        }

        JsonObject report = new JsonObject();
        report.addProperty("build", build); //$NON-NLS-1$
        report.add("counts", counts); //$NON-NLS-1$
        report.addProperty("truncated", Boolean.valueOf(sorted.size() > maxProblems)); //$NON-NLS-1$
        report.add("problems", problemArray); //$NON-NLS-1$
        if (!warnings.isEmpty()) {
            JsonArray warningArray = new JsonArray();
            for (String warning : warnings) {
                warningArray.add(new JsonPrimitive(warning));
            }
            report.add("warnings", warningArray); //$NON-NLS-1$
        }

        return report;
    }

    /**
     * Returns the report as result of the tool.
     *
     * @return successful result with the report as text
     */
    public McpToolResult toToolResult() {
        return McpToolResult.success(JsonTexts.toText(toJson()));
    }

    private int count(Severity aSeverity) {

        int count = 0;
        for (Problem problem : problems) {
            if (problem.severity == aSeverity) {
                count++;
            }
        }

        return count;
    }

    /**
     * Severity of a problem, from the highest to the lowest.
     */
    public enum Severity {

        ERROR ("error"), //$NON-NLS-1$
        WARNING ("warning"), //$NON-NLS-1$
        INFO ("info"); //$NON-NLS-1$

        private final String label;

        private Severity(String aLabel) {
            label = aLabel;
        }

        /**
         * Returns the name of the severity in the arguments and in the result
         * of the tool.
         *
         * @return 'error', 'warning' or 'info'
         */
        public String getLabel() {
            return label;
        }

        /**
         * Returns the severity of a name.
         *
         * @param aLabel - 'error', 'warning' or 'info'
         * @return severity
         * @throws IllegalArgumentException if the name is unknown
         */
        public static Severity fromLabel(String aLabel) {

            for (Severity severity : values()) {
                if (severity.label.equals(aLabel)) {
                    return severity;
                }
            }

            throw new IllegalArgumentException("Unknown severity: " + aLabel); //$NON-NLS-1$
        }
    }

    /**
     * A problem, that RDi shows in the view Problems.
     */
    public static final class Problem {

        private final Severity severity;
        private final String file;
        private final String project;
        private final int line;
        private final String message;
        private final String type;
        private final Integer id;

        /**
         * Creates a problem.
         *
         * @param aSeverity - severity
         * @param aFile - absolute path of the file, folder or project, that has
         *        the problem
         * @param aProject - name of the project
         * @param aLine - line number, starting at 1, or a value less than 1, if
         *        the problem has no line
         * @param aMessage - text of the problem
         * @param aType - type of the marker, e.g. org.eclipse.jdt.core.problem
         * @param anId - ID of the problem of the Java compiler or
         *        <code>null</code>
         */
        public Problem(Severity aSeverity, String aFile, String aProject, int aLine, String aMessage, String aType, Integer anId) {
            severity = aSeverity;
            file = aFile;
            project = aProject;
            line = aLine;
            message = aMessage;
            type = aType;
            id = anId;
        }

        private JsonObject toJson() {

            JsonObject json = new JsonObject();
            json.addProperty("severity", severity.getLabel()); //$NON-NLS-1$
            json.addProperty("file", file); //$NON-NLS-1$
            json.addProperty("project", project); //$NON-NLS-1$
            if (line > 0) {
                json.addProperty("line", Integer.valueOf(line)); //$NON-NLS-1$
            }
            json.addProperty("message", message); //$NON-NLS-1$
            json.addProperty("type", type); //$NON-NLS-1$
            if (id != null) {
                json.addProperty("id", id); //$NON-NLS-1$
            }

            return json;
        }
    }
}
