/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.launches;

import java.io.File;
import java.io.IOException;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import de.tools400.iagentbridge.ui.forwarding.Forwarding;
import de.tools400.iagentbridge.ui.forwarding.ForwardingClient;
import de.tools400.iagentbridge.ui.forwarding.ForwardingClient.Kind;
import de.tools400.iagentbridge.ui.forwarding.ForwardingClient.Response;
import de.tools400.iagentbridge.ui.forwarding.TestInstance;

/**
 * Finds out, which instance answers at a port: its workspace, that the tool
 * workspace_info of the core returns. Test instances of one RDi version share a
 * port (de and en); the one, that started second, runs without server.
 */
public final class Identity {

    private static final int READ_TIMEOUT_MILLIS = 15000;

    private Identity() {
    }

    /**
     * Returns, whether the instance at the port of a launch has its workspace.
     *
     * @param anInstance - the instance of the launch, with its port
     * @param aWorkspace - the workspace of the launch configuration, resolved,
     *        or <code>null</code>, if it is not known
     * @return the result
     */
    public static Result check(TestInstance anInstance, String aWorkspace) {

        String token;
        try {
            token = Forwarding.token(anInstance, System.getProperties());
        } catch (IOException e) {
            return Result.UNKNOWN;
        }

        Response response = new ForwardingClient(ForwardingClient.CONNECT_TIMEOUT_MILLIS, READ_TIMEOUT_MILLIS).send(anInstance, token,
            "workspace_info", new JsonObject()); //$NON-NLS-1$
        if (response.getKind() == Kind.REJECTED) {
            // Another server: it does not accept the token.
            return Result.OTHER;
        }
        if (response.getKind() != Kind.ANSWERED || response.getResult().isError()) {
            return Result.NO_ANSWER;
        }

        String workspace = workspaceOf(response.getResult().getText());
        if (workspace == null || aWorkspace == null) {
            return Result.UNKNOWN;
        }

        return isSamePath(workspace, aWorkspace) ? Result.SAME : Result.OTHER;
    }

    /**
     * Returns the workspace of the answer of workspace_info, after the line,
     * that names the instance and may contain '{' in its name.
     */
    static String workspaceOf(String aText) {
        int start = aText.indexOf('{', aText.indexOf('\n') + 1);
        if (start < 0) {
            return null;
        }
        try {
            JsonElement answer = new JsonParser().parse(aText.substring(start));
            JsonElement workspace = answer.isJsonObject() ? answer.getAsJsonObject().get("workspace") : null; //$NON-NLS-1$
            return workspace == null || !workspace.isJsonPrimitive() ? null : workspace.getAsString();
        } catch (JsonParseException e) {
            return null;
        }
    }

    /**
     * Compares two paths of this computer, canonical and without case.
     */
    static boolean isSamePath(String aPath, String anOtherPath) {
        try {
            return new File(aPath).getCanonicalPath().equalsIgnoreCase(new File(anOtherPath).getCanonicalPath());
        } catch (IOException e) {
            return new File(aPath).getAbsolutePath().equalsIgnoreCase(new File(anOtherPath).getAbsolutePath());
        }
    }

    /**
     * Who answers at the port of a launch.
     */
    public enum Result {

        /** The instance of the launch: it has the workspace of the launch. */
        SAME,

        /** Another process: another workspace, or it rejects the token. */
        OTHER,

        /** No server answers at the port. */
        NO_ANSWER,

        /**
         * A server answers, but its workspace or the one of the launch is not
         * known.
         */
        UNKNOWN
    }
}
