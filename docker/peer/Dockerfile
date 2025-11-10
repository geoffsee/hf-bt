# syntax=docker/dockerfile:1.7
FROM python:3.12-slim

# Install openssl for self-signed cert generation
RUN apt-get update \
    && apt-get install -y --no-install-recommends openssl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy peer server script
COPY scripts/peer_sidecar.py /app/peer_sidecar.py

# Install runtime deps
RUN pip install --no-cache-dir aioquic

# Default env; can be overridden by compose
ENV PEER_HOST=0.0.0.0 \
    PEER_PORT=4443 \
    PEER_CERT=/app/dev-cert.pem \
    PEER_KEY=/app/dev-key.pem

# Startup script that ensures certs and launches the peer
COPY scripts/start-peer.sh /usr/local/bin/start-peer
RUN chmod +x /usr/local/bin/start-peer

EXPOSE 4443
ENTRYPOINT ["start-peer"]
