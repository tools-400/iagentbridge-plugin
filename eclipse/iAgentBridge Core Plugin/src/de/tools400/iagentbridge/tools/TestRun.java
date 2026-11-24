/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.tools;

import java.util.LinkedHashSet;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.ILaunchesListener2;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.jdt.junit.JUnitCore;
import org.eclipse.jdt.junit.TestRunListener;
import org.eclipse.jdt.junit.model.ITestCaseElement;
import org.eclipse.jdt.junit.model.ITestElement;
import org.eclipse.jdt.junit.model.ITestElement.FailureTrace;
import org.eclipse.jdt.junit.model.ITestElement.ProgressState;
import org.eclipse.jdt.junit.model.ITestElementContainer;
import org.eclipse.jdt.junit.model.ITestRunSession;
import org.eclipse.jdt.junit.model.ITestSuiteElement;

import de.tools400.iagentbridge.tools.TestRunReport.End;
import de.tools400.iagentbridge.tools.TestRunReport.Outcome;
import de.tools400.iagentbridge.tools.TestRunReport.Result;

/**
 * A run of the tests of a JUnit launch configuration: launches it, collects the
 * results of the tests, which JDT reports to the view JUnit, and ends the
 * launch, when the timeout is reached or the user cancels the job.
 * <p>
 * JDT reports the end of a test run only, when the run completes or the user
 * stops it in the view JUnit. When the launch ends otherwise, e.g. because it
 * is terminated or the test VM exits, JDT reports nothing. Therefore the
 * results of the tests are collected, while they run, and the end of the launch
 * ends the waiting, too.
 */
final class TestRun {

    /** Interval, in which the waiting thread checks for the cancellation */
    private static final long POLL_MILLIS = 200;

    /**
     * Time, that JDT gets to report the end of the test run after the end of
     * the launch, because it reads the messages of the test VM in a thread of
     * its own
     */
    private static final long GRACE_MILLIS = 5000;

    /**
     * Time, that the launch gets to end after the test run or after it has been
     * terminated, e.g. the shutdown of the workbench of a JUnit Plug-in Test
     */
    private static final long END_OF_LAUNCH_MILLIS = 10000;

    private final ILaunchConfiguration configuration;
    private final int timeoutSeconds;
    private final TestRunReport report;

    private final Object lock;
    private final Set<ITestCaseElement> running;
    private ITestRunSession session;
    private ProgressState finalState;

    /**
     * Creates a test run.
     *
     * @param aConfiguration - JUnit launch configuration
     * @param aTimeoutSeconds - maximum duration of the test run in seconds
     * @param aMaxFailures - maximum number of failed tests in the report
     */
    TestRun(ILaunchConfiguration aConfiguration, int aTimeoutSeconds, int aMaxFailures) {
        configuration = aConfiguration;
        timeoutSeconds = aTimeoutSeconds;
        report = new TestRunReport(aConfiguration.getName(), aMaxFailures);
        lock = new Object();
        running = new LinkedHashSet<ITestCaseElement>();
    }

    /**
     * Returns the report of the test run.
     *
     * @return report
     */
    TestRunReport getReport() {
        return report;
    }

    /**
     * Launches the configuration and waits for the end of the test run.
     *
     * @param aMonitor - progress monitor of the job
     * @throws CoreException if the launch fails, e.g. because the test class
     *         contains no tests
     * @throws OperationCanceledException if the user cancels the job; the
     *         launch is terminated
     */
    void run(IProgressMonitor aMonitor) throws CoreException {

        // JDT does not tell the number of tests in advance. The view Progress
        // shows the running test instead.
        aMonitor.beginTask("", IProgressMonitor.UNKNOWN); //$NON-NLS-1$
        ILaunchManager launchManager = DebugPlugin.getDefault().getLaunchManager();
        Listener listener = new Listener();
        JUnitCore.addTestRunListener(listener);
        launchManager.addLaunchListener(listener);
        try {

            long start = System.currentTimeMillis();
            // Without a build: get_problems builds. The launch with a build
            // would ask in a dialog, if the build has errors.
            ILaunch launch = configuration.launch(ILaunchManager.RUN_MODE, new LaunchMonitor(aMonitor));
            if (aMonitor.isCanceled()) {
                terminate(launch);
                throw new OperationCanceledException();
            }
            // The timeout starts after the launch, which may have waited for
            // the answer to a dialog of RDi.
            End end = waitForEnd(launch, System.currentTimeMillis() + timeoutSeconds * 1000L, aMonitor);
            long millis = System.currentTimeMillis() - start;
            waitForEndOfLaunch(launch);

            synchronized (lock) {
                for (ITestCaseElement test : running) {
                    report.addRunning(test.getTestClassName(), test.getTestMethodName());
                }
                addWarning(launch, end);
                report.setEnd(end, millis);
            }

        } finally {
            JUnitCore.removeTestRunListener(listener);
            launchManager.removeLaunchListener(listener);
            aMonitor.done();
        }
    }

