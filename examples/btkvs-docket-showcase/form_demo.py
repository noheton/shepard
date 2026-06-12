"""BT-KVS docket showcase — Streamlit form demo (BTKVS-B2).

Renders the docket ``:general`` form from the compiled descriptor
(``GET /v2/templates/{appId}/form``, doc 125 §5.1), submits through the
server-computed ``submit`` block, and maps the 422 ``violations[]``
(doc 125 §5.2) back to inline per-field errors — ``violations[].path``
equals ``fields[].path``, so the mapping is a dictionary lookup.

This file doubles as the descriptor's Python consumption example (doc
125 §9 risk 6 — the BT-KVS group keeps their Streamlit frontend).

Run the UI::

    streamlit run form_demo.py -- --host https://shepard-api.nuclide.systems \
        --apikey <token> --template <templateAppId> --collection <collectionAppId>

Headless self-test (no network, no streamlit needed)::

    python3 form_demo.py --selftest
"""

from __future__ import annotations

import argparse
import json
import sys
import urllib.error
import urllib.request
from typing import Any

DASH = "http://datashapes.org/dash#"


# ---------------------------------------------------------------------------
# Pure helpers (network + mapping) — exercised by the self-test.


def _req(method: str, url: str, apikey: str, body: Any = None) -> tuple[int, Any]:
    headers = {"X-API-KEY": apikey, "Accept": "application/json"}
    data = None
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            raw = resp.read()
            return resp.status, (json.loads(raw) if raw else None)
    except urllib.error.HTTPError as e:
        raw = e.read()
        try:
            return e.code, json.loads(raw)
        except (json.JSONDecodeError, ValueError):
            return e.code, {"detail": raw.decode("utf-8", "replace")[:400]}


def fetch_descriptor(host: str, apikey: str, template_app_id: str) -> dict:
    status, body = _req("GET", f"{host.rstrip('/')}/v2/templates/{template_app_id}/form", apikey)
    if status != 200:
        raise RuntimeError(f"descriptor fetch failed: HTTP {status}: {body}")
    return body


def submit_form(host: str, apikey: str, descriptor: dict, collection_app_id: str,
                name: str, attributes: dict[str, str]) -> tuple[int, Any]:
    """POST to the server-computed submit target — the client never chooses an endpoint."""
    href = descriptor["submit"]["href"].replace("{collectionAppId}", collection_app_id)
    return _req(descriptor["submit"]["method"], f"{host.rstrip('/')}{href}", apikey,
                {"name": name, "attributes": attributes})


def violations_by_path(problem: Any) -> dict[str, str]:
    """422 problem-JSON → {fields[].path → message} (doc 125 §5.2 dictionary lookup)."""
    out: dict[str, str] = {}
    for v in (problem or {}).get("violations", []) or []:
        path = v.get("path")
        if path:
            out[path] = v.get("message") or v.get("constraint") or "invalid value"
    return out


# ---------------------------------------------------------------------------
# Streamlit rendering (imported lazily so the self-test stays stdlib-only).


