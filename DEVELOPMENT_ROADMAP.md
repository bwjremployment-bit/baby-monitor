# DEVELOPMENT_ROADMAP.md — Seven stages

One (or more) Claude Code session per stage. Do not start a stage without the user's go-ahead. Verify prior work against code before continuing.

## Stage 1 — Requirements, research, architecture ✅ DONE (2026-11-07)
Environment inspected; scope corrected to an honest monitor; all design deliverables written (architecture, requirements, connectivity, security, risk register, decisions, roadmap) + tracking docs. **Exit:** awaiting user go-ahead + OQ-1/2/3.

## Stage 2 — Project foundation & shared system ✅ DONE (2026-11-07, APK build pending first CI run)
- Install free Android cmdline SDK tools in this env; accept licenses; wire Gradle + version catalog.
- Create Gradle root + modules: `core` (lib), `camera` (app), `viewer` (app), `signaling-server` (Node/Deno).
- App IDs, Kotlin, WebRTC + AndroidX deps, manifests/permissions scaffolding.
- Shared `core`: signalling message schema + WS client, pairing/crypto, connection state machine, WebRTC factory helpers.
- First successful **builds** of both app modules (empty-but-compiling). Commit to git.
- **Exit:** two buildable APK modules + shared core + signalling server skeleton, builds green.

## Stage 3 — Jiàndié Camera
- Camera preview + honest LIVE indicator; switch/flash; Radius settings screen.
- Capture → WebRTC tracks; foreground TransmissionService (camera+mic); background/screen-off/locked capture.
- Network monitoring, lifecycle, reconnection, diagnostics/errors in Radius; optional boot/auto-start.
- Unit tests for lifecycle/state logic. **Exit:** camera app runs, transmits to a loopback/test peer; background validated on device (by user).

## Stage 4 — Yps Viewer
- Live-view UI, pairing (scan + manual), connection management, remote video+audio playback, status + manual reconnect, full-screen + rotation, error handling.
- **Exit:** viewer connects to the real camera (not a mock) on a LAN/same-network first, media shown.

## Stage 5 — Integration & remote connectivity (critical)
- Both ends on the shared signalling; pairing + authenticated connect end to end.
- STUN; NAT traversal; TURN (per OQ-1) incl. self-hosted coturn if needed.
- Remote across **different** networks (home Wi-Fi ↔ mobile data); reconnection; A/V sync; latency tuning; security verification.
- **Exit:** genuine remote connection with live A/V across networks, documented.

## Stage 6 — Testing, debugging, optimisation
- Transmitter tests, viewer tests, end-to-end acceptance scenario, long-run/thermal/battery, fault injection.
- Clearly separate simulated vs on-device results. Fill TEST_REGISTER + BUG_REGISTER.
- **Exit:** acceptance scenario passes on real devices; critical bugs closed.

## Stage 7 — Final build, delivery, handover
- Final code/security review; clean builds; generate installable APKs (free local keystore); verify install + inter-op; finalise all guides (install/pair/setup/operate/troubleshoot) + known limitations.
- **Exit:** both APKs + full source + docs delivered and backed up.
