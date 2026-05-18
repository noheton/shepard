#!/usr/bin/env python3
"""Upstream-current importer for the LUMEN-inspired showcase dataset.

This script targets the *upstream* shepard at gitlab.com/dlr-shepard/shepard
whose published ``shepard-client`` Python package is the currently-deployed
reality. It is a parallel counterpart to ``seed.py`` (the dispatcher-branch
script that exercises PR #1000 / PR #1001 features).

What it imports — same entity tree as ``seed.py`` minus the post-PR features:

* One Collection: "LUMEN-Inspired Hotfire Test Campaign — Q3 2024 (synthetic)"
* Seven test runs TR-001..TR-007 (top-level DataObjects)
* Per-run TimeseriesContainer, FileContainer, StructuredDataContainer
* Per-run TimeseriesReference, FileReference, StructuredDataReference
* TR-004 child DataObject "Anomaly Investigation" with predecessor link
* Two LabJournalEntry rows (TR-004 debrief, TR-006 fix-confirmation)
* Phase-of-burn semantic annotations on every run's timeseries-reference
* TR-004 vibration-anomaly placeholder annotation
* Three Collection versions: v0 (pre-import marker), v1 (after seven runs),
  v2 (after analysis sub-tree)
* Three permission roles: campaign_lead (Owner/manager), analyst (Writer),
  reviewer (Reader)
* One API key per role (no validUntil — that's L5, dispatcher-branch only)

What this script deliberately does NOT call (PR #1000 / #1001 endpoints):

* No L5 ``validUntil`` field on POST /users/{u}/apikeys
* No P21 @PATCH on any resource (only GET/POST/PUT/DELETE)
* No P14 application/x-ndjson on /timeseriesContainers/{id}/payload
* No R2 body-form POST /collections/{id}/export (only legacy GET)
* No P3 /temp/migrations/state nor MigrationProgress reads
* No A1b /healthz/started split nor A1c /healthz 503-on-PostGIS-down assumption
* No shepard.permissions.cache.* config knobs

Everything here is in the published shepard-client (>= 5.2.0) on GitLab's
PyPI index. See ``README-upstream.md`` for operator instructions.
"""

from __future__ import annotations

import argparse
import json
import sys
import time
from pathlib import Path
from typing import Any
from urllib import error as urllib_error
from urllib import request as urllib_request

# We rely on the published shepard-client only to confirm the package is
# installed (so operators know they're on the upstream-current SDK) and to
# inherit its Configuration object's host + apikey conventions. All actual
# wire calls are issued via stdlib ``urllib`` against upstream-current
# OpenAPI paths from gitlab.com/dlr-shepard/shepard.
try:
    import shepard_client  # type: ignore  # noqa: F401
    from shepard_client import Configuration  # type: ignore
except ImportError:
    sys.stderr.write(
        "shepard-client is not installed. Per docs/getting-started.md run:\n"
        "  pip install shepard-client \\\n"
        "    --index-url https://gitlab.com/api/v4/projects/59082852/packages/pypi/simple\n"
    )
    raise


COLLECTION_NAME = "LUMEN-Inspired Hotfire Test Campaign — Q3 2024 (synthetic)"
COLLECTION_DESCRIPTION = (
    "LUMEN-Inspired Hotfire Test Campaign — Q3 2024 (synthetic). "
    "Imported via upstream-current shepard import script."
)

RUN_IDS = ["TR-001", "TR-002", "TR-003", "TR-004", "TR-005", "TR-006", "TR-007"]

# 2024-Q3 — fixed run-start unix timestamps so re-imports are deterministic.
RUN_START_UNIX_S = {
    "TR-001": 1722470400,  # 2024-08-01 00:00 UTC
    "TR-002": 1723075200,  # 2024-08-08
    "TR-003": 1723680000,  # 2024-08-15
    "TR-004": 1724284800,  # 2024-08-22 — the anomaly run
    "TR-005": 1724889600,  # 2024-08-29
    "TR-006": 1725494400,  # 2024-09-05 — re-fly, anomaly fixed
    "TR-007": 1726099200,  # 2024-09-12
}

