/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.launches;

import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.ICoreRunnable;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.osgi.util.NLS;

import de.tools400.iagentbridge.ui.Messages;
import de.tools400.iagentbridge.ui.model.Deadline;

/**
 * Launches a configuration in a job without build, like run_tests. A
 * configuration of PDE starts as a copy of a working copy without the check of
 * the plug-ins of PDE: no dialog, and what PDE saves, stays in memory; the
 * .launch file is not written. A launch, that still waits after the deadline,
 * e.g. for an answer in a dialog, is remembered, until its job ends.
 */
public final class LaunchStarter {

    private static final String ATTR_AUTOMATIC_VALIDATE = "automaticValidate"; //$NON-NLS-1$

    private static volatile String waiting;

    private LaunchStarter() {
    }

    /**
     * Returns the name of the configuration, whose start still waits.
     *
     * @return name or <code>null</code>
     */
    public static String getWaiting() {
        return waiting;
    }

    /**
     * Launches a configuration.
     *
     * @param aConfiguration - the configuration
     * @param aMode - 'run' or 'debug'
     * @param aDeadline - deadline of the call
     * @return the launch with a process
     * @throws LaunchException if the launch did not start
     * @throws InterruptedException if the thread is interrupted
     * @throws CoreException if the working copy cannot be created
     */
    public static ILaunch launch(ILaunchConfiguration aConfiguration, String aMode, Deadline aDeadline)
        throws LaunchException, InterruptedException, CoreException {

        String name = aConfiguration.getName();
        ILaunchConfigurationWorkingCopy copy = aConfiguration.getWorkingCopy();
        copy.setAttribute(ATTR_AUTOMATIC_VALIDATE, false);
        // PDE saves the configuration, that it launches, e.g. when it sets a
        // missing source path provider. A copy of the copy saves into the copy
        // in memory, never into the file.
        ILaunchConfigurationWorkingCopy nested = copy.getWorkingCopy();

        AtomicReference<Object> outcome = new AtomicReference<Object>();
        Job job = Job.create(NLS.bind(Messages.Task_Starting_A, name), (ICoreRunnable)aMonitor -> {
            try {
                outcome.set(nested.launch(aMode, aMonitor));
            } catch (Throwable e) {
                // Also an Error of a delegate: the job ends, and the tool
                // must not report a dialog.
                outcome.set(e);
            } finally {
                if (name.equals(waiting)) {
                    waiting = null;
                }
            }
        });
        // Set before the job runs, which clears it at its end, also after the
        // deadline.
        waiting = name;
        job.schedule();
        job.join(Math.max(1, aDeadline.remainingMillis()), null);

        Object result = outcome.get();
        if (result == null) {
            throw new LaunchException("RDi is probably waiting for an answer in a dialog: the start of \"" + name //$NON-NLS-1$
                + "\" did not return within the time. Answer the dialog in RDi; until then start_launch starts nothing."); //$NON-NLS-1$
        }
        if (result instanceof Throwable) {
            throw new LaunchException("The start of \"" + name + "\" failed: " + result); //$NON-NLS-1$ //$NON-NLS-2$
        }

        ILaunch launch = (ILaunch)result;
        if (launch.getProcesses().length == 0) {
            throw new LaunchException("RDi did not start \"" + name + "\" (a dialog cancelled?)."); //$NON-NLS-1$ //$NON-NLS-2$
        }

        return launch;
    }

    /**
     * A launch, that did not start.
     */
    public static final class LaunchException extends Exception {

        private static final long serialVersionUID = 1L;

        LaunchException(String aMessage) {
            super(aMessage);
        }
    }
}
