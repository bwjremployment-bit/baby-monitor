# TEST_REGISTER.md

Columns: ID · Area · What · Type (unit/instrumented/manual-device/e2e) · Env (cloud/phone) · Result · Date.
**Rule:** every on-device/e2e result must say which physical phones + networks were used. Simulated ≠ on-device.

## Planned (Stage 2+)
| ID | Area | What | Type | Env |
|----|------|------|------|-----|
| T-001 | core/signalling | message encode/decode + schema validation | unit | cloud |
| T-002 | core/pairing | QR payload + manual-code round-trip; token derivation/verify | unit | cloud |
| T-003 | core/state | connection state-machine transitions | unit | cloud |
| T-004 | build | both app modules assemble (assembleDebug) | build | cloud |
| T-005 | camera | foreground service survives background/screen-off/lock | manual-device | phone |
| T-006 | camera | camera switch / torch | manual-device | phone |
| T-007 | viewer | pair via QR + via manual code | manual-device | phone |
| T-008 | e2e | same-network live A/V | e2e | phones (LAN) |
| T-009 | e2e | **remote** home-WiFi ↔ mobile-data live A/V (acceptance) | e2e | phones (2 networks) |
| T-010 | resilience | network drop + restore → auto-reconnect, both ends | manual-device | phones |
| T-011 | security | unauthorised/invalid token rejected | unit+e2e | cloud+phones |
| T-012 | battery/thermal | long run on transmitter | manual-device | phone |

## Executed (Stage 2, in sandbox)
| ID | What | Type | Env | Result | Date |
|----|------|------|-----|--------|------|
| T-001 | signalling message encode/decode + schema + unknown-field tolerance | unit | cloud | **PASS** (11 tests) | 2026-11-07 |
| T-002 | pairing: Base32 round-trip, URI + manual-code round-trip, token derive/verify, wrong-secret & role-bound rejection, window skew | unit | cloud | **PASS** (10 tests) | 2026-11-07 |
| T-003 | connection state-machine transitions (happy path, drop/recover, auth-fail, invalid rejected) | unit | cloud | **PASS** (7 tests) | 2026-11-07 |
| T-SIG | signalling relay: pair camera+viewer, offer/answer/ice relay, room isolation, role enforcement, peer-leave | integration | cloud | **PASS** (2 tests) | 2026-11-07 |
| T-004 | both Android app modules assembleDebug | build | **CI (pending first run)** | scripts parse; blocked only by sandbox egress, runs on GitHub Actions | 2026-11-07 |

Totals in sandbox: **40 tests, 0 failures** (shared 28 + signalling 2 integration cases covering 10 assertions). Android APK build moved to CI (sandbox cannot reach dl.google.com).
