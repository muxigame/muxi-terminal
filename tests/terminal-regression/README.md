# 131 unified terminal regression QA

Test-only additions for source base 307c651c04293dd442e6e12ddbf8e4923ca53680.
The exact 131 terminal jar is locked to SHA256
0c60283617c2084890aa74695b03187e1f1077df0a204f42d06ab448300094ec.
No product src files, server configs, original worlds or installed clients change.

## Integrator

Apply the accompanying additions patch to the terminal test branch and use the
already-authorized Git synchronization workflow to fetch it on 131. Do not SCP
a private package. Do not rebuild/replace the locked jar with an unknown input.
A later QA-only commit may descend from the source base; the launcher rejects
changes or untracked files in src and records Git HEAD, jar hash and all mod hashes.
No commit/push is performed by this delivery.

Known paths were independently checked read-only over SSH:
- worktree: C:\Users\ranzh\Documents\Codex\terminal-integration-task6-20261001\worktree
- JDK21: C:\Users\ranzh\Documents\Codex\terminal-integration-task6-20261001\tools\jdk\jdk-21.0.12.1+1
- game resources: C:\Users\ranzh\workspace\dev\muxigame\_client_test\game
- pack: C:\Users\ranzh\workspace\dev\muxigame\better-mc-remake\pack\staging\files
- compile dependencies, read-only: C:\Users\ranzh\workspace\dev\muxigame\bmc5server\libraries

## First user desktop action, after Git synchronization

In the assigned 131 interactive desktop, open PowerShell and run exactly:

    & 'C:\Python38\python.exe' 'C:\Users\ranzh\Documents\Codex\terminal-integration-task6-20261001\worktree\tests\terminal-regression\run_131_qa.py' preflight --assigned-slot task5-parent-assigned --visible

Impact: compile a small test, then open a new 640x360 GLFW window for about
1.5 seconds and close it automatically. Java memory cap 128 MiB; renderer process
timeout 15 seconds. No Minecraft, world, mod initialization or server launch.
The screenshot is a calibration rectangle, explicitly not game regression proof.
It records actual GL renderer/vendor/version, visible flag, GL error and framebuffer
pixels. The expected NVIDIA hardware renderer is mandatory; inventory alone is
not accepted. A success receipt is required before other modes.

SSH session0 may run --prepare-only for static compilation/resource copying.
It cannot launch this preflight or any client. The non-session-0 guard remains.
No scheduled task, desktop input injection, user-process control, trust change,
or cross-session helper is used. Scripts modify no system JAVA_HOME or PATH.

Evidence is in
C:\Users\ranzh\Documents\Codex\terminal-integration-task6-20261001\qa-runtime.
active-preflight.json names the unique lab; boot.log, inputs.json, exit.json,
render-preflight-result.json, render-preflight.png, run-summary.json and
last-preflight.json provide observable receipts.

## Subsequent authorized desktop modes

Replace preflight with full after reviewing the preflight receipt.
full creates a private singleplayer flat world, uses the released 1.4.25 default
mod selection, validates all 419 published mod input hashes (excluding terminal),
caps Java at 6 GiB/4 CPUs and exits after at most 900 seconds.
firstperson repeats the same suite with FirstPerson enabled and checks its actual
API enabled state in every hand combination. Model-mod presence is recorded;
this does not imply every custom skin/model has been tested.

The full suite captures 41 normal scenes, equip/swing shots, eight actual-frame
motion shots for day/night x shaders on/off, and logout title. It checks all eight
hand combinations, real PlayerRenderer arm directions, PoseStack restoration,
ordinary items before/after, actual dimension dayTime, native Core rows versus
visible task DOM, all ten naturally loaded guide images, repeated app navigation,
held shell/content separation and intermediate alpha/scale. Shader appearance
still requires visual review of the raw game framebuffer captures.

Sampling uses the content browser, loading/rendered/motion readiness, route,
generation and identity checks, delayed CefStringVisitor getSource, and a
measurement-only DOM attribute. It installs no QA query router and invokes no
hydrateImages, no tasks fixture and no image fixture. Actual product native
requests are logged by BridgeProbeMixin. A timeout retains actual state/last DOM;
it is not silently relabeled as a product cancellation.

container uses the owner's existing synthetic SSO/security fixture in a visible
menu-only client, 2 GiB/2 CPUs, timeout 180 seconds. It needs an explicit
--core-jar argument naming the reviewed SSO Core candidate; published Core 1.12.0
does not contain TerminalPassportApi. Obtain that artifact through its own owner's
Git/build handoff, not by editing the Core or server workspace here.
It verifies rapid launch/animation, persistent shell, latest content, real CEF
synthetic one-use POST/cancel/late callback, external/iframe native denial,
web-to-local blocking and personal-app persistence. The HTTP fixture listens
only on 127.0.0.1. Synthetic account responses and the test-only identity/TCP
agent are explicitly separated from the genuine tasks/icons/world run.
Production SSO is not claimed or contacted by the fixture.

All modes write the actual private process PID and exit code; only that child
can be terminated on timeout. The prior OpenAL.dll/c0000409 issue is not patched
or hidden. Nonzero exit blocks a clean-pass claim. Existing JBY screenshots
belong to a different jar; no new 131 native verification has happened yet.
MC graphics FAST is measured when the actual client runs; no model/service Fast
tier is visible, so no tier verification is claimed.
