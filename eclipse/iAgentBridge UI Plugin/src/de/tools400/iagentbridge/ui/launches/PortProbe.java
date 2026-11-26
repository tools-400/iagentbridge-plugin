/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.launches;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;

/**
 * Asks, whether a server answers at a port of this computer: any HTTP answer
 * counts, also 401 for a request without token.
 */
public final class PortProbe {

    private static final int CONNECT_TIMEOUT_MILLIS = 1000;
    private static final int READ_TIMEOUT_MILLIS = 3000;

    private PortProbe() {
    }

    /**
     * Returns, whether a server answers at a port.
     *
     * @param aPort - the port
     * @return <code>true</code>, if a server answered with HTTP
     */
    public static boolean answers(int aPort) {

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection)new URL("http://127.0.0.1:" + aPort + "/mcp").openConnection(Proxy.NO_PROXY); //$NON-NLS-1$ //$NON-NLS-2$
            connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
            connection.setReadTimeout(READ_TIMEOUT_MILLIS);
            connection.getResponseCode();
            return true;
        } catch (IOException e) {
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
