import { createLogger } from "../util/logger.js";

const logger = createLogger({ level: "info", label: "signaling" });

class SignalingService {
  constructor() {
    this.deviceRegistry = new Map(); // deviceId -> { lastSeen, ip }
    this.messageQueues = new Map(); // deviceId -> [messages]
    this.signalQueues = new Map(); // deviceId -> [signals]
  }

  register(deviceId, ip) {
    if (!deviceId) return false;
    this.deviceRegistry.set(deviceId, { lastSeen: Date.now(), ip });
    logger.debug(`Device registered: ${deviceId} from ${ip}`);
    return true;
  }

  heartbeat(deviceId) {
    const info = this.deviceRegistry.get(deviceId);
    if (info) {
      info.lastSeen = Date.now();
      return true;
    }
    return false;
  }

  lookup(deviceId) {
    const info = this.deviceRegistry.get(deviceId);
    if (info) {
      const online = Date.now() - info.lastSeen < 60000;
      return { ok: true, deviceId, online, ip: info.ip };
    }
    return { ok: false, error: "not_found" };
  }

  relay(message) {
    const to = message.to;
    if (!to) return false;
    if (!this.messageQueues.has(to)) {
      this.messageQueues.set(to, []);
    }
    this.messageQueues.get(to).push(message);
    logger.debug(`Relayed message to ${to}`);
    return true;
  }

  signal(message) {
    const to = message.to;
    if (!to) return false;
    if (!this.signalQueues.has(to)) {
      this.signalQueues.set(to, []);
    }
    this.signalQueues.get(to).push(message);
    logger.debug(`Relayed signal to ${to}`);
    return true;
  }

  poll(deviceId, kind) {
    const queueMap = kind === "signal" ? this.signalQueues : this.messageQueues;
    const queue = queueMap.get(deviceId);

    if (!queue || queue.length === 0) {
      return { type: "none" };
    }

    const messages = [...queue];
    queue.length = 0; // Clear the queue

    return {
      type: "batch",
      messages,
    };
  }

  cleanup() {
    const now = Date.now();
    // Prune stale devices and queues
    for (const [deviceId, info] of this.deviceRegistry.entries()) {
      if (now - info.lastSeen > 300000) { // 5 minutes
        this.deviceRegistry.delete(deviceId);
        this.messageQueues.delete(deviceId);
        this.signalQueues.delete(deviceId);
        logger.debug(`Pruned stale device: ${deviceId}`);
      }
    }
  }
}

export const signalingService = new SignalingService();

// Periodically cleanup
setInterval(() => signalingService.cleanup(), 60000).unref();
