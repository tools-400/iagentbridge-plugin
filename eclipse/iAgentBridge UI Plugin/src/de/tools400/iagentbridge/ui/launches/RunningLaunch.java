/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.launches;

/**
 * A launch, that runs, as list_launches shows it and start_launch checks it,
 * without Eclipse types.
 */
public final class RunningLaunch {

    private final String name;
    private final String mode;
    private final Integer port;
    private final boolean uiTools;
    private final long startTime;
    private final boolean suspended;
    private final String hotCodeReplace;

    /**
     * Creates the launch.
     *
     * @param aName - name of the launch configuration
     * @param aMode - 'run' or 'debug'
     * @param aPort - port of iAgentBridge or <code>null</code>
     * @param aUiTools - <code>true</code>, if it runs the UI tools
     * @param aStartTime - start in milliseconds since 1970, or 0
     * @param aSuspended - <code>true</code>, if a thread is suspended in the
     *        debugger
     * @param aHotCodeReplace - the last result of hot code replace or
     *        <code>null</code>
     */
    public RunningLaunch(String aName, String aMode, Integer aPort, boolean aUiTools, long aStartTime, boolean aSuspended, String aHotCodeReplace) {
        name = aName;
        mode = aMode;
        port = aPort;
        uiTools = aUiTools;
        startTime = aStartTime;
        suspended = aSuspended;
        hotCodeReplace = aHotCodeReplace;
    }

    public String getName() {
        return name;
    }

    public String getMode() {
        return mode;
    }

    public Integer getPort() {
        return port;
    }

    public boolean hasUiTools() {
        return uiTools;
    }

    public long getStartTime() {
        return startTime;
    }

    public boolean isSuspended() {
        return suspended;
    }

    public String getHotCodeReplace() {
        return hotCodeReplace;
    }
}
