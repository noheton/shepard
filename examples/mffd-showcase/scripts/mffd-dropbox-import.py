# /// script
# requires-python = ">=3.11"
# dependencies = ["requests", "tqdm"]
# ///
#!/usr/bin/env python3
"""mffd-dropbox-import.py v14 — MFFD manufacturing process data ingest with provenance.

v14: full three-payload migration — files + timeseries (WIDE CSV export→import) +
     structured data (JSON download→re-upload). Shared TS container per step with
     idempotent state tracking. All prior retry/resume/lock/gate logic preserved.


Agentic Data Management workflow
──────────────────────────────────
This script implements a human+Claude-in-the-loop import:
  1. Bootstrap (run once on nuclide.systems from this repo):
       creates the destination collection + skeleton DataObjects + uploads this
       script as a provenance artifact + initial snapshot capturing t=0 state.
  2. Shell fetch (on DLR machine):
       run-mffd-import.sh downloads this script from the ImportScripts DataObject
       in the collection, then runs it.
  3. Warmup probe:
       uploads one payload per reference type; Claude reviews with the operator
       in chat; Claude then calls PATCH /collections/{id} to set import_ready.
  4. Full import:
       traverses source collections on DLR intranet (48297 tapelaying,
       163811 bridge/frame welding), downloads every DataObject + file, re-uploads
       to the destination collection. All progress is checkpointed.
  5. Post-import snapshot:
       snapshot label includes current collection name so future renames are tracked.

⚠  TIMESTAMP NOTE
──────────────────
File upload timestamps and DataObject creationDate reflect the IMPORT time,
not the original measurement time. Source timestamps are preserved as
source_created / source_modified attributes on each migrated DataObject.

Three modes
───────────
  --bootstrap    Create destination collection + self-upload + t=0 snapshot.
                 Run this ONCE on nuclide.systems before the DLR import.

  SOURCE MODE    Traverse source Shepard collections (set SOURCE_* vars), pull
  (default)      all DataObjects + files, re-upload to destination collection.
                 Use SOURCE_SHEPARD_URL / SOURCE_SHEPARD_API_KEY for cross-instance.

  LOCAL MODE     Read files from DATA_DIR subdirs. Fallback when no SOURCE vars set.

Usage
─────
  # Step 1 — bootstrap (run once on nuclide.systems):
  SHEPARD_URL=https://shepard.nuclide.systems \\
  SHEPARD_API_KEY=<nuclide-key> \\
  uv run python mffd-dropbox-import.py --bootstrap

  # Step 2 — full cross-instance import (run on DLR network):
  SHEPARD_URL=https://shepard.nuclide.systems \\
  SHEPARD_API_KEY=<nuclide-key> \\
  SOURCE_SHEPARD_URL=https://backend.bt-au-cube3.intra.dlr.de \\
  SOURCE_SHEPARD_API_KEY=eyJhbGciOiJSUzI1NiJ9... \\
  SOURCE_TAPELAYING_COLL_ID=48297 \\
  SOURCE_BRIDGEWELDING_COLL_ID=163811 \\
  SESSION_ID=2026-05-22-Q1 \\
  uv run python mffd-dropbox-import.py

  # Or: fetch via shell helper (after bootstrap):
  bash run-mffd-import.sh

  # Dry-run:
  uv run python mffd-dropbox-import.py --dry-run

Environment variables
─────────────────────
  SHEPARD_URL                   Destination instance.  Default: https://shepard.nuclide.systems
  SHEPARD_API_KEY               Destination auth — X-API-KEY (Shepard JWTs go here)
  SHEPARD_BEARER_TOKEN          Destination auth — Authorization: Bearer (Keycloak)
  SOURCE_SHEPARD_URL            Source instance for cross-instance pull.  Default: SHEPARD_URL
  SOURCE_SHEPARD_API_KEY        Source auth (JWT from DLR intranet instance)
  SOURCE_TAPELAYING_COLL_ID     Source collection ID for tape-laying  (48297 on DLR)
  SOURCE_BRIDGEWELDING_COLL_ID  Source collection ID for bridge welding (163811 on DLR)
  SESSION_ID                    Run identifier.  Default: today YYYY-MM-DD
  DATA_DIR                      Root dir for LOCAL MODE.  Default: .
  COLLECTION_NAME               Destination collection name.  Default: MFFD-Dropbox
  LOG_DIR                       Log + state file directory.  Default: script directory
  PAGE_SIZE                     DataObjects per page when traversing.  Default: 50
  OPERATOR                      Your name/email for provenance attrs.  Default: (empty)
"""

from __future__ import annotations

import argparse
import datetime
import os
import sys
import tempfile
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Iterator

try:
    import requests
    from requests import Session, Response
    from tqdm import tqdm
except ImportError:
    print("ERROR: uv run python mffd-dropbox-import.py  (or: pip install requests tqdm)", file=sys.stderr)
    sys.exit(1)

# ── Configuration ─────────────────────────────────────────────────────────────

SHEPARD_URL = os.environ.get("SHEPARD_URL", "https://shepard.nuclide.systems").rstrip("/")
SHEPARD_API_KEY = os.environ.get("SHEPARD_API_KEY", "")
SHEPARD_BEARER_TOKEN = os.environ.get("SHEPARD_BEARER_TOKEN", "")
SESSION_ID = os.environ.get("SESSION_ID", datetime.date.today().isoformat())
MAX_DOS_PER_STEP = 0  # Set from --max-dos in main(); 0 = unlimited
DATA_DIR = Path(os.environ.get("DATA_DIR", "."))
COLLECTION_NAME = os.environ.get("COLLECTION_NAME", "MFFD-Dropbox")
LOG_DIR = Path(os.environ.get("LOG_DIR", Path(__file__).parent))
PAGE_SIZE = int(os.environ.get("PAGE_SIZE", "50"))

# Source collection IDs — set these for SOURCE MODE
SOURCE_TAPELAYING_COLL_ID: int | None = (
    int(os.environ["SOURCE_TAPELAYING_COLL_ID"])
    if "SOURCE_TAPELAYING_COLL_ID" in os.environ
    else None
)
SOURCE_BRIDGEWELDING_COLL_ID: int | None = (
    int(os.environ["SOURCE_BRIDGEWELDING_COLL_ID"])
    if "SOURCE_BRIDGEWELDING_COLL_ID" in os.environ
    else None
)

IMPORT_TIME = datetime.datetime.now(datetime.timezone.utc).isoformat()

# Cross-instance source (set SOURCE_* vars for DLR intranet pull)
SOURCE_SHEPARD_URL = os.environ.get("SOURCE_SHEPARD_URL", SHEPARD_URL)
SOURCE_SHEPARD_API_KEY = os.environ.get("SOURCE_SHEPARD_API_KEY", SHEPARD_API_KEY)
OPERATOR = os.environ.get("OPERATOR", "")


# ── Tee logging ───────────────────────────────────────────────────────────────

class Tee:
    def __init__(self, log_path: Path) -> None:
        self._log = open(log_path, "w", buffering=1, encoding="utf-8")
        self._stdout = sys.stdout

    def write(self, msg: str) -> None:
        self._stdout.write(msg)
        self._log.write(msg)

    def flush(self) -> None:
        self._stdout.flush()
        self._log.flush()

    def close(self) -> None:
        self._log.close()


# ── Data model ────────────────────────────────────────────────────────────────

@dataclass
class FileRef:
    fref_id: int
    name: str
    size: int = 0


@dataclass
class TsRef:
    ref_id: int
    name: str
    container_id: int


@dataclass
class StructuredRef:
    ref_id: int
    name: str
    container_id: int


@dataclass
class SourceDO:
    """A DataObject discovered in a source collection."""
    do_id: int
    name: str
    description: str
    attributes: dict[str, str]
    file_refs: list[FileRef] = field(default_factory=list)
    ts_refs: list[TsRef] = field(default_factory=list)
    structured_refs: list[StructuredRef] = field(default_factory=list)
    created: str = ""
    modified: str = ""


# ── HTTP client ───────────────────────────────────────────────────────────────

