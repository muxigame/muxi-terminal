# Unified room UI

The minigames lobby keeps the room list and a single create entry. Creation,
invitation and settings each use the same modal shell and terminal pixel style.
The room view shows the server room name, mode/map/difficulty, roster, start/leave,
invite and settings. Creation remains in a waiting room until the host starts.

The name defaults to the current server player's display nickname plus 的房间.
Names use 1–24 UTF-16 code units; controls, formatting characters and § are rejected.
Names belong to the room session and end with that room, not a player/world record.

Invitation tabs are 在线玩家 then 我的好友. Trusted friend invitations use the existing
authenticated service, real online/friend intersection and final operation receipt.
Closing/reopening a modal retains its pending request. Uncertain requests stay
blocked until a matching result is corroborated. Without the optional trusted
service, the online tab uses a game-declared invitation action; friends explicitly
show unavailable. Recovery retains a compact safe exit when participation is denied.

## Server contract

The framework snapshot advertises `roomUiVersion: 1` and
`self: {uuid, name}`. Room rows add `roomName`, `capacity`, `count`, and
`roster: [{uuid, name, host, online, side?}]` from the actual room membership.

`roomCreate` carries `[name, [field values in the server create.fields order]]`.
The server validates current connection/admission, declared options/mode compatibility
and native action eligibility, invokes the existing game creation adapter, and names
the confirmed membership. It does not start the game.

`roomSettings` carries `[sessionUuid, name, []]` for rename or
`[sessionUuid, name, [difficultyFieldId, choice]]`. Only the current host in that
actual waiting room can mutate it. The server chooses only a declared difficulty
or enemy action; arbitrary action names and stale room generations are rejected.
Native adapter failure leaves the name unchanged. Existing packet lengths stay 128.

Flight presets derive seats, AI, reserves and active limits from the difficulty.
PVE settings show only name/difficulty. PVP retains compact red/blue assignment.
Horse and Zombie keep their original difficulty semantics. Outbreak keeps its
existing campaign/survival mode override for a selected map and now permits host
difficulty changes only in WAITING. Mode and map remain fixed after creation.

## Verification and candidate building

`node tests/minigames-model.test.cjs` and `node tests/friends/invites.test.cjs`
exercise naming/options and invitation receipt boundaries. `node
tests/minigames-app.integration.test.cjs` runs the real hidden Chromium suite,
including four games, duplicate submits, modal lifecycle, permission changes,
keyboard/focus, recovery/shop preservation and 480×320–1920×1080 layout.
API responses in Chromium are explicitly fixtures, not live account acceptance.

The framework `tests/run_runtime_boundaries.py` includes RoomPresentationTest.
`tests/build_room_ui_candidate.py` builds tracked HEAD with an explicit room-only
overlay into a new ignored build directory. It excludes other owner dirty changes
and suspended Endgame drafts; `CANDIDATE.json` records source and JAR SHA-256.

`tests/run_room_native.py` uses the shared private MC runner, two actual hidden
network clients and game-declared create/invite/join/settings/leave actions. It
verifies authoritative snapshots and normal exits. It claims waiting-room protocol
coverage, not MCEF visual, physical input, live SSO/friend service, combat or settlement.
The native callback sends GameNetwork.Action; acknowledged terminal dispatch/replay
is separately covered by the framework dispatcher tests and browser fixtures.

No production deployment, release version bump, parent gitlink update or Endgame
restoration is part of this candidate.
