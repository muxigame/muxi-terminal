# Public terminal integration QA source

Unique source from the 2026-10-02/03 public terminal owner's private QA workspace is preserved here before disposable runtime cleanup. These are test sources only; the production build does not include them.

`java/` preserves the later native map/terminal/settings and full-pack keybinding QA variant, guard-denial diagnostics, and target-preparation observations. It intentionally coexists with the earlier `tests/task6-native-map-qa` variant; do not compile both copies of `NativeRuntimeQA` together. The unchanged `NetworkProbeMixin`, `RendererProbeMixin`, preflight and optional MCEF/WMI helper sources already live in that directory. `HiddenWindowMixin` already lives in `tests/client-smoke`.

The native suite requires its historical Core/Terminal/Waystones APIs and an explicitly owned offline lab whose absolute root equals `qa.ownerRoot`. It creates test world fixtures and writes results. This source migration does not build or launch it, and does not certify current full-pack compatibility. Use the unified `better-mc-remake` local MC debug workflow for any future approved run. Legacy instance-copy, desktop-launch, attach/retry and stop scripts are excluded; do not reuse them to operate a current instance.

`run_map_lifecycle.py` preserves the isolated map-return fixtures against the actual `TerminalNativeMapBridge`, with fake MC/CEF/launcher classes. Its CLI accepts an installed JDK 21, an optional exact source file, and an optional output directory; defaults use this canonical repository. It starts only javac/java, with no game, server, network or OS input:

```powershell
python -B tests/public-integration-runtime/run_map_lifecycle.py --jdk <installed-jdk-21>
```

Historical fixture results and native input acceptance remain scoped by `muxi-game-core/tests/evidence/global-input-native-20261003.json` and its input-link QA documentation. Migration checks cover byte equality for all three Java files, unchanged embedded map fixture bodies and Python syntax/help only. Existing gameplay tests were not repeated, and no current runtime acceptance is claimed.

The source provenance lists hashes and existing canonical equivalents. Superseded Friends policy/transport tests remain represented by Git history and the current canonical tests; old candidate source does not replace them. Worlds, caches, mods, credentials, restricted assets, compiled classes and JARs are excluded.
