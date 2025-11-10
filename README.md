# hf-bt

HuggingSwarm BitTorrent/WebTransport API — demo/stub service built with Kotlin and Spring Boot.

This project exposes a small HTTP API that simulates a "master" service for a swarm-based model distribution system. It provides:

- A stub catalog of models
- Model manifest lookup
- Bootstrap info (peers, trackers, relays) for a given model revision
- Swarm health metrics (fake data)
- Torrent file endpoint (fake torrent)
- Optional webseed proxy to Hugging Face (or stubbed local bytes when disabled)
- Optional demo peer sidecar (WebTransport over QUIC) for local experimentation

Status: Developer demo, not production-ready.


## Contents
- Overview
- Requirements
- Quickstart (local)
- Configuration
- API Reference (with examples)
- Docker & Compose
- Helper Scripts
- Development & Testing
- Troubleshooting


## Overview
- Language/Runtime: Kotlin, Java 21
- Framework: Spring Boot 3.5
- Build: Gradle (wrapper included)
- Default port: 8080
- Sample model used throughout: `runwayml/stable-diffusion-v1-5`, revision `abc123`

Main components:
- Controllers:
  - `CatalogController` — `/api/v1/catalog`
  - `ModelController` — `/api/v1/models/...`, `/api/v1/bootstrap`, `/api/v1/health/swarm`
  - `TorrentController` — `/torrents/...`, `/webseed/...`
- Services:
  - `StubCatalogService` — serves stubbed catalog/manifest/torrent/metrics; optionally adds a local demo peer to bootstrap output
  - `WebseedProxyService` — proxies ranged file requests to upstream (Hugging Face) or returns stub bytes when disabled
- Optional peer sidecar:
  - `scripts/peer_sidecar.py` (aioquic WebTransport demo) and `docker/peer/Dockerfile`


## Requirements
- Java 21 (Temurin/Eclipse Adoptium recommended)
- Gradle wrapper is included; no system Gradle required
- Optional for demo peer:
  - Python 3.9+
  - `pip install aioquic`
  - Dev TLS certificate/key (self-signed is fine)
- Optional: Docker and Docker Compose v2


## Quickstart (local)
1) Clone and run the API:

```bash
./gradlew bootRun
```

The server listens on `http://localhost:8080` (configurable via Spring properties). Try a quick check:

```bash
curl -s http://localhost:8080/api/v1/catalog | jq .
```

2) (Optional) Use the included Python CLI helper instead of crafting curl commands:

```bash
python3 scripts/hfbt_client.py catalog
python3 scripts/hfbt_client.py bootstrap --model runwayml/stable-diffusion-v1-5 --rev abc123
```


## Configuration
Spring Boot properties can be set via `application.properties`, system properties, or environment variables. The default config lives at `src/main/resources/application.properties`.

Key properties:

- Demo peer (developer-only feature flags):
  - `demo.peer.enabled` (default: `false`) — when true, the app will attempt to include a local peer URL in bootstrap output.
  - `demo.peer.impl` (default: `jetty`) — select demo peer integration:
    - `jetty` — no actual peer is started; if `demo.peer.enabled=true`, a static URL is advertised from `demo.peer.url`.
    - `aioquic` — tries to launch the Python sidecar (`scripts/peer_sidecar.py`).
  - `demo.peer.url` — URL to advertise as the local peer (used by `jetty`/noop mode or as fallback).
  - `demo.peer.host` (default: `127.0.0.1`) and `demo.peer.port` (default: ephemeral when unset) — host/port for the sidecar.
  - `demo.peer.script-path` (default: `scripts/peer_sidecar.py`) — path to Python sidecar script.
  - `demo.peer.python-command` (default: `python3`) — interpreter to use.
  - `demo.peer.cert-path`, `demo.peer.key-path` — TLS cert/key for the sidecar (required unless your environment provides defaults).
  - `demo.peer.start-retries` (default: `3`) and `demo.peer.start-backoff-ms` (default: `1000`).

- Webseed proxy:
  - `webseed.base-url` (default: `https://huggingface.co`) — when non-empty, `/webseed/...` requests will be proxied to this upstream.
    - Set to empty to disable proxying and return local stub bytes.
  - `webseed.timeout-ms` (default: `10000`)
  - `webseed.user-agent` (default: `hf-bt-webseed-proxy/1.0`)

Environment variable mapping (Spring Boot): replace dots with underscores and use uppercase. Examples:

- `demo.peer.enabled` → `DEMO_PEER_ENABLED=true`
- `demo.peer.impl` → `DEMO_PEER_IMPL=aioquic`
- `webseed.base-url` → `WEBSEED_BASE_URL=""` (empty string disables proxy)


## API Reference
All endpoints return JSON unless noted otherwise. The stub dataset uses:
- Model ID: `runwayml/stable-diffusion-v1-5`
- Revision: `abc123`

Base URL (default): `http://localhost:8080`

- GET `/api/v1/catalog?query={optional}`
  - Lists catalog items (stubbed minimal set). Filter with `query`.
  - Example:
    ```bash
    curl -s "http://localhost:8080/api/v1/catalog" | jq .
    ```
  - Sample response:
    ```json
    {
      "items": [
        {
          "id": "runwayml/stable-diffusion-v1-5",
          "latestRev": "abc123",
          "sizeBytes": 1088,
          "torrentUrl": "/torrents/runwayml/stable-diffusion-v1-5/abc123.torrent",
          "magnetUri": "magnet:?xt=urn:btih:example"
        }
      ]
    }
    ```

