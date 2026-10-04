# Isolated music acceptance

These helpers run only in a private instance launched by `better-mc-remake/scripts/local_mc_debug.py`. They do not belong in a production mod folder. The unified runner owns process launch, the QA-only WMI timeout (2000 before SystemReport.putHardware), and normal stop. Do not register a persistent task, take desktop focus, or change identity/permissions.

Build the production candidate with `tests/build_music_candidate.py --workspace <workspace> --java-home <jdk21> --output <private-build>`; the output argument is required so shared build/libs and build/release.json remain untouched. Run `tests/run_music_native_tests.py` with explicit candidate/dependencies/output arguments for decoder/envelope and installed API signature checks.

Compile this separate helper:

```powershell
python tests/music-next-runtime/build_fixture.py --java-home <jdk21> --candidate <private-candidate.jar> --dependencies <private-build/music-test-dependencies.jar> --mcef <installed-mcef.jar> --output <private-build/qa>
```

Use the unified runner's `run` command with a unique instance root, one client, a unique port, hold mode and the private candidate/helper as `--mod`. Add native net_music_list 4.3, netmusic 1.5.2 and cloth-config as `--mod`; MCEF, BiomeMusic 4.1, Audio Improvements 2.0.2, Cupboard and YACL as `--client-mod`. Add the installed mcef-libraries directory through `--data-dir`. Coordinate with the other owner before any focus/input use. This fixture uses hidden/unfocused callbacks, not physical input.

After the runner reports ready:

```powershell
python tests/music-next-runtime/run_checks.py --instance-root <private-instance> --debug-cli <better-mc-remake/scripts/local_mc_debug.py> --assets <client-game/assets> --output <private-results> --player MusicNext131
# A shorter regression for native-player -> local startup, ownership and disconnect:
python tests/music-next-runtime/run_targeted.py --instance-root <private-instance> --debug-cli <better-mc-remake/scripts/local_mc_debug.py> --assets <client-game/assets> --output <private-results> --player MusicNext131
python <better-mc-remake/scripts/local_mc_debug.py> stop --instance-root <private-instance> --wait-seconds 2
```

Use fresh private data for each suite. The fixture creates two real PCM WAV files, imports them through LocalMusicLibrary/LocalDecoder, and fills a native portable item's existing playlist using those private file URIs. Those URIs are test data only; no product upload, network catalog, or service was added. The scripts inspect actual OpenAL gain/state, native queues, resource reloads and player inventory. Requests and waits are bounded. A product assertion failure leaves the instance available for inspection; its owner must still request normal stop and collect `run-result.json`.

Acceptance boundaries: decoder/import storage is real, but the Windows OS file-picker and physical keyboard/mouse route are not accepted by this fixture. Provider grouping is exercised with Minecraft's 60 tracks plus the native portable fixture; wider production MOD resource combinations need separate acceptance. No mixer/default-background setting was added while that policy remains pending.
