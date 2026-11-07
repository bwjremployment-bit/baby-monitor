# SECURITY_DESIGN.md — Security & privacy

## 1. Threat model (private family use)

Protect against: an unauthorised party connecting to the camera and viewing the baby's room; eavesdropping on the media in transit; a lost phone leaking pairing secrets; the signalling server being abused to join a room.

Out of scope: nation-state attackers, physical device compromise with full root. Target: strong, practical protection with zero paid infrastructure.

## 2. Media encryption

- WebRTC media is **DTLS-SRTP encrypted end-to-end by default** — not optional, cannot be turned off. Keys are negotiated per session; the signalling server never sees them.
- Signalling runs over **WSS (TLS)** so SDP/ICE cannot be read or tampered with in transit. (Self-hosted relay uses a free Let's Encrypt cert, or TLS terminated by the free host / `cloudflared` tunnel.)

## 3. Pairing (Q2 = C: QR + manual code)

1. On first run, Jiàndié Camera generates:
   - a random **room ID** (unguessable, e.g. 128-bit),
   - a **shared pairing secret** (high-entropy),
   - from these, derives a signalling **auth token** (HMAC).
2. These are packed into a pairing payload and shown as a **QR code**; a shorter human-typable **manual code** encodes the same secret (e.g. base32, grouped) as fallback.
3. Yps Viewer scans/enters it once and stores it. From then on both sides derive the same auth token.
4. The pairing secret is **never transmitted over the network** — it is carried out-of-band (screen → camera, or typed), which is what makes the pairing secure.

## 4. Connection authentication

- To join a signalling room a client must present a valid token derived from the pairing secret. The server rejects mismatches (`auth-error`) → Viewer shows "invalid/not paired" (V8).
- To defeat replay/MITM on signalling, the token is **time-limited** (HMAC over room + role + time window). coturn (if used) uses the same `use-auth-secret` time-limited-credential scheme, so no static TURN password is shipped.
- Optionally pin the expected DTLS fingerprint learned at pairing for extra assurance (Stage 5 decision).

## 5. Credential storage

- Secrets stored with **EncryptedSharedPreferences** backed by the **Android Keystore** (hardware-backed where available). No secrets in source, logs, or plaintext prefs.
- **No hardcoded credentials** anywhere in the apps or server.

## 6. Revocation (revocable pairing)

- Camera keeps a list of paired viewers (by a per-pairing id). "Unpair / revoke" rotates the room ID + secret (or removes that viewer's entry), instantly invalidating old tokens. Re-pair to reconnect.

## 7. Privacy posture

- **No public feed:** a room only exists between paired devices gated by the token; room IDs are unguessable.
- **No unnecessary storage:** media is live-only; nothing recorded or uploaded. No analytics, no third-party SDKs that phone home.
- **OS transparency respected:** Android's camera/mic active indicators and the foreground-service notification are shown, never suppressed. This is both a compliance requirement and a safety property for an honest monitor.

## 8. Server hardening

- Signalling relay: validates message shape, enforces token on `join`, rate-limits, rooms are ephemeral and auto-expire, stores nothing. Runs over WSS only.
- If self-hosting TURN/coturn: restrict relay IP ranges, enable `fingerprint`, `use-auth-secret`, deny loopback/private peers (`no-multicast-peers`, `denied-peer-ip`).

## 9. Open items for later stages

- Finalise token time-window + clock-skew tolerance (Stage 5).
- Decide whether to pin DTLS fingerprint at pairing (Stage 5).
- Confirm Keystore behaviour on the specific spare-phone Android version (Stage 3/6).
