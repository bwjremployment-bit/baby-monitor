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
- **Host choice:** user has no preference → **Deno Deploy** free tier (WebSocket, no credit card). Portable to a home machine or Cloudflare if preferred.
- **Why:** minimal, portable, free to host anywhere, carries no media, easy to audit.
- **Fallback:** if WS hosting proves awkward, a long-poll HTTP fallback — avoided unless needed.

## TD-006 — STUN/TURN (revised 2026-11-08)
- **Context:** home internet is LTE (Sagemcom 5366) → CGNAT; home-hosted TURN is impossible. Usage is on-demand (≤~4 h, ~3×/week ≈ ≤48 h/month).
- **Choice:** public free STUN (Google) first; **prefer IPv6 direct** (zero relay data) when both carriers provide it; otherwise a **free hosted TURN tier** with time-limited credentials as fallback. ~6.5–13 GB/month worst case fits a ~20 GB/month free tier.
- **Why:** no card, no self-hosting, fits the usage. 24/7 streaming would NOT fit; the on-demand model is what makes it work.
- **Depends on:** OQ-4 (carrier IPv6 support) to know whether TURN is needed at all.

## TD-007 — Pairing & crypto (revised 2026-11-08)
- **Choice:** single 128-bit secret transferred out-of-band (QR + 28-char manual code); room id derived one-way from the secret; **peer-to-peer authentication** (viewer start-request token verified by the camera; HMAC over offer/answer SDP binding the DTLS fingerprint); Keystore-backed EncryptedSharedPreferences; revocation = new secret (moves the room).
- **Why:** the relay is untrusted third-party infrastructure; it must be unable to mint credentials, inject sessions or MITM media. Short code is actually typeable. See SECURITY_DESIGN §2.
- **Verified:** 34 unit tests incl. tampered-fingerprint, role-reflection, wrong-secret, window-expiry.

## TD-008 — QR
- **Choice:** generate with ZXing; scan with ML Kit barcode (offline) or ZXing.
- **Why:** free, offline, no network/privacy cost.

## TD-009 — Build & compatibility
- **Choice:** Gradle (Kotlin DSL) + version catalog. targetSdk = recent (35); **minSdk pinned to the spare phone** once known (OQ-3); **minSdk 29 (Android 10)** — camera phone is a Realme 6 (ships Android 10, max 11).
- **Why:** modern build; widest practical reach (Q1=D) anchored to the actual device.

## TD-010 — Testing approach
- **Choice:** JVM unit tests (signalling message schema, pairing/crypto, state machine) runnable in this cloud env; instrumented/device tests + real end-to-end run on the user's phones (guided). Clearly label simulated vs on-device (Stage 6).
- **Why:** this cloud box has no device; unit-test the device-independent logic here, validate hardware/network behaviour on real phones.

## TD-011 — Android networking/concurrency libraries
- **Choice:** OkHttp 4.12 (WebSocket transport for signalling) + kotlinx-coroutines. Both free, Maven Central, ubiquitous.
- **Why:** OkHttp's WebSocket is robust on Android (ping/pong, TLS, proxies); avoids hand-rolling a socket client.

## TD-012 — Build split & where compilation happens
- **Choice:** `shared/` is a standalone JVM Gradle build (testable anywhere); apps consume it via `includeBuild`; APKs are built on GitHub Actions.
- **Why:** sandbox egress blocks dl.google.com (Android SDK + Google Maven). Verified Stage 2; first CI run failed only on a setup-android quirk (removed `tools` package), fixed.
