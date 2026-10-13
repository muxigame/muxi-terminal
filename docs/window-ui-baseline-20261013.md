# Window UI baseline and preservation record

Canonical project: muxi-mc-workspace/muxi-terminal, dev. No source checkout/worktree is used for this revision.

The live pack1.4.37 terminal is muxi-terminal-0.2.4-shop-ui-qa.3.jar, SHA256 bd4a181268a4b1df7e555ac41f22d79407639e3694337aad3b979e4587512292. It was built from9fc5ab7ea897a1e6b40a0e3a75623f695f777b3c plus seven then-uncommitted shop files. No complete standalone shop source commit existed. The historical build inputs are read-only evidence; every one of the seven published overlay files was already present byte-for-byte in this canonical working directory.

Retained published source: PreviewResource.java; TerminalIcons.java; terminal/index.html; terminal/terminal-icons.js; terminal/apps/minigames/app.js, app.css and model.js. Retained later commit4433b378f372777586251522139cb0e960522936 adds short-game-audio filtering. No reset/stash/checkout was performed.

Before applying any window hunk, the canonical source was fully rebuilt with a unique baseline artifact identity. PreviewResource.class, TerminalIcons.class and TerminalScreen.class match the live JAR byte-for-byte. Every non-music class matches; no class is removed. The only Java differences are five added music classes and three changed music classes, exactly the later retained music change. Three unchanged resource files differ only in line endings; generated TOML differs by version identity. All seven shop overlays remain byte-for-byte unchanged.

Window behavior changes are confined to TerminalScreen.java and new TerminalWindowLayout.java: enlarge the former outer rectangle by10%, add a native fullscreen/restore button beside X, retain16:10 and existing browser/page, and use shared GUI/pixel bounds for layout/input. No shop, icon, camera, album or hand model logic is altered by these hunks. Version identity is0.2.4-window-ui.1.

Offline checks on actual canonical files:967 layout/input/focus/close-reopen fixture assertions;11 PreviewResource GIF/path/size checks;36 actual headless Chromium shop checks. The browser checks include actual approved GIF decoding/animation beyond its11-second loop, unchanged media nodes across polling/card updates, per-category and refresh scroll retention, missing-GIF PNG fallback, resource recovery and preview release. Game/balance/redemption APIs in the browser test are explicit fixtures.

The previous fff05ddd... candidate is withdrawn and cannot be published. Its45 native window checks do not certify this rebuilt artifact. Targeted native MC/MCEF window/shop acceptance is still required and must be separately scheduled after the current owner's window is released. Compilation, byte preservation and browser checks alone are not a production acceptance claim.

Machine-readable build reports under build/current-window record exact artifact entries, source hashes, production/current-source differences and test results. Build directories and historical test evidence are not source truth. The published shop files and their regression tests are committed together so a future clone of dev preserves those features.

Published shop source consolidation commit: `04b8fe28c3421890490aa744dca3c7daed40da23`.
