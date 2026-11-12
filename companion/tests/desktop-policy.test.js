import assert from "node:assert/strict";
import test from "node:test";
import { isPermittedDownloadWindow } from "../desktop/window-policy.js";

test("desktop shell permits only same-origin attachment windows", () => {
  const origin = "http://127.0.0.1:43831";
  assert.equal(
    isPermittedDownloadWindow(`${origin}/api/files/file-1/download?mode=encrypted`, origin),
    true
  );
  assert.equal(isPermittedDownloadWindow("https://example.com/", origin), false);
  assert.equal(isPermittedDownloadWindow(`${origin}/api/status`, origin), false);
  assert.equal(isPermittedDownloadWindow("not a URL", origin), false);
});
