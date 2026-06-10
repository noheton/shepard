"""Shared helpers for the per-feature showcase seeds (Core V2CONV set).

Each ``feat-<slug>/seed.py`` proves ONE shipped V2CONV feature end-to-end
against the live backend with synthetic data only (no real DLR/MFFD IP).

Design mirrors ``examples/lumen-showcase/seed.py``:

* ``argparse`` ``--host`` / ``--apikey`` (+ ``--reset``).
* the generated ``shepard_client`` for v1 Collection / DataObject objects
  (the parts with no v2 SDK helper yet);
* plain ``urllib`` for the ``/v2/`` feature surfaces under test.

Hard project invariants enforced here (CLAUDE.md):

* **appId-only** — every URL / identifier we print or store is the UUID v7
  ``appId``, never the numeric Neo4j id.
* **Idempotent** — ``ensure_collection`` looks an existing ``feat-<slug>``
  Collection up by name and reuses it; ``--reset`` deletes first.
* **Synthetic only** — no real data.

This module is imported by each seed via a ``sys.path`` insert of the
parent ``feature-showcase`` directory, so the seeds stay one file each.
"""

from __future__ import annotations

import argparse
import json
import re
import urllib.error
import urllib.request
from dataclasses import dataclass
from typing import Any

from shepard_client import (  # type: ignore
    ApiClient,
    Collection,
    CollectionApi,
    CollectionSearchBody,
    CollectionSearchParams,
    Configuration,
    DataObject,
    DataObjectApi,
    PermissionType,
    SearchApi,
)

FRONTEND_BASE = "https://shepard.nuclide.systems"
"""Public frontend host. Printed links use ``appId`` paths only."""


# ---------------------------------------------------------------------------
# CLI + client bootstrap


def build_parser(description: str) -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(description=description)
    p.add_argument(
        "--host",
        default="http://localhost:8080/shepard/api",
        help="v1 API base (default: %(default)s). The v2 base is derived from it.",
    )
    p.add_argument("--apikey", required=True, help="X-API-KEY (instance-admin).")
    p.add_argument(
        "--reset",
        action="store_true",
        help="Delete the feat-<slug> collection (and any helper templates) before seeding.",
    )
    return p


@dataclass
class Ctx:
    """Everything a seed needs: the v1 host, the derived v2 base, the key,
    and the typed v1 APIs."""

    host: str
    v2: str
    apikey: str
    client: ApiClient
    collection: CollectionApi
    data_object: DataObjectApi
    search: SearchApi


def v2_base(host: str) -> str:
    """Derive the ``/v2`` base from the v1 ``/shepard/api`` host."""
    return re.sub(r"/shepard/api/?$", "/v2", host.rstrip("/"))


def make_ctx(args: argparse.Namespace) -> Ctx:
    cfg = Configuration(host=args.host)
    cfg.api_key["api_key"] = args.apikey
    client = ApiClient(cfg)
    # The generated client header name is X-API-KEY; set it explicitly so we
    # match the backend's case-sensitive filter regardless of SDK quirks.
    client.default_headers["X-API-KEY"] = args.apikey
    return Ctx(
        host=args.host.rstrip("/"),
        v2=v2_base(args.host),
        apikey=args.apikey,
        client=client,
        collection=CollectionApi(client),
        data_object=DataObjectApi(client),
        search=SearchApi(client),
    )


# ---------------------------------------------------------------------------
# Logging


def log(action: str, msg: str, ident: object = "") -> None:
    suffix = f" ({ident})" if ident != "" else ""
    print(f"{action:<7} {msg}{suffix}", flush=True)


def section(title: str) -> None:
    print(f"\n--- {title} ---", flush=True)


# ---------------------------------------------------------------------------
# v2 HTTP helpers (plain urllib — the feature surfaces under test)


def _req(
    ctx: Ctx,
    method: str,
    path: str,
    body: Any = None,
    accept: str = "application/json",
    content_type: str = "application/json",
) -> tuple[int, bytes, dict[str, str]]:
    """Low-level v2 call. ``path`` is relative to the v2 base (leading slash
    optional). Returns ``(status, raw_bytes, headers)``; never raises on a
    4xx/5xx — the caller inspects the status (so a seed can show a 422)."""
    url = ctx.v2 + ("" if path.startswith("/") else "/") + path
    data = None
    headers = {"X-API-KEY": ctx.apikey, "Accept": accept}
    if body is not None:
        data = body if isinstance(body, bytes) else json.dumps(body).encode()
        headers["Content-Type"] = content_type
    request = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=60) as resp:
            return resp.status, resp.read(), dict(resp.headers)
    except urllib.error.HTTPError as e:
        return e.code, e.read(), dict(e.headers or {})