PERMISSION_ROLES = {
    "campaign_lead": "manager",  # Owner-equivalent in upstream Permissions
    "analyst": "writer",
    "reviewer": "reader",
}

# Phase-of-burn windows used both for annotations and the timeseries
# reference's [start, end] envelope.
PHASE_NAMES = ("prechill", "ignition", "mainstage", "shutdown")
PHASE_BOUNDS_S = {
    "prechill": (0.0, 5.0),
    "ignition": (5.0, 10.0),
    "mainstage": (10.0, 50.0),
    "shutdown": (50.0, 60.0),
}

# Ontology IRIs are kept stable so the dispatcher-branch seed.py and this
# script annotate with the same subject/predicate/object triples.
DLR_NS = "https://shepard.example/ontology/dlr#"
PHASE_PROP_IRI = f"{DLR_NS}phaseOfBurn"
ANOMALY_PROP_IRI = f"{DLR_NS}vibration-anomaly"
PHASE_VALUE_IRI = {p: f"{DLR_NS}phase/{p}" for p in PHASE_NAMES}
ANOMALY_VALUE_IRI = f"{DLR_NS}anomaly/fuel-pump-vibration-spike"

TIMESERIES_BATCH_ROWS = 1000  # legacy clients chunk at 1k


# ---------------------------------------------------------------------------
# Logging — match the sibling agent's "OK / SKIP / UPDATE name (kind, id)"
# ---------------------------------------------------------------------------


def log(verb: str, name: str, kind: str, ident: Any = "-") -> None:
    print(f"{verb} {name} ({kind}, {ident})", flush=True)


# ---------------------------------------------------------------------------
# Thin HTTP wrapper that reuses the shepard-client Configuration so we
# inherit its host, headers, and SSL handling.
# ---------------------------------------------------------------------------


class UpstreamApi:
    """Tiny HTTP wrapper over the upstream-current REST surface.

    Uses ``shepard_client.Configuration`` for host + apikey, but issues
    raw requests via stdlib so the script does not depend on which
    *exact* operationId names a given shepard-client version generates.
    Every endpoint we hit is GET/POST/PUT/DELETE on a path that is in
    the upstream OpenAPI ``main`` branch.
    """

    def __init__(self, host: str, apikey: str):
        self.config = Configuration(host=host)
        self.config.api_key["X-API-KEY"] = apikey
        self.host = host.rstrip("/")
        self.apikey = apikey

    def _req(
        self,
        method: str,
        path: str,
        *,
        body: Any = None,
        query: dict[str, Any] | None = None,
        accept: str = "application/json",
    ) -> Any:
        url = self.host + path
        if query:
            from urllib.parse import urlencode

            url += "?" + urlencode({k: v for k, v in query.items() if v is not None})
        data: bytes | None = None
        headers = {
            "Accept": accept,
            "X-API-KEY": self.apikey,
        }
        if body is not None:
            data = json.dumps(body).encode("utf-8")
            headers["Content-Type"] = "application/json"
        req = urllib_request.Request(url, data=data, method=method, headers=headers)
        try:
            with urllib_request.urlopen(req, timeout=120) as resp:
                raw = resp.read()
                if not raw:
                    return None
                ctype = resp.headers.get("Content-Type", "")
                if "application/json" in ctype:
                    return json.loads(raw)
                return raw
        except urllib_error.HTTPError as e:
            err_body = e.read().decode("utf-8", "replace")
            raise RuntimeError(
                f"{method} {url} -> {e.code}: {err_body}"
            ) from None

    def get(self, path: str, query: dict[str, Any] | None = None) -> Any:
        return self._req("GET", path, query=query)

    def post(self, path: str, body: Any, query: dict[str, Any] | None = None) -> Any:
        return self._req("POST", path, body=body, query=query)

    def put(self, path: str, body: Any) -> Any:
        return self._req("PUT", path, body=body)

    def delete(self, path: str) -> None:
        try:
            self._req("DELETE", path)
        except RuntimeError as e:
            if " 404:" in str(e):
                return
            raise

    def post_multipart(self, path: str, file_name: str, file_bytes: bytes) -> Any:
        """Plain multipart/form-data POST (for FileContainer payload uploads)."""
        boundary = "----shepardSeedBoundary7XupSt"
        sep = f"--{boundary}".encode()
        end = f"--{boundary}--".encode()
        parts = [
            sep,
            (
                f'Content-Disposition: form-data; name="file"; filename="{file_name}"\r\n'
                f"Content-Type: application/octet-stream\r\n\r\n"
            ).encode(),
            file_bytes,
            b"\r\n",
            end,
            b"\r\n",
        ]
        body = b"\r\n".join(parts[:1]) + b"\r\n" + parts[1] + parts[2] + parts[3] + parts[4] + parts[5]
        url = self.host + path
        headers = {
            "Accept": "application/json",
            "X-API-KEY": self.apikey,
            "Content-Type": f"multipart/form-data; boundary={boundary}",
            "Content-Length": str(len(body)),
        }
        req = urllib_request.Request(url, data=body, method="POST", headers=headers)
        with urllib_request.urlopen(req, timeout=120) as resp:
            raw = resp.read()
            if not raw:
                return None
            return json.loads(raw)


