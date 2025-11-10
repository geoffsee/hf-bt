#!/usr/bin/env bash
set -euo pipefail

# Minimal launcher for the demo WebTransport peer sidecar (aioquic).
# Requirements:
#  - python3
#  - pip install aioquic
#  - TLS cert/key (see below)

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$DIR/.." && pwd)"

HOST="${PEER_HOST:-127.0.0.1}"
PORT="${PEER_PORT:-4443}"
CERT="${PEER_CERT:-$HOME/.config/hswarm/dev-cert.pem}"
KEY="${PEER_KEY:-$HOME/.config/hswarm/dev-key.pem}"

if ! command -v python3 >/dev/null 2>&1; then
  echo "python3 is required" >&2
  exit 1
fi

if ! python3 -c "import aioquic" >/dev/null 2>&1; then
  echo "Python package 'aioquic' is required. Install with: pip install aioquic" >&2
  exit 1
fi

if [[ ! -f "$CERT" || ! -f "$KEY" ]]; then
  echo "TLS cert/key not found. Provide PEER_CERT/PEER_KEY or place dev-cert.pem/dev-key.pem under ~/.config/hswarm/" >&2
  exit 1
fi

export PEER_HOST="$HOST"
export PEER_PORT="$PORT"
export PEER_CERT="$CERT"
export PEER_KEY="$KEY"

exec python3 "$ROOT/scripts/peer_sidecar.py"