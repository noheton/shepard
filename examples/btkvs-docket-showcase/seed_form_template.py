"""BT-KVS docket showcase — form-template seed (BTKVS-B2).

Registers the **docket ``:general`` section** as a ``STRUCTURED_RECIPE``
``:ShepardTemplate`` whose ``shapeGraph`` is authored **through the
ShaclShapeBuilder JSON DSL** (``POST /v2/shapes/build``) — never
hand-written Turtle. The registered template is the doc-125 §5.1
acceptance artifact: ``GET /v2/templates/{appId}/form`` compiles it into
the form descriptor the Streamlit demo (``form_demo.py``) renders.

The shape models the six ``general``-section fields of the canonical
``I123`` docket (``seed.py`` / ``aidocs/integrations/116``):

==============  ===============================  =========================
field           constraint                        hints
==============  ===============================  =========================
docket_id       string, required, ``^[A-Z]\\d{3}$``  TextField, group Identity,
                                                  placeholder ``I123``,
                                                  cell ``K1`` @ Laufzettel sheet
project         string, required                  TextField, group Identity,
                                                  cell ``C4``
project_lead    string                            TextField, group Identity
delivery_date   ``xsd:date``                      (DASH-scored → DatePicker)
ktr             ``xsd:integer``                   placeholder ``123456``
comments        string                            ``dash:singleLine false``
                                                  (DASH-scored → TextArea)
==============  ===============================  =========================

**Path-namespace note (written deviation per doc 125 §5.1):** property
paths live in ``urn:shepard:attribute:*`` — the namespace the
validate-on-instantiate seam (``TemplateInstantiationRest``) builds the
candidate data graph in — so the form's submit leg actually validates
the user's input today. The ``urn:btkvs:docket:*`` predicate paths of
the §5.1 worked example arrive with the BTKVS-C1 decompose endpoint,
whose plugin owns that namespace.

Usage::

    python3 seed_form_template.py --host https://shepard-api.nuclide.systems --apikey <token>

Idempotent — by-name + kind lookup on the template before create;
safe to re-run. Requires an instance-admin token (template writes are
admin-gated).
"""

from __future__ import annotations

import argparse
import json
import sys
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from typing import Any

TEMPLATE_NAME = "Docket — general section"
TEMPLATE_KIND = "STRUCTURED_RECIPE"
SHAPE_IRI = "urn:btkvs:shape:docket-general"
GROUP_IDENTITY = "urn:btkvs:group:identity"
ATTR = "urn:shepard:attribute:"
XSD = "http://www.w3.org/2001/XMLSchema#"
DASH = "http://datashapes.org/dash#"
INSTANCE_URI = "urn:shepard:instance:candidate"
LAUFZETTEL_SHEET = "Laufzettel C-C bzw C-C-SiC"

TEMPLATE_DESCRIPTION = (
    "BTKVS-B2 acceptance shape: the docket :general section as a SHACL "
    "form template (aidocs/integrations/125 §5.1). Authored through the "
    "ShaclShapeBuilder JSON DSL with DASH editor hints + 2 Excel "
    "cell-mappings (Docket-ID → K1, Project → C4 on the Laufzettel "
    "sheet). GET /v2/templates/{appId}/form compiles it into the form "
    "descriptor; the submit leg is the V2CONV-B2-validated instantiation "
    "endpoint, whose 422 carries structured violations[]."
)


def docket_general_dsl() -> dict[str, Any]:
    """The ShaclShapeBuilder JSON DSL for the docket ``:general`` shape."""
    return {
        "shapeIri": SHAPE_IRI,
        "targetNode": INSTANCE_URI,
        "closed": False,
        "groups": [{"iri": GROUP_IDENTITY, "label": "Identity", "order": 1}],
        "properties": [
            {
                "path": ATTR + "docket_id",
                "datatype": XSD + "string",
                "minCount": 1,
                "maxCount": 1,
                "pattern": "^[A-Z][0-9]{3}$",
                "hints": {
                    "name": "Docket ID",
                    "description": "Internal docket identifier — one capital letter + three digits.",
                    "order": 1,
                    "group": GROUP_IDENTITY,
                    "editor": DASH + "TextFieldEditor",
                    "placeholder": "I123",
                    "cellMapping": {"sheet": LAUFZETTEL_SHEET, "cell": "K1"},
                },
            },
            {
                "path": ATTR + "project",
                "datatype": XSD + "string",
                "minCount": 1,
                "hints": {
                    "name": "Project",
                    "order": 2,
                    "group": GROUP_IDENTITY,
                    "editor": DASH + "TextFieldEditor",
                    "cellMapping": {"cell": "C4"},
                },
            },
            {
                "path": ATTR + "project_lead",
                "datatype": XSD + "string",
                "hints": {
                    "name": "Project lead",
                    "order": 3,
                    "group": GROUP_IDENTITY,
                    "editor": DASH + "TextFieldEditor",
                },
            },
            {
                "path": ATTR + "delivery_date",
                "datatype": XSD + "date",
                # no explicit editor — exercises DASH constraint scoring → DatePicker
                "hints": {"name": "Delivery date", "order": 4},
            },
            {
                "path": ATTR + "ktr",
                "datatype": XSD + "integer",
                "hints": {"name": "KTR (cost centre)", "order": 5, "placeholder": "123456"},
            },
            {
                "path": ATTR + "comments",
                "datatype": XSD + "string",
                # singleLine false + no editor — DASH scoring → TextArea
                "hints": {"name": "Comments", "order": 6, "singleLine": False},
            },
        ],
    }


