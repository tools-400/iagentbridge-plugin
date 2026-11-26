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
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;

/**
 * Whether a workspace is in use: a running Eclipse holds a lock on the file
 * .metadata/.lock of its workspace.
 */
public final class WorkspaceLock {

    private static final String LOCK_FILE = ".metadata/.lock"; //$NON-NLS-1$

    private WorkspaceLock() {
    }

    /**
     * Returns whether another process or this one has locked a workspace. The
     * lock, that the check takes, is released at once.
     *
     * @param aWorkspace - path of the workspace
     * @return <code>true</code>, if it is locked
     */
    public static boolean isLocked(String aWorkspace) {

        File lockFile = new File(aWorkspace, LOCK_FILE);
        if (!lockFile.isFile()) {
            return false;
        }

        try (RandomAccessFile file = new RandomAccessFile(lockFile, "rw")) { //$NON-NLS-1$
            FileLock lock = file.getChannel().tryLock();
            if (lock == null) {
                return true;
            }
            lock.release();
            return false;
        } catch (OverlappingFileLockException e) {
            // This JVM holds the lock.
            return true;
        } catch (IOException e) {
            // The lock cannot be checked: the launch tries it.
            return false;
        }
    }
}
