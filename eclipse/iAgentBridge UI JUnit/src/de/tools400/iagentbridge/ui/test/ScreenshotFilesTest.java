/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Date;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import de.tools400.iagentbridge.ui.tools.ScreenshotFiles;

public class ScreenshotFilesTest {

    private static final byte[] PNG = { 1, 2, 3 };

    private File directory;

    @Before
    public void setUp() throws IOException {
        directory = new File(Files.createTempDirectory("iagentbridge-screenshots").toFile(), "screenshots");
    }

    @After
    public void tearDown() {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                file.delete();
            }
        }
        directory.delete();
        directory.getParentFile().delete();
    }

    @Test
    public void testSave() throws IOException {

        File file = ScreenshotFiles.save(directory, PNG, new Date(0));

        assertTrue(file.getName(), file.getName().matches("ui-\\d{8}-\\d{6}-\\d{3}\\.png"));
        assertArrayEquals(PNG, Files.readAllBytes(file.toPath()));
    }

    @Test
    public void testSave_sameTime() throws IOException {

        File first = ScreenshotFiles.save(directory, PNG, new Date(0));
        File second = ScreenshotFiles.save(directory, PNG, new Date(0));

        assertNotEquals(first, second);
        assertTrue(first.exists());
        assertTrue(second.exists());
    }

    @Test
    public void testSave_keepsNewest() throws IOException {

        File oldest = ScreenshotFiles.save(directory, PNG, new Date(1000));
        File second = ScreenshotFiles.save(directory, PNG, new Date(2000));
        for (int i = 0; i < ScreenshotFiles.KEEP; i++) {
            ScreenshotFiles.save(directory, PNG, new Date(10000 + i * 1000L));
        }
        File other = new File(directory, "notes.txt");
        Files.write(other.toPath(), PNG);

        String[] names = directory.list();
        Arrays.sort(names);
        assertEquals(ScreenshotFiles.KEEP + 1, names.length);
        assertFalse(oldest.exists());
        assertFalse(second.exists());
        assertTrue(other.exists());
    }
}
