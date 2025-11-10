#!/usr/bin/env python3
"""
Minimal WebTransport demo sidecar using aioquic.

Notes:
- This is a developer-only demo. It is not started automatically by the app yet.
- Requires Python 3.9+ and `aioquic` (pip install aioquic).
- Uses self-signed TLS by default (verification should be disabled on the client side for demos).

Environment variables:
- PEER_HOST (default: 127.0.0.1)
- PEER_PORT (default: 4443)
- PEER_CERT (path to PEM cert; default: generated in-memory self-signed if omitted)
- PEER_KEY  (path to PEM key; default: generated in-memory if omitted)

Endpoint:
- WebTransport sessions accepted at /.well-known/webtransport
- Control stream expects a JSON line with {"type":"hello", ...} and responds {"type":"ok"}
- A subsequent bidirectional stream with {"type":"request_chunk", ...} causes the server
  to send a fixed 64 KiB payload for model id runwayml/stable-diffusion-v1-5 @ abc123.

This script is intentionally simple and not production-ready.
"""
import asyncio
import json
import os
import ssl
from dataclasses import dataclass

from aioquic.asyncio import serve
from aioquic.h3.connection import H3_ALPN
from aioquic.quic.configuration import QuicConfiguration
from aioquic.webtransport.server import WebTransportH3Server

MODEL_ID = "runwayml/stable-diffusion-v1-5"
REV = "abc123"
STUB_CHUNK = bytes([0xAB]) * 65536  # 64 KiB

@dataclass
class Cfg:
    host: str = os.getenv("PEER_HOST", "127.0.0.1")
    port: int = int(os.getenv("PEER_PORT", "4443"))
    cert: str | None = os.getenv("PEER_CERT")
    key: str | None = os.getenv("PEER_KEY")

class Peer(WebTransportH3Server):
    async def stream_created(self, session, stream):
        reader = stream.get_reader()
        writer = stream.get_writer()
        data = await reader.read(65536)
        try:
            msg = json.loads(data.decode("utf-8"))
        except Exception:
            await writer.write(b'{"type":"error","code":"bad_json"}')
            await writer.write_eof()
            return

        if msg.get("type") == "hello":
            await writer.write(b'{"type":"ok"}')
            await writer.write_eof()
            return

        if msg.get("type") == "request_chunk":
            if msg.get("model_id") == MODEL_ID and msg.get("rev") == REV:
                await writer.write(STUB_CHUNK)
            else:
                await writer.write(b"\x00" * min(int(msg.get("length", 4096)), 4096))
            await writer.write_eof()
            return

        await writer.write(b'{"type":"error","code":"bad_type"}')
        await writer.write_eof()

async def main():
    cfg = Cfg()
    quic_cfg = QuicConfiguration(is_client=False, alpn_protocols=H3_ALPN)
    # Dev-mode TLS: allow self-signed
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    if cfg.cert and cfg.key:
        context.load_cert_chain(cfg.cert, cfg.key)
    else:
        # Create a temporary self-signed certificate (in-memory) is non-trivial; instead,
        # instruct the developer to provide cert/key or run with a local dev cert.
        # For convenience, try to read from default paths if present.
        default_cert = os.path.expanduser("~/.config/hswarm/dev-cert.pem")
        default_key = os.path.expanduser("~/.config/hswarm/dev-key.pem")
        if os.path.exists(default_cert) and os.path.exists(default_key):
            context.load_cert_chain(default_cert, default_key)
        else:
            raise SystemExit(
                "TLS cert/key required. Set PEER_CERT/PEER_KEY env or place dev-cert.pem/dev-key.pem under ~/.config/hswarm/"
            )
    print(f"[peer_sidecar] Listening on https://{cfg.host}:{cfg.port}/.well-known/webtransport")
    await serve(cfg.host, cfg.port, configuration=quic_cfg, create_protocol=Peer, ssl=context)

if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        pass
