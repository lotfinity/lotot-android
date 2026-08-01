#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PACKAGE="com.lotot.android"
ACTIVITY="com.fr3ts0n.ecu.gui.androbd.MainActivity"
PORT="${LOTOT_UI_PORT:-5173}"
ACTION="${1:-start}"
DEVICE="${2:-${ANDROID_SERIAL:-}}"
HOST="${3:-}"

usage() {
  cat <<EOF
Usage:
  $0 start <adb-serial> [host]
  $0 bundled <adb-serial>
  $0 stop

Examples:
  $0 start 100.68.236.4:5555 100.120.107.43
  $0 bundled 100.68.236.4:5555
EOF
}

require_device() {
  if [[ -z "$DEVICE" ]]; then
    echo "ADB device serial is required." >&2
    usage >&2
    exit 2
  fi
  adb -s "$DEVICE" get-state >/dev/null
}

resolve_host() {
  if [[ -n "$HOST" ]]; then
    return
  fi
  if command -v tailscale >/dev/null 2>&1; then
    HOST="$(tailscale ip -4 2>/dev/null | head -n1 || true)"
  fi
  if [[ -z "$HOST" ]]; then
    echo "Host IP is required when Tailscale is unavailable." >&2
    usage >&2
    exit 2
  fi
}

case "$ACTION" in
  start)
    require_device
    resolve_host
    pkill -u "$(id -un)" -f "vite.*${PORT}" 2>/dev/null || true
    nohup npm --prefix "$ROOT_DIR/lotot-ui" run dev \
      >"/tmp/lotot-vite-${PORT}.log" 2>&1 &
    for _ in $(seq 1 40); do
      curl -fsS "http://127.0.0.1:${PORT}/" >/dev/null 2>&1 && break
      sleep 0.25
    done
    curl -fsS "http://127.0.0.1:${PORT}/" >/dev/null
    URL="http://${HOST}:${PORT}/"
    adb -s "$DEVICE" shell am force-stop "$PACKAGE"
    adb -s "$DEVICE" shell am start \
      -n "${PACKAGE}/${ACTIVITY}" \
      --es lotot_ui_url "$URL" >/dev/null
    echo "LotoT live UI: $URL"
    echo "Vite log: /tmp/lotot-vite-${PORT}.log"
    ;;
  bundled)
    require_device
    adb -s "$DEVICE" shell am force-stop "$PACKAGE"
    adb -s "$DEVICE" shell am start \
      -n "${PACKAGE}/${ACTIVITY}" \
      --es lotot_ui_url bundled >/dev/null
    echo "LotoT restored to bundled UI."
    ;;
  stop)
    pkill -u "$(id -un)" -f "vite.*${PORT}" 2>/dev/null || true
    echo "Vite server stopped."
    ;;
  *)
    usage >&2
    exit 2
    ;;
esac
