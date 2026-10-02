# Native album QA on 131

These are QA-only sources. The runner launches one visible private MC instance, uses its actual F2 KeyboardHandler and native camera plus actual CEF DOM, and requests normal world logout/Minecraft shutdown. No OS kill or production writes. Runtime pictures remain outside tracked files.

Prerequisites: the installed 131 client at the default `ALBUM_QA_GAME`, Java 21 at `ALBUM_QA_JDK`, existing `build/music-test-dependencies.jar` compile-only classes, and Python `psutil`. Existing MCEF native binaries, assets and libraries are reused; no downloads or security credentials. The launcher requires `JBC_FCRL`, interactive Session 2. Give this newly owned GLFW window focus without competing owner input.

```powershell
# Optional overrides. Use a private work directory under the authorized workspace.
$env:ALBUM_QA_WORKDIR = 'C:\Users\ranzh\Documents\Codex\2026-10-02\task-10'
$env:ALBUM_QA_JDK = 'C:\Users\ranzh\Documents\Codex\terminal-integration-task6-20261001\tools\jdk\jdk-21.0.12.1+1'
$env:ALBUM_QA_GAME = 'C:\Users\ranzh\workspace\dev\muxigame\_client_test\game'
python tests/album-runtime/build_candidate.py
python tests/album-runtime/run_native_qa.py --cycles 8
# Run another new instance, pointing only to the earlier own lab's screenshots:
python tests/album-runtime/run_native_qa.py --cycles 24 --other-instance '<same workdir>/album-runtime-<previous-id>/screenshots'
```

Defaults write to `build/album-native-qa`. `--prepare-only` prepares an isolated lab without launching. `--lab` starts a prepared lab once; it must remain inside the configured work directory. Other-instance comparisons accept only another owned lab under that same directory. Cycles are bounded to 1–32. QA sets `GlobalConfig.OSHI_UTIL_WMI_TIMEOUT=2000` before hardware reporting; production is unaffected.

Outputs per private lab: `inputs.json`, `run-summary.json`, `album-runtime-result.json`, `process-memory.json`, `exit.json`, `boot.log`, and actual visual PNG evidence. Inspect `success`, `normalLogout`, `cleanExit`, `exitCode=0`, `forceTerminationPerformed=false`; a missing summary, monitor error or stage timeout is not a pass. Check finalizer errors separately and inspect the PNGs. Native image identity probe, managed/direct counters, real JS heap (may be browser-rounded) and child process private allocation are separate measurements; none establishes indefinite memory stability alone.

Synthetic complements:
```powershell
python tests/album/run-library-tests.py
python tests/album/run-bridge-fixtures.py
node tests/album/controller.test.cjs
```

See [the 131 evidence note](../../docs/album/native-screenshots-131-20261002.md) for actual scope and limitations. Never substitute these fixture results for real F2/CEF runtime acceptance.


Full installed pack controls (131 only):
```powershell
python tests/album-runtime/run_native_qa.py --cycles 24 --phased --full-template 'C:\Users\ranzh\Documents\Codex\camera-native-task22-20261002\run-20261002-055245-420455d3' --other-instance '<own workdir>/album-runtime-<previous-id>/screenshots'
```

This approved existing QA template preserves its 400+ jars, configs, shader packs, and loader dependency overrides. It skips all existing worlds, screenshots, browser caches, the YSM auth/custom/export tree, and old terminal/QA jars. It selects only the installed public YSM builtin `default` model and texture through the real PLAYER attachment API. The QA observes the actual player renderer's completed model draw in the native selfie; the generic entity renderer is a different path and cannot establish this acceptance. Never change renderer return values or manufacture pass counters.

The bounded protocol uses exactly three newly generated photographs: eight warmup open/close rounds, a 60-second fixed-photo loaded hold, a 60-second closed hold, eight measured rounds, a 60-second closed hold, eight measured rounds, a 60-second content-browser destruction hold, and a 60-second all-terminal-view destruction hold. No forced GC occurs in full-pack mode. Global NativeImage, CEF client/texture, query settlement, natural GC, managed/direct memory, and child-process private allocations are observed separately. A shared MCEF engine default client may remain: explicit destruction must return terminal-owned clients and textures to the measured before-terminal baseline, not assume global clients are zero. Global full-pack images also cannot be expected to be zero; screenshot image allocations are tracked separately.

The full runner uses existing libraries/assets and blocks HTTP/HTTPS via a local closed proxy port. Its maximum elapsed deadline is 19 minutes, with only a normal logout/stop request near the boundary; it never force-terminates a stalled process. Inspect each phase and release boundary before concluding whether growth plateaus, persists, or remains attributable only to shared engine/allocator caching. One finite run cannot establish indefinite leak freedom or compatibility with other shaders, custom/private YSM models, or multiplayer model selection.

Known original MCEF2.1.6 shutdown risk: read-only bytecode of installed SHA0c7696216fa5cfee659d687475873c847a9a17cc8ce3a56119a69c946eeb8772 confirms CefWindowsShutdownMixin injects Minecraft.close TAIL and performs taskkill /F /IM jcef_helper.exe without PID/instance scope. Another MC normal exit can invalidate a sampling run. Coordinate normal exits across owners; never treat a renderer disappearance or helper crash drop as released memory. The normal MCEF JVM shutdown hook separately disposes its own client/app; the terminal must not globally dispose the app shared with other mods. A separately modified dependency candidate needs its own SHA and normal-exit/no-residue verification. No modified dependency is included in these original-pack results. See ../../docs/album/fullpack-131-20261002.md.

python tests/album-runtime/analyze_resource_phases.py '<own-lab>' writes resource-analysis.json. It marks renderer-missing phases separately, excludes console helpers from CEF child totals, and does not count an in-progress run as successful.


Project shutdown guard follow-up: use `--require-shutdown-guard --export-mixins` for actual transformed-handler verification. The new peer coordinator verifies a fresh native image query in B after guarded A exits normally, then resumes the full 24-round resource protocol. `prepare_shutdown_pair.py` prepares only; `run_shutdown_pair.py --manifest <pair-inputs.json>` starts the two owned labs in order. Wait for the coordinated MC/CEF start/stop window before executing. The runner records owned helper PID/creation-time identities and checks bounded no-residue exit without killing processes. See [the compatibility guard and reproduction instructions](../mcef-shutdown/README.md). Original failed full-pack results remain unchanged.
