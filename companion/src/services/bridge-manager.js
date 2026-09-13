import { EventEmitter } from "events";
import {
  SessionCipher,
  computePairingProof,
  deriveSessionKey,
  randomId,
  safeEqualBase64,
} from "../security/crypto.js";
import { PairingManager } from "../security/pairing-manager.js";
import { UsbTransport } from "../transport/usb.js";
import { NetworkTransport } from "../transport/network.js";
import { createDeferred } from "../util/deferred.js";

function decodeJson(payload) {
  const text = Buffer.isBuffer(payload) ? payload.toString("utf8") : String(payload);
  return JSON.parse(text);
}

function sanitizeDevice(device) {
  if (!device || typeof device !== "object") return { id: "unknown", name: "Unknown Device", fingerprint: "" };
  return {
    id: String(device.id || "unknown").slice(0, 128),
    name: String(device.name || "CalcVault Phone").slice(0, 128),
    fingerprint: String(device.fingerprint || "").slice(0, 256),
    model: String(device.model || "").slice(0, 128),
    osVersion: String(device.osVersion || "").slice(0, 64),
  };
}

function splitBuffer(chunk, maxChunkBytes) {
  if (!Buffer.isBuffer(chunk)) return [];
  if (chunk.length <= maxChunkBytes) return [chunk];

  const parts = [];
  for (let offset = 0; offset < chunk.length; offset += maxChunkBytes) {
    parts.push(chunk.subarray(offset, Math.min(offset + maxChunkBytes, chunk.length)));
  }
  return parts;
}

export class BridgeManager extends EventEmitter {
  constructor(config, logger, backupStore) {
    super();
    this.config = config;
    this.logger = logger;
    this.backupStore = backupStore;
    this.pairingManager = new PairingManager(config);
    this.usbTransport = new UsbTransport(config, logger);
    this.networkTransport = new NetworkTransport(config);

    this.connection = null;
    this.sessionCipher = null;
    this.sessionInfo = null;
    this.pendingOps = new Map();
    this.fileMetadataCache = [];
    this.heartbeatTimer = null;
    this.disconnecting = false;

    this.pairingManager.on("pairing:created", () => {
      this.emitStatus();
    });
  }

  async listUsbDevices() {
    return this.usbTransport.listDevices();
  }

  statusSnapshot() {
    return {
      connected: Boolean(this.connection),
      transport: this.connection?.transport || "none",
      endpoint: this.connection?.endpoint || "",
      pairPending: Boolean(this.pairingManager.current()),
      sessionActive: Boolean(this.sessionCipher && this.sessionInfo && Date.now() < this.sessionInfo.expiresAt),
      sessionExpiresAt: this.sessionInfo?.expiresAt || 0,
      device: this.sessionInfo?.device || null,
      cachedFiles: this.fileMetadataCache.length,
      pendingOperations: this.pendingOps.size,
    };
  }

  emitStatus() {
    this.emit("status", this.statusSnapshot());
  }

  ensureSession() {
    if (!this.connection || !this.sessionCipher || !this.sessionInfo) {
      throw new Error("session_not_paired");
    }

    if (Date.now() >= this.sessionInfo.expiresAt) {
      this.disconnect("session_expired");
      throw new Error("session_expired");
    }
  }

  async connect(options = {}) {
    const transport = String(options.transport || "usb").toLowerCase();
    if (!["usb", "network"].includes(transport)) {
      throw new Error("unsupported_transport");
    }

    await this.disconnect("reconnect");

    const connector = transport === "usb" ? this.usbTransport : this.networkTransport;
    const conn = await connector.connect(options);

    this.connection = {
      ...conn,
      connectedAt: Date.now(),
    };

    this.bindConnectionHandlers();
    this.emitStatus();

    return this.statusSnapshot();
  }

  async disconnect(reason = "manual") {
    if (this.disconnecting) return;
    this.disconnecting = true;

    const oldConnection = this.connection;
    this.connection = null;
    this.sessionCipher = null;
    this.sessionInfo = null;
    this.pairingManager.invalidate();
    this.fileMetadataCache = [];

    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = null;
    }

    for (const [requestId, op] of this.pendingOps.entries()) {
      clearTimeout(op.timer);
      op.final.reject(new Error(`operation_aborted:${reason}`));
      op.authorized.reject(new Error(`operation_aborted:${reason}`));
      this.pendingOps.delete(requestId);
    }

