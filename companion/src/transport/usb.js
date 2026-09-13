import WebSocket from "ws";
import { runCommand } from "../util/exec.js";
import { getFreeTcpPort } from "../util/net.js";

function parseAdbDevices(stdout) {
  const lines = String(stdout || "").split(/\r?\n/).slice(1);
  const devices = [];

  for (const line of lines) {
    const value = line.trim();
    if (!value) continue;
    const [serial, state, ...rest] = value.split(/\s+/);
    if (!serial || !state || state !== "device") continue;

    const details = {};
    for (const part of rest) {
      const [key, rawValue] = part.split(":");
      if (!key || !rawValue) continue;
      details[key] = rawValue;
    }

    devices.push({
      serial,
      model: details.model || "unknown",
      transportId: details.transport_id || "",
      product: details.product || "",
      usb: details.usb || "",
    });
  }

  return devices;
}

function openWebSocket(url) {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(url, { handshakeTimeout: 10_000 });

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

export class UsbTransport {
  constructor(config, logger) {
    this.config = config;
    this.logger = logger;
  }

  async listDevices() {
    if (this.config.disableUsbTransport) return [];

    const result = await runCommand(this.config.adbPath, ["devices", "-l"], { timeoutMs: 12_000 });
    if (!result.ok) {
      this.logger.warn("ADB device scan failed", { stderr: result.stderr.trim() });
      return [];
    }

    return parseAdbDevices(result.stdout);
  }

  async connect({ serial }) {
    if (this.config.disableUsbTransport) {
      throw new Error("usb_transport_disabled");
    }

    if (!serial) {
      throw new Error("usb_serial_required");
    }

    const localPort = await getFreeTcpPort(this.config.bindHost);
    const forward = await runCommand(
      this.config.adbPath,
      ["-s", serial, "forward", `tcp:${localPort}`, `tcp:${this.config.phoneBridgePort}`],
      { timeoutMs: 12_000 }
    );

    if (!forward.ok) {
      throw new Error(`adb_forward_failed:${forward.stderr.trim()}`);
    }

    const url = `ws://${this.config.bindHost}:${localPort}/bridge`;
    try {
      const ws = await openWebSocket(url);
      const cleanup = async () => {
        await runCommand(this.config.adbPath, ["-s", serial, "forward", "--remove", `tcp:${localPort}`], {
          timeoutMs: 8_000,
        });
      };

      return {
        transport: "usb",
        serial,
        endpoint: url,
        ws,
        cleanup,
      };
    } catch (error) {
      await runCommand(this.config.adbPath, ["-s", serial, "forward", "--remove", `tcp:${localPort}`], {
        timeoutMs: 8_000,
      });
      throw error;
    }
  }
}
