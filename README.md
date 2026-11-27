# iAgentBridge

*Lets AI agents like Claude Code use functions of RDi.*

iAgentBridge is a plug-in for IBM Rational Developer for i (RDi). It runs a server of the Model
Context Protocol (MCP) inside RDi, so that an AI agent, e.g. Claude Code, can call functions of
RDi as tools - with the settings, that apply in RDi.

Further information is available on the
[iAgentBridge web site](https://tools-400.github.io/iagentbridge/).

## Features

* **Clean Up** (`clean_up`): runs *Source > Clean Up* of RDi on Java files, with the clean-up
  profile of the project or else of the workspace, including the formatter. The agent gets back,
  which files Clean Up changed.
* **Problems** (`get_problems`): builds projects of the workspace and returns the errors and
  warnings of the view *Problems*, e.g. Java compile errors and errors in `MANIFEST.MF`, so that
  the agent can check, that its code compiles.
* **Tests** (`run_tests`): runs a JUnit or JUnit Plug-in Test launch configuration of RDi and
  returns the results: which tests failed, with expected and actual value and a short stack trace.
  A timeout ends tests, that hang.
* **Import** (`import_projects`): imports existing projects, e.g. of a Git working copy, into the
  workspace, like *File > Import > Existing Projects into Workspace*, so that the agent can build
  and test them.
* **Workspace information** (`workspace_info`): tells the agent, which RDi, workspace and Java
  projects it talks to and which clean-up profile applies to each project.
* **User interface of a test instance** (optional feature *iAgentBridge UI*, for developers of RDi
  plug-ins): lets the agent start a test instance of RDi from the workspace and read and operate
  its user interface like a user (`ui_snapshot`, `ui_click` and more), e.g. to check a new dialog.
  The user interface of the RDi, in which the developer works, is never touched. The server tells
  the agent, when and how to test a change of the user interface; the agent asks the developer
  first.
* **Tools of other plug-ins**: other RDi plug-ins can add tools to the server, e.g. `format_rpg` of
  iRPGFormatter, which formats RPG sources with the settings of RDi, and `ifs_projects_compile` and
  `ifs_projects_check_syntax` of IFS Projects, which compile files of local IFS projects on the
  IBM i and check their syntax. They come with optional features of these plug-ins.
* **Preference page**: sets the port and whether RDi starts the server, shows the token and offers
  the ready-made command, that registers the server in Claude Code.
* **Local and protected**: the server listens on `127.0.0.1` only and requires a token.
* **One server per RDi**: each RDi instance can run its own server on its own port; RDi 9.6, 9.8
  and 9.9 get different default ports.

## Requirements

* RDi 9.6, 9.8 or 9.9.
* An MCP client with the transport *Streamable HTTP* and its own HTTP headers, e.g. Claude Code.

## Installation

1. Choose *Help > Install New Software...* in RDi.
2. Add the update site `https://tools-400.github.io/iagentbridge/update-site/eclipse/rdi8.0/` and
   select *iAgentBridge*; developers of RDi plug-ins also *iAgentBridge UI*.
3. Finish the wizard and restart RDi. The view *Error Log* shows the address of the server.

## Usage

[USAGE.md](USAGE.md) describes how to connect an MCP client, the tools and the UI tools, the
preferences and system properties, test instances and security.

## For developers

How the plug-in works internally, how to add a tool to iAgentBridge itself and how to run the
tests: [DEVELOPMENT.md](DEVELOPMENT.md).
How to add a tool with a plug-in of your own: [EXTENDING.md](EXTENDING.md).

## Liability

This software is free and provided without warranty. You use it at your own risk: test it with
non-critical data first, and back up your data before you use it. The [license](LICENSE) applies in
addition.

## See Also

* [iAgentBridge web site](https://tools-400.github.io/iagentbridge/)
* [iAgentBridge Update Site](https://github.com/tools-400/iagentbridge)
* [Model Context Protocol](https://modelcontextprotocol.io)
* [Connect Claude Code to tools via MCP](https://code.claude.com/docs/en/mcp)
