/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.tools;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.ui.progress.IProgressConstants;

/**
 * Runs the operations of the tools as jobs, which the view Progress shows and
 * in which the user can cancel them, while the thread of the server waits.
 */
final class ToolJobs {

    private ToolJobs() {
    }

    /**
     * Schedules a job and waits for its end. A job, that ends with an error,
     * opens no dialog in RDi: the client gets the error.
     *
     * @param aJob - job, that has not been scheduled yet
     * @return result of the job
     * @throws InterruptedException if the waiting thread has been interrupted,
     *         e.g. because the server stops; the job is cancelled
     */
    static IStatus runAndWait(Job aJob) throws InterruptedException {

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

        return aJob.getResult();
    }
}
