#!/usr/bin/env python3
"""feat-unified-refs-containers — A2/A3: unified /v2/references + /v2/containers.

Proves the V2CONV-A2 (references) + A3 (containers) convergence: one appId-keyed
CRUD surface dispatched by ``?kind=``, instead of the per-kind v1 resources.

Containers (A3) — ``POST|GET /v2/containers?kind=…`` for file / timeseries /
structured-data:
  * create one container of each kind (body ``{name}``),
  * GET each back by appId (self-describing ``kind`` discriminator),
  * list by kind and confirm ours appears.

References (A2) — ``POST|GET /v2/references?kind=…&dataObjectAppId=…``:
  * create a ``kind=uri`` reference (``{uri, relationship}``),
  * create a ``kind=file`` singleton via the multipart ``POST /v2/files`` entry
    (the unified create path intentionally rejects binary ``kind=file`` and
    points here),
  * list ``kind=uri`` and ``kind=file`` references on the DataObject,
  * GET each reference back by appId.

Every identifier on the wire is an ``appId`` (UUID v7), never a numeric id.

References (external):
  * OpenAPI 3 / JSON-Schema (the request/response shapes): https://spec.openapis.org/oas/v3.1.0
  * RFC 7396 (JSON Merge Patch) — the PATCH semantics of these surfaces:
    https://www.rfc-editor.org/rfc/rfc7396

Run:
  /tmp/reseed-venv/bin/python examples/feature-showcase/feat-unified-refs-containers/seed.py \
      --host http://localhost:8080/shepard/api --apikey "$(cat /tmp/reseed_apikey.txt)"
"""

from __future__ import annotations

import json
import sys
import urllib.error
import urllib.request
import uuid
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
    v2_get,
    v2_post,
)

SLUG = "unified-refs-containers"
COLLECTION_NAME = f"feat-{SLUG}"
COLLECTION_DESCRIPTION = (
    "V2CONV-A2/A3 showcase (synthetic). The unified /v2/references?kind= and "
    "/v2/containers?kind= surfaces exercised across file / timeseries / "
    "structured-data / uri. appId-keyed throughout. No real DLR/MFFD data."
)

# Container kinds the in-tree handlers support today (plugin kinds like hdf 400
# until their module ships a ContainerKindHandler).
CONTAINER_KINDS = ["file", "timeseries", "structured-data"]


def ensure_container(ctx, kind: str) -> str:
    """Idempotent: reuse a feat-named container of this kind, else create it."""
    name = f"feat-{SLUG}-{kind}"
    status, rows, _ = v2_get(ctx, f"/containers?kind={kind}&name={name}")
    if status == 200 and isinstance(rows, list):
        existing = next((r for r in rows if r.get("name") == name), None)
        if existing is not None:
            log("SKIP", f"container kind={kind} '{name}'", existing["appId"])
            return existing["appId"]
    status, body, _ = v2_post(ctx, f"/containers?kind={kind}", {"name": name})
    if status != 201:
        log("FAIL", f"container kind={kind} create returned {status}: {str(body)[:160]}")
        sys.exit(1)
    log("OK", f"container kind={kind} '{name}'", body["appId"])
    return body["appId"]


def ensure_uri_reference(ctx, do_app_id: str) -> str:
    name = "synthetic-uri"
    status, rows, _ = v2_get(ctx, f"/references?kind=uri&dataObjectAppId={do_app_id}")
    if status == 200 and isinstance(rows, list):
        existing = next((r for r in rows if r.get("name") == name), None)
        if existing is not None:
            log("SKIP", f"uri reference '{name}'", existing["appId"])
            return existing["appId"]
    status, body, _ = v2_post(
        ctx,
        f"/references?kind=uri&dataObjectAppId={do_app_id}",
        {"name": name, "uri": "https://example.org/synthetic/spec.pdf", "relationship": "describes"},
    )
    if status != 201:
        log("FAIL", f"uri reference create returned {status}: {str(body)[:160]}")
        sys.exit(1)
    log("OK", f"uri reference '{name}'", body["appId"])
    return body["appId"]