    /**
     * Waits, until the test run has ended, the launch has ended, the timeout
     * has been reached or the user has cancelled the job. Meanwhile the view
     * Progress shows the running test.
     *
     * @return end of the test run
     */
    private End waitForEnd(ILaunch aLaunch, long aDeadline, IProgressMonitor aMonitor) throws DebugException {

        if (aLaunch.getProcesses().length == 0) {
            // RDi asked before the launch, e.g. about compile errors in a
            // plug-in, and the user cancelled the launch.
            return End.ABORTED;
        }

        long endOfLaunch = 0;
        String shownTest = ""; //$NON-NLS-1$
        while (true) {

            ProgressState state = getFinalState();
            if (state != null) {
                return state == ProgressState.STOPPED ? End.STOPPED : End.COMPLETED;
            }

            String test = getRunningTest();
            if (!test.equals(shownTest)) {
                aMonitor.subTask(test);
                shownTest = test;
            }

            long now = System.currentTimeMillis();
            if (aLaunch.isTerminated()) {
                if (endOfLaunch == 0) {
                    endOfLaunch = now;
                } else if (now - endOfLaunch >= GRACE_MILLIS) {
                    return End.ABORTED;
                }
            }

            if (aMonitor.isCanceled() || !pause()) {
                terminate(aLaunch);
                throw new OperationCanceledException();
            }

            if (now >= aDeadline) {
                terminate(aLaunch);
                return End.TIMEOUT;
            }
        }
    }

    private ProgressState getFinalState() {
        synchronized (lock) {
            return finalState;
        }
    }

    /**
     * Returns the test, that is running now.
     *
     * @return class and method of the test or an empty string
     */
    private String getRunningTest() {

        synchronized (lock) {
            if (running.isEmpty()) {
                return ""; //$NON-NLS-1$
            }
            ITestCaseElement test = running.iterator().next();
            return test.getTestClassName() + "." + test.getTestMethodName(); //$NON-NLS-1$
        }
    }

