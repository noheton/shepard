"""LUMEN hot-fire campaign — channel summary via the SQL-over-HTTP endpoint.

Demonstrates the P10a–P10c `/v2/sql/timeseries` curated SQL surface added
on this fork. Returns one row per channel per run with summary metrics
(min / max / mean / stddev / point count) for the configured time window.

**Auth.** Uses OIDC bearer-token auth (Keycloak password grant) rather
than the legacy `apikey` header — bearer is what production deployments
default to, and it's the right pattern for analysis scripts that run as
a specific person.

Usage:
    SHEPARD_API=https://shepard-api.example.dlr.de \\
    OIDC_ISSUER=https://shepard-auth.example.dlr.de/realms/shepard-demo \\
    OIDC_CLIENT_ID=frontend-dev \\
    OIDC_USERNAME=alice  OIDC_PASSWORD=alice-demo \\
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
import urllib.parse
import urllib.request


def _fetch_bearer_token(
    issuer: str, client_id: str, username: str, password: str
) -> str:
    """OIDC password grant against Keycloak. Returns the access_token.

    Public realm clients (`frontend-dev` in the demo) accept the
    Resource-Owner-Password-Credentials grant without a client secret.
    Production setups should prefer device-flow or authorization-code
    + PKCE; this is a script-friendly shortcut."""
    token_url = f"{issuer.rstrip('/')}/protocol/openid-connect/token"
    body = urllib.parse.urlencode({
        "grant_type": "password",
        "client_id": client_id,
        "username": username,
        "password": password,
    }).encode("utf-8")
    req = urllib.request.Request(
        token_url,
        data=body,
        headers={"Content-Type": "application/x-www-form-urlencoded"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=10) as resp:
        payload = json.loads(resp.read().decode("utf-8"))
    token = payload.get("access_token")
    if not token:
        raise RuntimeError(f"No access_token in Keycloak response: {payload}")
    return token


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument(
        "--api",
        default=os.environ.get("SHEPARD_API"),
        help="shepard API root, e.g. https://shepard-api.example.dlr.de",
    )
    ap.add_argument(
        "--issuer",
        default=os.environ.get("OIDC_ISSUER"),
        help="OIDC issuer URL (Keycloak realm), e.g. "
        "https://shepard-auth.example.dlr.de/realms/shepard-demo",
    )
    ap.add_argument(
        "--client-id",
        default=os.environ.get("OIDC_CLIENT_ID", "frontend-dev"),
        help="OIDC client id (public — no secret).",
    )
    ap.add_argument(
        "--username",
        default=os.environ.get("OIDC_USERNAME"),
        help="OIDC username (e.g. alice).",
    )
    ap.add_argument(
        "--password",
        default=os.environ.get("OIDC_PASSWORD"),
        help="OIDC password (consider --apikey instead for non-interactive use).",
    )
    ap.add_argument(
        "--apikey",
        default=os.environ.get("SHEPARD_API_KEY"),
        help="Fallback legacy API key — bypasses OIDC. Production scripts "
        "should prefer the bearer-token flow above.",
    )
    ap.add_argument(
        "--container",
        default="lumen-inspired-sensors",
        help="timeseries container name to query",
    )
    args = ap.parse_args(argv)

    if not args.api:
        ap.error("set --api or SHEPARD_API")

    api = args.api.rstrip("/")
    if api.endswith("/shepard/api"):
        api = api[: -len("/shepard/api")]

    # Auth: prefer bearer, fall back to apikey if the OIDC inputs are absent.
    auth_headers: dict[str, str]
    if args.issuer and args.username and args.password:
        try:
            token = _fetch_bearer_token(
                args.issuer, args.client_id, args.username, args.password
            )
        except urllib.error.HTTPError as e:
            print(
                f"Keycloak token grant failed: HTTP {e.code} {e.read().decode('utf-8', errors='replace')[:200]}",
                file=sys.stderr,
            )
            return 1
        auth_headers = {"Authorization": f"Bearer {token}"}
    elif args.apikey:
        auth_headers = {"apikey": args.apikey}
    else:
        ap.error(
            "no auth configured — provide either --issuer/--username/--password "
            "(bearer, preferred) or --apikey (legacy)"
        )

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
            **auth_headers,
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