class ShepardClient:
    """Minimal Shepard v1/v2 client. Credentials never echoed in output."""

    def __init__(self, base: str, api_key: str, bearer_token: str) -> None:
        self._base = base
        self._s = Session()
        self._s.headers.update({"Accept": "application/json"})
        if bearer_token:
            self._s.headers["Authorization"] = f"Bearer {bearer_token}"
        elif api_key:
            self._s.headers["X-API-KEY"] = api_key

    # ── Warmup ────────────────────────────────────────────────────────────────

    def warmup(self) -> bool:
        print("\n=== Warmup ===")
        # Prefer /v2/users/me (fork's clean identity endpoint).
        # Upstream's /shepard/api/users/currentUser doesn't exist on this fork's
        # post-v1-compat builds — the v1 surface treats `currentUser` as a literal
        # username and 404s. /v2/users/me returns the same shape.
        user_r = self._get(f"{self._base}/v2/users/me")
        if user_r is None:
            # Fall back to upstream v1 in case we're running against vanilla shepard.
            user_r = self._get(f"{self._base}/shepard/api/users/currentUser")
        if user_r is None:
            print("  [FAIL] Cannot reach /v2/users/me or /shepard/api/users/currentUser — check SHEPARD_URL + auth")
            return False
        user = user_r.json()
        username = user.get("username") or user.get("name") or user.get("sub") or "(unknown)"
        email = user.get("email") or ""
        print(f"  user     : {username}  {email}")

        coll_r = self._get(f"{self._base}/shepard/api/collections", {"page": "0", "size": "1"})
        if coll_r is not None:
            total = coll_r.headers.get("X-Total-Count", "?")
            print(f"  instance : {self._base}  (collections visible: {total})")

        v2_r = self._get_raw(f"{self._base}/v2/admin/features")
        if v2_r and v2_r.ok:
            print(f"  v2 API   : available")
        else:
            print(f"  v2 API   : not available — file upload via v1 fallback")

        print("=== Warmup OK ===\n")
        return True

    # ── Source collection traversal ───────────────────────────────────────────

    def iter_data_objects(self, coll_id: int) -> Iterator[SourceDO]:
        """Paginate through ALL DataObjects in a collection and yield them."""
        page = 0
        while True:
            r = self._get(
                f"{self._base}/shepard/api/collections/{coll_id}/dataObjects",
                {"page": str(page), "size": str(PAGE_SIZE)},
            )
            if r is None:
                break
            items = r.json()
            if not items:
                break
            for item in items:
                do_id = item["id"]
                file_refs = self._fetch_file_refs(coll_id, do_id)
                ts_refs = self._fetch_ts_refs(coll_id, do_id)
                structured_refs = self._fetch_structured_refs(coll_id, do_id)
                yield SourceDO(
                    do_id=do_id,
                    name=item.get("name", f"DO-{do_id}"),
                    description=item.get("description") or "",
                    attributes={k: str(v) for k, v in (item.get("attributes") or {}).items()},
                    file_refs=file_refs,
                    ts_refs=ts_refs,
                    structured_refs=structured_refs,
                    created=item.get("creationDate") or item.get("createdAt") or "",
                    modified=item.get("modificationDate") or item.get("updatedAt") or "",
                )
            total_pages = int(r.headers.get("X-Total-Pages", page + 1))
            page += 1
            if page >= total_pages:
                break

    def _fetch_file_refs(self, coll_id: int, do_id: int) -> list[FileRef]:
        r = self._get(
            f"{self._base}/shepard/api/collections/{coll_id}/dataObjects/{do_id}/fileReferences"
        )
        if r is None:
            return []
        refs = []
        for item in r.json():
            refs.append(FileRef(
                fref_id=item["id"],
                name=item.get("name") or item.get("fileName") or f"file-{item['id']}",
                size=item.get("size") or item.get("fileSize") or 0,
            ))
        return refs

    def _fetch_ts_refs(self, coll_id: int, do_id: int) -> list[TsRef]:
        r = self._get(
            f"{self._base}/shepard/api/collections/{coll_id}/dataObjects/{do_id}/timeseriesReferences"
        )
        if r is None:
            return []
        refs = []
        for item in r.json():
            refs.append(TsRef(
                ref_id=item["id"],
                name=item.get("name") or f"ts-{item['id']}",
                container_id=item.get("timeseriesContainerId") or 0,
            ))
        return refs

    def _fetch_structured_refs(self, coll_id: int, do_id: int) -> list[StructuredRef]:
        r = self._get(
            f"{self._base}/shepard/api/collections/{coll_id}/dataObjects/{do_id}/structuredDataReferences"
        )
        if r is None:
            return []
        refs = []
        for item in r.json():
            refs.append(StructuredRef(
                ref_id=item["id"],
                name=item.get("name") or f"structured-{item['id']}",
                container_id=item.get("structuredDataContainerId") or 0,
            ))
        return refs

    def export_ts(self, coll_id: int, do_id: int, ref_id: int) -> bytes | None:
        """Export a timeseries reference as WIDE CSV from the source instance."""
        url = (
            f"{self._base}/shepard/api/collections/{coll_id}"
            f"/dataObjects/{do_id}/timeseriesReferences/{ref_id}/export"
        )
        r = self._request_with_retry("GET", url, params={"csv_format": "WIDE"}, timeout=300)
        if r is None or not r.ok:
            if r:
                self._log_err("GET (ts-export)", url, r)
            return None
        return r.content

    def download_structured(self, coll_id: int, do_id: int, ref_id: int) -> dict | list | None:
        """Download structured data payload (JSON) from the source instance."""
        url = (
            f"{self._base}/shepard/api/collections/{coll_id}"
            f"/dataObjects/{do_id}/structuredDataReferences/{ref_id}/payload"
        )
        r = self._get(url)
        if r is None:
            return None
        try:
            return r.json()
        except Exception:
            return None

    def count_data_objects(self, coll_id: int) -> int:
        r = self._get(
            f"{self._base}/shepard/api/collections/{coll_id}/dataObjects",
            {"page": "0", "size": "1"},
        )
        if r is None:
            return 0
        return int(r.headers.get("X-Total-Count", 0))

    def download_file_ref(
        self,
        coll_id: int,
        do_id: int,
        fref_id: int,
        dest: Path,
        size_hint: int = 0,
    ) -> bool:
        url = (
            f"{self._base}/shepard/api/collections/{coll_id}"
            f"/dataObjects/{do_id}/fileReferences/{fref_id}/payload"
        )
        try:
            r = self._s.get(url, stream=True, timeout=600)
            if not r.ok:
                self._log_err("GET payload", url, r)
                return False
            total = int(r.headers.get("Content-Length", size_hint or 0))
            with dest.open("wb") as fh:
                with tqdm(
                    total=total or None,
                    unit="B",
                    unit_scale=True,
                    unit_divisor=1024,
                    desc=f"  ↓ {dest.name[:40]}",
                    leave=False,
                    file=sys.stderr,
                ) as bar:
                    for chunk in r.iter_content(chunk_size=65536):
                        fh.write(chunk)
                        bar.update(len(chunk))
            return True
        except Exception as exc:
            print(f"  [error] download fref {fref_id}: {exc}")
            return False

    # ── Dest collection ───────────────────────────────────────────────────────

    def find_collection(self, name: str) -> dict | None:
        r = self._get(f"{self._base}/shepard/api/collections", {"name": name})
        if r is None:
            return None
        for c in r.json():
            if c.get("name") == name:
                return c
        return None

    def create_collection(self, name: str, description: str, attrs: dict) -> dict | None:
        body: dict[str, Any] = {"name": name, "description": description}
        if attrs:
            body["attributes"] = attrs
        r = self._post(f"{self._base}/shepard/api/collections", body)
        return r.json() if r else None

    def get_collection_app_id(self, collection_id: int) -> str | None:
        r = self._get(f"{self._base}/shepard/api/collections/{collection_id}")
        if r is None:
            return None
        return r.json().get("appId")

    def get_collection_name(self, collection_id: int) -> str:
        r = self._get(f"{self._base}/shepard/api/collections/{collection_id}")
        if r is None:
            return COLLECTION_NAME
        return r.json().get("name") or COLLECTION_NAME

    def set_collection_public(self, coll_id: int) -> bool:
        """Set permissionType=Public so all instance users can read+write."""
        url = f"{self._base}/shepard/api/collections/{coll_id}/permissions"
        r = self._get(url)
        if r is None:
            return False
        perms = r.json()
        perms["permissionType"] = "Public"
        try:
            r2 = self._s.put(url, json=perms, timeout=30)
            if r2.ok:
                print(f"  [perms] collection {coll_id} → Public (all instance users can access)")
                return True
            self._log_err("PUT", url, r2)
        except Exception as exc:
            print(f"  [net] PUT permissions {coll_id}: {exc}")
        return False

    def find_data_object(self, coll_id: int, name: str) -> dict | None:
        r = self._get(
            f"{self._base}/shepard/api/collections/{coll_id}/dataObjects",
            {"name": name},
        )
        if r is None:
            return None
        for d in r.json():
            if d.get("name") == name:
                return d
        return None

    def create_data_object(
        self,
        coll_id: int,
        name: str,
        description: str = "",
        attrs: dict | None = None,
        predecessor_id: int | None = None,
    ) -> dict | None:
        body: dict[str, Any] = {"name": name}
        if description:
            body["description"] = description
        if attrs:
            body["attributes"] = attrs
        r = self._post(f"{self._base}/shepard/api/collections/{coll_id}/dataObjects", body)
        if r is None:
            return None
        do = r.json()
        if predecessor_id is not None:
            self._link_predecessor(coll_id, do["id"], predecessor_id)
        return do

    def _link_predecessor(self, coll_id: int, do_id: int, pred_id: int) -> None:
        url = (
            f"{self._base}/shepard/api/collections/{coll_id}"
            f"/dataObjects/{do_id}/predecessors/{pred_id}"
        )
        r = self._put(url, {})
        if r is not None:
            print(f"  [prov] predecessor {pred_id} → {do_id}")
        else:
            print(f"  [warn] predecessor link FAILED: {pred_id} → {do_id}")

    def verify_references(self, coll_id: int, do_id: int, do_name: str) -> None:
        base = f"{self._base}/shepard/api/collections/{coll_id}/dataObjects/{do_id}"
        kinds = [
            ("fileReferences",           "files"),
            ("structuredDataReferences", "structured"),
            ("timeseriesReferences",     "timeseries"),
        ]
        print(f"  [refs] {do_name!r}:")
        for endpoint, label in kinds:
            r = self._get(f"{base}/{endpoint}")
            if r is None:
                print(f"    {label:12s}: (error)")
            else:
                items = r.json() if isinstance(r.json(), list) else []
                if items:
                    names = [i.get("name") or i.get("id") or "?" for i in items[:4]]
                    more = f" +{len(items)-4}" if len(items) > 4 else ""
                    print(f"    {label:12s}: {len(items):3d}  [{', '.join(str(n) for n in names)}{more}]")
                else:
                    print(f"    {label:12s}: (none)")

    def list_file_refs(self, coll_id: int, do_id: int) -> set[str]:
        r = self._get(
            f"{self._base}/shepard/api/collections/{coll_id}"
            f"/dataObjects/{do_id}/fileReferences"
        )
        if r is None:
            return set()
        return {ref.get("name", "") for ref in r.json()}

    def upload_file(
        self,
        app_id: str,
        path: Path,
        display: str,
        coll_id: int | None = None,
        do_id: int | None = None,
    ) -> bool:
        size = path.stat().st_size

        # v2 singleton (fork instances)
        url_v2 = f"{self._base}/v2/files"
        params = {"parentDataObjectAppId": app_id, "name": display}
        try:
            with path.open("rb") as fh:
                with tqdm(
                    total=size,
                    unit="B",
                    unit_scale=True,
                    unit_divisor=1024,
                    desc=f"  ↑ {display[:40]}",
                    leave=False,
                    file=sys.stderr,
                ) as bar:
                    wrapped = _TqdmReader(fh, bar)
                    r = self._s.post(url_v2, params=params, files={"file": (Path(display).name, wrapped)}, timeout=600)
            if r.status_code not in (404, 405):
                if r.ok:
                    return True
                print(f"  [http {r.status_code}] v2 upload {display}: {r.text[:300]}")
                return False
        except Exception as exc:
            print(f"  [net] v2 upload {path.name}: {exc}")

        # v1 fallback
        if coll_id is not None and do_id is not None:
            url_v1 = (
                f"{self._base}/shepard/api/collections/{coll_id}"
                f"/dataObjects/{do_id}/fileReferences"
            )
            try:
                with path.open("rb") as fh:
                    with tqdm(
                        total=size,
                        unit="B",
                        unit_scale=True,
                        unit_divisor=1024,
                        desc=f"  ↑ {display[:40]} (v1)",
                        leave=False,
                        file=sys.stderr,
                    ) as bar:
                        wrapped = _TqdmReader(fh, bar)
                        r = self._s.post(url_v1, files={"file": (display, wrapped)}, timeout=600)
                if r.ok:
                    return True
                print(f"  [http {r.status_code}] v1 upload {display}: {r.text[:300]}")
            except Exception as exc:
                print(f"  [net] v1 upload {path.name}: {exc}")

        return False

    # ── Dest: timeseries container + import ──────────────────────────────────────

    def create_ts_container(self, name: str) -> int | None:
        """Create a new timeseries container on the dest instance. Returns OGM id."""
        r = self._post(
            f"{self._base}/shepard/api/timeseriesContainers",
            {"name": name, "type": "TIMESERIES"},
        )
        return r.json().get("id") if r else None

    def link_ts_to_do(self, coll_id: int, do_id: int, container_id: int, name: str) -> int | None:
        """Create a timeseriesReference from a DO to an existing container. Returns ref id."""
        url = (
            f"{self._base}/shepard/api/collections/{coll_id}"
            f"/dataObjects/{do_id}/timeseriesReferences"
        )
        r = self._post(url, {"name": name, "timeseriesContainerId": container_id})
        return r.json().get("id") if r else None

    def import_ts_csv(self, container_id: int, csv_bytes: bytes, filename: str = "export.csv") -> bool:
        """Import a CSV blob into a timeseries container on the dest instance."""
        import io
        url = f"{self._base}/shepard/api/timeseriesContainers/{container_id}/import"
        try:
            r = self._request_with_retry(
                "POST", url,
                files={"file": (filename, io.BytesIO(csv_bytes), "text/csv")},
                timeout=600,
            )
            if r is None:
                return False
            if r.ok:
                return True
            self._log_err("POST (ts-import)", url, r)
            return False
        except Exception as exc:
            print(f"  [net] import_ts_csv: {exc}")
            return False

    # ── Dest: structured data container ──────────────────────────────────────────

    def create_structured_container(self, name: str) -> int | None:
        """Create a structured data container on the dest instance. Returns OGM id."""
        r = self._post(f"{self._base}/shepard/api/structuredDataContainers", {"name": name})
        return r.json().get("id") if r else None

    def link_structured_to_do(
        self, coll_id: int, do_id: int, container_id: int, name: str
    ) -> bool:
        """Create a structuredDataReference from a DO to an existing container."""
        url = (
            f"{self._base}/shepard/api/collections/{coll_id}"
            f"/dataObjects/{do_id}/structuredDataReferences"
        )
        r = self._post(url, {"name": name, "structuredDataContainerId": container_id})
        return r is not None

    def upload_structured_payload(self, container_id: int, payload: dict | list) -> bool:
        """Upload JSON payload to a structured data container on the dest instance."""
        url = f"{self._base}/shepard/api/structuredDataContainers/{container_id}/payload"
        r = self._request_with_retry("PUT", url, json=payload, timeout=60)
        if r is None:
            return False
        if r.ok:
            return True
        self._log_err("PUT (structured)", url, r)
        return False

    def upload_self(self, coll_id: int, do_id: int, do_app_id: str) -> None:
        script_path = Path(__file__)
        existing = self.list_file_refs(coll_id, do_id)
        if script_path.name in existing:
            print(f"  [skip] {script_path.name} already in ImportScripts")
            return
        print(f"  [upload] {script_path.name}  ({_human(script_path.stat().st_size)})")
        ok = self.upload_file(do_app_id, script_path, script_path.name, coll_id, do_id)
        if not ok:
            print("  [warn] self-upload failed (non-fatal)")

    # ── Warmup probe payloads ─────────────────────────────────────────────────

    def probe_file(self, coll_id: int, do_id: int, app_id: str) -> bool:
        """Upload a tiny sentinel text file as file-reference probe."""
        import io
        probe_name = "warmup-probe.txt"
        content = (
            f"Shepard warmup probe\n"
            f"session:     {SESSION_ID}\n"
            f"import_time: {IMPORT_TIME}\n"
            f"host:        {self._base}\n"
        ).encode()
        url_v2 = f"{self._base}/v2/files"
        try:
            r = self._s.post(
                url_v2,
                params={"parentDataObjectAppId": app_id, "name": probe_name},
                files={"file": (probe_name, io.BytesIO(content))},
                timeout=30,
            )
            if r.status_code not in (404, 405) and r.ok:
                print(f"    ✓ file reference  — {probe_name} (v2)")
                return True
        except Exception:
            pass
        url_v1 = f"{self._base}/shepard/api/collections/{coll_id}/dataObjects/{do_id}/fileReferences"
        try:
            r = self._s.post(url_v1, files={"file": (probe_name, io.BytesIO(content))}, timeout=30)
            if r.ok:
                print(f"    ✓ file reference  — {probe_name} (v1)")
                return True
            print(f"    ✗ file reference  — http {r.status_code}: {r.text[:200]}")
        except Exception as exc:
            print(f"    ✗ file reference  — {exc}")
        return False

    def probe_structured(self, coll_id: int, do_id: int) -> bool:
        """Post a minimal structured-data reference probe (fails gracefully)."""
        url = (
            f"{self._base}/shepard/api/collections/{coll_id}"
            f"/dataObjects/{do_id}/structuredDataReferences"
        )
        body = {
            "name": "warmup-probe-structured",
            "data": {"session": SESSION_ID, "import_time": IMPORT_TIME, "probe": "warmup"},
        }
        try:
            r = self._s.post(url, json=body, timeout=30)
            if r.ok:
                print(f"    ✓ structured data — warmup-probe-structured")
                return True
            # 4xx = endpoint exists, body shape mismatch — still informative
            print(f"    ~ structured data — http {r.status_code} (reachable, body rejected: {r.text[:120]})")
        except Exception as exc:
            print(f"    ✗ structured data — {exc}")
        return False

    def probe_timeseries(self, coll_id: int, do_id: int) -> bool:
        """Try to create a timeseries reference probe (fails gracefully on v1-only)."""
        url = (
            f"{self._base}/shepard/api/collections/{coll_id}"
            f"/dataObjects/{do_id}/timeseriesReferences"
        )
        body = {
            "name": "warmup-probe-ts",
            "measurement": "warmup",
            "device": "probe",
            "location": "probe",
            "symbolicName": "probe",
            "field": "value",
        }
        try:
            r = self._s.post(url, json=body, timeout=30)
            if r.ok:
                print(f"    ✓ timeseries ref  — warmup-probe-ts")
                return True
            print(f"    ~ timeseries ref  — http {r.status_code} (expected without TS container: {r.text[:120]})")
        except Exception as exc:
            print(f"    ✗ timeseries ref  — {exc}")
        return False

    def get_collection_attr(self, coll_id: int, key: str) -> str | None:
        r = self._get(f"{self._base}/shepard/api/collections/{coll_id}")
        if r is None:
            return None
        return (r.json().get("attributes") or {}).get(key)

    # ── Snapshots (v2) ────────────────────────────────────────────────────────

    def create_snapshot(self, coll_app_id: str, label: str) -> dict | None:
        body = {
            "label": label,
            "description": f"Auto-snapshot after MFFD dropbox import — session {SESSION_ID} at {IMPORT_TIME}",
        }
        r = self._post(f"{self._base}/v2/collections/{coll_app_id}/snapshots", body)
        return r.json() if r else None

    # ── Low-level ─────────────────────────────────────────────────────────────

    # Retry-able status codes: gateway-down / upstream-error class.
    # Excludes 500 (genuine server bug — fail fast so we see it).
    _RETRY_STATUSES = frozenset({502, 503, 504, 520, 521, 522, 523, 524})

    def _request_with_retry(
        self,
        method: str,
        url: str,
        *,
        timeout: int = 60,
        deadline_s: float = 900.0,  # 15 min — covers a Quarkus reboot + Flyway migrate
        **kwargs: Any,
    ) -> Response | None:
        """HTTP call that survives a destination-Shepard redeploy.

        Retries forever (until deadline_s) on:
          * urllib3/requests transient errors (ConnectionError, Timeout,
            ChunkedEncodingError, ReadTimeout)
          * Gateway/upstream-error responses (502, 503, 504, 520-524)

        Non-retryable codes (200-499, 500) are returned as-is — the caller's
        existing `if not r.ok` branch logs them. We deliberately don't retry
        500 because that's a real server bug, not a deploy blip.

        Prints `[reconnect] backend unreachable...` once on entering the
        retry loop, then `[reconnect] backend back ✓` on first success.
        """
        backoff = 2.0
        max_backoff = 60.0
        deadline = time.monotonic() + deadline_s
        waiting = False
        attempt = 0

        while True:
            attempt += 1
            last_label: str = ""
            try:
                r = self._s.request(method, url, timeout=timeout, **kwargs)
                if r.status_code not in self._RETRY_STATUSES:
                    if waiting:
                        print(
                            f"  [reconnect] backend back ✓ (HTTP {r.status_code}, "
                            f"after {attempt} attempts)",
                            flush=True,
                        )
                    return r
                last_label = f"HTTP {r.status_code}"
            except (
                requests.exceptions.ConnectionError,
                requests.exceptions.Timeout,
                requests.exceptions.ChunkedEncodingError,
            ) as exc:
                last_label = exc.__class__.__name__

            if not waiting:
                print(
                    f"  [reconnect] backend unreachable ({last_label}); "
                    f"waiting up to {deadline_s:.0f}s for it to come back…",
                    flush=True,
                )
                waiting = True

            if time.monotonic() >= deadline:
                print(
                    f"  [reconnect] giving up on {method} {url.split('?')[0]} "
                    f"after {attempt} attempts ({deadline_s:.0f}s deadline)",
                    flush=True,
                )
                return None

            time.sleep(backoff)
            backoff = min(backoff * 1.5, max_backoff)

    def _get(self, url: str, params: dict | None = None) -> Response | None:
        r = self._request_with_retry("GET", url, params=params, timeout=30)
        if r is None:
            return None
        if not r.ok:
            self._log_err("GET", url, r)
            return None
        return r

    def _get_raw(self, url: str) -> Response | None:
        # Short deadline + low timeout: this is the warmup probe, we want
        # fast failure if the dest is genuinely down at startup.
        return self._request_with_retry("GET", url, timeout=10, deadline_s=30.0)

    def _post(self, url: str, body: dict) -> Response | None:
        r = self._request_with_retry("POST", url, json=body, timeout=60)
        if r is None:
            return None
        if not r.ok:
            self._log_err("POST", url, r)
            return None
        return r

    def _put(self, url: str, body: dict) -> Response | None:
        r = self._request_with_retry("PUT", url, json=body, timeout=30)
        if r is None:
            return None
        if not r.ok:
            self._log_err("PUT", url, r)
            return None
        return r

    @staticmethod
    def _log_err(method: str, url: str, r: Response) -> None:
        print(f"  [http {r.status_code}] {method} {url.split('?')[0]}: {r.text[:400]}")


