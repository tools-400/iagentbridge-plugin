# RDi MCP Server – Usage

How to connect an MCP client to RDi and how to use the tools. For what the plug-in is for, its
requirements and how to install it, see [README.md](README.md).

## Quick start

1. Install the plug-in and restart RDi. The *Error Log* view shows the message
   `RDi MCP server listening on http://127.0.0.1:39096/mcp, token: ...`.
2. Register the server in Claude Code (PowerShell), with a name of your choice, e.g. `rdi096`:

   ```powershell
   claude mcp add --scope user --transport http rdi096 http://127.0.0.1:39096/mcp --header "Authorization: Bearer $(Get-Content $HOME\.rdimcp\token)"
   ```

3. Start a new Claude Code session. The tools are named `mcp__rdi096__clean_up` and
   `mcp__rdi096__workspace_info`. Check the connection with `claude mcp get rdi096`.

The name is the name of the entry in the configuration of Claude Code (`~/.claude.json` with
`--scope user`); the plug-in does not know it. Remove the entry with
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

## Settings

The server is configured with system properties, e.g. in `eclipse.ini` after `-vmargs`:

| Property | Default | Content |
|---|---|---|
| `rdimcp.port` | `39096` | port of the server |
| `rdimcp.token` | content of `%USERPROFILE%\.rdimcp\token` | token, that the client must send as `Authorization: Bearer <token>` |

Without `rdimcp.token`, the plug-in reads the token from `%USERPROFILE%\.rdimcp\token`. If the
file does not exist, the plug-in creates it with a random token. All RDi instances of the user
share the file.

## Several RDi instances

Each RDi instance needs its own port. Set `-Drdimcp.port=...` in the `eclipse.ini` of the other
RDi versions and register each server under its own name, e.g. `rdi099` for RDi 9.9.

A test instance started from the workspace (*Run As > Eclipse Application*) starts a server, too,
if the plug-in is part of the launch configuration. Then either set `-Drdimcp.port=...` in the VM
arguments of the launch configuration or remove the plug-in from it. Otherwise the instance, that
starts first, gets the port, and the other one writes an error into its *Error Log* view.

## Security

* The server listens on the loopback address `127.0.0.1` only. Other computers cannot reach it.
* Every request must carry the token. Keep the token file private.
* Requests of web pages of other hosts are rejected (`Origin` header), so that a web page in a
  browser cannot call the server.
