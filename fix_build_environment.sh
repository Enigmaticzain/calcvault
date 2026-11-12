#!/usr/bin/env bash
# Backwards-compatible entry point for the safe Android build preflight/build script.
# This intentionally never changes ownership, deletes caches, or writes local.properties.
set -euo pipefail
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec "$PROJECT_ROOT/build_calcvault.sh" "$@"
