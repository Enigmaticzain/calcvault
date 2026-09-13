'use strict';

const http = require('http');
const { URL, URLSearchParams } = require('url');

const DEFAULTS = {
  signalTtlMs: 60_000,
  relayTtlMs: 15 * 60_000,
  presenceTtlMs: 5 * 60_000,
  peerRetentionMs: 20 * 60_000,
  roomTtlMs: 60_000,
  maxPayloadBytes: 25 * 1024 * 1024,
  maxQueueDepth: 512,
  rateLimit: 1200,
  rateWindowMs: 60_000,
  cleanupIntervalMs: 10_000,
};

function sanitizePath(pathname) {
  const value = String(pathname || '/').trim();
  if (!value || value === '/') return '/';
  return value.replace(/\/+$/, '') || '/';
}

function normalizePeerId(value) {
  const raw = String(value || '').trim();
  if (!raw) return '';
  try {
    return decodeURIComponent(raw).trim().slice(0, 128);
  } catch (_) {
    return raw.slice(0, 128);
  }
}

function firstNonBlank(...values) {
  for (const value of values) {
    if (value === undefined || value === null) continue;
    if (typeof value === 'number' && Number.isFinite(value)) return value;
    const text = String(value).trim();
    if (text) return value;
  }
  return '';
}

function getRemoteIp(req) {
  const forwarded = String(req.headers['x-forwarded-for'] || '')
    .split(',')
    .map((part) => part.trim())
    .find(Boolean);
  const raw = forwarded || req.socket.remoteAddress || '';
  return raw.replace('::ffff:', '');
}

function createLatestVersion() {
  return {
    code: 1,
    name: '1.0',
    url: 'https://calcvault-app.example.com/download/latest.apk',
  };
}

function createState() {
  const sessions = new Map();
  return {
    sessions,
    peers: sessions,
    signalQueues: new Map(),
    relayQueues: new Map(),
    rateMap: new Map(),
    rooms: new Map(),
    roomTimeouts: new Map(),
    latestVersion: createLatestVersion(),
    nextMessageSeq: 1,
  };
}

function createSession(state, peerId) {
  const safeId = normalizePeerId(peerId);
  if (!safeId) return null;

  const existing = state.sessions.get(safeId);
  if (existing) return existing;

  const created = {
    id: safeId,
    deviceId: safeId,
    device_id: safeId,
    connectionState: 'OFFLINE',
    registeredAt: 0,
    lastHeartbeat: 0,
    lastSeen: 0,
    last_seen: 0,
    lastPollAt: 0,
    ts: 0,
    candidates: [],
    version_code: 1,
    version_name: '1.0',
    token: '',
    status: 'OFFLINE',
    presenceStatus: 'OFFLINE',
    device_fingerprint: '',
    messageQueue: [],
    relayQueue: [],
  };

  state.sessions.set(safeId, created);
  return created;
}

function removeSession(state, peerId) {
  const safeId = normalizePeerId(peerId);
  if (!safeId) return;
  state.sessions.delete(safeId);
  state.signalQueues.delete(safeId);
  state.relayQueues.delete(safeId);
}

function syncLegacyQueues(state, session) {
  if (!session) return;

  if (session.messageQueue.length > 0) {
    state.signalQueues.set(session.deviceId, session.messageQueue);
  } else {
    state.signalQueues.delete(session.deviceId);
  }

  if (session.relayQueue.length > 0) {
    state.relayQueues.set(session.deviceId, session.relayQueue);
  } else {
    state.relayQueues.delete(session.deviceId);
  }
}

function cloneCandidate(candidate) {
  if (!candidate || typeof candidate !== 'object') return null;
  const ip = String(candidate.ip || '').trim().slice(0, 128);
  if (!ip) return null;
  return {
    ip,
    port: Number(candidate.port || 0) || 0,
    type: String(candidate.type || 'host').trim().slice(0, 32),
  };
}

function mergeCandidates(existingCandidates, newCandidates, remoteIp) {
  const merged = [];
  const seen = new Set();

  function addCandidate(candidate) {
    const safe = cloneCandidate(candidate);
    if (!safe) return;
    const key = `${safe.ip}:${safe.port}:${safe.type}`;
    if (seen.has(key)) return;
    seen.add(key);
    merged.push(safe);
  }

  (existingCandidates || []).forEach(addCandidate);
  (Array.isArray(newCandidates) ? newCandidates : []).forEach(addCandidate);

  if (remoteIp && remoteIp !== '127.0.0.1' && remoteIp !== '::1') {
    addCandidate({ ip: remoteIp, type: 'srflx' });
  }

  return merged.slice(0, 16);
}

