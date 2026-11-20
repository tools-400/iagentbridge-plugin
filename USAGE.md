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

4. Start a new Claude Code session. The tools are named `mcp__rdi096__clean_up` and
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

### workspace_info

Describes the RDi instance, the server runs in: product and version, Eclipse platform, Java,
workspace location, server URL and the open Java projects with their locations and their clean-up
profile, e.g. `iSphere Clean Up Profile (Helios) (project settings)`. Use it to check, which RDi
and workspace an agent talks to. It has no arguments.

## Preferences

*Window > Preferences > iAgentBridge*. The preferences are stored in the workspace, so every RDi
version with a workspace of its own can use its own port.

| Preference | Default | Content |
|---|---|---|
| *Start the server* | on | starts the server, when RDi starts and when you press *Apply* |
| *Port* | `39096` | port of the server on `127.0.0.1`; every RDi instance needs a port of its own |

*Apply* restarts the server, if the start or the port changed. The group *Connection* shows:

* **Status**: the address of the running server, *Stopped*, or the error of the last start, e.g.
  a port that another RDi instance uses already.
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
| `iagentbridge.token` | token, that the client must send as `Authorization: Bearer <token>`, instead of the token file |

## Test instances

A test instance started from the workspace (*Run As > Eclipse Application*) starts a server, too,
if the plug-in is part of the launch configuration. The launch configurations of iAgentBridge set
the ports `39196`, `39198` and `39199` for RDi 9.6, 9.8 and 9.9. In other launch configurations
either set `-Diagentbridge.port=...` or remove the plug-in. Otherwise the instance, that starts
first, gets the port, and the other one shows the error on the preference page and in the *Error
Log* view.

## Security

* The server listens on the loopback address `127.0.0.1` only. Other computers cannot reach it.
* Every request must carry the token. Keep the token file private.
* Requests of web pages of other hosts are rejected (`Origin` header), so that a web page in a
  browser cannot call the server.