# ---------------------------------------------------------------------------
# Idempotent by-name lookups
# ---------------------------------------------------------------------------


def find_collection_by_name(api: UpstreamApi, name: str) -> dict | None:
    items = api.get("/collections", query={"name": name, "size": 100})
    for it in items or []:
        if it.get("name") == name:
            return it
    return None


def find_dataobject_by_name(api: UpstreamApi, collection_id: int, name: str) -> dict | None:
    items = api.get(
        f"/collections/{collection_id}/dataObjects",
        query={"size": 1000},
    )
    for it in items or []:
        if it.get("name") == name:
            return it
    return None


def find_container_by_name(api: UpstreamApi, kind: str, name: str) -> dict | None:
    """kind in {timeseriesContainers, fileContainers, structuredDataContainers}."""
    items = api.get(f"/{kind}", query={"name": name, "size": 100})
    for it in items or []:
        if it.get("name") == name:
            return it
    return None


def find_reference(api: UpstreamApi, collection_id: int, data_object_id: int, ref_kind: str, name: str) -> dict | None:
    items = api.get(
        f"/collections/{collection_id}/dataObjects/{data_object_id}/{ref_kind}",
        query={"size": 100},
    )
    for it in items or []:
        if it.get("name") == name:
            return it
    return None


# ---------------------------------------------------------------------------
# Data loading — prefer sibling agent's generator, else local fallback.
# ---------------------------------------------------------------------------


