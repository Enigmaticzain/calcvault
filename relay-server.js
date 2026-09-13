'use strict';

const dgram = require('dgram');
const WebSocket = require('ws');

const {
  canonicalRequest,
  normalizeDeviceId,
} = require('./wrapper-utils');

const WS_PORT = Number(process.env.WS_PORT || 8082);
const UDP_PORT = Number(process.env.UDP_PORT || 8083);
const POLL_DELAY_IDLE_MS = 1_200;
const POLL_DELAY_BUSY_MS = 150;
const CORE_RETRY_DELAY_MS = 1_500;
const POLL_BATCH_LIMIT = 32;

function safeJsonParse(value) {
  try {
    return JSON.parse(String(value));
  } catch (_) {
    return null;
  }
}

function normalizeRelayPayload(payload, fallbackFrom = '') {
  if (!payload || typeof payload !== 'object' || Array.isArray(payload)) return null;

  const next = { ...payload };
  const from = normalizeDeviceId(
    next.from
      || next.sender
      || next.senderId
      || next.sid
      || next.device_id
      || fallbackFrom
  );
  const to = normalizeDeviceId(
    next.to
      || next.target
      || next.targetId
      || next.rid
      || next.device_id_to
      || next.partner_id
      || next.peer_id
  );

  if (!to) return null;
  if (from && !next.from) next.from = from;
  if (to && !next.to) next.to = to;
  if (from && !next.sid) next.sid = from;
  if (to && !next.rid) next.rid = to;
  if (!next.ts) next.ts = Date.now();

  return next;
}

function extractRelayMessages(response) {
  if (!response || response.statusCode < 200 || response.statusCode >= 300) {
    throw new Error('canonical_unavailable');
  }

  const payload = response.json;
  if (!payload || payload.type === 'none') return [];

  if (payload.type === 'batch' && Array.isArray(payload.messages)) {
    return payload.messages;
  }

  return [payload];
}

async function enqueueRelay(payload, options = {}) {
  const response = await canonicalRequest({
    ...options,
    method: 'POST',
    path: '/relay',
    json: payload,
  });

  if (response.statusCode < 200 || response.statusCode >= 300) {
    throw new Error('canonical_unavailable');
  }

  return response;
}

async function pollRelayMessages(deviceId, options = {}) {
  const response = await canonicalRequest({
    ...options,
    method: 'GET',
    path: `/poll/${encodeURIComponent(deviceId)}?kind=relay&batch=all&limit=${POLL_BATCH_LIMIT}`,
  });

  return extractRelayMessages(response);
}

function toLegacyRelayPacket(message) {
  const packet = {
    ...(message && typeof message === 'object' && !Array.isArray(message) ? message : {}),
  };

  if (packet.messageId !== undefined && packet.mid === undefined) {
    packet.mid = packet.messageId;
  }

  if (!packet.sid) packet.sid = normalizeDeviceId(packet.from);
  if (!packet.rid) packet.rid = normalizeDeviceId(packet.to);
  if (packet.payload !== undefined && packet.data === undefined) {
    packet.data = packet.payload;
  }
  if (!packet.ts) packet.ts = Date.now();

  delete packet.messageId;
  delete packet.from;
  delete packet.to;
  delete packet.payload;

  return packet;
}

function buildUdpPacket(message) {
  const packet = toLegacyRelayPacket(message);
  const payloadBuffer = Buffer.from(String(packet.data || ''), 'base64');
  delete packet.data;

  const headerBuffer = Buffer.from(JSON.stringify(packet), 'utf8');
  const output = Buffer.allocUnsafe(4 + headerBuffer.length + payloadBuffer.length);
  output.writeInt32BE(headerBuffer.length, 0);
  headerBuffer.copy(output, 4);
  payloadBuffer.copy(output, 4 + headerBuffer.length);
  return output;
}