class _TqdmReader:
    """Thin file wrapper that updates a tqdm bar as bytes are read (for upload progress)."""
    def __init__(self, fh: Any, bar: tqdm) -> None:
        self._fh = fh
        self._bar = bar

    def read(self, n: int = -1) -> bytes:
        chunk = self._fh.read(n)
        self._bar.update(len(chunk))
        return chunk


# ── Destination collection + skeleton DataObjects ─────────────────────────────

def ensure_dest_collection(client: ShepardClient) -> tuple[int, str | None]:
    """Find or create the MFFD-Dropbox collection. Returns (coll_id, coll_app_id)."""
    print(f"\n[collection] {COLLECTION_NAME!r}")
    coll = client.find_collection(COLLECTION_NAME)
    if coll:
        print(f"  found (id={coll['id']})")
    else:
        print("  creating ...")
        coll = client.create_collection(
            COLLECTION_NAME,
            description=(
                f"MFFD manufacturing dropbox — real process data.\n"
                f"Session {SESSION_ID}.  Import time: {IMPORT_TIME}.\n"
                f"Auto-created by mffd-dropbox-import.py."
            ),
            attrs={"domain": "MFFD", "session": SESSION_ID, "type": "dropbox"},
        )
        if coll is None:
            print("  FAILED — aborting")
            sys.exit(1)
        print(f"  created (id={coll['id']})")
        client.set_collection_public(coll["id"])
    coll_id: int = coll["id"]
    coll_app_id: str | None = coll.get("appId") or client.get_collection_app_id(coll_id)
    return coll_id, coll_app_id


