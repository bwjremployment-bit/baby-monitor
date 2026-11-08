import { test } from "node:test";
import assert from "node:assert/strict";
import { WebSocket } from "ws";
import { createServer } from "./server.js";

const PORT = 18099;
const URL = `ws://127.0.0.1:${PORT}`;

function open(onMessage) {
  const ws = new WebSocket(URL);
  ws.on("message", (d) => onMessage(JSON.parse(d.toString())));
  return new Promise((res) => ws.on("open", () => res(ws)));
}
const next = (predicate) => new Promise((res) => (predicate._resolve = res));
const wait = (ms) => new Promise((r) => setTimeout(r, ms));

test("relay: camera + viewer pair, offer/answer/ice forwarded, role enforced", async (t) => {
  const wss = createServer({ port: PORT });
  t.after(() => wss.close());

  const camMsgs = [];
  const viewMsgs = [];
  const cam = await open((m) => camMsgs.push(m));
  cam.send(JSON.stringify({ type: "join", room: "room-abcdef12", role: "camera" }));
  await wait(50);
  assert.deepEqual(camMsgs.at(-1), { type: "joined", peers: ["camera"] });

  const view = await open((m) => viewMsgs.push(m));
  view.send(JSON.stringify({ type: "join", room: "room-abcdef12", role: "viewer" }));
  await wait(50);
  // viewer sees both peers present; camera is told viewer arrived
  assert.ok(viewMsgs.some((m) => m.type === "joined" && m.peers.includes("camera")));
  assert.ok(camMsgs.some((m) => m.type === "peer" && m.role === "viewer" && m.present));

  // offer camera -> viewer
  cam.send(JSON.stringify({ type: "offer", sdp: "SDP_OFFER" }));
  await wait(50);
  assert.ok(viewMsgs.some((m) => m.type === "offer" && m.sdp === "SDP_OFFER"));

  // answer viewer -> camera
  view.send(JSON.stringify({ type: "answer", sdp: "SDP_ANSWER" }));
  await wait(50);
  assert.ok(camMsgs.some((m) => m.type === "answer" && m.sdp === "SDP_ANSWER"));

  // ice both ways
  view.send(JSON.stringify({ type: "ice", candidate: { sdpMid: "0", sdpMLineIndex: 0, candidate: "C" } }));
  await wait(50);
  assert.ok(camMsgs.some((m) => m.type === "ice" && m.candidate.candidate === "C"));

  // a second camera in the same room is rejected
  const dupMsgs = [];
  const dup = await open((m) => dupMsgs.push(m));
  dup.send(JSON.stringify({ type: "join", room: "room-abcdef12", role: "camera" }));
  await wait(50);
  assert.ok(dupMsgs.some((m) => m.type === "auth-error" && m.reason === "role-taken"));

  cam.close(); view.close();
});

test("isolation: messages never cross rooms; leave notifies peer", async (t) => {
  const wss = createServer({ port: PORT + 1 });
  t.after(() => wss.close());
  const url = `ws://127.0.0.1:${PORT + 1}`;

  const mk = (store) => {
    const ws = new WebSocket(url);
    ws.on("message", (d) => store.push(JSON.parse(d.toString())));
    return new Promise((res) => ws.on("open", () => res(ws)));
  };
  const aMsgs = [], bMsgs = [];
  const a = await mk(aMsgs);
  a.send(JSON.stringify({ type: "join", room: "room-aaaaaaa1", role: "camera" }));
  const b = await mk(bMsgs);
  b.send(JSON.stringify({ type: "join", room: "room-bbbbbbb2", role: "viewer" }));
  await wait(50);

  a.send(JSON.stringify({ type: "offer", sdp: "SECRET" }));
  await wait(50);
  assert.ok(!bMsgs.some((m) => m.type === "offer"), "offer must not leak to a different room");

  // same-room peer departure is signalled
  const cMsgs = [];
  const c = await mk(cMsgs);
  c.send(JSON.stringify({ type: "join", room: "room-aaaaaaa1", role: "viewer" }));
  await wait(50);
  c.close();
  await wait(50);
  assert.ok(aMsgs.some((m) => m.type === "peer" && m.role === "viewer" && m.present === false));

  a.close(); b.close();
});