def load_data_generator(data_dir: Path):
    """Return a callable (run_id) -> list[dict] for the timeseries CSVs.

    Order of preference:
      1. Sibling agent's ``examples/lumen-showcase/data/generate.py`` if it
         exposes ``read_csv`` / ``generate``. (Single source of truth.)
      2. On-disk CSVs at ``data/timeseries/TR-NNN.csv``. (Sibling already
         materialised them; we just read.)
      3. Our own ``_data_fallback.py`` — generates the CSVs in-place and
         then reads them back. Bit-identical to (1) by spec.
    """
    sibling_module = data_dir / "generate.py"
    csv_dir = data_dir / "timeseries"
    have_sibling_csvs = csv_dir.is_dir() and any(csv_dir.glob("TR-*.csv"))

    if sibling_module.is_file() and have_sibling_csvs:
        # Sibling has shipped both generator and CSVs — use its read_csv.
        sys.path.insert(0, str(data_dir))
        try:
            import generate as sibling_gen  # type: ignore
        finally:
            sys.path.pop(0)
        if hasattr(sibling_gen, "read_csv"):
            return lambda run_id: sibling_gen.read_csv(csv_dir / f"{run_id}.csv")
        # fall through to plain CSV reader

    if have_sibling_csvs:
        from csv import DictReader

        def _read(run_id: str):
            with (csv_dir / f"{run_id}.csv").open() as fh:
                return [{k: float(v) for k, v in row.items()} for row in DictReader(fh)]

        return _read

    # Last resort: regenerate via local fallback (matches sibling spec).
    sys.path.insert(0, str(Path(__file__).parent))
    try:
        import _data_fallback  # type: ignore
    finally:
        sys.path.pop(0)
    log("OK", "regenerating timeseries CSVs (fallback rng=2024)", "data")
    _data_fallback.generate(data_dir)
    return lambda run_id: _data_fallback.read_csv(csv_dir / f"{run_id}.csv")


# ---------------------------------------------------------------------------
# Importers — each one is idempotent.
# ---------------------------------------------------------------------------


def upsert_collection(api: UpstreamApi) -> dict:
    existing = find_collection_by_name(api, COLLECTION_NAME)
    if existing:
        if existing.get("description") != COLLECTION_DESCRIPTION:
            payload = dict(existing)
            payload["description"] = COLLECTION_DESCRIPTION
            updated = api.put(f"/collections/{existing['id']}", payload)
            log("UPDATE", COLLECTION_NAME, "Collection", updated["id"])
            return updated
        log("SKIP", COLLECTION_NAME, "Collection", existing["id"])
        return existing
    created = api.post(
        "/collections",
        {"name": COLLECTION_NAME, "description": COLLECTION_DESCRIPTION, "attributes": {"campaign": "lumen-q3-2024-synthetic"}},
    )
    log("OK", COLLECTION_NAME, "Collection", created["id"])
    return created


def upsert_dataobject(api: UpstreamApi, collection_id: int, name: str, *, parent_id: int | None = None, predecessor_ids: list[int] | None = None, description: str | None = None) -> dict:
    existing = find_dataobject_by_name(api, collection_id, name)
    if existing:
        log("SKIP", name, "DataObject", existing["id"])
        return existing
    body: dict[str, Any] = {"name": name}
    if description:
        body["description"] = description
    if parent_id is not None:
        body["parentId"] = parent_id
    if predecessor_ids:
        body["predecessorIds"] = predecessor_ids
    created = api.post(f"/collections/{collection_id}/dataObjects", body)
    log("OK", name, "DataObject", created["id"])
    return created


def upsert_container(api: UpstreamApi, kind: str, name: str) -> dict:
    existing = find_container_by_name(api, kind, name)
    if existing:
        log("SKIP", name, kind, existing["id"])
        return existing
    created = api.post(f"/{kind}", {"name": name})
    log("OK", name, kind, created["id"])
    return created


def upload_timeseries_payload(api: UpstreamApi, container_id: int, run_id: str, rows: list[dict]) -> tuple[int, int, dict]:
    """Upload one channel's worth of timeseries via the legacy JSON-array
    POST /timeseriesContainers/{id}/payload, batched at 1000 rows.

    We pick ``vib_fuel_pump`` as the channel-of-record so the anomaly is
    visible to the notebook. The remaining four channels would be uploaded
    the same way in production; we keep the upstream import light to stay
    inside the 2-minute runtime budget.

    Returns (start_ns, end_ns, timeseries_descriptor).
    """
    start_s = RUN_START_UNIX_S[run_id]
    descriptor = {
        "measurement": "hotfire",
        "device": f"engine-{run_id.lower()}",
        "location": "P5-teststand",
        "symbolicName": run_id,
        "field": "vib_fuel_pump",
    }
    total = len(rows)
    for offset in range(0, total, TIMESERIES_BATCH_ROWS):
        batch = rows[offset:offset + TIMESERIES_BATCH_ROWS]
        points = [
            {
                "timestamp": int((start_s + r["t"]) * 1_000_000_000),
                "value": r["vib_fuel_pump"],
            }
            for r in batch
        ]
        # NOTE: legacy application/json. Dispatcher-branch seed.py uses the
        # same endpoint via P14 application/x-ndjson; we deliberately do not.
        api.post(
            f"/timeseriesContainers/{container_id}/payload",
            {"timeseries": descriptor, "points": points},
        )
    end_s = start_s + 60
    return start_s * 1_000_000_000, end_s * 1_000_000_000, descriptor


