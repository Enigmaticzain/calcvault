import { createHash, randomBytes } from "crypto";
import { WebSocketServer } from "ws";
import {
  SessionCipher,
  computePairingProof,
  createEcdhKeyPair,
  deriveSessionKey,
  sha256Base64,
} from "../src/security/crypto.js";

function toSha256Hex(buffer) {
  return createHash("sha256").update(buffer).digest("hex");
}

function chunkBuffer(buffer, maxBytes = 192 * 1024) {
  const chunks = [];
  for (let offset = 0; offset < buffer.length; offset += maxBytes) {
    chunks.push(buffer.subarray(offset, Math.min(offset + maxBytes, buffer.length)));
  }
  return chunks;
}

function decodeJson(value) {
  const text = Buffer.isBuffer(value) ? value.toString("utf8") : String(value);
  return JSON.parse(text);
}

function buildMockSnapshot() {
  const marker = Buffer.from("CALCVAULT_ENCRYPTED_SNAPSHOT_V1", "utf8");
  const noise = randomBytes(128 * 1024);
  return Buffer.concat([marker, noise]);
}

export class MockPhoneBridge {
  constructor(options = {}) {
    this.host = options.host || "127.0.0.1";
    this.port = Number(options.port || 37111);
    this.device = {
      id: options.deviceId || "phone-test-01",
      name: options.deviceName || "CalcVault Test Phone",
      fingerprint: options.fingerprint || "mock-fingerprint-abc",
      model: "Pixel-Mock",
      osVersion: "Android-15",
    };

    this.server = null;
    this.ws = null;
    this.sessionCipher = null;
    this.currentPairingCode = "";
    this.pendingHello = null;
    this.activeOps = new Map();

    this.vaultFiles = new Map();
    const seedPlain = Buffer.from("seed-file-content-for-download", "utf8");
    this.vaultFiles.set("seed-1", {
      fileId: "seed-1",
      name: "seed.txt",
      plain: seedPlain,
      encrypted: Buffer.concat([Buffer.from("ENCv1", "utf8"), Buffer.from(seedPlain).reverse()]),
      modifiedAt: Date.now(),
    });
  }

  setPairingCode(code) {
    this.currentPairingCode = String(code || "").trim().toUpperCase();
    if (this.pendingHello) {
      const hello = this.pendingHello;
      this.pendingHello = null;
      this.finalizeHandshake(hello).catch(() => {});
    }
  }

  async start() {
    if (this.server) return;

    this.server = new WebSocketServer({
      host: this.host,
      port: this.port,
      path: "/bridge",
    });

    this.server.on("connection", (ws) => {
      this.ws = ws;
      ws.on("message", async (raw) => {
        try {
          await this.handleIncoming(raw);
        } catch (error) {
          ws.send(JSON.stringify({ type: "hello_error", reason: error.message || "mock_error" }));
        }
      });
    });

    await new Promise((resolve, reject) => {
      this.server.once("listening", resolve);
      this.server.once("error", reject);
    });
  }

  async stop() {
    this.sessionCipher = null;
    this.activeOps.clear();

    if (this.ws) {
      try {
        this.ws.close(1000, "stop");
      } catch (_) {
        // Ignore close errors.
      }
      this.ws = null;
    }

    if (!this.server) return;
    await new Promise((resolve) => this.server.close(resolve));
    this.server = null;
  }

  send(payload) {
    if (!this.ws) throw new Error("mock_socket_missing");
    this.ws.send(JSON.stringify(payload));
  }

  sendSecure(payload) {
    if (!this.sessionCipher) throw new Error("mock_session_missing");
    this.send(this.sessionCipher.encrypt(payload));
  }

  async handleIncoming(raw) {
    const incoming = decodeJson(raw);

    if (!this.sessionCipher) {
      await this.handleHandshake(incoming);
      return;
    }

    if (incoming.type !== "enc") return;
    const message = this.sessionCipher.decrypt(incoming);
    await this.handleSecureMessage(message);
  }

  async handleHandshake(message) {
    if (!message || message.type !== "hello") return;

    if (!this.currentPairingCode) {
      this.pendingHello = message;
      return;
    }
    await this.finalizeHandshake(message);
  }

  async finalizeHandshake(message) {
    const codeHash = sha256Base64(this.currentPairingCode);
    if (codeHash !== String(message.codeHash || "")) {
      throw new Error("mock_code_hash_mismatch");
    }

    const phoneKeys = createEcdhKeyPair();
    const phoneNonce = randomBytes(8).toString("hex");
    const proof = computePairingProof({
      pairingCode: this.currentPairingCode,
      sessionId: message.pairingId,
      pcPub: String(message.pcPub || ""),
      phonePub: phoneKeys.publicKeyDer,
      pcNonce: String(message.pcNonce || ""),
      phoneNonce,
      deviceId: this.device.id,
    });

    const sessionKey = deriveSessionKey({
      privateKey: phoneKeys.privateKey,
      peerPublicDerBase64: String(message.pcPub || ""),
      pairingCode: this.currentPairingCode,
      saltParts: [message.pairingId, message.pcNonce, phoneNonce],
    });

    this.sessionCipher = new SessionCipher({
      sessionId: message.pairingId,
      sessionKey,
      sendDirection: 2,
      recvDirection: 1,
    });

    this.send({
      type: "hello_ack",
      pairingId: message.pairingId,
      codeHash,
      phonePub: phoneKeys.publicKeyDer,
      phoneNonce,
      proof,
      device: this.device,
    });
  }

