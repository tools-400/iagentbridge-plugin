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
   `mcp__rdi096__workspace_info`. Check the connection with `claude mcp get rdi096`.

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

### Tools of other plug-ins

Other RDi plug-ins can add tools to the server. Install them like any other plug-in and restart
RDi; the agent sees them in a new session, e.g. as `mcp__rdi096__format_rpg`.

| Tool | Plug-in | Content |
|---|---|---|
| `format_rpg` | optional feature *iRPGFormatter iAgentBridge* of iRPGFormatter | formats RPG sources like *iFormat* of RDi, see the usage of iRPGFormatter |

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
  copies it to the clipboard.

## System properties

System properties override the preferences, e.g. in `eclipse.ini` after `-vmargs` or in the VM
arguments of a launch configuration:

| Property | Content |
|---|---|
| `iagentbridge.port` | port of the server; the preference page shows it and disables the field *Port* |
| `iagentbridge.serverEnabled` | `true` or `false`: whether RDi starts the server, instead of the preference *Start the server when RDi starts*; the preference page shows it and disables the check box. *Start* starts the server all the same |
| `iagentbridge.token` | token, that the client must send as `Authorization: Bearer <token>`, instead of the token file |

## Test instances

A test instance started from the workspace (*Run As > Eclipse Application*) starts a server, too,
if the plug-in is part of the launch configuration. The launch configurations of iAgentBridge set
the ports `39196`, `39198` and `39199` for RDi 9.6, 9.8 and 9.9. In other launch configurations
set `-Diagentbridge.port=...`, `-Diagentbridge.serverEnabled=false` or remove the plug-in, e.g. in
a *JUnit Plug-in Test*, that `run_tests` starts. Otherwise the instance, that starts first, gets
the port, and the other one shows the error on the preference page and in the *Error Log* view.

## Security

* The server listens on the loopback address `127.0.0.1` only. Other computers cannot reach it.
* Every request must carry the token. Keep the token file private.
* Requests of web pages of other hosts are rejected (`Origin` header), so that a web page in a
  browser cannot call the server.
