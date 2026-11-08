// Zero-knowledge WebSocket signalling relay.
//
// Responsibilities (deliberately minimal):
//   - Group two peers (one "camera", one "viewer") into a room by room id.
//   - Relay offer / answer / ice / start-request / bye between them, verbatim.
//   - Enforce: at most one of each role per room; messages never cross rooms.
//
// Trust model — the relay is UNTRUSTED:
//   - It never holds the pairing secret, so it can neither mint nor verify
//     credentials. The room id is derived one-way from the secret (so knowing
//     the room id reveals nothing about the secret) and is only a rendezvous
//     point.
//   - Authentication is end-to-end between the peers: the camera verifies the
//     viewer's start-request token before enabling capture, and each side
//     verifies an HMAC over the other's SDP (which binds the DTLS fingerprint),
//     so a malicious relay cannot inject a session or MITM the media. Media is
//     DTLS-SRTP and never touches this server.
//
// Portable: plain `ws`, no persistence. Runs on a home machine, Deno Deploy,
// Cloudflare, Fly, etc. Free to host.

import { WebSocketServer } from "ws";

const PORT = process.env.PORT ? Number(process.env.PORT) : 8080;
const ROLES = new Set(["camera", "viewer"]);
const RELAY_TYPES = new Set(["offer", "answer", "ice", "start-request", "bye"]);
const MAX_MESSAGE_BYTES = 64 * 1024; // SDP is small; cap to resist abuse.

/** room id -> { camera?: ws, viewer?: ws } */
const rooms = new Map();

export function createServer(opts = {}) {
  const wss = new WebSocketServer({ port: opts.port ?? PORT, maxPayload: MAX_MESSAGE_BYTES });

  wss.on("connection", (ws) => {
    ws.room = null;
    ws.role = null;

    ws.on("message", (data) => {
      let msg;
      try {
        msg = JSON.parse(data.toString());
      } catch {
        return send(ws, { type: "error", reason: "bad-json" });
      }
      if (!msg || typeof msg.type !== "string") return;

      if (msg.type === "join") return handleJoin(ws, msg);
      if (ws.room == null) return send(ws, { type: "error", reason: "not-joined" });
      if (RELAY_TYPES.has(msg.type)) return relayToPeer(ws, msg);
      // Unknown types from a joined peer are ignored (forward-compat).
    });

    ws.on("close", () => leave(ws));
    ws.on("error", () => leave(ws));
  });

  return wss;
}

function handleJoin(ws, msg) {
  const { room, role } = msg;
  if (typeof room !== "string" || room.length < 8 || room.length > 256) {
    return closeWith(ws, { type: "auth-error", reason: "bad-room" });
  }
  if (!ROLES.has(role)) {
    return closeWith(ws, { type: "auth-error", reason: "bad-role" });
  }
  if (ws.room != null) return; // already joined

  const entry = rooms.get(room) ?? {};
  if (entry[role]) {
    return closeWith(ws, { type: "auth-error", reason: "role-taken" });
  }
  entry[role] = ws;
  rooms.set(room, entry);
  ws.room = room;
  ws.role = role;

  const peers = Object.keys(entry).filter((r) => entry[r] && entry[r].readyState === 1);
  send(ws, { type: "joined", peers });
  // Tell the already-present peer that this one arrived.
  const peer = otherPeer(ws);
  if (peer) send(peer, { type: "peer", role, present: true });
}

function relayToPeer(ws, msg) {
  const peer = otherPeer(ws);
  if (peer && peer.readyState === 1) send(peer, msg);
}

function otherPeer(ws) {
  const entry = rooms.get(ws.room);
  if (!entry) return null;
  const otherRole = ws.role === "camera" ? "viewer" : "camera";
  return entry[otherRole] ?? null;
}

function leave(ws) {
  if (ws.room == null) return;
  const entry = rooms.get(ws.room);
  if (entry && entry[ws.role] === ws) {
    const peer = otherPeer(ws);
    delete entry[ws.role];
    if (peer) send(peer, { type: "peer", role: ws.role, present: false });
    if (!entry.camera && !entry.viewer) rooms.delete(ws.room);
  }
  ws.room = null;
  ws.role = null;
}

function send(ws, obj) {
  if (ws.readyState === 1) ws.send(JSON.stringify(obj));
}

function closeWith(ws, obj) {
  send(ws, obj);
  try { ws.close(); } catch { /* ignore */ }
}

// Started directly (not imported by a test).
if (import.meta.url === `file://${process.argv[1]}`) {
  createServer();
  console.log(`[signaling] listening on ws://0.0.0.0:${PORT}`);
}
