# Task22 source checkpoint — paused, not release-ready

This commit saves source snapshots and independently reviewable patches. It does not apply or claim ownership of the mixed task6/task14 working-tree baseline. Do not cherry-pick the entire mixed worktree or publish this candidate as accepted.

Current production source: current-source/. Shared owner wiring and compile context: owner-context/. Native module/wiring deltas: native-review/. Prior camera and album work: historical-review/. QA-only code and fixed Session2 action: qa-source/. Task scripts: task-scripts/.

The .2 candidate compiles; its real retest has not run. First actual 131 run, Java 15204, passed 45 assertions and saved six photos but failed at stage 5 with CefQueryCallback_N::finalize(). Exit 0 is normal process shutdown, not functional acceptance. All selfies only showed sky; YSM self-portrait acceptance remains pending. Current .2 repair completes invalid album callbacks with a safe rejection and has 69 bridge fixture checks. QA also adds stable scene readiness and authority/render diagnostics. No production changes, upload, OS camera, or new task registration.

Paused by user before switching computers. Do not execute tests, launchers, SSH scripts, builds, or deployments until the parent coordinates authorization on 131. The two older one-shot task scripts are historical code and were never registered; the current handoff exclusively uses task14's existing MuxiDesktopQA entry. Session2 fixed script is already staged on 131 for run-20261002-055245-420455d3, not started.

Photo PNGs, logs, runtime data, build outputs, archives, credentials, and binaries are deliberately outside this Git checkpoint. They remain in the local evidence/review archives. The original mixed worktree and other owners' changes are preserved untouched.
