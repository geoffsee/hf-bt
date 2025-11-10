#!/usr/bin/env python3
"""
Lightweight CLI for interacting with the hf-bt API so collaborators can explore
the catalog, bootstrap peers, and demo endpoints without crafting HTTP requests manually.

Usage example:
  python3 scripts/hfbt_client.py catalog
  python3 scripts/hfbt_client.py bootstrap --model runwayml/stable-diffusion-v1-5 --rev abc123
  python3 scripts/hfbt_client.py webseed --path config.json
"""

from __future__ import annotations

import argparse
import json
import sys
import textwrap
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode, urljoin
from urllib.request import Request, urlopen


def build_url(base: str, path: str, params: dict[str, str] | None = None) -> str:
    normalized_base = base if base.endswith("/") else base + "/"
    url = urljoin(normalized_base, path.lstrip("/"))
    if params:
        url += "?" + urlencode(params)
    return url


def fetch_json(url: str) -> dict | list:
    req = Request(url, headers={"Accept": "application/json"})
    with urlopen(req, timeout=10) as response:
        return json.load(response)


def fetch_binary(url: str) -> tuple[bytes, dict[str, str]]:
    req = Request(url, headers={"Accept": "*/*"})
    with urlopen(req, timeout=10) as response:
        headers = {k: v for k, v in response.getheaders()}
        return response.read(), headers


def print_json(value: dict | list) -> None:
    print(json.dumps(value, indent=2, sort_keys=True))


def run_command(args: argparse.Namespace) -> int:
    try:
        if args.command == "catalog":
            url = build_url(args.base_url, "/api/v1/catalog", {"query": args.query} if args.query else None)
            print_json(fetch_json(url))
        elif args.command == "bootstrap":
            url = build_url(
                args.base_url,
                "/api/v1/bootstrap",
                {"model": args.model, "rev": args.rev},
            )
            print_json(fetch_json(url))
        elif args.command == "model":
            assert args.model and args.rev
            url = build_url(args.base_url, f"/api/v1/models/{args.model}/revs/{args.rev}")
            print_json(fetch_json(url))
        elif args.command == "health":
            assert args.model and args.rev
            url = build_url(
                args.base_url,
                "/api/v1/health/swarm",
                {"model": args.model, "rev": args.rev},
            )
            print_json(fetch_json(url))
        elif args.command in {"torrent", "webseed"}:
            assert args.model and args.rev
            target = "webseed" if args.command == "webseed" else "torrents"
            path = f"{args.model}/{args.rev}"
            if args.command == "webseed":
                assert args.path
                path += "/" + args.path
            else:
                path += ".torrent"
            url = build_url(args.base_url, f"/{target}/{path}")
            data, headers = fetch_binary(url)
            print(f"Fetched {len(data)} bytes from {url}")
            for key in ("Content-Type", "Content-Length"):
                if key in headers:
                    print(f"{key}: {headers[key]}")
            snippet = data[:64]
            print(f"Sample (hex): {snippet.hex()}...")
        else:
            raise SystemExit("Unknown command")
    except HTTPError as exc:
        print(f"HTTP {exc.code} {exc.reason} when calling {exc.url}", file=sys.stderr)
        return 1
    except URLError as exc:
        print(f"Failed to reach {args.base_url}: {exc}", file=sys.stderr)
        return 2
    return 0


def main() -> None:
    parser = argparse.ArgumentParser(
        description="CLI helper for the hf-bt API (catalog, bootstrap, torrent, etc.)"
    )
    parser.add_argument(
        "--base-url",
        default="http://localhost:8080",
        help="Base URL of the API (default: %(default)s)",
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    subparsers.add_parser("catalog", help="List catalog entries").add_argument(
        "--query", help="Filter catalog items by name", default=None
    )

    bootstrap_parser = subparsers.add_parser("bootstrap", help="Fetch bootstrap peers/trackers")
    bootstrap_parser.add_argument("--model", required=True, help="Model ID (namespace/name)")
    bootstrap_parser.add_argument("--rev", required=True, help="Revision ID")

    model_parser = subparsers.add_parser("model", help="Fetch model manifest")
    model_parser.add_argument("--model", required=True, help="Model ID (namespace/name)")
    model_parser.add_argument("--rev", required=True, help="Revision ID")

    health_parser = subparsers.add_parser("health", help="Fetch swarm health")
    health_parser.add_argument("--model", required=True, help="Model ID (namespace/name)")
    health_parser.add_argument("--rev", required=True, help="Revision ID")

    torrent_parser = subparsers.add_parser("torrent", help="Download torrent file")
    torrent_parser.add_argument("--model", required=True, help="Model ID (namespace/name)")
    torrent_parser.add_argument("--rev", required=True, help="Revision ID")

    webseed_parser = subparsers.add_parser("webseed", help="Download file from webseed")
    webseed_parser.add_argument("--model", required=True, help="Model ID (namespace/name)")
    webseed_parser.add_argument("--rev", required=True, help="Revision ID")
    webseed_parser.add_argument("--path", required=True, help="Path within the model manifest")

    args = parser.parse_args()
    raise SystemExit(run_command(args))


if __name__ == "__main__":
    main()
