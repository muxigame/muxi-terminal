# Native camera validation

The terminal camera opens a native full-screen viewfinder. It captures the world/shader output before camera controls and saves through `Screenshot.grab` to the current Minecraft `gameDirectory/screenshots`. Closing restores the same terminal screen and browser document plus the original perspective/HUD settings.

`NativeScreenshotLifecycleMixin` explicitly owns the native readback until the native screenshot executor accepts it. Cancellation, listener exceptions, rejected tasks, readback failures and asynchronous completion all release the image without forced garbage collection.

Run the lifecycle regression fixtures with a JDK 21 and the existing NeoForge server libraries:

```text
python tests/camera/run-screenshot-lifecycle.py --java-home <jdk21> --server-libraries <server>/libraries
```

The fixtures exercise the production ownership wrappers: 22 assertions and 1000 cancellation scopes. They complement the real Minecraft run; they do not simulate visual acceptance or prove mixin injection by themselves.

`camera-131-20261002.json` records the actual 131 run: 134 checks, six forward/selfie PNGs under Better MC shaders and YSM, 16 camera cycles with perspective/HUD restoration, a real F2 keyboard-handler capture, and installed native screenshot-manager enumeration of both F2 and camera files. The JVM exited normally with no remaining descendants. Repeating 16 cancelled captures changed native image growth from 16 images / 58,982,400 bytes to zero. No forced GC was used.

The measured full-pack image baseline belongs to the loaded game and is not attributed to this camera. Resource deltas during camera cycles stayed bounded; closing the terminal released its CEF texture. Physical OS focus-loss was not exercised. The independent combined CEF album run still failed on a DOM-response timeout and invalid original callback frame; that gallery acceptance remains with its owner.

The tested jar hash identifies a shared dev working-tree build that included other owners' uncommitted changes. No production instance or release was changed. Raw PNGs, result JSON, process sampling and JVM native-memory receipts remain in the local QA directory recorded in the JSON.
