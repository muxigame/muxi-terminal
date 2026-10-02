# Terminal camera task22 → task6

Independent camera implementation and review package. Do not deploy the review jar or apply the original task6 patch again. No shared source, shell renderer, hand renderer, Minecraft/Core release version, server entity, active client, SSH session or deployment was changed.

## Integration boundary

The real isolated Git worktree is `../camera-worktree`, branch `task22-camera`, backed by a private local bare clone. Its Git checkout starts at shared main `a0ba0ec`; its implementation/compilation source was overlaid with task6's **unified-terminal** candidate (the one described by task6's `unified-delivery/README.md`). **Patches target that captured unified candidate, not a0ba0ec or clean c674e73.** `VERIFICATION.json` records exact base hashes. Do not use a raw `git diff` of this worktree: that would include inherited task6 changes.

- `camera-module.patch`: new `TerminalCamera`, `TerminalCameraBridge`, `TerminalPhotoStore`, camera JS/controller/CSS and camera test files only.
- `camera-owner-wiring.patch`: minimal suggested hooks in task6-owned `TerminalBrowserSession`, `TerminalNativeBridge`, `app.js`, `index.html`, for task6 to review and reconcile with parallel settings/music additions.
- `camera-combined.patch`: the above two combined, tested on the captured candidate.
- `camera-task22-review.zip`: selected source, patches, audit manifest, review jar and test evidence. Browser evidence uses clearly labeled synthetic pixels.

No changes to TerminalScreen, TerminalHandRenderer, TerminalClient, TerminalWebPolicy, TerminalViewClient, SSO, shared CSS or the existing fixed shell. The bridge dispatch hook runs inside the owner's existing main-thread/generation/frame guard. All camera commands further require the current independent BUILTIN content browser and exact local `index.html#/camera` main frame; shell, other built-ins, WEB, ACCOUNT and iframe callers are denied. The existing WEB/ACCOUNT clients receive no router. No OS camera, HTTP upload, social share, file dialog, arbitrary local path or deletion API exists.

Owner wiring: add camera to both native built-in allowlists and JS route/navigation allowlist; include the module CSS and scripts after app-container. The additive camera module inserts its card/content into existing shell containers and uses existing button/card/navigation styles. Reconcile the shell's installed-app count once camera/settings/music are all integrated. Keep camera capability dispatch after existing authorization; do not attach it to any external CefClient.

## Implemented behavior and limits

Front view uses FIRST_PERSON; selfie uses THIRD_PERSON_FRONT. The game's ordinary world/player pipeline renders both, including installed YSM rendering and shader passes. No custom world render pass, entity teleport, player rotation mutation, private YSM API or projection/model matrix replacement is used. Third-person wall collision/FOV/character holding pose follow Minecraft. This is not a free camera; aiming follows the player's existing facing direction. Final native capture excludes terminal browser, HUD and first-person hands; a selfie may naturally include the character holding its terminal item. Non-HUD world effects already present in the framebuffer may appear.

RenderFrame Pre applies the requested camera type and enables the pre-HUD hook even after F1; Post restores the previous camera type/hideGui values. Tick, content-generation changes, screen close, world unload and focus loss also cancel/restore. Hand cancellation runs at HIGHEST priority before the existing terminal RenderHand renderer. RenderGui Pre copies the already rendered color texture before HUD layers and terminal Screen rendering. Native allocations and texture binding/pixel-pack parameters are restored in finally paths. PNG encoding and file IO use one daemon operation at a time; each worker exits, there is no persistent executor/queue. Once a shutter has entered a disk write it may finish saving after exit; callbacks and preview pixels from the closed session are discarded.

Preview is limited to approximately 1 FPS and 640 pixels wide; capture retains the framebuffer resolution, up to 16 megapixels. Opened photos are displayed at up to 1280 pixels wide; original PNG files remain full resolution. Framebuffer readback still runs on the render thread and needs real performance/shader verification. Capture works while the owned camera content is visible, the terminal Screen is open, and the game window is focused. It pauses on focus loss; click Resume after returning. Taking pictures while the terminal remains only held with no Screen open is intentionally unavailable in this candidate.

Saved location: `<gameDirectory>/screenshots/muxi-terminal/<local profile UUID>/`. Timestamp + UUID names and CREATE_NEW prevent overwrites. Photo identifiers are opaque and strictly validated; no caller controls directories. Symlink/junction directories and linked photo files are rejected. Album lists at most the recent 200 valid app-owned names from a bounded directory enumeration. There is no cloud or server storage, and screenshots/IO are never accessible to personal web apps.

## Verification

Passed: full product Java compilation with installed Minecraft 1.21.1 / NeoForge 21.1.250 / Java release 21; camera/controller/app JS syntax; controller lifecycle 14 checks; headless browser UI 13 checks; native photo-store tests including 80 concurrent unique saves, restart persistence, traversal/foreign-file/corruption rejection and symlink denial; existing SSO adapter 84 checks; native content transition 20 checks. Combined patch applies with whitespace checks and reproduces every selected source file. The JDK sandbox produced Windows toRealPath/ZIP close access errors; read-only dependency compilation and isolated filesystem tests were rerun with auto-reviewed escalation and passed cleanly. No approval was rejected.

Reproduce from the source root:

```powershell
node tests/camera/controller.test.cjs
python tests/camera/run-native-tests.py
python tests/camera/prepare-browser-qa.py
node tests/camera/run-browser-qa.mjs
python tests/run_navigation_adapter_tests.py
python tests/run_motion_tests.py
```

Build with existing build.py and explicit paths to the installed server/client/mod dependencies. The review jar keeps task6's 0.2.1 metadata and is **not published**. The pack's deployed 1.4.26 remains untouched.

## 131 real-game acceptance — still required

Parent must coordinate the exclusive GUI slot with task14 on the authorized `jbc-1@192.168.110.131`. GUI channel is currently absent. This task started no Minecraft GUI/client, took over no existing session, did not SSH, restart, hot-replace or release anything.

1. Vanilla and actual YSM character selfies, forward/selfie switches during live preview, near-wall clipping, shader on/off and day/night. Compare preview with saved PNG, orientation, colors, image flip/alpha and absence of terminal/HUD/first-person hands. Validate shader final-frame capture location; compilation does not prove this.
2. Main/off hand, right/left dominant hand, other hand empty/occupied, dual terminal, with FirstPerson installed. Verify hand cancellation and subsequent terminal rendering restored in every variant.
3. Original FIRST_PERSON/THIRD_PERSON_BACK/THIRD_PERSON_FRONT, original F1 hideGui, canceled shutter, rapid switching, Home/Back/Escape/Delete, another Screen, world unload/disconnect, Alt-Tab/minimize/focus regain, resize/fullscreen and repeated reopen. Confirm original camera and GUI state on the following frame and no preview on stale/closed content.
4. Render/native memory and thread counts through repeated capture/view/exit; inject PNG write failure and readback failure; check pack state/texture binding, no leaked NativeImage, no lingering camera worker, and no broken lighting/matrices in subsequent held-terminal/world draws. Check 1080p/4K stalls and rejected >16 MP frames.
5. Album persists after client restart; rapid photos never overwrite; failure path informs the UI; screenshot root is profile local. No automatic upload. WEB/ACCOUNT, iframe, local-origin spoof, another built-in and stale generation camera requests must fail against the real private MCEF client/router.

Actual render/YSM/shader/resource release and 131 GUI evidence remain acceptance gates. Review jar/source alone is not release approval.
