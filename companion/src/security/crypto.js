import {
  createHash,
  createHmac,
  createCipheriv,
  createDecipheriv,
  diffieHellman,
  generateKeyPairSync,
  createPublicKey,
  hkdfSync,
  randomBytes,
  timingSafeEqual,
} from "crypto";

const PAIRING_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

export function generatePairingCode(length = 8) {
  const out = [];
  for (let i = 0; i < length; i += 1) {
    const idx = randomBytes(1)[0] % PAIRING_ALPHABET.length;
    out.push(PAIRING_ALPHABET[idx]);
  }
  return out.join("");
}

export function sha256Base64(value) {
  return createHash("sha256").update(value).digest("base64");
}

export function createEcdhKeyPair() {
  const { privateKey, publicKey } = generateKeyPairSync("x25519");
  return {
    privateKey,
    publicKeyDer: publicKey.export({ format: "der", type: "spki" }).toString("base64"),
  };
}

export function deriveSessionKey({ privateKey, peerPublicDerBase64, pairingCode, saltParts = [] }) {
  const peerPublicKey = createPublicKey({
    key: Buffer.from(peerPublicDerBase64, "base64"),
    format: "der",
    type: "spki",
  });

  const sharedSecret = diffieHellman({ privateKey, publicKey: peerPublicKey });
  const saltHash = createHash("sha256");
  saltHash.update(Buffer.from(String(pairingCode), "utf8"));
  for (const part of saltParts) {
    saltHash.update(Buffer.from(String(part), "utf8"));
  }

  return hkdfSync("sha256", sharedSecret, saltHash.digest(), Buffer.from("calcvault-session-v1", "utf8"), 32);
}

export function computePairingProof({ pairingCode, sessionId, pcPub, phonePub, pcNonce, phoneNonce, deviceId }) {
  const transcript = [sessionId, pcPub, phonePub, pcNonce, phoneNonce, deviceId || ""].join("|");
  return createHmac("sha256", Buffer.from(String(pairingCode), "utf8"))
    .update(Buffer.from(transcript, "utf8"))
    .digest("base64");
}

export function safeEqualBase64(expected, candidate) {
  const a = Buffer.from(String(expected || ""), "base64");
  const b = Buffer.from(String(candidate || ""), "base64");
  if (a.length !== b.length) return false;
  return timingSafeEqual(a, b);
}

function buildDirectionalNonce(seq, directionFlag) {
  const nonce = Buffer.alloc(12);
  nonce.writeUInt32BE(0x43564231, 0); // "CVB1"
  nonce.writeUInt8(directionFlag & 0xff, 4);
  nonce.writeUInt32BE(seq >>> 0, 8);
  return nonce;
}

export class SessionCipher {
  constructor({ sessionId, sessionKey, sendDirection = 1, recvDirection = 2 }) {
    this.sessionId = sessionId;
    this.sessionKey = Buffer.from(sessionKey);
    this.sendDirection = Number(sendDirection || 1);
    this.recvDirection = Number(recvDirection || 2);
    this.sendSeq = 1;
    this.recvSeq = 0;
  }

  encrypt(payload) {
    const seq = this.sendSeq;
    this.sendSeq += 1;

    const nonce = buildDirectionalNonce(seq, this.sendDirection);
    const cipher = createCipheriv("aes-256-gcm", this.sessionKey, nonce);
    const plaintext = Buffer.from(JSON.stringify(payload), "utf8");
    const ciphertext = Buffer.concat([cipher.update(plaintext), cipher.final()]);
    const tag = cipher.getAuthTag();

    return {
      type: "enc",
      sid: this.sessionId,
      seq,
      iv: nonce.toString("base64"),
      ct: ciphertext.toString("base64"),
      tag: tag.toString("base64"),
    };
  }

  decrypt(envelope) {
    if (!envelope || envelope.type !== "enc") {
      throw new Error("invalid_envelope");
    }

    const seq = Number(envelope.seq || 0);
    if (!Number.isFinite(seq) || seq <= this.recvSeq) {
      throw new Error("replay_detected");
    }

    const nonce = Buffer.from(String(envelope.iv || ""), "base64");
    const expectedNonce = buildDirectionalNonce(seq, this.recvDirection);
    if (nonce.length !== expectedNonce.length || !nonce.equals(expectedNonce)) {
      throw new Error("nonce_mismatch");
    }

    const decipher = createDecipheriv("aes-256-gcm", this.sessionKey, nonce);
    decipher.setAuthTag(Buffer.from(String(envelope.tag || ""), "base64"));

    const plaintext = Buffer.concat([
      decipher.update(Buffer.from(String(envelope.ct || ""), "base64")),
      decipher.final(),
    ]);

    this.recvSeq = seq;
    return JSON.parse(plaintext.toString("utf8"));
  }
}

export function randomId(bytes = 16) {
  return randomBytes(bytes).toString("hex");
}
