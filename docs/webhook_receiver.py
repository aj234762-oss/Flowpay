"""Tiny local receiver for trying FlowPay's signed webhook deliveries."""

from http.server import BaseHTTPRequestHandler, HTTPServer
import getpass
import hashlib
import hmac
import json


secret = getpass.getpass("Paste the merchant webhook signing secret: ").strip()
if not secret.startswith("whsec_"):
    raise SystemExit("The signing secret should start with whsec_.")


class FlowPayReceiver(BaseHTTPRequestHandler):
    def do_GET(self):
        self.send_response(200)
        self.send_header("Content-Type", "text/plain; charset=utf-8")
        self.end_headers()
        self.wfile.write(
            b"Receiver is running. FlowPay sends webhook events with POST."
        )

    def do_POST(self):
        length = int(self.headers.get("Content-Length", "0"))
        body = self.rfile.read(length)

        expected = "sha256=" + hmac.new(
            secret.encode("utf-8"), body, hashlib.sha256
        ).hexdigest()
        supplied = self.headers.get("X-FlowPay-Signature", "")
        valid = hmac.compare_digest(expected, supplied)

        print("\n--- FlowPay webhook ---")
        print("Event:", self.headers.get("X-FlowPay-Event"))
        print("Event ID:", self.headers.get("X-FlowPay-Event-Id"))
        print("Delivery ID:", self.headers.get("X-FlowPay-Delivery-Id"))
        print("Signature valid:", valid)

        try:
            print("Payload:")
            print(json.dumps(json.loads(body), indent=2))
        except json.JSONDecodeError:
            print("Body:", body.decode("utf-8", errors="replace"))

        self.send_response(200 if valid else 401)
        self.end_headers()
        self.wfile.write(b"OK" if valid else b"Invalid webhook signature")

    def log_message(self, format, *args):
        print("Receiver:", format % args)


print("Listening on port 3000")
HTTPServer(("0.0.0.0", 3000), FlowPayReceiver).serve_forever()