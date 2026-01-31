#!/usr/bin/env python3
"""
Fetch metrics from the HTTP to EMS Bridge.

Retrieves statistics from /metrics or /stats endpoint.
"""

import argparse
import json
import os
import sys

from http_to_ems_client import HttpToEmsClient


def main() -> int:
    parser = argparse.ArgumentParser(description="Get metrics from HTTP to EMS Bridge")
    parser.add_argument(
        "--bridge",
        "-b",
        default=os.environ.get("HTTP_EMS_BRIDGE_URL", "http://localhost:8080"),
    )
    parser.add_argument(
        "--plain",
        action="store_true",
        help="Plain text output instead of JSON",
    )

    args = parser.parse_args()

    try:
        with HttpToEmsClient(base_url=args.bridge) as client:
            stats = client.get_stats(use_json=not args.plain)

        if args.plain:
            if isinstance(stats, dict) and "raw" in stats:
                print(stats["raw"])
            else:
                print(json.dumps(stats, indent=2))
        else:
            print(json.dumps(stats, indent=2))
        return 0
    except Exception as e:
        print(f"Error: {e}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
