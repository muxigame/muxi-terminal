# Muxi Player Terminal

The in-game application platform for Muxi Game (Minecraft 1.21.1 / NeoForge).

The terminal is a handheld item backed by MCEF. It opens a local, offline
`mod://muxi_terminal/terminal/index.html` shell that behaves like a tablet/phone
home screen. Native Minecraft actions can be exposed to web apps through a
small CEF message bridge.

## v0.1 scope

- `Player Terminal` item, automatically granted once when a player first joins.
- Right-click opens the terminal home screen.
- One persistent MCEF browser session per client, shared by the full-screen UI
  and the first-person handheld display.
- Map-like centered first-person handheld rendering with two visible arms.
- Local terminal desktop with the first app: **Game Guide**.
- Initial guide entries for Create, Ice and Fire, Waystones, Farmer's Delight,
  The Twilight Forest and Alex's Mobs.
- CEF bridge foundation (`cefQuery`) for native actions such as returning home,
  closing the terminal and opening Patchouli books later.

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
waits for MCEF, opens the local terminal home page, verifies a browser texture
was produced, captures `terminal-home.png`, and exits without connecting to a
server or using a real account.

