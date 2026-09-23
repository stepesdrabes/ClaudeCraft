# ClaudeCraft

Claude inside Minecraft. Chat with Claude Code from an in-game panel while you play, and let it see and build in your world.

Inspired by [t3craft](https://github.com/maxwellyoung/t3craft), but talks to Claude Code directly.

## Features

- Minecraft-styled panel that fills the screen: threads on the left, the conversation in the middle, and a side panel with context usage, todos, agents and background tasks, and MCP servers (folded into header chips on small screens)
- Threads are real Claude Code sessions (resume them in the terminal too), grouped by project folder, including worktree sessions
- Streaming Markdown replies with real emoji (Twemoji), tool calls, subagent progress, plan cards, todo checklists and image thumbnails
- Image input: paste an image from the clipboard or attach a screenshot of your view
- Model picker with an effort selector, unavailable models with an update hint, the Claude Code version, a binary chooser and a one-click update
- Permission modes (Shift+Tab), plan approval (approve, auto-accept edits or keep planning), in-game approvals and answers to Claude's questions
- Background sessions that keep running after you quit (`claude --bg`), plus stopping and backgrounding running tasks
- Thread actions: rename, fork, archive, delete (to the Trash), continue in the background or bring back into the game
- Context and plan-usage meters, MCP server status with on/off and reconnect
- HUD pill, toasts and note-block pings when Claude finishes or needs you
- Minecraft tools for Claude: `status`, `run_command`, `read_blocks`, `nearby_entities`, `screenshot`, `say`
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
| ⌘Enter (macOS) or Ctrl+Enter | Send as a background session |
| Alt+Enter | New line |
| Shift+Tab | Cycle permission mode |
| ⌘V or Ctrl+V | Paste text, or an image when the clipboard holds one |
| Y / A / N | Allow / always allow / deny a pending action (for plans: approve / auto-accept edits / keep planning) |
| 1–9 | Pick an answer to Claude's question |
| Ctrl+B | Move running Bash commands and subagents to the background |
| Ctrl+C (macOS) or Stop | Stop Claude |
| Right-click a thread | Rename, fork, archive, delete, background |
| Esc | Back to the game |

Type anything else to decline a pending action with feedback instead.

| Command | Action |
| --- | --- |
| `/clear` | Start a new chat |
| `/rename <title>` | Rename this chat |
| `/fork` | Branch this chat into a new one |
| `/compact [instructions]` | Summarize the conversation to free context |
| `/background <prompt>` | Run the prompt as a background session |
| `/effort <level>` | `auto`, `low`, `medium`, `high`, `xhigh` or `max` |
| `/screenshot` | Attach what you see in the game |
| `/worktree` | Start a new chat in its own git worktree |
| `/archive` | Archive this chat |

Other `/` commands go to Claude Code.

Settings live in `config/claudecraft.json` (`claudePath`, `model`, `effort`, `permissionMode`, `mcpServer`, `mcpPort`, `sounds`, `toasts`, `archived`). The model menu can also pick the `claude` binary.

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
      "args": ["-cp", "/path/to/claudecraft-fabric-0.2.0+26.2.jar", "dev.claudecraft.agent.mcp.McpStdioBridge"]
    }
  }
}
```

Background sessions started from the panel use this server too, so they can keep building while the game is open.

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
core               Minecraft-agnostic app: chats, UI toolkit, panel, HUD, emoji atlas, Minecraft tools
platform           Stonecutter tree for 1.16.5–26.3 (Mojang names): Fabric, NeoForge, Forge
legacy             Stonecutter tree for 1.8.9 and 1.12.2 (MCP names): Forge, Legacy Fabric
build-logic        Shared Gradle plugins, the Prism deploy task and the emoji atlas generator
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
./gradlew emojiAtlas                         # regenerate the committed Twemoji atlas
./gradlew test -Dclaudecraft.live=true       # also run the live tests against your Claude Code
```

Requires JDK 25+ to run Gradle; toolchains for older Java versions are provisioned automatically.

## Credits

Emoji graphics are [Twemoji](https://github.com/jdecked/twemoji) by Twitter, Inc. and other contributors, licensed under [CC-BY 4.0](https://creativecommons.org/licenses/by/4.0/). The atlas in `core/src/main/resources/assets/claudecraft/emoji` is downscaled from Twemoji 17.0.3; its license ships next to it.
