/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.tools;

import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.jobs.Job;

import de.tools400.iagentbridge.mcp.ToolJobs;

/**
 * Runs the operations of the tools on the resources of the workspace as jobs,
 * while the thread of the server waits.
 */
final class WorkspaceJobs {

    private WorkspaceJobs() {
    }

    /**
     * Runs an operation on the resources of the workspace in a job with the
     * rule of the workspace root, which the view Progress shows and in which
     * the user can cancel it, and waits for its end. The resource change events
     * are sent at the end of the operation.
     *
     * @param aName - name of the job, which the view Progress shows
     * @param anOperation - operation
     * @return result of the job, see {@link ToolJobs#runAndWait(Job)}
     * @throws InterruptedException if the waiting thread has been interrupted;
     *         the job is cancelled
     */
    static IStatus runAndWait(String aName, IWorkspaceRunnable anOperation) throws InterruptedException {

        IWorkspace workspace = ResourcesPlugin.getWorkspace();
        Job job = Job.create(aName, monitor -> workspace.run(anOperation, workspace.getRoot(), IWorkspace.AVOID_UPDATE, monitor));
        job.setRule(workspace.getRoot());
        job.setPriority(Job.BUILD);

        return ToolJobs.runAndWait(job);
    }
}
