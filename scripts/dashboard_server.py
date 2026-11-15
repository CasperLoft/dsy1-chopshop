#!/usr/bin/env python3
"""Serve the local dashboard and report RabbitMQ TCP reachability."""
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import json
import socket

DASHBOARD = Path(__file__).resolve().parent.parent / "dashboard"


class Handler(SimpleHTTPRequestHandler):
    def do_GET(self):
        if self.path == "/api/status/rabbitmq":
            try:
                with socket.create_connection(("127.0.0.1", 5672), timeout=1):
                    result = {"reachable": True}
            except OSError:
                result = {"reachable": False}
            data = json.dumps(result).encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Cache-Control", "no-store")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)
        else:
            super().do_GET()


if __name__ == "__main__":
    server = ThreadingHTTPServer(("127.0.0.1", 8000), partial(Handler, directory=str(DASHBOARD)))
    print("Dashboard available at http://localhost:8000", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
