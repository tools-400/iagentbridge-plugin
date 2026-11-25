# Extending iAgentBridge

How to write a plug-in that adds a tool to the MCP server of iAgentBridge. For the use of the
plug-in, see [USAGE.md](USAGE.md), for its internals [DEVELOPMENT.md](DEVELOPMENT.md).

An extension plug-in is an ordinary Eclipse plug-in, that requires
`de.tools400.iagentbridge.core.plugin` and contributes to its extension point. It is shipped as a
feature of its own, so that only the tools, that are used, are installed. The reference
implementation is the plug-in `iRPGFormatter iAgentBridge Plugin` of the repository
`irpgformatter-plugin`: its tool `format_rpg` formats RPG sources with iRPGFormatter.

## Extension point

| Extension point | Interface | Purpose |
|---|---|---|
| `de.tools400.iagentbridge.core.plugin.tools` | `IMcpTool` | Adds a tool to the MCP server. The client lists it with `tools/list` and calls it with `tools/call`, like the tools of iAgentBridge. |

The API is in the exported package `de.tools400.iagentbridge.mcp`:

| Class | Purpose |
|---|---|
| `IMcpTool` | the tool: name, description, JSON schema of the arguments and `execute()` |
| `McpToolResult` | the result: `success(text)` or `error(text)`, since 1.1.0 with images: `withImage(data, mimeType)`, see *Images* in [Rules](#4-rules) |
| `ToolArguments` | reads the arguments: `getStringList()`, since 1.1.0 also `getString()` (a string, that must not be empty) and the optional arguments `getOptionalStringList()`, `getChoice()` (one of fixed strings) and `getInt()`, and `toLocalPath()`, which turns a path of Git Bash like `/c/dir/file` into `C:/dir/file` |
| `ToolJobs` | since 1.1.0: `runAndWait()` schedules a job, which the view *Progress* shows and in which the user can cancel it, and waits for its end, see *Jobs* in [Rules](#4-rules) |

The other classes of the package, `McpDispatcher`, `McpHttpServer`, `McpResponse` and
`IMcpErrorLog`, are internal; the rest of the plug-in is not exported.

## A tool

### 1. The plug-in project

`META-INF/MANIFEST.MF`:

```
Manifest-Version: 1.0
Bundle-ManifestVersion: 2
Bundle-Name: My Tool
Bundle-SymbolicName: com.example.iagentbridge.mytool;singleton:=true
Bundle-Version: 1.0.0
Bundle-RequiredExecutionEnvironment: JavaSE-1.8
Bundle-ActivationPolicy: lazy
Require-Bundle: org.eclipse.core.runtime,
 de.tools400.iagentbridge.core.plugin;bundle-version="[1.0.0.b002,2.0.0)"
Import-Package: com.google.gson;version="[2.2.0,3.0.0)"
```

Java 8 byte code, because iAgentBridge runs in RDi 9.6. 1.0.0.b002 is the first version with the
extension point.

The arguments and the schema of a tool are objects of Gson. Import Gson with `Import-Package` and
the same version range as iAgentBridge: RDi 9.6 has Gson 2.2.4 and 2.8.2, and the export of
iAgentBridge declares `uses:="com.google.gson"`, so that both plug-ins get the same Gson. Use only
the API of Gson 2.2.4, e.g. `new JsonParser().parse(...)`.

### 2. The extension

`plugin.xml`:

```xml
<extension point="de.tools400.iagentbridge.core.plugin.tools">
   <tool
         id="com.example.iagentbridge.mytool"
         class="com.example.iagentbridge.mytool.MyTool">
   </tool>
</extension>
```

The `id` identifies the contribution. The name, with which the client calls the tool, is returned
by `IMcpTool.getName()`. The class needs a public constructor without parameters.

### 3. The tool

```java
package com.example.iagentbridge.mytool;

import java.io.File;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import de.tools400.iagentbridge.mcp.IMcpTool;
import de.tools400.iagentbridge.mcp.McpToolResult;
import de.tools400.iagentbridge.mcp.ToolArguments;

public class MyTool implements IMcpTool {

    private static final String INPUT_SCHEMA = "{\"type\":\"object\",\"properties\":{\"files\":"
        + "{\"type\":\"array\",\"items\":{\"type\":\"string\"},"
        + "\"description\":\"Absolute paths of the files\"}},\"required\":[\"files\"]}";

    public String getName() {
        return "count_bytes";
    }

    public String getDescription() {
        return "Returns the size of files in bytes.";
    }

    public JsonObject getInputSchema() {
        return new JsonParser().parse(INPUT_SCHEMA).getAsJsonObject();
    }

    public McpToolResult execute(JsonObject anArguments) {

        List<String> paths = ToolArguments.getStringList(anArguments, "files");

        StringBuilder text = new StringBuilder();
        for (String path : paths) {
            File file = new File(ToolArguments.toLocalPath(path));
            if (!file.isFile()) {
                throw new IllegalArgumentException("File not found: " + path);
            }
            text.append(path).append(": ").append(file.length()).append('\n');
        }

        return McpToolResult.success(text.toString());
    }
}
```

The description tells the AI agent what the tool does and when to use it; write it for the agent,
not for a user. The agent sees the text of the result; JSON is easy for it to read.

### 4. Rules

* **The name** consists of 1 to 64 letters, digits, `_` and `-`, e.g. `format_rpg`. It must be
  unique: the tools of iAgentBridge are added first, then the contributed tools, and the first tool
  with a name wins.
* **The description and the input schema** are required. The schema is a JSON schema of the type
  `object`, in the dialect JSON Schema 2020-12, unless it names another one with `$schema`.
* **Invalid arguments**: throw an `IllegalArgumentException`. The client gets its message as a
  tool error. Any other exception is returned as a tool error with the class names and messages of
  its causes and is written to the error log of RDi with its stack trace.
* **A failed tool** returns `McpToolResult.error(text)`, so that the agent notices it.
* **Images**: `McpToolResult.success(text).withImage(png, "image/png")` returns an image, e.g. a
  screenshot, after the text; the client shows it to the agent. The method returns a new result and
  keeps a copy of the bytes. Requires `bundle-version="[1.1.0,2.0.0)"`.
* **Threads**: the server handles one request after the other in a thread of its own, never in the
  UI thread. A tool may block until its work is done, e.g. join a job. Work with the UI, e.g. to
  check the editors, in `Display.syncExec()`.
* **Jobs**: run long work in a job with `ToolJobs.runAndWait(job)`, so that the user sees it in the
  view *Progress* and can cancel it. Set rule and priority of the job, but do not schedule it and
  do not call `setUser(true)`: Eclipse would open a progress dialog. A job, that fails, opens no
  error dialog; the method returns its result, also of a job, that has been cancelled while it
  ran, and `CANCEL_STATUS`, if the job has been cancelled before it ran. When the server stops,
  e.g. after *New Token*, the waiting thread is
  interrupted: the method cancels the job and throws the `InterruptedException` without waiting for
  the end of the job. Requires `bundle-version="[1.1.0,2.0.0)"`.
* **Life cycle**: the server creates the tools, when it starts - when RDi starts, and when the
  preference page restarts it. Changes to the installed tools need a restart of RDi.
* **Errors**: a tool, that cannot be created, that fails to describe itself or whose name is
  invalid or taken, is left out. The *Error Log* view shows the reason. The message
  `iAgentBridge server listening on ...` lists the names of the tools, that the server offers.
* **Files of the workspace**: refresh a file before you read it, e.g. with
  `IFile.refreshLocal()`; the agent changes files outside of RDi.

### 5. Tests

Put the tests into a **fragment of your own plug-in**. The logic of a tool, that needs no Eclipse
types, e.g. the text of its result, can be tested with JUnit alone. The reference implementation
tests the JSON of its result in `FormatRpgReportTest` of the fragment `iRPGFormatter iAgentBridge
JUnit`.

### 6. Shipping it

Put the plug-in into a feature of its own, which requires iAgentBridge:

```xml
<requires>
   <import plugin="de.tools400.iagentbridge.core.plugin" version="1.0.0.b002" match="greaterOrEqual"/>
</requires>
```

The plug-ins of tools-400 import the plug-in, not the feature
`de.tools400.iagentbridge.core.feature`, because their shared `build.xml` sets the version of
every `<import feature` line to the version of the product, that is built.

The feature is installed from the update site of its product; the update site of iAgentBridge must
be available, too, or iAgentBridge must be installed before.