def ensure_dest_do(
    client: ShepardClient,
    coll_id: int,
    name: str,
    description: str,
    attrs: dict,
    predecessor_id: int | None = None,
) -> dict | None:
    existing = client.find_data_object(coll_id, name)
    if existing:
        print(f"  already exists (id={existing['id']})")
        return existing
    do = client.create_data_object(
        coll_id, name, description=description, attrs=attrs, predecessor_id=predecessor_id
    )
    if do:
        print(f"  created (id={do['id']}, appId={do.get('appId')})")
    return do


# ── Bootstrap (one-time collection setup) ─────────────────────────────────────

def run_bootstrap(client: ShepardClient) -> None:
    """
    One-time setup: create the destination collection with rich provenance attrs,
    skeleton DataObjects, self-upload the script, and capture a t=0 snapshot.

    Run this ONCE from nuclide.systems before the DLR intranet import.
    After bootstrap the ImportScripts DataObject holds this script — from that
    point on use run-mffd-import.sh to fetch + run.
    """
    print("\n=== Bootstrap ===")
    print(f"  destination: {SHEPARD_URL}")
    print(f"  collection : {COLLECTION_NAME!r}")
    if OPERATOR:
        print(f"  operator   : {OPERATOR}")

    # 1. Find or create the collection with rich provenance attrs
    print(f"\n[collection] {COLLECTION_NAME!r}")
    coll = client.find_collection(COLLECTION_NAME)
    if coll:
        print(f"  already exists (id={coll['id']}) — will add skeleton DOs + script + snapshot")
    else:
        print("  creating ...")
        attrs: dict[str, str] = {
            "domain": "MFFD",
            "session": SESSION_ID,
            "type": "dropbox",
            "source_instance": SOURCE_SHEPARD_URL,
            "source_tapelaying_coll_id": str(SOURCE_TAPELAYING_COLL_ID) if SOURCE_TAPELAYING_COLL_ID else "48297",
            "source_bridgewelding_coll_id": str(SOURCE_BRIDGEWELDING_COLL_ID) if SOURCE_BRIDGEWELDING_COLL_ID else "163811",
            "import_tool": "mffd-dropbox-import.py",
            "bootstrapped_at": IMPORT_TIME,
        }
        if OPERATOR:
            attrs["operator"] = OPERATOR
        coll = client.create_collection(
            COLLECTION_NAME,
            description=(
                f"MFFD manufacturing dropbox — real process data from DLR Augsburg ZLP.\n"
                f"Session {SESSION_ID}.  Bootstrapped at {IMPORT_TIME}.\n"
                f"Source: {SOURCE_SHEPARD_URL}\n"
                f"Created by mffd-dropbox-import.py --bootstrap."
            ),
            attrs=attrs,
        )
        if coll is None:
            print("  FAILED — aborting bootstrap")
            sys.exit(1)
        print(f"  created (id={coll['id']}, appId={coll.get('appId')})")

    coll_id: int = coll["id"]
    coll_app_id: str | None = coll.get("appId") or client.get_collection_app_id(coll_id)
    coll_name: str = coll.get("name") or COLLECTION_NAME

    # Make the collection visible+writable to all instance users so team members
    # (e.g. flo) can find it by name and upload data without needing explicit grants.
    client.set_collection_public(coll_id)

    # 2. Skeleton DataObjects — placeholders for real data that arrives via source mode
    session_attrs: dict[str, str] = {
        "session": SESSION_ID,
        "campaign": "MFFD",
        "bootstrapped_at": IMPORT_TIME,
    }
    if OPERATOR:
        session_attrs["operator"] = OPERATOR

    src_tl = SOURCE_TAPELAYING_COLL_ID or 48297
    src_bw = SOURCE_BRIDGEWELDING_COLL_ID or 163811

    print(f"\n[skeleton] TapeLaying-skeleton")
    tl = ensure_dest_do(
        client, coll_id,
        "TapeLaying-skeleton",
        description=(
            f"Placeholder for MFFD tape-laying process data.\n"
            f"Real data will be imported from source collection {src_tl} on DLR intranet "
            f"({SOURCE_SHEPARD_URL})."
        ),
        attrs={**session_attrs, "process_step": "tapelaying", "status": "skeleton",
               "source_collection_id": str(src_tl), "source_instance": SOURCE_SHEPARD_URL},
    )

    print(f"\n[skeleton] BridgeWelding-skeleton")
    bw = ensure_dest_do(
        client, coll_id,
        "BridgeWelding-skeleton",
        description=(
            f"Placeholder for MFFD bridge/frame welding process data.\n"
            f"Real data will be imported from source collection {src_bw} on DLR intranet "
            f"({SOURCE_SHEPARD_URL}).\n"
            f"Process chain predecessor: TapeLaying-skeleton."
        ),
        attrs={**session_attrs, "process_step": "bridgewelding", "status": "skeleton",
               "source_collection_id": str(src_bw), "source_instance": SOURCE_SHEPARD_URL},
        predecessor_id=tl["id"] if tl else None,
    )

    print(f"\n[skeleton] WikiDump-{SESSION_ID}")
    ensure_dest_do(
        client, coll_id,
        f"WikiDump-{SESSION_ID}",
        description=(
            f"Wiki export placeholder — session {SESSION_ID}.\n"
            f"Upload one zip file manually via the UI."
        ),
        attrs={**session_attrs, "process_step": "wikidump",
               "note": "upload manually — one zip file"},
    )

    # 3. ImportScripts + self-upload (script lives here for reproducibility)
    print(f"\n[importscripts]")
    isc = ensure_dest_do(
        client, coll_id,
        "ImportScripts",
        description=(
            "Ingest scripts — provenance artifact.\n"
            "Fetch mffd-dropbox-import.py from here via run-mffd-import.sh to reproduce this run."
        ),
        attrs={"type": "toolbox", "note": "self-uploaded by mffd-dropbox-import.py"},
    )
    if isc:
        client.upload_self(coll_id, isc["id"], isc.get("appId") or "")
        client.verify_references(coll_id, isc["id"], "ImportScripts")

    # 4. t=0 snapshot — label records collection name at this moment
    print(f"\n[snapshot] t=0 ...")
    if coll_app_id:
        label = f"bootstrap-t0@{coll_name}"
        snap = client.create_snapshot(coll_app_id, label)
        if snap:
            print(f"  created: {snap.get('label')}  (appId={snap.get('appId')})")
            print(f"  label captures collection name '{coll_name}' — renames tracked via snapshot history")
        else:
            print("  WARNING: snapshot creation failed (non-fatal)")
    else:
        print("  WARNING: no collection appId — snapshot skipped")

    print(f"\n=== Bootstrap done ===")
    print(f"  Collection '{coll_name}'  id={coll_id}")
    print(f"  Script uploaded to ImportScripts DataObject.")
    print()
    print("  Next step — run from DLR network:")
    print(f"    export SHEPARD_URL={SHEPARD_URL}")
    print(f"    export SHEPARD_API_KEY=<nuclide-key>")
    print(f"    export SOURCE_SHEPARD_URL={SOURCE_SHEPARD_URL}")
    print(f"    export SOURCE_SHEPARD_API_KEY=<dlr-intranet-jwt>")
    print(f"    export SOURCE_TAPELAYING_COLL_ID={src_tl}")
    print(f"    export SOURCE_BRIDGEWELDING_COLL_ID={src_bw}")
    print(f"    export SESSION_ID={SESSION_ID}")
    print(f"    bash run-mffd-import.sh")


