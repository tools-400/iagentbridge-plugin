/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.launches;

import de.tools400.iagentbridge.mcp.McpToolResult;
import de.tools400.iagentbridge.ui.forwarding.ForwardingClient.Kind;
import de.tools400.iagentbridge.ui.forwarding.ForwardingClient.Response;
import de.tools400.iagentbridge.ui.model.Hints;

/**
 * The decisions of stop_launch, without Eclipse types: whether the test
 * instance closes its workbench itself or ends with terminate(), and what the
 * outcome of ui_close means.
 */
public final class StopDecision {

    private final Action action;
    private final String reason;

    private StopDecision(Action anAction, String aReason) {
        action = anAction;
        reason = aReason;
    }

    /**
     * Returns, whether stop_launch must ask, who answers at the port of a
     * launch: only for a launch, that may close its workbench.
     *
     * @param aLaunch - the launch
     * @param aForce - argument 'force'
     * @return <code>true</code>, if the decision depends on the identity
     */
    public static boolean needsIdentity(RunningLaunch aLaunch, boolean aForce) {
        return aLaunch.getPort() != null && aLaunch.hasUiTools() && !aForce && !aLaunch.isSuspended();
    }

    /**
     * Decides before ui_close.
     *
     * @param aLaunch - the launch
     * @param aForce - argument 'force'
     * @param anIdentity - who answers at its port; only used, if
     *        {@link #needsIdentity(RunningLaunch, boolean)} is true
     * @return CLOSE or TERMINATE with the reason
     */
    public static StopDecision before(RunningLaunch aLaunch, boolean aForce, Identity.Result anIdentity) {

        if (aLaunch.getPort() == null || !aLaunch.hasUiTools()) {
            return terminate("it has no server with the UI tools, that could close its workbench"); //$NON-NLS-1$
        }
        if (aForce) {
            return terminate("'force' is true"); //$NON-NLS-1$
        }
        if (aLaunch.isSuspended()) {
            return terminate("it is stopped in the debugger"); //$NON-NLS-1$
        }

        switch (anIdentity) {
        case SAME:
            return new StopDecision(Action.CLOSE, null);
        case OTHER:
            return terminate("its port belongs to another process, which keeps running. start_launch rejects a new start on this port, " //$NON-NLS-1$
                + "until that process ends; " + Hints.FOREIGN_PROCESS); //$NON-NLS-1$
        case NO_ANSWER:
            return terminate("its server does not answer"); //$NON-NLS-1$
        default:
            // Never close the workbench of another instance.
            return terminate("the workspace of the instance at its port could not be compared with the one of the launch"); //$NON-NLS-1$
        }
    }

    /**
     * Decides after the call of ui_close.
     *
     * @param aResponse - how the call ended, with its result
     * @return WAIT for the end, REJECT because of unsaved changes, or TERMINATE
     *         with the reason
     */
    public static StopDecision afterClose(Response aResponse) {

        Kind kind = aResponse.getKind();
        McpToolResult result = aResponse.getResult();

        if (kind == Kind.BROKEN || kind == Kind.ANSWERED && !result.isError()) {
            // The server may stop, before it answers, when the workbench
            // closes.
            return new StopDecision(Action.WAIT, null);
        }
        if (kind == Kind.ANSWERED && CloseResult.isUnsaved(result)) {
            return new StopDecision(Action.REJECT, result.getText());
        }

        // In own words: the texts and hints of the forwarding, e.g. 'still
        // starting?', do not fit, after its server answered workspace_info.
        switch (kind) {
        case REFUSED:
            return terminate("its server stopped accepting connections"); //$NON-NLS-1$
        case TIMEOUT:
            return terminate("ui_close did not answer in time"); //$NON-NLS-1$
        case REJECTED:
            return terminate("its server rejected ui_close: " + aResponse.getReason()); //$NON-NLS-1$
        default:
            return terminate("it could not close its workbench: " + withoutHeader(result.getText())); //$NON-NLS-1$
        }
    }

    /**
     * Returns a forwarded text without its first line, that names the test
     * instance.
     */
    private static String withoutHeader(String aText) {
        int end = aText.indexOf('\n');
        return aText.startsWith("Test instance ") && end >= 0 ? aText.substring(end + 1) : aText; //$NON-NLS-1$
    }

    private static StopDecision terminate(String aReason) {
        return new StopDecision(Action.TERMINATE, aReason);
    }

    public Action getAction() {
        return action;
    }

    /**
     * Returns the reason of TERMINATE or the message of REJECT.
     *
     * @return reason or <code>null</code>
     */
    public String getReason() {
        return reason;
    }

    /**
     * What stop_launch does.
     */
    public enum Action {

        /** Calls ui_close in the test instance. */
        CLOSE,

        /** Waits for the end of the launch. */
        WAIT,

        /** Rejects, because of unsaved changes. */
        REJECT,

        /** Ends the launch with terminate(). */
        TERMINATE
    }
}
