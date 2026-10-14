# Preserved native camera QA source

These seven Java sources are the unique camera QA code used for the 2026-10-02
131 run. They were moved into the existing terminal repository before cleanup of
the old session's disposable instances. `source-provenance.json` records every
original and destination source hash. Six files are byte-identical; one had only
eight trailing spaces removed from an empty line. Java tokens are unchanged.

The durable historical result is [camera-131-20261002.json](../camera-131-20261002.json):
134 checks, 16 camera rounds, cancellation resource accounting, actual Minecraft
F2 handlers and native screenshot-manager enumeration. The combined CEF album
run was incomplete. None of these results establish current-source acceptance,
physical OS keyboard/visual acceptance or SSO acceptance.

`build-qa.py` only compiles a QA mod from explicit local dependencies:

```text
python tests/camera/native-runtime/build-qa.py --product <candidate-terminal.jar> --java-home <jdk21> --server-libraries <installed-server>/libraries --client-libraries <installed-client>/libraries --mcef-jar <installed-mcef.jar> --output <new-small-qa-build-dir>
```

It starts no processes beyond the JDK compiler/version check, creates no Minecraft
instance and downloads no resources. The compile receipt explicitly records
`runtimeTested=false`. Building checks Java compatibility, not mixin injection or
DOM compatibility with a newer product. The copied QA intentionally retains the
old DOM selectors, mixin targets and screenshot-viewer integration for diagnosis.

For a future authorized runtime run, read `better-mc-remake/docs/local-mc-debug.md`
under the sole workspace root first and use that workflow with an independently
owned directory/port and an explicit resource budget. The existing terminal/SSO
runner remains separate. This QA creates a temporary creative world and may
recycle/restore QA photos inside its game directory: never install it in a real
player or production instance. `OfflineMcefMixin` assumes CEF is already installed;
it bypasses only the old downloader checksum step and proves no online setup.

`-Dqa.cameraOnly=true` selects the historical 16-round camera/F2 path;
`-Dqa.expectClosed=true` asserts cancelled native images are closed;
`-Dqa.cancelOnly=true` selects the cancellation-only diagnostic. Camera paths
require an active window, so a future visual run needs a coordinated focus owner.
Do not change product focus checks merely to make a hidden QA run pass. Verify
the latest APIs and runtime dependencies before using this historical harness.

The former scripts tied to an absolute session directory and an entire copied
pack are superseded; they are not a second supported launcher. No instances,
caches, worlds, resource packs, native binaries or raw results are committed here.