def ensure_timeseries_reference(api: UpstreamApi, collection_id: int, data_object_id: int, container_id: int, name: str, descriptor: dict, start_ns: int, end_ns: int) -> dict:
    existing = find_reference(api, collection_id, data_object_id, "timeseriesReferences", name)
    if existing:
        log("SKIP", name, "TimeseriesReference", existing["id"])
        return existing
    body = {
        "name": name,
        "start": start_ns,
        "end": end_ns,
        "timeseries": [descriptor],
        "timeseriesContainerId": container_id,
    }
    created = api.post(
        f"/collections/{collection_id}/dataObjects/{data_object_id}/timeseriesReferences",
        body,
    )
    log("OK", name, "TimeseriesReference", created["id"])
    return created


def ensure_file_payload(api: UpstreamApi, file_container_id: int, file_name: str, file_bytes: bytes) -> str:
    """Upload one file to a FileContainer; return its oid."""
    existing_files = api.get(f"/fileContainers/{file_container_id}/payload") or []
    for f in existing_files:
        if f.get("filename") == file_name or f.get("name") == file_name:
            log("SKIP", file_name, "ShepardFile", f.get("oid", "-"))
            return f["oid"]
    created = api.post_multipart(f"/fileContainers/{file_container_id}/payload", file_name, file_bytes)
    log("OK", file_name, "ShepardFile", created.get("oid", "-"))
    return created["oid"]


def ensure_file_reference(api: UpstreamApi, collection_id: int, data_object_id: int, file_container_id: int, name: str, oids: list[str]) -> dict:
    existing = find_reference(api, collection_id, data_object_id, "fileReferences", name)
    if existing:
        log("SKIP", name, "FileReference", existing["id"])
        return existing
    body = {
        "name": name,
        "fileContainerId": file_container_id,
        "fileOids": oids,
    }
    created = api.post(
        f"/collections/{collection_id}/dataObjects/{data_object_id}/fileReferences",
        body,
    )
    log("OK", name, "FileReference", created["id"])
    return created


def ensure_structured_payload(api: UpstreamApi, container_id: int, payload: dict) -> str:
    """Push one JSON document to a StructuredDataContainer; return its oid."""
    body = {
        "structuredData": {"name": payload.get("__name__", "doc")},
        "payload": json.dumps(payload),
    }
    created = api.post(f"/structuredDataContainers/{container_id}/payload", body)
    return created.get("oid") or created.get("structuredData", {}).get("oid")


def ensure_structured_reference(api: UpstreamApi, collection_id: int, data_object_id: int, container_id: int, name: str, oids: list[str]) -> dict:
    existing = find_reference(api, collection_id, data_object_id, "structuredDataReferences", name)
    if existing:
        log("SKIP", name, "StructuredDataReference", existing["id"])
        return existing
    body = {
        "name": name,
        "structuredDataContainerId": container_id,
        "structuredDataOids": oids,
    }
    created = api.post(
        f"/collections/{collection_id}/dataObjects/{data_object_id}/structuredDataReferences",
        body,
    )
    log("OK", name, "StructuredDataReference", created["id"])
    return created


