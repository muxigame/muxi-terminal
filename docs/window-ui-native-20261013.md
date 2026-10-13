# Targeted native window/shop/settings and graceful exit acceptance

Canonical source is muxi-terminal dev, with localization from canonical muxi-localization dev. Final terminal candidate: muxi-terminal-0.2.4-window-ui.3.jar, SHA256 a32649a2b90665337de0b260fcd5b2957d6a136c95f89f26fa183d14601512fc, SHA1 9a2d926500dfb1bb452441233251331eebb47ea8, 636110 bytes. Localization remains muxi-localization-0.1.0-settings.3.jar, SHA256 941301cc49103ae10be98b46d22c9f718f7deaf25293fcad9ae3ed0100295683.

This is targeted native acceptance on real MC1.21.1/NeoForge21.1.250/MCEF2.1.6 and pinned settings mods. It does not claim a new full-pack gameplay/SSO acceptance. The game catalog is an explicit private fixture; icons.get, Java resource loading and CEF image decoding are real. User resource packs/options are confined to marked private instances.

| Evidence | Exact scope and result |
| --- | --- |
| build/local-mc-debug/window-locale-20261013/coordinator | Terminal50e1: native normal/fullscreen/restore, browser retained, real approved GIF decoded/visible; previous incomplete round remains recorded. |
| build/local-mc-debug/window-locale-missing-20261013/missing-checks.json | Same50e1: native missing-GIF PNG fallback (156px, real PNG data URL), GIF recovery hides backup, category scroll155/93 restored, refresh keeps93. |
| build/local-mc-debug/window-locale-graceful-20261013/missing-checks.json | Localization9413: Iris Settings and Download Shaders actually drawn in video/shader screens, Chinese/English/Chinese; physical OS F3+T increments completed reload5 to6 and preserves229 translations plus selected user-pack order. Screenshots taken after reload overlay disappears. |
| build/local-mc-debug/window-locale-graceful3-20261013/cleanup-verification.json | Final terminala326: live native shell/app at exit, both JVM exit0, CEF TERMINATED, terminal views0, all6 observed owned helper identities gone naturally, no force cleanup, port26133 rebind and world-lock exclusive open pass. Checked2026-10-13T13:32:07.788679Z. |
| build/native-final/graceful3-artifact-diff.json | Against50e1: no removed entries, every asset and window/layout/preview/music class unchanged; only TerminalViewClient lifecycle callbacks and compat shutdown classes differ. |

A normal stop of50e1 reproduced an owned renderer orphan and CEF remained INITIALIZED at Minecraft.close return, proving that the issue was not specific to the hold timeout. Only the precisely identified dead-parent orphan was cleaned; that historical run remains failed for normal helper exit. The first shutdown revision(.2,05e588...) crashed in N_DoMessageLoopWork after app.dispose destroyed native context and is rejected.

Final fix tracks actual native onAfterCreated/onBeforeClose callbacks, closes views, drains callbacks before app disposal with a two-second bound, and never calls the native message loop after disposal. It then uses normal per-JVM MCEF.shutdown and a bounded Java state wait. There is no OS-process enumeration, ProcessBuilder or force-kill path in this production hook. The existing exact merged-handler global-kill guard remains unchanged. Tracking starts only after native creation, so cancelled unstarted account views do not enter the set.

The Windows host had only a Chinese keyboard layout loaded. That intercepted letter keys in the first physical F3+T attempts. The verified test temporarily loaded English only for this owned MC window thread, sent actual OS key events, restored its original layout and unloaded the temporary one. No user or system configuration file was edited. The checked-in missing-suite driver includes this bounded restore path, verifies PID/creation time before every command, rejects stopping/stopped labs and uses unique DOM tokens plus append-only receipts.

build/native-final/final-handoff.json and both candidate build handoff files contain the consolidated evidence. No production files or root submodule pointers were changed. Production rollout remains with the release coordinator.