# ── SOURCE MODE ───────────────────────────────────────────────────────────────

def run_source_mode(
    dest_client: ShepardClient,
    coll_id: int,
    state: ImportState | None = None,
    source_client: ShepardClient | None = None,
) -> dict[str, int]:
    """
    Traverse source Shepard collections, migrating all three payload types
    (files, timeseries, structured data) into the destination collection.

    Strategy:
    - One dest DO per step (tapelaying / bridgewelding) as the step container.
    - Files: each source file → dest DO (prefixed with source DO name).
    - Timeseries: one shared TS container per step; each source DO's TS refs are
      exported as WIDE CSV and imported into the shared container; the dest step
      DO gets one timeseriesReference to the shared container.
    - Structured data: each source DO's structured refs are downloaded as JSON
      and re-uploaded into a new dest structured container linked to the step DO.

    Pass source_client for cross-instance pull (DLR intranet → nuclide.systems).
    If source_client is None, dest_client is used for source operations too.

    Returns mapping of step_key → dest_do_id for predecessor wiring.
    """
    _src = source_client or dest_client

    print()
    print("  ┌─────────────────────────────────────────────────────────────────┐")
    print("  │  ⚠  TIMESTAMP CAVEAT                                            │")
    print("  │  DataObject creationDate and file upload timestamps will         │")
    print("  │  reflect the IMPORT time, not the original measurement time.     │")
    print("  │  Source timestamps are preserved as source_created / source_mod  │")
    print("  │  attributes on every migrated DataObject.                        │")
    print("  └─────────────────────────────────────────────────────────────────┘")
    print()

    if source_client is not None:
        print(f"  [cross-instance] source : {_src._base}")
        print(f"  [cross-instance] dest   : {dest_client._base}")

    do_ids: dict[str, int] = {}

    source_map = [
        ("tapelaying",    SOURCE_TAPELAYING_COLL_ID,    None),
        ("bridgewelding", SOURCE_BRIDGEWELDING_COLL_ID, "tapelaying"),
    ]

    for step_key, src_coll_id, pred_key in source_map:
        if src_coll_id is None:
            print(f"\n[{step_key}] SOURCE_{step_key.upper()}_COLL_ID not set — skipping")
            continue

        print(f"\n[{step_key}] source collection {src_coll_id}")
        total_dos = _src.count_data_objects(src_coll_id)
        print(f"  {total_dos} DataObject(s) to migrate")

        dest_do_name = f"{step_key.capitalize()}-{SESSION_ID}"
        pred_id = do_ids.get(pred_key) if pred_key else None

        base_attrs: dict[str, str] = {
            "session": SESSION_ID,
            "campaign": "MFFD",
            "process_step": step_key,
            "source_collection_id": str(src_coll_id),
            "import_time": IMPORT_TIME,
            "timestamp_note": "file timestamps reflect import time not measurement time",
        }

        dest_do = ensure_dest_do(
            dest_client,
            coll_id,
            dest_do_name,
            description=(
                f"MFFD {step_key} — migrated from source collection {src_coll_id}.\n"
                f"Session {SESSION_ID}.  Import time: {IMPORT_TIME}.\n"
                f"⚠ Timestamps reflect import time, not original measurement time."
            ),
            attrs=base_attrs,
            predecessor_id=pred_id,
        )
        if dest_do is None:
            print(f"  FAILED to create dest DataObject — skipping {step_key}")
            continue

        do_ids[step_key] = dest_do["id"]
        dest_app_id: str = dest_do.get("appId") or ""
        dest_do_id: int = dest_do["id"]

        dest_client.verify_references(coll_id, dest_do_id, dest_do_name)

        # ── Shared TS container for this step ─────────────────────────────────
        ts_container_id: int | None = state.get_ts_container(step_key) if state else None
        if ts_container_id is None:
            ts_container_name = f"MFFD-{step_key}-ts-{SESSION_ID}"
            print(f"  [ts] creating shared TS container {ts_container_name!r}")
            ts_container_id = dest_client.create_ts_container(ts_container_name)
            if ts_container_id:
                print(f"  [ts] container id={ts_container_id}")
                if state:
                    state.set_ts_container(step_key, ts_container_id)
                # Link the step DO to the shared TS container
                dest_client.link_ts_to_do(coll_id, dest_do_id, ts_container_id, ts_container_name)
            else:
                print(f"  [ts] WARNING: could not create TS container — timeseries will be skipped")

        # ── Walk source DOs ────────────────────────────────────────────────────
        files_uploaded = 0
        files_skipped = 0
        files_failed = 0
        ts_imported = 0
        ts_skipped = 0
        ts_failed = 0
        structured_imported = 0
        structured_failed = 0

        with tqdm(
            total=total_dos,
            desc=f"  DataObjects [{step_key}]",
            unit="DO",
            file=sys.stderr,
        ) as do_bar:
            for src_do in _src.iter_data_objects(src_coll_id):
                if MAX_DOS_PER_STEP and do_bar.n >= MAX_DOS_PER_STEP:
                    do_bar.write(f"  [--max-dos {MAX_DOS_PER_STEP} reached; stopping step early]")
                    break
                do_bar.set_postfix_str(src_do.name[:30])

                payload_summary = (
                    f"{len(src_do.file_refs)}f"
                    f" {len(src_do.ts_refs)}ts"
                    f" {len(src_do.structured_refs)}sd"
                )
                do_bar.write(f"  ↳ {src_do.name}  ({payload_summary})")

                existing_names = dest_client.list_file_refs(coll_id, dest_do_id)

                # ── Files ──────────────────────────────────────────────────────
                with tempfile.TemporaryDirectory() as tmpdir:
                    tmp = Path(tmpdir)
                    for fref in src_do.file_refs:
                        dest_name = f"{src_do.name}/{fref.name}"
                        state_key = f"{step_key}/{dest_name}"

                        if state is not None and state.is_file_done(state_key):
                            do_bar.write(f"    [skip-file] {dest_name}")
                            files_skipped += 1
                            continue

                        if dest_name in existing_names or fref.name in existing_names:
                            do_bar.write(f"    [skip-file] {fref.name}")
                            files_skipped += 1
                            if state is not None:
                                state.mark_file_done(state_key)
                            continue

                        tmp_file = tmp / fref.name
                        ok = _src.download_file_ref(
                            src_coll_id, src_do.do_id, fref.fref_id, tmp_file, fref.size
                        )
                        if not ok:
                            do_bar.write(f"    [error-file] download {fref.name}")
                            files_failed += 1
                            continue

                        ok = dest_client.upload_file(
                            dest_app_id, tmp_file, dest_name, coll_id, dest_do_id
                        )
                        if ok:
                            do_bar.write(f"    [ok-file] {dest_name}  ({_human(fref.size)})")
                            files_uploaded += 1
                            if state is not None:
                                state.mark_file_done(state_key)
                        else:
                            do_bar.write(f"    [error-file] upload {dest_name}")
                            files_failed += 1

                # ── Timeseries ─────────────────────────────────────────────────
                if ts_container_id is not None:
                    for ts_ref in src_do.ts_refs:
                        state_key = f"ts/{step_key}/{src_do.do_id}/{ts_ref.ref_id}"
                        if state is not None and state.is_ts_done(state_key):
                            do_bar.write(f"    [skip-ts] {ts_ref.name}")
                            ts_skipped += 1
                            continue

                        do_bar.write(f"    [ts] exporting {ts_ref.name!r} from source ref {ts_ref.ref_id}")
                        csv_bytes = _src.export_ts(src_coll_id, src_do.do_id, ts_ref.ref_id)
                        if csv_bytes is None:
                            do_bar.write(f"    [error-ts] export failed for {ts_ref.name}")
                            ts_failed += 1
                            continue

                        do_bar.write(f"    [ts] importing {len(csv_bytes):,} bytes → container {ts_container_id}")
                        ok = dest_client.import_ts_csv(
                            ts_container_id,
                            csv_bytes,
                            filename=f"{step_key}-{src_do.do_id}-{ts_ref.ref_id}.csv",
                        )
                        if ok:
                            ts_imported += 1
                            if state is not None:
                                state.mark_ts_done(state_key)
                        else:
                            do_bar.write(f"    [error-ts] import failed for {ts_ref.name}")
                            ts_failed += 1

                # ── Structured data ────────────────────────────────────────────
                for sd_ref in src_do.structured_refs:
                    state_key = f"sd/{step_key}/{src_do.do_id}/{sd_ref.ref_id}"
                    if state is not None and state.is_structured_done(state_key):
                        do_bar.write(f"    [skip-sd] {sd_ref.name}")
                        continue

                    payload = _src.download_structured(src_coll_id, src_do.do_id, sd_ref.ref_id)
                    if payload is None:
                        do_bar.write(f"    [error-sd] download failed for {sd_ref.name}")
                        structured_failed += 1
                        continue

                    container_name = f"{src_do.name}/{sd_ref.name}"
                    container_id = dest_client.create_structured_container(container_name)
                    if container_id is None:
                        do_bar.write(f"    [error-sd] could not create container for {sd_ref.name}")
                        structured_failed += 1
                        continue

                    linked = dest_client.link_structured_to_do(coll_id, dest_do_id, container_id, container_name)
                    ok = dest_client.upload_structured_payload(container_id, payload)
                    if linked and ok:
                        do_bar.write(f"    [ok-sd] {sd_ref.name}")
                        structured_imported += 1
                        if state is not None:
                            state.mark_structured_done(state_key)
                    else:
                        do_bar.write(f"    [error-sd] upload/link failed for {sd_ref.name}")
                        structured_failed += 1

                do_bar.update(1)

        print(
            f"  → files: uploaded={files_uploaded} skipped={files_skipped} failed={files_failed}"
        )
        print(
            f"  → ts:    imported={ts_imported} skipped={ts_skipped} failed={ts_failed}"
        )
        print(
            f"  → sd:    imported={structured_imported} failed={structured_failed}"
        )
        dest_client.verify_references(coll_id, dest_do_id, dest_do_name)

    return do_ids