# ---------------------------------------------------------------------------
# Minimal HTTP helpers (stdlib-only, mirrors seed.py)


def _log(status: str, name: str, kind: str = "", extra: str = "") -> None:
    tail = ""
    if kind or extra:
        tail = f" ({kind}" + (f", {extra}" if extra else "") + ")"
    print(f"{status:<6} {name}{tail}", flush=True)


@dataclass
class Api:
    host: str
    apikey: str

    def _req(self, method: str, path: str, *, json_body: Any = None, params: dict[str, str] | None = None) -> Any:
        url = f"{self.host.rstrip('/')}{path}"
        if params:
            url = f"{url}?{urllib.parse.urlencode(params)}"
        headers = {"X-API-KEY": self.apikey, "Accept": "application/json"}
        data: bytes | None = None
        if json_body is not None:
            data = json.dumps(json_body).encode("utf-8")
            headers["Content-Type"] = "application/json"
        req = urllib.request.Request(url, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=120) as resp:
                body = resp.read()
                return json.loads(body) if body else None
        except urllib.error.HTTPError as e:
            raise RuntimeError(f"HTTP {e.code} on {method} {path}: {e.read()[:400]!r}") from e

    def get(self, path: str, **kw: Any) -> Any:
        return self._req("GET", path, **kw)

    def post(self, path: str, **kw: Any) -> Any:
        return self._req("POST", path, **kw)


# ---------------------------------------------------------------------------
# Seed flow


def seed(api: Api) -> None:
    # 1. Compile the DSL through the builder endpoint — the shape is authored
    #    via the DSL, the server emits the canonical deterministic Turtle.
    build = api.post("/v2/shapes/build", json_body=docket_general_dsl())
    if not build or build.get("error"):
        raise RuntimeError(f"shape build failed: {build}")
    shape_graph = build["shapeGraph"]
    _log("OK", SHAPE_IRI, "shapeGraph compiled", f"{len(shape_graph)} chars")

    # 2. Idempotency: skip when a non-retired template with this name+kind exists.
    existing = api.get("/v2/templates", params={"kind": TEMPLATE_KIND})
    for t in existing or []:
        if t.get("name") == TEMPLATE_NAME:
            _log("SKIP", TEMPLATE_NAME, "ShepardTemplate (exists)", t["appId"])
            verify(api, t["appId"])
            return

    # 3. Register the STRUCTURED_RECIPE template carrying the shapeGraph.
    body = {
        "structuredData": {"name": "docket-general", "section": "general"},
        "shapeGraph": shape_graph,
    }
    created = api.post(
        "/v2/templates",
        json_body={
            "name": TEMPLATE_NAME,
            "templateKind": TEMPLATE_KIND,
            "body": json.dumps(body, ensure_ascii=False),
            "description": TEMPLATE_DESCRIPTION,
            "tags": ["btkvs", "docket", "form", "shacl"],
            "iconKey": "mdi-form-select",
        },
    )
    _log("OK", TEMPLATE_NAME, "ShepardTemplate", created["appId"])
    verify(api, created["appId"])


def verify(api: Api, app_id: str) -> None:
    """Round-trip check: the descriptor endpoint must compile 6 fields."""
    descriptor = api.get(f"/v2/templates/{app_id}/form")
    fields = descriptor.get("fields", [])
    groups = descriptor.get("groups", [])
    _log("OK", f"GET /v2/templates/{app_id}/form", "descriptor", f"{len(fields)} fields, {len(groups)} groups")
    if len(fields) != 6:
        raise RuntimeError(f"expected 6 descriptor fields, got {len(fields)}")
    print(f"\n  Template appId:  {app_id}")
    print(f"  Submit target:   {descriptor['submit']['method']} {descriptor['submit']['href']}")
    print(f"  Demo:            python3 form_demo.py --host {api.host} --apikey … --template {app_id} --collection <collectionAppId>")


def main(argv: list[str]) -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--host", required=True, help="Shepard API base, e.g. https://shepard-api.nuclide.systems")
    p.add_argument("--apikey", required=True, help="X-API-KEY token (instance-admin — template writes are admin-gated)")
    args = p.parse_args(argv)
    seed(Api(host=args.host, apikey=args.apikey))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
