# iAgentBridge – Developer notes

How the plug-in works, how to add a tool and how to run the tests. For its use, see
[USAGE.md](USAGE.md), for its installation [README.md](README.md), for plug-ins, that add tools,
[EXTENDING.md](EXTENDING.md).

## Setup

* Clone the repository into the RDi workspace and import the projects of the folders `eclipse`
  and `build`. The target platform is the running platform (RDi).
* Projects:
  * `eclipse/iAgentBridge Core Plugin` (`de.tools400.iagentbridge.core.plugin`) is the plug-in,
    with `eclipse/iAgentBridge Core Feature` the product.
  * `eclipse/iAgentBridge JUnit` (`de.tools400.iagentbridge.junit`) is a fragment with the unit
    tests.
  * `eclipse/iAgentBridge Update Site`: *Build All* in `site.xml`.
  * `build/iAgentBridge Build`: the release script.
* To install the plug-in from the sources into the running RDi, choose *File > Export... >
  Plug-in Development > Deployable plug-ins and fragments*, select
  `de.tools400.iagentbridge.core.plugin` and the destination *Install into host*, then restart RDi.
* Java 8 byte code, so that the plug-in runs in RDi 9.6.
* The plug-in uses internal classes of JDT (`CleanUpRefactoring`, `JavaPlugin`), which are the
  only way to run Clean Up like the menu. RDi shows *Discouraged access* warnings for them.
* The HTTP server is `com.sun.net.httpserver` of the JRE. The execution environment JavaSE-1.8
  does not export it, so the project settings report the access as a warning instead of an error.
  At runtime Equinox loads it from the JRE, because the Eclipse launcher enables
  `osgi.compatibility.bootdelegation` by default.
* Launch configurations: one project per RDi version, `eclipse/iAgentBridge Launches (RDi 9.6)`,
  `(RDi 9.8)` and `(RDi 9.9)`, German and English. Import only the project of the running RDi
  version. They are the launch configurations of IFS Projects plus the bundles of iAgentBridge and
  `-Diagentbridge.port=39196`, `39198` or `39199`, so that a test instance does not take the port
  of the RDi it was started from. They contain the plug-ins of iRPGFormatter and its plug-in
  `iRPGFormatter iAgentBridge Plugin` (repository `irpgformatter-plugin`), so that a test instance
  offers the tool `format_rpg`, too.
* Release: `build/iAgentBridge Build/build/build.xml` (same script as IFS Projects, iRPGFormatter,
  iRPGUnit, iSphere, JCRCMDS and Rapid Fire, settings in `build.properties`: no manual, no
  Eclipse Marketplace). Update site and home page go to `docs/` of the repository
  `iagentbridge` (GitHub Pages). `.github/workflows` tags a release version and mirrors the
  release into that repository.

## Architecture

Packages of `iAgentBridge Core Plugin`:

| Package | Content |
|---|---|
| `de.tools400.iagentbridge` | `IAgentBridgePlugin` (activator, starts, stops and restarts the server), `IAgentBridgeStartup` (`org.eclipse.ui.startup`), `ServerSettings` (port and token), `ClientCommands` (command for Claude Code), `ToolCollection` (the tools of the server), `ContributedTools` (the tools of other plug-ins), `Messages` |
| `de.tools400.iagentbridge.mcp` | the protocol: `McpHttpServer`, `McpDispatcher`, `McpResponse`, `IMcpErrorLog`, `IMcpTool`, `McpToolResult`, `ToolArguments`; exported for the tools of other plug-ins |
| `de.tools400.iagentbridge.tools` | the tools: `CleanUpTool`, `WorkspaceInfoTool` and their helpers |
| `de.tools400.iagentbridge.preferences` | `Preferences` and their defaults |
| `de.tools400.iagentbridge.preferencepages` | `IAgentBridgePreferencePage` |

`ServerSettings`, `ClientCommands`, `ToolCollection` and the package `mcp` have no Eclipse
dependencies, so that the tests run outside of RDi. Gson is imported with `Import-Package` from
RDi (2.2.4 and 2.8.2 in RDi 9.6), so the code uses only the API of Gson 2.2.4, e.g.
`new JsonParser().parse(...)`.

### Protocol

* Transport *Streamable HTTP*: one endpoint `/mcp`, `POST` only. Every request gets a single
  `application/json` response, notifications and responses of the client get `202 Accepted`.
  There are no SSE streams and no sessions; `GET` and `DELETE` get `405 Method Not Allowed`.
* A batch (JSON array, protocol version 2025-03-26) gets the responses to its requests as an array.
* An invalid message, whose `id` is not known (no JSON, an `id`, that is no string or number, or
  no `id` at all, e.g. a notification with a wrong `jsonrpc`), gets `400 Bad Request` and a
  JSON-RPC error without `id`, as the MCP schema demands, not `"id": null`. An invalid message with
  a valid `id` (e.g. a wrong `jsonrpc` or no `method`) gets the error with this `id` and `200`, like
  any request. A batch gets the `400`, when it is empty or none of its responses has an `id`.
  `McpResponse.isRejected()` tells `McpHttpServer` to send the `400`.
* Checks in this order: path (`404`), `Origin` header (`403`), token (`401`), method (`405`),
  size of the request (`413`), header `MCP-Protocol-Version` (`400` with a JSON-RPC error, if it
  names a version, that is not in `SUPPORTED_PROTOCOL_VERSIONS`). The header is not checked for
  `initialize`, which negotiates the version, and a request without it is accepted (the server then
  assumes 2025-03-26).
* The `401` carries `WWW-Authenticate: Bearer realm="iagentbridge"`, as HTTP demands, and
  `error="invalid_token"`, if the client sent a wrong bearer token. It names no `resource_metadata`:
  the token is a scheme of its own, not the OAuth of the MCP authorization.
