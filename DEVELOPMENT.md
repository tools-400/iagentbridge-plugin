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
| `de.tools400.iagentbridge.mcp` | the protocol: `McpHttpServer`, `McpDispatcher`, `McpResponse`, `IMcpErrorLog`, `IMcpTool`, `McpToolResult`, `ToolArguments`, and `ToolJobs` (runs a job and waits for it); exported for the tools of other plug-ins |
| `de.tools400.iagentbridge.tools` | the tools: `CleanUpTool`, `GetProblemsTool`, `ImportProjectsTool`, `RunTestsTool`, `WorkspaceInfoTool` and their helpers, e.g. `WorkspaceFiles` (file of the workspace for a path of the client, unsaved changes), `WorkspaceProjects` (projects and the projects, that they reference), `WorkspaceJobs` (runs an operation on the resources in a job and waits for it), `StatusMessages`, `ProblemReport` (result of `get_problems`), `ProjectDirectories` (search of `import_projects`), `ImportReport` (result of `import_projects`), `TestRun` (launch and listeners of `run_tests`) and `TestRunReport` (result of `run_tests`) |
| `de.tools400.iagentbridge.preferences` | `Preferences` and their defaults |
| `de.tools400.iagentbridge.preferencepages` | `IAgentBridgePreferencePage` |

`ServerSettings`, `ClientCommands`, `ToolCollection`, `ProblemReport`, `ProjectDirectories`,
`ImportReport`, `TestRunReport` and the package `mcp` except `ToolJobs` have no Eclipse
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
  same time. While `run_tests` waits for the tests, the server answers no other request.

### Settings

* The preferences (`serverEnabled`, `port`) are stored in the instance scope, i.e. per workspace.
* The default of `port` is `ServerSettings.defaultPort()` of the version of RDi (the version of the
  bundle, that defines the product): 39000 + 10 × major + minor, e.g. 39096 for 9.6.13, the same
  rule as the server name `rdi096` of `ClientCommands`. Without a known version it is 39096.
* `ServerSettings` takes the port of the system property `iagentbridge.port`, if it is set, else
  the one of the preferences, and the token of `iagentbridge.token` or else of the token file.
* `serverEnabled` is read only by `IAgentBridgeStartup` (`startServer()`), through
  `ServerSettings.isServerEnabled()`: the system property `iagentbridge.serverEnabled` (`true` or
  `false`) overrides it, any other value is an error of the start. *Apply* on the
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

### Problems

`GetProblemsTool` runs two jobs with the rule of the workspace root, one after the other; the
thread of the server waits for each with `join()` (`WorkspaceJobs`, `ToolJobs`). The view *Progress*
shows them, and the user can cancel them; then the tool fails. A job, that is cancelled before it
runs, e.g. while it waits for its rule, has no result (`JobManager.cancel()` sets none);
`ToolJobs` returns `CANCEL_STATUS` instead. When the thread of the server is interrupted, because
the server stops, e.g. after *New Token*, `ToolJobs` cancels the job. A job, that ends with an
error, e.g. because a builder throws an exception, opens no dialog in RDi
(`IProgressConstants.NO_IMMEDIATE_ERROR_PROMPT_PROPERTY`). The
tool names the error in `warnings` and returns the problems all the same, because the build goes
on with the other builders and projects. The warning contains the messages of all children of the
status, because the message of the status itself (*Errors occurred during the build.*) names no
reason. `Job.create()` returns a plain status, whose exception is the `CoreException` of the
operation with these children.

1. The first job refreshes the projects and all open projects, that they reference directly or
   indirectly (`IProject.getReferencedProjects()`, which contains the dynamic references, that JDT
   sets for the projects on the build path), with `DEPTH_INFINITE`.
2. The second job builds the active build configurations of the projects with
   `IWorkspace.build(..., buildReferences = true, ...)`.

The build must not run in the operation of the refresh: PDE changes the class path container of
a project, whose `MANIFEST.MF` changed, only at the resource change event at the end of that
operation, often in a job of its own (`PluginModelManager$UpdateClasspathsJob`, rule of the
workspace root, no family). A build in the same operation used the old class path, and nothing built
the project again. The build job, scheduled after the end of the refresh, runs after that job.
Afterwards the tool joins `ResourcesPlugin.FAMILY_AUTO_BUILD`, also with `build: none`, because an
automatic build, that is still running, adds markers later.

The problems are the markers of type `IMarker.PROBLEM` and its subtypes, e.g. of JDT and PDE;
`ProblemReport` filters, sorts and cuts them. With `files` the tool also reads the markers of
their projects themselves: when the build path is broken, the Java builder removes the problems of
all files and adds one to the project. The files with unsaved changes come from the file buffers
(`WorkspaceFiles`, like the check of `CleanUpTool`), so an editor, that does not use file buffers,
is not seen.

