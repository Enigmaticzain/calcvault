import { EventEmitter } from "events";
import QRCode from "qrcode";
import {
  createEcdhKeyPair,
  generatePairingCode,
  randomId,
  sha256Base64,
} from "./crypto.js";

export class PairingManager extends EventEmitter {
  constructor(config) {
    super();
    this.config = config;
    this.activePairing = null;
  }

  async begin({ transport, endpointHint = "" }) {
    const pairingId = randomId(12);
    const code = generatePairingCode(this.config.pairingCodeLength);
    const codeHash = sha256Base64(code);
    const ecdh = createEcdhKeyPair();
    const pcNonce = randomId(8);
    const createdAt = Date.now();
    const expiresAt = createdAt + this.config.pairingTtlMs;

    const payload = {
      version: 1,
      pairingId,
      code,
      transport,
      endpointHint,
      expiresAt,
    };

    const pairingUri = `calcvault://pair?data=${encodeURIComponent(Buffer.from(JSON.stringify(payload), "utf8").toString("base64"))}`;
    const qrSvg = await QRCode.toString(pairingUri, {
      type: "svg",
      errorCorrectionLevel: "M",
      margin: 1,
      width: 240,
    });

    this.activePairing = {
      pairingId,
      code,
      codeHash,
      ecdh,
      pcNonce,
      createdAt,
      expiresAt,
      transport,
      endpointHint,
      qrSvg,
      pairingUri,
    };

    this.emit("pairing:created", {
      pairingId,
      expiresAt,
      transport,
      endpointHint,
    });

    return {
      pairingId,
      code,
      expiresAt,
      qrSvg,
      pairingUri,
      transport,
      endpointHint,
    };
  }

  current() {
    if (!this.activePairing) return null;
    if (Date.now() > this.activePairing.expiresAt) {
      this.activePairing = null;
      return null;
    }
    return this.activePairing;
  }

  invalidate() {
    this.activePairing = null;
  }
}
