/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.tools;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

/**
 * Saves screenshots as 'ui-&lt;time stamp&gt;.png' in a folder and keeps only
 * the newest ones there.
 */
public final class ScreenshotFiles {

    /** Number of screenshots, that the folder keeps */
    public static final int KEEP = 50;

    private static final String PREFIX = "ui-"; //$NON-NLS-1$
    private static final String SUFFIX = ".png"; //$NON-NLS-1$

    private ScreenshotFiles() {
    }

    /**
     * Returns the folder of the screenshots: 'iagentbridge/screenshots' in the
     * folder for temporary files.
     *
     * @return folder
     */
    public static File getDirectory() {
        return new File(new File(System.getProperty("java.io.tmpdir"), "iagentbridge"), "screenshots"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    /**
     * Saves a screenshot and deletes the oldest ones, so that the folder keeps
     * {@link #KEEP} screenshots.
     *
     * @param aDirectory - the folder
     * @param aPng - the PNG
     * @param aTime - time of the screenshot
     * @return the file
     * @throws IOException if the file cannot be written
     */
    public static File save(File aDirectory, byte[] aPng, Date aTime) throws IOException {

        Files.createDirectories(aDirectory.toPath());

        String name = PREFIX + new SimpleDateFormat("yyyyMMdd-HHmmss-SSS").format(aTime); //$NON-NLS-1$
        File file = new File(aDirectory, name + SUFFIX);
        for (int i = 2; file.exists(); i++) {
            file = new File(aDirectory, name + "-" + i + SUFFIX); //$NON-NLS-1$
        }
        Files.write(file.toPath(), aPng);

        keepNewest(aDirectory, KEEP);

        return file;
    }

    /**
     * Deletes the oldest screenshots, so that the folder keeps the newest ones.
     * The names contain the time stamp, so they sort by time.
     *
     * @param aDirectory - the folder
     * @param aCount - number of screenshots to keep
     */
    static void keepNewest(File aDirectory, int aCount) {

        File[] files = aDirectory.listFiles((aDir, aName) -> aName.startsWith(PREFIX) && aName.endsWith(SUFFIX));
        if (files == null || files.length <= aCount) {
            return;
        }

        Arrays.sort(files);
        for (int i = 0; i < files.length - aCount; i++) {
            files[i].delete();
        }
    }
}
