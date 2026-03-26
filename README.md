# UI-Utils 26x CevAPI

![26.1](https://i.imgur.com/oEPShFT.png)

## Overview

- This is a rewrite of the original **UI-Utils 2.4.0**, migrated to Mojang mappings (mojmap) which was ported out of [Wurst-7-CevAPI](https://github.com/cev-api/Wurst7-CevAPI) and then bumped to **Minecraft 26.1** and put into a standalone mod. 
- On top of classic UI-Utils quality-of-life features, this build adds advanced packet tooling, command scanning and expanded UI controls. 
- This application is designed and presented for people already familiar with UI-Utils. There will be no explanations or guide on how to use it in-game, you can instead find that [here](https://github.com/ui-utils/docs/blob/main/OverlayOverview.md).
- If you are looking for a **1.21.11** version you can find that [here](https://github.com/cev-api/UI-Utils-CevAPI).

## Build

Build with Gradle (Java 25+):

```powershell
./gradlew clean build
```

Artifacts land in `build/libs/`.

## Getting Started

1. Install Fabric Loader and Fabric API for your Minecraft 26.1 installation.
2. Drop the built jar from `build/libs/` into your `mods/` folder.
3. Launch Minecraft. Open any container — the UI‑Utils toolbox appears on the left.

## Highlights

- Simple, always‑available in‑GUI toolbox for handled screens
  - Close GUI without sending a packet
  - De‑sync tricks (close packet only)
  - Send/Delay queue for UI packets, with flush on demand
  - Copy GUI title JSON
- Command (and Plugin) Scanner
  - Enumerate server side commands that are typically unavailable to the player by sending specialised packets
  - Optionally elicit only 'unknown' commands. Scans via packets then compares via client commands, whichever isn't available to the user is shown.
  - Your command list probing won't appear in server side logs
  - Can also run each command, run specific commands via packets or enumerate via client side commands 
  - Great replacement for when the UI-Utils plugin scanner fails
- Packet fabrication helpers (ClickSlot, ButtonClick)
  - In-game popup that is repositionable.
- Added extra tools from [FrannnnDev's fork](https://github.com/FrannnnDev/ui-utils-advanced/) of UI-Utils
  - Leave & send, Disconnect & send, Save/Load GUI, Clear Queue, Queue, Resync Inv, Disconnect, Spam +/-, Send One, Pop Last
  - Queue helper and counter
  - ```.uiutils``` commands
  - Named GUI slot maps
  - Plugin scanner
- Advanced Packet Tool (APT)
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
- Expanded Settings screen
  - Tri‑state Slot Overlay: OFF / HOVER / ALWAYS
  - Unified HSV color picker with target selector:
    - Button background color
    - Button text color
    - Overlay number color
    - Packet HUD text color
  - Overlay alpha and XY offsets
  - Resource‑pack bypass/deny toggles
  - Keybinds: restore GUI, delay toggle and open Advanced Packet Tool
  - Disconnect method selector (used by UI‑Utils “Disconnect” buttons)
    - Includes QUIT, packet-based kick styles and lag styles
    - Includes TIMEOUT mode (KeepAlive wait + block + delayed action)
- Packet HUD 
  - In-game HUD rendering of packet flow.
  - Format:
    - `888 IN / 999 OUT`
    - `    20 QUEUED` (only shown when queue > 0)
  - HUD color is configurable in Settings
- Themed UI‑Utils buttons
  - Colored button renderer is used across UI‑Utils screens and injected buttons
  - Removes mixed vanilla/colored button look

#### Log Example
```
[12:52:54]: Fabricate ClickSlot: syncId=6, revision=1, slot=2, button=0, action=PICKUP, times=1, diffSlots=1, carriedBefore=<empty>, carriedAfter=class_10939[item=Reference{ResourceKey[minecraft:item / minecraft:oak_slab]=minecraft:oak_slab}, count=11, components=class_10936[addedComponents={}, removedComponents=[]]]
[12:52:54]: Fabricate ClickSlot: menu.containerId=6, syncIdMatch=true, diffDetail=[2: minecraft:oak_slabx11 -> empty] 
[12:52:54]: UiUtilsConnectionMixin: attempting to send UI packet class_2813 (sendUiPackets=true, delayUiPackets=false)
```

#### UI‑Utils Commands

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
- `sendpackets <on|off|toggle>`
- `disconnectmethod <list|current|METHOD>`
- `timeout <seconds>`
- `lagmethod <list|current|METHOD>`
- `settings`

## Settings List

- Slot overlay mode: `OFF` / `HOVER` / `ALWAYS`
- Packet HUD toggle
- Log to chat toggle
- Bypass resource-pack toggle
- Force-deny resource-pack toggle
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

## Notes on the Mojmap Migration

- Entire codebase uses Mojang mappings for clarity and forward‑compat.
- Mixins target 26.1 client internals; packet types are discovered at runtime with a reflective catalog for resilience across dot‑releases.
- APT’s UI is intentionally external for now to avoid churn in the in‑game widget APIs and keep the dual‑list UX snappy. May become internalised in the future.

## Credits

- Original concept: [UI‑Utils](https://github.com/cev-api/UI-Utils-CevAPI) ([MrBreakNFix](https://github.com/MrBreakNFix) and [contributors](https://github.com/cev-api/UI-Utils-CevAPI/graphs/contributors))
- Modernization + new features: CevAPI
- Advanced Packet Tool inspired by [HelixCraft's Packet Logger](https://github.com/HelixCraft/Fabric-Packet-Logger)
- Extra UI-Utils options inspired by [FrannnnDev's fork](https://github.com/FrannnnDev/ui-utils-advanced/)
- Published **with approval** from [MrBreakNFix](https://github.com/MrBreakNFix)

## License

This project is licensed under the GNU General Public License v3.0 or later (GPL-3.0-or-later). See [LICENSE](./LICENSE).

## Disclaimer

UI‑Utils is a debugging and testing toolkit. Be nice, follow server rules and local laws. You are responsible for how you use these tools.
