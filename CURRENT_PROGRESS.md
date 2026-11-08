# CURRENT_PROGRESS.md

**Stage:** 3 — Jiàndié Camera (in progress). Stages 1–2 complete.
**Updated:** 2026-11-08. **Repo:** https://github.com/bwjremployment-bit/baby-monitor (branch `master`; CI builds both APKs on every push).

## Stage status
| Stage | State |
|---|---|
| 1 Requirements/architecture | ✅ done |
| 2 Foundation + CI | ✅ done — both APKs built green on CI (run #2) |
| 3 Jiàndié Camera | 🔶 in progress (see below) |
| 4–7 | not started |

## Verified facts (evidence)
- `shared/` (pure Kotlin): **34 unit tests pass** locally and on CI.
- `signaling-server/`: **2 integration tests pass** locally and on CI.
- Both app modules **compile and package** on CI (Stage 2 skeleton). No functional on-device testing has happened yet.
- Sandbox cannot reach `dl.google.com` → Android compiled only on GitHub Actions.

## Stage 3 tidy-up done at session start (2026-11-08)
Found and fixed before building on top of them:
1. **Auth was not actually implemented** (docs claimed peer-to-peer verification; nothing did it) and a malicious relay could swap the DTLS fingerprint. Now: viewer `start-request` carries a token the **camera verifies before enabling capture**; offer/answer carry an **HMAC over the SDP** (binds DTLS fingerprint). 6 new tests.
2. **Manual pairing code was ~79 chars** → room now derived one-way from a single 128-bit secret → **28 chars**.
3. Stale docs corrected: module layout (`shared/` not `core/`), TD-006 (no home TURN), security design rewritten, test counts.

## Stage 3 remaining work (not yet built)
WebRTC capture/preview layer · OkHttp signalling client · Keystore storage + QR · TransmissionController/Service (armed → live on authenticated start-request, network monitoring, reconnection) · real camera UI + Radius screen · CI green · on-device validation (user, Realme 6).

## Decisions / user answers (see MASTER_PROJECT.md, TECHNICAL_DECISIONS.md)
- Honest-monitor scope (no covert/disguised features). Camera phone = Realme 6 (Android 10/11) → minSdk 29. Pairing = QR + manual code. Manual start + optional auto-start + viewer remote-wake when armed. Balanced quality. Signalling relay on a free host (Deno Deploy suggested; user creates any login).
- Usage is **on-demand** (≤~4 h, ~3×/week) → free TURN tier suffices if needed; IPv6-direct preferred.

## Open questions
- **OQ-4:** home LTE carrier + viewer carrier, and IPv6 support (decides whether TURN is needed). Needed by Stage 5.
- Where/when to deploy the signalling relay (needed by Stage 5; user must create the free hosting login).
- Viewer phone Android version (minor).
