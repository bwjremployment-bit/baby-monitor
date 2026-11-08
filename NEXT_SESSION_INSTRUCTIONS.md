# NEXT_SESSION_INSTRUCTIONS.md

## Start of any session
1. Read `CURRENT_PROGRESS.md`, then this file.
2. Verify, don't trust: `git log --oneline`; `cd shared && ./gradlew test` (expect 40 pass); `cd signaling-server && npm test` (expect 3 pass); check the latest GitHub Actions run (`gh api repos/bwjremployment-bit/baby-monitor/actions/runs`).
3. Do not redo finished work.

## Environment facts
- Sandbox cannot compile Android (egress blocks dl.google.com). `shared/` and `signaling-server/` build/test here; **APKs build on GitHub Actions** (repo: bwjremployment-bit/baby-monitor, branch master). Pushing works (Claude GitHub App installed).
- Get compiler errors from CI: download the failed job log with `gh api repos/<repo>/actions/jobs/<id>/logs` and grep for ` e: `.

## Where Stage 3 stands
All Stage 3 camera code is written (see CURRENT_PROGRESS). Remaining for Stage 3:
1. Get CI green for the camera module (fix any compile errors).
2. Small unit tests for device-independent bits if any are worth it.
3. **The user installs the camera APK on the Realme 6** and runs T-005/T-005b/T-005c (TEST_REGISTER). Stage 3 is NOT complete until background/locked operation is validated on the device. Give the user step-by-step instructions and ask for results; record them honestly.

## Then (when the user says go)
**Stage 4 — Yps Viewer:** pairing (scan QR via CameraX+ML Kit or ZXing, or paste/type the 28-char code), `SignalingClient` equivalent (reuse the camera's; consider moving it to a shared Android library module), send `StartRequest(nonce, signStart(...))`, receive offer → verify `verifySdp(CAMERA)` → create answer → sign with `signSdp(VIEWER)`, render with `SurfaceViewRenderer`, play audio, status + auto-reconnect + manual button, fullscreen/rotation.
Reconnect rule: on ICE failure the viewer sends a **new** StartRequest (fresh nonce); the camera restarts the session.

## Open questions for the user
- **OQ-4:** home LTE carrier + viewer carrier; IPv6? (The camera's Radius screen now reports the real connection path — direct IPv4/IPv6 or relay — so this will be answered empirically in Stage 5.)
- **Signalling relay hosting:** needs a free host and a one-time login the USER creates (Deno Deploy suggested). The relay in `signaling-server/` is Node `ws`; it must be ported/deployed (Stage 5), or run on any always-on machine. Until then the apps cannot connect across the internet.
- Viewer phone Android version.

## Reminders
Zero cost; no accounts created on the user's behalf; honest-monitor (no covert features; never suppress OS indicators or the foreground notification); label simulated vs on-device results; never claim success without evidence.
