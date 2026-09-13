import WebSocket from "ws";

function ensureWsUrl(url) {
  const value = String(url || "").trim();
  if (!value) throw new Error("network_url_required");
  if (!value.startsWith("ws://") && !value.startsWith("wss://")) {
    throw new Error("network_url_must_be_ws_or_wss");
  }
  return value;
}

function openWebSocket(url) {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(url, { handshakeTimeout: 12_000 });

    const onError = (error) => {
      ws.removeListener("open", onOpen);
      reject(error);
    };

    const onOpen = () => {
      ws.removeListener("error", onError);
      resolve(ws);
    };

    ws.once("error", onError);
    ws.once("open", onOpen);
  });
}

export class NetworkTransport {
  constructor(config) {
    this.config = config;
  }

  async connect({ url }) {
    if (!this.config.allowNetworkTransport) {
      throw new Error("network_transport_disabled");
    }

    const endpoint = ensureWsUrl(url);
    const ws = await openWebSocket(endpoint);

    return {
      transport: "network",
      endpoint,
      ws,
      cleanup: async () => {
        // No external forwarding resources to release.
      },
    };
  }
}
