# CURRENT_PROGRESS.md

**Stage:** 3 — Jiàndié Camera (in progress). Stages 1–2 complete.
**Updated:** 2026-11-08. **Repo:** https://github.com/bwjremployment-bit/baby-monitor (branch `master`; CI builds both APKs on every push).

## Stage status
| Stage | State |
|---|---|
| 1 Requirements/architecture | ✅ done |
| 2 Foundation + CI | ✅ done — both APKs built green on CI (run #2) |
| 3 Jiàndié Camera | 🔶 **code complete + compiles on CI; NOT yet validated on a device** — awaiting the user's Realme 6 tests (docs/DEVICE_TEST_GUIDE.md) |
| 4–7 | not started |

## Verified facts (evidence)
- `shared/` (pure Kotlin): **40 unit tests pass** locally and on CI.
- `signaling-server/`: **3 integration tests pass** locally (takeover + heartbeat added).
- Both app modules **compile and package** on CI (Stage 2 skeleton). No functional on-device testing has happened yet.
- Sandbox cannot reach `dl.google.com` → Android compiled only on GitHub Actions.

## Stage 3 tidy-up done at session start (2026-11-08)
Found and fixed before building on top of them:
1. **Auth was not actually implemented** (docs claimed peer-to-peer verification; nothing did it) and a malicious relay could swap the DTLS fingerprint. Now: viewer `start-request` carries a **signed, single-use nonce** the camera verifies (and replay-checks) before enabling capture; offer/answer carry an **HMAC over the SDP** (binds the DTLS fingerprint). Covered by tests.
2. **Manual pairing code was ~79 chars** → room now derived one-way from a single 128-bit secret → **28 chars**.
3. Stale docs corrected: module layout (`shared/` not `core/`), TD-006 (no home TURN), security design rewritten, test counts.

## Stage 3 — what is written (compile status: see CI run for the latest commit)
- `data/SecurePrefs` (Keystore-encrypted settings + pairing secret), `data/QrCode`
- `net/SignalingClient` (OkHttp WSS, backoff, reconnect-on-network-change)
- `rtc/CameraEngine` (ref-counted camera; mic only while LIVE; flip; best-effort torch), `rtc/PeerSession` (send-only, bitrate-capped; reports direct/IPv6/relay path), `rtc/IceConfig`
- `service/TransmissionController` (standby → LIVE only after verifying a signed, single-use start-request and the viewer's signed answer; 60 s grace; locks; network callback)
- `TransmissionService` (honest ongoing notification, Turn-off action), `NotificationHelper`, `BootReceiver` ("tap to resume"), `Arming`
- `MainActivity` (preview + always-visible status pill + flip/flash/Radius), `RadiusActivity` (standby, QR + code pairing, server, quality, audio, relay, battery shortcuts, diagnostics)

## CI status
Latest commit builds green: shared tests, relay tests, **both APKs** (camera ~27 MB, viewer skeleton ~26 MB). A built-in **background self-test** (Radius) reports on the phone whether Android lets standby use the camera and microphone with the screen off.

## Not verified yet (do not assume these work)
Everything Android is **compile-checked only**. Nothing has run on a phone. The critical unknown is **T-005**: that a service armed from the foreground can still open the camera on remote wake after the screen has been off for a while (Android 11 while-in-use rule). Also unverified: torch while streaming, Realme background-kill behaviour, audio capture, the QR render, encrypted prefs on the device.

## Decisions / user answers (see MASTER_PROJECT.md, TECHNICAL_DECISIONS.md)
- Honest-monitor scope (no covert/disguised features). Camera phone = Realme 6 (Android 10/11) → minSdk 29. Pairing = QR + manual code. Manual start + optional auto-start + viewer remote-wake when armed. Balanced quality. Signalling relay on a free host (Deno Deploy suggested; user creates any login).
- Usage is **on-demand** (≤~4 h, ~3×/week) → free TURN tier suffices if needed; IPv6-direct preferred.

## Open questions
- **OQ-4:** home LTE carrier + viewer carrier, and IPv6 support (decides whether TURN is needed). Needed by Stage 5.
- Where/when to deploy the signalling relay (needed by Stage 5; user must create the free hosting login).
- Viewer phone Android version (minor).
