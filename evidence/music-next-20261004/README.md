# Music next — 131, 2026-10-04

Implemented in the current dev checkout, music-owned files only. No branch/worktree, game/navigation/bridge edits, deployment, shared publisher output writes or production changes.

Actual selectable resource files are cached and published in visible batches of 12. Provider groups use existing localization or official MOD metadata, with namespace fallback. Refresh/reload generations prevent stale additions. Single-track and one-pass group/local/native queues use actual native state; multiple carried terminals share one player session. Closing APP/terminal or changing the held item retains playback while a terminal remains anywhere in the standard personal inventory. Removing all terminals, manual stop and unload/disconnect release the session; reconnect does not resume it.

BiomeMusic gain adjustment previously multiplied an already-adjusted category gain again, while Audio Improvements periodically recalculated the native gain. The Biome fade sentinel also collided with actual low gain values. A per-stream normalized 40-tick envelope now preserves one category application and releases completed fades. Actual native file volume (.4/.8/1 in this test) is retained. APP-owned music remains exclusive under the existing policy. The final bounded startup retry fixes temporary native RECORDS concurrency cache rejection: it retries once per second only before getStream was requested, within the existing 30-second timeout, so pending streams are never duplicated.

Evidence:

- `ui.json`: 33 actual Chromium DOM/interaction checks passed on final JS; native API fixtures.
- `layout.json`: 41 rendering/scroll/focus checks passed, including 32px rows and list-only scrolling.
- `decoder-envelope.json`: 46 isolated JVM decoder, storage, stream release and normalized envelope checks passed.
- `native-bindings.json`: 14 actual installed API signature checks passed; no invented native pause API.
- `native-core-stage.json`: 41 individually passing real MC/OpenAL checks on the prior candidate. Its aggregate did not pass because the portable-stop/local startup blocker was discovered at the final phase. The earlier QA transport/phase errors are recorded, not counted as product acceptance.
- `final-targeted.json`: all 12 final-backend MC/OpenAL checks passed, including the regression above, native playlist EOF, one local stream, APP detach/held-item continuation, disconnect release and no reconnect resume.
- `normal-exit.json`: last unified runner instance exited normally; server=0, host=0, no forced kill or production mutation. Runner acceptance alone covers connection/hold; music acceptance comes from the music-specific traces.
- `candidate.json`: final private candidate and exact native-test jar hashes. All compiled class bytes match the native-test jar; the sole post-native difference is the corrected import tooltip describing inventory lifetime. Final UI33 passed after that text edit.
- `shared-publisher-outputs.json`: frozen shared jar/release metadata SHA256 values remain unchanged.

Remaining acceptance: OS file-picker selection/cancellation using physical input; broad production pack MOD resource grouping and listening across actual game environment transitions. Callback tests are not physical/visual desktop acceptance or listening acceptance. The optional background mixing/default policy remains pending upstream; no such setting was introduced. No publication was performed.