def ensure_lab_journal(api: UpstreamApi, data_object_id: int, content: str, marker: str) -> dict:
    """Idempotent by ``marker`` substring in journalContent."""
    existing = api.get("/labJournalEntries", query={"dataObjectId": data_object_id}) or []
    for e in existing:
        if marker in (e.get("journalContent") or ""):
            log("SKIP", marker, "LabJournalEntry", e["id"])
            return e
    created = api.post(
        "/labJournalEntries",
        {"journalContent": content},
        query={"dataObjectId": data_object_id},
    )
    log("OK", marker, "LabJournalEntry", created["id"])
    return created


def ensure_default_semantic_repository(api: UpstreamApi) -> int:
    name = "lumen-showcase-stub-repo"
    existing = api.get("/semanticRepositories") or []
    for r in existing:
        if r.get("name") == name:
            return r["id"]
    created = api.post(
        "/semanticRepositories",
        {
            "name": name,
            "type": "SPARQL",
            "endpoint": DLR_NS,  # placeholder; queried only by the notebook
        },
    )
    log("OK", name, "SemanticRepository", created["id"])
    return created["id"]


def ensure_reference_annotation(api: UpstreamApi, collection_id: int, data_object_id: int, reference_id: int, prop_iri: str, value_iri: str, repo_id: int) -> dict | None:
    """Annotate a reference (timeseries/file/etc) with a (property, value) pair."""
    path = f"/collections/{collection_id}/dataObjects/{data_object_id}/references/{reference_id}/semanticAnnotations"
    existing = api.get(path) or []
    for a in existing:
        if a.get("propertyIRI") == prop_iri and a.get("valueIRI") == value_iri:
            log("SKIP", value_iri, "SemanticAnnotation", a["id"])
            return a
    body = {
        "propertyIRI": prop_iri,
        "valueIRI": value_iri,
        "propertyRepositoryId": repo_id,
        "valueRepositoryId": repo_id,
    }
    created = api.post(path, body)
    log("OK", value_iri, "SemanticAnnotation", created["id"])
    return created


# ---------------------------------------------------------------------------
# Permissions + API keys (upstream-current)
# ---------------------------------------------------------------------------


def edit_collection_permissions(api: UpstreamApi, collection_id: int, role_users: dict[str, str]) -> None:
    """Apply campaign_lead/analyst/reviewer roles via the upstream
    PUT /collections/{id}/permissions.

    role_users: {"campaign_lead": "...", "analyst": "...", "reviewer": "..."}.
    """
    current = api.get(f"/collections/{collection_id}/permissions")
    body = dict(current or {})
    body["entityId"] = collection_id
    body["permissionType"] = current.get("permissionType") if current else "Private"
    body["manager"] = sorted(set((body.get("manager") or []) + [role_users["campaign_lead"]]))
    body["writer"] = sorted(set((body.get("writer") or []) + [role_users["analyst"]]))
    body["reader"] = sorted(set((body.get("reader") or []) + [role_users["reviewer"]]))
    body["owner"] = role_users["campaign_lead"]
    api.put(f"/collections/{collection_id}/permissions", body)
    log("OK", "campaign_lead/analyst/reviewer", "Permissions", collection_id)


def ensure_apikey(api: UpstreamApi, username: str, key_name: str) -> dict:
    """Create a named API key for a user. NO validUntil: that field is the
    L5 dispatcher-branch addition (PR #1000) and is not in upstream today.
    On the dispatcher-branch seed.py the reviewer key gets validUntil=now+90d.
    """
    existing = api.get(f"/users/{username}/apikeys") or []
    for k in existing:
        if k.get("name") == key_name:
            log("SKIP", key_name, "ApiKey", k["uid"])
            return k
    created = api.post(f"/users/{username}/apikeys", {"name": key_name})
    log("OK", key_name, "ApiKey", created.get("uid", "-"))
    return created


