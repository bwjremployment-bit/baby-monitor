# NEXT_SESSION_INSTRUCTIONS.md

## At the start of the next session
1. Read `CURRENT_PROGRESS.md` then this file.
2. Verify against the repo: `git log --oneline`, `cd shared && ./gradlew test` (should pass 28), `cd signaling-server && npm test` (should pass). Confirm module files exist before continuing.
3. Do not redo Stages 1–2.

## Environment reality (important)
- The sandbox **cannot compile Android** (egress blocks `dl.google.com` → no Android SDK, no AGP/AndroidX). `shared` + `signaling-server` build/test here; **APKs build on GitHub Actions** (`.github/workflows/android.yml`) or Android Studio.
- To get the first real APKs + green Android build, the repo needs to be on **GitHub** so Actions can run. Ask the user for a repo (or to create an empty private/public one) and push; do NOT create accounts/repos on their behalf without the go-ahead. CI is free for public repos; private repos get free minutes.

## Open items carried forward
- **OQ-4 (connectivity, important):** home LTE carrier + viewer carrier, and whether they provide **public IPv6** (decides whether a free direct connection is possible vs needing hosted TURN). Needed by Stage 5; good to resolve during Stage 3/4.
- Viewer phone Android version (minor).

## Stage 3 objectives (Jiàndié Camera — the transmitter)
Build the real camera app on top of the Stage 2 skeleton:
1. Camera preview (WebRTC `Camera2Capturer` → local `VideoTrack` → `SurfaceViewRenderer`) with an always-visible LIVE indicator (honest monitor).
2. Camera switch; flash/torch where supported; basic camera settings.
3. Real **Radius** screen: enable/disable transmission, pairing (generate + show QR via zxing + manual code), receiver auth, A/V config, connection info, diagnostics/errors.
4. Flesh out `TransmissionService`: own the capturer + PeerConnection; keep capturing in background / screen-off / locked.
5. Microphone capture → audio track (silent locally).
6. Network monitoring (`ConnectivityManager`), transmission lifecycle via the shared state machine, reconnection with backoff.
7. Wire the `shared` signalling WS client (implement the Android WS transport) + WebRTC `PeerConnectionFactory` helper.
8. Optional auto-start/remote-start (best-effort per Android 14+ — document).
9. Iterate builds via CI; add instrumented/unit tests where device-independent. Update TEST_REGISTER + BUG_REGISTER with real results. On-device background validation by the user.

## Reminders
- Zero cost; no account creation on the user's behalf; honest monitor (no covert features; never suppress OS indicators/FGS notification).
- Label simulated vs on-device results honestly. Never claim success without evidence (e.g., don't call background operation done until validated on the Realme 6).
