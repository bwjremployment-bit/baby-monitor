# PROJECT_ARCHITECTURE.md — System & Application Architecture

## 1. System overview

```
   BABY'S ROOM (home Wi-Fi)                                 ANYWHERE (mobile data)
 ┌───────────────────────────┐                          ┌───────────────────────────┐
 │      Jiàndié Camera        │                          │        Yps Viewer          │
 │  (transmitter, Android)    │                          │   (receiver, Android)      │
 │                            │                          │                            │
 │  CameraX/Camera2 capture   │                          │  SurfaceViewRenderer       │
 │  Mic capture               │                          │  AudioTrack playback       │
 │  WebRTC PeerConnection ────┼───── DTLS-SRTP media ─────┼──── WebRTC PeerConnection  │
 │  Foreground service        │       (P2P, direct        │                            │
 │  (camera+microphone type)  │        or via TURN)       │                            │
 └──────────┬─────────────────┘                          └──────────────┬─────────────┘
            │                                                            │
            │  WSS (signalling: SDP offer/answer + ICE)                  │
            └──────────────────────┬──────────────────────┬─────────────┘
                                   │                      │
                          ┌────────▼──────────┐   ┌───────▼─────────┐
                          │  Signalling relay  │   │   STUN server   │
                          │ (tiny WS server,   │   │ (public free or │
                          │  self/free-hosted) │   │  self-hosted)   │
                          └────────────────────┘   └─────────────────┘
                                                    (TURN only if NAT requires — see
                                                     CONNECTIVITY_DESIGN.md)
```

- **Media path:** peer-to-peer WebRTC. Encrypted by DTLS-SRTP (mandatory in WebRTC). Never touches the signalling server.
- **Signalling path:** a small WebSocket relay carries only connection-setup messages (session description + ICE candidates) between the two paired devices within a "room". It never sees media and holds no media.
- **STUN:** lets each peer learn its public address for NAT traversal. Stateless and cheap.
- **TURN:** relay fallback used only when direct P2P is impossible (symmetric NAT / CGNAT). The one component with a real zero-cost challenge — see CONNECTIVITY_DESIGN.md.

## 2. Project / module layout (as built in Stage 2)

```
babymonitor/                       Gradle root (settings.gradle.kts, version catalog)
├── shared/                        STANDALONE pure-Kotlin build — consumed via includeBuild
│   ├── signaling/                 message protocol (sealed SignalMessage + JSON codec)
│   ├── pairing/                   PairingPayload (QR URI + manual code), Base32,
│   │                              PairingCrypto (room derivation, tokens, SDP MACs)
│   └── state/                     ConnectionState + deterministic state machine
│   (buildable + unit-testable anywhere with just a JDK; 34 tests)
├── camera/                        Android app — Jiàndié Camera (com.family.jiandie.camera)
├── viewer/                        Android app — Yps Viewer (com.family.yps.viewer)
├── signaling-server/              Node `ws` zero-knowledge relay (free-hostable)
└── .github/workflows/android.yml  CI: tests + builds both APKs
```

Why `shared/` is a separate build: the development sandbox cannot reach Google's Maven (Android Gradle Plugin / AndroidX), so the protocol, pairing and crypto code is kept free of Android so it can be built and tested anywhere. The apps consume it as `com.family.monitor:shared` via `includeBuild`. APKs are compiled on GitHub Actions. Android-specific code (WebRTC, OkHttp transport, Keystore storage, services, UI) lives in the app modules.

Both apps are **independently installable APKs** speaking the identical protocol from `shared/`, which is what keeps them two halves of one system.

## 3. Jiàndié Camera (transmitter) architecture

- **UI (Kotlin, Jetpack Compose or Views):** camera preview + persistent LIVE indicator; standard camera affordances (switch, flash); a **Radius** settings screen holding all transmission concerns.
- **Capture:** WebRTC `Camera2Capturer` → local `VideoTrack`; `AudioDeviceModule` → local `AudioTrack`. Local preview renders the same video track via `SurfaceViewRenderer` (so the camera UI and the stream share one camera session — the camera is a single-access resource, so we do not open it twice).
- **TransmissionService:** a **foreground service** (`foregroundServiceType="camera|microphone"`, with the matching `FOREGROUND_SERVICE_CAMERA` / `FOREGROUND_SERVICE_MICROPHONE` permissions on Android 14+). Owns the capturer + PeerConnection so capture survives the Activity being backgrounded, screen off, phone locked. Shows the mandatory ongoing notification.
- **Lifecycle & recovery:** network callback (`ConnectivityManager`) → on loss, hold; on restore, re-run ICE / rejoin signalling. State machine: `IDLE → ARMED → CONNECTING → LIVE → RECONNECTING → …`.
- **Boot/auto-start (optional, Q3=C):** `BOOT_COMPLETED` receiver may re-arm where OS permits; subject to Android 14+ restrictions on launching camera/mic foreground services from background/boot — documented, not promised on every device.
- **Remote start (Q3, viewer-initiated):** when user has set the camera to "Armed/standby", the phone keeps a lightweight signalling presence; a paired viewer's authenticated start request transitions `ARMED → LIVE`. (If the OS forbids starting a camera foreground service from the background on a given version, this degrades to "camera stays live while armed" — documented per device.)

## 4. Yps Viewer (receiver) architecture

- **UI:** opens to live view; `SurfaceViewRenderer` for remote video, WebRTC audio auto-plays; status bar (Connecting / Live / Reconnecting / Error) + manual Reconnect; full-screen + rotation.
- **Pairing:** scan QR (CameraX + ML Kit barcode, or ZXing — all free/offline) **or** enter the manual code; stores the pairing secret in the Keystore-backed store.
- **Connection:** joins the same signalling room, authenticates with the paired token, receives the offer (or sends one — role TBD in Stage 5), renders remote tracks. Auto-reconnect with backoff + manual override.

## 5. Shared connection state machine (core/model)

`DISCONNECTED → SIGNALING_CONNECTING → SIGNALING_READY → PEER_CONNECTING → LIVE → PEER_RECONNECTING → (LIVE | FAILED)` with explicit `AUTH_FAILED` terminal. Both apps consume the same enum + transitions so status display and reconnection behave identically.

## 6. Technology summary (detail + rationale in TECHNICAL_DECISIONS.md)

Kotlin · WebRTC (`io.github.webrtc-sdk:android` prebuilt, free, Maven Central) · Camera2Capturer/CameraX · foreground service · self-written WS signalling relay · public or self-hosted STUN · TURN only if required (self-hosted coturn or free tier) · Android Keystore / EncryptedSharedPreferences · QR via ML Kit/ZXing.
