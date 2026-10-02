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
