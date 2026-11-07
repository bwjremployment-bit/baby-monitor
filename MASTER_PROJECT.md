# MASTER_PROJECT.md — Remote Baby Monitor (Jiàndié Camera + Yps Viewer)

**Last updated:** 2026-11-07 (end of Stage 1)
**Current stage:** Stage 1 complete → awaiting user input before Stage 2

## 1. What this project is

Two Android apps that together form one remote baby-monitoring system:

| App | Role | Where it runs |
|-----|------|---------------|
| **Jiàndié Camera** | Transmitter: captures live video + audio in the baby's room and streams it over the internet | Spare phone, home Wi-Fi |
| **Yps Viewer** | Receiver: connects remotely and shows the live video + audio | Primary phone, mobile data / any internet |

The two communicate over the public internet via **WebRTC** (peer-to-peer media, DTLS-SRTP encrypted) with a tiny self-hosted **signalling relay** used only to set up the connection.

## 2. Important scope decision (agreed 2026-11-07)

The original brief asked for the transmitter to be **disguised as an ordinary camera app** with all streaming hidden in a "Radius" submenu, silent capture, and no on-screen indication it is transmitting. That design is indistinguishable from covert-surveillance ("stalkerware") and **will not be built**.

**Agreed instead — an honest baby monitor:**
- The camera app **clearly shows its live / streaming state** on screen. Anyone holding the phone can see it is transmitting.
- All the requested capability stays: WebRTC live video + audio, home-Wi-Fi → remote-viewer-on-mobile-data, QR + code pairing, encrypted & authenticated connections, auto-reconnect with status + manual button, balanced quality, background operation with the screen off.
- "Silent" means the transmitter makes no notification/UI sounds in the baby's room — **not** suppressing Android's mandatory camera/mic privacy indicators or the foreground-service notification (Android enforces those; we comply).
- App product names (Jiàndié Camera / Yps Viewer) are retained as labels, but the camera UI is honest about what it does.

## 3. Hard constraints

- **Zero cost.** No purchases, subscriptions, paid APIs/hosting/accounts, no cards, no trials that convert. Open-source + genuinely-free infrastructure only.
- No account creation on the user's behalf. No credential exposure.
- Private family use. Encrypted, authenticated, no public feed, no unnecessary recording/storage.

## 4. Setup answers (from user)

| Q | Topic | Answer |
|---|-------|--------|
| Q1 | Android compatibility | **D** — widest practical; pin to spare-phone version. *(Phone model/version still needed — see NEXT_SESSION_INSTRUCTIONS.md)* |
| Q2 | Pairing | **C** — QR **and** manual code |
| Q3 | When transmission starts | **C** — manual start in-app, optional auto-start where Android allows; **plus** viewer may remotely turn transmission on when the camera phone is connected/standing by |
| Q4 | Connection drop | **C** — auto-reconnect + status + manual reconnect button |
| Q5 | Video quality | **C** — balanced quality / latency / battery |

## 5. Document map

Tracking (root):
- `MASTER_PROJECT.md` (this file) — the one-page overview
- `CURRENT_PROGRESS.md` — what is done / in progress right now
- `NEXT_SESSION_INSTRUCTIONS.md` — exact next tasks + open questions
- `DEVELOPMENT_ROADMAP.md` — the seven stages
- `TECHNICAL_DECISIONS.md` — technology choices + rationale
- `TEST_REGISTER.md` — tests planned/run + results
- `BUG_REGISTER.md` — defects + status

Design deliverables (`docs/`):
- `docs/PROJECT_ARCHITECTURE.md` — system + per-app architecture
- `docs/REQUIREMENTS.md` — requirements specification
- `docs/CONNECTIVITY_DESIGN.md` — WebRTC / signalling / STUN / TURN / NAT
- `docs/SECURITY_DESIGN.md` — pairing, auth, encryption, storage
- `docs/RISK_REGISTER.md` — risks + mitigations

## 6. Session discipline

Seven stages, one (or more) session each. **Stage 1 done.** Do not start Stage 2 without the user's go-ahead and the two required inputs (phone version + signalling-host choice). At each session start: read `CURRENT_PROGRESS.md` and `NEXT_SESSION_INSTRUCTIONS.md`, then verify claims against the actual code before continuing. Never re-do completed work; never claim success without evidence.
