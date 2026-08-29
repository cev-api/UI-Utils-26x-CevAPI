# UI-Utils 26x CevAPI

![26.1](https://i.imgur.com/RoXogYx.png)
![Plugin](https://i.imgur.com/vOyTsYx.png)
![Macro](https://i.imgur.com/9H2JbKC.png)

## Overview

- This is a rewrite of the original **UI-Utils 2.4.0**, migrated to Mojang mappings (mojmap) which was ported out of [Wurst-7-CevAPI](https://github.com/cev-api/Wurst7-CevAPI) and then bumped to **Minecraft 26.x** and put into a standalone mod. 
- On top of classic UI-Utils quality-of-life features, this build adds advanced packet tooling, command scanning, macros, auto-duping and expanded UI controls. 
- This application is designed and presented for people already familiar with UI-Utils. **There will be no explanations or guide on how to use it in-game**, you can instead find that [here](https://github.com/ui-utils/docs/blob/main/OverlayOverview.md).
- If you are looking for a **1.21.11** version you can find that [here](https://github.com/cev-api/UI-Utils-CevAPI) but it will not have the majority of features in this project.

## Build

Build with Gradle (Java 25+):

```powershell
./gradlew clean build
```

Artifacts land in `build/libs/`.
The default artifact version label is `26.1-26.2` to reflect the shared jar support range.

Default build target is Minecraft `26.2`, and the runtime compatibility layer keeps the jar working across `26.1.x` and `26.2.x`.
If you want to compile specifically against `26.1.2`, you can still override the versions at build time:

```powershell
./gradlew build -Pminecraft_version=26.1.2 -Pfabric_version=0.145.4+26.1.2 -Ploader_version=0.18.4
```

## Getting Started

1. Install Fabric Loader and Fabric API for your Minecraft `26.1.x` or `26.2.x` installation.
2. Drop the built jar from `build/libs/` into your `mods/` folder.
3. Launch Minecraft. Open any container — the UI‑Utils toolbox appears on the left.

## Highlights

### Simple GUI
  - Close GUI without sending a packet
  - De‑sync tricks (close packet only)
  - Send/Delay queue for UI packets, with flush on demand
  - Copy GUI title JSON
### Command, Plugin, and Server Scanner
  - Passively caches joined-server packet evidence (configuration, known packs, payload channels, registries, dimensions, advancements, tab/scoreboard text, and chat-completion metadata) per connected server. Server-list/status traffic is excluded.
  - Command scans offer two modes:
    - **Packet** probes command suggestions and can discover roots beyond the immediately visible command list.
    - **Client** recursively enumerates the synced Brigadier command tree, including literal subcommands, argument paths, and redirect/alias branches, without sending probe packets.
  - Packet-discovered roots that are absent from the current player's synced command tree are highlighted red: they are permission-hidden for that player and should not be assumed executable.
  - Discovered command rows are clickable. Selecting one fills the packet-command field; execution still requires the explicit **Send packet cmds** button.
  - After a manual packet-command send, a temporary output panel records the sent command and system command responses. It does not capture player chat.
  - Supports packet execution of selected/manual commands, optional execution of discovered commands, and `trigger` value discovery (logged as `trigger (value)`).
  - Plugin results merge packet evidence, known packs, custom payloads, command-tree roots, plugin-list evidence, and root hints. Expand plugin rows for associated commands and cached server evidence.
  - A Verbose Server Scan combines the current server fingerprint with plugin and command scans, starting missing scans for the current server when needed. Its report is scrollable and copyable.
  - Completed plugin, command, and verbose scans are stored as compact per-server JSON history under `<gameDir>/config/ui-utils-scan-history/`. Each scan overwrites its latest snapshot and records only added, removed, changed, or unchanged findings to avoid unbounded duplicate logs.
  - Supports parsing plugins for vulnerabilities pursuant to the latest cache of [DupeDB](https://dupedb.net/).
### AntiCheat Detector
  - Reads packets on server join to determine the current AntiCheat, if any.
  - Disable/Enable in settings.
### Packet Fabrication Helpers (ClickSlot, ButtonClick)
  - In-game popup that is repositionable.
### Added Extra Tools From [FrannnnDev's fork](https://github.com/FrannnnDev/ui-utils-advanced/) of UI-Utils
  - Leave & send, Disconnect & send, Save/Load GUI, Clear Queue, Queue, Resync Inv, Disconnect, Spam +/-, Send One, Pop Last
  - Queue helper and counter
  - ```.uiutils``` commands
  - Named GUI slot maps
  - Plugin scanner
### Advanced Packet Tool (APT)
  - Lets you manage packet behavior per packet type (S2C & C2S)
  - Supports 3 independent modes: Log, Deny and Delay
  - Modes can overlap (Log & Deny Packet A but also Log & Delay Packet B)
  - Toggles for enabling modes as well as cycling through packet edit list
  - Delay is tick based
  - Searchable dual-list UI with select all/none controls
  - Optional ```Show Unknown Packets``` feature to allow ```class_####``` packets
  - Inspired by [HelixCraft's Packet Logger](https://github.com/HelixCraft/Fabric-Packet-Logger)
  - Runs in an external desktop window for now (Swing)
  - Open from the UI‑Utils overlay or by keybind (configurable in Settings)
### Macro System (Beta)
  - A macro is a named automation script made of ordered steps (actions and wait conditions).
  - Macros can be saved, edited, duplicated, reordered, deleted, exported and imported.
  - The macro editor supports:
    - Add Action and Add Conditional pickers
    - Per-step controls: move up/down, duplicate, edit, delete
    - Scrollable step list with draggable scrollbar
    - Undo/redo for editor changes
    - Run Once (temporary) and Run (saved macro)
    - Optional keybind assignment per macro
  - The step options screen supports:
    - Context-aware fields per action type
    - Compact layout for simple actions
    - Multi-value list fields (comma-separated input + Add/Clear)
    - No empty editor for actions with no configurable options (for example `STOP_MACRO`)
  - Runtime behavior:
    - Macros execute steps in order
    - `WAIT_*` conditions pause progression until matched (or timeout depending on the condition)
    - `STOP_MACRO` terminates execution immediately
  - Import/export:
    - Import reads `.nbt` macro files
    - Export accepts a folder path and writes `<macro-name>.nbt`
    - Native pickers are available from `...` buttons
    - Default directories are auto-created:
      - `<gameDir>/config/ui-utils/macro_import`
      - `<gameDir>/config/ui-utils/macro_export`
### Autoduper (Beta)
  - Uses a strategy matrix built from 218+ different methodologies inspired by [DupeDB](https://dupedb.net)
  - Accepts a plugin GUI open command plus an optional prepare command, so it can work with plugin inventory GUIs such as `/pv 1`, `/ec`, `/ah`, or `/shop`
  - Player-inventory target slots are treated as seed items for plugin GUI lifecycles, not as a vanilla inventory dupe scan
  - Per-category toggles let you narrow the run instead of running everything:
    - Movement methods
    - Close methods
    - Reopen methods
    - Packet-delay variants
    - Validation
  - Start from the main UI-Utils overlay or from the Autoduper options screen while a container is open
  - Includes exact attempt replay, a visible abort overlay and a hold-to-abort key
  - Success messages report the exact attempt number for easy replay
  - Verbose mode toggle to find niche dupes even when similar attempts failed
### Expanded Settings Screen
  - Tri‑state Slot Overlay: OFF / HOVER / ALWAYS
  - Unified HSV color picker with target selector:
    - Button background color
    - Button text color
    - Overlay number color
    - Packet HUD text color
  - Overlay alpha and XY offsets
  - Resource‑pack bypass/deny toggles
  - Close Delay and Command Delay sliders (tick based)
  - Dedicated Keybinds screen for rebinding UI-Utils actions
  - Legacy direct key fields still present: restore GUI and delay toggle
  - Disconnect method selector (used by UI‑Utils “Disconnect” buttons)
    - Includes QUIT, packet-based kick styles and lag styles
    - Includes TIMEOUT mode (KeepAlive wait + block + delayed action)
### Packet HUD 
  - In-game HUD rendering of packet flow.
  - Format:
    - `888 IN / 999 OUT`
    - `    20 QUEUED` (only shown when queue > 0)
  - HUD color is configurable in Settings
  - Positionable to all four corners
### Themed UI‑Utils buttons
  - Colored button renderer is used across UI‑Utils screens and injected buttons
  - Removes mixed vanilla/colored button look

#### Log Example
```
[12:52:54]: Fabricate ClickSlot: syncId=6, revision=1, slot=2, button=0, action=PICKUP, times=1, diffSlots=1, carriedBefore=<empty>, carriedAfter=class_10939[item=Reference{ResourceKey[minecraft:item / minecraft:oak_slab]=minecraft:oak_slab}, count=11, components=class_10936[addedComponents={}, removedComponents=[]]]
[12:52:54]: Fabricate ClickSlot: menu.containerId=6, syncIdMatch=true, diffDetail=[2: minecraft:oak_slabx11 -> empty] 
[12:52:54]: UiUtilsConnectionMixin: attempting to send UI packet class_2813 (sendUiPackets=true, delayUiPackets=false)
```

## UI‑Utils Commands

Supported roots:
- `.uiutils`
- `uiutils`

Main commands:
- `help`
- `enable` / `disable`
- `close`
- `desync`
- `apt` (aliases: `advancedpacketscanner`, `advancedpackettool`)
- `chat <message>`
- `screen <save|load|list|info> [slot]`
- `plugins`
- `commands`
- `queue <list|clear|sendone|poplast|spam [times]>`
- `packethud <on|off|toggle>`
- `delay <on|off|toggle>`
- `closedelay <ticks>`
- `commanddelay <ticks>` (alias: `cmddelay`)
- `sendpackets <on|off|toggle>`
- `disconnectmethod <list|current|METHOD>`
- `timeout <seconds>`
- `lagmethod <list|current|METHOD>`
- `settings`
- `autoduper <open|start|stop|status|slot|command|attempt|hybrid [openCommand]>`

## Settings List

- Slot overlay mode: `OFF` / `HOVER` / `ALWAYS`
- Packet HUD toggle
- Log to chat toggle
- Bypass resource-pack toggle
- Force-deny resource-pack toggle
- AntiCheat detector toggle
- Disconnect method selector
- Timeout seconds selector (for `TIMEOUT` disconnect mode)
- Timeout lag method selector (for `TIMEOUT` disconnect mode)
- Color target selector
  - Button background color
  - Button text color
  - Overlay number color
  - Packet HUD text color
- HSV color picker for selected target
- Selected color hex field (`#RRGGBB`)
- Slot overlay alpha
- Slot overlay X offset
- Slot overlay Y offset
- Fabricate overlay background alpha
- Restore GUI key field
- Packet tool key field
- Delay toggle key field
- Keybinds page: configurable bindings for restore, packet tool, scanners, autoduper start, packet queue controls, disconnect flows and chat field send
- UI close delay ticks
- UI command/chat delay ticks
- Autoduper: plugin GUI open command, prepare command, target slot, max attempts, step delay, verbose mode, drop validation, single-attempt replay, abort key, hold-to-abort toggle and category filters
- Autoduper category filters include hybrid command+interact reopen and finish actions (leave+send / disconnect+send)

## Macro Import/Export

Open `Macros` from the overlay to use the Macro Library.

Workflow:
1. Create or edit a macro, then save it.
2. To export, select the macro and choose an export folder (`...` opens folder picker).
3. To import, choose a `.nbt` file (`...` opens file picker) and import it.

Path behavior:
- Import field points to: `<gameDir>/config/ui-utils/macro_import`
- Export field points to: `<gameDir>/config/ui-utils/macro_export`
- Both folders are created automatically if they do not exist.

## Notes on the Mojmap Migration

- Entire codebase uses Mojang mappings for clarity and forward‑compat.
- The current jar is built against 26.2 and includes runtime shims for 26.1.x screen/chat API differences.
- Packet types are discovered at runtime with a reflective catalog for resilience across dot‑releases.
- APT’s UI is intentionally external for now to avoid churn in the in‑game widget APIs and keep the dual‑list UX snappy. May become internalised in the future.

## Credits

- Original concept: [UI‑Utils](https://github.com/cev-api/UI-Utils-CevAPI) ([MrBreakNFix](https://github.com/MrBreakNFix) and [contributors](https://github.com/cev-api/UI-Utils-CevAPI/graphs/contributors))
- Modernization + new features: CevAPI
- Advanced Packet Tool inspired by [HelixCraft's Packet Logger](https://github.com/HelixCraft/Fabric-Packet-Logger)
- Extra UI-Utils options inspired by [FrannnnDev's fork](https://github.com/FrannnnDev/ui-utils-advanced/)
- Published **with approval** from [MrBreakNFix](https://github.com/MrBreakNFix)
- [DupeDB](https://dupedb.net) for their dupe list. Will eventually add API access for live updates.

## License

This project is licensed under the GNU General Public License v3.0 or later (GPL-3.0-or-later). See [LICENSE](./LICENSE).

## Disclaimer

UI‑Utils is a debugging and testing toolkit. Be nice, follow server rules and local laws. You are responsible for how you use these tools.
