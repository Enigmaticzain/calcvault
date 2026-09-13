import fs from "fs";
import fsp from "fs/promises";
import path from "path";

export async function ensureDir(dirPath) {
  await fsp.mkdir(dirPath, { recursive: true });
}

export function sanitizeFileName(value) {
  return String(value || "file")
    .replace(/[^a-zA-Z0-9._-]/g, "_")
    .replace(/_{2,}/g, "_")
    .slice(0, 180);
}

export function createWriteStreamAtomic(filePath) {
  const dir = path.dirname(filePath);
  fs.mkdirSync(dir, { recursive: true });
  const tempPath = `${filePath}.tmp-${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;
  const stream = fs.createWriteStream(tempPath, { flags: "wx" });

  return {
    stream,
    tempPath,
    async commit() {
      await fsp.rename(tempPath, filePath);
    },
    async discard() {
      try {
        await fsp.unlink(tempPath);
      } catch (_) {
        // Ignore best-effort cleanup errors.
      }
    },
  };
}

export async function fileExists(filePath) {
  try {
    await fsp.access(filePath);
    return true;
  } catch (_) {
    return false;
  }
}
