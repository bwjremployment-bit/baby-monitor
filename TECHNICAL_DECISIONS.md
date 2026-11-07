# TECHNICAL_DECISIONS.md

Each decision: context → options → choice → rationale → fallback. Revisit only with reason.

## TD-001 — Language & UI toolkit
- **Choice:** Kotlin. UI: Jetpack Compose for new screens; interop with Views where a WebRTC `SurfaceViewRenderer` is needed (it's a View, embedded via `AndroidView`).
- **Why:** Kotlin is the modern Android standard; Compose speeds up the simple viewer UI and the Radius settings. WebRTC renderers are Views, so a thin interop layer is expected.
- **Fallback:** classic Views/XML if a Compose+WebRTC interop issue appears.

## TD-002 — WebRTC library
- **Options:** old `org.webrtc:google-webrtc` (stale/unpublished) · `io.github.webrtc-sdk:android` (maintained, Maven Central, free) · `io.getstream:stream-webrtc-android` (GetStream, free).
- **Choice:** `io.github.webrtc-sdk:android`.
- **Why:** free, on Maven Central, tracks upstream, minSdk 21, widely used.
- **Fallback:** GetStream `stream-webrtc-android` (same API surface) if versioning/ABI issues arise.

## TD-003 — Camera capture
- **Choice:** WebRTC `Camera2Capturer` feeding the local `VideoTrack`; local preview renders that same track via `SurfaceViewRenderer`.
- **Why:** the camera is a single-access resource; opening it once for both preview and stream avoids conflicts and keeps capture alive in the foreground service. CameraX may be used only for the QR-scan screen in the viewer (not for the streaming capture).
- **Fallback:** custom Camera2 → `VideoFrame` pipe if `Camera2Capturer` misbehaves on a device.

## TD-004 — Background execution
- **Choice:** Foreground service, `foregroundServiceType="camera|microphone"`, with `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CAMERA`, `FOREGROUND_SERVICE_MICROPHONE`, `POST_NOTIFICATIONS` (Android 13+), `CAMERA`, `RECORD_AUDIO`, `WAKE_LOCK`, `INTERNET`, `ACCESS_NETWORK_STATE`. Request battery-optimisation exemption.
- **Why:** only supported way to keep camera+mic capturing with the app backgrounded / screen off / locked, while complying with Android 14+ FGS rules.
- **Note:** auto-start on boot and viewer-remote-start are best-effort under Android 14+ background-FGS-launch limits (R4).

## TD-005 — Signalling transport & server
- **Choice:** JSON over **WSS**; a small self-written WebSocket relay (Node `ws` or Deno), stateless, ephemeral rooms, token-gated.
- **Why:** minimal, portable, free to host anywhere, carries no media, easy to audit.
- **Fallback:** if WS hosting proves awkward, a long-poll HTTP fallback — avoided unless needed.

## TD-006 — STUN/TURN
- **Choice:** public free STUN (Google) + optionally self-hosted coturn; **TURN via self-hosted coturn at home** when NAT requires it, with a free public TURN tier as best-effort fallback. Time-limited HMAC credentials (`use-auth-secret`).
- **Why:** only genuinely zero-cost robust TURN path; no static secrets shipped.
- **Depends on:** OQ-1 (public IP / port-forward / carrier).

## TD-007 — Pairing & crypto
- **Choice:** out-of-band pairing secret via QR + manual code; HMAC-derived time-limited auth tokens; EncryptedSharedPreferences + Android Keystore; revocation by rotating room/secret.
- **Why:** secure without any server-side account system; standard, free, offline.

## TD-008 — QR
- **Choice:** generate with ZXing; scan with ML Kit barcode (offline) or ZXing.
- **Why:** free, offline, no network/privacy cost.

## TD-009 — Build & compatibility
- **Choice:** Gradle (Kotlin DSL) + version catalog. targetSdk = recent (35); **minSdk pinned to the spare phone** once known (OQ-3); provisional minSdk 26 (Android 8) pending that info.
- **Why:** modern build; widest practical reach (Q1=D) anchored to the actual device.

## TD-010 — Testing approach
- **Choice:** JVM unit tests (signalling message schema, pairing/crypto, state machine) runnable in this cloud env; instrumented/device tests + real end-to-end run on the user's phones (guided). Clearly label simulated vs on-device (Stage 6).
- **Why:** this cloud box has no device; unit-test the device-independent logic here, validate hardware/network behaviour on real phones.