def v2_get(ctx: Ctx, path: str, accept: str = "application/json"):
    status, raw, hdrs = _req(ctx, "GET", path, accept=accept)
    return status, _decode(raw, accept), hdrs


def v2_post(ctx: Ctx, path: str, body: Any = None, accept: str = "application/json"):
    status, raw, hdrs = _req(ctx, "POST", path, body=body, accept=accept)
    return status, _decode(raw, accept), hdrs


def v2_patch(
    ctx: Ctx,
    path: str,
    body: Any,
    accept: str = "application/json",
    content_type: str = "application/merge-patch+json",
):
    status, raw, hdrs = _req(ctx, "PATCH", path, body=body, accept=accept, content_type=content_type)
    return status, _decode(raw, accept), hdrs


def v2_delete(ctx: Ctx, path: str):
    status, raw, hdrs = _req(ctx, "DELETE", path)
    return status, _decode(raw), hdrs


def _decode(raw: bytes, accept: str = "application/json"):
    """JSON-decode when the caller asked for JSON; otherwise return raw bytes."""
    if accept != "application/json":
        return raw
    if not raw:
        return None
    try:
        return json.loads(raw)
    except (ValueError, UnicodeDecodeError):
        return raw.decode("utf-8", "replace")


# ---------------------------------------------------------------------------
# Collection helpers (v1 client for create; appId resolved off the wire)


def _query(name: str) -> str:
    return f'{{"property":"name","value":"{name}","operator":"eq"}}'


def find_collection_by_name(ctx: Ctx, name: str) -> Collection | None:
    body = CollectionSearchBody(searchParams=CollectionSearchParams(query=_query(name)))
    res = ctx.search.search_collections(body)
    for c in res.results or []:
        if c.name == name:
            return c
    return None


def collection_app_id(ctx: Ctx, coll_id: int) -> str:
    """Resolve a Collection's appId from its numeric id via the v1 GET — the
    generated client model doesn't surface appId, but the wire response does."""
    req = urllib.request.Request(
        f"{ctx.host}/collections/{coll_id}",
        headers={"X-API-KEY": ctx.apikey, "Accept": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.loads(r.read())["appId"]


def data_object_app_id(ctx: Ctx, coll_id: int, do_id: int) -> str:
    """Resolve a DataObject's appId off the wire (the typed model omits it)."""
    req = urllib.request.Request(
        f"{ctx.host}/collections/{coll_id}/dataObjects/{do_id}",
        headers={"X-API-KEY": ctx.apikey, "Accept": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.loads(r.read())["appId"]


def reset_collection(ctx: Ctx, name: str) -> None:
    existing = find_collection_by_name(ctx, name)
    if existing is None:
        log("SKIP", f"reset: no prior '{name}'")
        return
    ctx.collection.delete_collection(existing.id)
    log("OK", f"reset: deleted Collection '{name}'", existing.id)


def ensure_collection(ctx: Ctx, name: str, description: str) -> Collection:
    """Idempotent: reuse an existing feat-<slug> Collection by name, else create
    it PUBLIC so the showcase is explorable without per-user role plumbing."""
    existing = find_collection_by_name(ctx, name)
    if existing is not None:
        log("SKIP", f"Collection '{name}'", existing.id)
        return existing
    coll = ctx.collection.create_collection(Collection(name=name, description=description))
    try:
        perms = ctx.collection.get_collection_permissions(coll.id)
        perms.permission_type = PermissionType.PUBLIC
        ctx.collection.edit_collection_permissions(coll.id, perms)
    except Exception as e:  # pragma: no cover - best-effort visibility flip
        log("WARN", f"could not set Collection PUBLIC: {str(e)[:60]}")
    log("OK", f"Collection '{name}'", coll.id)
    return coll


def ensure_data_object(
    ctx: Ctx, coll: Collection, name: str, attributes: dict[str, str] | None = None
) -> DataObject:
    """Idempotent child DataObject by (name, no parent)."""
    for o in ctx.data_object.get_all_data_objects(coll.id) or []:
        if o.name == name and (o.parent_id or None) is None:
            log("SKIP", f"DataObject '{name}'", o.id)
            return o
    do = DataObject(name=name, attributes=attributes or {})
    created = ctx.data_object.create_data_object(coll.id, do)
    log("OK", f"DataObject '{name}'", created.id)
    return created


# ---------------------------------------------------------------------------
# Reporting


def collection_link(app_id: str) -> str:
    """appId-based frontend URL (HARD RULE 1 — never a numeric id)."""
    return f"{FRONTEND_BASE}/collections/{app_id}"


def print_outcome(slug: str, coll_app_id: str, extra: dict[str, str] | None = None) -> None:
    section("OUTCOME")
    log("DONE", f"feat-{slug} collection appId", coll_app_id)
    log("LINK", collection_link(coll_app_id))
    for k, v in (extra or {}).items():
        log("APPID", f"{k}", v)
