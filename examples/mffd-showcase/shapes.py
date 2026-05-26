#!/usr/bin/env python3
"""
shapes.py — MFFD LBR iiwa Force-Trace VIEW_RECIPE demonstrator.

Seeds a VIEW_RECIPE ShepardTemplate that maps the LBR iiwa 14 R820 6-axis
force-torque channels from the MFFD synthetic dataset to the Trace3D renderer:

    force_x_N  → role "x"       (Fx, newtons)
    force_y_N  → role "y"       (Fy, newtons)
    force_z_N  → role "z"       (Fz, newtons)
    torque_z_Nm → role "color"  (Tz, newton-metres — scalar colour channel)

The resulting 3D force-trajectory traces 42 cleat-insertion cycles (reach →
insert → withdraw, ~2.7 s each) during stringer attachment.  Colour encodes
torque about the tool axis.

Usage:
    python3 shapes.py --url https://shepard.nuclide.systems --api-key <token>

Options:
    --url       Backend base URL  (default: http://localhost:8080)
    --api-key   Bearer token or X-API-KEY value  (required)
    --dry-run   Print the template body and exit without POSTing

Designed for: task #83 — TPL1+TPL2 M1 milestone acceptance test.
Design ref:   aidocs/platform/83-tpl1-tpl2-shapes-templates-views.md §MFFD
Channel data: examples/mffd-showcase/data/generate.py (LBR_FT channels, 10 Hz, 120 s)
"""
from __future__ import annotations

import argparse
import json
import sys
import urllib.error
import urllib.request

# ── channel-selector constants ────────────────────────────────────────────────
#
# 5-tuple channel identity (pre TS-ID migration, aidocs/platform/87).
# Fields mirror what the MFFD ingest script writes to TimescaleDB:
#   measurement = process step name used at ingest time
#   device      = robot model identifier
#   location    = facility code
#   symbolicName = sensor group
#   field       = signal name matching the CSV column headers in data/
#
# Post TS-ID migration these become a single shepardId string; the VIEW_RECIPE
# body format will change accordingly.

_LBR_SELECTOR_TMPL = json.dumps({
    "measurement":  "CleatsWithLBR",
    "device":       "iiwa14_R820",
    "location":     "ZLP_Augsburg",
    "symbolicName": "lbr_force_torque",
    "field":        "{field}",  # replaced below
})


def _selector(field: str) -> str:
    """Build a JSON-string channel selector for the given field name."""
    return json.dumps({
        "measurement":  "CleatsWithLBR",
        "device":       "iiwa14_R820",
        "location":     "ZLP_Augsburg",
        "symbolicName": "lbr_force_torque",
        "field":        field,
    })


# ── template body ─────────────────────────────────────────────────────────────

TEMPLATE_NAME = "MFFD LBR iiwa Force-Trace (Trace3D)"
TEMPLATE_DESCRIPTION = (
    "VIEW_RECIPE template driving the Trace3D renderer on MFFD LBR iiwa 14 R820 "
    "force-torque data.  Maps the 3-axis force vector to x/y/z and the tool-axis "
    "torque to the colour scalar.  Designed for the Cleats-with-LBR process step "
    "(step 7 in the MFFD upper-fuselage assembly chain, synthetic dataset)."
)

VIEW_RECIPE_BODY: dict = {
    "renderer": "tresjs",
    "channelBindings": [
        {
            "role":            "x",
            "channelSelector": _selector("force_x_N"),
            "unit":            "http://qudt.org/vocab/unit/N",
            "required":        True,
        },
        {
            "role":            "y",
            "channelSelector": _selector("force_y_N"),
            "unit":            "http://qudt.org/vocab/unit/N",
            "required":        True,
        },
        {
            "role":            "z",
            "channelSelector": _selector("force_z_N"),
            "unit":            "http://qudt.org/vocab/unit/N",
            "required":        True,
        },
        {
            "role":            "color",
            "channelSelector": _selector("torque_z_Nm"),
            "unit":            "http://qudt.org/vocab/unit/N-M",
            "required":        False,
        },
    ],
}


# ── HTTP helpers ──────────────────────────────────────────────────────────────

def _v2_base(url: str) -> str:
    """Strip /shepard/api suffix so v2 calls land at the backend root."""
    url = url.rstrip("/")
    for suffix in ("/shepard/api", "/shepard/api/"):
        if url.endswith(suffix.rstrip("/")):
            return url[: -len(suffix.rstrip("/"))]
    return url


def _headers(api_key: str) -> dict[str, str]:
    """Return request headers.  Supports both API-key and Bearer token."""
    h: dict[str, str] = {"Content-Type": "application/json", "Accept": "application/json"}
    if api_key.startswith("Bearer "):
        h["Authorization"] = api_key
    elif api_key.startswith("ey"):
        h["Authorization"] = f"Bearer {api_key}"
    else:
        h["X-API-KEY"] = api_key
    return h


