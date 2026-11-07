# NEXT_SESSION_INSTRUCTIONS.md

## At the start of the next session
1. Read `CURRENT_PROGRESS.md` then this file.
2. Verify against the actual repo (don't trust docs alone): `ls -R`, `git log --oneline`, confirm which files/modules exist before continuing.
3. Do **not** redo Stage 1. Do **not** start Stage 2 until the user says go **and** the three OQs below are answered (at least OQ-3; OQ-1/2 can be refined during Stage 2 but are needed by Stage 5).

## Required inputs from the user (ask if not already given)
- **OQ-3 (needed to pin minSdk, do first):** spare/camera phone make + model + Android version (Settings → About phone → Android version); viewer phone Android version.
- **OQ-2 (needed by Stage 5, decide early):** where to host the tiny signalling relay — home machine/Raspberry Pi, Deno Deploy (free, no card), Cloudflare Workers (free, no card), or other. Do not create accounts for the user.
- **OQ-1 (needed by Stage 5):** does home internet have a public IP and can the router forward ports (for self-hosted coturn TURN)? which mobile carrier is the viewer phone on (CGNAT likelihood)?

## Exact first tasks of Stage 2 (once cleared)
1. Install Android cmdline SDK tools (free): download `commandline-tools`, set `ANDROID_HOME`, `sdkmanager --licenses`, install `platform-tools`, `platforms;android-35`, `build-tools;35.0.0`. Record exact steps + versions in TECHNICAL_DECISIONS.md.
2. Create Gradle root (`settings.gradle.kts`, `gradle/libs.versions.toml`, wrapper) + modules `core`, `camera`, `viewer`, and non-Android `signaling-server`.
3. Set applicationIds (e.g. `com.family.jiandie.camera`, `com.family.yps.viewer`), minSdk = device version (OQ-3), targetSdk 35, Kotlin, Compose, WebRTC + AndroidX deps via catalog.
4. Scaffold `core`: signalling message types + WS client interface, pairing/crypto stubs, connection state machine, WebRTC factory helper.
5. `assembleDebug` both apps successfully; commit.
6. Update CURRENT_PROGRESS / TEST_REGISTER (T-004) with real build output.

## Reminders
- Zero cost; no account creation on the user's behalf; no covert features.
- Label simulated vs on-device results honestly. Never claim success without evidence.
- Keep tracking docs updated as work lands.