def render(args: argparse.Namespace) -> None:
    import streamlit as st

    st.set_page_config(page_title="BT-KVS docket form", layout="centered")
    descriptor = fetch_descriptor(args.host, args.apikey, args.template)
    st.title(descriptor.get("title", "Form"))
    st.caption(f"Shape: `{descriptor.get('shapeIri')}` · kind {descriptor.get('templateKind')}")

    errors: dict[str, str] = st.session_state.get("errors", {})
    group_labels = {g["id"]: g.get("label") or g["id"] for g in descriptor.get("groups", [])}
    values: dict[str, str] = {}
    current_group = None

    for f in descriptor.get("fields", []):
        if f.get("group") != current_group:
            current_group = f.get("group")
            if current_group:
                st.subheader(group_labels.get(current_group, current_group))
        label = f.get("label") or f["path"]
        if f.get("required"):
            label += " *"
        kwargs = {"key": f["path"], "placeholder": f.get("placeholder") or "", "help": f.get("description")}
        editor = f.get("editor", "")
        if editor == DASH + "EnumSelectEditor":
            raw = st.selectbox(label, options=[""] + (f.get("options") or []), key=f["path"], help=f.get("description"))
        elif editor == DASH + "DatePickerEditor":
            picked = st.date_input(label, value=None, key=f["path"], help=f.get("description"))
            raw = picked.isoformat() if picked else ""
        elif editor == DASH + "TextAreaEditor":
            raw = st.text_area(label, **kwargs)
        elif editor == DASH + "BooleanSelectEditor":
            raw = "true" if st.checkbox(label, key=f["path"], help=f.get("description")) else "false"
        else:  # TextFieldEditor + fallback
            raw = st.text_input(label, **kwargs)
        if f["path"] in errors:
            st.error(errors[f["path"]], icon="⚠️")  # inline field error (BTKVS-B2 acceptance)
        if raw:
            values[f.get("attributeKey") or f["path"]] = str(raw)

    if st.button("Submit", type="primary"):
        doc_name = f"Docket {values.get('docket_id', 'draft')} — general"
        status, body = submit_form(args.host, args.apikey, descriptor, args.collection, doc_name, values)
        if status == 201:
            st.session_state["errors"] = {}
            st.success(f"Created DataObject `{(body or {}).get('appId', '?')}`")
        elif status == 422:
            st.session_state["errors"] = violations_by_path(body)
            st.warning((body or {}).get("detail", "Validation failed"))
            st.rerun()
        else:
            st.error(f"HTTP {status}: {json.dumps(body)[:400]}")


# ---------------------------------------------------------------------------
# Headless self-test — descriptor → bad submit → violation maps to the field.


def selftest() -> int:
    descriptor = {
        "fields": [
            {"path": "urn:shepard:attribute:docket_id", "attributeKey": "docket_id",
             "label": "Docket ID", "pattern": "^[A-Z][0-9]{3}$", "required": True},
            {"path": "urn:shepard:attribute:project", "attributeKey": "project", "label": "Project"},
        ],
        "submit": {"method": "POST",
                   "href": "/v2/collections/{collectionAppId}/data-objects/from-template/tmpl-1"},
    }
    problem_422 = {
        "type": "/problems/template-instantiation.unprocessable",
        "status": 422,
        "detail": "DataObject violates the template's SHACL shape.",
        "violations": [{
            "path": "urn:shepard:attribute:docket_id",
            "value": "123",
            "constraint": "http://www.w3.org/ns/shacl#PatternConstraintComponent",
            "message": "Value does not match pattern \"^[A-Z][0-9]{3}$\"",
        }],
    }
    mapped = violations_by_path(problem_422)
    field_paths = {f["path"] for f in descriptor["fields"]}
    assert set(mapped) <= field_paths, "every violation path must map to a rendered field"
    assert "urn:shepard:attribute:docket_id" in mapped, "Docket-ID violation must land on the Docket-ID field"
    assert "pattern" in mapped["urn:shepard:attribute:docket_id"], "message should carry the pattern"
    href = descriptor["submit"]["href"].replace("{collectionAppId}", "coll-1")
    assert href == "/v2/collections/coll-1/data-objects/from-template/tmpl-1"
    print("selftest OK — 422 violations[] maps to descriptor fields by path")
    return 0


def main() -> int:
    if "--selftest" in sys.argv:
        return selftest()
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--host", required=True, help="Shepard API base")
    p.add_argument("--apikey", required=True, help="X-API-KEY token")
    p.add_argument("--template", required=True, help="appId of the STRUCTURED_RECIPE form template")
    p.add_argument("--collection", required=True, help="appId of the target Collection")
    args, _ = p.parse_known_args()
    render(args)
    return 0


if __name__ == "__main__":
    sys.exit(main())
