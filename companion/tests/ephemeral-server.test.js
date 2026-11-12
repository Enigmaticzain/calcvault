import assert from "node:assert/strict";
import fs from "node:fs/promises";
import net from "node:net";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { createCompanionApp } from "../src/main.js";

test("port zero selects an available loopback port and returns a usable launch URL", async (t) => {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), "cv-companion-ephemeral-"));
  const app = await createCompanionApp({
    bindHost: "127.0.0.1",
    port: 0,
    dataRoot: path.join(root, "data"),
    backupRoot: path.join(root, "data", "backups"),
  });
  let started = false;
  t.after(async () => {
    if (started) await app.stop();
    await fs.rm(root, { recursive: true, force: true });
  });

  await app.start();
  started = true;
  const url = new URL(app.launchUrl);
  assert.equal(url.hostname, "127.0.0.1");
  assert.ok(Number(url.port) > 0);
  assert.ok(url.searchParams.get("token"));

  const health = await fetch(`${url.origin}/health`);
  assert.equal(health.status, 200);
  assert.equal((await health.json()).service, "calcvault-companion");

  // Legacy signaling routes must not register identities, retain IPs, or relay payloads.
  const register = await fetch(`${url.origin}/register`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ device_id: "test-device" }),
  });
  assert.equal(register.status, 410);
  assert.match((await register.json()).error, /secure_signaling_unavailable/);

  const signal = await fetch(`${url.origin}/signal`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ from: "test-device", to: "peer", signal: "secret" }),
  });
  assert.equal(signal.status, 410);
  assert.match((await signal.json()).error, /secure_signaling_unavailable/);

  const rejectedUpgrade = await new Promise((resolve, reject) => {
    const socket = net.connect(Number(url.port), "127.0.0.1");
    socket.setTimeout(2_000, () => socket.destroy(new Error("WebSocket rejection timed out")));
    socket.once("error", reject);
    socket.once("connect", () => socket.write(
      `GET /ws HTTP/1.1\r\nHost: 127.0.0.1:${url.port}\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: 13\r\n\r\n`
    ));
    socket.once("data", (data) => {
      resolve(data.toString("utf8"));
      socket.destroy();
    });
  });
  assert.match(rejectedUpgrade, /^HTTP\/1\.1 410 Gone/);
});
