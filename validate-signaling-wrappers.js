'use strict';

const dgram = require('dgram');
const WebSocket = require('ws');

const { createUnifiedServer, attachPairingWebSocket } = require('./signaling-server/server');
const signalingWrapper = require('./signaling-server');
const discoveryWrapper = require('./discovery-server');
const relayWrapper = require('./relay-server');

const verbose = process.env.DEBUG_VALIDATE_WRAPPERS === '1';

function log(step) {
  if (!verbose) return;
  console.error(`[validate] ${step}`);
}

function once(emitter, event) {
  return new Promise((resolve) => emitter.once(event, resolve));
}

function maybeAddress(server) {
  try {
    return server.address();
  } catch (_) {
    return null;
  }
}

function withTimeout(promise, timeoutMs, label) {
  return Promise.race([
    promise,
    new Promise((_, reject) => {
      const timer = setTimeout(() => reject(new Error(`${label}_timeout`)), timeoutMs);
      timer.unref?.();
    }),
  ]);
}

async function waitForWsOpen(ws, timeoutMs = 3_000) {
  if (ws.readyState === WebSocket.OPEN) return;

  await new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('ws_open_timeout')), timeoutMs);

    ws.once('open', () => {
      clearTimeout(timer);
      resolve();
    });
    ws.once('error', (error) => {
      clearTimeout(timer);
      reject(error);
    });
  });
}

function waitForWsMessage(ws, predicate, timeoutMs = 4_000) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      cleanup();
      reject(new Error('ws_message_timeout'));
    }, timeoutMs);

    const onMessage = (data) => {
      let parsed;
      try {
        parsed = JSON.parse(String(data));
      } catch (_) {
        parsed = String(data);
      }

      if (!predicate(parsed)) return;
      cleanup();
      resolve(parsed);
    };

    const onError = (error) => {
      cleanup();
      reject(error);
    };

    function cleanup() {
      clearTimeout(timer);
      ws.off('message', onMessage);
      ws.off('error', onError);
    }

    ws.on('message', onMessage);
    ws.on('error', onError);
  });
}

function buildUdpPacket(header, payloadBuffer) {
  const headerBuffer = Buffer.from(JSON.stringify(header), 'utf8');
  const packet = Buffer.allocUnsafe(4 + headerBuffer.length + payloadBuffer.length);
  packet.writeInt32BE(headerBuffer.length, 0);
  headerBuffer.copy(packet, 4);
  payloadBuffer.copy(packet, 4 + headerBuffer.length);
  return packet;
}

function parseUdpPacket(packet) {
  const headerLength = packet.readInt32BE(0);
  const header = JSON.parse(packet.slice(4, 4 + headerLength).toString('utf8'));
  const payload = packet.slice(4 + headerLength);
  return { header, payload };
}

async function request(base, path, options = {}) {
  const response = await fetch(`${base}${path}`, options);
  const text = await response.text();

  let json = null;
  try {
    json = text ? JSON.parse(text) : null;
  } catch (_) {
    json = null;
  }

  return { status: response.status, text, json };
}

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

async function closeWebSocket(ws) {
  if (!ws || ws.readyState === WebSocket.CLOSED) return;

  await withTimeout(
    new Promise((resolve) => {
      const done = () => resolve();

      ws.once('close', done);
      ws.once('error', done);
      try {
        ws.close();
      } catch (_) {
        resolve();
      }
    }),
    1_000,
    'ws_close'
  ).catch(() => {
    try {
      ws.terminate();
    } catch (_) {
      // Ignore forced-close errors.
    }
  });
}

