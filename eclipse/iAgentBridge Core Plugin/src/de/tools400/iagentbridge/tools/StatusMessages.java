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
import org.eclipse.core.runtime.IStatus;

/**
 * Turns the result of a job into a text for the client.
 */
final class StatusMessages {

    private StatusMessages() {
    }

    /**
     * Returns the messages of a status and of its children, each message once.
     * The message of a status often names no reason, e.g. 'Errors occurred
     * during the build.'; the messages of its children do.
     *
     * @param aStatus - status, e.g. the result of a job
     * @return messages, separated by blanks
     */
    static String getMessages(IStatus aStatus) {

        Set<String> messages = new LinkedHashSet<String>();
        addMessages(messages, aStatus);

        StringBuilder text = new StringBuilder();
        for (String message : messages) {
            if (text.length() > 0) {
                text.append(' ');
            }
            text.append(message);
        }

        return text.toString();
    }

    private static void addMessages(Set<String> aMessages, IStatus aStatus) {

        if (aStatus.getMessage() != null && !aStatus.getMessage().isEmpty()) {
            aMessages.add(aStatus.getMessage());
        }
        // The result of a job of Job.create() has no children: it carries the
        // CoreException of the operation, whose status has them.
        if (aStatus.getException() instanceof CoreException) {
            addMessages(aMessages, ((CoreException)aStatus.getException()).getStatus());
        }
        for (IStatus child : aStatus.getChildren()) {
            addMessages(aMessages, child);
        }
    }
}
