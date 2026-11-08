# SECURITY_DESIGN.md — Security & privacy

*Revised 2026-11-08 (Stage 3 start): the earlier draft had the relay verifying tokens, which the zero-knowledge relay cannot do. Authentication is now end-to-end between the two peers, and the code (`shared/…/pairing/Pairing.kt`) implements and tests it.*

## 1. Threat model (private family use)

Protect against: an unauthorised party watching the baby's room; eavesdropping or man-in-the-middle on the media; a **compromised or malicious signalling relay**; a lost phone leaking pairing secrets; someone who learns the room id.

Out of scope: nation-state attackers, a rooted/compromised endpoint phone. Target: strong, practical protection on zero-cost infrastructure.

## 2. Trust model — the relay is untrusted

The signalling relay (free-hosted, third-party infrastructure) sees only: the room id, who joined, and SDP/ICE blobs. It **never holds the pairing secret**, so it can neither mint nor verify credentials, and it never sees media.

Therefore authentication is **peer-to-peer**, using HMAC-SHA256 keyed by the pairing secret:

| Control | Who proves | Who verifies | What it stops |
|---|---|---|---|
| **Start-request MAC** `signStart(secret, room, nonce, now)` + one-time nonce | viewer | **camera**, *before* enabling camera/mic; nonce checked against a replay cache | anyone who merely learned the room id waking the camera; a relay **replaying** a captured request |
| **SDP MAC on the offer** `signSdp(…, CAMERA, sdp)` | camera | viewer | relay injecting its own session / rogue "camera" |
| **SDP MAC on the answer** `signSdp(…, VIEWER, sdp)` | viewer | camera | relay answering in the viewer's place |

The SDP contains the **DTLS fingerprint**, so a valid MAC over the SDP means the media key exchange is bound to the pairing secret. A relay that swaps the fingerprint to MITM the media produces an SDP whose MAC fails (tested: a one-character change is rejected). MACs are role-bound (a camera signature cannot be reflected back as a viewer signature), room-bound, and time-windowed (5 min, ±1 window of clock skew).

Replay: every start-request carries a fresh 128-bit nonce; the camera remembers nonces for 15 min (longer than the MAC can verify, bounded to 256 entries) and ignores repeats, so a relay cannot replay a captured request to switch the camera on. Even a successful wake could not obtain media (that needs a validly signed answer). Clock skew: MACs are accepted ±1 five-minute window, so phone clocks must be within ~5 min (both phones auto-set their time from the network).

**Relay availability is not a confidentiality problem:** a hostile relay can drop messages or refuse service (the viewer then shows "can't connect"), but cannot view, wake or impersonate. Accepted: the relay is third-party free hosting.

## 3. Media encryption

WebRTC media is **DTLS-SRTP encrypted by default** (not optional). Per-session keys; the relay never sees them. Signalling runs over **WSS (TLS)** so SDP/ICE are also protected from network observers.

## 4. Pairing (Q2 = C: QR + manual code)

1. The camera generates a **128-bit random secret** (`SecureRandom`) on first use.
2. The **signalling room id is derived one-way** from the secret (`HMAC(secret, "room-v1")`, 128 bits). Only the secret is transferred; the relay learns the room id but nothing about the secret.
3. The secret is shown on the camera as a **QR code** (`jiandie://pair?v=1&s=…`) and as a **28-character manual code** (`ABCD-EFGH-…`, copy/paste or type). The viewer scans or enters it once and stores it.
4. The secret travels **out-of-band** (screen → viewer). It is never sent over any network.

## 5. Credential storage

Secrets live in **EncryptedSharedPreferences** backed by the **Android Keystore**. Nothing in source, logs, or plaintext prefs; no hardcoded credentials anywhere (app or server). Android `allowBackup="false"`, so pairing secrets are not copied to cloud backups.

## 6. Revocation

"Unpair / re-pair" generates a **new secret**, which also moves the room (room derives from the secret). Old viewers' tokens and signatures stop verifying immediately and they cannot even reach the new room. Re-pair the viewer to resume.

## 7. Privacy posture

- **No public feed:** media only flows to a peer that proves possession of the pairing secret.
- **No storage:** live-only; nothing recorded or uploaded. No analytics, no third-party SDKs that phone home.
- **OS transparency respected:** Android's camera/mic indicators and the foreground-service notification are shown and never suppressed. The camera app displays its own live/transmitting state. Starting capture on a remote request still shows the notification and indicators.

## 8. Relay hardening

Validates message shape and size (64 KiB cap), enforces one camera + one viewer per room, rooms are ephemeral and stored nowhere, WSS only. If a TURN service is used: time-limited credentials only (never a static password in the app), deny private/loopback peer ranges.

## 9. Open items

- Confirm Keystore behaviour on the Realme 6 (Android 10/11) during Stage 3/6 device testing.
- Optional later hardening: a per-device pairing (one secret per viewer) so a single viewer can be revoked without re-pairing all; not needed for a single-viewer family setup.
