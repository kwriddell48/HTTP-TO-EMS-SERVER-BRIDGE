#!/usr/bin/env python3
"""
Request-reply: Send a message to EMS and wait for a reply.

Convenience wrapper around ems_send.py for request-reply mode.
"""

import argparse
import os
import sys

from http_to_ems_client import BridgeError, HttpToEmsClient


def main() -> int:
    parser = argparse.ArgumentParser(description="Send request to EMS and wait for reply")
    parser.add_argument("--bridge", "-b", default=os.environ.get("HTTP_EMS_BRIDGE_URL", "http://localhost:8080"))
    parser.add_argument("--url", "-u", required=True, help="EMS URL (e.g., tcp://localhost:7222)")
    parser.add_argument("--user", default=os.environ.get("JMS_USR"), help="EMS username")
    parser.add_argument("--password", "-p", default=os.environ.get("JMS_PSW", ""))
    parser.add_argument("--queue", "-q", required=True, help="Request queue")
    parser.add_argument("--reply-queue", help="Reply queue (optional; uses temp queue if omitted)")
    parser.add_argument("--body", "-d", default="", help="Message body")
    parser.add_argument("--file", "-f", help="Read body from file")
    parser.add_argument("--timeout", "-t", type=int, default=30000, help="Timeout in ms")

    args = parser.parse_args()

    user = args.user or os.environ.get("JMS_USR")
    if not user:
        parser.error("--user is required (or set JMS_USR)")

    body = args.body
    if args.file:
        with open(args.file, "r", encoding="utf-8") as f:
            body = f.read()
    elif not body and not sys.stdin.isatty():
        body = sys.stdin.read()

    password = args.password or None
    if password == "":
        password = None

    try:
        with HttpToEmsClient(base_url=args.bridge) as client:
            result = client.request_reply(
                jms_url=args.url,
                user=user,
                queue=args.queue,
                body=body,
                password=password,
                reply_queue=args.reply_queue,
                timeout_ms=args.timeout,
            )
        print(result.body)
        return 0
    except BridgeError as e:
        print(f"Error: {e.message}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
