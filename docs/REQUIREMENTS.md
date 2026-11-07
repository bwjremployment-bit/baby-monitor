# REQUIREMENTS.md — Requirements Specification

Status key: **MUST** (required), **SHOULD** (strongly wanted), **MAY** (optional).
Verification: how each will be shown true in Stage 6.

## A. System-level

| ID | Requirement | Level | Verify |
|----|-------------|-------|--------|
| S1 | Transmitter on home Wi-Fi streams live video+audio to a receiver on a *different* network (mobile data), across the internet | MUST | End-to-end test, two networks |
| S2 | No dependency on same Wi-Fi, Bluetooth, local IP, or LAN discovery | MUST | Code review + remote test |
| S3 | Media encrypted end-to-end in transit | MUST | WebRTC DTLS-SRTP verified; wireshark spot-check |
| S4 | Only a paired, authenticated receiver can connect | MUST | Attempt unauthorised join → rejected |
| S5 | No public/unauthenticated feed; no unnecessary media storage | MUST | Code review |
| S6 | Zero paid services/infra at runtime | MUST | Dependency + infra audit |
| S7 | Auto-reconnect after network drop on both ends | MUST | Kill/restore network test |

## B. Jiàndié Camera (transmitter)

| ID | Requirement | Level | Verify |
|----|-------------|-------|--------|
| C1 | Camera preview on main screen; honest live/streaming indicator always visible when transmitting | MUST | Manual UI |
| C2 | Front/back camera switch | MUST | Manual |
| C3 | Flash/torch control where hardware supports it | SHOULD | Manual on capable device |
| C4 | Microphone capture + transmit | MUST | Audio heard on viewer |
| C5 | Transmission config isolated in a dedicated settings section (the "Radius" section) — start/stop, pairing, receiver auth, A/V config, connection info, diagnostics, errors | MUST | Manual UI |
| C6 | Continues transmitting with app backgrounded, screen off, phone locked — via a foreground service with camera+microphone type | MUST | Background test on device |
| C7 | Manual start; optional auto-start on boot where OS allows | MUST (manual) / SHOULD (auto) | Manual + boot test |
| C8 | Viewer may remotely request transmission to start when the camera phone is powered, connected to signalling, and in a "standby/armed" state the user enabled | SHOULD | Remote-start test |
| C9 | No notification/UI sound emitted by the app in the room | MUST | Manual (silent) |
| C10 | Respects Android privacy indicators + foreground-service notification (does NOT suppress them) | MUST | Code review |
| C11 | Network monitoring, transmission lifecycle, reconnection, diagnostics/errors surfaced in Radius | MUST | Manual + fault injection |

## C. Yps Viewer (receiver)

| ID | Requirement | Level | Verify |
|----|-------------|-------|--------|
| V1 | Opens straight to live view (minimal UI) | MUST | Manual |
| V2 | Live video playback | MUST | End-to-end |
| V3 | Live audio playback | MUST | End-to-end |
| V4 | Connection status + reconnection status shown | MUST | Manual |
| V5 | Full-screen viewing; handles rotation | MUST | Manual |
| V6 | Auto-reconnect + manual reconnect button (Q4=C) | MUST | Fault injection |
| V7 | Secure pairing (QR + manual code, Q2=C) | MUST | Pairing test |
| V8 | Clear handling of invalid credentials / connection errors | MUST | Negative test |
| V9 | No ads, subscriptions, social, recording library, AI, accounts beyond pairing | MUST | Code review |

## D. Non-functional

| ID | Requirement | Level | Target |
|----|-------------|-------|--------|
| N1 | Glass-to-glass latency | SHOULD | < ~1 s on good networks (balanced profile, Q5=C) |
| N2 | Battery: transmitter sustainable overnight on a plugged-in phone | MUST | Should assume charger in room; measure drain unplugged |
| N3 | Android compatibility | MUST | minSdk pinned to spare phone (TBD); target recent SDK |
| N4 | Reconnect within seconds of network restoration | SHOULD | Measure |
| N5 | Thermal: no shutdown over long runs on test devices | SHOULD | Long-run test |

## E. Out of scope (explicitly not built)

- Covert/disguised operation, hidden streaming, suppression of OS privacy indicators.
- Cloud recording, media libraries, multi-camera dashboards, user accounts/profiles beyond device pairing, analytics, ads.
