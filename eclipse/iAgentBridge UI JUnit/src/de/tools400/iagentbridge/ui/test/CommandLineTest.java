/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.Map;

import org.junit.Test;

import de.tools400.iagentbridge.ui.forwarding.CommandLine;
import de.tools400.iagentbridge.ui.forwarding.TestInstance;

public class CommandLineTest {

    private static final String COMMAND_LINE = "\"C:\\IBM\\RDi 096\\jdk\\bin\\javaw.exe\" -Dorg.eclipse.swt.graphics.Resource.reportNonDisposed=true "
        + "-Diagentbridge.port=39196 -Diagentbridge.uiTools=true \"-Dcom.ibm.cic.licensing.p2.home.dir=C:\\Program Data\" "
        + "-Dempty= -Dflag -classpath C:\\IBM\\SDPShared\\plugins\\org.eclipse.equinox.launcher.jar org.eclipse.equinox.launcher.Main -nl de";

    @Test
    public void testSplit() {
        assertEquals(Arrays.asList("C:\\IBM\\RDi 096\\jdk\\bin\\javaw.exe", "-a", "b c", "-Dx=\"q\" r", "d"),
            CommandLine.split("\"C:\\IBM\\RDi 096\\jdk\\bin\\javaw.exe\"  -a \"b c\" \"-Dx=\\\"q\\\" r\"\td"));
        assertEquals(Arrays.asList("a", ""), CommandLine.split("a \"\""));
        assertEquals(Arrays.asList(), CommandLine.split("   "));
    }

    @Test
    public void testSystemProperties() {

        Map<String, String> properties = CommandLine.systemProperties(COMMAND_LINE);

        assertEquals("39196", properties.get("iagentbridge.port"));
        assertEquals("true", properties.get("iagentbridge.uiTools"));
        assertEquals("C:\\Program Data", properties.get("com.ibm.cic.licensing.p2.home.dir"));
        assertEquals("", properties.get("empty"));
        assertEquals("", properties.get("flag"));
        assertNull(properties.get("nl"));
    }

    @Test
    public void testTestInstance() {

        TestInstance instance = TestInstance.fromCommandLine("iAgentBridge (de)", COMMAND_LINE);

        assertEquals("iAgentBridge (de)", instance.getName());
        assertEquals(Integer.valueOf(39196), instance.getPort());
        assertNull(instance.getToken());
        assertEquals("\"iAgentBridge (de)\" (port 39196)", instance.describe());
    }

    @Test
    public void testTestInstance_token() {

        TestInstance instance = TestInstance.fromCommandLine("t", "java -Diagentbridge.uiTools=TRUE -Diagentbridge.port=1 -Diagentbridge.token=abc");

        assertEquals(Integer.valueOf(1), instance.getPort());
        assertEquals("abc", instance.getToken());
    }

    @Test
    public void testTestInstance_withoutUiTools() {
        assertNull(TestInstance.fromCommandLine("t", "java -Diagentbridge.port=39196"));
        assertNull(TestInstance.fromCommandLine("t", "java -Diagentbridge.uiTools=false -Diagentbridge.port=39196"));
    }

    @Test
    public void testTestInstance_invalidPort() {
        assertNull(TestInstance.fromCommandLine("t", "java -Diagentbridge.uiTools=true").getPort());
        assertNull(TestInstance.fromCommandLine("t", "java -Diagentbridge.uiTools=true -Diagentbridge.port=x").getPort());
        assertNull(TestInstance.fromCommandLine("t", "java -Diagentbridge.uiTools=true -Diagentbridge.port=70000").getPort());
        assertEquals("\"t\"", TestInstance.fromCommandLine("t", "java -Diagentbridge.uiTools=true").describe());
    }
}
