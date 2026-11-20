# iAgentBridge

*Lets AI agents like Claude Code use functions of RDi.*

iAgentBridge is a plug-in for IBM Rational Developer for i (RDi). It runs a server of the Model
Context Protocol (MCP) inside RDi, so that an AI agent, e.g. Claude Code, can call functions of
RDi as tools - with the settings, that apply in RDi.

## Features

* **Clean Up**: runs *Source > Clean Up* of RDi on Java files, with the clean-up profile of the
  project or else of the workspace, including the formatter. The agent gets back, which files
  Clean Up changed.
* **Workspace information**: tells the agent, which RDi, workspace and Java projects it talks to
  and which clean-up profile applies to each project.
* **Tools of other plug-ins**: other RDi plug-ins can add tools to the server, e.g. `format_rpg` of
  iRPGFormatter, which formats RPG sources with the settings of RDi.
* **Preference page**: start of the server, port, token and the ready-made command, that
  registers the server in Claude Code.
* **Local and protected**: the server listens on `127.0.0.1` only and requires a token.
* **One server per RDi**: each RDi instance can run its own server on its own port; RDi 9.6, 9.8
  and 9.9 get different default ports.

## Requirements

* RDi 9.6, 9.8 or 9.9.
* An MCP client with the transport *Streamable HTTP* and its own HTTP headers, e.g. Claude Code.

## Installation

1. Choose *Help > Install New Software...* in RDi.
2. Add the update site `https://tools-400.github.io/iagentbridge/update-site/eclipse/rdi8.0/` and
   select *iAgentBridge*.
3. Finish the wizard and restart RDi. The *Error Log* view shows the address of the server.

Home page: https://tools-400.github.io/iagentbridge/.

## Usage

[USAGE.md](USAGE.md) describes how to connect an MCP client, the tools and the preferences.

## For developers

How the plug-in works internally, how to add a tool and how to run the tests:
[DEVELOPMENT.md](DEVELOPMENT.md).
How to write a plug-in, that adds a tool to the server: [EXTENDING.md](EXTENDING.md).

## Liability

This software is free and provided without warranty. You use it at your own risk: test it with
non-critical data first, and back up your data before you use it. The [license](LICENSE) applies in
addition.

## See Also

* [Model Context Protocol](https://modelcontextprotocol.io)
* [Connect Claude Code to tools via MCP](https://code.claude.com/docs/en/mcp)
