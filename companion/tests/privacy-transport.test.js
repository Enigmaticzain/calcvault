import assert from "node:assert/strict";
import test from "node:test";
import { NetworkTransport } from "../src/transport/network.js";

test("direct LAN transfer remains unavailable even when legacy config opts in", async () => {
  const transport = new NetworkTransport({ allowNetworkTransport: true });

  await assert.rejects(
    transport.connect({ url: "ws://192.0.2.10:37111/bridge" }),
    /network_transport_unavailable: direct LAN file transfer is not approved/
  );
});
