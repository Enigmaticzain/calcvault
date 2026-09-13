'use strict';

const http = require('http');
const https = require('https');
const { URL } = require('url');
const WebSocket = require('ws');

const HOP_BY_HOP_HEADERS = new Set([
  'connection',
  'keep-alive',
  'proxy-authenticate',
  'proxy-authorization',
  'te',
  'trailer',
  'transfer-encoding',
  'upgrade',
]);

function sanitizeBaseUrl(value) {
  return String(value || '').trim().replace(/\/+$/, '');
}

function normalizeDeviceId(value) {
  const raw = String(value || '').trim();
  if (!raw) return '';

  try {
    return decodeURIComponent(raw).trim().slice(0, 128);
  } catch (_) {
    return raw.slice(0, 128);
  }
}

function getCanonicalHttpBase(options = {}) {
  return sanitizeBaseUrl(
    options.httpBaseUrl
      || process.env.CANONICAL_SIGNALING_HTTP_URL
      || process.env.CANONICAL_SIGNALING_URL
      || 'http://127.0.0.1:3000'
  );
}

function getCanonicalWsBase(options = {}) {
  const explicit = sanitizeBaseUrl(
    options.wsBaseUrl
      || process.env.CANONICAL_SIGNALING_WS_URL
      || process.env.CANONICAL_SIGNALING_WEBSOCKET_URL
  );

  if (explicit) return explicit;

  const base = new URL(`${getCanonicalHttpBase(options)}/`);
  const protocol = base.protocol === 'https:' ? 'wss:' : 'ws:';
  return sanitizeBaseUrl(`${protocol}//${base.host}${base.pathname}`);
}

function joinPaths(prefix, pathname) {
  const left = prefix && prefix !== '/' ? prefix.replace(/\/+$/, '') : '';
  const right = String(pathname || '/').startsWith('/') ? String(pathname || '/') : `/${pathname || ''}`;
  return `${left}${right}` || '/';
}

function buildForwardUrl(baseUrl, requestUrl, mapPath = (value) => value) {
  const base = new URL(`${sanitizeBaseUrl(baseUrl)}/`);
  const incoming = new URL(requestUrl || '/', 'http://wrapper.invalid');
  const target = new URL(base.toString());
  target.pathname = joinPaths(base.pathname, mapPath(incoming.pathname));
  target.search = incoming.search;
  return target;
}

function getTransportForProtocol(protocol) {
  return protocol === 'https:' ? https : http;
}

function stripHopByHopHeaders(headers) {
  const next = {};
  for (const [name, value] of Object.entries(headers || {})) {
    if (HOP_BY_HOP_HEADERS.has(String(name).toLowerCase())) continue;
    if (value === undefined) continue;
    next[name] = value;
  }
  return next;
}

function buildCompatibilityErrorHeaders(contentType) {
  return {
    'Content-Type': contentType,
    'Cache-Control': 'no-store',
    'X-Content-Type-Options': 'nosniff',
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'GET,POST,OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type',
  };
}

function pathExpectsPlainText(reqUrl) {
  const pathname = new URL(reqUrl || '/', 'http://wrapper.invalid').pathname;

  if (pathname.startsWith('/heartbeat/') || pathname.startsWith('/v1/heartbeat/')) {
    return true;
  }

  return new Set([
    '/signal',
    '/v1/signal',
    '/presence',
    '/v1/presence',
    '/relay',
    '/v1/relay',
  ]).has(pathname);
}

function writeCompatibilityError(req, res, statusCode = 503, errorCode = 'canonical_unavailable') {
  if (res.headersSent) {
    res.destroy();
    return;
  }

  if (pathExpectsPlainText(req.url)) {
    res.writeHead(statusCode, buildCompatibilityErrorHeaders('text/plain; charset=utf-8'));
    res.end('error');
    return;
  }

  res.writeHead(statusCode, buildCompatibilityErrorHeaders('application/json; charset=utf-8'));
  res.end(JSON.stringify({ ok: false, error: errorCode }));
}

function proxyHttpRequest(req, res, options = {}) {
  const targetUrl = buildForwardUrl(
    getCanonicalHttpBase(options),
    req.url,
    options.mapRequestPath || ((value) => value)
  );

  const transport = getTransportForProtocol(targetUrl.protocol);
  const headers = stripHopByHopHeaders(req.headers);
  headers.host = targetUrl.host;

  const upstream = transport.request(
    {
      protocol: targetUrl.protocol,
      hostname: targetUrl.hostname,
      port: targetUrl.port || undefined,
      method: req.method,
      path: `${targetUrl.pathname}${targetUrl.search}`,
      headers,
    },
    (upstreamRes) => {
      const responseHeaders = stripHopByHopHeaders(upstreamRes.headers);
      res.writeHead(upstreamRes.statusCode || 502, responseHeaders);
      upstreamRes.pipe(res);
    }
  );

  upstream.on('error', () => {
    writeCompatibilityError(req, res);
  });

  req.on('aborted', () => upstream.destroy());
  req.pipe(upstream);
}

function forwardableWsHeaders(headers) {
  const next = {};
  for (const name of ['origin', 'cookie', 'authorization', 'user-agent']) {
    if (headers && headers[name]) next[name] = headers[name];
  }
  return next;
}

