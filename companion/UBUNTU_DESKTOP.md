# CalcVault Companion for Ubuntu

CalcVault Companion has a desktop shell around the existing local USB backup UI. The shell starts the companion inside the app, binds only to `127.0.0.1` on an OS-assigned port, keeps mutable data under Electron's per-user application-data directory, and opens the existing UI in a sandboxed window. Only same-origin file-download windows are permitted. This is a personal desktop utility, not a public multi-user relay or chat server.

The existing local-browser launch (`npm start`) remains available. The desktop shell does not enable LAN transfer, network pairing, signaling, or partner messaging; those remain governed by the separate Android privacy-route/security review.

## Build an Ubuntu package

Use Node.js 20 or newer and a Debian/Ubuntu x64 build host with `dpkg-deb`:

```sh
cd companion
npm ci
npm audit
npm test
npm run build:ubuntu
```

The `.deb` is written under `companion/release/` (ignored by Git). Install it with Ubuntu's package installer or `sudo apt install ./release/CalcVault-Companion-1.0.0-x64.deb`. ADB is needed for USB device operations; install Ubuntu's Android platform-tools if it is not already available. The app itself stores its persistent state under the current Linux user's application-data directory, not inside the installed package.

Electron's Linux binary is fetched during dependency installation from the upstream release asset host. The build cannot complete in environments that cannot reach that host; do not substitute or package an unverified Electron binary. Verify the package on Ubuntu before distributing it.

## Privacy and validation

The desktop service must remain loopback-only. Do not set `CV_BIND_HOST` to a LAN/public interface. Direct LAN transfer is deliberately rejected by the backend even when legacy configuration asks for it. The existing USB/ADB path is the only supported phone bridge in this build.

The Ubuntu package is not a Tor router, partner-messaging endpoint, or approved encrypted relay. Review `../docs/SECURITY_REVIEW.md` before making security or production-readiness claims.
