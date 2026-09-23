# ClaudeCraft

Claude inside Minecraft. Chat with Claude Code from an in-game panel while you play, and let it see and build in your world.

Inspired by [t3craft](https://github.com/maxwellyoung/t3craft), but talks to Claude Code directly.

## Features

- Minecraft-styled panel with threads, streaming Markdown replies, tool calls, and a live status line
- Threads are real Claude Code sessions (resume them in the terminal too), grouped by project folder
- Model picker, permission modes (Shift+Tab), `/` command suggestions, prompt history
- In-game approvals (Y/A/N) and answers to Claude's questions (1–9)
- HUD pill, toasts and note-block pings when Claude finishes or needs you
- Minecraft tools for Claude: `status`, `run_command`, `read_blocks`, `nearby_entities`, `say`
- Local MCP server so Claude Desktop or a terminal Claude Code session can drive the game too

## Requirements

- [Claude Code](https://claude.com/code) installed and logged in (`claude` works in a terminal)
- Fabric (with Fabric API), NeoForge, Forge, or Legacy Fabric (with Legacy Fabric API)

## Usage

| Key | Action |
| --- | --- |
| `` ` `` | Open or close the panel (rebind under Controls → ClaudeCraft) |
| Enter | Send and return to the game |
| Shift+Enter | Send and keep the panel open |
| Alt+Enter | New line |
| Shift+Tab | Cycle permission mode |
| Y / A / N | Allow / always allow / deny a pending action |
| 1–9 | Pick an answer to Claude's question |
| Ctrl+C (macOS) or Stop | Stop Claude |
| Esc | Back to the game |

Settings live in `config/claudecraft.json` (`claudePath`, `mcpServer`, `mcpPort`, `sounds`, `toasts`).

## Connect Claude Desktop or Claude Code

While the game runs, ClaudeCraft serves its tools at `http://127.0.0.1:25595/mcp`.

```bash
claude mcp add --transport http --scope user minecraft http://127.0.0.1:25595/mcp
```

Claude Desktop only speaks stdio, so point it at the bridge in the mod jar (`claude_desktop_config.json`):

```json
{
  "mcpServers": {
    "minecraft": {
      "command": "java",
      "args": ["-cp", "/path/to/claudecraft-fabric-0.1.0+26.2.jar", "dev.claudecraft.agent.mcp.McpStdioBridge"]
    }
  }
}
```

## Supported versions

| Loader | Minecraft |
| --- | --- |
| Fabric | 1.16.5, 1.17.1, 1.18.2, 1.19.2, 1.19.4, 1.20.1, 1.20.4, 1.20.6, 1.21.1, 1.21.4, 1.21.5, 1.21.8, 1.21.10, 1.21.11, 26.1.2, 26.2, 26.3 |
| NeoForge | 1.20.6, 1.21.1, 1.21.4, 1.21.5, 1.21.8, 1.21.10, 1.21.11, 26.1.2, 26.2, 26.3 |
| Forge | 1.8.9, 1.12.2, 1.18.2, 1.19.2, 1.20.1 |
| Legacy Fabric | 1.8.9, 1.12.2 |

Each jar also covers neighbouring patch versions (for example the 1.21.8 jar runs on 1.21.6–1.21.8).

## Project layout

```
agent/api          Connector-agnostic agent API: sessions, events, tools, JSON (Java 8, no dependencies)
agent/mcp          MCP server (in-process and streamable HTTP) and a stdio bridge
agent/claude-code  Claude Code connector (claude CLI + SDK control protocol)
core               Minecraft-agnostic app: chats, UI toolkit, panel, HUD, Minecraft tools
platform           Stonecutter tree for 1.16.5–26.3 (Mojang names): Fabric, NeoForge, Forge
legacy             Stonecutter tree for 1.8.9 and 1.12.2 (MCP names): Forge, Legacy Fabric
build-logic        Shared Gradle plugins and the Prism deploy task
```

New AI backends implement `dev.claudecraft.agent.Connector`; the UI and tools stay unchanged.

## Building

```bash
./gradlew build                              # all modern jars
./gradlew -p legacy build                    # 1.8.9 and 1.12.2 jars
./gradlew :platform:26.2-fabric:runClient    # dev client
./gradlew :platform:26.2-fabric:prism        # create/update a Prism Launcher instance with the mod
./gradlew prism                              # a representative set of modern instances
./gradlew -p legacy prism                    # 1.8.9 and 1.12.2 instances
```

Requires JDK 25+ to run Gradle; toolchains for older Java versions are provisioned automatically.