# ---------------------------------------------------------------------------
# Versions (upstream-current — feature-flag-gated; we tolerate 404)
# ---------------------------------------------------------------------------


def ensure_collection_version(api: UpstreamApi, collection_id: int, version_name: str, description: str) -> dict | None:
    path = f"/collections/{collection_id}/versions"
    try:
        existing = api.get(path) or []
    except RuntimeError as e:
        if " 404:" in str(e):
            log("SKIP", version_name, "Version (versioning toggle off)", "-")
            return None
        raise
    for v in existing:
        if v.get("name") == version_name:
            log("SKIP", version_name, "Version", v.get("uid", "-"))
            return v
    created = api.post(path, {"name": version_name, "description": description})
    log("OK", version_name, "Version", created.get("uid", "-"))
    return created


# ---------------------------------------------------------------------------
# Reset
# ---------------------------------------------------------------------------


def reset(api: UpstreamApi) -> None:
    existing = find_collection_by_name(api, COLLECTION_NAME)
    if not existing:
        log("SKIP", COLLECTION_NAME, "Collection (nothing to reset)", "-")
        return
    api.delete(f"/collections/{existing['id']}")
    log("OK", COLLECTION_NAME, "Collection (deleted)", existing["id"])
    # Containers are not auto-deleted by collection deletion; clean them by name.
    for kind in ("timeseriesContainers", "fileContainers", "structuredDataContainers"):
        for run_id in RUN_IDS:
            cname = f"lumen-{run_id}-{kind[:-1]}"
            c = find_container_by_name(api, kind, cname)
            if c:
                api.delete(f"/{kind}/{c['id']}")
                log("OK", cname, f"{kind} (deleted)", c["id"])


# ---------------------------------------------------------------------------
# Driver
# ---------------------------------------------------------------------------


