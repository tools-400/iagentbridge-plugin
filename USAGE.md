# iAgentBridge – Usage

How to connect an MCP client to RDi, how to use the tools and how to set up the server. For what
the plug-in is for, its requirements and how to install it, see [README.md](README.md).

## Quick start

1. Install the plug-in and restart RDi. The *Error Log* view shows the message
   `iAgentBridge server listening on http://127.0.0.1:39096/mcp, token: ...`.
2. Open *Window > Preferences > iAgentBridge* and press *Copy* next to the command for Claude Code.
3. Run the command in PowerShell. It registers the server in Claude Code for all your projects,
   under a name like `rdi096` for RDi 9.6:

   ```powershell
   claude mcp add --scope user --transport http rdi096 http://127.0.0.1:39096/mcp --header "Authorization: Bearer $(Get-Content 'C:\Users\<user>\.iagentbridge\token')"
   ```

4. Start a new Claude Code session. The tools are named `mcp__rdi096__clean_up`,
   `mcp__rdi096__get_problems`, `mcp__rdi096__import_projects`, `mcp__rdi096__run_tests` and
   `mcp__rdi096__workspace_info`, with the optional feature *iAgentBridge UI* also the
   [UI tools](#ui-tools), e.g. `mcp__rdi096__ui_snapshot` and `mcp__rdi096__ui_click`. Check the
   connection with `claude mcp get rdi096`.

The name is the name of the entry in the configuration of Claude Code (`~/.claude.json` with
`--scope user`); the plug-in only suggests it. Remove the entry with
`claude mcp remove rdi096 -s user`.

## Tools

### clean_up

Runs *Source > Clean Up* of RDi on Java files and saves them, like the menu with the configured
profiles. The clean-up profile is the one of the project settings, if the project has its own
settings, otherwise the one of the workspace. The formatter is part of Clean Up, if the profile
enables it.

Argument:

| Name | Content |
|---|---|
| `files` | absolute paths of the `.java` files, e.g. `C:/workspaces/project/src/a/B.java` or `/c/workspaces/project/src/a/B.java` (Git Bash) |

Result, for each file: the project, the clean-up profile and whether Clean Up changed the file,
or the reason why it was skipped:

| Message | Reason |
|---|---|
| `File not found.` | the path does not exist |
| `The file does not belong to an open project of the RDi workspace ...` | the file is outside of the projects of the workspace, e.g. in a Git worktree or in a closed project |
| `The file is not a Java source file on the build path of project ...` | the file is not in a source folder of a Java project |
| `The file has unsaved changes in an RDi editor.` | save or discard the changes in RDi first |

The tool reads the files from disk before Clean Up, so changes made outside of RDi are taken into
account. Messages of the condition checks of Clean Up, e.g. about compile errors, are returned in
`messages`.

### get_problems

Returns the errors and warnings, that RDi shows in the view *Problems*: compile errors and
warnings of Java files, errors of the build path and the errors of the plug-in development tools,
e.g. in `MANIFEST.MF`, `plugin.xml` and `build.properties`. An agent calls it after it changed
files outside of RDi, to check that the code compiles.

Before it reads the problems, the tool reads the projects and the projects, that they depend on,
from disk and builds them. Then it waits for the automatic build of RDi, so that no problems are
missing. Reading and building run as jobs *iAgentBridge: Refreshing projects* and *iAgentBridge:
Building projects*, which the view *Progress* shows and in which you can cancel them.

Arguments, all optional:

| Name | Default | Content |
|---|---|---|
| `projects` | all open projects | names of projects of the RDi workspace |
| `files` | – | absolute paths of files, like the files of `clean_up`; the tool builds their projects and returns only the problems of these files and of their projects, e.g. an error of the build path. Errors, that a change causes in other files, e.g. in the callers of a changed method, are returned only with `projects`. Not together with `projects`. |
| `build` | `incremental` | `incremental` reads the projects from disk and builds the changes, `full` rebuilds the projects completely, `none` returns the problems, that RDi shows now, without reading from disk and without a build |
| `minSeverity` | `warning` | lowest severity returned: `error`, `warning` or `info` |
| `maxProblems` | `200` | maximum number of problems returned |

Result:

```json
{
  "build": "incremental",
  "counts": { "error": 1, "warning": 3 },
  "truncated": false,
  "problems": [
    { "severity": "error", "file": "C:/ws/p/src/a/B.java", "project": "p", "line": 42,
      "message": "The method foo() is undefined for the type B",
      "type": "org.eclipse.jdt.core.problem", "id": 67108964 }
  ],
  "warnings": [ "C:/ws/p/src/a/C.java has unsaved changes in an RDi editor. Its problems are those of the file on disk." ]
}
```

* `counts`: the number of problems per severity, from `error` down to `minSeverity`, also of the
  problems, that `maxProblems` cuts off.
* `truncated`: `true`, if there are more problems than `maxProblems`.
* `problems`: sorted by severity, file and line. `file` is the absolute path of the file, or of the
  folder or project, e.g. for an error of the build path. `line` is missing, if the problem has no
  line. `type` is the type of the marker, e.g. `org.eclipse.jdt.core.problem` of the Java compiler
  or `org.eclipse.pde.core.problem` of the plug-in development tools. `id` is the ID of a problem
  of the Java compiler.
* `warnings`: files with unsaved changes in an RDi editor. Their problems are those of the file on
  disk, not of the text in the editor. Also errors of reading from disk or of the build, e.g. of a
  builder of a project, with the messages of RDi: `The build failed, the problems may be
  incomplete: ...`. RDi goes on with the other builders and projects, so the tool returns the
  problems all the same. RDi shows no dialog for such an error; the view *Progress* keeps it, and
  an error of a builder is also in the *Error Log*.

The tool fails with one of these messages:

| Message | Reason |
|---|---|
| `Project '...' is not an open project of the RDi workspace ...` | the project does not exist or is closed |
| `<path>: File not found.` | the path does not exist |
| `<path>: The file does not belong to an open project of the RDi workspace ...` | the file is outside of the projects of the workspace, e.g. in a Git worktree or in a closed project |
| `Pass either 'projects' or 'files', not both.` | both arguments are set |
| `Argument '...' must be ...` | an argument has a wrong type or value |
| `The refresh or the build was cancelled in RDi.` | the job was cancelled in the view *Progress* |

### import_projects

Imports existing projects into the RDi workspace, like *File > Import... > General > Existing
Projects into Workspace* without *Copy projects into workspace*: the projects stay, where they are,
e.g. in a Git working copy. An agent calls it, when a project, that it wants to build or test with
the other tools, is not in the workspace yet. The tool runs as job *iAgentBridge: Importing
projects*, which the view *Progress* shows and in which you can cancel it.

Argument:

| Name | Content |
|---|---|
| `paths` | absolute paths of directories: the directory of a project, which contains the file `.project`, or a directory, below which the tool searches projects, e.g. the working copy of a repository |

The tool searches like the wizard with *Search for nested projects* turned off: it does not search
the directories of the projects, that it finds, and it skips the directories `.metadata` and `.git`.
A project gets the name of its file `.project` and is opened. Afterwards RDi builds it, if *Project
> Build Automatically* is on; `get_problems` builds it and returns its problems.

Result, for each project:

```json
{
  "counts": { "imported": 1, "existing": 1, "failed": 1 },
  "projects": [
    { "result": "imported", "project": "my.lib", "location": "C:/git/repo/my.lib" },
    { "result": "existing", "project": "my.app", "location": "C:/git/repo/my.app" },
    { "result": "failed", "project": "my.test", "location": "C:/git/repo/my.test",
      "message": "A project of that name is already in the RDi workspace at C:/git/old/my.test." }
  ]
}
```

* `imported`: the project is new in the workspace.
* `existing`: the directory is already a project of the workspace, maybe with another name;
  `message` says, if it is closed.
* `failed`: `message` tells why, see the table below. `project` is missing, if the tool found no
  project.

The tool fails with the same JSON, if a project could not be imported, with `"cancelled": true`,
if the import was cancelled in the view *Progress*, and with `error`, if the import ended with an
error of RDi; the projects listed have been handled before.

| Message | Reason |
|---|---|
| `The path is not absolute.` | e.g. `.` |
| `Directory not found.` | the path does not exist |
| `Not a directory.` | the path is a file, e.g. the file `.project` |
| `No project found: ...` | neither the directory nor a directory below it contains a file `.project` |
| `A project of that name is already in the RDi workspace at ...` | another directory is imported under the same name |
| messages of RDi | e.g. the file `.project` cannot be read, or the directory is directly in the directory of the workspace, but has another name than the project |

### run_tests

Runs a launch configuration of the type *JUnit* or *JUnit Plug-in Test*, like *Run* in RDi, waits
for the end of the tests and returns their results. The view *JUnit* of RDi shows the test run as
usual. The tool runs as job *iAgentBridge: Running the tests of* with the name of the launch
configuration, which the view *Progress* shows and in which you can cancel it.

The tool does not build: call `get_problems` first, which reads the changed files from disk and
builds them. The tool does not start the tests, if the project of the launch configuration or a
project, that it references, has compile errors or files with unsaved changes in an RDi editor,
because RDi would ask in a dialog first. For a *JUnit Plug-in Test* RDi also checks the other
plug-ins of the workspace, that the test launches; their compile errors still open the dialog, which
must be answered in RDi.

Arguments:

| Name | Default | Content |
|---|---|---|
| `launch` | – | name of the launch configuration, as RDi shows it in *Run > Run Configurations...* |
| `timeoutSeconds` | `600` | maximum duration of the test run, including the start of the test VM, but not a dialog of RDi before the launch; then the tool ends the test run |
| `maxFailures` | `20` | maximum number of failed tests returned |

Result:

```json
{
  "launch": "my tests",
  "result": "failed",
  "counts": { "run": 3, "failures": 1, "errors": 1, "ignored": 1 },
  "seconds": 1.0,
  "truncated": false,
  "failures": [
    { "result": "error", "class": "a.BTest", "method": "testFoo",
      "trace": "java.lang.NullPointerException\n\tat a.BTest.testFoo(BTest.java:18)" },
    { "result": "failure", "class": "a.BTest", "method": "testBar",
      "expected": "expected text", "actual": "actual text",
      "trace": "org.junit.ComparisonFailure: expected:<[expected] text> but was:<[actual] text>\n\tat a.BTest.testBar(BTest.java:12)" }
  ]
}
```

* `result`: `passed`, if all tests ran and none failed, `failed`, if all tests ran and at least one
  failed. Failed tests are a result, not an error of the tool. In the cases of the table below the
  tool fails, with the same JSON.
* `counts`: the tests, that ran (without the ignored ones), the failures (failed assertions), the
  errors (exceptions) and the ignored tests, also of the failed tests, that `maxFailures` cuts off.
* `failures`: `result` is `failure` or `error`. `method` is missing for an error of the whole test
  class, e.g. in a `@BeforeClass` method. `expected` and `actual` are the values of a failed
  comparison, e.g. of `assertEquals` with strings. `trace` is the stack trace without the frames of
  JUnit and of reflection, like the filter of the view *JUnit*, cut after 30 lines; long values are
  cut after 4000 characters.
* `truncated`: `true`, if more tests failed than `maxFailures`.
* `running` (only when the test run did not end normally): the tests, that had started and not
  ended.
* `warnings` (only when the test run did not end normally): why.

| `result` | Reason |
|---|---|
| `timeout` | the test run took longer than `timeoutSeconds`; the tool terminated the launch |
| `stopped` | the test run was stopped in the view *JUnit*. JUnit stops after the running test, so a test, that hangs, ends only with the timeout or with *Terminate* in the view *Console* |
| `aborted` | the launch ended, before all tests ran, e.g. because a test called `System.exit()`, the test VM could not start or the launch was terminated in RDi; the view *Console* shows the output of the test VM. Also, when RDi did not start the launch, e.g. because a dialog of RDi was cancelled |

The tool fails with one of these messages:

| Message | Reason |
|---|---|
| `Launch configuration '...' not found. JUnit launch configurations of RDi: ...` | there is no launch configuration of that name |
| `Launch configuration '...' is of type '...', not of type JUnit or JUnit Plug-in Test. ...` | the launch configuration runs no JUnit tests |
| `The tests were not started, because RDi would ask in a dialog first. ...` | compile errors or files with unsaved changes, see above |
| `The test run was cancelled in RDi.` | the job was cancelled in the view *Progress*; the tool terminated the launch |
| `The launch failed: ...` | RDi could not start the launch, e.g. because the project of the launch configuration does not exist |
| `Argument '...' is missing.`, `Argument '...' must be ...` | `launch` is missing, or an argument has a wrong type or value |

The server handles one call after the other: while the tests run, calls of other tools wait, also
those of other agents.

### workspace_info

Describes the RDi instance, the server runs in: product and version, Eclipse platform, Java,
workspace location, server URL and the open Java projects with their locations and their clean-up
profile, e.g. `iSphere Clean Up Profile (Helios) (project settings)`. Use it to check, which RDi
and workspace an agent talks to. It has no arguments.

### UI tools

For developers of RDi plug-ins: the optional feature *iAgentBridge UI* lets an agent read and
operate the user interface of a **test instance** of RDi, that you started from the workspace, e.g.
to check a new dialog: open it, fill in its fields, press its buttons and look at the result.
Install the feature into the RDi, in which you work, and start the test instance with a launch
configuration, that has the VM argument `-Diagentbridge.uiTools=true`, see [Test
instances](#test-instances).

The tools never read or change the user interface of the RDi, in which you work. Its server offers
them, but forwards every call to the running test instance; the test instance reads its user
interface and answers. So Claude Code needs only the entry of your RDi, e.g. `rdi096`, and a
restart of the test instance needs no reconnect. The first line of the answer names the test
instance.

**ui_snapshot** returns the user interface as indented text: first the workbench (active shell,
focus, perspective, views, editors with *dirty*, shells, an open native dialog), then the widget
tree of a shell, one line per widget: type, text, reference like `[w17]`, label, value, state
(`disabled`, `selected`, `focus`, `default`, ...), a locator in the style of SWTBot, e.g.
`textWithLabel("Port:")`, and the tooltip. Tables show their columns and the first 50 rows (a
virtual table only its visible rows), lists their first 50 items, trees their expanded items,
progress bars, scales and sliders their value and range, e.g. `value="40" range=0..100`, the LPEX
editor its name, number of lines and cursor. A reference stays valid, as long as the widget lives.

```
display: activeShell="Preferences" focus=tree [w2]
workbench: perspective "Resource" (org.eclipse.ui.resourcePerspective), activePart "Project Explorer"
views: "Project Explorer" (org.eclipse.ui.navigator.ProjectExplorer), "Outline" (org.eclipse.ui.views.ContentOutline)
editors: none
shells: "runtime-iagentbridge-en - Resource - IBM Rational Developer for i" [w9], "Preferences" [w1] modal
shell "Preferences" [w1] modal active
  tree [w2] focus
    item "General" [w3] collapsed
    item "iAgentBridge" [w4] selected
  group "Connection" [w10]
    text [w11] label="Port:" value="39196" locator=textWithLabel("Port:")
  button "Apply and Close" [w20] default locator=button("Apply and Close")
(7 nodes)
```

| Argument | Type | Default | Content |
|---|---|---|---|
| `shell` | string | the active shell | reference or title of a shell, or `all` for all visible shells |
| `ref` | string | – | reference of a widget, e.g. `w17`: only its sub tree |
| `maxNodes` | integer | 400 | number of widgets, after which the snapshot is cut |
| `toolItems` | boolean | false | also show the tool bars and their items |

**ui_screenshot** returns a PNG image of a shell or a control and saves it in
`%TEMP%\iagentbridge\screenshots\` (the folder keeps the newest 50 images). The controls draw
themselves, so the image is right, also when other windows cover the test instance; the test
instance is not brought to the front. The image of a shell has no title bar and no menu bar.

| Argument | Type | Default | Content |
|---|---|---|---|
| `shell` | string | the active shell | reference or title of a shell |
| `ref` | string | – | reference of a control instead of a shell; not together with `shell` |
| `maxWidth` | integer | 1280 | wider images are scaled down to this width |

**Actions** operate the test instance like a user. They send the events of the mouse and the
keyboard to the widgets in the order, in which SWTBot sends them, without the mouse and the keyboard
of the operating system, so you can go on working, while an agent operates the test instance. Before
an action, the tool checks, that the widget is visible and enabled, that no dialog blocks its shell,
and that a row or a tree item still shows the text of the last snapshot. An action on a widget of a
view or an editor activates it first, like a click into it, so that e.g. the items of a context menu
work with its selection. Afterwards it returns the snapshot of the active shell; `snapshot: false`
turns it off. A dialog, that an action opens, may come on top of your windows, e.g. when the test
instance runs in the debugger; it is put behind all other windows then, so that it does not cover
your work, and flickers briefly. The UI tools read it all the same. The page *iAgentBridge > UI
tools* of the preferences lets it lie on top instead, see [Preferences](#preferences).

| Tool | Arguments | Content |
|---|---|---|
| `ui_click` | `ref`, `double` (false) | clicks a push button, a checkbox, radio or toggle button, a link (its first hyperlink), a tab, a tool item, a row of a table or an item of a tree; `double: true` double clicks a row or an item, e.g. to open a file |
| `ui_type` | `ref`, `text`, `clear` (true), `submit` (false) | types into a text field, an editable combo, a StyledText or a spinner (an integer); `clear: false` inserts at the cursor (text fields and StyledText only); `submit: true` sends DefaultSelection to the field, e.g. for a filter field. Afterwards the field gets KeyUp and FocusOut, so that a preference page checks the value like after typing |
| `ui_select` | `ref`, `values` | selects entries of a combo or a list, rows of a table by the text of their first column, or items of a tree by paths like `General > Editors`: the levels of a path are expanded, and the tool waits for the next level, which e.g. *Remote Systems* creates later. A virtual table is searched in its visible rows only |
| `ui_check` | `ref`, `checked` | checks or unchecks a checkbox, a toggle button, a radio button (only `true`) or a row or tree item with a check box; nothing is done, if the state is right already |
| `ui_menu` | `path`, `ref` | selects an item of the menu bar of the active shell, e.g. `Window > Preferences...`, or with `ref` of the context menu of a widget, a row or a tree item. Mnemonics, shortcuts and `...` at the end do not matter; the texts depend on the language of the test instance. A wrong path returns the items of the menu |
| `ui_key` | `keys`, `ref` | without `ref` ESC, which closes the active dialog, or ENTER, which clicks its default button; with `ref` keys like `Ctrl+S`, `F5` or `Shift+Tab` to that widget. They work in widgets, that draw themselves, like the LPEX editor, not in native text fields (use `ui_type`), and they never trigger shortcuts of the workbench (use `ui_command`, e.g. `org.eclipse.ui.edit.delete` for DELETE in the LPEX editor) |
| `ui_command` | `id`, `parameters` | executes an Eclipse command, e.g. `org.eclipse.ui.window.preferences`, independent of the language |
| `ui_wait` | one of `shell`, `text`, `gone`, `jobsIdle`; `timeoutSeconds` (10, at most 60) | waits for a shell with a title, a text in the active shell (searched like the snapshot shows it), the end of a shell or a widget (an unknown reference is an error), or the end of all jobs, and returns the snapshot. While it waits, the server of the RDi, in which you work, answers no other call |
| `ui_lpex` | `ref`, `from`, `to`, `line`, `column`, `text` | reads lines of the LPEX editor with their numbers, the cursor and the changes, or sets the cursor and inserts text at it; the text is inserted also in replace mode, not into a read-only document. Keys like ENTER with `ui_key`; DELETE is no key of LPEX in RDi, use `ui_command` with `org.eclipse.ui.edit.delete` |

When an action opens a modal dialog, it returns only, when the dialog is closed. The tool then
answers `The action has not returned yet - a dialog is open.` with the snapshot of the dialog; if
the action fails later, the next UI tool reports it in its first line. Commands, that depend on the
active part, e.g. *Save*, may be disabled, while the test instance is in the background: save in
the LPEX editor with `ui_key` and `Ctrl+S`.

Limits: password fields have no value, also those, that only show an echo character like the
sign-on dialog of RSE, and the actions never type into them. Native dialogs of Windows, e.g. *Open
File*, are no widgets: the snapshot only reports `native dialog open`, and the actions are rejected,
until you close the dialog. When RDi is not in the foreground, the operating system shows
no active shell; then the deepest visible shell counts as active, modal shells first.

| Error | Meaning |
|---|---|
| `No test instance with the UI tools is running. ...` | start a test instance; the message names the launch configurations with `-Diagentbridge.uiTools=true` |
| `Several test instances with the UI tools are running: ...` | stop all but one |
| `... does not accept connections yet - still starting?` | the test instance is still starting, or its server is not running |
| `... did not answer within 30 s - stopped in the debugger? ...` | the test instance stops at a breakpoint, or the port of its launch configuration is the port of the RDi, in which you work |
| `... rejected the token (HTTP 401) ...` | the launch configuration sets another token with `-Diagentbridge.token` |
| `UI thread busy for 10 s - ...` | a long operation or a native dialog blocks the user interface of the test instance |
| `Ref w17 is unknown or outdated, take a new snapshot.` | the widget does not exist any more |
| `Ref w17 now shows "..." instead of "..." - its viewer was refreshed. ...` | the viewer reuses its rows or items after a refresh; take a new snapshot |
| `The shell "..." is blocked by the modal dialog "..." ...` | operate or close the dialog first |
| `The native dialog "..." is open. ...` | close the native dialog in the test instance by hand |
| `... is a password field. ...` | enter passwords in the test instance yourself |
| `Not executed - UI thread busy for 20 s. ...` | the action did not run and will not run; repeat it later |
| `UI thread busy for 20 s - the action is still running. ...` | the action runs; take a snapshot later instead of repeating it |
| `The earlier action ... failed: ...` | an action, that had opened a dialog, failed after the dialog was closed |
| `A listener of the action failed: ...` | code of the test instance threw an exception during the action |
| `The command '...' is not enabled in the current state of the workbench. ...` | the command has no enabled handler now, see above |

### Tools of other plug-ins

Other RDi plug-ins can add tools to the server. Install them like any other plug-in and restart
RDi; the agent sees them in a new session, e.g. as `mcp__rdi096__format_rpg`.

| Tool | Plug-in | Content |
|---|---|---|
| `format_rpg` | optional feature *iRPGFormatter iAgentBridge* of iRPGFormatter | formats RPG sources like *iFormat* of RDi, see the usage of iRPGFormatter |
| `ifs_projects_compile` | optional feature *IFS Projects iAgentBridge* of IFS Projects | compiles a file of a local IFS project on the IBM i like *Build on IBM i*, see the usage of IFS Projects |
| `ifs_projects_check_syntax` | optional feature *IFS Projects iAgentBridge* of IFS Projects | checks the syntax of an RPGLE or SQLRPGLE file of a local IFS project on the IBM i like *Check Syntax*, see the usage of IFS Projects |

The message `iAgentBridge server listening on ...` in the *Error Log* view ends with the names of
all tools, that the server offers. A tool of another plug-in, that fails to start, is left out;
the *Error Log* view shows the reason.

## Preferences

*Window > Preferences > iAgentBridge*. The preferences are stored in the workspace, so every RDi
version with a workspace of its own can use its own port.

| Preference | Default | Content |
|---|---|---|
| *Start the server when RDi starts* | on | starts the server automatically, when RDi starts |
| *Port* | `39096` for RDi 9.6, `39098` for 9.8, `39099` for 9.9 | port of the server on `127.0.0.1`; every RDi instance needs a port of its own |

The default port depends on the version of RDi, so that RDi 9.6, 9.8 and 9.9 can run their servers
at the same time. It matches the suggested name of the server in Claude Code, e.g. `rdi098` and
port `39098`.

*Apply* restarts a running server, if its port changed. The check box neither starts nor stops the
server: it only tells, whether RDi starts it. The group *Connection* shows:

* **Status**: a green circle and the address of the running server, a gray circle and *Stopped*,
  or a red circle and the error of the last start, e.g. *Port 39098 is in use*, when another RDi
  instance uses the port already. *Start* saves the settings and starts the server now, e.g. after
  the port has become free or has been changed; it is active only while the server does not run.
* **Token**: the token file `%USERPROFILE%\.iagentbridge\token`. The plug-in creates it with a
  random token at the first start; all RDi instances of the user share it. *New Token* replaces
  the token; register the clients again afterwards.
* **Command for Claude Code**: the command of the quick start, with the port of this RDi. *Copy*
  copies it to the clipboard. If the system property `iagentbridge.serverName` has an invalid
  value, the field shows the error instead of a command.

With the optional feature *iAgentBridge UI*, the page *iAgentBridge > UI tools* sets, where the
dialogs, that an action of the [UI tools](#ui-tools) opens in the test instance, lie:

| Preference | Default | Content |
|---|---|---|
| *Dialogs, that an action opens in the test instance* | *In the background* | *In the background*: they are put behind all other windows and flicker briefly, if they came on top. *In the foreground*: they stay, where Windows puts them, e.g. on top of the other windows, when the test instance runs in the debugger |

Set it in the RDi, in which you work: it passes the setting to the test instance with every call of
a UI tool. In the test instance it applies only to calls on its own port.

## System properties

System properties override the preferences, e.g. in `eclipse.ini` after `-vmargs` or in the VM
arguments of a launch configuration:

| Property | Content |
|---|---|
| `iagentbridge.port` | port of the server; the preference page shows it and disables the field *Port* |
| `iagentbridge.serverEnabled` | `true` or `false`: whether RDi starts the server, instead of the preference *Start the server when RDi starts*; the preference page shows it and disables the check box. *Start* starts the server all the same |
| `iagentbridge.token` | token, that the client must send as `Authorization: Bearer <token>`, instead of the token file |
| `iagentbridge.serverName` | name of the server in the command for Claude Code on the preference page, instead of the name derived from the version of RDi, e.g. `rdi096test` in a test instance; letters, digits, `_` and `-` |
| `iagentbridge.uiTools` | `true`: this RDi is a test instance, that executes the UI tools of the feature *iAgentBridge UI*; without it, they are forwarded to such a test instance |

## Test instances

A test instance started from the workspace (*Run As > Eclipse Application*) starts a server, too,
if the plug-in is part of the launch configuration. The launch configurations of iAgentBridge set
the ports `39196`, `39198` and `39199` for RDi 9.6, 9.8 and 9.9. In other launch configurations
set `-Diagentbridge.port=...`, `-Diagentbridge.serverEnabled=false` or remove the plug-in, e.g. in
a *JUnit Plug-in Test*, that `run_tests` starts. Otherwise the instance, that starts first, gets
the port, and the other one shows the error on the preference page and in the *Error Log* view.

The launch configurations of iAgentBridge also set `-Diagentbridge.uiTools=true`, so that the test
instance executes the [UI tools](#ui-tools), and
`-Diagentbridge.serverName=rdi096test` (`rdi098test`, `rdi099test`), so that its preference page
does not suggest the name of the RDi, in which you work. The test instance needs no entry in Claude
Code. The RDi, in which you work, finds it among its running launches of the type *Eclipse
Application*: by `-Diagentbridge.uiTools=true` in the command line of the process, which also
gives the port and, if set, the token. Only one such test instance may run at a time.

## Security

* The server listens on the loopback address `127.0.0.1` only. Other computers cannot reach it.
* Every request must carry the token. Keep the token file private.
* Requests of web pages of other hosts are rejected (`Origin` header), so that a web page in a
  browser cannot call the server.
* The UI tools read and operate only a test instance with `-Diagentbridge.uiTools=true`, never the
  RDi, in which you work. They return password fields without value and never type into them;
  sign on to the IBM i in the test instance yourself. A click in the test instance can run commands
  on the IBM i like a click of yours: give the test instance a workspace of its own and connections
  to test systems and test libraries only.
