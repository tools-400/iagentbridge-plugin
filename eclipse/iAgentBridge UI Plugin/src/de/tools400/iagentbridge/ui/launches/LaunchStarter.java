/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.launches;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
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
import de.tools400.iagentbridge.ui.model.Hints;

/**
 * Launches a configuration in a job without build, like run_tests. A
 * configuration of PDE starts as a copy of a working copy without the check of
 * the plug-ins of PDE: no dialog, and what PDE saves, stays in memory; the
 * .launch file is not written. A launch, that still waits after the deadline,
 * e.g. for an answer in a dialog, is remembered, until its job ends.
 */
public final class LaunchStarter {

    private static final String ATTR_AUTOMATIC_VALIDATE = "automaticValidate"; //$NON-NLS-1$

    private static final String NO_SPLASH = "-nosplash"; //$NON-NLS-1$

    private static volatile String waiting;

    private LaunchStarter() {
    }

    /**
     * Returns the boolean attributes, that the working copy of a configuration
     * gets before its start: PDE does not check the plug-ins, which shows a
     * dialog in this RDi, if it finds a problem. The program arguments come
     * from {@link #programArguments(String)}.
     *
     * @return names and values of the attributes
     */
    static Map<String, Boolean> workingCopyAttributes() {

        Map<String, Boolean> attributes = new LinkedHashMap<String, Boolean>();
        attributes.put(ATTR_AUTOMATIC_VALIDATE, Boolean.FALSE);

        return attributes;
    }

    /**
     * Returns the program arguments of the working copy: -nosplash and the
     * arguments of the configuration, so that the instance shows no splash
     * screen, which takes the focus of the operating system. PDE shows the
     * splash screen, if the generated config.ini names one, unless the
     * arguments contain -nosplash; the attribute showSplash of the
     * configuration does not turn it off. -nosplash comes first, so that an
     * open quote or -vmargs in the arguments does not take it in.
     *
     * @param anArguments - the program arguments of the configuration, or
     *        <code>null</code>
     * @return program arguments with -nosplash
     */
    static String programArguments(String anArguments) {

        String arguments = anArguments == null ? "" : anArguments.trim(); //$NON-NLS-1$
        if (Arrays.asList(arguments.split("\\s+")).contains(NO_SPLASH)) { //$NON-NLS-1$
            return arguments;
        }

        return arguments.isEmpty() ? NO_SPLASH : NO_SPLASH + " " + arguments; //$NON-NLS-1$
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
        for (Map.Entry<String, Boolean> attribute : workingCopyAttributes().entrySet()) {
            copy.setAttribute(attribute.getKey(), attribute.getValue().booleanValue());
        }
        copy.setAttribute(Launches.ATTR_PROGRAM_ARGUMENTS, programArguments(copy.getAttribute(Launches.ATTR_PROGRAM_ARGUMENTS, (String)null)));
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
            throw new LaunchException(noReturn(name));
        }
        if (result instanceof Throwable) {
            throw new LaunchException(failed(name, (Throwable)result));
        }

        ILaunch launch = (ILaunch)result;
        if (launch.getProcesses().length == 0) {
            throw new LaunchException(notStarted(name));
        }

        return launch;
    }

    /**
     * Returns the text for a start, that did not return within the time.
     *
     * @param aName - the name of the configuration
     * @return text
     */
    static String noReturn(String aName) {
        return "RDi is probably waiting for an answer in a dialog: the start of \"" + aName //$NON-NLS-1$
            + "\" did not return within the time. Until it is answered, start_launch starts nothing. " + Hints.askDeveloper("answer the dialog"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /**
     * Returns the text for a start, that failed.
     *
     * @param aName - the name of the configuration
     * @param aFailure - the failure
     * @return text
     */
    static String failed(String aName, Throwable aFailure) {
        return Hints.after("The start of \"" + aName + "\" failed: " + aFailure, //$NON-NLS-1$ //$NON-NLS-2$
            "Check the launch configuration, or ask the developer to start it by hand."); //$NON-NLS-1$
    }

    /**
     * Returns the text for a start without a process.
     *
     * @param aName - the name of the configuration
     * @return text
     */
    static String notStarted(String aName) {
        return "RDi did not start \"" + aName + "\" (a dialog cancelled?). Ask the developer, which dialog RDi showed, then repeat start_launch."; //$NON-NLS-1$ //$NON-NLS-2$
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
