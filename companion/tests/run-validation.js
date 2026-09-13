import assert from "assert";
import os from "os";
import path from "path";
import fs from "fs/promises";
import { fileURLToPath } from "url";
import { createCompanionApp } from "../src/main.js";
import { MockPhoneBridge } from "./mock-phone.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

function randomPort(start = 43000, span = 1200) {
  return start + Math.floor(Math.random() * span);
}

function parseApiToken(html) {
  const match = String(html || "").match(/apiToken:\s*"([a-f0-9]+)"/i);
  if (!match) throw new Error("api_token_not_found");
  return match[1];
}

async function bootstrapUiSession(launchUrl) {
  const first = await fetch(launchUrl, { redirect: "manual" });
  if (![302, 303].includes(first.status)) {
    throw new Error(`expected_redirect_got_${first.status}`);
  }

  const cookieHeader = first.headers.get("set-cookie");
  if (!cookieHeader) throw new Error("session_cookie_missing");
  const cookie = cookieHeader.split(";")[0];

  const target = new URL(launchUrl);
  const second = await fetch(`${target.origin}/`, {
    headers: {
      cookie,
    },
  });

  const html = await second.text();
  const apiToken = parseApiToken(html);

  return {
    origin: target.origin,
    cookie,
    apiToken,
  };
}

async function api(session, method, route, body = null) {
  const response = await fetch(`${session.origin}${route}`, {
    method,
    headers: {
      cookie: session.cookie,
      "x-calcvault-ui-token": session.apiToken,
      ...(body && !(body instanceof FormData) ? { "content-type": "application/json" } : {}),
    },
    body: body
      ? (body instanceof FormData ? body : JSON.stringify(body))
      : undefined,
  });

  const payload = await response.json().catch(() => ({}));
  if (!response.ok || payload.ok === false) {
    throw new Error(payload.error || `api_error_${response.status}`);
  }
  return payload;
}

async function waitFor(predicate, timeoutMs = 5000, stepMs = 120) {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    if (await predicate()) return true;
    await new Promise((resolve) => setTimeout(resolve, stepMs));
  }
  return false;
}

async function run() {
  const phonePort = randomPort(37200, 500);
  const companionPort = randomPort(43800, 700);
  const tmpRoot = await fs.mkdtemp(path.join(os.tmpdir(), "cv-companion-test-"));
  const backupRoot = path.join(tmpRoot, "backups");

  const mockPhone = new MockPhoneBridge({ port: phonePort });
  await mockPhone.start();

  const app = await createCompanionApp({
    port: companionPort,
    bindHost: "127.0.0.1",
    allowNetworkTransport: true,
    disableUsbTransport: false,
    backupRoot,
    dataRoot: tmpRoot,
    webRoot: path.resolve(__dirname, "..", "web"),
  });

  await app.start();

  let session;
  try {
    session = await bootstrapUiSession(app.launchUrl);

    const usbScan = await api(session, "GET", "/api/usb/devices");
    assert.ok(Array.isArray(usbScan.devices), "usb scan response invalid");

    await api(session, "POST", "/api/connection/connect", {
      transport: "network",
      url: `ws://127.0.0.1:${phonePort}/bridge`,
    });

    const pairing = await api(session, "POST", "/api/pairing/start");
    mockPhone.setPairingCode(pairing.pairing.code);

    const paired = await waitFor(async () => {
      const status = await api(session, "GET", "/api/status");
      return Boolean(status.status?.sessionActive);
    }, 7_000);

    assert.ok(paired, "phone pairing did not complete");

    const created = await api(session, "POST", "/api/backups/create");
    assert.ok(created.backup?.backupId, "backup id missing");
    assert.ok(created.backup?.sha256, "backup hash missing");

    const backups = await api(session, "GET", "/api/backups");
    assert.ok(backups.backups.length >= 1, "backup listing empty");
    const newestBackup = backups.backups[0];

    const restore = await api(session, "POST", `/api/backups/${encodeURIComponent(newestBackup.backupId)}/restore`);
    assert.ok(restore.result?.digest, "restore digest missing");

    const uploadPayload = Buffer.from("test-file-secret-content", "utf8");
    const form = new FormData();
    form.append("file", new Blob([uploadPayload]), "evidence.txt");
    const upload = await api(session, "POST", "/api/files/upload", form);
    assert.ok(upload.result?.fileId, "uploaded file id missing");

    const files = await api(session, "GET", "/api/files");
    assert.ok(files.files.length >= 1, "file index is empty");

    const targetFileId = upload.result.fileId;
    const encryptedDownload = await fetch(
      `${session.origin}/api/files/${encodeURIComponent(targetFileId)}/download?mode=encrypted&apiToken=${session.apiToken}`,
      { headers: { cookie: session.cookie } }
    );
    assert.equal(encryptedDownload.status, 200, "encrypted download failed");

    const encryptedBuffer = Buffer.from(await encryptedDownload.arrayBuffer());
    assert.ok(encryptedBuffer.length > 0, "encrypted download empty");
    assert.ok(!encryptedBuffer.includes(uploadPayload), "encrypted download leaked plaintext");

    const decryptedDownload = await fetch(
      `${session.origin}/api/files/${encodeURIComponent(targetFileId)}/download?mode=decrypted&apiToken=${session.apiToken}`,
      { headers: { cookie: session.cookie } }
    );
    assert.equal(decryptedDownload.status, 200, "decrypted download failed");
    const decryptedBuffer = Buffer.from(await decryptedDownload.arrayBuffer());
    assert.ok(decryptedBuffer.equals(uploadPayload), "decrypted download mismatch");

    const backupBytes = await fs.readFile(newestBackup.filePath);
    assert.ok(!backupBytes.includes(uploadPayload), "backup file unexpectedly contains plaintext upload payload");

    console.log("Validation complete: pairing, backup/restore, upload/download, and leakage checks passed.");
    console.log(`Companion port: ${companionPort}`);
    console.log(`Mock phone port: ${phonePort}`);
    console.log(`Backup root: ${backupRoot}`);
  } finally {
    await app.stop();
    await mockPhone.stop();
  }
}

run().catch((error) => {
  console.error("Validation failed:", error);
  process.exitCode = 1;
});
