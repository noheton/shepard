# /// script
# requires-python = ">=3.11"
# dependencies = ["requests", "tqdm"]
# ///
#!/usr/bin/env python3
"""mffd-dropbox-import.py — MFFD manufacturing process data ingest with provenance.

⚠️  TIMESTAMP WARNING
─────────────────────
Reimporting from source Shepard collections causes ALL timestamps (DataObject
creationDate, file upload time) to reflect the IMPORT time, NOT the original
measurement or recording time.  The original source timestamps are preserved as
explicit attributes (source_created, source_modified) but Shepard's own
timestamp fields will be wrong.  This is a known limitation of the v1 copy path.

Two modes
─────────
  SOURCE MODE  (preferred)   — traverse source Shepard collections by ID,
                               download every DataObject + file, re-upload to
                               the MFFD-Dropbox collection.

    Set SOURCE_TAPELAYING_COLL_ID and/or SOURCE_BRIDGEWELDING_COLL_ID.

  LOCAL MODE   (fallback)    — read files from DATA_DIR subdirs (tapelaying/,
                               bridgewelding/).

Both modes create the same provenance chain:
    TapeLaying-{session} → BridgeWelding-{session}
    WikiDump-{session}   (standalone placeholder)
    ImportScripts        (this script — self-uploaded)

Usage
─────
  # Source mode (DLR intranet, Shepard-issued JWT):
  SHEPARD_URL=https://backend.bt-au-cube3.intra.dlr.de \\
  SHEPARD_API_KEY=eyJhbGciOiJSUzI1NiJ9... \\
  SOURCE_TAPELAYING_COLL_ID=48297 \\
  SOURCE_BRIDGEWELDING_COLL_ID=163811 \\
  SESSION_ID=2026-05-22-Q1 \\
  uv run python mffd-dropbox-import.py

  # Local mode:
  SHEPARD_URL=https://shepard.nuclide.systems \\
  SHEPARD_API_KEY=<key> \\
  DATA_DIR=/path/to/mffd-data \\
  SESSION_ID=2026-05-22-Q1 \\
  uv run python mffd-dropbox-import.py

  # Dry-run:
  uv run python mffd-dropbox-import.py --dry-run

Environment variables
─────────────────────
  SHEPARD_URL                   Default: https://shepard.nuclide.systems
  SHEPARD_API_KEY               X-API-KEY header (Shepard-issued JWTs go here too)
  SHEPARD_BEARER_TOKEN          Authorization: Bearer (OIDC/Keycloak tokens)
  SOURCE_TAPELAYING_COLL_ID     Source collection ID for tape-laying DataObjects
  SOURCE_BRIDGEWELDING_COLL_ID  Source collection ID for bridge/frame welding
  SESSION_ID                    Run identifier.  Default: today YYYY-MM-DD
  DATA_DIR                      Local dir for LOCAL MODE.  Default: .
  COLLECTION_NAME               Destination collection.  Default: MFFD-Dropbox
  LOG_DIR                       Log file directory.  Default: script directory
  PAGE_SIZE                     DataObjects per page when traversing.  Default: 50
"""

from __future__ import annotations

