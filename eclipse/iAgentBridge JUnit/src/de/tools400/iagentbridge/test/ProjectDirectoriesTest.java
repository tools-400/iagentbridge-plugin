/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import de.tools400.iagentbridge.tools.ProjectDirectories;

public class ProjectDirectoriesTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void testFind_projectDirectory() throws Exception {

        File project = project("repo/p");

        assertEquals(Collections.singletonList(project), find(project));
    }

    @Test
    public void testFind_projectsBelowSortedByPath() throws Exception {

        File b = project("repo/b");
        File a = project("repo/sub/a");
        File c = project("repo/c");

        assertEquals(Arrays.asList(b, c, a), find(directory("repo")));
    }

    @Test
    public void testFind_nestedProjectsAreNotSearched() throws Exception {

        File outer = project("repo/outer");
        project("repo/outer/inner");

        assertEquals(Collections.singletonList(outer), find(directory("repo")));
    }

    @Test
    public void testFind_metadataAndGitAreSkipped() throws Exception {

        File p = project("repo/p");
        project("repo/.metadata/q");
        project("repo/.git/r");

        assertEquals(Collections.singletonList(p), find(directory("repo")));
    }

    @Test
    public void testFind_directoryNamedProjectIsNoProject() throws Exception {

        directory("repo/p/.project");

        assertTrue(find(directory("repo")).isEmpty());
    }

    @Test
    public void testFind_noProject() throws Exception {

        directory("repo/src");

        assertTrue(find(directory("repo")).isEmpty());
    }

    @Test
    public void testFind_missingDirectory() throws Exception {

        assertInvalidPath(new File(folder.getRoot(), "missing"), "Directory not found.");
    }

    @Test
    public void testFind_relativePath() throws Exception {

        // The current directory of RDi is no directory of the client.
        assertInvalidPath(new File("."), "The path is not absolute.");
    }

    @Test
    public void testFind_fileIsNoDirectory() throws Exception {

        File project = project("repo/p");

        assertInvalidPath(new File(project, ProjectDirectories.PROJECT_FILE), "Not a directory.");
    }

    @Test
    public void testFind_canceled() throws Exception {

        project("repo/p");

        try {
            ProjectDirectories.find(directory("repo"), () -> true);
            fail("InterruptedException expected");
        } catch (InterruptedException e) {
            // expected
        }
    }

    private List<File> find(File aDirectory) throws IOException, InterruptedException {
        return ProjectDirectories.find(aDirectory, () -> false);
    }

    private void assertInvalidPath(File aPath, String aMessage) throws IOException, InterruptedException {

        try {
            find(aPath);
            fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException e) {
            assertEquals(aMessage, e.getMessage());
        }
    }

    private File project(String aPath) throws IOException {

        File directory = directory(aPath);
        assertTrue(new File(directory, ProjectDirectories.PROJECT_FILE).createNewFile());

        return directory;
    }

    private File directory(String aPath) throws IOException {

        File directory = new File(folder.getRoot(), aPath);
        if (!directory.isDirectory()) {
            assertTrue(directory.mkdirs());
        }

        return directory.getCanonicalFile();
    }
}
