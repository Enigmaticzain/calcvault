import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { createCompanionApp } from "../src/main.js";

function randomPort(start = 43800, span = 1200) {
  return start + Math.floor(Math.random() * span);
}

function parseApiToken(html) {
  const match = String(html || "").match(/apiToken:\s*"([a-f0-9]+)"/i);
  if (!match) throw new Error("api_token_not_found");
  return match[1];
}

async function bootstrapUiSession(launchUrl) {
  const first = await fetch(launchUrl, { redirect: "manual" });
  assert.ok([302, 303].includes(first.status), `expected redirect, got ${first.status}`);
  const cookieHeader = first.headers.get("set-cookie");
  assert.ok(cookieHeader, "session cookie missing");
  const cookie = cookieHeader.split(";")[0];
  const origin = new URL(launchUrl).origin;
  const second = await fetch(`${origin}/`, { headers: { cookie } });
  const apiToken = parseApiToken(await second.text());
  return { origin, cookie, apiToken };
}

async function run() {
  const port = randomPort();
  const root = await fs.mkdtemp(path.join(os.tmpdir(), "cv-companion-privacy-test-"));
  const app = await createCompanionApp({
    port,
    bindHost: "127.0.0.1",
    dataRoot: root,
    backupRoot: path.join(root, "backups"),
  });

  await app.start();
  try {
    const session = await bootstrapUiSession(app.launchUrl);
    const response = await fetch(`${session.origin}/api/connection/connect`, {
      method: "POST",
      headers: {
        cookie: session.cookie,
        "x-calcvault-ui-token": session.apiToken,
        "content-type": "application/json",
      },
      body: JSON.stringify({ transport: "network", url: "ws://192.0.2.10:37111/bridge" }),
    });
    const result = await response.json();

    assert.equal(response.status, 400);
    assert.equal(result.ok, false);
    assert.match(result.error, /network_transport_unavailable/);
    const status = await fetch(`${session.origin}/api/status`, {
      headers: { cookie: session.cookie, "x-calcvault-ui-token": session.apiToken },
    }).then((value) => value.json());
    assert.equal(status.status.connected, false);

    console.log("Validation complete: direct LAN route was rejected without connecting or falling back.");
  } finally {
    await app.stop();
    await fs.rm(root, { recursive: true, force: true });
  }
}

run().catch((error) => {
  console.error("Validation failed:", error);
  process.exitCode = 1;
});