  async handleSecureMessage(message) {
    switch (message.type) {
      case "ping":
        this.sendSecure({ type: "pong", ts: Date.now() });
        return;
      case "session_ready":
        return;
      case "op_request":
        await this.handleOperationRequest(message);
        return;
      case "op_chunk":
        await this.handleOperationChunk(message);
        return;
      case "op_chunk_end":
        await this.handleOperationEnd(message);
        return;
      default:
        return;
    }
  }

  authorize(requestId) {
    this.sendSecure({
      type: "op_authorized",
      requestId,
      approved: true,
      token: randomBytes(12).toString("hex"),
    });
  }

  async handleOperationRequest(message) {
    const { requestId, op, args = {} } = message;
    this.authorize(requestId);

    switch (op) {
      case "backup.create": {
        const snapshot = buildMockSnapshot();
        const digest = toSha256Hex(snapshot);

        this.sendSecure({
          type: "op_event",
          requestId,
          eventType: "backup_manifest",
          data: {
            backupVersion: 1,
            size: snapshot.length,
            sha256: digest,
          },
        });

        for (const part of chunkBuffer(snapshot)) {
          this.sendSecure({
            type: "op_event",
            requestId,
            eventType: "backup_chunk",
            data: {
              chunk: part.toString("base64"),
            },
          });
        }

        this.sendSecure({
          type: "op_result",
          requestId,
          ok: true,
          result: {
            backupVersion: 1,
            sha256: digest,
            size: snapshot.length,
            snapshotTs: Date.now(),
          },
        });
        return;
      }
      case "backup.restore": {
        this.activeOps.set(requestId, {
          type: "backup.restore",
          args,
          chunks: [],
        });
        return;
      }
      case "files.list": {
        const files = [...this.vaultFiles.values()].map((file) => ({
          fileId: file.fileId,
          name: file.name,
          size: file.plain.length,
          modifiedAt: file.modifiedAt,
        }));

        this.sendSecure({
          type: "op_result",
          requestId,
          ok: true,
          result: { files },
        });
        return;
      }
      case "files.upload": {
        this.activeOps.set(requestId, {
          type: "files.upload",
          args,
          chunks: [],
        });
        return;
      }
      case "files.download": {
        const target = this.vaultFiles.get(String(args.fileId || ""));
        if (!target) {
          this.sendSecure({
            type: "op_result",
            requestId,
            ok: false,
            error: "file_not_found",
          });
          return;
        }

        const mode = String(args.mode || "encrypted");
        const payload = mode === "decrypted" ? target.plain : target.encrypted;

        for (const part of chunkBuffer(payload, 128 * 1024)) {
          this.sendSecure({
            type: "op_event",
            requestId,
            eventType: "download_chunk",
            data: {
              chunk: part.toString("base64"),
            },
          });
        }

        this.sendSecure({
          type: "op_result",
          requestId,
          ok: true,
          result: {
            fileName: target.name,
            size: payload.length,
            mode,
          },
        });
        return;
      }
      default:
        this.sendSecure({
          type: "op_result",
          requestId,
          ok: false,
          error: "unsupported_operation",
        });
    }
  }

  async handleOperationChunk(message) {
    const op = this.activeOps.get(message.requestId);
    if (!op) return;

    const chunk = Buffer.from(String(message.chunk || ""), "base64");
    op.chunks.push(chunk);
  }

  async handleOperationEnd(message) {
    const op = this.activeOps.get(message.requestId);
    if (!op) return;

    this.activeOps.delete(message.requestId);
    const payload = Buffer.concat(op.chunks);

    if (op.type === "backup.restore") {
      const expected = String(op.args.sha256 || "");
      const digest = toSha256Hex(payload);
      const ok = expected && expected === digest;

      this.sendSecure({
        type: "op_result",
        requestId: message.requestId,
        ok,
        result: {
          restoredAt: Date.now(),
          bytes: payload.length,
          digest,
        },
        error: ok ? undefined : "restore_integrity_failed",
      });
      return;
    }

    if (op.type === "files.upload") {
      const fileId = `file-${randomBytes(4).toString("hex")}`;
      const fileName = String(op.args.fileName || `${fileId}.bin`);

      this.vaultFiles.set(fileId, {
        fileId,
        name: fileName,
        plain: payload,
        encrypted: Buffer.concat([Buffer.from("ENCv1", "utf8"), Buffer.from(payload).reverse()]),
        modifiedAt: Date.now(),
      });

      this.sendSecure({
        type: "op_result",
        requestId: message.requestId,
        ok: true,
        result: {
          fileId,
          name: fileName,
          size: payload.length,
          storedAt: Date.now(),
        },
      });
      return;
    }

    this.sendSecure({
      type: "op_result",
      requestId: message.requestId,
      ok: false,
      error: "unknown_stream_operation",
    });
  }
}