function buildUdpErrorPacket(code, detail) {
  const payloadBuffer = Buffer.from(JSON.stringify({ error: code, detail }), 'utf8');
  const headerBuffer = Buffer.from(JSON.stringify({ type: 'error', ts: Date.now() }), 'utf8');
  const output = Buffer.allocUnsafe(4 + headerBuffer.length + payloadBuffer.length);
  output.writeInt32BE(headerBuffer.length, 0);
  headerBuffer.copy(output, 4);
  payloadBuffer.copy(output, 4 + headerBuffer.length);
  return output;
}

function parseUdpPacket(msg) {
  if (!Buffer.isBuffer(msg) || msg.length < 4) return null;

  const headerLength = msg.readInt32BE(0);
  if (!Number.isFinite(headerLength) || headerLength < 0 || msg.length < 4 + headerLength) {
    return null;
  }

  const header = safeJsonParse(msg.slice(4, 4 + headerLength).toString('utf8'));
  if (!header || typeof header !== 'object' || Array.isArray(header)) {
    return null;
  }

  return {
    header,
    payload: msg.slice(4 + headerLength),
  };
}

function sendWebSocketMessage(ws, payload) {
  return new Promise((resolve, reject) => {
    if (ws.readyState !== WebSocket.OPEN) {
      resolve(false);
      return;
    }

    ws.send(payload, (error) => {
      if (error) reject(error);
      else resolve(true);
    });
  });
}

function createRelayPoller(ws, getDeviceId, options = {}) {
  let timer = null;
  let closed = false;
  let inFlight = false;
  let reportedCoreFailure = false;

  function clearTimer() {
    if (!timer) return;
    clearTimeout(timer);
    timer = null;
  }

  function schedule(delayMs) {
    if (closed) return;
    clearTimer();
    timer = setTimeout(run, delayMs);
    timer.unref?.();
  }

  async function run() {
    if (closed || inFlight || ws.readyState !== WebSocket.OPEN) return;

    const deviceId = normalizeDeviceId(getDeviceId());
    if (!deviceId) {
      schedule(POLL_DELAY_IDLE_MS);
      return;
    }

    inFlight = true;

    try {
      const messages = await pollRelayMessages(deviceId, options);
      reportedCoreFailure = false;

      for (const message of messages) {
        await sendWebSocketMessage(ws, JSON.stringify(toLegacyRelayPacket(message)));
      }

      schedule(messages.length > 0 ? POLL_DELAY_BUSY_MS : POLL_DELAY_IDLE_MS);
    } catch (_) {
      if (!reportedCoreFailure) {
        reportedCoreFailure = true;
        try {
          await sendWebSocketMessage(ws, JSON.stringify({ error: 'CORE_UNAVAILABLE' }));
        } catch (_) {
          // Ignore send failures; close handler will stop the poller.
        }
      }

      schedule(CORE_RETRY_DELAY_MS);
    } finally {
      inFlight = false;
    }
  }

  ws.on('close', () => {
    closed = true;
    clearTimer();
  });

  return {
    kick() {
      schedule(0);
    },
    stop() {
      closed = true;
      clearTimer();
    },
  };
}

async function handleUdpRelay(udpServer, msg, rinfo, options = {}) {
  const parsed = parseUdpPacket(msg);
  if (!parsed) {
    const errorPacket = buildUdpErrorPacket('INVALID_RELAY', 'malformed_packet');
    udpServer.send(errorPacket, rinfo.port, rinfo.address);
    return;
  }

  const senderId = normalizeDeviceId(
    parsed.header.sid
      || parsed.header.from
      || parsed.header.device_id
  );

  const relayPayload = normalizeRelayPayload(
    {
      ...parsed.header,
      data: parsed.payload.toString('base64'),
    },
    senderId
  );

  if (!relayPayload) {
    const errorPacket = buildUdpErrorPacket('INVALID_RELAY', 'missing_target');
    udpServer.send(errorPacket, rinfo.port, rinfo.address);
    return;
  }

  try {
    await enqueueRelay(relayPayload, options);
  } catch (_) {
    const errorPacket = buildUdpErrorPacket('CORE_UNAVAILABLE', 'relay_enqueue_failed');
    udpServer.send(errorPacket, rinfo.port, rinfo.address);
    return;
  }

  const pollId = normalizeDeviceId(relayPayload.sid || relayPayload.from);
  if (!pollId) return;

  try {
    const pending = await pollRelayMessages(pollId, options);
    for (const message of pending) {
      udpServer.send(buildUdpPacket(message), rinfo.port, rinfo.address);
    }
  } catch (_) {
    const errorPacket = buildUdpErrorPacket('CORE_UNAVAILABLE', 'relay_poll_failed');
    udpServer.send(errorPacket, rinfo.port, rinfo.address);
  }
}