function updateSessionActivity(session, options = {}) {
  if (!session) return;

  const now = options.now || Date.now();
  session.lastSeen = now;
  session.last_seen = now;
  session.connectionState = 'ONLINE';
  session.status = 'ONLINE';

  if (options.heartbeat) {
    session.lastHeartbeat = now;
  }

  if (options.poll) {
    session.lastPollAt = now;
  }

  if (!session.presenceStatus || session.presenceStatus === 'OFFLINE') {
    session.presenceStatus = String(options.status || 'ONLINE').slice(0, 128);
  }
}

function refreshSessionState(session, config, now = Date.now()) {
  if (!session) return false;
  const online = now - (session.lastSeen || 0) < config.presenceTtlMs;
  session.connectionState = online ? 'ONLINE' : 'OFFLINE';
  session.status = online ? 'ONLINE' : 'OFFLINE';
  if (!online) {
    session.presenceStatus = 'OFFLINE';
  } else if (!session.presenceStatus || session.presenceStatus === 'OFFLINE') {
    session.presenceStatus = 'ONLINE';
  }
  return online;
}

function pruneQueue(queue, ttl, now) {
  if (!Array.isArray(queue) || queue.length === 0) return;

  const filtered = queue.filter((item) => item && now - item.ts < ttl);
  if (filtered.length === queue.length) return;

  queue.splice(0, queue.length, ...filtered);
}

function pruneSessionQueues(state, session, config, now = Date.now()) {
  pruneQueue(session.messageQueue, config.signalTtlMs, now);
  pruneQueue(session.relayQueue, config.relayTtlMs, now);
  syncLegacyQueues(state, session);
}

function enqueueMessage(state, peerId, kind, envelope, config) {
  const session = createSession(state, peerId);
  if (!session) return false;

  const now = Date.now();
  const queue = kind === 'relay' ? session.relayQueue : session.messageQueue;
  const ttl = kind === 'relay' ? config.relayTtlMs : config.signalTtlMs;
  pruneQueue(queue, ttl, now);

  if (queue.length >= config.maxQueueDepth) {
    syncLegacyQueues(state, session);
    return false;
  }

  queue.push(envelope);
  syncLegacyQueues(state, session);
  return true;
}

function drainMessages(state, session, kind, limit, config, now = Date.now()) {
  if (!session) return [];

  const queue = kind === 'relay' ? session.relayQueue : session.messageQueue;
  const ttl = kind === 'relay' ? config.relayTtlMs : config.signalTtlMs;
  pruneQueue(queue, ttl, now);

  const safeLimit = Math.max(1, Math.min(Number(limit || 1) || 1, queue.length || 1));
  const drained = queue.splice(0, safeLimit);
  syncLegacyQueues(state, session);
  return drained;
}

function isRateLimited(state, ip, config) {
  const now = Date.now();
  const key = String(ip || 'unknown');
  const entry = state.rateMap.get(key) || { count: 0, windowStart: now };

  if (now - entry.windowStart > config.rateWindowMs) {
    entry.count = 1;
    entry.windowStart = now;
  } else {
    entry.count += 1;
  }

  state.rateMap.set(key, entry);
  return entry.count > config.rateLimit;
}

