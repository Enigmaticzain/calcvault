export class NetworkTransport {
  constructor(_config) {
    // Retain the constructor contract while the phone has no consented LAN route.
  }

  async connect() {
    throw new Error(
      "network_transport_unavailable: direct LAN file transfer is not approved by the Android File Sharing privacy route; use USB local-only"
    );
  }
}
