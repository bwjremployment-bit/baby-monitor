# BUG_REGISTER.md

Columns: ID · Severity · Area · Description · Found by · Status · Fix.

## Open
_None known. (No on-device testing has happened yet — absence of bugs here is not evidence of absence.)_

## Closed
| ID | Sev | Area | Description | Found by | Status | Fix |
|----|-----|------|-------------|----------|--------|-----|
| B-001 | high | security | Docs claimed peer-to-peer auth but nothing implemented it; a malicious relay could swap the DTLS fingerprint (MITM) | design review (Stage 3 start) | fixed | HMAC over SDP; start-request MAC; tests |
| B-002 | med | security | Start-request MAC identical for 5 min → relay could replay it to switch the camera on | design review | fixed | one-time nonce + ReplayGuard (tests) |
| B-003 | med | usability | Manual pairing code ~79 chars | design review | fixed | room derived from secret → 28 chars |
| B-004 | high | relay | After a silent mobile-network drop the camera's reconnect would be refused ("role taken") while the dead socket lingered | design review | fixed | takeover + server heartbeat (tests; heartbeat test verified to fail when disabled) |
| B-005 | high | service | Early return before `startForeground()` after `startForegroundService()` would crash (5 s contract) | code review | fixed | promote first, validate second |
| B-006 | med | ui | Status collector launched in `onStart` stacked a new collector on every resume | code review | fixed | launched once in `onCreate` |
| B-007 | low | build | `SerializationException` not visible to app module (shared keeps serialization internal) | CI compile | fixed | catch `IllegalArgumentException` (its supertype) |
| B-008 | low | ci | `setup-android` default package list includes removed `tools` | CI | fixed | `packages: ''` |