def run(host: str, apikey: str, data_dir: Path, do_reset: bool) -> None:
    api = UpstreamApi(host, apikey)

    if do_reset:
        reset(api)

    read_run = load_data_generator(data_dir)

    # Version 0: pre-import marker (no entities yet — but we record the
    # intent so the notebook can show "what existed before"). Created
    # against the Collection AFTER it exists; we still call it v0.
    coll = upsert_collection(api)
    coll_id = coll["id"]

    repo_id = ensure_default_semantic_repository(api)

    ensure_collection_version(api, coll_id, "v0", "Pre-import marker — Collection just created.")

    # Per-run import.
    run_data_objects: dict[str, int] = {}
    run_ts_refs: dict[str, int] = {}
    for run_id in RUN_IDS:
        do = upsert_dataobject(
            api,
            coll_id,
            run_id,
            description=f"Hotfire test run {run_id} (synthetic).",
        )
        run_data_objects[run_id] = do["id"]

        ts_container = upsert_container(api, "timeseriesContainers", f"lumen-{run_id}-timeseriesContainer")
        file_container = upsert_container(api, "fileContainers", f"lumen-{run_id}-fileContainer")
        sd_container = upsert_container(api, "structuredDataContainers", f"lumen-{run_id}-structuredDataContainer")

        rows = read_run(run_id)
        start_ns, end_ns, descriptor = upload_timeseries_payload(api, ts_container["id"], run_id, rows)

        ts_ref = ensure_timeseries_reference(
            api,
            coll_id,
            do["id"],
            ts_container["id"],
            f"{run_id}-vib-fuel-pump",
            descriptor,
            start_ns,
            end_ns,
        )
        run_ts_refs[run_id] = ts_ref["id"]

        # One-line structured-data sidecar per run: the run header.
        sd_oid = ensure_structured_payload(
            api,
            sd_container["id"],
            {
                "__name__": f"{run_id}-header",
                "run_id": run_id,
                "start_unix_s": RUN_START_UNIX_S[run_id],
                "duration_s": 60.0,
            },
        )
        ensure_structured_reference(
            api,
            coll_id,
            do["id"],
            sd_container["id"],
            f"{run_id}-header",
            [sd_oid],
        )

        # One small "report" file per run.
        report = (
            f"# {run_id} report\n\n"
            f"start_unix_s: {RUN_START_UNIX_S[run_id]}\n"
            f"duration_s: 60\n"
            f"channel: vib_fuel_pump (g)\n"
        ).encode()
        oid = ensure_file_payload(api, file_container["id"], f"{run_id}-report.md", report)
        ensure_file_reference(api, coll_id, do["id"], file_container["id"], f"{run_id}-report", [oid])

        # Phase-of-burn annotations on the timeseries reference.
        for phase in PHASE_NAMES:
            ensure_reference_annotation(
                api,
                coll_id,
                do["id"],
                ts_ref["id"],
                PHASE_PROP_IRI,
                PHASE_VALUE_IRI[phase],
                repo_id,
            )

        # TR-004 carries the vibration anomaly placeholder annotation.
        if run_id == "TR-004":
            ensure_reference_annotation(
                api,
                coll_id,
                do["id"],
                ts_ref["id"],
                ANOMALY_PROP_IRI,
                ANOMALY_VALUE_IRI,
                repo_id,
            )

    ensure_collection_version(
        api,
        coll_id,
        "v1",
        "Seven test runs imported; per-run containers + references + phase-of-burn annotations.",
    )

    # Analysis sub-tree: TR-004 anomaly investigation child + lab-journal entries.
    investigation = upsert_dataobject(
        api,
        coll_id,
        "TR-004 Anomaly Investigation",
        parent_id=run_data_objects["TR-004"],
        predecessor_ids=[run_data_objects["TR-004"]],
        description="Root-cause analysis for the TR-004 fuel-pump vibration spike.",
    )

    ensure_lab_journal(
        api,
        run_data_objects["TR-004"],
        (
            "TR-004 debrief: at t≈32.5s a +6g vibration spike was observed on "
            "the fuel-pump RMS channel. See child investigation DataObject. "
            "[marker:tr-004-debrief]"
        ),
        "marker:tr-004-debrief",
    )
    ensure_lab_journal(
        api,
        run_data_objects["TR-006"],
        (
            "TR-006 fix-confirmation: the modified fuel-pump mount eliminated "
            "the t≈32.5s anomaly seen on TR-004. No spike observed; campaign "
            "may continue to TR-007. [marker:tr-006-fix]"
        ),
        "marker:tr-006-fix",
    )

    ensure_collection_version(
        api,
        coll_id,
        "v2",
        "Analysis sub-tree populated (Anomaly Investigation child + lab-journal debriefs).",
    )

    # Permissions + API keys.
    role_users = {
        "campaign_lead": "campaign_lead",
        "analyst": "analyst",
        "reviewer": "reviewer",
    }
    edit_collection_permissions(api, coll_id, role_users)

    for role, username in role_users.items():
        # Single-line note per the brief: on the dispatcher-branch seed.py
        # the reviewer key gets validUntil=now+90d via L5 (PR #1000).
        ensure_apikey(api, username, f"lumen-showcase-{role}")


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    p.add_argument("--host", required=True, help="e.g. https://shepard.example/shepard/api")
    p.add_argument("--apikey", required=True, help="Caller's API key (manager on the target instance)")
    p.add_argument("--reset", action="store_true", help="Delete the showcase Collection + containers and re-seed")
    p.add_argument(
        "--data-dir",
        type=Path,
        default=Path(__file__).parent / "data",
        help="Where to find (or generate) timeseries CSVs",
    )
    return p.parse_args()


def main() -> int:
    args = parse_args()
    t0 = time.time()
    try:
        run(args.host, args.apikey, args.data_dir, args.reset)
    except RuntimeError as e:
        sys.stderr.write(f"ERROR: {e}\n")
        return 1
    print(f"DONE in {time.time() - t0:.1f}s", flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
