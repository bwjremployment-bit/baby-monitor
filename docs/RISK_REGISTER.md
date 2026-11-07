# RISK_REGISTER.md

Likelihood/Impact: L/M/H. Status: Open / Mitigated / Accepted / Needs-user-input.

| ID | Risk | L | I | Mitigation | Status |
|----|------|---|---|-----------|--------|
| R1 | **Mobile-carrier CGNAT/symmetric NAT blocks direct P2P**, needing TURN, which has no free *public* guarantee | H | H | Self-host coturn at home (free) if public IP + port-forward available; free TURN tier as best-effort fallback; direct P2P works in many cases | **Needs-user-input (OQ-1)** |
| R2 | No Android SDK in this cloud env; no physical device reachable here | H | M | Install free Google cmdline SDK tools in Stage 2 for builds/unit tests; on-device tests done by user on real phones with guided steps | Mitigated (plan) |
| R3 | WebRTC prebuilt library drift/unavailability | M | M | Use maintained `io.github.webrtc-sdk:android`; documented fallback to GetStream build (TD-002) | Mitigated |
| R4 | Android 14+ restricts starting camera/mic foreground services from background/boot → auto-start (Q3) + viewer-remote-start (C8) may not work on all versions | M | M | Manual start is primary + always works; auto/remote-start declared best-effort per OS version; keep-alive "armed" presence; document per-device | Open |
| R5 | Battery/thermal on an **older** transmitter phone during long runs | M | M | Balanced encode profile (Q5=C); assume charger in room; measure drain; lower resolution/fps option in Radius | Open |
| R6 | OEM battery-optimisation / Doze killing the foreground service on some brands (Xiaomi/Huawei/Samsung aggressive killers) | M | H | Request battery-optimisation exemption; persistent foreground service; document per-OEM steps; auto-restart | Open |
| R7 | Signalling host free-tier limits/sleeping | M | M | Traffic is tiny/bursty; choose a tier that wakes on connect, or self-host at home; no media through it | **Needs-user-input (OQ-2)** |
| R8 | Camera single-access conflict (preview vs stream vs torch while capturing) | M | M | Single capture session feeds both preview + stream; torch controlled through the active session; document devices where torch-while-streaming is limited | Open |
| R9 | Spare-phone Android version unknown → wrong minSdk / missing APIs | M | M | Pin minSdk to the actual device once known | **Needs-user-input (OQ-3)** |
| R10 | Misuse of a camera-streaming app for covert surveillance | L | H | Honest-monitor design: always-visible live indicator, OS indicators + FGS notification never suppressed; covert features refused | Mitigated (by design) |
| R11 | Cannot produce signed release APK without a keystore | L | L | Generate a free local debug/release keystore in Stage 7 (no paid cert needed for sideload install) | Mitigated (plan) |
| R12 | QR/scan library cost or privacy | L | L | Use free offline ML Kit barcode or ZXing; no network | Mitigated |

## Open questions requiring user input (carried to NEXT_SESSION_INSTRUCTIONS.md)

- **OQ-1 (TURN/NAT):** Home internet public IP + can you port-forward the router? Which mobile carrier is the viewer phone on?
- **OQ-2 (signalling host):** Where to host the tiny signalling relay — home machine/Pi, Deno Deploy (free, no card), Cloudflare (free, no card), or other? (I will not create accounts for you.)
- **OQ-3 (device):** Spare (camera) phone make/model + Android version; primary (viewer) phone Android version.