# ── LOCAL MODE ────────────────────────────────────────────────────────────────

def file_list(directory: Path | None) -> list[Path]:
    if directory is None or not directory.is_dir():
        return []
    return sorted(f for f in directory.rglob("*") if f.is_file())


def run_local_mode_stateful(client: ShepardClient, coll_id: int, state: ImportState) -> dict[str, int]:
    return run_local_mode(client, coll_id, state)


def run_local_mode(client: ShepardClient, coll_id: int, state: ImportState | None = None) -> dict[str, int]:
    do_ids: dict[str, int] = {}

    chain = [
        ("tapelaying",    None),
        ("bridgewelding", "tapelaying"),
    ]
    session_attrs = {"session": SESSION_ID, "campaign": "MFFD", "import_time": IMPORT_TIME}

    for step_key, pred_key in chain:
        print(f"\n[{step_key}]")
        dest_do_name = f"{step_key.capitalize()}-{SESSION_ID}"
        local_dir = DATA_DIR / step_key if (DATA_DIR / step_key).is_dir() else None
        pred_id = do_ids.get(pred_key) if pred_key else None

        dest_do = ensure_dest_do(
            client,
            coll_id,
            dest_do_name,
            description=f"MFFD {step_key} — local import, session {SESSION_ID}.",
            attrs={**session_attrs, "process_step": step_key},
            predecessor_id=pred_id,
        )
        if dest_do is None:
            continue

        do_ids[step_key] = dest_do["id"]
        app_id = dest_do.get("appId") or ""

        client.verify_references(coll_id, dest_do["id"], dest_do_name)

        files = file_list(local_dir)
        if not files:
            print(f"  no local files in {local_dir or '(no dir)'}")
            continue

        existing_names = client.list_file_refs(coll_id, dest_do["id"])
        uploaded = 0
        with tqdm(total=len(files), desc=f"  files [{step_key}]", unit="file", file=sys.stderr) as bar:
            for fp in files:
                display = str(fp.relative_to(DATA_DIR))
                state_key = f"{step_key}/{display}"
                bar.set_postfix_str(fp.name[:30])
                if state is not None and state.is_file_done(state_key):
                    bar.write(f"  [resume-skip] {display}")
                    bar.update(1)
                    continue
                if display in existing_names or fp.name in existing_names:
                    bar.write(f"  [skip] {display}")
                    if state is not None:
                        state.mark_file_done(state_key)
                    bar.update(1)
                    continue
                ok = client.upload_file(app_id, fp, display, coll_id, dest_do["id"])
                bar.write(f"  {'[ok]  ' if ok else '[err] '}{display}  ({_human(fp.stat().st_size)})")
                if ok:
                    uploaded += 1
                    if state is not None:
                        state.mark_file_done(state_key)
                bar.update(1)

        if uploaded:
            client.verify_references(coll_id, dest_do["id"], dest_do_name)

    return do_ids


