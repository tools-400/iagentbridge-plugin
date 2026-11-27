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
  * `eclipse/iAgentBridge UI Plugin` (`de.tools400.iagentbridge.ui.plugin`) is the optional plug-in
    with the UI tools, with `eclipse/iAgentBridge UI Feature`, which requires the core feature of
    the same or a newer version, and the fragment `eclipse/iAgentBridge UI JUnit`
    (`de.tools400.iagentbridge.ui.junit`).
  * `eclipse/iAgentBridge Update Site`: *Build All* in `site.xml`.
  * `build/iAgentBridge Build`: the release script.
* To install the plug-in from the sources into the running RDi, choose *File > Export... >
  Plug-in Development > Deployable plug-ins and fragments*, select
  `de.tools400.iagentbridge.core.plugin`, for the UI tools also
  `de.tools400.iagentbridge.ui.plugin`, and the destination *Install into host*, then restart RDi.
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
  offers the tool `format_rpg`, too, and the plug-in `IFS Projects iAgentBridge Plugin` (repository
  `ifs-projects-plugin`) with the tools `ifs_projects_compile` and `ifs_projects_check_syntax`.
  They set `-Diagentbridge.uiTools=true` and `-Diagentbridge.serverName=rdi096test` (`rdi098test`,
  `rdi099test`), see [UI tools](#ui-tools). *Use @argfile* must stay off in them: with it, JDT
  writes the arguments into a file, and the command line, from which the forwarding reads port and
  token, has no `-Diagentbridge...` any more.
* Release: `build/iAgentBridge Build/build/build.xml` (same script as IFS Projects, iRPGFormatter,
  iRPGUnit, iSphere, JCRCMDS and Rapid Fire, settings in `build.properties`: no manual, no
  Eclipse Marketplace). Update site and home page go to `docs/` of the repository
  `iagentbridge` (GitHub Pages).

## Architecture

Packages of `iAgentBridge Core Plugin`:

| Package | Content |
|---|---|
| `de.tools400.iagentbridge` | `IAgentBridgePlugin` (activator, starts, stops and restarts the server), `IAgentBridgeStartup` (`org.eclipse.ui.startup`), `ServerSettings` (port and token), `ClientCommands` (command for Claude Code), `ToolCollection` (the tools and the instructions of the server), `ContributedTools` (the tools and the instructions of other plug-ins), `Messages` |
| `de.tools400.iagentbridge.mcp` | the protocol: `McpHttpServer`, `McpDispatcher`, `McpResponse`, `IMcpErrorLog`, `IMcpTool`, `McpToolResult`, `ToolArguments`, and `ToolJobs` (runs a job and waits for it); exported for the tools of other plug-ins |
| `de.tools400.iagentbridge.tools` | the tools: `CleanUpTool`, `GetProblemsTool`, `ImportProjectsTool`, `RunTestsTool`, `WorkspaceInfoTool` and their helpers, e.g. `WorkspaceFiles` (file of the workspace for a path of the client, unsaved changes), `WorkspaceProjects` (projects and the projects, that they reference), `WorkspaceJobs` (runs an operation on the resources in a job and waits for it), `StatusMessages`, `ProblemReport` (result of `get_problems`), `ProjectDirectories` (search of `import_projects`), `ImportReport` (result of `import_projects`), `TestRun` (launch and listeners of `run_tests`) and `TestRunReport` (result of `run_tests`) |
| `de.tools400.iagentbridge.preferences` | `Preferences` and their defaults |
| `de.tools400.iagentbridge.preferencepages` | `IAgentBridgePreferencePage` |

`ServerSettings`, `ClientCommands`, `ToolCollection`, `ProblemReport`, `ProjectDirectories`,
`ImportReport`, `TestRunReport` and the package `mcp` except `ToolJobs` have no Eclipse
dependencies, so that the tests run outside of RDi. Gson is imported with `Import-Package` from
RDi (2.2.4 and 2.8.2 in RDi 9.6), so the code uses only the API of Gson 2.2.4, e.g.
`new JsonParser().parse(...)`.

Packages of `iAgentBridge UI Plugin`, see [UI tools](#ui-tools) and [Launch tools](#launch-tools):

| Package | Content |
|---|---|
| `de.tools400.iagentbridge.ui.tools` | `AbstractUiTool` (executes in a test instance or forwards), `UiSnapshotTool`, `UiScreenshotTool`, `Snapshots`, `AbstractActionTool` (the flow of an action), `UiClickTool`, `UiTypeTool`, `UiSelectTool`, `UiCheckTool`, `UiMenuTool`, `UiKeyTool`, `UiCommandTool`, `UiWaitTool`, `UiLpexTool`, `UiErrorLogTool`, `UiCloseTool`, `UiArguments`, `ScreenshotFiles`, `ImageDataScaler` and `ImageScaler` (scale a screenshot down); the tools of the RDi, in which the developer works: `StartLaunchTool`, `StopLaunchTool`, `ListLaunchesTool` and `LaunchToolSupport` |
| `de.tools400.iagentbridge.ui.launches` | for these tools: `Launches` (launch configurations and launches), `LaunchInfo` and `RunningLaunch` (without Eclipse types), `StartChecks` and `LaunchProblems` (checks before the start), `LaunchStarter` (the start in a job), `PortProbe`, `Identity` (who answers at a port), `WorkspaceLock`, `StopDecision` (how to end a launch), `CloseResult` (the result of `ui_close`), `HotCodeReplace` |
| `de.tools400.iagentbridge.ui.preferences` | `UiPreferences`, `UiToolsPreferencePage` (page *iAgentBridge > UI tools*); its texts in `de.tools400.iagentbridge.ui.Messages` |
| `de.tools400.iagentbridge.ui.forwarding` | `TestInstanceFinder` (the test instance among the launches), `Forwarding` (which test instance, which token), `ForwardingClient` (HTTP), `ForwardedResult` (JSON-RPC response to `McpToolResult`), `TestInstance`, `CommandLine`, `TokenFile` |
| `de.tools400.iagentbridge.ui.swt` | `UiThread` (code in the UI thread with a timeout), `WidgetRefs`, `ShellPicker`, `NativeDialogs`, `WidgetModelBuilder` (widgets to the model), `WorkbenchStateBuilder`, `ShellPainter` (image of a shell); for the actions `ActionTarget` (the widget and its checks), `ShellLock`, `WidgetEvents`, `KeyEvents`, `MenuWalker`, `ItemFinder`, `ForegroundGuard`, `ListenerFailures`, `LpexEdits`; for `ui_close` `OpenDialogs` |
| `de.tools400.iagentbridge.ui.model` | without SWT types: the snapshot (`WidgetNode`, `WorkbenchState`, `SnapshotWriter`, `LocatorBuilder`, `Mnemonics`) and the actions (`Deadline`, `Journal`, `JournalEntry`, `PendingFailures`, `KeyParser`, `KeyStroke`, `ItemPath`, `WaitCondition`, `LpexLines`) and the error log (`ErrorLogEntry`, `ErrorLogParser`, `ErrorLogReport`), and `Hints`, that several error texts share |

The packages `model` and `forwarding` except `TestInstanceFinder`, the classes `LaunchInfo`,
`RunningLaunch`, `StartChecks`, `StopDecision`, `CloseResult`, `PortProbe`, `Identity` and
`WorkspaceLock` of `launches`, and `UiArguments`, `ScreenshotFiles` and `ImageScaler` have no
Eclipse dependencies, so that the tests run outside of RDi; so has `UiThread.act()`, which takes the
UI thread as an
`Executor`. The package `tools` uses `swt` and `launches`, not the other way round.

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

The build must not run in the operation of the refresh: PDE changes the class path container of a
project, whose `MANIFEST.MF` changed, only at the resource change event at the end of that
operation, often in a job of its own (`PluginModelManager$UpdateClasspathsJob`, rule of the
workspace root, no family). A build in the same operation would use the old class path, and nothing
would build the project again. The build job, scheduled after the end of the refresh, runs after
that job. Afterwards the tool joins `ResourcesPlugin.FAMILY_AUTO_BUILD`, also with `build: none`,
because an automatic build, that is still running, adds markers later.

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
the test run at once (`Launch.isTerminated()` is `false` for such a launch, so the tool must not
wait for its end). It recognizes its test run by `ITestRunSession.getTestRunName()`, the name of the
launch configuration, because the API of JDT has no link from a test run to its launch. JDT calls
`sessionFinished()` only, when the test run completes or is stopped in the view *JUnit*, not when
the launch is terminated or the test VM exits (`sessionTerminated` of the internal model). So the
results are collected test by test in `testCaseFinished()`, the failures of test classes (e.g. of a
`@BeforeClass` method) in `sessionFinished()`, and the waiting ends with `sessionFinished()`, 5
seconds after the end of the launch (`aborted`), at the timeout or when the job is cancelled; in
the last two cases the tool terminates the launch. Then it waits up to 10 seconds for the end of
the launch, e.g. of the workbench of a *JUnit Plug-in Test*. `TestRunReport` counts the results,
filters and cuts the stack traces (the default filters of the view *JUnit*) and builds the JSON.

### UI tools

The UI tools of `iAgentBridge UI Plugin` come into the server by the extension point.
`AbstractUiTool.execute()` executes a tool, if the system property `iagentbridge.uiTools` is `true`
(a test instance); otherwise it forwards the call and never reads the user interface of its own RDi.

**Forwarding.** `TestInstanceFinder` looks for running launches of the type *Eclipse Application*
(`org.eclipse.pde.ui.RuntimeWorkbench`), whose process has `-Diagentbridge.uiTools=true` in its
command line (`IProcess.ATTR_CMDLINE`, set by JDT, with all variables resolved). The command line
gives the port (`-Diagentbridge.port`) and, if set, the token (`-Diagentbridge.token`); else
`TokenFile` reads the token file. The names of the system properties and the path of the token file
are those of `ServerSettings` of the core plug-in, whose package is not exported; `TestInstance` and
`TokenFile` repeat them. `Forwarding` decides: no test instance, several, no port, no token, or the
call. `ForwardingClient` sends `tools/call` with the same name and arguments plus `forwarded: true`
over `Proxy.NO_PROXY`, without `initialize`, because the server keeps no session; 2 s to connect, 30
s for the answer (`ui_wait` its `timeoutSeconds` plus 20 s; the RDi, that forwards, checks this
argument itself). The body goes with a fixed length (`setFixedLengthStreamingMode`): otherwise
`HttpURLConnection` sends a POST again, when the server closes the connection without an answer, and
an action would run twice. `ForwardedResult` returns texts and images of the test instance
unchanged, after a line, that names it. `ForwardingClient.send()` also returns the kind of the
outcome - answered, connection refused, connection broken after sending, no answer in time, or
rejected (token, HTTP status, no JSON) -, which `stop_launch` decides on; only unsaved changes it
finds by the start of the text of `ui_close` (`CloseResult`). `call()` returns the result only. A
server without `uiTools`, that gets a call with `forwarded`, rejects it: if a launch with the UI
tools had the port of the RDi, in which the developer works, that RDi would forward to itself; the
server handles one request after the other, so the marked call runs after the first one has timed
out, and the chain ends there.

**Threads.** `UiThread.read()` passes the code to the UI thread with `asyncExec` and waits at most
10 s; code, that gets its turn later, is skipped. `syncExec` would block the server, as long as the
UI thread is busy, e.g. in a long operation. After the end of the display, e.g. when an action
closed the last dialog of a closing workbench, the executor of `UiThread.executor()` throws a
`RejectedExecutionException`; an action then reports, that the workbench has ended.

**Snapshot.** In the UI thread `WorkbenchStateBuilder` and `WidgetModelBuilder` build a model
without SWT types; `SnapshotWriter` writes the text outside of it. Plain composites are left out,
their children appear one level up. A password field is a `Text` with `SWT.PASSWORD` or an echo
character: the sign-on dialog of RSE sets only the echo character. A virtual table shows only its
visible rows, because other rows would make its content provider load data. When RDi is not in the
foreground, `Display.getActiveShell()` is `null`; `ShellPicker` then takes the deepest visible
shell, modal ones first. `WidgetRefs` keeps the references in a `WeakHashMap` and forgets disposed
widgets at each snapshot. `Mnemonics` removes the mnemonic from texts as SWT shows them: `&&` is
one `&`. `LocatorBuilder` writes locators in the style of SWTBot for the SWTBot tests, that are
built from snapshots.

**Native dialogs.** SWT has no API for an open native dialog. `FileDialog`, `MessageBox` and the
other native dialogs of SWT register themselves in `open()`; `NativeDialogs` reads them with the
internal method `Display.getModalDialog()` by reflection, so that it needs no access to internal
packages at compile time and reports no dialog, if the method is missing. `Shell.isEnabled()` does
not show, that Windows disabled the owner of a native dialog. While a native dialog is open, the UI
thread still runs the code of `asyncExec`, so a snapshot answers at once.

**Screenshots.** `ShellPainter` lets the controls draw themselves with `Control.print`, so the
image is right, also when another window covers the test instance, and the test instance never gets
the focus. `Control.print` on a shell itself returns a white image, so the image of a shell is
composed of the images of its children, from the bottom to the top (`getChildren()` returns the
topmost child first); title bar and menu bar are missing. Only the `ImageData` is taken in the UI
thread; `ImageDataScaler` and `ImageLoader` scale and encode it outside. `ImageScaler` averages the
area, that each new pixel covers, because picking the nearest pixel (`ImageData.scaledTo`) drops
lines of characters and turns text unreadable.

**Actions.** `AbstractActionTool` checks the arguments outside of the UI thread, then
`UiThread.act()` passes one runnable to the UI thread: it checks the target, records the action in
the journal and sends the events. An action may open a modal dialog, whose event loop runs, until
the dialog is closed, so the server thread waits 1 s and then passes a probe: if the probe runs
while the action has not returned, a dialog is open, and the tool returns the snapshot of it. A
state, that changes with `compareAndSet` only (waiting, started, finished, expired, abandoned),
decides between the start of the action and the end of the deadline: an action, that has not
started, when the deadline ends, never runs, so that a repeated call does not click twice. An
action, that ends after its tool returned, sets its entry in the journal and reports a failure
through `PendingFailures` to the next UI tool. All work of a call in the UI thread shares one
`Deadline` of 20 s, below the 30 s of the forwarding. The snapshot after an action runs after all
code, that the action passed with `asyncExec`. A path in a tree (`ui_select`) is a prelude of steps,
one runnable for each level, because a viewer like the one of *Remote Systems* creates the children
only, when the UI thread is free; between the steps the server thread waits for the next item. The
journal keeps the last 1000 actions with the locator of their widget for SWTBot tests; no tool
returns it yet.

**Events.** `WidgetEvents` sends the events of a user with `notifyListeners`, in the order of SWTBot
3.1.0 (its sources are in the jar `org.eclipse.swtbot.swt.finder`), but all events of an action in
one runnable, and never `Display.post`, `setFocus()` or `forceActive()` - SWTBot's `setFocus()`
calls `forceActive()`, which brings the test instance to the front. An event to a widget, that is
disposed meanwhile, e.g. a button, whose click closed its dialog, is skipped. ENTER clicks the
default button with these events, not with `Shell.traverse(SWT.TRAVERSE_RETURN)`, which sends
`BM_CLICK` to Windows; ESC is `Shell.traverse(SWT.TRAVERSE_ESCAPE)`, which closes the shell without
input of the operating system. `MenuWalker` lets a menu fill itself with `Show` before it reads the
items; it selects an item like `SWTBotMenu.click()`: `Hide` to the menus above it, then `Selection`.
A radio item of a menu or a tool bar is selected like SWTBot does it: the radio item of its group,
that was selected before, gets `Deactivate`, is deselected and gets `Selection`; a menu with
`SWT.NO_RADIO_GROUP` has no group. A context menu starts with `MenuDetect`. `KeyEvents` sends
KeyDown and KeyUp; an `LpexWindow` has no key listener of its own, so its keys go to `textWindow()`.
Shortcuts of the workbench do not react to these events, and DELETE is no key of LPEX in RDi, so
`ui_command` does these. `LpexEdits` inserts the text of `ui_lpex` with `insertText` and closes it
with `undo check`, like the change of a key, so that LPEX counts it and an undo takes it back on its
own, and then with `parse`, so that the RPG parser of RDi colors it at once; a read-only document is
rejected. After `setText` a field gets KeyUp and FocusOut: JFace field editors check their value on
KeyUp (`VALIDATE_ON_KEY_STROKE`) or on FocusOut (`VALIDATE_ON_FOCUS_LOST`), not on Modify.

**Checks.** `ActionTarget` checks, that the control of the widget is visible and enabled. Before the
events it activates the view or the editor of the widget like a click into the part, because the
events alone do not activate it, and the items of a context menu would use the selection of another
part: the `CTabFolder` of its part stack gets `Selection` with the tab of the part - the tab, that
E4 marks with the data `modelElement`, not the tab of a page of an editor with pages like the Screen
Designer. For an item of the tool bar of a view, which lies in the top right of the folder, it is
the tab, that the folder shows. A viewer reuses its tree items and table rows, when it is refreshed,
so a snapshot remembers the text of each item, that it shows: `WidgetModelBuilder` puts the text
into the node, `SnapshotWriter` collects the nodes, that it writes - not those after the cut -, and
`WidgetRefs.remember()` keeps them. An action on an item with another text is rejected. After an
action, that closed its dialog or dropped its item, `ActionTarget.describe()` returns the
description from before. `ShellLock` decides, whether a dialog blocks the shell: first
`NativeDialogs`, because the internal method `Control.isActive()` of SWT does not see, that a native
dialog blocks its parent; then `Control.isActive()` by reflection, which covers the modal styles of
SWT; `Shell.isEnabled()` does not show the block. If the method is missing, the deepest visible
application or system modal shell and its descendants are free.

**Foreground.** A dialog, that an action opens, may lie on top of the other windows, although the
test instance does not get the foreground: the keyboard stays with the window of the developer. It
does so, when the test instance runs in the debugger; when it runs without, Windows puts the dialog
behind the window in the foreground. `ForegroundGuard` remembers at the start of an action, whether
a window of another process is in the foreground; if so, it puts the active shell of the test
instance behind all windows (`SetWindowPos` with `HWND_BOTTOM` and `SWP_NOACTIVATE`) after each
activation of a shell during the action and at its end, with `asyncExec`, after `Shell.open()`. It
calls the internal class `org.eclipse.swt.internal.win32.OS` by reflection. The preference *Dialogs,
that an action opens in the test instance* (`UiPreferences`, page `UiToolsPreferencePage`) turns the
guard off. The RDi, that forwards a call, adds its preference as the argument `keepDialogsBehind`,
which no schema names; the test instance uses its own preference only, when the argument is missing.

**Failures of listeners.** Since the SWT of RDi 9.8, `notifyListeners` passes an exception of a
listener to the runtime exception handler of the display, an error to its error handler, and the
workbench sets handlers, that do not throw them again. `ListenerFailures` puts handlers in front for
the time of an action; they pass every failure on and hold those of the own calls of the action. A
failure, above whose frame of the action a frame of `Display.readAndDispatch`,
`Synchronizer.runAsyncMessages` or `Display.runTimer` lies, comes from a nested event loop, also the
one of a native dialog, and is not held. While a native dialog is open, nothing is held: the loop of
the dialog also runs listeners without such a frame, e.g. the `Settings` listeners, when Windows
reports a changed setting. The frame of the action is found by the depth of the stack at its start,
not by the name of a method, because all actions run the same code. The SWT of RDi 9.6 has no such
handlers: there the exception reaches the action.

**Commands.** `ui_command` executes a command with the handler service of the workbench window.
Handlers, that depend on the active part, e.g. *Save*, may be disabled, while the test instance is
in the background.

**Error texts.** Every error text of the UI tools and the launch tools, that reports a state, tells
the agent, what to do next: repeat, take a snapshot, operate a dialog, restart the test instance or
ask the developer. Hints, that several texts share, are constants of `Hints`; `Hints.after()`
appends one to a text, whose end comes from outside, e.g. an answer of the test instance, as a
sentence of its own, and `Hints.askDeveloper()` names, what the developer shall do in the RDi, that
the UI tools cannot reach. A launch, that the agent did not start, belongs to the developer: the
hints never tell the agent to stop it. A text, that is built in code with widgets, a display or
launches, lies in a package visible static method next to it, so that a test checks it without them.

**Instructions.** The workflow for the agent - when to test, the question to the developer, the
steps from `get_problems` to `stop_launch` - is the file `instructions/ui-tools.txt` of the UI
plug-in, contributed with the element `instructions` of the extension, see [Tools](#tools). It names
no product and no port, because it serves the development of any plug-in, and it says nothing, that
the descriptions of the tools or the hints of the error texts say already. Whoever changes a UI tool
or a launch tool checks the instructions in the same commit.

**Error log.** `UiErrorLogTool` reads `Platform.getLogFileLocation()` in the server thread, so it
answers also, while the UI thread is busy. The `.log` of Equinox is UTF-8: `!SESSION` before the
first entry of a start and at the start of every file after a rotation, followed by a header up to
the first `!ENTRY`; `!ENTRY <plug-in> <severity> <code> <date>`, `!MESSAGE` with continuation lines,
`!STACK <code>`, `!SUBENTRY <depth> ...` for child entries and an empty line after each entry;
severities 0, 1, 2, 4 and 8. Messages and stack traces may contain empty lines too, so only
`!ENTRY`, `!SUBENTRY` and `!SESSION` end an entry, and `ErrorLogEntry` drops the empty lines at its
end; it collects the lines in a `StringBuilder`, because a stack trace may have thousands of lines.
A start without an entry writes no `!SESSION`, so `since: session` takes the start time of the
system property `eclipse.startTime` (else `RuntimeMXBean.getStartTime()`); a time must be the whole
argument, so that a zone like `Z` is rejected and not ignored. The numbers of the backups
`.bak_<n>.log` run in a circle, so the tool reads the current file only; if a backup contains a
`!SESSION` with this start time, the first line says, that earlier entries of the session are in
the backups. `ErrorLogReport` shows at most 20 child entries of an entry and their number.
`Files.readAllLines` reads the log, so that Equinox can still rename it at a rotation; an open
`FileInputStream` lets `File.renameTo()` fail.

**Working on the UI plug-in.** The RDi, in which you work, offers the tools and the instructions of
its installed UI plug-in. New tools or arguments of the workspace reach an agent only directly on
the port of the test instance, e.g. with `claude -p --mcp-config`, until they are installed in that
RDi and a new session of Claude Code starts; a changed text of the instructions reaches an agent
only in a new session, too.

### Launch tools

`start_launch`, `stop_launch` and `list_launches` implement `IMcpTool` directly, not
`AbstractUiTool`: they run in the RDi, in which the developer works, and reject in a test instance.

**Start without a dialog.** `ILaunchConfiguration.launch()` runs the checks of the delegates also
without a build, and each of them asks in a dialog: errors in the projects of the launch (markers
of `org.eclipse.jdt.core.problem` and, for an *Eclipse Application*, of
`org.eclipse.pde.core.problem`), editors with unsaved changes, whether to clear the workspace
(`clearws` with `askclear`), variables like `${string_prompt}` in the arguments and in the
workspace (`location`), and the validation of the plug-ins of PDE. `StartChecks` and
`LaunchProblems` check the first four before the start; `Launches.workspace()` never resolves a
`location` with such a variable. Unsaved changes are those of the file buffers and of the editors
of `IWorkbenchPage.getDirtyEditors()`, which `start_launch` reads in the UI thread: a change on a
form page of the editor of a `MANIFEST.MF` leaves its file buffer unchanged, and RDi asks in the
dialog *Save and Launch*. The projects are those of the internal
`LaunchPluginValidator.getAffectedProjects(ILaunchConfiguration)` of `org.eclipse.pde.launching`
(same signature in RDi 9.6 and 9.9; called by reflection through the bundle, so that the code needs
no access to the internal package), else all plug-in and feature projects; a *JUnit Plug-in Test*
checks all plug-ins of its configuration, too. A workspace, that another process has locked,
cannot be used; `WorkspaceLock` tries the file lock on `.metadata/.lock`, that a running Eclipse
holds, and releases it at once. `LaunchStarter` sets `automaticValidate=false` in a working copy, so
that the validation of PDE opens no dialog, and appends `-nosplash` to its program arguments, unless
they contain it: the splash screen takes the focus from the window of the developer. The attribute
`showSplash` of the configuration does not help, because PDE of RDi 9.6 and 9.9 takes the splash
screen from `config.ini` and adds `-showsplash`, unless the program arguments contain `-nosplash`.
It launches a working copy of that copy: PDE saves the
configuration, that it launches, e.g. when it sets a missing `SOURCE_PATH_PROVIDER`, and a copy of
a copy saves into the outer copy in memory, never into the `.launch` file. A plug-in, that does not
resolve, is missing in the test instance then.

The launch runs in a job without a scheduling rule (`Job.create`), named after
`Messages.Task_Starting_A`, which the server thread joins until the deadline of the call. RDi
registers the launch before its checks, without a process. If `launch()` does not return in time, a
dialog is open: the tool answers, and `LaunchStarter` keeps the name of the waiting start, until its
job ends, so that `start_launch` rejects a new start, `list_launches` shows it and `stop_launch`
names it. The job catches every `Throwable` of `launch()`, so that it never ends with an error. A
launch without a process after `launch()` means a cancelled dialog. The port comes from the command
line of the process (`TestInstance.ofCommandLine()`, also without `uiTools`), where JDT has resolved
the variables of the VM arguments; before the start it is known only without variables. Without a
port, `start_launch` checks, that the process has not ended. With a port, `PortProbe` sends a GET to
`/mcp` every 0.5 s; any HTTP answer counts (`405`, `401`). The server starts in
`IAgentBridgeStartup.earlyStartup()`, so the workbench is up then, but a dialog may be open, e.g.
the sign-on of a restored editor: `start_launch` therefore asks a test instance with the UI tools
with `ui_snapshot` (`maxNodes: 1`) for its modal shells and names them in its answer.

`Identity` asks the instance at the port for `workspace_info`, reads the JSON after the line, that
names the instance, and compares its `workspace` with the resolved attribute `location` of the
configuration, canonically and without case. Its result is `SAME`, `OTHER` (another workspace, or
the token is rejected), `NO_ANSWER` or `UNKNOWN` (a workspace is not known). A port, that answers
before the start, belongs to another process; so does one, that answers with `OTHER` after the
start - then the new launch has no server (`IAgentBridgePlugin` catches the `BindException`) and is
terminated.

**Clean end.** `StopDecision` decides, how `stop_launch` ends a launch. A launch with port and
`uiTools`, without `force`, that is not suspended in the debugger and whose identity is `SAME`, gets
`ui_close` directly at its instance (`ForwardingClient.send()`, not `Forwarding`, which rejects,
while several instances run); with `UNKNOWN` it is terminated, so that the workbench of another
instance is never closed. `UiCloseTool` reads the dirty editors and the dirty views, that are an
`ISaveablePart` (the *Project Explorer* is one), in the UI thread; without any, it closes the open
dialogs with `OpenDialogs`, then passes `IWorkbench.close()` with `asyncExec` and answers at once.
`close()` asks nothing, also not with *Confirm exit when closing last window*, which a new workspace
has on (tested in RDi 9.6 and 9.9), but it waits in an event loop for running operations: with the
connect of RSE, whose progress dialog *Progress Information* waits for the sign-on *Enter Password*
(both modal children of the workbench window), it never returns, and the test instance runs on.
`OpenDialogs` closes the modal shells with `Shell.close()`, the children before their parent: a
JFace dialog answers the event `Close` with *Cancel*, but returns *OK*, if its shell is only
disposed together with its parent. The cancelled connect ends, its progress dialog closes, and the
process ends after 3 to 5 s, also with an open native dialog. Shells, that are not modal, e.g. the
workbench windows and the hidden shells of E4, stay. An answer without error, or a connection, that
breaks after sending (the server may stop before it answers), means waiting for
`ILaunch.isTerminated()`; unsaved parts mean rejecting (`CloseResult.isUnsaved()` finds the start of
its text also after the line of a forwarded result); any other outcome and the end of
`timeoutSeconds` mean `ILaunch.terminate()`, with a reason in own words: the texts of the
forwarding, e.g. *still starting?*, do not fit, after the instance answered `workspace_info`.
`stop_launch` says, when the process still runs 10 s after `terminate()`. RDi 9.6 logs *The
workspace will exit with unsaved changes in this session* (code 10035) at almost every end of the
workbench, a terminated instance does not.

**Hot code replace.** `HotCodeReplace` registers one `IJavaHotCodeReplaceListener`
(`JDIDebugModel.addHotCodeReplaceListener`), when `start_launch` starts in debug mode or
`list_launches` runs, and keeps the last result per debug target in a `WeakHashMap`; results within
2 s belong to one replacement, e.g. obsolete methods and success, and each kind is named once. The
events arrive before
`get_problems` returns. A change of the hierarchy fails, and JDT opens its dialog in the RDi, that
holds the debug launch. `Launches` reports a suspended thread of the debug targets.

### Tools

`IAgentBridgePlugin.createTools()` collects the tools, whenever the server starts: first the tools
of iAgentBridge, then the tools of the extension point `de.tools400.iagentbridge.core.plugin.tools`
(`schema/tools.exsd`), which `ContributedTools` creates. `ToolCollection` leaves out a tool, whose
name is invalid or already taken, or that fails to return its name, description or input schema of
type `object`; `ContributedTools` leaves out a tool, that cannot be created or does not implement
`IMcpTool`. Their errors go to the error log, so that one faulty plug-in does not stop the server.
The info message of the start lists the names of the tools.

The instructions, that `initialize` returns, are `IAgentBridgePlugin.INSTRUCTIONS`, followed by
those of the element `instructions` of other plug-ins. `ContributedTools` reads their files after
all tools, as UTF-8 without a byte order mark; `ToolCollection.addInstructions()` turns the line
ends into `\n`, removes the white space at the start and the end, and leaves out a text, that is
empty, the second one of a plug-in, and the one of a plug-in, of which no tool was added; such
errors and a missing or unreadable file go to the error log. `getInstructions()` joins the texts
ordered by the IDs of the plug-ins, each after an empty line. Claude Code reads the first 4096
characters, before version 2.1.296 the first 2048, so the text of the core has a budget of 1000
characters and the one of the UI plug-in 3000.

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
to describe themselves; and the instructions: order, empty line, line ends, texts left out.
`InstructionsBudgetTest` keeps `IAgentBridgePlugin.INSTRUCTIONS` within 1000 characters.
`ProblemReportTest` checks the result of `get_problems`: order, severity filter, counts, cut and
JSON; the build and the markers themselves need RDi and are tested by hand
with a test instance. `TestRunReportTest` checks the result of `run_tests`: counts, ends, cut,
filter of the stack traces and JSON; the launch and the events of JDT are tested by hand with a test
instance and test projects with green, red and hanging tests. `ProjectDirectoriesTest` checks the
search of `import_projects` in temporary directories and `ImportReportTest` its result; the import
itself is tested by hand with a test instance. `McpToolResultTest` checks the JSON of results with
images, `McpDispatcherTest` the messages of the protocol (`initialize`, `ping`, `tools/list`,
`tools/call`, notifications, errors and which of them go to the error log), `ToolArgumentsTest` the
arguments of the tools, `ServerSettingsTest` port, server name and `serverEnabled` from the
preferences and the system properties, `ClientCommandsTest` the name of the server and the command
for Claude Code, `CleanUpProfileNamesTest` the description of the clean-up profile of a project.

The tests in `eclipse/iAgentBridge UI JUnit` use no Eclipse types either. `SnapshotWriterTest`,
`LocatorBuilderTest` and `MnemonicsTest` check the text of a snapshot from a model,
`CommandLineTest` the command line of a test instance, `ForwardedResultTest` and `ForwardingTest`
the result and the decisions of the forwarding, `ForwardingClientTest` the forwarding against a real
`McpHttpServer` (image, wrong token, closed port, timeout, a call to itself, a POST sent only once
and the kinds of outcome), `UiArgumentsTest`,
`ScreenshotFilesTest` and `ImageScalerTest` the arguments, the files and the scaling of the
screenshots. For the actions `UiThreadTest` checks `UiThread.act()` with a thread of its own in
place of the UI thread (an action, that ends, opens a dialog, runs too long, or never runs),
`JournalTest` the journal and `PendingFailures`, `KeyParserTest`, `ItemPathTest`,
`WaitConditionTest` and `LpexLinesTest` the keys, paths, conditions and the lines of `ui_lpex`,
`AbstractActionToolTest` the flow of an action without a display (failures of earlier actions, a
failing snapshot, the guard for the foreground, an action or a wait, after which the display has
ended) and of `ui_wait` with a check and a snapshot of the test in place of the display,
`UiToolsTest` single tools without a widget (among them the arguments of the launch tools,
their rejection in a test instance, the error log of a file and the result of `ui_screenshot`
without the image), and `UiMessagesTest`, that both
languages have the keys of `Messages`. `ErrorLogTest` checks the parser and the report of the error
log (sessions, child entries, empty lines, continuation lines, severities, filters, cuts, a long
stack trace), `LaunchRulesTest` the checks before a start, the lock of a workspace, the decisions of
`stop_launch` and the result of `ui_close`, `IdentityTest` `Identity` against a real
`McpHttpServer`, `HotCodeReplaceTest` the text of the results of hot code replace,
`LaunchStarterTest` the attributes and the program arguments of a start (`-nosplash`). `HintsTest`
checks `Hints.after()`, `SwtHintsTest` and `LaunchHintsTest` the error texts with their hints, that
are built in code with widgets or launches. `UiInstructionsTest` checks the file of the
instructions: at most 3000 characters, the rules before the question and the steps and within the
first 2048 characters after the text of the core, no product name and no port, ASCII only. The
widgets themselves, the events of the actions, the forwarding from the RDi, in which you work, the
screenshots, and the start and end of launches with hot code replace are tested by hand with a test
instance.
`AbstractActionToolTest` and `UiToolsTest` lie in the package `de.tools400.iagentbridge.ui.tools` of
the plug-in, `HotCodeReplaceTest`, `LaunchStarterTest` and `LaunchHintsTest` in
`de.tools400.iagentbridge.ui.launches`, `SwtHintsTest` in `de.tools400.iagentbridge.ui.swt`, because
they call package visible methods.

Outside of RDi, compile with the ECJ of RDi 9.6 against all bundles of
`C:\IBM\RDi_096\configuration\org.eclipse.equinox.simpleconfigurator\bundles.info`, copy the
`messages*.properties` into the output and run `org.junit.runner.JUnitCore` with the same bundles
and JUnit and Hamcrest on the classpath: besides Gson and `org.eclipse.osgi` (for `NLS`), the tests
of the UI plug-in load classes of SWT.

A test with a real client: start an `McpHttpServer` with a test tool and let Claude Code call it
with a configuration file of its own, without changing the configuration of the user:

```powershell
claude -p "Call the tool echo of the MCP server test." --mcp-config mcp-test.json --strict-mcp-config --allowedTools mcp__test__echo
```

`mcp-test.json` contains the server with `"type": "http"`, its `url` and the `Authorization`
header.
