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

## Blocking open questions (need user)
- **OQ-1** TURN/NAT: home public IP + port-forward? viewer's mobile carrier?
- **OQ-2** Signalling host choice (home / Deno Deploy / Cloudflare / other). No accounts will be created on the user's behalf.
- **OQ-3** Device versions: spare (camera) phone make/model + Android version; viewer phone Android version.
