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

Message schema (JSON over WSS): `join{room, role}` → `joined{peers}` / `auth-error`; `peer{role, present}`; `offer{sdp, auth}`, `answer{sdp, auth}`, `ice{candidate}`, `bye`, plus `start-request{token}` (viewer→camera, Q3 remote start). `token` and `auth` are end-to-end HMACs verified by the *peer*, not the relay (see SECURITY_DESIGN §2).

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

> **Superseded by §5a:** home is LTE/CGNAT, so option 1 below (home coturn) is **not viable**. Kept for the record.

Zero-cost paths, in preference order (original analysis):
1. **Self-hosted `coturn` at home** — free software. Works **if** the home router has a public IPv4 and you can port-forward (or the ISP gives a static/public IP). This is the most robust zero-cost TURN. Needs: open UDP/TCP 3478 (+ TLS 5349) and a relay port range. Credentials via coturn's time-limited HMAC (`use-auth-secret`) so no static passwords ship in the app.
2. **coturn on the same free host** as signalling (Fly/Deno are not ideal for TURN's port ranges; a small free-tier VM elsewhere may be needed — flag if chosen).
3. **Free public TURN tiers** (e.g. Open Relay / metered free tier) — usable as a *fallback* but availability/limits are outside our control; documented as best-effort, not a dependency.

**Key open question for you (OQ-1):** Does your home internet give you a public IP and can your router forward ports? And which mobile carrier is the viewer phone on? This decides whether self-hosted TURN at home is viable, or whether we rely on direct P2P + a free TURN fallback. Stage 1 cannot resolve this without that info.

## 5a. UPDATE (2026-11-07): home internet is LTE — this is the crux

The home modem is a **Sagemcom 5366 LTE** → home internet is **cellular LTE**, behind **CGNAT** (no public IPv4, no port-forwarding). The viewer is also on mobile data. So **both peers are typically behind carrier-grade symmetric NAT** — the hardest case for P2P.

Implications, in order of preference for a zero-cost working connection:

1. **IPv6 direct (best free path).** Many LTE carriers assign a **public IPv6** address per device with no NAT. If *both* the home LTE and the viewer's carrier provide IPv6, WebRTC ICE can connect directly over IPv6 host candidates — **no TURN, no cost, low latency.** We will prioritise gathering and trying IPv6 candidates. **Depends on OQ-4** (carrier IPv6 support).
2. **IPv4 STUN direct.** May still work if one side has a cone NAT; unreliable when both are symmetric CGNAT. We try it, but don't count on it.
3. **Publicly-hosted TURN relay (fallback when 1 & 2 fail).** coturn **cannot** be self-hosted at home now (CGNAT makes home unreachable). A publicly-reachable TURN host is required. Zero-cost options are constrained:
   - Free TURN tiers (e.g. metered.ca Open Relay): work, but cap monthly GB — **too small for continuous overnight streaming every night** (continuous A/V ≈ 0.5–1.5 GB/hour). Fine for intermittent checks, not 24/7.
   - A genuinely-free always-on VM with a public IP + large egress (e.g. Oracle Cloud "Always Free") could host coturn at no ongoing cost, **but signup requires a card for identity verification**, which conflicts with the strict no-card rule. **User decision needed** if IPv6 is unavailable.

**Honest conclusion:** if carrier IPv6 is available on both ends (OQ-4), we get a free, direct, low-latency connection and TURN is unnecessary. If it is not, continuous zero-cost 24/7 remote viewing runs into a real infrastructure limit; we then either (a) accept a free TURN tier with a usage cap (intermittent viewing), or (b) the user opts into a free-but-card-verified VM for self-hosted TURN. We will not pretend this limit away.

## 6. NAT traversal flow

ICE gathers host + server-reflexive (STUN) + relay (TURN) candidates on both ends, exchanged via signalling, and picks the best working pair. Trickle ICE for speed. If all direct pairs fail and no TURN is available, the connection cannot form — this is the scenario TURN exists to cover, hence OQ-1 matters.

## 7. Reconnection

- Transmitter: on network loss, keep capture alive, tear down the dead PeerConnection, rejoin signalling on restore, renegotiate. Exponential backoff.
- Viewer: same, plus visible status (Q4=C) and a manual Reconnect button.
- Signalling server treats a dropped socket as "peer left"; the surviving peer waits in-room for rejoin.

## 8. Honest latency/quality note (Q5=C balanced)

Default encoding: VP8/H.264 video at a moderate resolution/bitrate (e.g. 640×480–1280×720, adaptive), Opus audio. WebRTC's congestion control (GCC) adapts bitrate to the link. Balanced profile favours a stable sub-second feed over maximum sharpness, and reduces battery/thermal load on an older transmitter phone.
