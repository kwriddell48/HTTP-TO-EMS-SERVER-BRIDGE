#!/usr/bin/env python3
"""
HTTP to EMS Bridge - CLI Tool

Send messages to TIBCO EMS via the HTTP to EMS Bridge.
Requires the Java bridge to be running (default: http://localhost:8080).
"""

import argparse
import os
import sys
from typing import Optional

from http_to_ems_client import BridgeError, HttpToEmsClient


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Send messages to TIBCO EMS via the HTTP to EMS Bridge",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  # Publish-only
  python ems_send.py --url tcp://localhost:7222 --user admin --queue queue.test --body "Hello" --publish-only

  # Request-reply
  python ems_send.py --url tcp://localhost:7222 --user admin --queue queue.req --reply-queue queue.reply --body "ping"

  # Read body from file
  python ems_send.py --url tcp://localhost:7222 --user admin --queue queue.test --file message.json --publish-only

  # Read body from stdin
  echo "Hello" | python ems_send.py --url tcp://localhost:7222 --user admin --queue queue.test --publish-only
        """,
    )

    parser.add_argument(
        "--bridge",
        "-b",
        default=os.environ.get("HTTP_EMS_BRIDGE_URL", "http://localhost:8080"),
        help="Bridge URL (default: %(default)s or HTTP_EMS_BRIDGE_URL env)",
    )
    parser.add_argument(
        "--url",
        "-u",
        required=True,
        help="EMS server URL (e.g., tcp://localhost:7222)",
    )
    parser.add_argument(
        "--user",
        default=os.environ.get("JMS_USR"),
        help="EMS username (or JMS_USR env)",
    )
    parser.add_argument(
        "--password",
        "-p",
        default=os.environ.get("JMS_PSW", ""),
        help="EMS password (or JMS_PSW env)",
    )
    parser.add_argument(
        "--queue",
        "-q",
        required=True,
        help="Destination queue (JMS-QU1)",
    )
    parser.add_argument(
        "--body",
        "-d",
        default="",
        help="Message body",
    )
    parser.add_argument(
        "--file",
        "-f",
        help="Read message body from file",
    )
    parser.add_argument(
        "--publish-only",
        action="store_true",
        help="Publish without waiting for reply",
    )
    parser.add_argument(
        "--reply-queue",
        help="Reply queue for request-reply (JMS-QU2)",
    )
    parser.add_argument(
        "--timeout",
        "-t",
        type=int,
        default=30000,
        help="Reply timeout in milliseconds (default: 30000)",
    )
    parser.add_argument(
        "--correlation-id",
        help="JMS correlation ID",
    )
    parser.add_argument(
        "--plain",
        action="store_true",
        help="Use text/plain instead of application/json",
    )
    parser.add_argument(
        "--timeout-sec",
        type=float,
        default=60.0,
        help="HTTP request timeout in seconds (default: 60)",
    )

    args = parser.parse_args()

    user = args.user or os.environ.get("JMS_USR")
    if not user:
        parser.error("--user is required (or set JMS_USR environment variable)")

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
        client = HttpToEmsClient(base_url=args.bridge, timeout=args.timeout_sec)
        result = client.send(
            jms_url=args.url,
            user=user,
            queue=args.queue,
            body=body,
            password=password,
            reply_queue=args.reply_queue,
            publish_only=args.publish_only,
            timeout_ms=args.timeout,
            correlation_id=args.correlation_id,
            use_json=not args.plain,
        )
        client.close()

        print(result.body)
        return 0

    except BridgeError as e:
        print(f"Error: {e.message}", file=sys.stderr)
        return 1
    except Exception as e:
        print(f"Error: {e}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
