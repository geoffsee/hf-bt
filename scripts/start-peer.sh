#!/usr/bin/env bash
set -euo pipefail

: "${PEER_HOST:=0.0.0.0}"
: "${PEER_PORT:=4443}"
: "${PEER_CERT:=/app/dev-cert.pem}"
: "${PEER_KEY:=/app/dev-key.pem}"

# Generate a self-signed cert if missing (dev/demo only)
if [[ ! -f "$PEER_CERT" || ! -f "$PEER_KEY" ]]; then
  echo "[peer] generating self-signed TLS certs at $PEER_CERT / $PEER_KEY"
  openssl req -x509 -newkey rsa:2048 -nodes -keyout "$PEER_KEY" -out "$PEER_CERT" -subj "/CN=localhost" -days 3650 >/dev/null 2>&1
fi

export PEER_HOST
export PEER_PORT
export PEER_CERT
export PEER_KEY

echo "[peer] starting aioquic WebTransport server on ${PEER_HOST}:${PEER_PORT}"
exec python3 -u /app/peer_sidecar.py
