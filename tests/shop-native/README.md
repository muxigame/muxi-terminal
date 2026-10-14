# Frozen joint shop native QA

This is the maintained small-code source for the shop UI checks originally run on 2026-10-14. No Minecraft instance, world, browser profile, logs, screenshots, model, GIF, PNG or candidate JAR belongs in this directory.

The historical fixture is the exact 1.4.38 joint input lock e036daf0aff1236c0fc559b0aeef9e85faab1975a33dff29a242c105a2236917, with shop-layout.1, parkour qa4 and endless candidate5. It is not a current production release selection. The held runtime contained one actual offline player; no second player or authenticated purchase was claimed. GUI scale4 produced a real 736x288 content viewport and motivated shop-layout.2. Its 24 Chromium checks passed; the initial CSS-only native preview did not yet include the final spacing fix.

Only run after the coordinator assigns the exclusive client input window. All new-copy and large-write operations remain paused until separately authorized. Set `MUXI_SHOP_NATIVE_EVIDENCE` to the owned evidence directory. Runtime data and dependencies must be supplied independently, verified against the lock, and remain outside versioned source. `MUXI_NATIVE_LAB` selects exactly the marked instance for the existing WindowProbe controller.

Order: prepare_joint_ui_20261014.py -> run_joint_ui_20261014.py -> joint_ui_control_20261014.py open/shop -> native_shop_checks_20261014.py -> native_animation_followup_20261014.py. The native short-height preview is optional and removes its temporary CSS before releasing the GUI. External input handoff and production deployment are coordinator responsibilities.

Known measurement corrections: Canvas image-poster hashes do not establish rendered GIF animation; compare fully decoded actual Minecraft framebuffer captures for each visible image. Screenshot.grab is asynchronous, so file existence alone is insufficient. A backend product without a preview must be checked for title, price and CTA rather than failed solely for lacking an img element. Preserve exact product hashes, actual player count, normal JVM exit codes and JCEF cleanup status separately.