    if (oldConnection?.ws) {
      try {
        oldConnection.ws.removeAllListeners();
        oldConnection.ws.close(1000, reason);
      } catch (_) {
        // Ignore close errors.
      }
    }

    if (oldConnection?.cleanup) {
      try {
        await oldConnection.cleanup();
      } catch (error) {
        this.logger.warn("Connection cleanup failed", { error: error?.message || String(error) });
      }
    }

    this.disconnecting = false;
    this.emitStatus();
  }

  bindConnectionHandlers() {
    if (!this.connection?.ws) return;
    const ws = this.connection.ws;

    ws.on("message", async (raw) => {
      try {
        await this.handleIncoming(raw);
      } catch (error) {
        this.logger.warn("Incoming message handling failed", { error: error?.message || String(error) });
      }
    });

    ws.on("close", async () => {
      if (this.disconnecting) return;
      await this.disconnect("socket_closed");
    });

    ws.on("error", (error) => {
      this.logger.warn("Bridge socket error", { error: error?.message || String(error) });
    });
  }

  async startPairing() {
    if (!this.connection?.ws) {
      throw new Error("not_connected");
    }

    const ticket = await this.pairingManager.begin({
      transport: this.connection.transport,
      endpointHint: this.connection.endpoint,
    });

    const hello = {
      type: "hello",
      version: 1,
      pairingId: ticket.pairingId,
      codeHash: this.pairingManager.current().codeHash,
      pcPub: this.pairingManager.current().ecdh.publicKeyDer,
      pcNonce: this.pairingManager.current().pcNonce,
      sessionTtlMs: this.config.sessionTtlMs,
    };

    this.sendPlain(hello);
    this.emit("pairing:started", {
      pairingId: ticket.pairingId,
      expiresAt: ticket.expiresAt,
      transport: ticket.transport,
    });
    this.emitStatus();
    return ticket;
  }

  sendPlain(message) {
    if (!this.connection?.ws) {
      throw new Error("not_connected");
    }
    this.connection.ws.send(JSON.stringify(message));
  }

  sendSecure(message) {
    this.ensureSession();
    const envelope = this.sessionCipher.encrypt(message);
    this.connection.ws.send(JSON.stringify(envelope));
  }

  async handleIncoming(raw) {
    const incoming = decodeJson(raw);

    if (!this.sessionCipher) {
      await this.handleHandshakeMessage(incoming);
      return;
    }

    if (incoming.type !== "enc") {
      if (incoming.type === "hello_ack") {
        return;
      }
      throw new Error("unencrypted_message_rejected");
    }

    const message = this.sessionCipher.decrypt(incoming);
    await this.handleSecureMessage(message);
  }

  async handleHandshakeMessage(message) {
    if (!message || typeof message !== "object") return;

    if (message.type !== "hello_ack") {
      if (message.type === "hello_error") {
        throw new Error(`pairing_failed:${message.reason || "unknown"}`);
      }
      return;
    }

    const pairing = this.pairingManager.current();
    if (!pairing) {
      throw new Error("pairing_not_active");
    }

    const phoneCodeHash = String(message.codeHash || "");
    if (message.pairingId !== pairing.pairingId || phoneCodeHash !== pairing.codeHash) {
      throw new Error("pairing_identity_mismatch");
    }

    const expectedProof = computePairingProof({
      pairingCode: pairing.code,
      sessionId: pairing.pairingId,
      pcPub: pairing.ecdh.publicKeyDer,
      phonePub: String(message.phonePub || ""),
      pcNonce: pairing.pcNonce,
      phoneNonce: String(message.phoneNonce || ""),
      deviceId: String(message.device?.id || ""),
    });

    if (!safeEqualBase64(expectedProof, String(message.proof || ""))) {
      throw new Error("pairing_proof_invalid");
    }

    const sessionKey = deriveSessionKey({
      privateKey: pairing.ecdh.privateKey,
      peerPublicDerBase64: String(message.phonePub || ""),
      pairingCode: pairing.code,
      saltParts: [pairing.pairingId, pairing.pcNonce, String(message.phoneNonce || "")],
    });

    const device = sanitizeDevice(message.device);

    this.sessionCipher = new SessionCipher({
      sessionId: pairing.pairingId,
      sessionKey,
    });

    this.sessionInfo = {
      sessionId: pairing.pairingId,
      establishedAt: Date.now(),
      expiresAt: Date.now() + this.config.sessionTtlMs,
      device,
    };

    this.pairingManager.invalidate();
    this.startHeartbeat();

    this.sendSecure({
      type: "session_ready",
      at: Date.now(),
      capabilities: ["backup.create", "backup.restore", "files.list", "files.upload", "files.download"],
    });

    this.emit("session:paired", {
      device,
      expiresAt: this.sessionInfo.expiresAt,
    });
    this.emitStatus();
  }

  startHeartbeat() {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer);
    }

    this.heartbeatTimer = setInterval(() => {
      if (!this.sessionCipher) return;
      try {
        this.sendSecure({ type: "ping", ts: Date.now() });
      } catch (_) {
        // Session may have expired or disconnected.
      }
    }, 15_000);

    this.heartbeatTimer.unref?.();
  }

  beginOperation(op, args = {}, options = {}) {
    this.ensureSession();

    const requestId = randomId(12);
    const final = createDeferred();
    const authorized = createDeferred();
    const timeoutMs = Number(options.timeoutMs || this.config.opTimeoutMs);

    const timer = setTimeout(() => {
      this.rejectOperation(requestId, new Error("operation_timeout"));
    }, timeoutMs);
    timer.unref?.();

    this.pendingOps.set(requestId, {
      requestId,
      op,
      final,
      authorized,
      timer,
      onEvent: options.onEvent,
      approved: false,
    });

    this.sendSecure({
      type: "op_request",
      requestId,
      op,
      args,
      requestedAt: Date.now(),
    });

    return {
      requestId,
      result: final.promise,
      authorized: authorized.promise,
    };
  }

  async sendOperationChunk(requestId, chunk, index) {
    const op = this.pendingOps.get(requestId);
    if (!op) {
      throw new Error("operation_not_found");
    }
    if (!op.approved) {
      throw new Error("operation_not_authorized");
    }

    const dataBuffer = Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk);
    const parts = splitBuffer(dataBuffer, this.config.maxChunkBytes);

    let nextIndex = index;
    for (const part of parts) {
      this.sendSecure({
        type: "op_chunk",
        requestId,
        index: nextIndex,
        chunk: part.toString("base64"),
      });
      nextIndex += 1;
    }

    return nextIndex;
  }

  sendOperationEnd(requestId, metadata = {}) {
    const op = this.pendingOps.get(requestId);
    if (!op) {
      throw new Error("operation_not_found");
    }
    if (!op.approved) {
      throw new Error("operation_not_authorized");
    }

    this.sendSecure({
      type: "op_chunk_end",
      requestId,
      metadata,
    });
  }

  settleOperation(requestId, result) {
    const op = this.pendingOps.get(requestId);
    if (!op) return;

    clearTimeout(op.timer);
    this.pendingOps.delete(requestId);
    op.final.resolve(result);
    op.authorized.resolve({ approved: true });
  }

  rejectOperation(requestId, error) {
    const op = this.pendingOps.get(requestId);
    if (!op) return;

    clearTimeout(op.timer);
    this.pendingOps.delete(requestId);
    op.final.reject(error);
    op.authorized.reject(error);
  }

  async handleSecureMessage(message) {
    switch (message.type) {
      case "ping":
        this.sendSecure({ type: "pong", ts: Date.now() });
        return;
      case "pong":
        return;
      case "session_expired":
        await this.disconnect("phone_session_expired");
        return;
      case "op_authorized": {
        const op = this.pendingOps.get(message.requestId);
        if (!op) return;
        const approved = Boolean(message.approved);
        op.approved = approved;
        if (approved) {
          op.authorized.resolve({ approved: true, token: message.token || "" });
          this.emit("operation:authorized", { requestId: message.requestId, op: op.op });
        } else {
          this.rejectOperation(message.requestId, new Error(message.reason || "operation_denied_by_phone"));
        }
        return;
      }
      case "op_event": {
        const op = this.pendingOps.get(message.requestId);
        if (!op) return;
        if (typeof op.onEvent === "function") {
          op.onEvent(message);
        }
        this.emit("operation:event", message);
        return;
      }
      case "op_result": {
        if (!message.ok) {
          this.rejectOperation(message.requestId, new Error(message.error || "operation_failed"));
          return;
        }

        this.settleOperation(message.requestId, message.result || {});
        this.emit("operation:result", message);
        return;
      }
      default:
        this.emit("message", message);
    }
  }

  async createBackup() {
    this.ensureSession();

    let writer = null;
    let writeChain = Promise.resolve();
    let backupVersion = 1;

    const operation = this.beginOperation("backup.create", {
      requireApproval: true,
      protocol: 1,
    }, {
      onEvent: (event) => {
        if (event.eventType === "backup_manifest") {
          backupVersion = Number(event.data?.backupVersion || 1);
        }

        if (event.eventType === "backup_chunk") {
          writeChain = writeChain.then(async () => {
            if (!writer) {
              writer = await this.backupStore.createWriter({
                deviceId: this.sessionInfo.device.id,
                backupVersion,
              });
            }
            await writer.append(String(event.data?.chunk || ""));
          });
        }
      },
    });

    await operation.authorized;
    const result = await operation.result;
    await writeChain;

    if (!writer) {
      throw new Error("backup_empty_stream");
    }

    const metadata = await writer.finalize({
      expectedSha256: String(result.sha256 || ""),
      backupVersion: Number(result.backupVersion || backupVersion || 1),
      sourceSnapshotTs: result.snapshotTs || Date.now(),
    });

    this.emit("backup:created", metadata);
    return metadata;
  }

  async restoreBackup(backupId) {
    this.ensureSession();

    const { metadata, stream } = await this.backupStore.openBackupStream(backupId);
    const operation = this.beginOperation("backup.restore", {
      requireApproval: true,
      backupId,
      sha256: metadata.sha256,
      size: metadata.size,
      backupVersion: metadata.backupVersion,
    });

    await operation.authorized;

    let index = 0;
    for await (const chunk of stream) {
      index = await this.sendOperationChunk(operation.requestId, chunk, index);
    }

    this.sendOperationEnd(operation.requestId, {
      finalChunkIndex: Math.max(index - 1, 0),
    });

    const result = await operation.result;
    this.emit("backup:restored", {
      backupId,
      result,
    });

    return {
      backupId,
      ...result,
    };
  }

  async getFileIndex() {
    this.ensureSession();
    const operation = this.beginOperation("files.list", {
      requireApproval: true,
    });

    await operation.authorized;
    const result = await operation.result;

    this.fileMetadataCache = Array.isArray(result.files) ? result.files : [];
    this.emit("files:index", {
      count: this.fileMetadataCache.length,
    });

    return this.fileMetadataCache;
  }

  async uploadFileStream({ fileName, size, mimeType, stream }) {
    this.ensureSession();

    const operation = this.beginOperation("files.upload", {
      requireApproval: true,
      fileName,
      size,
      mimeType,
    });

    await operation.authorized;

    let index = 0;
    for await (const chunk of stream) {
      index = await this.sendOperationChunk(operation.requestId, chunk, index);
    }

    this.sendOperationEnd(operation.requestId, {
      finalChunkIndex: Math.max(index - 1, 0),
      fileName,
      size,
    });

    const result = await operation.result;
    this.emit("files:uploaded", {
      fileName,
      fileId: result.fileId || "",
    });

    return result;
  }

  async streamDownloadToWritable({ fileId, mode = "encrypted", writeChunk }) {
    this.ensureSession();

    if (typeof writeChunk !== "function") {
      throw new Error("write_chunk_callback_required");
    }

    let writeChain = Promise.resolve();
    const operation = this.beginOperation("files.download", {
      requireApproval: true,
      fileId,
      mode,
    }, {
      onEvent: (event) => {
        if (event.eventType !== "download_chunk") return;
        const chunk = Buffer.from(String(event.data?.chunk || ""), "base64");
        writeChain = writeChain.then(() => writeChunk(chunk, event.data || {}));
      },
    });

    await operation.authorized;
    const result = await operation.result;
    await writeChain;

    this.emit("files:downloaded", {
      fileId,
      mode,
      bytes: result.size || 0,
    });

    return result;
  }
}
