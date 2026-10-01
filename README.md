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
- Muxi Passport navigates the same embedded browser to the game platform player
  center at `https://mc.muxigame.com/account.html`. The native terminal frame always
  keeps a Home control available so users can return from external Muxi pages.
- Capability-style CEF bridge for local resources, task actions, manuals,
  challenge entry and allowlisted Muxi web pages. Native bridge calls are
  accepted only from the local `mod://muxi_terminal/` origin; external account
  or future web apps never inherit Java/game capabilities.

## Terminal platform login (isolated, not deployed)

The local `passport.open` capability asks Game Core for a PKCE-bound, single-use
ticket. Both a launcher-issued restricted native credential and the actual
LoginGate-verified game connection are required; a UID or a game-server key alone
cannot log anyone into the platform. Neither the launcher OAuth token nor the
restricted credential enters HTML. The proof and ticket each expire in 30 seconds.

Native code submits the ticket/verifier as a POST from the same browser's exact main
frame at `https://mc.muxigame.com/api/v1/auth/terminal`, after checking the same game
connection is still active. MCEF's multiplexed load handler is used; no credential
enters webpage JavaScript or the DOM. No credential
is carried in a URL, browser storage, command-line argument, or application log.
The platform redeems the ticket server-to-server using its existing confidential
OIDC client and issues its own host-only HttpOnly `bmc_session`, then returns to
`/account.html`. The standalone muxi-auth account page is not the destination.

Missing/disabled/expired authentication, old clients, disabled LoginGate, network
errors and a rejected exchange all fall back to the existing platform login.
Deployment requires separately approved auth/platform feature flags and updated
launcher/Core/terminal builds; this working copy does not enable or deploy them.

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