# ── Shared standalones (wikidump + importscripts) ─────────────────────────────

def ensure_standalones(client: ShepardClient, coll_id: int) -> None:
    session_attrs = {"session": SESSION_ID, "campaign": "MFFD", "import_time": IMPORT_TIME}

    print(f"\n[wikidump]")
    wd = ensure_dest_do(
        client, coll_id,
        f"WikiDump-{SESSION_ID}",
        description=f"Wiki export placeholder — session {SESSION_ID}.\nUpload one zip file manually.",
        attrs={**session_attrs, "process_step": "wikidump", "note": "upload manually — one zip file"},
    )
    if wd:
        print("  ready — upload one zip via the UI")
        client.verify_references(coll_id, wd["id"], f"WikiDump-{SESSION_ID}")

    print(f"\n[importscripts]")
    isc = ensure_dest_do(
        client, coll_id,
        "ImportScripts",
        description="Ingest scripts — provenance artifact. Fetch from here to reproduce this run.",
        attrs={"type": "toolbox", "note": "self-uploaded by mffd-dropbox-import.py"},
    )
    if isc:
        client.upload_self(coll_id, isc["id"], isc.get("appId") or "")
        client.verify_references(coll_id, isc["id"], "ImportScripts")


# ── State tracker (resume support) ───────────────────────────────────────────

import json
import time

class ImportState:
    """Persist progress to JSON so a failed run can resume cleanly."""

    def __init__(self, path: Path) -> None:
        self._path = path
        self._data: dict = {}
        if path.exists():
            try:
                with path.open() as f:
                    self._data = json.load(f)
                print(f"[state] Resuming from {path}  ({len(self._data.get('completed_files',[]))} files already done)")
            except Exception:
                self._data = {}

    def _save(self) -> None:
        self._path.write_text(json.dumps(self._data, indent=2))

    @property
    def warmup_done(self) -> bool:
        return bool(self._data.get("warmup_done"))

    def mark_warmup_done(self) -> None:
        self._data["warmup_done"] = True
        self._save()

    @property
    def gate_passed(self) -> bool:
        return bool(self._data.get("gate_passed"))

    def mark_gate_passed(self) -> None:
        self._data["gate_passed"] = True
        self._save()

    def is_do_done(self, do_name: str) -> bool:
        return do_name in self._data.get("completed_dos", [])

    def mark_do_done(self, do_name: str) -> None:
        self._data.setdefault("completed_dos", [])
        if do_name not in self._data["completed_dos"]:
            self._data["completed_dos"].append(do_name)
        self._save()

    def is_file_done(self, key: str) -> bool:
        return key in self._data.get("completed_files", [])

    def mark_file_done(self, key: str) -> None:
        self._data.setdefault("completed_files", [])
        if key not in self._data["completed_files"]:
            self._data["completed_files"].append(key)
        self._save()

    def is_ts_done(self, key: str) -> bool:
        return key in self._data.get("completed_ts", [])

    def mark_ts_done(self, key: str) -> None:
        self._data.setdefault("completed_ts", [])
        if key not in self._data["completed_ts"]:
            self._data["completed_ts"].append(key)
        self._save()

    def is_structured_done(self, key: str) -> bool:
        return key in self._data.get("completed_structured", [])

    def mark_structured_done(self, key: str) -> None:
        self._data.setdefault("completed_structured", [])
        if key not in self._data["completed_structured"]:
            self._data["completed_structured"].append(key)
        self._save()

    def get_ts_container(self, step_key: str) -> int | None:
        return self._data.get("ts_containers", {}).get(step_key)

    def set_ts_container(self, step_key: str, container_id: int) -> None:
        self._data.setdefault("ts_containers", {})[step_key] = container_id
        self._save()


# ── Deployment lock ───────────────────────────────────────────────────────────

class DeployLock:
    """Write a lock file that signals 'do not redeploy while import is running'."""

    def __init__(self, path: Path) -> None:
        self._path = path

    def acquire(self) -> None:
        if self._path.exists():
            existing = self._path.read_text().strip()
            print(f"[lock] WARNING: lock file exists from prior run: {self._path}")
            print(f"       Contents: {existing}")
            print(f"       Delete it manually if the previous run is gone, then re-run.")
            sys.exit(1)
        self._path.write_text(
            f"session={SESSION_ID}\nstarted={IMPORT_TIME}\npid={os.getpid()}\n"
        )
        print(f"[lock] acquired → {self._path}")

    def release(self) -> None:
        if self._path.exists():
            self._path.unlink()
            print(f"[lock] released → {self._path}")


# ── Warmup probe + human/Claude gate ─────────────────────────────────────────

GATE_ATTR = "import_ready"
GATE_POLL_SEC = 8
GATE_TIMEOUT_MIN = 60

def warmup_probe_and_gate(
    client: ShepardClient,
    coll_id: int,
    state: ImportState,
) -> None:
    """
    1. Create WarmupProbe DataObject.
    2. Upload one probe per reference type (file / structured / timeseries).
    3. Wait for a human (+ Claude) to review and set collection attribute
       import_ready=<SESSION_ID> to proceed.
    Claude sets the attribute via:
       PATCH /shepard/api/collections/{coll_id}
       body: {"attributes": {"import_ready": "<SESSION_ID>"}}
    """
    if state.gate_passed:
        print("[gate] already cleared in a previous run — continuing")
        return

    print("\n=== Warmup Probe ===")

    # Create or find WarmupProbe DataObject
    probe_do_name = f"WarmupProbe-{SESSION_ID}"
    probe_do = client.find_data_object(coll_id, probe_do_name)
    if not probe_do:
        probe_do = client.create_data_object(
            coll_id,
            probe_do_name,
            description=(
                f"Warmup probe DataObject — one payload of each reference type.\n"
                f"Review these before continuing the import.\n"
                f"Session {SESSION_ID}  import_time {IMPORT_TIME}"
            ),
            attrs={
                "session": SESSION_ID,
                "type": "warmup_probe",
                "import_time": IMPORT_TIME,
            },
        )
    if probe_do is None:
        print("  [warn] Could not create WarmupProbe DataObject — gate skipped")
        state.mark_gate_passed()
        return

    do_id: int = probe_do["id"]
    app_id: str = probe_do.get("appId") or ""

    if not state.warmup_done:
        print(f"  Uploading probe payloads to {probe_do_name!r} (id={do_id}) ...")
        client.probe_file(coll_id, do_id, app_id)
        client.probe_structured(coll_id, do_id)
        client.probe_timeseries(coll_id, do_id)
        state.mark_warmup_done()
    else:
        print(f"  Probe payloads already uploaded (state file records warmup_done=True)")

    # Show what's there now
    client.verify_references(coll_id, do_id, probe_do_name)

    # Print gate instructions
    coll_url = f"{SHEPARD_URL}/collections/{coll_id}/dataobjects/{do_id}"
    print()
    print("  ┌─────────────────────────────────────────────────────────────────────┐")
    print("  │  REVIEW GATE                                                         │")
    print(f"  │  Check the WarmupProbe DataObject in the UI:                         │")
    print(f"  │  {coll_url[:68]:<68}  │")
    print(f"  │                                                                      │")
    print(f"  │  When Claude + you agree the probe data looks good, Claude will set:  │")
    print(f"  │    PATCH /shepard/api/collections/{coll_id}                           │")
    print(f"  │    body: {{\"attributes\": {{\"{GATE_ATTR}\": \"{SESSION_ID}\"}}}}          │")
    print(f"  │                                                                      │")
    print(f"  │  The script will detect the change and continue.                     │")
    print("  └─────────────────────────────────────────────────────────────────────┘")
    print()

    # Poll for the gate attribute (written to stderr so tqdm works)
    deadline = time.time() + GATE_TIMEOUT_MIN * 60
    with tqdm(
        desc="  Waiting for import_ready",
        unit="poll",
        file=sys.stderr,
        bar_format="{desc} | {elapsed} elapsed | {postfix}",
    ) as bar:
        while time.time() < deadline:
            val = client.get_collection_attr(coll_id, GATE_ATTR)
            bar.set_postfix_str(f"{GATE_ATTR}={val!r}")
            if val == SESSION_ID:
                bar.write(f"  [gate] {GATE_ATTR}={val!r} — CLEARED, continuing import")
                state.mark_gate_passed()
                return
            bar.update(1)
            time.sleep(GATE_POLL_SEC)

    print(f"\n[gate] TIMEOUT after {GATE_TIMEOUT_MIN} min — no confirmation received")
    print(f"       Re-run the script; it will skip the probe (already done) and poll again.")
    sys.exit(1)


