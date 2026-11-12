# CalcVault application audit status

**Audit date:** 2026-11-12  
**Result:** INCOMPLETE — do not treat this checkout as release-ready.

## Scope and constraints

This pass reviewed the Android Gradle setup, Android manifest/component declarations, privacy transport gates, the companion service and its deployment/build shape, and the repository's JavaScript checks. It did not replace device-based validation or an Android compiler run. User-facing vault/companion features were not intentionally removed; insecure/unverified network signaling paths were explicitly made fail-closed and disclose that the secure route is unavailable.

The privacy/security constraints remain unchanged: Tor/onion first where implemented; an encrypted relay may receive ciphertext only and no keys/plaintext; direct P2P only after explicit IP-disclosure consent; never silently downgrade; and fail closed when a secure route is unavailable. The current Android build still has no complete Tor or authenticated ciphertext-only relay/signaling adapter. Calls and other network controls therefore remain unavailable rather than being sent over an unverified route. See [SECURITY_REVIEW.md](SECURITY_REVIEW.md) and [PRIVACY_NETWORK_ARCHITECTURE.md](PRIVACY_NETWORK_ARCHITECTURE.md).

## Checks completed

- `git diff --check` — passed.
- `bash FINAL_VERIFICATION.sh` — 28 structural checks passed. These are source/layout/manifest-presence checks only; the script itself recommends a Gradle compile afterward and is not a build test.
- Manifest component names were cross-checked against declared Kotlin classes — no missing component class was found.
- Android resource XML was parsed — no malformed XML was found. A basic `R.layout` scan produced only Android framework layout constants (`simple_list_item_1`, `simple_spinner_item`, and `simple_spinner_dropdown_item`) as apparent misses.
- Root JavaScript project: `npm audit` — 0 vulnerabilities; `npm run lint` — passed; `npm run build` — passed (Vite production assets and Node server bundle).
- Companion: `npm audit` — 0 vulnerabilities; `npm test` — passed (4 tests; includes legacy unauthenticated signaling REST/WebSocket rejection, port-zero loopback startup, direct-LAN fail-closed behavior, and desktop navigation policy); `npm run validate` — passed and verified that direct LAN transfer is rejected without fallback. Companion signaling REST endpoints now return 410, and non-UI WebSocket upgrades are rejected without recording IPs or payloads.
- Added an Ubuntu Electron shell around the existing companion. Its window is sandboxed, Node integration is disabled, it uses a random loopback-only port and per-user writable data directory, and only same-origin attachment downloads may open a window. The shell and package config pass Node syntax checks; the Electron GUI itself has not run here.
- Android source audit found mixed pair identifiers (`zain`/`sanu` vs `USER_A`/`USER_B`), app-unwritable absolute storage paths, and local file copies that were labeled encrypted. Pair identity is now normalized to stable two-user IDs; storage paths are app-rooted; file records are chunked through the existing encrypted `StorageManager` provider with metadata committed last and access-checked exports; USB provider tombstones now hide deleted keys and replay on index rebuild.
- Parsed all 233 Android Kotlin source/test files with the Kotlin tree-sitter grammar — no syntax errors. Three existing semicolon-separated private setters were reformatted after the parser flagged them. This is a syntax parse, **not** Kotlin type checking or Gradle compilation.
- Call transport audit and additional changes are documented in [CALL_TRANSPORT_AUDIT.md](CALL_TRANSPORT_AUDIT.md): canonical call identity and recipient checks, duplicate-answer/lifecycle guards, foreground mic/camera service confirmation before media, notification resume/end actions, a reversible video-call camera control, malformed ICE URI rejection, bounded recovery, remote candidate preservation when an answer arrives, and direct-call candidate gathering blocked because the current WebRTC stack cannot preserve TURN-first/direct-last order. A fresh syntax-only parse after the latest edits covered all 233 Kotlin source/test files with no syntax errors; `git diff --check` and `bash FINAL_VERIFICATION.sh` (28/28 structural checks) also passed. Android build, unit tests, and device tests remain blocked.
- The root lockfile resolves Vite 6.4.6 and patched transitive dependencies; a `qs` override was added to keep the dependency tree on a fixed `qs` version. The companion already has its own `qs` override in the current worktree.
- Removed the shared Gradle configuration's hard-coded `/usr/lib/jvm/java-17-openjdk-amd64` path so Android Studio and other Ubuntu JDK 17 installations can use their configured `JAVA_HOME`/Gradle JDK. This configuration change could not be verified by running Gradle here.
- Replaced the Android build helper's source-mutating `sed` operations with a non-destructive JDK/SDK preflight and standard unit-test/debug-APK build; the legacy helper now delegates to it without changing permissions, deleting caches, or rewriting SDK configuration.

The root Vite project is a separate React/Node project and its successful build is **not** evidence of an Android APK or Ubuntu desktop package build.

## Blocked / not completed

- **Android APK and unit tests:** not built or run. This environment has no `java`, `javac`, `kotlinc`, `gradle`, or `adb`, no discoverable JVM, and no Android SDK/platform. The safe `./build_calcvault.sh` preflight was run and stopped with `ERROR: Java 17 is required`; no Gradle build was attempted. The Gradle wrapper distribution fetch had previously failed with SSL/EOF. Without JDK 17 plus Android SDK 34/build-tools, a Kotlin compile, APK build, instrumentation test, install, or crash-on-device check cannot be reported.
- **Ubuntu desktop package:** the Electron shell and `npm run build:ubuntu` Debian target are now defined, but no `.deb` was produced. The build was attempted and stopped while fetching the Electron binary with a TLS certificate verification error; the upstream release redirects to an asset host this sandbox cannot reach. The companion runtime and shell policy tests pass, but the GUI and package need a build on an Ubuntu x64 host with access to the verified upstream Electron binary.
- **Runtime/security validation:** no Android device/two-device testing, independent security review, or end-to-end verification of Tor, relay, P2P consent, calls, media sharing, or reconnection was performed. Those secure network transports are not integrated, so the current security review remains NO-GO.

## Repeatable checks

From the repository root:

```sh
npm ci --ignore-scripts
npm audit
npm run lint
npm run build
```

From `companion/`:

```sh
npm ci --ignore-scripts
npm audit
npm test
npm run validate
```

When a JDK 17 and Android SDK 34 environment is available, run at minimum:

```sh
./gradlew clean testDebugUnitTest assembleDebug
```

Then install and exercise the APK on supported Android devices, test each UI entry point and permission lifecycle, and perform the route/security checks before making any readiness claim. On an Ubuntu x64 host with verified Electron download access, run `cd companion && npm ci && npm run build:ubuntu`; test/install the resulting `.deb` and confirm it preserves the companion's local-only transport/security behavior.