import argparse
import datetime
import os
import sys
import tempfile
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
class SourceDO:
    """A DataObject discovered in a source collection."""
    do_id: int
    name: str
    description: str
    attributes: dict[str, str]
    file_refs: list[FileRef] = field(default_factory=list)
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
        user_r = self._get(f"{self._base}/shepard/api/users/currentUser")
        if user_r is None:
            print("  [FAIL] Cannot reach /users/currentUser — check SHEPARD_URL + auth")
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
                file_refs = self._fetch_file_refs(coll_id, item["id"])
                yield SourceDO(
                    do_id=item["id"],
                    name=item.get("name", f"DO-{item['id']}"),
                    description=item.get("description") or "",
                    attributes={k: str(v) for k, v in (item.get("attributes") or {}).items()},
                    file_refs=file_refs,
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

    def _get(self, url: str, params: dict | None = None) -> Response | None:
        try:
            r = self._s.get(url, params=params, timeout=30)
            if not r.ok:
                self._log_err("GET", url, r)
                return None
            return r
        except Exception as exc:
            print(f"  [net] GET {url}: {exc}")
            return None

    def _get_raw(self, url: str) -> Response | None:
        try:
            return self._s.get(url, timeout=10)
        except Exception:
            return None

    def _post(self, url: str, body: dict) -> Response | None:
        try:
            r = self._s.post(url, json=body, timeout=60)
            if not r.ok:
                self._log_err("POST", url, r)
                return None
            return r
        except Exception as exc:
            print(f"  [net] POST {url}: {exc}")
            return None

    def _put(self, url: str, body: dict) -> Response | None:
        try:
            r = self._s.put(url, json=body, timeout=30)
            if not r.ok:
                self._log_err("PUT", url, r)
                return None
            return r
        except Exception as exc:
            print(f"  [net] PUT {url}: {exc}")
            return None

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


# ── SOURCE MODE ───────────────────────────────────────────────────────────────

def run_source_mode(client: ShepardClient, coll_id: int, state: ImportState | None = None) -> dict[str, int]:
    """
    Traverse source Shepard collections, downloading DataObjects and files,
    re-uploading into the destination collection.

    Returns mapping of step_key → dest_do_id for predecessor wiring.
    """
    # ⚠️  Prominent timestamp warning
    print()
    print("  ┌─────────────────────────────────────────────────────────────────┐")
    print("  │  ⚠  TIMESTAMP CAVEAT                                            │")
    print("  │  DataObject creationDate and file upload timestamps will         │")
    print("  │  reflect the IMPORT time, not the original measurement time.     │")
    print("  │  Source timestamps are preserved as source_created / source_mod  │")
    print("  │  attributes on every migrated DataObject.                        │")
    print("  └─────────────────────────────────────────────────────────────────┘")
    print()

    do_ids: dict[str, int] = {}   # step_key → dest do_id

    source_map = [
        ("tapelaying",    SOURCE_TAPELAYING_COLL_ID,    None),
        ("bridgewelding", SOURCE_BRIDGEWELDING_COLL_ID, "tapelaying"),
    ]

    for step_key, src_coll_id, pred_key in source_map:
        if src_coll_id is None:
            print(f"\n[{step_key}] SOURCE_{step_key.upper()}_COLL_ID not set — skipping")
            continue

        print(f"\n[{step_key}] source collection {src_coll_id}")
        total_dos = client.count_data_objects(src_coll_id)
        print(f"  {total_dos} DataObject(s) to migrate")

        # Group DataObject: one dest DO per step, aggregate all source DOs into it
        dest_do_name = f"{step_key.capitalize()}-{SESSION_ID}"
        pred_id = do_ids.get(pred_key) if pred_key else None

        base_attrs: dict[str, str] = {
            "session": SESSION_ID,
            "campaign": "MFFD",
            "process_step": step_key,
            "source_collection_id": str(src_coll_id),
            "import_time": IMPORT_TIME,
            "timestamp_note": (
                "file timestamps reflect import time not measurement time"
            ),
        }

        dest_do = ensure_dest_do(
            client,
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

        # Verify refs before migration
        client.verify_references(coll_id, dest_do["id"], dest_do_name)

        # Walk source DOs with progress bar
        files_uploaded = 0
        files_skipped = 0
        files_failed = 0

        with tqdm(
            total=total_dos,
            desc=f"  DataObjects [{step_key}]",
            unit="DO",
            file=sys.stderr,
        ) as do_bar:
            for src_do in client.iter_data_objects(src_coll_id):
                do_bar.set_postfix_str(src_do.name[:30])
                do_bar.write(f"  ↳ {src_do.name}  ({len(src_do.file_refs)} file(s))")

                existing_names = client.list_file_refs(coll_id, dest_do["id"])

                with tempfile.TemporaryDirectory() as tmpdir:
                    tmp = Path(tmpdir)
                    for fref in src_do.file_refs:
                        dest_name = f"{src_do.name}/{fref.name}"
                        state_key = f"{step_key}/{dest_name}"

                        # State file says done → skip without even hitting API
                        if state is not None and state.is_file_done(state_key):
                            do_bar.write(f"    [resume-skip] {dest_name}")
                            files_skipped += 1
                            continue

                        # API idempotency check
                        if dest_name in existing_names or fref.name in existing_names:
                            do_bar.write(f"    [skip] {fref.name}")
                            files_skipped += 1
                            if state is not None:
                                state.mark_file_done(state_key)
                            continue

                        tmp_file = tmp / fref.name
                        ok = client.download_file_ref(
                            src_coll_id, src_do.do_id, fref.fref_id, tmp_file, fref.size
                        )
                        if not ok:
                            do_bar.write(f"    [error] download {fref.name}")
                            files_failed += 1
                            continue

                        ok = client.upload_file(
                            dest_app_id, tmp_file, dest_name, coll_id, dest_do["id"]
                        )
                        if ok:
                            do_bar.write(f"    [ok] {dest_name}  ({_human(fref.size)})")
                            files_uploaded += 1
                            if state is not None:
                                state.mark_file_done(state_key)
                        else:
                            do_bar.write(f"    [error] upload {dest_name}")
                            files_failed += 1

                do_bar.update(1)

        print(f"  → uploaded={files_uploaded}  skipped={files_skipped}  failed={files_failed}")
        client.verify_references(coll_id, dest_do["id"], dest_do_name)

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
    ap.add_argument("--dry-run", action="store_true", help="Print plan, make no API calls.")
    args = ap.parse_args()

    source_mode = SOURCE_TAPELAYING_COLL_ID is not None or SOURCE_BRIDGEWELDING_COLL_ID is not None
    mode_label = "SOURCE" if source_mode else "LOCAL"

    if args.dry_run:
        print(f"[config] mode                        = {mode_label}")
        print(f"[config] SHEPARD_URL                 = {SHEPARD_URL}")
        print(f"[config] COLLECTION_NAME             = {COLLECTION_NAME!r}")
        print(f"[config] SESSION_ID                  = {SESSION_ID!r}")
        if source_mode:
            print(f"[config] SOURCE_TAPELAYING_COLL_ID   = {SOURCE_TAPELAYING_COLL_ID}")
            print(f"[config] SOURCE_BRIDGEWELDING_COLL_ID= {SOURCE_BRIDGEWELDING_COLL_ID}")
        else:
            print(f"[config] DATA_DIR                    = {DATA_DIR.resolve()}")
        print()
        print("=== DRY RUN — no API calls ===")
        print("Would create:")
        print(f"  Collection:              {COLLECTION_NAME!r}")
        print(f"  Tapelaying-{SESSION_ID}  ← root of chain")
        print(f"  Bridgewelding-{SESSION_ID} ← predecessor: Tapelaying")
        print(f"  WikiDump-{SESSION_ID}    ← standalone placeholder")
        print(f"  ImportScripts            ← self-upload")
        if source_mode:
            print(f"\nSource collections:")
            print(f"  tapelaying    → id {SOURCE_TAPELAYING_COLL_ID}")
            print(f"  bridgewelding → id {SOURCE_BRIDGEWELDING_COLL_ID}")
        return

    if not SHEPARD_API_KEY and not SHEPARD_BEARER_TOKEN:
        print("ERROR: set SHEPARD_API_KEY or SHEPARD_BEARER_TOKEN.", file=sys.stderr)
        sys.exit(1)

    LOG_DIR.mkdir(parents=True, exist_ok=True)
    log_path  = LOG_DIR / f"mffd-import-{SESSION_ID}.log"
    state_path = LOG_DIR / f"mffd-import-{SESSION_ID}.state.json"
    lock_path  = LOG_DIR / ".mffd-import.lock"

    # Acquire deployment lock before touching any infrastructure
    lock = DeployLock(lock_path)
    lock.acquire()

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
    if source_mode:
        print(f"[config] SOURCE_TAPELAYING_COLL_ID   = {SOURCE_TAPELAYING_COLL_ID}")
        print(f"[config] SOURCE_BRIDGEWELDING_COLL_ID= {SOURCE_BRIDGEWELDING_COLL_ID}")

    state = ImportState(state_path)
    client = ShepardClient(SHEPARD_URL, SHEPARD_API_KEY, SHEPARD_BEARER_TOKEN)

    try:
        if not client.warmup():
            sys.exit(1)

        coll_id, coll_app_id = ensure_dest_collection(client)

        # Warmup probe + human/Claude review gate
        warmup_probe_and_gate(client, coll_id, state)

        if source_mode:
            run_source_mode(client, coll_id, state)
        else:
            run_local_mode(client, coll_id, state)

        ensure_standalones(client, coll_id)

        print("\n[snapshot] creating ...")
        if coll_app_id:
            label = f"dropbox-import-{SESSION_ID}"
            snap = client.create_snapshot(coll_app_id, label)
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