def _request(method: str, url: str, headers: dict, body: bytes | None = None) -> tuple[int, dict]:
    req = urllib.request.Request(url, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        payload: dict = {}
        try:
            payload = json.loads(e.read().decode("utf-8"))
        except Exception:
            pass
        return e.code, payload


# ── main logic ────────────────────────────────────────────────────────────────

def find_existing(base: str, headers: dict, name: str) -> str | None:
    """Return the appId of an existing template with this name, or None."""
    status, body = _request("GET", f"{base}/v2/templates?kind=VIEW_RECIPE", headers)
    if status != 200:
        return None
    rows = body if isinstance(body, list) else body.get("content", body.get("results", []))
    for t in rows:
        if isinstance(t, dict) and t.get("name") == name:
            return t.get("appId")  # type: ignore[return-value]
    return None


def create_template(base: str, headers: dict, dry_run: bool) -> str | None:
    """POST the VIEW_RECIPE template and return its appId.  Idempotent."""
    payload = {
        "name":         TEMPLATE_NAME,
        "templateKind": "VIEW_RECIPE",
        "body":         json.dumps(VIEW_RECIPE_BODY),
        "description":  TEMPLATE_DESCRIPTION,
        "tags":         ["mffd", "lbr-iiwa", "force-trace", "trace3d", "VIEW_RECIPE"],
    }

    if dry_run:
        print("── Dry run: template payload that would be POSTed to POST /v2/templates ──")
        print(json.dumps(payload, indent=2))
        print()
        print("── VIEW_RECIPE body (pretty-printed) ──")
        print(json.dumps(VIEW_RECIPE_BODY, indent=2))
        return None

    # Idempotency check — skip if already present.
    existing_id = find_existing(base, headers, TEMPLATE_NAME)
    if existing_id:
        print(f"SKIP  {TEMPLATE_NAME!r} already exists → appId: {existing_id}")
        return existing_id

    body_bytes = json.dumps(payload).encode("utf-8")
    status, body = _request("POST", f"{base}/v2/templates", headers, body_bytes)

    if status in (200, 201):
        app_id: str = body.get("appId", "")
        print(f"OK    Created template {TEMPLATE_NAME!r} → appId: {app_id}")
        return app_id
    elif status == 409:
        print(f"SKIP  {TEMPLATE_NAME!r} already exists (409 conflict)")
        return find_existing(base, headers, TEMPLATE_NAME)
    elif status in (401, 403):
        print(f"ERROR {TEMPLATE_NAME!r}: authentication failed (HTTP {status}). "
              "Check --api-key is instance-admin or authenticated user.", file=sys.stderr)
        return None
    else:
        print(f"ERROR POST /v2/templates → HTTP {status}: {body}", file=sys.stderr)
        return None


def smoke_test(base: str, headers: dict, template_app_id: str, focus_id: str = "test-focus") -> None:
    """Call POST /v2/shapes/render and print the response to verify wiring."""
    req_body = json.dumps({
        "templateAppId":  template_app_id,
        "focusShepardId": focus_id,
    }).encode("utf-8")
    status, body = _request("POST", f"{base}/v2/shapes/render", headers, req_body)
    print()
    print("── POST /v2/shapes/render smoke test ──")
    print(f"    status:   {status}")
    if status == 200:
        print(f"    renderer: {body.get('renderer')}")
        bindings = body.get("channelBindings", [])
        print(f"    bindings: {len(bindings)} declared")
        for b in bindings:
            print(f"      role={b.get('role'):8s}  status={b.get('status')}  "
                  f"required={b.get('required')}")
    else:
        print(f"    body: {body}", file=sys.stderr)


# ── CLI entry point ───────────────────────────────────────────────────────────

def main() -> None:
    parser = argparse.ArgumentParser(
        description="Seed the MFFD LBR iiwa Force-Trace VIEW_RECIPE template.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    parser.add_argument("--url",       default="http://localhost:8080",
                        help="Backend base URL (default: http://localhost:8080)")
    parser.add_argument("--api-key",   required=False, default="",
                        help="API key or Bearer token (required unless --dry-run)")
    parser.add_argument("--dry-run",   action="store_true",
                        help="Print the template payload and exit without POSTing")
    parser.add_argument("--smoke",     action="store_true",
                        help="Run POST /v2/shapes/render after creation to verify wiring")
    parser.add_argument("--focus-id",  default="test-focus",
                        help="DataObject appId to use in the smoke-test render call "
                             "(default: 'test-focus' — any appId is valid in beta, "
                             "bindings are DECLARED regardless of focus)")
    args = parser.parse_args()

    if not args.dry_run and not args.api_key:
        parser.error("--api-key is required unless --dry-run is set")

    base    = _v2_base(args.url)
    headers = _headers(args.api_key) if args.api_key else {
        "Content-Type": "application/json",
        "Accept":       "application/json",
    }

    app_id = create_template(base, headers, args.dry_run)

    if app_id and args.smoke:
        smoke_test(base, headers, app_id, focus_id=args.focus_id)

    if app_id:
        print()
        print("── Next step: open the shape render playground ──")
        print(f"    URL:      {base.rstrip('/')}/shapes/render")
        print(f"    template: {app_id}")
        print("    Enter the template appId + any DataObject appId → 'Fetch bindings'")
        print("    Then enter the LBR timeseries container ID + 'Render 3D'")
        print("    Expected renderer dispatch: 'tresjs' → Trace3DView")


if __name__ == "__main__":
    main()