* `McpDispatcher` handles `initialize`, `ping`, `tools/list` and `tools/call`. It returns the
  protocol version, that the client asks for, if it is in `SUPPORTED_PROTOCOL_VERSIONS`, otherwise
  `LATEST_PROTOCOL_VERSION`. Claude Code first sends `server/discover` with the header
  `MCP-Protocol-Version: 2026-07-28`, gets the `400` of the header check (without the header it
  would get `-32601 Method not found`) and then sends `initialize` without the header.
* An `IllegalArgumentException` of a tool is returned as a tool error with its message, any other
  exception with its class names and messages; such an exception is also written to the error log
  of RDi with its stack trace. `arguments`, that are no object, give `-32602 Invalid params`. An
  exception outside of `execute()`, e.g. of `getDescription()` during `tools/list`, gives
  `-32603 Internal error` and is written to the error log as well. The error log is an
  `IMcpErrorLog`, which `IAgentBridgePlugin` passes to `McpDispatcher`.
* A single thread handles the requests one after the other, so that two tools never run at the
  same time.

### Settings

* The preferences (`serverEnabled`, `port`) are stored in the instance scope, i.e. per workspace.
* The default of `port` is `ServerSettings.defaultPort()` of the version of RDi (the version of the
  bundle, that defines the product): 39000 + 10 × major + minor, e.g. 39096 for 9.6.13, the same
  rule as the server name `rdi096` of `ClientCommands`. Without a known version it is 39096.
* `ServerSettings` takes the port of the system property `iagentbridge.port`, if it is set, else
  the one of the preferences, and the token of `iagentbridge.token` or else of the token file.
* `serverEnabled` is read only by `IAgentBridgeStartup` (`startServer()`). *Apply* on the
  preference page restarts a running server, if its port changed; *Start* saves and calls
  `startServerNow()`, which ignores `serverEnabled`. There is no stop: a server runs until RDi
  ends. *New Token* writes a new token and restarts a running server.

### Clean Up

`CleanUpTool` does what *Source > Clean Up* does with the configured profiles:
`CleanUpRefactoring` with `setUseOptionsFromProfile(true)` and all clean-ups of the registry of
JDT, run by `PerformRefactoringOperation` in `IWorkspace.run()` with the scheduling rule of the
refactoring. Each file is refreshed from disk before, and its content is compared after Clean Up.

JDT takes the options from the project, if its settings have the key `cleanup_profile`, otherwise
from the profile of the workspace (`CleanUpPreferenceUtil.loadOptions`). `CleanUpProfileNames`
describes the profile with the same rule. `CleanUpPreferenceUtil.hasSettingsInScope` is no help
here: it checks the keys of the save actions (`sp_...`).

### Tools

`IAgentBridgePlugin.createTools()` collects the tools, whenever the server starts: first the tools
of iAgentBridge, then the tools of the extension point `de.tools400.iagentbridge.core.plugin.tools`
(`schema/tools.exsd`), which `ContributedTools` creates. `ToolCollection` leaves out a tool, whose
name is invalid or already taken, or that fails to return its name, description or input schema of
type `object`; `ContributedTools` leaves out a tool, that cannot be created or does not implement
`IMcpTool`. Their errors go to the error log, so that one faulty plug-in does not stop the server.
The info message of the start lists the names of the tools.

The package `de.tools400.iagentbridge.mcp` is exported with `uses:="com.google.gson"`, because
`IMcpTool` has Gson types in its methods: a plug-in with a tool then gets the same Gson as
iAgentBridge, although RDi 9.6 has two versions of it. Only `IMcpTool`, `McpToolResult` and
`ToolArguments` are API, see [EXTENDING.md](EXTENDING.md).

### Adding a tool

1. Implement `IMcpTool`: name, description for the agent, JSON schema of the arguments and
   `execute()`. Throw an `IllegalArgumentException` for invalid arguments.
2. Add the tool to `IAgentBridgePlugin.createTools()`.

A tool, that belongs to another plug-in, e.g. to iRPGFormatter, lives in that plug-in or in a
plug-in of its own and uses the extension point, see [EXTENDING.md](EXTENDING.md).

## Tests

The tests in `eclipse/iAgentBridge JUnit` use no Eclipse types; run them with *Run As > JUnit
Test*. `McpHttpServerTest` starts the server on a free port and sends raw HTTP requests, because
`HttpURLConnection` drops the header `Origin`. `CheckNLSMessages` (launch in
`eclipse/iAgentBridge JUnit/launches`) checks, that `messages.properties` and
`messages_de.properties` have exactly the keys of `Messages`. `ToolCollectionTest` checks, which
tools the server accepts: names, descriptions, input schemas, duplicate names and tools, that fail
to describe themselves.

Outside of RDi, compile with the ECJ of RDi 9.6 against all bundles of
`C:\IBM\RDi_096\configuration\org.eclipse.equinox.simpleconfigurator\bundles.info`, copy the
`messages*.properties` into the output and run `org.junit.runner.JUnitCore` with Gson, JUnit,
Hamcrest and `org.eclipse.osgi` (for `NLS`) of `C:\IBM\SDPShared\plugins` on the classpath.

A test with a real client: start an `McpHttpServer` with a test tool and let Claude Code call it
with a configuration file of its own, without changing the configuration of the user:

```powershell
claude -p "Call the tool echo of the MCP server test." --mcp-config mcp-test.json --strict-mcp-config --allowedTools mcp__test__echo
```

`mcp-test.json` contains the server with `"type": "http"`, its `url` and the `Authorization`
header.