function startServer(options = {}) {
  const wsPort = Number(options.wsPort ?? process.env.WS_PORT ?? WS_PORT);
  const udpPort = Number(options.udpPort ?? process.env.UDP_PORT ?? UDP_PORT);

  const udpServer = dgram.createSocket('udp4');
  const wss = new WebSocket.Server({ port: wsPort });

  function terminateClients() {
    for (const client of wss.clients) {
      try {
        client.terminate();
      } catch (_) {
        // Ignore teardown errors.
      }
    }
  }

  wss.on('connection', (ws) => {
    let clientId = '';
    const poller = createRelayPoller(ws, () => clientId, options);

    ws.on('message', (message) => {
      void (async () => {
        const parsed = safeJsonParse(message);
        if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
          await sendWebSocketMessage(ws, JSON.stringify({ error: 'INVALID_RELAY', detail: 'malformed_json' })).catch(() => {});
          return;
        }

        if (parsed.type === 'auth') {
          const nextClientId = normalizeDeviceId(parsed.device_id || parsed.deviceId || parsed.from);
          if (!nextClientId) {
            await sendWebSocketMessage(ws, JSON.stringify({ error: 'INVALID_AUTH' })).catch(() => {});
            return;
          }

          clientId = nextClientId;
          poller.kick();
          return;
        }

        const relayPayload = normalizeRelayPayload(parsed, clientId);
        if (!relayPayload) {
          await sendWebSocketMessage(ws, JSON.stringify({ error: 'INVALID_RELAY' })).catch(() => {});
          return;
        }

        try {
          await enqueueRelay(relayPayload, options);
          poller.kick();
        } catch (_) {
          await sendWebSocketMessage(ws, JSON.stringify({ error: 'CORE_UNAVAILABLE' })).catch(() => {});
        }
      })();
    });

    ws.on('close', () => {
      poller.stop();
    });
  });

  udpServer.on('message', (msg, rinfo) => {
    void handleUdpRelay(udpServer, msg, rinfo, options).catch(() => {
      const errorPacket = buildUdpErrorPacket('CORE_UNAVAILABLE', 'relay_bridge_failed');
      udpServer.send(errorPacket, rinfo.port, rinfo.address);
    });
  });

  udpServer.on('error', () => process.exit(1));
  udpServer.bind(udpPort);

  const shutdown = () => {
    terminateClients();

    try {
      wss.close();
    } catch (_) {
      // Ignore shutdown errors.
    }

    try {
      udpServer.close(() => process.exit(0));
    } catch (_) {
      process.exit(0);
    }
  };

  process.on('SIGTERM', shutdown);
  process.on('SIGINT', shutdown);

  return {
    udpServer,
    webSocketServer: wss,
    stop() {
      return new Promise((resolve) => {
        let pending = 2;
        const done = () => {
          pending -= 1;
          if (pending <= 0) resolve();
        };

        terminateClients();

        try {
          wss.close(done);
        } catch (_) {
          done();
        }

        try {
          udpServer.close(done);
        } catch (_) {
          done();
        }
      });
    },
  };
}

module.exports = { startServer };

if (require.main === module) {
  startServer();
}
