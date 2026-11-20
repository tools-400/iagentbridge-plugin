# RDi MCP Server – Developer notes

How the plug-in works, how to add a tool and how to run the tests. For its use, see
[USAGE.md](USAGE.md), for its installation [README.md](README.md).

## Setup

* Clone the repository into the RDi workspace and import the projects of the folder `eclipse`.
  The target platform is the running platform (RDi).
* `eclipse/RDi MCP Core Plugin` (`de.tools400.rdimcp.core.plugin`) is the plug-in,
  `eclipse/RDi MCP JUnit` (`de.tools400.rdimcp.junit`) is a fragment with the unit tests.
* Java 8 byte code, so that the plug-in runs in RDi 9.6.
* The plug-in uses internal classes of JDT (`CleanUpRefactoring`, `JavaPlugin`), which are the
  only way to run Clean Up like the menu. RDi shows *Discouraged access* warnings for them.
* The HTTP server is `com.sun.net.httpserver` of the JRE. The execution environment JavaSE-1.8
  does not export it, so the project settings report the access as a warning instead of an error.
  At runtime Equinox loads it from the JRE, because the Eclipse launcher enables
  `osgi.compatibility.bootdelegation` by default.

## Architecture

Packages of `RDi MCP Core Plugin`:

| Package | Content |
|---|---|
| `de.tools400.rdimcp` | `RdiMcpPlugin` (activator, starts and stops the server), `RdiMcpStartup` (`org.eclipse.ui.startup`), `ServerSettings` (port and token) |
| `de.tools400.rdimcp.mcp` | the protocol: `McpHttpServer`, `McpDispatcher`, `IMcpTool`, `McpToolResult`, `ToolArguments` |
| `de.tools400.rdimcp.tools` | the tools: `CleanUpTool`, `WorkspaceInfoTool` and their helpers |

`ServerSettings` and the package `mcp` have no Eclipse dependencies, so that the tests run
outside of RDi. Gson is imported with `Import-Package` from RDi (2.2.4 and 2.8.2 in RDi 9.6), so
the code uses only the API of Gson 2.2.4, e.g. `new JsonParser().parse(...)`.

### Protocol

* Transport *Streamable HTTP*: one endpoint `/mcp`, `POST` only. Every request gets a single
  `application/json` response, notifications get `202 Accepted`. There are no SSE streams and no
  sessions; `GET` and `DELETE` get `405 Method Not Allowed`.
* Checks in this order: path (`404`), `Origin` header (`403`), token (`401`), method (`405`).
* `McpDispatcher` handles `initialize`, `ping`, `tools/list` and `tools/call`. It returns the
  protocol version, that the client asks for, if it is in `SUPPORTED_PROTOCOL_VERSIONS`, otherwise
  `LATEST_PROTOCOL_VERSION`. Claude Code first sends `server/discover` for a newer protocol
  version, gets `-32601 Method not found` and then sends `initialize`.
* An `IllegalArgumentException` of a tool is returned as a tool error with its message, any other
  exception with its class names and messages.
* A single thread handles the requests one after the other, so that two tools never run at the
  same time.

### Clean Up

`CleanUpTool` does what *Source > Clean Up* does with the configured profiles:
`CleanUpRefactoring` with `setUseOptionsFromProfile(true)` and all clean-ups of the registry of
JDT, run by `PerformRefactoringOperation` in `IWorkspace.run()` with the scheduling rule of the
refactoring. Each file is refreshed from disk before, and its content is compared after Clean Up.

JDT takes the options from the project, if its settings have the key `cleanup_profile`, otherwise
from the profile of the workspace (`CleanUpPreferenceUtil.loadOptions`). `CleanUpProfileNames`
describes the profile with the same rule. `CleanUpPreferenceUtil.hasSettingsInScope` is no help
here: it checks the keys of the save actions (`sp_...`).

### Adding a tool

1. Implement `IMcpTool`: name, description for the agent, JSON schema of the arguments and
   `execute()`. Throw an `IllegalArgumentException` for invalid arguments.
2. Add the tool to the list in `RdiMcpPlugin.startServer()`.

## Tests

The tests in `eclipse/RDi MCP JUnit` use no Eclipse types; run them with *Run As > JUnit Test*.
`McpHttpServerTest` starts the server on a free port and sends raw HTTP requests, because
`HttpURLConnection` drops the header `Origin`.

Outside of RDi, compile with the ECJ of RDi 9.6 against all bundles of
`C:\IBM\RDi_096\configuration\org.eclipse.equinox.simpleconfigurator\bundles.info` and run
`org.junit.runner.JUnitCore` with Gson, JUnit and Hamcrest of `C:\IBM\SDPShared\plugins` on the
classpath.

A test with a real client: start an `McpHttpServer` with a test tool and let Claude Code call it
with a configuration file of its own, without changing the configuration of the user:

```powershell
claude -p "Call the tool echo of the MCP server test." --mcp-config mcp-test.json --strict-mcp-config --allowedTools mcp__test__echo
```

`mcp-test.json` contains the server with `"type": "http"`, its `url` and the `Authorization`
header.
