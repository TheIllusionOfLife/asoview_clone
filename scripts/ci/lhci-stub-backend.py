#!/usr/bin/env python3
"""Minimal stub backend for the Lighthouse CI job.

The Lighthouse job builds the web app and runs it against
`http://localhost:3999` as its `NEXT_PUBLIC_API_BASE_URL`. Real SSR
pages call `/v1/areas` and `/v1/products` during render; without any
upstream they `ECONNREFUSED` and the landing page 500s. A seeded real
gateway would need testcontainers; until that harness lands, this
trivial stub returns empty-but-valid shapes so the page renders its
fallback copy ("エリア情報を読み込めませんでした") and Lighthouse can
measure the actual rendered HTML.

Only GET is supported; any other method returns 405. Stdout is silenced
so CI logs stay clean.
"""

from __future__ import annotations

import http.server
import sys


class _Handler(http.server.BaseHTTPRequestHandler):
    def do_GET(self) -> None:  # noqa: N802
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        if "/v1/areas" in self.path:
            self.wfile.write(b"[]")
        elif "/v1/products" in self.path:
            self.wfile.write(
                b'{"content":[],"totalElements":0,"number":0,"size":0}'
            )
        else:
            self.wfile.write(b"{}")

    def do_HEAD(self) -> None:  # noqa: N802
        self.send_response(200)
        self.end_headers()

    def log_message(self, *args: object, **kwargs: object) -> None:
        return None


def main() -> int:
    server = http.server.HTTPServer(("127.0.0.1", 3999), _Handler)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        return 0
    return 0


if __name__ == "__main__":
    sys.exit(main())