### Import of projects

`ImportProjectsTool` does what the wizard *Existing Projects into Workspace* does without copying
(`WizardProjectsImportPage` of `org.eclipse.ui.ide`), in a job without a scheduling rule: it
searches the projects first, so that RDi can save and build meanwhile, and imports them in
`IWorkspace.run()` with the rule of the workspace root.

* `ProjectDirectories` searches like `collectProjectFilesFromDirectory()` without nested projects: a
  directory with a file `.project` is a project and is not searched further, `.metadata` is
  skipped, and the canonical paths stop links, that lead back. Unlike the wizard it also skips
  `.git`, which contains no projects.
* A directory, that is already the location of a project of the workspace
  (`findContainersForLocationURI`), is reported as `existing`.
* The project gets the name, the description and the location of `loadProjectDescription()`, like
  in `createExistingProject()`: the location is the directory, unless it is the default location,
  the directory of the workspace and the name of the project. `validateProjectLocationURI` checks
  it. RDi 9.6 accepts a project in the directory of another project.
* `open()` without `BACKGROUND_REFRESH`, unlike the wizard, so that the next tool finds all files
  of the project.

`ImportReport` builds the result; an error of one project does not stop the others.

### Test runs

`RunTestsTool` finds the launch configuration by its name. A type of launch configuration runs
JUnit tests, if its delegate for `run` is a `JUnitLaunchConfigurationDelegate` of JDT or a subclass,
e.g. the one of a *JUnit Plug-in Test* of PDE: the IDs of the types and the attributes of JDT JUnit
(`JUnitLaunchConfigurationConstants`) are internal in RDi 9.6.

`ILaunchConfiguration.launch()` always runs the checks of the delegate, also without a build: JDT
asks in a dialog, if the project of the launch configuration or a project, that it references, has
compile errors, and RDi asks to save editors with unsaved changes. The tool checks the same before
the launch: the markers of the Java compiler (`IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER`, without
subtypes) with severity error and the file buffers with unsaved changes in these projects. PDE
checks all plug-ins of the workspace, that a *JUnit Plug-in Test* launches; the tool does not.

`TestRun` runs in a job without a scheduling rule, which the thread of the server joins. It
registers a `TestRunListener` of `JUnitCore` and an `ILaunchesListener2` and launches without a
build. The launch gets a monitor, that passes only the cancellation of the job on; the view
*Progress* shows the running test instead of the progress of the launch, because JDT does not tell
the number of tests in advance. The timeout starts, when `launch()` returns, i.e. after a dialog of
RDi. A launch, that RDi did not start, e.g. because a dialog was cancelled, has no process; it ends
the test run at once (`Launch.isTerminated()` is `false` for such a launch, so the tool must not wait
for its end). It recognizes its test run by `ITestRunSession.getTestRunName()`, the name of the launch
configuration, because the API of JDT has no link from a test run to its launch. JDT calls
`sessionFinished()` only, when the test run completes or is stopped in the view *JUnit*, not when
the launch is terminated or the test VM exits (`sessionTerminated` of the internal model). So the
results are collected test by test in `testCaseFinished()`, the failures of test classes (e.g. of a
`@BeforeClass` method) in `sessionFinished()`, and the waiting ends with `sessionFinished()`, 5
seconds after the end of the launch (`aborted`), at the timeout or when the job is cancelled; in
the last two cases the tool terminates the launch. Then it waits up to 10 seconds for the end of
the launch, e.g. of the workbench of a *JUnit Plug-in Test*. `TestRunReport` counts the results,
filters and cuts the stack traces (the default filters of the view *JUnit*) and builds the JSON.

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
iAgentBridge, although RDi 9.6 has two versions of it. Only `IMcpTool`, `McpToolResult`,
`ToolArguments` and `ToolJobs` are API, see [EXTENDING.md](EXTENDING.md). `ToolJobs` is API, because
every tool, that runs a job, needs to wait for it like the tools of iAgentBridge; the other helpers
of the package `tools`, e.g. `WorkspaceFiles`, stay internal.

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
to describe themselves. `ProblemReportTest` checks the result of `get_problems`: order, severity
filter, counts, cut and JSON; the build and the markers themselves need RDi and are tested by hand
with a test instance. `TestRunReportTest` checks the result of `run_tests`: counts, ends, cut,
filter of the stack traces and JSON; the launch and the events of JDT are tested by hand with a test
instance and test projects with green, red and hanging tests. `ProjectDirectoriesTest` checks the
search of `import_projects` in temporary directories and `ImportReportTest` its result; the import
itself is tested by hand with a test instance.

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
