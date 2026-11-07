# Baby Monitor — Jiàndié Camera + Yps Viewer

Two Android apps forming one remote baby monitor: **Jiàndié Camera** (transmitter, in the baby's room on home Wi-Fi) streams live video + audio over the internet via **WebRTC** to **Yps Viewer** (receiver, on mobile data anywhere). Honest monitor — always shows when it is transmitting. Zero paid services.

See `MASTER_PROJECT.md` for the overview and `docs/` for the full design.

## Repository layout

| Path | What | Builds where |
|------|------|--------------|
| `shared/` | Pure-Kotlin library: signalling protocol, pairing/crypto, connection state machine | Anywhere (JVM) — `cd shared && ./gradlew test` |
| `camera/` | Jiàndié Camera Android app | CI / Android Studio |
| `viewer/` | Yps Viewer Android app | CI / Android Studio |
| `signaling-server/` | Zero-knowledge WebSocket relay (Node) | Anywhere — `cd signaling-server && npm test` |
| `.github/workflows/android.yml` | Free CI: builds both APKs + runs all tests | GitHub Actions |

## Why CI builds the APKs

The development sandbox's egress policy blocks `dl.google.com`, which serves both the Android SDK and Google's Maven repo (AGP, AndroidX). So Android compilation happens on **GitHub Actions** (free for public repos; SDK preinstalled, full internet) or in **Android Studio** on your own machine. The device-independent `shared` and `signaling-server` build and test anywhere, including the sandbox.

## Build locally (Android Studio)
Open the repo root in Android Studio (Giraffe+). It builds `:camera` and `:viewer`; `shared` is consumed as an included build. Run configs produce debug APKs.

## Build via CI
Push to GitHub → the `build` workflow runs tests and uploads `jiandie-camera-debug` and `yps-viewer-debug` APK artifacts. Download + sideload to the phones.

## Status
Stage 2 (project foundation) complete. `shared` (28 tests) and `signaling-server` (2 tests) pass in the sandbox; the Android APK build is wired and parses, pending its first CI run. See `CURRENT_PROGRESS.md`.
