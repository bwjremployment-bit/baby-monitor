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

## Executed
_None yet (Stage 1 produced no code to test)._
