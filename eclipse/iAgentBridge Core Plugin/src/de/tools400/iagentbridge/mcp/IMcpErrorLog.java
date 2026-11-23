/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.mcp;

/**
 * Log of the unexpected errors of the server, e.g. the error log of Eclipse.
 */
public interface IMcpErrorLog {

    /**
     * Writes an unexpected error to the log.
     *
     * @param aMessage - message of the error
     * @param aThrowable - the cause, never <code>null</code>
     */
    void logError(String aMessage, Throwable aThrowable);
}
