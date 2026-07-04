"""feat-trace3d — Trace3D colour-mapped 3D path from X/Y/Z + value TimeseriesReferences.

Feature it proves
-----------------
The ``vis-trace3d`` plugin's ``Trace3DPngRenderer`` (a ``ViewRecipeRenderer`` SPI
impl) renders a VIEW_RECIPE template — declaring x / y / z + a colour-mapped scalar
channel binding — into a colour-mapped 3D brush trace. The render flows through the
converged, stateless ``POST /v2/shapes/render`` endpoint (aidocs/platform/191 #2):

  * ``Accept: application/json`` → the channel-binding view-model (status DECLARED;
    live channel resolution lands with TPL2c).
  * ``Accept: image/png``       → a real server-side PNG raster of the view recipe
    (pure-JVM AWT; the renderer's ``producibleMedia()`` content-negotiation path).

The shape IRI is the one the renderer claims:
``http://semantics.dlr.de/shepard-ui/trace3d#Trace3DViewShape``.

What this seed does
-------------------
  1. Creates the ``feat-trace3d`` Collection + a "TCP Brush Trace Demo" DataObject.
  2. Creates a TimeseriesContainer + four TimeseriesReferences (x, y, z, temp) whose
     channel selectors are the AFP-TCP-style 5-tuples the recipe binds.
  3. Mints a VIEW_RECIPE ShepardTemplate carrying the Trace3D shape IRI + the four
     channelBindings (roles x/y/z/color).
  4. POSTs ``/v2/shapes/render`` twice (JSON + PNG) and asserts:
     * JSON returns the four DECLARED channel bindings, and
     * PNG returns image/png bytes (the renderer's content-negotiation path),
       falling back gracefully (gap noted) if the plugin is disabled.

Synthetic data only — no real DLR/MFFD IP.

Usage
-----
    python seed.py --apikey "$API_KEY"
    python seed.py --apikey "$API_KEY" --reset
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from _client import HttpError, V2Client, build_arg_parser, client_from_args, log  # noqa: E402

SLUG = "feat-trace3d"
COLLECTION_DESC = (
    "Trace3D showcase: a colour-mapped 3D brush trace rendered from x/y/z + scalar "
    "TimeseriesReferences via POST /v2/shapes/render (vis-trace3d plugin). "
    "Synthetic data only — NOT real DLR/MFFD data."
)

TRACE3D_SHAPE_IRI = "http://semantics.dlr.de/shepard-ui/trace3d#Trace3DViewShape"

# AFP-TCP-style channel selectors (5-tuple measurement identity) the recipe binds.
CHANNELS = [
    # (role, symbolicName, unit IRI, required)
    ("x", "tcp_x", "http://qudt.org/vocab/unit/MilliM", True),
    ("y", "tcp_y", "http://qudt.org/vocab/unit/MilliM", True),
    ("z", "tcp_z", "http://qudt.org/vocab/unit/MilliM", True),
    ("color", "tcp_temp", "http://qudt.org/vocab/unit/DEG_C", False),
]
MEASUREMENT = "afp"
DEVICE = "tcp"
LOCATION = "cell"
FIELD = "value"


def _selector(symbolic: str) -> str:
    return json.dumps(
        {
            "measurement": MEASUREMENT,
            "device": DEVICE,
            "location": LOCATION,
            "symbolicName": symbolic,
            "field": FIELD,
        }
    )


def _ensure_container(c: V2Client, name: str) -> dict:
    rows = c.get("/containers?kind=timeseries") or []
    rows = rows if isinstance(rows, list) else rows.get("content", [])
    for row in rows:
        if row.get("name") == name:
            return row
    return c.post("/containers?kind=timeseries", {"name": name})


def _find_view_template(c: V2Client, name: str) -> dict | None:
    rows = c.get("/templates?kind=VIEW_RECIPE") or []
    rows = rows if isinstance(rows, list) else rows.get("content", [])
    for row in rows:
        if row.get("name") == name:
            return row
    return None


def main(argv: list[str] | None = None) -> int:
    parser = build_arg_parser(__doc__)
    args = parser.parse_args(argv)
    try:
        c = client_from_args(args)
    except SystemExit as e:
        log("ERROR", str(e), "")
        return 2

    if args.reset and c.reset_collection(SLUG):
        log("reset", SLUG, "Collection", "deleted existing")

    existing = c.find_collection_by_name(SLUG)
    if existing:
        coll = existing
        log("reuse", SLUG, "Collection", coll["appId"])
    else:
        coll = c.create_collection(SLUG, COLLECTION_DESC)
        log("create", SLUG, "Collection", coll["appId"])
    coll_app = coll["appId"]

    # DataObject (focus of the render projection).
    do = c.create_data_object(coll_app, "TCP Brush Trace Demo",
                              description="Synthetic AFP TCP path — x/y/z + temperature channels.")
    do_app = do["appId"]
    log("create", "TCP Brush Trace Demo", "DataObject", do_app)

    # TimeseriesContainer + one TimeseriesReference per channel.
    tsc = _ensure_container(c, f"{SLUG}-channels")
    tsc_id = tsc["id"]
    log("ensure", tsc["name"], "TimeseriesContainer", tsc["appId"])

    ref_app_ids: dict[str, str] = {}
    for role, symbolic, _unit, _req in CHANNELS:
        body = {
            "name": f"tcp-{role}",
            "start": 0,
            "end": 10_000,
            "timeseriesContainerId": tsc_id,
            "timeseries": [
                {
                    "measurement": MEASUREMENT,
                    "device": DEVICE,
                    "location": LOCATION,
                    "symbolicName": symbolic,
                    "field": FIELD,
                }
            ],
        }
        ref = c.post(f"/references?kind=timeseries&dataObjectAppId={do_app}", body)
        ref_app_ids[role] = ref["appId"]
        log("create", f"tcp-{role}", "TimeseriesReference", ref["appId"])

    # VIEW_RECIPE template carrying the Trace3D shape IRI + channel bindings.
    channel_bindings = [
        {
            "role": role,
            "channelSelector": _selector(symbolic),
            "unit": unit,
            "required": req,
        }
        for role, symbolic, unit, req in CHANNELS
    ]
    recipe_body = {
        "view": "trace3d",
        "renderer": "tresjs",
        "shape": TRACE3D_SHAPE_IRI,
        "viewRecipeShape": TRACE3D_SHAPE_IRI,
        "title": "AFP TCP Brush Trace",
        "trace3d:colorMap": "viridis",
        "channelBindings": channel_bindings,
    }
    tmpl = _find_view_template(c, f"{SLUG}-trace3d-recipe")
    if tmpl:
        log("reuse", tmpl["name"], "VIEW_RECIPE template", tmpl["appId"])
    else:
        tmpl = c.post(
            "/templates",
            {
                "name": f"{SLUG}-trace3d-recipe",
                "templateKind": "VIEW_RECIPE",
                "body": json.dumps(recipe_body),
                "description": "Trace3D x/y/z + colour view recipe (synthetic).",
                "tags": ["trace3d", "showcase", "synthetic"],
            },
        )
        log("create", tmpl["name"], "VIEW_RECIPE template", tmpl["appId"])
    tmpl_app = tmpl["appId"]

    # ── Render: JSON view-model ──────────────────────────────────────────────
    render_req = {"templateAppId": tmpl_app, "focusShepardId": do_app}
    view = c.post("/shapes/render", render_req)
    bindings = view.get("channelBindings") or []
    assert len(bindings) == len(CHANNELS), f"expected {len(CHANNELS)} bindings, got {len(bindings)}"
    roles = sorted(b.get("role") for b in bindings)
    log("render", "JSON view-model", "bindings", f"{roles} all status={bindings[0].get('status')}")

    # ── Render: PNG raster (content negotiation) ─────────────────────────────
    # The renderer's producibleMedia() PNG path: Accept: image/png → server-side
    # AWT raster. Fetch raw bytes directly (urllib) so we can inspect the PNG magic.
    import urllib.error
    import urllib.request

    png_ok = False
    png_req = urllib.request.Request(
        f"{c.v2}/shapes/render",
        data=json.dumps(render_req).encode(),
        headers={"X-API-KEY": c.api_key, "Accept": "image/png",
                 "Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(png_req, timeout=60) as resp:
            raw = resp.read()
            ctype = resp.getheader("Content-Type", "")
            if raw[:8] == b"\x89PNG\r\n\x1a\n":
                png_ok = True
                log("render", "PNG raster", "image/png", f"{len(raw)} bytes (vis-trace3d Trace3DPngRenderer)")
            else:
                # 200 but a JSON view-model fell out → the renderer fail-softed (e.g.
                # fontconfig missing). Documented as RESEED-FIND-TRACE3D-FONTCONFIG.
                log("GAP", "PNG fell back to JSON view-model", "vis-trace3d",
                    f"content-type={ctype} — renderer reached but raster failed "
                    "(RESEED-FIND-TRACE3D-FONTCONFIG: backend image needs fontconfig + a font)")
    except urllib.error.HTTPError as e:
        log("GAP", "PNG render failed", f"HTTP {e.code}", e.read()[:160].decode("utf-8", "replace"))

    # ── Verify against live ──────────────────────────────────────────────────
    got = c.get(f"/templates/{tmpl_app}")
    assert got["appId"] == tmpl_app and got["templateKind"] == "VIEW_RECIPE"
    log("verify", "template round-trips", "VIEW_RECIPE", tmpl_app)

    print()
    log("DONE", SLUG, "Collection", coll_app)
    print(f"   Frontend:  {c.frontend_collection_url(coll_app)}")
    print(f"   DataObject:{c.frontend_data_object_url(coll_app, do_app)}")
    print(f"   Template appId: {tmpl_app}")
    print(f"   Reference appIds: {ref_app_ids}")
    print(f"   PNG render: {'OK (vis-trace3d active)' if png_ok else 'GAP — JSON fallback (vis-trace3d disabled)'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