async function main() {
  const checks = [];

  const canonical = createUnifiedServer({
    rateLimit: 10_000,
    cleanupIntervalMs: 50,
    presenceTtlMs: 5_000,
    peerRetentionMs: 10_000,
  });
  attachPairingWebSocket({ server: canonical.server, state: canonical.state, path: '/ws' });
  log('starting canonical core');
  await new Promise((resolve) => canonical.server.listen(0, resolve));

  const canonicalBase = `http://127.0.0.1:${canonical.server.address().port}`;
  log(`canonical listening on ${canonicalBase}`);
  const signaling = signalingWrapper.startServer({ port: 0, httpBaseUrl: canonicalBase });
  const discovery = discoveryWrapper.startServer({ port: 0, httpBaseUrl: canonicalBase });
  const relay = relayWrapper.startServer({ wsPort: 0, udpPort: 0, httpBaseUrl: canonicalBase });

  const resources = [];
  try {
    if (!signaling.server.listening) await once(signaling.server, 'listening');
    if (!discovery.server.listening) await once(discovery.server, 'listening');
    if (!maybeAddress(relay.udpServer)) await once(relay.udpServer, 'listening');

    const signalingBase = `http://127.0.0.1:${signaling.server.address().port}`;
    const discoveryBase = `http://127.0.0.1:${discovery.server.address().port}`;
    const relayWsPort = relay.webSocketServer.address().port;
    const relayUdpPort = relay.udpServer.address().port;
    log(`wrappers listening on signaling=${signalingBase} discovery=${discoveryBase} relayWs=${relayWsPort} relayUdp=${relayUdpPort}`);

    log('checking http register forwarding');
    const registerAlpha = await request(signalingBase, '/register', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ deviceId: 'alpha' }),
    });
    assert(registerAlpha.status === 200 && registerAlpha.json && registerAlpha.json.ok === true, 'signaling wrapper register failed');

    const registerBravo = await request(discoveryBase, '/v1/register', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ device_id: 'bravo' }),
    });
    assert(registerBravo.status === 200 && registerBravo.json && registerBravo.json.ok === true, 'discovery wrapper register failed');
    checks.push('http_register_forwarding');

    log('checking cross-wrapper signal flow');
    const signalPost = await request(discoveryBase, '/signal', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ from: 'alpha', to: 'bravo', type: 'offer', order: 42 }),
    });
    assert(signalPost.status === 200 && signalPost.text === 'ok', 'wrapper signal post failed');

    const pollBravo = await request(signalingBase, '/poll/bravo');
    assert(pollBravo.status === 200 && pollBravo.json && pollBravo.json.order === 42, 'wrapper signal poll mismatch');
    checks.push('cross_wrapper_signal_flow');

    log('checking legacy text shapes');
    const heartbeat = await request(discoveryBase, '/heartbeat/alpha');
    assert(heartbeat.status === 200 && heartbeat.text === 'ok', 'heartbeat text response mismatch');

    const presence = await request(signalingBase, '/presence', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ from: 'alpha', to: 'bravo', status: 'ONLINE' }),
    });
    assert(presence.status === 200 && presence.text === 'ok', 'presence text response mismatch');
    checks.push('legacy_text_shapes');

    log('checking signaling websocket pairing proxy');
    const wsA = new WebSocket(`ws://127.0.0.1:${signaling.server.address().port}/`);
    const wsB = new WebSocket(`ws://127.0.0.1:${signaling.server.address().port}/`);
    resources.push(() => closeWebSocket(wsA), () => closeWebSocket(wsB));

    await Promise.all([waitForWsOpen(wsA), waitForWsOpen(wsB)]);
    const peerConnectedA = waitForWsMessage(wsA, (message) => message && message.status === 'PEER_CONNECTED');
    const peerConnectedB = waitForWsMessage(wsB, (message) => message && message.status === 'PEER_CONNECTED');
    wsA.send(JSON.stringify({ join: 'ROOM42' }));
    wsB.send(JSON.stringify({ join: 'ROOM42' }));
    await Promise.all([peerConnectedA, peerConnectedB]);

    const forwardedSignal = waitForWsMessage(wsB, (message) => message && message.signal === 'hello-core');
    wsA.send(JSON.stringify({ signal: 'hello-core' }));
    await forwardedSignal;
    checks.push('signaling_ws_pairing_proxy');

    log('checking relay websocket invalid-message handling');
    const relayWs = new WebSocket(`ws://127.0.0.1:${relayWsPort}`);
    resources.push(() => closeWebSocket(relayWs));
    await waitForWsOpen(relayWs);
    relayWs.send(JSON.stringify({ type: 'auth', device_id: 'relay-b' }));

    const malformedRelayWs = waitForWsMessage(
      relayWs,
      (message) => message && message.error === 'INVALID_RELAY' && message.detail === 'malformed_json'
    );
    relayWs.send('not-json');
    await malformedRelayWs;
    checks.push('relay_ws_invalid_message_error');

    log('checking relay udp invalid-packet handling');
    const udpClient = dgram.createSocket('udp4');
    resources.push(() => new Promise((resolve) => udpClient.close(resolve)));

    const malformedUdpReply = await new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error('udp_invalid_reply_timeout')), 4_000);
      udpClient.once('message', (packet) => {
        clearTimeout(timer);
        resolve(parseUdpPacket(packet));
      });
      udpClient.send(Buffer.from('bad-packet'), relayUdpPort, '127.0.0.1');
    });
    assert(malformedUdpReply.header.type === 'error', 'relay UDP invalid packet response missing');
    checks.push('relay_udp_invalid_message_error');

    log('checking relay udp-to-ws flow');
    const relayMessage = waitForWsMessage(
      relayWs,
      (message) =>
        message
        && message.sid === 'relay-a'
        && message.rid === 'relay-b'
        && message.data === Buffer.from('hello-relay').toString('base64')
    );
    udpClient.send(
      buildUdpPacket(
        { mid: 1, sid: 'relay-a', rid: 'relay-b', type: 'blob', ts: Date.now() },
        Buffer.from('hello-relay')
      ),
      relayUdpPort,
      '127.0.0.1'
    );
    await relayMessage;
    checks.push('relay_udp_to_ws_flow');

    log('checking relay ws-to-udp flow');
    relayWs.send(JSON.stringify({
      mid: 2,
      sid: 'relay-b',
      rid: 'relay-a',
      type: 'blob',
      ts: Date.now(),
      data: Buffer.from('pong-relay').toString('base64'),
    }));

    const udpReply = await new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error('udp_reply_timeout')), 4_000);
      udpClient.once('message', (packet) => {
        clearTimeout(timer);
        resolve(parseUdpPacket(packet));
      });
      udpClient.send(
        buildUdpPacket(
          { mid: 3, sid: 'relay-a', rid: 'relay-b', type: 'blob', ts: Date.now() },
          Buffer.from('keepalive')
        ),
        relayUdpPort,
        '127.0.0.1'
      );
    });
    assert(udpReply.header.sid === 'relay-b' && udpReply.header.rid === 'relay-a', 'relay UDP return path mismatch');
    assert(udpReply.payload.toString('utf8') === 'pong-relay', 'relay UDP payload mismatch');
    checks.push('relay_ws_to_udp_flow');

    log('checking canonical failure response');
    canonical.server.close(() => {});
    canonical.server.closeAllConnections?.();
    canonical.server.closeIdleConnections?.();
    canonical.cleanup();
    await new Promise((resolve) => setTimeout(resolve, 100));

    const unavailablePing = await request(signalingBase, '/ping');
    assert(unavailablePing.status === 503, 'wrapper should return 503 when canonical is down');
    assert(unavailablePing.json && unavailablePing.json.error === 'canonical_unavailable', 'wrapper unavailable response mismatch');
    checks.push('canonical_failure_response');
  } finally {
    log('starting cleanup');
    while (resources.length > 0) {
      const close = resources.pop();
      await withTimeout(close().catch(() => {}), 1_500, 'resource_cleanup').catch(() => {});
    }

    await Promise.allSettled([
      withTimeout(signaling.stop(), 2_000, 'signaling_stop'),
      withTimeout(discovery.stop(), 2_000, 'discovery_stop'),
      withTimeout(relay.stop(), 2_000, 'relay_stop'),
    ]);

    if (canonical.server.listening) {
      canonical.cleanup();
      await withTimeout(new Promise((resolve) => canonical.server.close(resolve)), 2_000, 'canonical_stop').catch(() => {});
    }
    log('cleanup finished');
  }

  log('validation complete');
  console.log(JSON.stringify({ ok: true, checks }));
}

main().catch((error) => {
  console.error(error.stack || error.message || String(error));
  process.exit(1);
});
