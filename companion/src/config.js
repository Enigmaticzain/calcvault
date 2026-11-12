import path from "path";
import { fileURLToPath } from "url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const rootDir = path.resolve(__dirname, "..");

function boolFromEnv(name, fallback = false) {
  const value = process.env[name];
  if (value === undefined) return fallback;
  return ["1", "true", "yes", "on"].includes(String(value).toLowerCase());
}

export function loadConfig() {
  const port = Number(process.env.CV_PORT || 43831);
  // Port 0 asks the OS for an available port (used by the embedded desktop shell).
  if (!Number.isFinite(port) || port < 0 || port > 65535) {
    throw new Error("Invalid CV_PORT");
  }

  return {
    appName: "CalcVault Companion",
    env: process.env.NODE_ENV || "production",
    bindHost: process.env.CV_BIND_HOST || "127.0.0.1",
    port,
    webRoot: path.resolve(rootDir, "web"),
    dataRoot: path.resolve(rootDir, "data"),
    backupRoot: path.resolve(rootDir, "data", "backups"),
    pairingCodeLength: Number(process.env.CV_PAIRING_CODE_LENGTH || 8),
    pairingTtlMs: Number(process.env.CV_PAIRING_TTL_MS || 5 * 60_000),
    sessionTtlMs: Number(process.env.CV_SESSION_TTL_MS || 15 * 60_000),
    opTimeoutMs: Number(process.env.CV_OPERATION_TIMEOUT_MS || 60_000),
    maxChunkBytes: Number(process.env.CV_MAX_CHUNK_BYTES || 256 * 1024),
    maxUploadBytes: Number(process.env.CV_MAX_UPLOAD_BYTES || 5 * 1024 * 1024 * 1024),
    adbPath: process.env.CV_ADB_PATH || "adb",
    phoneBridgePort: Number(process.env.CV_PHONE_BRIDGE_PORT || 37111),
    // Direct LAN file transfer has no integration with Android's per-feature privacy choice.
    // Keep it unavailable; USB/ADB loopback is the only exposed bridge transport.
    allowNetworkTransport: false,
    disableUsbTransport: boolFromEnv("CV_DISABLE_USB_TRANSPORT", false),
    trustedOrigin: process.env.CV_TRUSTED_ORIGIN || "",
  };
}
