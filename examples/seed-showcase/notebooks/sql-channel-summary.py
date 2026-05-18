"""LUMEN hot-fire campaign — channel summary via the SQL-over-HTTP endpoint.

Demonstrates the P10a–P10c `/v2/sql/timeseries` curated SQL surface added
on this fork. Returns one row per channel per run with summary metrics
(min / max / mean / stddev / point count) for the configured time window.

Usage:
    SHEPARD_API=https://shepard-api.example.dlr.de \\
    SHEPARD_API_KEY=your-key \\
        python sql-channel-summary.py [--container lumen-inspired-sensors]

The endpoint returns CSV by default; this script asks for application/json
so the result is easy to consume in pandas / matplotlib. The server caps
both row count and query duration (see /v2/admin/sql-timeseries/config).

This script is intentionally pure-stdlib so it runs in any python:3.12-slim
container without extra packages. Add `import pandas as pd; df = pd.read_csv(...)`
in your own notebook for ergonomic post-processing.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.request


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument(
        "--api",
        default=os.environ.get("SHEPARD_API"),
        help="shepard API root, e.g. https://shepard-api.example.dlr.de",
    )
    ap.add_argument(
        "--api-key",
        default=os.environ.get("SHEPARD_API_KEY"),
        help="apikey header value",
    )
    ap.add_argument(
        "--container",
        default="lumen-inspired-sensors",
        help="timeseries container name to query",
    )
    args = ap.parse_args(argv)

    if not args.api or not args.api_key:
        ap.error("set --api / --api-key or SHEPARD_API / SHEPARD_API_KEY env vars")

    api = args.api.rstrip("/")
    if api.endswith("/shepard/api"):
        api = api[: -len("/shepard/api")]

    # Curated SQL — the server enforces a strict allowlist on the FROM clause
    # and the WHERE selectors. We aggregate per channel for the entire range.
    sql = f"""
        SELECT
          measurement,
          device,
          location,
          symbolic_name,
          field,
          COUNT(*)              AS n,
          MIN(value::double precision)    AS v_min,
          MAX(value::double precision)    AS v_max,
          AVG(value::double precision)    AS v_mean,
          STDDEV(value::double precision) AS v_stddev
        FROM "{args.container}".sensors
        GROUP BY measurement, device, location, symbolic_name, field
        ORDER BY measurement, field
        LIMIT 200
    """.strip()

    body = json.dumps({"sql": sql}).encode("utf-8")
    req = urllib.request.Request(
        f"{api}/v2/sql/timeseries",
        data=body,
        headers={
            "apikey": args.api_key,
            "Content-Type": "application/json",
            "Accept": "application/json",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            payload = json.loads(resp.read().decode("utf-8"))
            truncated = resp.headers.get("x-shepard-truncated") == "true"
    except urllib.error.HTTPError as e:
        print(f"HTTP {e.code}: {e.read().decode('utf-8', errors='replace')[:400]}", file=sys.stderr)
        return 1
    except urllib.error.URLError as e:
        print(f"Network error: {e}", file=sys.stderr)
        return 1

    rows = payload if isinstance(payload, list) else payload.get("rows", [])
    if not rows:
        print("(no rows)")
        return 0

    # Pretty-print as a fixed-width table.
    cols = list(rows[0].keys())
    widths = {c: max(len(c), max(len(str(r.get(c, ""))) for r in rows)) for c in cols}
    print("  ".join(c.ljust(widths[c]) for c in cols))
    print("  ".join("-" * widths[c] for c in cols))
    for r in rows:
        print("  ".join(str(r.get(c, "")).ljust(widths[c]) for c in cols))

    if truncated:
        print("\n[note] server truncated the result — raise the row cap "
              "via PATCH /v2/admin/sql-timeseries/config if you need more.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
