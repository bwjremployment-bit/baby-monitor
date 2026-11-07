# CONNECTIVITY_DESIGN.md — Real-time connectivity

## 1. Why WebRTC

Compared against the viable zero-cost options:

| Option | Live A/V | P2P / low-latency | Encrypted by default | Android support | Zero-cost | Verdict |
|--------|----------|-------------------|----------------------|-----------------|-----------|---------|
| **WebRTC** | yes | yes (P2P, sub-second) | yes (DTLS-SRTP, mandatory) | mature prebuilt libs | yes (self-host signalling/TURN) | **Chosen** |
| RTSP/RTP (self) | yes | LAN-oriented; NAT traversal is DIY | no (plain) unless add SRTP | libs exist | yes | Rejected: NAT traversal + security are exactly what WebRTC already solves |
| MJPEG / HTTP stream | video only, high latency | server-pull, no P2P | only via TLS | easy | needs a reachable server | Rejected: no audio sync, high latency, needs public server |
| HLS/DASH | yes | 5–30 s latency | TLS | easy | needs server + storage | Rejected: far too much latency; segment storage |
| Commercial SDKs (Agora/Twilio/etc.) | yes | yes | yes | yes | **no (paid)** | Rejected: violates zero-cost |

WebRTC is purpose-built for exactly this (live, low-latency, P2P, encrypted) and its only cost items (signalling, STUN, TURN) all have genuine zero-cost paths.

## 2. WebRTC library choice

Google's old `org.webrtc:google-webrtc` prebuilt is unpublished/stale. Free, maintained prebuilts on Maven Central:
- **`io.github.webrtc-sdk:android`** (webrtc-sdk.org) — chosen. Tracks upstream, Maven Central, no cost, minSdk 21.
- `io.getstream:stream-webrtc-android` (GetStream) — strong fallback if the above has an issue.

Decision recorded in TECHNICAL_DECISIONS.md (TD-002). Fallback documented so Stage 3 is not blocked by a single library.

## 3. Signalling

Purpose: exchange SDP offer/answer + ICE candidates, and gate room entry by auth token. Tiny, bursty traffic only during connection setup + reconnects. **Never carries media.**

Message schema (JSON over WSS): `join{room, role, token}` → `joined{peers}` / `auth-error`; `offer{sdp}`, `answer{sdp}`, `ice{candidate}`, `bye`, plus `start-request` (viewer→camera, for Q3 remote start).

**Server:** ~150-line Node (`ws`) or Deno WebSocket relay that we write and own. Rooms are ephemeral; it stores nothing persistent.

**Zero-cost hosting options (user chooses — see open question OQ-2):**
1. **Home machine / Raspberry Pi / the spare PC** on the home network, exposed via the router, or via a free tunnel (e.g. `cloudflared` free tunnel) — fully self-owned, no third party.
2. **Deno Deploy free tier** — WebSocket support, GitHub login, **no credit card**.
3. **Cloudflare Workers + Durable Objects free tier** — WebSocket hibernation, no card for the free plan.
4. **Fly.io / Render free tiers** — possible but may require a card / sleep; lower preference.

I will **not** create any of these accounts for you. We write portable code (plain WS) that runs on any of them or on a home box.

## 4. STUN

- Default: public free STUN (e.g. `stun:stun.l.google.com:19302`) — stateless, long-standing, free.
- Self-host option: `coturn` also serves STUN, if you prefer no third party.
- Multiple STUN URLs configured for resilience.

## 5. TURN — the one hard zero-cost problem

TURN relays media when direct P2P fails. Direct P2P succeeds in most home-Wi-Fi ↔ mobile-data cases, **but mobile carriers frequently use CGNAT (symmetric NAT)**, which can force a TURN relay. There is no reliably-free *public* TURN we can depend on.

Zero-cost paths, in preference order:
1. **Self-hosted `coturn` at home** — free software. Works **if** the home router has a public IPv4 and you can port-forward (or the ISP gives a static/public IP). This is the most robust zero-cost TURN. Needs: open UDP/TCP 3478 (+ TLS 5349) and a relay port range. Credentials via coturn's time-limited HMAC (`use-auth-secret`) so no static passwords ship in the app.
2. **coturn on the same free host** as signalling (Fly/Deno are not ideal for TURN's port ranges; a small free-tier VM elsewhere may be needed — flag if chosen).
3. **Free public TURN tiers** (e.g. Open Relay / metered free tier) — usable as a *fallback* but availability/limits are outside our control; documented as best-effort, not a dependency.

**Key open question for you (OQ-1):** Does your home internet give you a public IP and can your router forward ports? And which mobile carrier is the viewer phone on? This decides whether self-hosted TURN at home is viable, or whether we rely on direct P2P + a free TURN fallback. Stage 1 cannot resolve this without that info.

## 6. NAT traversal flow

ICE gathers host + server-reflexive (STUN) + relay (TURN) candidates on both ends, exchanged via signalling, and picks the best working pair. Trickle ICE for speed. If all direct pairs fail and no TURN is available, the connection cannot form — this is the scenario TURN exists to cover, hence OQ-1 matters.

## 7. Reconnection

- Transmitter: on network loss, keep capture alive, tear down the dead PeerConnection, rejoin signalling on restore, renegotiate. Exponential backoff.
- Viewer: same, plus visible status (Q4=C) and a manual Reconnect button.
- Signalling server treats a dropped socket as "peer left"; the surviving peer waits in-room for rejoin.

## 8. Honest latency/quality note (Q5=C balanced)

Default encoding: VP8/H.264 video at a moderate resolution/bitrate (e.g. 640×480–1280×720, adaptive), Opus audio. WebRTC's congestion control (GCC) adapts bitrate to the link. Balanced profile favours a stable sub-second feed over maximum sharpness, and reduces battery/thermal load on an older transmitter phone.
