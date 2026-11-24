/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.mcp;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.ui.progress.IProgressConstants;

/**
 * Runs the work of a tool as a job, which the view Progress shows and in which
 * the user can cancel it, while the thread of the server waits for its end.
 *
 * @since 1.1.0
 */
public final class ToolJobs {

    private ToolJobs() {
    }

    /**
     * Schedules a job and waits for its end. A job, that ends with an error,
     * opens no dialog in RDi: the client gets the error.
     * <ul>
     * <li>The job must not have been scheduled. Its rule, priority and kind are
     * up to the caller, e.g. a <code>WorkspaceJob</code>.</li>
     * <li>Do not call <code>setUser(true)</code>: Eclipse opens a progress
     * dialog for a user job, unless the user lets such jobs run in the
     * background.</li>
     * <li>Never call the method in the UI thread. The server calls the tools in
     * a thread of its own.</li>
     * </ul>
     *
     * @param aJob - job, that has not been scheduled yet
     * @return result of the job, also of a job, that has been cancelled while
     *         it ran; {@link Status#CANCEL_STATUS}, if the job has been
     *         cancelled before it ran
     * @throws InterruptedException if the waiting thread has been interrupted,
     *         e.g. because the server stops; the job is cancelled, but the
     *         method does not wait for its end
     */
    public static IStatus runAndWait(Job aJob) throws InterruptedException {

        // Without the property RDi opens a dialog for every call with an error.
        aJob.setProperty(IProgressConstants.NO_IMMEDIATE_ERROR_PROMPT_PROPERTY, Boolean.TRUE);
        aJob.schedule();
        try {
            aJob.join();
        } catch (InterruptedException e) {
            // E.g. 'New Token' restarts the server. Nobody waits for the
            // result any more, and a test run would go on until its timeout.
            aJob.cancel();
            throw e;
        }

        // A job, that is cancelled before it runs, e.g. while it waits for
        // its rule, has no result.
        IStatus result = aJob.getResult();
        if (result == null) {
            return Status.CANCEL_STATUS;
        }

        return result;
    }
}
