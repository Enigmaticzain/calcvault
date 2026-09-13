import { spawn } from "child_process";

export function runCommand(command, args = [], options = {}) {
  return new Promise((resolve) => {
    const child = spawn(command, args, {
      stdio: ["ignore", "pipe", "pipe"],
      timeout: options.timeoutMs || 15_000,
    });

    let stdout = "";
    let stderr = "";

    child.stdout.on("data", (chunk) => {
      stdout += chunk.toString("utf8");
    });

    child.stderr.on("data", (chunk) => {
      stderr += chunk.toString("utf8");
    });

    child.on("error", (error) => {
      resolve({ ok: false, code: -1, stdout, stderr, error });
    });

    child.on("close", (code, signal) => {
      const ok = code === 0;
      resolve({ ok, code: code ?? -1, signal, stdout, stderr });
    });
  });
}