# ── Helpers ───────────────────────────────────────────────────────────────────

def _human(n: int | float) -> str:
    for u in ("B", "KB", "MB", "GB", "TB"):
        if n < 1024:
            return f"{n:.1f} {u}"
        n /= 1024
    return f"{n:.1f} PB"


# ── Main ──────────────────────────────────────────────────────────────────────

def main() -> None:
    ap = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter
    )
    ap.add_argument(
        "--bootstrap",
        action="store_true",
        help=(
            "One-time setup: create destination collection with provenance attrs, "
            "skeleton DataObjects, self-upload this script, t=0 snapshot. "
            "Run once on nuclide.systems BEFORE the DLR intranet import."
        ),
    )
    ap.add_argument("--dry-run", action="store_true", help="Print plan, make no API calls.")
    ap.add_argument(
        "--max-dos",
        type=int,
        default=0,
        metavar="N",
        help=(
            "Process at most N source DataObjects per step (default: 0 = unlimited). "
            "Use for safe sample runs after a previous full attempt — e.g. `--max-dos 10` "
            "to verify file transfer works before re-attempting all 8462 DOs."
        ),
    )
    args = ap.parse_args()
    global MAX_DOS_PER_STEP
    MAX_DOS_PER_STEP = args.max_dos

    source_mode = SOURCE_TAPELAYING_COLL_ID is not None or SOURCE_BRIDGEWELDING_COLL_ID is not None
    cross_instance = SOURCE_SHEPARD_URL != SHEPARD_URL
    if args.bootstrap:
        mode_label = "BOOTSTRAP"
    elif source_mode:
        mode_label = "SOURCE"
    else:
        mode_label = "LOCAL"

    if args.dry_run:
        print(f"[config] mode                        = {mode_label}")
        print(f"[config] SHEPARD_URL                 = {SHEPARD_URL}")
        print(f"[config] COLLECTION_NAME             = {COLLECTION_NAME!r}")
        print(f"[config] SESSION_ID                  = {SESSION_ID!r}")
        if OPERATOR:
            print(f"[config] OPERATOR                     = {OPERATOR}")
        if source_mode or args.bootstrap:
            print(f"[config] SOURCE_SHEPARD_URL          = {SOURCE_SHEPARD_URL}")
            print(f"[config] SOURCE_TAPELAYING_COLL_ID   = {SOURCE_TAPELAYING_COLL_ID or 48297}")
            print(f"[config] SOURCE_BRIDGEWELDING_COLL_ID= {SOURCE_BRIDGEWELDING_COLL_ID or 163811}")
        else:
            print(f"[config] DATA_DIR                    = {DATA_DIR.resolve()}")
        print()
        print(f"=== DRY RUN ({mode_label}) — no API calls ===")
        if args.bootstrap:
            print("Would create:")
            print(f"  Collection:              {COLLECTION_NAME!r} (with provenance attrs)")
            print(f"  TapeLaying-skeleton      ← process chain root")
            print(f"  BridgeWelding-skeleton   ← predecessor: TapeLaying-skeleton")
            print(f"  WikiDump-{SESSION_ID}    ← placeholder (one zip upload)")
            print(f"  ImportScripts            ← self-upload of this script")
            print(f"  snapshot: bootstrap-t0@{COLLECTION_NAME}")
        else:
            print("Would create:")
            print(f"  Collection:              {COLLECTION_NAME!r}")
            print(f"  Tapelaying-{SESSION_ID}  ← root of chain")
            print(f"  Bridgewelding-{SESSION_ID} ← predecessor: Tapelaying")
            print(f"  WikiDump-{SESSION_ID}    ← standalone placeholder")
            print(f"  ImportScripts            ← self-upload")
            if source_mode:
                print(f"\nSource collections:")
                print(f"  tapelaying    → id {SOURCE_TAPELAYING_COLL_ID}  ({SOURCE_SHEPARD_URL})")
                print(f"  bridgewelding → id {SOURCE_BRIDGEWELDING_COLL_ID}")
        return

    if not SHEPARD_API_KEY and not SHEPARD_BEARER_TOKEN:
        print("ERROR: set SHEPARD_API_KEY or SHEPARD_BEARER_TOKEN.", file=sys.stderr)
        sys.exit(1)

    LOG_DIR.mkdir(parents=True, exist_ok=True)
    log_path  = LOG_DIR / f"mffd-import-{SESSION_ID}.log"
    state_path = LOG_DIR / f"mffd-import-{SESSION_ID}.state.json"
    lock_path  = LOG_DIR / ".mffd-import.lock"

    tee = Tee(log_path)
    sys.stdout = tee  # type: ignore[assignment]

    print(f"[config] mode                        = {mode_label}")
    print(f"[config] SHEPARD_URL                 = {SHEPARD_URL}")
    print(f"[config] COLLECTION_NAME             = {COLLECTION_NAME!r}")
    print(f"[config] SESSION_ID                  = {SESSION_ID!r}")
    print(f"[config] import_time                 = {IMPORT_TIME}")
    auth_mode = "Bearer" if SHEPARD_BEARER_TOKEN else "X-API-KEY"
    print(f"[config] auth                        = {auth_mode}")
    print(f"[config] log file                    = {log_path}")
    print(f"[config] state file                  = {state_path}")
    if OPERATOR:
        print(f"[config] OPERATOR                    = {OPERATOR}")
    if source_mode or args.bootstrap:
        print(f"[config] SOURCE_SHEPARD_URL          = {SOURCE_SHEPARD_URL}")
        if cross_instance:
            print(f"[config] cross-instance              = YES")
    if source_mode:
        print(f"[config] SOURCE_TAPELAYING_COLL_ID   = {SOURCE_TAPELAYING_COLL_ID}")
        print(f"[config] SOURCE_BRIDGEWELDING_COLL_ID= {SOURCE_BRIDGEWELDING_COLL_ID}")

    dest_client = ShepardClient(SHEPARD_URL, SHEPARD_API_KEY, SHEPARD_BEARER_TOKEN)

    if not dest_client.warmup():
        tee.close()
        sys.stdout = tee._stdout  # type: ignore[attr-defined]
        sys.exit(1)

    # ── Bootstrap mode (no lock — idempotent, no large upload) ────────────────
    if args.bootstrap:
        try:
            run_bootstrap(dest_client)
        finally:
            tee.close()
            sys.stdout = tee._stdout  # type: ignore[attr-defined]
            print(f"\nLog written to: {log_path}")
        return

    # ── Import mode (SOURCE or LOCAL) — acquire lock around full upload ────────
    lock = DeployLock(lock_path)
    lock.acquire()

    try:
        state = ImportState(state_path)

        coll_id, coll_app_id = ensure_dest_collection(dest_client)

        # Warmup probe + human/Claude review gate
        warmup_probe_and_gate(dest_client, coll_id, state)

        if source_mode:
            src_client = (
                ShepardClient(SOURCE_SHEPARD_URL, SOURCE_SHEPARD_API_KEY, "")
                if cross_instance
                else None
            )
            run_source_mode(dest_client, coll_id, state, source_client=src_client)
        else:
            run_local_mode(dest_client, coll_id, state)

        ensure_standalones(dest_client, coll_id)

        # Snapshot: fetch current collection name so renames are captured in label
        print("\n[snapshot] creating ...")
        if coll_app_id:
            current_name = dest_client.get_collection_name(coll_id)
            label = f"dropbox-import-{SESSION_ID}@{current_name}"
            snap = dest_client.create_snapshot(coll_app_id, label)
            if snap:
                print(f"  snapshot: {snap.get('label')}  appId={snap.get('appId')}")
            else:
                print("  WARNING: snapshot creation failed (non-fatal)")
        else:
            print("  WARNING: no collection appId — snapshot skipped")

        print(f"\n=== Done ({mode_label} mode) ===")
        print(f"  Session:  {SESSION_ID}")
        print(f"  Done at:  {datetime.datetime.now(datetime.timezone.utc).isoformat()}")
        print(f"  Log:      {log_path}")

    finally:
        lock.release()
        tee.close()
        sys.stdout = tee._stdout  # type: ignore[attr-defined]
        print(f"\nLog written to: {log_path}")


if __name__ == "__main__":
    main()
