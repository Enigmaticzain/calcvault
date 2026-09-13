import http from "http";
import fs from "fs/promises";
import path from "path";
import express from "express";
import Busboy from "busboy";
import { WebSocketServer } from "ws";
import { randomBytes } from "crypto";

function parseCookies(req) {
  const raw = String(req.headers.cookie || "");
  const cookies = {};
  for (const part of raw.split(";")) {
    const [key, ...rest] = part.trim().split("=");
    if (!key) continue;
    cookies[key] = decodeURIComponent(rest.join("=") || "");
  }
  return cookies;
}

function jsonError(res, status, message) {
  res.status(status).json({ ok: false, error: message });
}

function validateOrigin(req, trustedOrigin) {
  if (!trustedOrigin) return true;
  const origin = String(req.headers.origin || "");
  if (!origin) return false;
  return origin === trustedOrigin;
}

function createSessionStore(config) {
  const sessions = new Map();

  function create() {
    const sessionId = randomBytes(16).toString("hex");
    const apiToken = randomBytes(24).toString("hex");
    const now = Date.now();
    const entry = {
      sessionId,
      apiToken,
      createdAt: now,
      expiresAt: now + config.sessionTtlMs,
    };
    sessions.set(sessionId, entry);
    return entry;
  }

  function get(sessionId) {
    const item = sessions.get(sessionId);
    if (!item) return null;
    if (Date.now() > item.expiresAt) {
      sessions.delete(sessionId);
      return null;
    }

    item.expiresAt = Date.now() + config.sessionTtlMs;
    return item;
  }

  function cleanup() {
    const now = Date.now();
    for (const [sessionId, item] of sessions.entries()) {
      if (item.expiresAt <= now) {
        sessions.delete(sessionId);
      }
    }
  }

  return {
    create,
    get,
    cleanup,
  };
}

function extractStatusPayload(bridgeManager) {
  return {
    ok: true,
    status: bridgeManager.statusSnapshot(),
    now: Date.now(),
  };
}

