# RISK_REGISTER.md

Likelihood/Impact: L/M/H. Status: Open / Mitigated / Accepted / Needs-user-input.

| ID | Risk | L | I | Mitigation | Status |
|----|------|---|---|-----------|--------|
| R1 | Both ends behind cellular CGNAT; home TURN not self-hostable. **Downgraded** by on-demand usage (≤~48 h/mo): free TURN tier (~20 GB/mo) now sufficient as fallback; IPv6 direct = zero relay data | M | M | Prefer IPv6 direct (OQ-4); else free hosted TURN tier (no card) — fits the usage. Keep bitrate modest | **Mitigated (usage profile) / confirm OQ-4** |
| R2 | Sandbox egress blocks dl.google.com → no Android SDK **and** no Google Maven (AGP/AndroidX) here; APKs cannot be compiled in-sandbox | H | M | **APK builds run on free GitHub Actions CI** (SDK + internet); `shared` + `signaling-server` build/test in-sandbox; on-device tests on user phones. Verified: only AGP resolution is blocked, scripts parse | **Mitigated (CI)** |
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

- **OQ-1 (resolved):** Home internet is LTE/CGNAT (Sagemcom 5366 LTE) → no public IPv4, no home-TURN. Superseded by OQ-4.
- **OQ-4 (new, important):** Do the home LTE carrier and the viewer's mobile carrier provide **public IPv6**? (If yes → free direct connection, no TURN needed.) Name the carrier(s).
- **OQ-2 (resolved):** Signalling relay → Deno Deploy free tier (no card). User creates the login; I create no accounts.
- **OQ-3 (resolved):** Camera phone = Realme 6 (Android 10/11) → minSdk 29. Viewer phone Android version still to confirm (minor).