function bridgeWebSockets(clientWs, upstreamWs) {
  let closed = false;

  function closeBoth(code, reason) {
    if (closed) return;
    closed = true;

    if (clientWs.readyState === WebSocket.OPEN || clientWs.readyState === WebSocket.CONNECTING) {
      try {
        clientWs.close(code, reason);
      } catch (_) {
        clientWs.terminate();
      }
    }

    if (upstreamWs.readyState === WebSocket.OPEN || upstreamWs.readyState === WebSocket.CONNECTING) {
      try {
        upstreamWs.close(code, reason);
      } catch (_) {
        upstreamWs.terminate();
      }
    }
  }

  clientWs.on('message', (data, isBinary) => {
    if (upstreamWs.readyState !== WebSocket.OPEN) return;
    upstreamWs.send(data, { binary: isBinary }, (error) => {
      if (error) closeBoth(1011, 'upstream_send_failed');
    });
  });

  upstreamWs.on('message', (data, isBinary) => {
    if (clientWs.readyState !== WebSocket.OPEN) return;
    clientWs.send(data, { binary: isBinary }, (error) => {
      if (error) closeBoth(1011, 'client_send_failed');
    });
  });

  clientWs.on('close', (code, reason) => closeBoth(code, reason));
  upstreamWs.on('close', (code, reason) => closeBoth(code, reason));
  clientWs.on('error', () => closeBoth(1011, 'client_error'));
  upstreamWs.on('error', () => closeBoth(1011, 'upstream_error'));
}

function attachWebSocketProxy(server, options = {}) {
  const wss = new WebSocket.Server({ noServer: true });

  server.on('upgrade', (req, socket, head) => {
    const targetUrl = buildForwardUrl(
      getCanonicalWsBase(options),
      req.url,
      options.mapWebSocketPath || ((value) => value)
    );

    let responded = false;
    const upstreamWs = new WebSocket(targetUrl.toString(), {
      headers: forwardableWsHeaders(req.headers),
      perMessageDeflate: false,
    });

    function failUpgrade() {
      if (responded) return;
      responded = true;
      try {
        socket.write('HTTP/1.1 502 Bad Gateway\r\nConnection: close\r\n\r\n');
      } catch (_) {
        // Best-effort error response.
      }
      socket.destroy();
      try {
        upstreamWs.terminate();
      } catch (_) {
        // Ignore.
      }
    }

    upstreamWs.once('open', () => {
      if (responded) return;
      responded = true;
      wss.handleUpgrade(req, socket, head, (clientWs) => {
        bridgeWebSockets(clientWs, upstreamWs);
      });
    });

    upstreamWs.once('error', failUpgrade);
    socket.on('error', failUpgrade);
  });

  return wss;
}

function canonicalRequest(options = {}) {
  const method = String(options.method || 'GET').toUpperCase();
  const baseUrl = getCanonicalHttpBase(options);
  const targetUrl = buildForwardUrl(baseUrl, options.path || '/', options.mapRequestPath || ((value) => value));
  const transport = getTransportForProtocol(targetUrl.protocol);

  const headers = stripHopByHopHeaders(options.headers || {});
  headers.host = targetUrl.host;

  let body = options.body;
  if (body === undefined && options.json !== undefined) {
    body = JSON.stringify(options.json);
    if (!headers['Content-Type'] && !headers['content-type']) {
      headers['Content-Type'] = 'application/json; charset=utf-8';
    }
  }

  if (body !== undefined && !headers['Content-Length'] && !headers['content-length']) {
    headers['Content-Length'] = Buffer.byteLength(body);
  }

  return new Promise((resolve, reject) => {
    const req = transport.request(
      {
        protocol: targetUrl.protocol,
        hostname: targetUrl.hostname,
        port: targetUrl.port || undefined,
        method,
        path: `${targetUrl.pathname}${targetUrl.search}`,
        headers,
      },
      (res) => {
        const chunks = [];

        res.on('data', (chunk) => chunks.push(chunk));
        res.on('end', () => {
          const text = Buffer.concat(chunks).toString('utf8');
          let json = null;

          try {
            json = text ? JSON.parse(text) : null;
          } catch (_) {
            json = null;
          }

          resolve({
            statusCode: res.statusCode || 0,
            headers: res.headers,
            text,
            json,
          });
        });
      }
    );

    req.on('error', reject);

    if (body !== undefined) req.end(body);
    else req.end();
  });
}

function startCompatibilityProxy(options = {}) {
  const server = http.createServer((req, res) => proxyHttpRequest(req, res, options));

  let wss = null;
  if (options.enableWebSocket) {
    wss = attachWebSocketProxy(server, options);
  }

  server.listen(Number(options.port ?? 0));

  return {
    server,
    webSocketServer: wss,
    stop() {
      if (wss) {
        for (const client of wss.clients) {
          try {
            client.terminate();
          } catch (_) {
            // Ignore teardown errors.
          }
        }

        try {
          wss.close();
        } catch (_) {
          // Ignore shutdown errors.
        }
      }

      return new Promise((resolve) => server.close(resolve));
    },
  };
}

module.exports = {
  buildCompatibilityErrorHeaders,
  buildForwardUrl,
  canonicalRequest,
  getCanonicalHttpBase,
  getCanonicalWsBase,
  normalizeDeviceId,
  proxyHttpRequest,
  startCompatibilityProxy,
  stripHopByHopHeaders,
  writeCompatibilityError,
};
