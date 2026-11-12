import assert from "node:assert/strict";
import test from "node:test";
import { loadConfig } from "../src/config.js";

test("port zero is accepted for a random OS-assigned loopback server port", () => {
  const original = process.env.CV_PORT;
  process.env.CV_PORT = "0";
  try {
    assert.equal(loadConfig().port, 0);
  } finally {
    if (original === undefined) delete process.env.CV_PORT;
    else process.env.CV_PORT = original;
  }
});
