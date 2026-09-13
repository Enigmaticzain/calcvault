import fs from "fs";
import fsp from "fs/promises";
import path from "path";
import { createHash } from "crypto";
import { ensureDir, createWriteStreamAtomic, sanitizeFileName, fileExists } from "../util/fs.js";

export class BackupStore {
  constructor(config, logger) {
    this.config = config;
    this.logger = logger;
  }

  async init() {
    await ensureDir(this.config.backupRoot);
  }

  async listBackups() {
    await this.init();
    const deviceDirs = await fsp.readdir(this.config.backupRoot, { withFileTypes: true });
    const items = [];

    for (const entry of deviceDirs) {
      if (!entry.isDirectory()) continue;
      const deviceId = entry.name;
      const dir = path.join(this.config.backupRoot, deviceId);
      const files = await fsp.readdir(dir, { withFileTypes: true });
      for (const file of files) {
        if (!file.isFile() || !file.name.endsWith(".cvb")) continue;
        const filePath = path.join(dir, file.name);
        const stat = await fsp.stat(filePath);
        const backupId = file.name.replace(/\.cvb$/i, "");

        let metadata = {};
        const metadataPath = `${filePath}.meta.json`;
        if (await fileExists(metadataPath)) {
          try {
            metadata = JSON.parse(await fsp.readFile(metadataPath, "utf8"));
          } catch (_) {
            metadata = {};
          }
        }

        items.push({
          backupId,
          deviceId,
          filePath,
          size: stat.size,
          createdAt: metadata.createdAt || stat.mtimeMs,
          sha256: metadata.sha256 || "",
          backupVersion: metadata.backupVersion || 1,
          encrypted: true,
        });
      }
    }

    items.sort((a, b) => Number(b.createdAt) - Number(a.createdAt));
    return items;
  }

  async createWriter({ deviceId, backupVersion = 1 }) {
    await this.init();
    const safeDevice = sanitizeFileName(deviceId || "unknown-device");
    const dir = path.join(this.config.backupRoot, safeDevice);
    await ensureDir(dir);

    const stamp = new Date().toISOString().replace(/[:.]/g, "-");
    const backupId = `${safeDevice}_${stamp}_v${backupVersion}`;
    const filePath = path.join(dir, `${backupId}.cvb`);
    const atomic = createWriteStreamAtomic(filePath);
    const hash = createHash("sha256");

    return {
      backupId,
      filePath,
      append(base64Chunk) {
        const chunk = Buffer.from(String(base64Chunk || ""), "base64");
        hash.update(chunk);
        return new Promise((resolve, reject) => {
          if (!atomic.stream.write(chunk)) {
            atomic.stream.once("drain", resolve);
            return;
          }
          resolve();
        });
      },
      async finalize(metadata = {}) {
        await new Promise((resolve, reject) => {
          atomic.stream.end((error) => {
            if (error) reject(error);
            else resolve();
          });
        });

        const digest = hash.digest("hex");
        if (metadata.expectedSha256 && metadata.expectedSha256 !== digest) {
          await atomic.discard();
          throw new Error("backup_hash_mismatch");
        }

        await atomic.commit();

        const sidecar = {
          backupId,
          deviceId: safeDevice,
          createdAt: Date.now(),
          sha256: digest,
          backupVersion,
          ...metadata,
        };
        await fsp.writeFile(`${filePath}.meta.json`, JSON.stringify(sidecar, null, 2), "utf8");

        return {
          ...sidecar,
          filePath,
          size: (await fsp.stat(filePath)).size,
          encrypted: true,
        };
      },
      async abort() {
        atomic.stream.destroy();
        await atomic.discard();
      },
    };
  }

  async openBackupStream(backupId) {
    const backups = await this.listBackups();
    const target = backups.find((item) => item.backupId === backupId);
    if (!target) {
      throw new Error("backup_not_found");
    }

    return {
      metadata: target,
      stream: fs.createReadStream(target.filePath),
    };
  }
}
