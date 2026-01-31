#!/usr/bin/env python3
"""
Publish-only: Send a message to EMS and get the JMS Message ID.

Convenience wrapper around ems_send.py --publish-only.
"""

import argparse
import os
import sys

from http_to_ems_client import BridgeError, HttpToEmsClient


def main() -> int:
    parser = argparse.ArgumentParser(description="Publish message to EMS (fire-and-forget)")
    parser.add_argument("--bridge", "-b", default=os.environ.get("HTTP_EMS_BRIDGE_URL", "http://localhost:8080"))
    parser.add_argument("--url", "-u", required=True, help="EMS URL (e.g., tcp://localhost:7222)")
    parser.add_argument("--user", default=os.environ.get("JMS_USR"), help="EMS username")
    parser.add_argument("--password", "-p", default=os.environ.get("JMS_PSW", ""))
    parser.add_argument("--queue", "-q", required=True, help="Destination queue")
    parser.add_argument("--body", "-d", default="", help="Message body")
    parser.add_argument("--file", "-f", help="Read body from file")

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
            result = client.publish(
                jms_url=args.url,
                user=user,
                queue=args.queue,
                body=body,
                password=password,
            )
        print(result.message_id or result.body)
        return 0
    except BridgeError as e:
        print(f"Error: {e.message}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