    /**
     * Waits until a listener reports an event or the poll interval has passed.
     *
     * @return <code>false</code>, if the thread has been interrupted
     */
    private boolean pause() {

        synchronized (lock) {
            try {
                lock.wait(POLL_MILLIS);
                return true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

    private static void terminate(ILaunch aLaunch) throws DebugException {

        if (aLaunch.canTerminate()) {
            aLaunch.terminate();
        }
    }

    /**
     * Waits a while for the end of the launch, so that the next launch of the
     * same configuration does not find the workspace of a JUnit Plug-in Test
     * still in use.
     */
    private void waitForEndOfLaunch(ILaunch aLaunch) {

        if (aLaunch.getProcesses().length == 0) {
            // RDi did not start the launch. Such a launch is never terminated.
            return;
        }

        long deadline = System.currentTimeMillis() + END_OF_LAUNCH_MILLIS;
        while (!aLaunch.isTerminated() && System.currentTimeMillis() < deadline) {
            if (!pause()) {
                return;
            }
        }
    }

    private void addWarning(ILaunch aLaunch, End anEnd) {

        switch (anEnd) {
        case STOPPED:
            report.addWarning("The test run was stopped in the view JUnit of RDi."); //$NON-NLS-1$
            break;
        case TIMEOUT:
            report.addWarning("The test run was ended after the timeout of " + timeoutSeconds + " seconds."); //$NON-NLS-1$ //$NON-NLS-2$
            break;
        case ABORTED:
            if (aLaunch.getProcesses().length == 0) {
                report.addWarning("RDi did not start the launch, e.g. because the launch was cancelled in a dialog of RDi."); //$NON-NLS-1$
            } else {
                report.addWarning("The launch ended, before all tests ran" + getExitValues(aLaunch) //$NON-NLS-1$
                    + ". The view Console of RDi shows the output of the test VM, e.g. why it could not start."); //$NON-NLS-1$
            }
            break;
        default:
            break;
        }
    }

    private static String getExitValues(ILaunch aLaunch) {

        StringBuilder text = new StringBuilder();
        for (IProcess process : aLaunch.getProcesses()) {
            try {
                text.append(", exit value ").append(process.getExitValue()); //$NON-NLS-1$
            } catch (DebugException e) {
                // The process has not ended yet.
            }
        }

        return text.toString();
    }

    private static Result toResult(ITestElement aTest, String aClassName, String aMethodName) {

        FailureTrace failure = aTest.getFailureTrace();
        if (failure == null) {
            return new Result(toOutcome(aTest), aClassName, aMethodName, null, null, null);
        }

        return new Result(toOutcome(aTest), aClassName, aMethodName, failure.getTrace(), failure.getExpected(), failure.getActual());
    }

    private static Outcome toOutcome(ITestElement aTest) {

        ITestElement.Result result = aTest.getTestResult(false);
        if (result == ITestElement.Result.FAILURE) {
            return Outcome.FAILURE;
        }
        if (result == ITestElement.Result.ERROR) {
            return Outcome.ERROR;
        }
        if (result == ITestElement.Result.IGNORED) {
            return Outcome.IGNORED;
        }

        return Outcome.OK;
    }

    /**
     * Progress monitor of the launch: passes the cancellation of the job on,
     * but not the progress of the launch, because the view Progress shows the
     * running test instead. A cancellation by the launch stays in the launch:
     * JDT cancels the monitor, when the user cancels a dialog of PDE before the
     * launch, which is no cancellation of the job.
     */
    private static final class LaunchMonitor extends NullProgressMonitor {

        private final IProgressMonitor jobMonitor;

        /**
         * Creates a progress monitor for the launch.
         *
         * @param aJobMonitor - progress monitor of the job
         */
        LaunchMonitor(IProgressMonitor aJobMonitor) {
            jobMonitor = aJobMonitor;
        }

        @Override
        public boolean isCanceled() {
            return super.isCanceled() || jobMonitor.isCanceled();
        }
    }

    /**
     * Receives the events of the test runs of JDT and the ends of the launches.
     * JDT calls it in a thread, that reads the messages of the test VM.
     */
    private final class Listener extends TestRunListener implements ILaunchesListener2 {

        @Override
        public void sessionLaunched(ITestRunSession aSession) {

            // The public API of JDT does not tell the launch of a test run.
            // The name of a test run is the name of its launch configuration.
            synchronized (lock) {
                if (session == null && configuration.getName().equals(aSession.getTestRunName())) {
                    session = aSession;
                }
            }
        }

        @Override
        public void testCaseStarted(ITestCaseElement aTestCase) {

            synchronized (lock) {
                if (isOfThisRun(aTestCase)) {
                    running.add(aTestCase);
                }
            }
        }

        @Override
        public void testCaseFinished(ITestCaseElement aTestCase) {

            synchronized (lock) {
                if (isOfThisRun(aTestCase)) {
                    running.remove(aTestCase);
                    report.add(toResult(aTestCase, aTestCase.getTestClassName(), aTestCase.getTestMethodName()));
                }
            }
        }

        @Override
        public void sessionFinished(ITestRunSession aSession) {

            synchronized (lock) {
                if (aSession == session) {
                    addFailuresOfTestClasses(aSession);
                    finalState = aSession.getProgressState();
                    lock.notifyAll();
                }
            }
        }

        @Override
        public void launchesTerminated(ILaunch[] aLaunches) {
            synchronized (lock) {
                lock.notifyAll();
            }
        }

        @Override
        public void launchesAdded(ILaunch[] aLaunches) {
        }

        @Override
        public void launchesChanged(ILaunch[] aLaunches) {
        }

        @Override
        public void launchesRemoved(ILaunch[] aLaunches) {
        }

        private boolean isOfThisRun(ITestElement aTest) {
            return session != null && aTest.getTestRunSession() == session;
        }

        /**
         * Adds the failures of test classes, e.g. of a method annotated with
         * BeforeClass. JDT reports them with the test class, not with a test.
         */
        private void addFailuresOfTestClasses(ITestElementContainer aContainer) {

            for (ITestElement child : aContainer.getChildren()) {
                if (child instanceof ITestSuiteElement) {
                    ITestSuiteElement suite = (ITestSuiteElement)child;
                    if (suite.getFailureTrace() != null) {
                        report.add(toResult(suite, suite.getSuiteTypeName(), null));
                    }
                    addFailuresOfTestClasses(suite);
                }
            }
        }
    }
}