function parseBody(req, maxBytes) {
  return new Promise((resolve, reject) => {
    let size = 0;
    const chunks = [];

    req.on('data', (chunk) => {
      size += chunk.length;
      if (size > maxBytes) {
        reject(new Error('too_large'));
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });

    req.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')));
    req.on('error', reject);
  });
}

function tryParseJson(raw) {
  const text = String(raw || '').trim();
  if (!text) return {};
  try {
    return JSON.parse(text);
  } catch (_) {
    return null;
  }
}

function tryParseBody(raw) {
  const parsedJson = tryParseJson(raw);
  if (parsedJson !== null) return parsedJson;

  const text = String(raw || '').trim();
  if (!text) return {};

  const looksLikeForm = (text.includes('=') || text.includes('&')) && !/[\r\n]/.test(text);
  if (looksLikeForm) {
    const params = new URLSearchParams(text);
    const parsedForm = {};
    for (const [key, value] of params.entries()) {
      parsedForm[key] = value;
    }

    if (Object.keys(parsedForm).length > 0) {
      return parsedForm;
    }
  }

  return { signal: text };
}

function buildJsonResponse(res, statusCode, payload) {
  res.writeHead(statusCode, {
    'Content-Type': 'application/json; charset=utf-8',
    'Cache-Control': 'no-store',
    'X-Content-Type-Options': 'nosniff',
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'GET,POST,OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type',
  });
  res.end(JSON.stringify(payload));
}

function buildTextResponse(res, statusCode, text) {
  res.writeHead(statusCode, {
    'Content-Type': 'text/plain; charset=utf-8',
    'Cache-Control': 'no-store',
    'X-Content-Type-Options': 'nosniff',
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'GET,POST,OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type',
  });
  res.end(text);
}

function buildPeerLookup(session, config) {
  if (!session) return null;

  const online = refreshSessionState(session, config);
  return {
    device_id: session.deviceId,
    deviceId: session.deviceId,
    device_fingerprint: session.device_fingerprint || '',
    deviceFingerprint: session.device_fingerprint || '',
    candidates: Array.isArray(session.candidates) ? session.candidates : [],
    version_code: session.version_code || 1,
    versionCode: session.version_code || 1,
    version_name: session.version_name || '1.0',
    versionName: session.version_name || '1.0',
    last_seen: session.lastSeen || 0,
    lastSeen: session.lastSeen || 0,
    last_heartbeat: session.lastHeartbeat || 0,
    lastHeartbeat: session.lastHeartbeat || 0,
    online,
    status: online ? 'ONLINE' : 'OFFLINE',
    connection_state: session.connectionState,
    connectionState: session.connectionState,
    queue_depth: session.messageQueue.length,
    queueDepth: session.messageQueue.length,
  };
}

function inferSignalType(payload, nestedSignal) {
  const source = (nestedSignal && typeof nestedSignal === 'object') ? nestedSignal : payload;
  if (!source || typeof source !== 'object') return '';

  if (source.candidate || source.sdpMid || source.sdpMLineIndex !== undefined) return 'ice';
  if (source.offer) return 'offer';
  if (source.answer) return 'answer';
  if (source.status || source.presence || source.state) return 'presence';
  if (source.callId && !source.type) return 'signal';
  if (source.sdp && String(source.signalType || source.kind || '').toLowerCase() === 'offer') return 'offer';
  if (source.sdp && String(source.signalType || source.kind || '').toLowerCase() === 'answer') return 'answer';
  return '';
}

function normalizeIncomingPayload(rawBody, parsedBody, fallbackType) {
  let payload;
  if (parsedBody && typeof parsedBody === 'object' && !Array.isArray(parsedBody)) {
    payload = { ...parsedBody };
  } else if (parsedBody !== undefined && parsedBody !== null && parsedBody !== '') {
    payload = { signal: parsedBody };
  } else {
    payload = { signal: String(rawBody || '').trim() };
  }

  const nestedSignal = payload.signal && typeof payload.signal === 'object' && !Array.isArray(payload.signal)
    ? payload.signal
    : null;

  const from = normalizePeerId(firstNonBlank(
    payload.from,
    payload.device_id,
    payload.deviceId,
    payload.sender,
    payload.senderId,
    payload.user_id,
    payload.userId,
    payload.sid,
    nestedSignal && firstNonBlank(
      nestedSignal.from,
      nestedSignal.device_id,
      nestedSignal.deviceId,
      nestedSignal.sender,
      nestedSignal.senderId,
      nestedSignal.user_id,
      nestedSignal.userId,
      nestedSignal.sid
    )
  ));

  const to = normalizePeerId(firstNonBlank(
    payload.to,
    payload.device_id_to,
    payload.deviceIdTo,
    payload.target,
    payload.targetId,
    payload.partner_id,
    payload.partnerId,
    payload.peer_id,
    payload.peerId,
    payload.rid,
    nestedSignal && firstNonBlank(
      nestedSignal.to,
      nestedSignal.device_id_to,
      nestedSignal.deviceIdTo,
      nestedSignal.target,
      nestedSignal.targetId,
      nestedSignal.partner_id,
      nestedSignal.partnerId,
      nestedSignal.peer_id,
      nestedSignal.peerId,
      nestedSignal.rid
    )
  ));

  const type = String(firstNonBlank(
    payload.type,
    nestedSignal && nestedSignal.type,
    inferSignalType(payload, nestedSignal),
    fallbackType || 'signal'
  ) || fallbackType || 'signal').trim();

  if (from && !payload.from) payload.from = from;
  if (to && !payload.to) payload.to = to;
  if (type && !payload.type) payload.type = type;

  return {
    payload,
    from,
    to,
    type,
  };
}

function createEnvelope(state, rawBody, parsedBody, fallbackType) {
  const normalized = normalizeIncomingPayload(rawBody, parsedBody, fallbackType);
  const now = Date.now();

  if (!normalized.to && fallbackType !== 'presence_broadcast') {
    return null;
  }

  return {
    id: state.nextMessageSeq,
    seq: state.nextMessageSeq++,
    ts: now,
    payload: normalized.payload,
    rawBody,
    type: normalized.type || fallbackType || 'signal',
    from: normalized.from,
    to: normalized.to,
  };
}

function handleRegister(state, req, res, parsedBody, config) {
  const deviceId = normalizePeerId(firstNonBlank(
    parsedBody.device_id,
    parsedBody.deviceId,
    parsedBody.user_id,
    parsedBody.userId,
    parsedBody.id,
    parsedBody.from
  ));

  const session = createSession(state, deviceId);
  if (!session) {
    buildJsonResponse(res, 400, { ok: false, error: 'invalid_device_id' });
    return;
  }

  const now = Date.now();
  const remoteIp = getRemoteIp(req);

  updateSessionActivity(session, { now, heartbeat: true });
  session.registeredAt = session.registeredAt || now;
  session.ts = now;
  session.candidates = mergeCandidates(session.candidates, parsedBody.candidates, remoteIp);
  session.version_code = Number(firstNonBlank(
    parsedBody.version_code,
    parsedBody.versionCode,
    parsedBody.code,
    session.version_code,
    1
  )) || 1;
  session.version_name = String(firstNonBlank(
    parsedBody.version_name,
    parsedBody.versionName,
    parsedBody.name,
    session.version_name,
    '1.0'
  )).slice(0, 64);
  session.token = String(firstNonBlank(parsedBody.token, session.token)).slice(0, 256);
  session.device_fingerprint = String(firstNonBlank(
    parsedBody.device_fingerprint,
    parsedBody.deviceFingerprint,
    parsedBody.fingerprint,
    session.device_fingerprint
  )).slice(0, 128);

  buildJsonResponse(res, 200, {
    ok: true,
    status: 'Registered',
    latest: state.latestVersion,
    peer: buildPeerLookup(session, config),
  });
}

function handleLookup(state, res, peerId, config) {
  const session = state.sessions.get(normalizePeerId(peerId));
  if (!session) {
    buildJsonResponse(res, 404, { ok: false, error: 'not_found' });
    return;
  }

  buildJsonResponse(res, 200, buildPeerLookup(session, config));
}

function handleSignalPost(state, res, rawBody, parsedBody, config) {
  const envelope = createEnvelope(state, rawBody, parsedBody, 'signal');
  if (!envelope || !envelope.to) {
    buildJsonResponse(res, 400, { ok: false, error: 'invalid_signal' });
    return;
  }

  if (envelope.from) {
    const sender = createSession(state, envelope.from);
    updateSessionActivity(sender, { now: Date.now() });
  }

  if (!enqueueMessage(state, envelope.to, 'signal', envelope, config)) {
    buildTextResponse(res, 503, 'queue_full');
    return;
  }

  buildTextResponse(res, 200, 'ok');
}

function handlePresencePost(state, res, rawBody, parsedBody, config) {
  const from = normalizePeerId(firstNonBlank(
    parsedBody.from,
    parsedBody.device_id,
    parsedBody.deviceId,
    parsedBody.user_id,
    parsedBody.userId
  ));
  const to = normalizePeerId(firstNonBlank(
    parsedBody.to,
    parsedBody.partner_id,
    parsedBody.partnerId,
    parsedBody.peer_id,
    parsedBody.peerId,
    parsedBody.target,
    parsedBody.targetId
  ));
  const status = String(firstNonBlank(
    parsedBody.status,
    parsedBody.presence,
    parsedBody.state,
    'ONLINE'
  )).slice(0, 128);

  if (!from || !to) {
    buildJsonResponse(res, 400, { ok: false, error: 'invalid_presence' });
    return;
  }

  const now = Date.now();
  const sender = createSession(state, from);
  updateSessionActivity(sender, { now });
  sender.presenceStatus = status || 'ONLINE';

  const payload = {
    ...((parsedBody && typeof parsedBody === 'object' && !Array.isArray(parsedBody)) ? parsedBody : {}),
    type: 'presence',
    from,
    to,
    status,
  };

  const envelope = {
    id: state.nextMessageSeq,
    seq: state.nextMessageSeq++,
    ts: now,
    payload,
    rawBody,
    type: 'presence',
    from,
    to,
  };

  if (!enqueueMessage(state, to, 'signal', envelope, config)) {
    buildTextResponse(res, 503, 'queue_full');
    return;
  }

  buildTextResponse(res, 200, 'ok');
}

function handleRelayPost(state, res, rawBody, parsedBody, config) {
  const payload = {
    ...((parsedBody && typeof parsedBody === 'object' && !Array.isArray(parsedBody)) ? parsedBody : {}),
  };
  const from = normalizePeerId(firstNonBlank(
    payload.from,
    payload.sender,
    payload.senderId,
    payload.sid
  ));
  const to = normalizePeerId(firstNonBlank(
    payload.to,
    payload.target,
    payload.targetId,
    payload.rid
  ));

  if (!to) {
    buildJsonResponse(res, 400, { ok: false, error: 'invalid_relay' });
    return;
  }

  if (from && !payload.from) payload.from = from;
  if (to && !payload.to) payload.to = to;

  if (from) {
    const sender = createSession(state, from);
    updateSessionActivity(sender, { now: Date.now() });
  }

  const envelope = {
    id: state.nextMessageSeq,
    seq: state.nextMessageSeq++,
    ts: Date.now(),
    payload,
    rawBody,
    type: String(firstNonBlank(payload.type, 'relay')).slice(0, 64),
    from,
    to,
  };

  if (!enqueueMessage(state, to, 'relay', envelope, config)) {
    buildTextResponse(res, 503, 'queue_full');
    return;
  }

  buildTextResponse(res, 200, 'ok');
}

function handlePoll(state, res, peerId, kind, config, searchParams) {
  const safeId = normalizePeerId(peerId);
  const session = state.sessions.get(safeId);

  if (!session) {
    buildJsonResponse(res, 200, { type: 'none' });
    return;
  }

  const batch = String(searchParams.get('batch') || searchParams.get('drain') || '').toLowerCase();
  const wantsBatch = batch === 'all' || batch === 'true' || batch === '1';
  const limit = wantsBatch
    ? Math.max(1, Number(searchParams.get('limit') || session.messageQueue.length || session.relayQueue.length || 1) || 1)
    : 1;

  updateSessionActivity(session, { now: Date.now(), poll: true });

  const items = drainMessages(state, session, kind, limit, config);
  if (items.length === 0) {
    buildJsonResponse(res, 200, { type: 'none' });
    return;
  }

  if (!wantsBatch) {
    buildJsonResponse(res, 200, items[0].payload);
    return;
  }

  buildJsonResponse(res, 200, {
    type: 'batch',
    count: items.length,
    messages: items.map((item) => item.payload),
  });
}

function handleHeartbeat(state, res, peerId) {
  const session = createSession(state, peerId);
  if (session) {
    updateSessionActivity(session, { now: Date.now(), heartbeat: true });
    session.presenceStatus = 'ONLINE';
  }

  buildTextResponse(res, 200, 'ok');
}

function handleAdminVersion(state, res, parsedBody) {
  const code = Number(firstNonBlank(parsedBody.code, parsedBody.version_code, 1)) || 1;
  const name = String(firstNonBlank(parsedBody.name, parsedBody.version_name, '1.0')).slice(0, 64);
  const url = String(firstNonBlank(parsedBody.url, '')).slice(0, 512);
  state.latestVersion = { code, name, url };
  buildJsonResponse(res, 200, { ok: true, latest: state.latestVersion });
}

function cleanupState(state, config) {
  const now = Date.now();

  for (const [peerId, session] of state.sessions.entries()) {
    refreshSessionState(session, config, now);
    pruneSessionQueues(state, session, config, now);

    const inactiveFor = now - Math.max(session.lastSeen || 0, session.ts || 0, session.lastHeartbeat || 0);
    const hasPendingMessages = session.messageQueue.length > 0 || session.relayQueue.length > 0;

    if (!hasPendingMessages && inactiveFor > config.peerRetentionMs) {
      removeSession(state, peerId);
    }
  }

  for (const [ip, entry] of state.rateMap.entries()) {
    if (now - entry.windowStart > config.rateWindowMs * 2) {
      state.rateMap.delete(ip);
    }
  }
}

function createHttpHandler(state, options = {}) {
  const config = { ...DEFAULTS, ...options };

  return async function httpHandler(req, res) {
    if (req.method === 'OPTIONS') {
      buildTextResponse(res, 204, '');
      return;
    }

    if (isRateLimited(state, getRemoteIp(req), config)) {
      buildJsonResponse(res, 429, { ok: false, error: 'rate_limited' });
      return;
    }

    const url = new URL(req.url || '/', 'http://localhost');
    const path = sanitizePath(url.pathname);
    const pathWithoutVersion = path === '/v1'
      ? '/'
      : path.replace(/^\/v1(?=\/|$)/, '') || '/';
    const method = String(req.method || 'GET').toUpperCase();

    if (method === 'GET' && (path === '/ping' || path === '/v1/ping')) {
      buildJsonResponse(res, 200, { ok: true });
      return;
    }

    if (method === 'GET' && pathWithoutVersion.startsWith('/lookup/')) {
      handleLookup(state, res, pathWithoutVersion.slice('/lookup/'.length), config);
      return;
    }

    if (method === 'GET' && pathWithoutVersion.startsWith('/poll/')) {
      const peerId = pathWithoutVersion.slice('/poll/'.length);
      const kind = String(url.searchParams.get('kind') || 'signal').toLowerCase() === 'relay'
        ? 'relay'
        : 'signal';
      handlePoll(state, res, peerId, kind, config, url.searchParams);
      return;
    }

    if (method === 'GET' && pathWithoutVersion.startsWith('/heartbeat/')) {
      handleHeartbeat(state, res, pathWithoutVersion.slice('/heartbeat/'.length));
      return;
    }

    if (method === 'GET' && pathWithoutVersion.startsWith('/relay/poll/')) {
      handlePoll(state, res, pathWithoutVersion.slice('/relay/poll/'.length), 'relay', config, url.searchParams);
      return;
    }

    if (method !== 'POST') {
      buildJsonResponse(res, 404, { ok: false, error: 'not_found' });
      return;
    }

    let rawBody = '';
    let parsedBody = {};
    try {
      rawBody = await parseBody(req, config.maxPayloadBytes);
      parsedBody = tryParseBody(rawBody);
    } catch (error) {
      buildJsonResponse(res, error.message === 'too_large' ? 413 : 400, {
        ok: false,
        error: error.message || 'invalid_body',
      });
      return;
    }

    if (path === '/register' || path === '/v1/register') {
      handleRegister(state, req, res, parsedBody, config);
      return;
    }

    if (path === '/signal' || path === '/v1/signal') {
      handleSignalPost(state, res, rawBody, parsedBody, config);
      return;
    }

    if (path === '/presence' || path === '/v1/presence') {
      handlePresencePost(state, res, rawBody, parsedBody, config);
      return;
    }

    if (path === '/relay' || path === '/v1/relay') {
      handleRelayPost(state, res, rawBody, parsedBody, config);
      return;
    }

    if (path === '/admin/set-version' || path === '/v1/admin/set-version') {
      handleAdminVersion(state, res, parsedBody);
      return;
    }

    buildJsonResponse(res, 404, { ok: false, error: 'not_found' });
  };
}

function clearRoom(state, roomCode) {
  const room = state.rooms.get(roomCode);
  if (!room) return;

  state.rooms.delete(roomCode);

  const timeout = state.roomTimeouts.get(roomCode);
  if (timeout) clearTimeout(timeout);
  state.roomTimeouts.delete(roomCode);
}

function attachRoomLifetime(state, roomCode, config) {
  const existing = state.roomTimeouts.get(roomCode);
  if (existing) clearTimeout(existing);

  const timeout = setTimeout(() => clearRoom(state, roomCode), config.roomTtlMs);
  timeout.unref?.();
  state.roomTimeouts.set(roomCode, timeout);
}

function attachPairingWebSocket({ server = null, port = null, path = '/', state, options = {} }) {
  let WebSocket;
  try {
    WebSocket = require('ws');
  } catch (_) {
    return null;
  }

  const config = { ...DEFAULTS, ...options };
  const wsPath = sanitizePath(path || '/');
  const wss = server
    ? new WebSocket.Server({ server, path: wsPath })
    : new WebSocket.Server({ port });

  wss.on('connection', (ws) => {
    let currentRoom = null;

    ws.on('message', (message) => {
      const parsed = tryParseJson(String(message)) || {};

      if (parsed.join) {
        const roomCode = String(parsed.join).trim().toUpperCase().slice(0, 32);
        if (!roomCode) {
          ws.send(JSON.stringify({ error: 'INVALID_ROOM' }));
          return;
        }

        const room = state.rooms.get(roomCode) || [];
        if (room.length >= 2 && !room.includes(ws)) {
          ws.send(JSON.stringify({ error: 'ROOM_FULL' }));
          return;
        }

        if (!room.includes(ws)) room.push(ws);
        state.rooms.set(roomCode, room);
        currentRoom = roomCode;
        attachRoomLifetime(state, roomCode, config);

        if (room.length === 2) {
          room.forEach((peer) => {
            try {
              peer.send(JSON.stringify({ status: 'PEER_CONNECTED' }));
            } catch (_) {
              // Close handling cleans up dead peers.
            }
          });
        }
        return;
      }

      if (parsed.signal && currentRoom) {
        const room = state.rooms.get(currentRoom) || [];
        room.forEach((peer) => {
          if (peer === ws) return;
          try {
            peer.send(JSON.stringify({ signal: parsed.signal }));
          } catch (_) {
            // Close handling cleans up dead peers.
          }
        });
      }
    });

    ws.on('close', () => {
      if (!currentRoom) return;

      const room = state.rooms.get(currentRoom) || [];
      const nextRoom = room.filter((peer) => peer !== ws);

      if (nextRoom.length > 0) {
        state.rooms.set(currentRoom, nextRoom);
        attachRoomLifetime(state, currentRoom, config);
      } else {
        clearRoom(state, currentRoom);
      }
    });
  });

  return wss;
}

function createUnifiedServer(options = {}) {
  const config = { ...DEFAULTS, ...options };
  const state = options.state || createState();
  const handler = createHttpHandler(state, config);

  const server = http.createServer((req, res) => {
    handler(req, res).catch((error) => {
      buildJsonResponse(res, 500, {
        ok: false,
        error: String(error && error.message ? error.message : 'internal_error'),
      });
    });
  });

  const cleanupTimer = setInterval(() => cleanupState(state, config), config.cleanupIntervalMs);
  cleanupTimer.unref?.();

  return {
    server,
    state,
    cleanup() {
      clearInterval(cleanupTimer);
      state.sessions.clear();
      state.signalQueues.clear();
      state.relayQueues.clear();
      state.rateMap.clear();
      state.rooms.clear();
      for (const timeout of state.roomTimeouts.values()) clearTimeout(timeout);
      state.roomTimeouts.clear();
    },
  };
}

function startServer() {
  const PORT = Number(process.env.PORT || 3000);
  const WS_PATH = sanitizePath(process.env.WS_PATH || '/ws');

  const { server, state, cleanup } = createUnifiedServer();
  const webSockets = [];

  const primaryWs = attachPairingWebSocket({ server, state, path: WS_PATH });
  if (primaryWs) webSockets.push(primaryWs);

  if (WS_PATH !== '/') {
    const legacyRootWs = attachPairingWebSocket({ server, state, path: '/' });
    if (legacyRootWs) webSockets.push(legacyRootWs);
  }

  server.on('error', () => process.exit(1));
  server.listen(PORT);

  const shutdown = () => {
    try {
      webSockets.forEach((wss) => {
        try {
          wss.close();
        } catch (_) {
          // Best-effort shutdown.
        }
      });
      cleanup();
      server.close(() => process.exit(0));
      setTimeout(() => process.exit(0), 250).unref?.();
    } catch (_) {
      process.exit(0);
    }
  };

  process.on('SIGTERM', shutdown);
  process.on('SIGINT', shutdown);

  return { server, state, cleanup, webSockets };
}

module.exports = {
  DEFAULTS,
  attachPairingWebSocket,
  buildPeerLookup,
  cleanupState,
  createHttpHandler,
  createState,
  createUnifiedServer,
  normalizePeerId,
  startServer,
};

if (require.main === module) {
  startServer();
}
