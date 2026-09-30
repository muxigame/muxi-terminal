# Muxi Player Terminal

The in-game application platform for Muxi Game (Minecraft 1.21.1 / NeoForge).

The terminal is a handheld item backed by MCEF. It opens a local, offline
`mod://muxi_terminal/terminal/index.html` shell that behaves like a tablet/phone
home screen. Native Minecraft actions can be exposed to web apps through a
small CEF message bridge.

## v0.2 scope

- `Player Terminal` item, automatically granted once when a player first joins.
- Right-click opens the terminal home screen.
- One persistent MCEF browser session per client, shared by the full-screen UI
  and the first-person handheld display.
- Map-like centered first-person handheld rendering with two visible arms.
- Local terminal desktop with three apps: **Game Guide**, **Tasks** and
  **Muxi Passport**.
- Game Guide entries for Create, Ice and Fire, Waystones, Farmer's Delight,
  The Twilight Forest, Alex's Mobs, Starcatcher, Modular Golems, Touhou Little
  Maid and L_Ender's Cataclysm.
- Guide icons are read at runtime from the installed mods through Minecraft's
  resource manager; third-party textures are not copied into this repository.
- Native manual integration: Create's Ponder index, Modular Golems and Touhou
  Little Maid Patchouli books, Starcatcher's Fishing Guide, Alex's Mobs Animal
  Dictionary, and the Ice and Fire Bestiary. The Bestiary screen reuses the
  player's existing Bestiary stack so unlocked pages are retained even though
  the player is holding the terminal. Mods without a dedicated in-game manual
  show an explanatory note.
- Tasks is a web presentation of the existing Game Core task system. Game Core
  remains the authority for snapshots, claim/reroll actions and tracked-task
  state, so the compact HUD tracker remains unchanged.
- Muxi Passport navigates the same embedded browser to
  `https://account.muxigame.com/account`. The native terminal frame always
  keeps a Home control available so users can return from external Muxi pages.
- Capability-style CEF bridge for local resources, task actions, manuals,
  challenge entry and allowlisted Muxi web pages. Native bridge calls are
  accepted only from the local `mod://muxi_terminal/` origin; external account
  or future web apps never inherit Java/game capabilities.

## Build

The repository uses the same offline JDK 21 build style as `muxi-game-core`.
It compiles against the already-installed Minecraft/NeoForge client libraries
and the pack's MCEF jar, but does not bundle that dependency. Patchouli is
detected at runtime and remains optional.

```powershell
python build.py --server ..\bmc5server
```

The artifact is written to `build/libs/`.

## Runtime dependency

- MCEF 2.1.6+ for Minecraft 1.21.1 (client side)

WebDisplays can remain installed during migration, but Muxi Player Terminal
does not require it.

## Native smoke test

`python tests/run_client_smoke.py` launches an invisible isolated client,
waits for MCEF, opens the local terminal, injects synthetic Game Core task data,
and captures the Home, Guide and Tasks pages. It verifies the Chromium texture,
local `mod://` route, task bridge and shared tracked-task state without joining
a server or using a real account.