export async function createHttpServer({ config, logger, bridgeManager, backupStore }) {
  const app = express();
  const sessionStore = createSessionStore(config);
  const launchToken = randomBytes(18).toString("hex");
  const webSockets = new Set();

  app.disable("x-powered-by");
  app.use(express.json({ limit: "2mb" }));

  app.use((_, res, next) => {
    res.setHeader("Cache-Control", "no-store");
    next();
  });

  // --- Signaling Endpoints for Phones ---
  app.post("/register", async (req, res) => {
    const { device_id, deviceId } = req.body || {};
    const id = device_id || deviceId;
    const ip = req.ip || req.connection.remoteAddress;
    if (id) {
      const { signalingService } = await import("../services/signaling-service.js");
      signalingService.register(id, ip);
      res.json({ ok: true });
    } else {
      res.status(400).json({ ok: false });
    }
  });

  app.get("/heartbeat/:deviceId", async (req, res) => {
    const { signalingService } = await import("../services/signaling-service.js");
    const ok = signalingService.heartbeat(req.params.deviceId);
    res.json({ ok });
  });

  app.get("/lookup/:deviceId", async (req, res) => {
    const { signalingService } = await import("../services/signaling-service.js");
    const info = signalingService.lookup(req.params.deviceId);
    if (info.ok) res.json(info);
    else res.status(404).json(info);
  });

  app.post("/relay", async (req, res) => {
    const { signalingService } = await import("../services/signaling-service.js");
    const ok = signalingService.relay(req.body);
    res.json({ ok });
  });

  app.post("/signal", async (req, res) => {
    const { signalingService } = await import("../services/signaling-service.js");
    const ok = signalingService.signal(req.body);
    res.json({ ok });
  });

  app.get("/poll/:deviceId", async (req, res) => {
    const { signalingService } = await import("../services/signaling-service.js");
    const kind = req.query.kind;
    const result = signalingService.poll(req.params.deviceId, kind);
    res.json(result);
  });
  // --- End Signaling Endpoints ---

  app.get("/health", (_, res) => {
    res.json({ ok: true, service: "calcvault-companion", ts: Date.now() });
  });

  app.get("/", async (req, res) => {
    const cookies = parseCookies(req);
    let session = sessionStore.get(cookies.cv_session || "");

    if (!session) {
      const token = String(req.query.token || "");
      if (token !== launchToken) {
        res.status(401).type("text/plain").send("Unauthorized local session. Launch with the secure URL printed by the companion service.");
        return;
      }

      session = sessionStore.create();
      const maxAgeSeconds = Math.floor(config.sessionTtlMs / 1000);
      res.setHeader("Set-Cookie", `cv_session=${encodeURIComponent(session.sessionId)}; HttpOnly; SameSite=Strict; Path=/; Max-Age=${maxAgeSeconds}`);
      res.redirect(302, "/");
      return;
    }

    const htmlPath = path.join(config.webRoot, "index.html");
    let html = await fs.readFile(htmlPath, "utf8");
    html = html.replaceAll("__CV_API_TOKEN__", session.apiToken);
    html = html.replaceAll("__CV_SESSION_TTL_MS__", String(config.sessionTtlMs));
    res.type("html").send(html);
  });

  app.get("/styles.css", async (_, res) => {
    const cssPath = path.join(config.webRoot, "styles.css");
    res.type("text/css").send(await fs.readFile(cssPath, "utf8"));
  });

  app.get("/app.js", async (_, res) => {
    const jsPath = path.join(config.webRoot, "app.js");
    res.type("application/javascript").send(await fs.readFile(jsPath, "utf8"));
  });

  function requireUiAuth(req, res, next) {
    if (!validateOrigin(req, config.trustedOrigin)) {
      jsonError(res, 403, "origin_not_allowed");
      return;
    }

    const cookies = parseCookies(req);
    const session = sessionStore.get(cookies.cv_session || "");
    if (!session) {
      jsonError(res, 401, "session_missing_or_expired");
      return;
    }

    const token = String(req.headers["x-calcvault-ui-token"] || req.query.apiToken || "");
    if (!token || token !== session.apiToken) {
      jsonError(res, 401, "invalid_api_token");
      return;
    }

    req.cvSession = session;
    next();
  }

  app.use("/api", requireUiAuth);

  app.get("/api/status", (_, res) => {
    res.json(extractStatusPayload(bridgeManager));
  });

  app.get("/api/usb/devices", async (_, res) => {
    try {
      const devices = await bridgeManager.listUsbDevices();
      res.json({ ok: true, devices });
    } catch (error) {
      jsonError(res, 500, error?.message || "usb_scan_failed");
    }
  });

  app.post("/api/connection/connect", async (req, res) => {
    try {
      const { transport, serial, url } = req.body || {};
      const state = await bridgeManager.connect({ transport, serial, url });
      res.json({ ok: true, state });
    } catch (error) {
      jsonError(res, 400, error?.message || "connect_failed");
    }
  });

  app.post("/api/connection/disconnect", async (_, res) => {
    await bridgeManager.disconnect("ui_disconnect");
    res.json({ ok: true, status: bridgeManager.statusSnapshot() });
  });

  app.post("/api/pairing/start", async (_, res) => {
    try {
      const ticket = await bridgeManager.startPairing();
      res.json({ ok: true, pairing: ticket });
    } catch (error) {
      jsonError(res, 400, error?.message || "pairing_start_failed");
    }
  });

  app.get("/api/backups", async (_, res) => {
    try {
      const backups = await backupStore.listBackups();
      res.json({ ok: true, backups });
    } catch (error) {
      jsonError(res, 500, error?.message || "backup_list_failed");
    }
  });

  app.post("/api/backups/create", async (_, res) => {
    try {
      const backup = await bridgeManager.createBackup();
      res.json({ ok: true, backup });
    } catch (error) {
      jsonError(res, 400, error?.message || "backup_create_failed");
    }
  });

  app.post("/api/backups/:backupId/restore", async (req, res) => {
    try {
      const result = await bridgeManager.restoreBackup(String(req.params.backupId || ""));
      res.json({ ok: true, result });
    } catch (error) {
      jsonError(res, 400, error?.message || "backup_restore_failed");
    }
  });

  app.get("/api/files", async (_, res) => {
    try {
      const files = await bridgeManager.getFileIndex();
      res.json({ ok: true, files });
    } catch (error) {
      jsonError(res, 400, error?.message || "file_index_failed");
    }
  });

  app.post("/api/files/upload", async (req, res) => {
    const bb = Busboy({
      headers: req.headers,
      limits: {
        files: 1,
        fileSize: config.maxUploadBytes,
      },
    });

    let uploadPromise = null;
    let uploaded = false;

    bb.on("file", (_, file, info) => {
      if (uploaded) {
        file.resume();
        return;
      }

      uploaded = true;
      uploadPromise = bridgeManager.uploadFileStream({
        fileName: info.filename || "upload.bin",
        size: 0,
        mimeType: info.mimeType || "application/octet-stream",
        stream: file,
      });
    });

    bb.on("error", (error) => {
      jsonError(res, 400, error?.message || "upload_parse_failed");
    });

    bb.on("finish", async () => {
      if (!uploadPromise) {
        jsonError(res, 400, "missing_file");
        return;
      }

      try {
        const result = await uploadPromise;
        res.json({ ok: true, result });
      } catch (error) {
        jsonError(res, 400, error?.message || "upload_failed");
      }
    });

    req.pipe(bb);
  });

  app.get("/api/files/:fileId/download", async (req, res) => {
    try {
      const fileId = String(req.params.fileId || "");
      const mode = String(req.query.mode || "encrypted");
      let done = false;
      const safeName = `${fileId}_${mode}.bin`.replace(/[^a-zA-Z0-9._-]/g, "_");
      res.setHeader("Content-Type", "application/octet-stream");
      res.setHeader("Content-Disposition", `attachment; filename=\"${safeName}\"`);

      const resultPromise = bridgeManager.streamDownloadToWritable({
        fileId,
        mode,
        writeChunk: async (chunk) => {
          if (done) return;
          if (!res.write(chunk)) {
            await new Promise((resolve) => res.once("drain", resolve));
          }
        },
      });

      await resultPromise;
      done = true;
      res.end();
    } catch (error) {
      if (!res.headersSent) {
        jsonError(res, 400, error?.message || "download_failed");
      } else {
        res.end();
      }
    }
  });

  app.use((error, _, res, __) => {
    logger.error("Unhandled API error", { error: error?.stack || error?.message || String(error) });
    jsonError(res, 500, "internal_error");
  });

  const server = http.createServer(app);
  const wsServer = new WebSocketServer({ server, path: "/ui/events" });

  // --- Added Pairing Signaling WS ---
  const rooms = new Map(); // roomName -> Set of WS
  const pairingWsServer = new WebSocketServer({ noServer: true });

  server.on("upgrade", (req, socket, head) => {
    const { pathname } = new URL(req.url, `http://${req.headers.host}`);

    if (pathname === "/ui/events") {
      wsServer.handleUpgrade(req, socket, head, (ws) => {
        wsServer.emit("connection", ws, req);
      });
    } else if (pathname === "/ws" || pathname === "/") {
      pairingWsServer.handleUpgrade(req, socket, head, (ws) => {
        logger.info("Pairing WS connected", { ip: req.ip || req.connection.remoteAddress });

        ws.on("message", (data) => {
          try {
            const json = JSON.parse(data.toString());
            if (json.join) {
              const room = json.join;
              if (!rooms.has(room)) rooms.set(room, new Set());
              rooms.get(room).add(ws);

              const roomSet = rooms.get(room);
              if (roomSet.size >= 2) {
                const status = JSON.stringify({ status: "PEER_CONNECTED" });
                for (const client of roomSet) {
                  if (client.readyState === 1) client.send(status);
                }
              }
            } else if (json.signal) {
              for (const [room, clients] of rooms.entries()) {
                if (clients.has(ws)) {
                  for (const client of clients) {
                    if (client !== ws && client.readyState === 1) {
                      client.send(data.toString());
                    }
                  }
                  break;
                }
              }
            }
          } catch (e) {
            logger.warn("Pairing WS error", { error: e.message });
          }
        });

        ws.on("close", () => {
          for (const clients of rooms.values()) {
            clients.delete(ws);
          }
        });
      });
    } else {
      socket.destroy();
    }
  });
  // --- End Pairing Signaling WS ---

  function broadcast(type, payload) {
    const message = JSON.stringify({ type, payload, ts: Date.now() });
    for (const ws of webSockets) {
      if (ws.readyState === ws.OPEN) {
        ws.send(message);
      }
    }
  }

  wsServer.on("connection", (ws, req) => {
    const cookies = parseCookies(req);
    const session = sessionStore.get(cookies.cv_session || "");
    if (!session) {
      ws.close(4001, "unauthorized");
      return;
    }

    const authToken = String(new URL(req.url, `http://${req.headers.host}`).searchParams.get("token") || "");
    if (authToken !== session.apiToken) {
      ws.close(4001, "unauthorized");
      return;
    }

    webSockets.add(ws);
    ws.send(JSON.stringify({ type: "status", payload: bridgeManager.statusSnapshot(), ts: Date.now() }));

    ws.on("close", () => {
      webSockets.delete(ws);
    });
  });

  const statusListener = (payload) => broadcast("status", payload);
  const opEventListener = (payload) => broadcast("operation_event", payload);
  const backupCreatedListener = (payload) => broadcast("backup_created", payload);

  bridgeManager.on("status", statusListener);
  bridgeManager.on("operation:event", opEventListener);
  bridgeManager.on("backup:created", backupCreatedListener);

  const sessionCleanupTimer = setInterval(() => {
    sessionStore.cleanup();
  }, 60_000);
  sessionCleanupTimer.unref?.();

  return {
    launchToken,
    server,
    async start() {
      await new Promise((resolve, reject) => {
        server.once("error", reject);
        server.listen(config.port, config.bindHost, () => {
          server.removeListener("error", reject);
          resolve();
        });
      });
    },
    async stop() {
      await bridgeManager.disconnect("server_stop");
      clearInterval(sessionCleanupTimer);
      for (const ws of webSockets) {
        try {
          ws.close(1001, "server_stopping");
        } catch (_) {
          // Ignore close errors.
        }
      }
      wsServer.close();
      await new Promise((resolve) => server.close(resolve));
      bridgeManager.removeListener("status", statusListener);
      bridgeManager.removeListener("operation:event", opEventListener);
      bridgeManager.removeListener("backup:created", backupCreatedListener);
    },
  };
}