def ensure_file_singleton(ctx, do_app_id: str) -> str:
    """Create a singleton FileReference via the multipart POST /v2/files entry.

    The unified create path rejects binary kind=file on purpose and points
    callers here; this is the FR1b single-file shape (CLAUDE.md "singleton
    FileReference for one-file uploads")."""
    name = "synthetic-note"
    status, rows, _ = v2_get(ctx, f"/references?kind=file&dataObjectAppId={do_app_id}")
    if status == 200 and isinstance(rows, list):
        existing = next((r for r in rows if r.get("name") == name), None)
        if existing is not None:
            log("SKIP", f"file singleton '{name}'", existing["appId"])
            return existing["appId"]

    content = b"synthetic showcase file payload (not real DLR data)\n"
    boundary = f"----shepard{uuid.uuid4().hex}"
    body = (
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="file"; filename="{name}.txt"\r\n'
        f"Content-Type: text/plain\r\n\r\n"
    ).encode() + content + f"\r\n--{boundary}--\r\n".encode()
    url = f"{ctx.v2}/files?name={name}&parentDataObjectAppId={do_app_id}"
    req = urllib.request.Request(
        url,
        data=body,
        headers={
            "X-API-KEY": ctx.apikey,
            "Content-Type": f"multipart/form-data; boundary={boundary}",
            "Accept": "application/json",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            payload = json.loads(r.read())
    except urllib.error.HTTPError as e:
        log("FAIL", f"POST /v2/files returned {e.code}: {e.read()[:160]!r}")
        sys.exit(1)
    app_id = payload.get("appId", "")
    log("OK", f"file singleton '{name}'", app_id)
    return app_id


def assert_get_by_appid(ctx, kind_label: str, app_id: str, path: str) -> None:
    status, body, _ = v2_get(ctx, path)
    if status == 200 and isinstance(body, dict):
        log("OK", f"GET {kind_label} by appId → 200 (kind={body.get('kind')})", app_id)
    else:
        log("WARN", f"GET {kind_label} by appId → {status}: {str(body)[:120]}")


def main() -> None:
    args = build_parser(__doc__.splitlines()[0]).parse_args()
    ctx = make_ctx(args)

    if args.reset:
        section("RESET")
        reset_collection(ctx, COLLECTION_NAME)
        # Containers are top-level (not under the collection); leave prior
        # feat-named containers in place — ensure_container reuses them.

    section("COLLECTION + DATAOBJECT")
    coll = ensure_collection(ctx, COLLECTION_NAME, COLLECTION_DESCRIPTION)
    coll_app_id = collection_app_id(ctx, coll.id)
    do = ensure_data_object(ctx, coll, "unified-demo-do", {"demo": "v2conv-a2-a3"})
    do_app_id = data_object_app_id(ctx, coll.id, do.id)

    section("CONTAINERS (A3) — create + GET + list per kind")
    container_app_ids: dict[str, str] = {}
    for kind in CONTAINER_KINDS:
        cid = ensure_container(ctx, kind)
        container_app_ids[kind] = cid
        assert_get_by_appid(ctx, f"container[{kind}]", cid, f"/containers/{cid}")
        status, rows, _ = v2_get(ctx, f"/containers?kind={kind}")
        seen = isinstance(rows, list) and any(r.get("appId") == cid for r in rows)
        log("OK" if seen else "WARN", f"list /v2/containers?kind={kind} contains ours ({len(rows) if isinstance(rows, list) else '?'} total)")

    section("REFERENCES (A2) — uri + file singleton, create + GET + list")
    uri_ref = ensure_uri_reference(ctx, do_app_id)
    assert_get_by_appid(ctx, "reference[uri]", uri_ref, f"/references/{uri_ref}")
    file_ref = ensure_file_singleton(ctx, do_app_id)
    assert_get_by_appid(ctx, "reference[file]", file_ref, f"/references/{file_ref}")

    for kind in ("uri", "file"):
        status, rows, _ = v2_get(ctx, f"/references?kind={kind}&dataObjectAppId={do_app_id}")
        n = len(rows) if isinstance(rows, list) else "?"
        log("OK" if status == 200 else "WARN", f"list /v2/references?kind={kind} → {status} ({n} refs)")

    print_outcome(
        SLUG,
        coll_app_id,
        {
            "dataObject": do_app_id,
            **{f"container_{k}": v for k, v in container_app_ids.items()},
            "uri_reference": uri_ref,
            "file_reference": file_ref,
        },
    )


if __name__ == "__main__":
    main()
