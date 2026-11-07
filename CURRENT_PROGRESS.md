# CURRENT_PROGRESS.md

**Stage:** 1 (complete). **Next:** Stage 2, after user go-ahead + OQ answers.
**Updated:** 2026-11-07.

## Done in Stage 1
- Inspected cloud build env: Java 21, Gradle, Node 22, Python 3.13, git present. **No Android SDK** (install in Stage 2). No physical device in this env.
- Agreed scope correction: **honest baby monitor**, not a disguised/covert camera (see MASTER_PROJECT §2). Covert features refused; all legitimate streaming capability retained.
- Captured setup answers Q1–Q5 (Q1 pending exact device version).
- Wrote all Stage 1 deliverables:
  - `docs/PROJECT_ARCHITECTURE.md`, `docs/REQUIREMENTS.md`, `docs/CONNECTIVITY_DESIGN.md`, `docs/SECURITY_DESIGN.md`, `docs/RISK_REGISTER.md`
  - `TECHNICAL_DECISIONS.md`, `DEVELOPMENT_ROADMAP.md`, `TEST_REGISTER.md`, `BUG_REGISTER.md`, `MASTER_PROJECT.md`, this file, `NEXT_SESSION_INSTRUCTIONS.md`
- Initialised git repo at `/home/user/babymonitor`.

## Key decisions locked
- Kotlin + WebRTC (`io.github.webrtc-sdk:android`, fallback GetStream); foreground service (camera+mic); self-written WSS signalling relay; STUN public/self; TURN self-hosted coturn if NAT needs it; EncryptedSharedPreferences+Keystore; QR via ML Kit/ZXing. (See TECHNICAL_DECISIONS.md.)

## No code yet
Stage 1 is design-only. Nothing to build/test on device yet. Any claim of working features before Stage 3+ would be unverified — do not assume.

## User answers received (2026-11-07)
- **OQ-3 → camera phone = Realme 6** (RMX2001; shipped Android 10, max Android 11). minSdk set to **29 (Android 10)** — widest practical, covers the device. Viewer phone version still unconfirmed (not blocking; viewer just needs to run the viewer app).
- **OQ-2 → "no preference, pick best"** → decision: **Deno Deploy** free tier for the signalling relay (WebSocket support, no credit card, deploy from a repo). Alt: home always-on machine if one exists. User creates the free login themselves; I create no accounts.
- **OQ-1 → home internet is LTE** (modem = **Sagemcom 5366 LTE**). This is the big one — see below.

## CRITICAL CONNECTIVITY FINDING (from modem screenshot)
Home internet is **cellular LTE**, so it is almost certainly behind **carrier-grade NAT (CGNAT)**: no public IPv4, port-forwarding impossible. Consequences:
- **Self-hosted coturn at home is NOT viable** (home is not publicly reachable). TD-006 / R1 updated.
- Both ends are likely cellular CGNAT (home LTE + viewer mobile data) → **direct IPv4 P2P will frequently fail**.
- **Best zero-cost path to a direct connection = IPv6**, IF both the home LTE and the viewer's mobile data hand out public IPv6. Many carriers do. This needs checking (new OQ-4).
- If IPv6 isn't available on both ends, a **publicly-hosted TURN relay** is required, and free TURN under *continuous* monitoring is genuinely constrained (free tiers cap monthly GB). This is an honest feasibility limit to resolve with the user, not to paper over.

## Still open
- **OQ-4 (new, important):** which mobile carrier(s) are the home LTE modem and the viewer phone on, and do they provide IPv6? (Determines whether a truly-free direct connection is possible.)
- Viewer phone Android version (minor).