- GET `/api/v1/models/{namespace}/{name}/revs/{rev}`
  - Returns a model manifest (files metadata and optional magnet/torrent).
  - Example:
    ```bash
    curl -s "http://localhost:8080/api/v1/models/runwayml/stable-diffusion-v1-5/revs/abc123" | jq .
    ```

- GET `/api/v1/bootstrap?model={id}&rev={rev}`
  - Returns peer endpoints, trackers, relays, and parameters. If demo peer is enabled, it appends a local peer URL.
  - Example:
    ```bash
    curl -s "http://localhost:8080/api/v1/bootstrap?model=runwayml/stable-diffusion-v1-5&rev=abc123" | jq .
    ```

- GET `/api/v1/health/swarm?model={id}&rev={rev}`
  - Returns stubbed swarm health metrics.

- GET `/torrents/{namespace}/{name}/{rev}.torrent`
  - Returns a `.torrent` file (content is a short stub; do not use in production).
  - Example:
    ```bash
    curl -I "http://localhost:8080/torrents/runwayml/stable-diffusion-v1-5/abc123.torrent"
    ```

- GET `/webseed/{namespace}/{name}/{rev}/{path}`
  - Returns a file via webseed. If `webseed.base-url` is set, it proxies to upstream; otherwise returns local stub bytes.
  - Supports the `Range` header for partial content.
  - Examples:
    ```bash
    # Fetch a JSON file
    curl -s "http://localhost:8080/webseed/runwayml/stable-diffusion-v1-5/abc123/config.json"

    # Fetch the first 16 bytes of a file
    curl -s -H "Range: bytes=0-15" \
      "http://localhost:8080/webseed/runwayml/stable-diffusion-v1-5/abc123/model.safetensors" | hexdump -C
    ```


## Docker & Compose
Build a local image and run:

```bash
docker build -t hf-bt:local .
docker run --rm -p 8080:8080 \
  -e DEMO_PEER_ENABLED=false \
  hf-bt:local
```

Compose file `docker-compose.yml` provides two services:
- `api` — this application
- `peer` — an aioquic WebTransport peer sidecar that generates self-signed certs at startup

Run API only:
```bash
docker compose up --build api
```

Run API + demo peer sidecar (ensure the API advertises the peer):
```bash
DEMO_PEER_ENABLED=true \
DEMO_PEER_IMPL=aioquic \
DEMO_PEER_URL=https://localhost:4443/.well-known/webtransport \
docker compose --profile peer up --build
```

Notes:
- The `peer` service listens on `4443` and creates self-signed TLS certs. Browsers/clients will need to trust/ignore cert warnings for demos.
- You can change advertised URLs by overriding `DEMO_PEER_URL` or setting `demo.peer.host/port` when launching a local sidecar.


## Helper Scripts
- `scripts/hfbt_client.py` — tiny CLI to interact with the API:
  ```bash
  python3 scripts/hfbt_client.py catalog
  python3 scripts/hfbt_client.py model --model runwayml/stable-diffusion-v1-5 --rev abc123
  python3 scripts/hfbt_client.py bootstrap --model runwayml/stable-diffusion-v1-5 --rev abc123
  python3 scripts/hfbt_client.py health --model runwayml/stable-diffusion-v1-5 --rev abc123
  python3 scripts/hfbt_client.py torrent --model runwayml/stable-diffusion-v1-5 --rev abc123
  python3 scripts/hfbt_client.py webseed --model runwayml/stable-diffusion-v1-5 --rev abc123 --path config.json
  ```

- `scripts/run-peer.sh` — launches the Python aioquic sidecar locally (requires `aioquic` and dev certs under `~/.config/hswarm/` by default):
  ```bash
  # Install dependency once
  pip install aioquic

  # Provide or generate dev certs (see output if missing)
  scripts/run-peer.sh
  ```

- `docker/peer/Dockerfile` + `scripts/start-peer.sh` — containerized version of the sidecar; generates self-signed certs at runtime.


## Development & Testing
- Build and run tests:
  ```bash
  ./gradlew clean test
  ```
- Run the app (skipping tests) and build bootable JAR:
  ```bash
  ./gradlew clean bootJar
  java -jar build/libs/*.jar
  ```

Tech stack highlights:
- Spring Boot 3.5, Kotlin 1.9, JUnit 5
- Controllers: `CatalogController`, `ModelController`, `TorrentController`
- Services: `StubCatalogService`, `WebseedProxyService`
- Configuration: `DemoPeerProperties`, `WebseedProperties`


## Troubleshooting
- 404 responses for model/revision:
  - Ensure you use the stubbed values: model `runwayml/stable-diffusion-v1-5` and rev `abc123`.
- Webseed proxy errors/timeouts:
  - Set `WEBSEED_BASE_URL=""` to disable proxying and return local stub bytes while developing offline.
- Demo peer sidecar fails to start:
  - For local Python: ensure `python3` and `aioquic` are installed, and provide TLS cert/key via `PEER_CERT`/`PEER_KEY` or in `~/.config/hswarm/`.
  - For Docker peer: the container auto-generates self-signed certs.
- Port already in use:
  - Change server port with `SERVER_PORT=8081` (or set `server.port=8081` in properties).


## License
No license file is present in this repository. If you intend to distribute this project, please add an appropriate `LICENSE` file.
