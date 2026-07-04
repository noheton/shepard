#!/usr/bin/env python3
"""feat-render-media — A1: VIEW_RECIPE rendered via POST /v2/shapes/render.

Proves the V2CONV-A1 content-negotiation path on the shapes/render endpoint:

1. Create a VIEW_RECIPE :ShepardTemplate whose body declares ``viewRecipeShape``
   = the Trace3D view shape IRI claimed by the ``vis-trace3d`` plugin's
   ``Trace3DPngRenderer``, plus the declared channel bindings (x / y / z + value).
2. Render it with ``Accept: application/json`` → a JSON view-model (the channel
   binding projection, status=DECLARED).
3. Render it with ``Accept: image/png`` → real PNG bytes (a server-side raster
   card of the view recipe). We assert the PNG magic header.

When the vis-trace3d plugin is NOT installed, the PNG negotiation has no
renderer to call and the endpoint falls back to the JSON view-model — so this
seed degrades gracefully (it reports a gap instead of crashing).

References:
  * OpenAPI 3 content negotiation (Accept-driven media): https://spec.openapis.org/oas/v3.1.0
  * Trace3DPngRenderer.TRACE3D_VIEW_SHAPE_IRI (the shape this seed targets)
  * aidocs/semantics/98 §1.2 — VIEW_RECIPE render contract

Run:
  /tmp/reseed-venv/bin/python examples/feature-showcase/feat-render-media/seed.py \
      --host http://localhost:8080/shepard/api --apikey "$(cat /tmp/reseed_apikey.txt)"
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from _shared import (  # noqa: E402
    build_parser,
    collection_app_id,
    data_object_app_id,
    ensure_collection,
    ensure_data_object,
    log,
    make_ctx,
    print_outcome,
    reset_collection,
    section,
    v2_delete,
    v2_get,
    v2_post,
)

SLUG = "render-media"
COLLECTION_NAME = f"feat-{SLUG}"
COLLECTION_DESCRIPTION = (
    "V2CONV-A1 showcase (synthetic). A VIEW_RECIPE rendered via "
    "POST /v2/shapes/render: Accept=application/json → view-model, "
    "Accept=image/png → raster (vis-trace3d). No real DLR/MFFD data."
)
TEMPLATE_NAME = f"feat-{SLUG}-trace3d"
TEMPLATE_KIND = "VIEW_RECIPE"

# The Trace3D view shape IRI the vis-trace3d Trace3DPngRenderer claims.
TRACE3D_VIEW_SHAPE_IRI = "http://semantics.dlr.de/shepard-ui/trace3d#Trace3DViewShape"
PNG_MAGIC = b"\x89PNG\r\n\x1a\n"

TEMPLATE_BODY = json.dumps(
    {
        "view": "trace3d",
        "renderer": "tresjs",
        "viewRecipeShape": TRACE3D_VIEW_SHAPE_IRI,
        "title": "Synthetic AFP TCP path",
        "trace3d:colorMap": "viridis",
        "channelBindings": [
            {"role": "x", "channelSelector": '{"field":"tcp_x"}', "unit": "http://qudt.org/vocab/unit/MilliM", "required": True},
            {"role": "y", "channelSelector": '{"field":"tcp_y"}', "unit": "http://qudt.org/vocab/unit/MilliM", "required": True},
            {"role": "z", "channelSelector": '{"field":"tcp_z"}', "unit": "http://qudt.org/vocab/unit/MilliM", "required": True},
            {"role": "value", "channelSelector": '{"field":"tcp_temp"}', "unit": "http://qudt.org/vocab/unit/DEG_C", "required": False},
        ],
    }
)


def find_template(ctx) -> dict | None:
    status, rows, _ = v2_get(ctx, f"/templates?kind={TEMPLATE_KIND}")
    if status != 200 or not isinstance(rows, list):
        return None
    return next((r for r in rows if r.get("name") == TEMPLATE_NAME and not r.get("retired")), None)


def ensure_template(ctx) -> str:
    existing = find_template(ctx)
    if existing is not None:
        log("SKIP", f"template '{TEMPLATE_NAME}'", existing["appId"])
        return existing["appId"]
    status, body, _ = v2_post(
        ctx,
        "/templates",
        {
            "name": TEMPLATE_NAME,
            "templateKind": TEMPLATE_KIND,
            "description": "Trace3D VIEW_RECIPE rendered to JSON view-model + PNG raster.",
            "body": TEMPLATE_BODY,
        },
    )
    if status != 201:
        log("FAIL", f"template create returned {status}: {str(body)[:160]}")
        sys.exit(1)
    log("OK", f"template '{TEMPLATE_NAME}'", body["appId"])
    return body["appId"]


def main() -> None:
    args = build_parser(__doc__.splitlines()[0]).parse_args()
    ctx = make_ctx(args)

    if args.reset:
        section("RESET")
        reset_collection(ctx, COLLECTION_NAME)
        existing = find_template(ctx)
        if existing:
            v2_delete(ctx, f"/templates/{existing['appId']}")
            log("OK", "retired prior template", existing["appId"])

    section("COLLECTION + FOCUS DATAOBJECT")
    coll = ensure_collection(ctx, COLLECTION_NAME, COLLECTION_DESCRIPTION)
    coll_app_id = collection_app_id(ctx, coll.id)
    do = ensure_data_object(ctx, coll, "trace3d-focus", {"demo": "v2conv-a1"})
    focus_app_id = data_object_app_id(ctx, coll.id, do.id)

    section("VIEW_RECIPE TEMPLATE")
    template_app_id = ensure_template(ctx)

    render_body = {"templateAppId": template_app_id, "focusShepardId": focus_app_id}

    section("RENDER (Accept: application/json) → view-model")
    status, body, _ = v2_post(ctx, "/shapes/render", render_body, accept="application/json")
    if status == 200 and isinstance(body, dict):
        n = len(body.get("channelBindings") or [])
        log("OK", f"JSON view-model: renderer={body.get('renderer')} bindings={n}")
    else:
        log("WARN", f"JSON render expected 200, got {status}: {str(body)[:160]}")

    section("RENDER (Accept: image/png) → raster bytes")
    status, raw, hdrs = v2_post(ctx, "/shapes/render", render_body, accept="image/png")
    png_ok = False
    if status == 200 and isinstance(raw, (bytes, bytearray)) and raw[:8] == PNG_MAGIC:
        png_ok = True
        log("OK", f"PNG raster: {len(raw)} bytes, content-type={hdrs.get('Content-Type')}")
    elif status == 200 and isinstance(raw, (bytes, bytearray)):
        # Fell back to JSON view-model — no renderer claimed the PNG media type.
        log(
            "GAP",
            "Accept=image/png fell back to JSON view-model — no vis-trace3d "
            "Trace3DPngRenderer registered; PNG path not exercised.",
        )
    elif status == 422:
        # RESEED-FIND-RENDER-PNG-LOG: the renderer IS registered but its PNG
        # rasterise path throws at runtime, and its catch block calls
        # io.quarkus.logging.Log.warnf — which throws UnsupportedOperationException
        # because the plugin jar is not Jandex-indexed (no beans.xml). The
        # secondary-write Log failure masks the primary fault, surfacing 422.
        # Degrade gracefully: report the gap, don't crash.
        log("GAP", f"Accept=image/png → 422 (RESEED-FIND-RENDER-PNG-LOG): {str(raw)[:160]}")
    else:
        log("WARN", f"PNG render expected 200, got {status}: {str(raw)[:160]}")

    print_outcome(
        SLUG,
        coll_app_id,
        {
            "template": template_app_id,
            "focus_dataobject": focus_app_id,
            "png_rendered": "yes" if png_ok else "no (JSON fallback)",
        },
    )


if __name__ == "__main__":
    main()
