#!/usr/bin/env python3
"""remote-import.py — bulk-dump a local directory tree into a remote Shepard instance.

Requirements
------------
    pip install requests          # only non-stdlib dependency

Usage
-----
    # Directory mode (auto-detected — no manifest.json in DATA_DIR):
    SHEPARD_URL=https://shepard.nuclide.systems \\
    SHEPARD_API_KEY=<your-key> \\
    COLLECTION_NAME="My Raw Import" \\
    DATA_DIR=/path/to/data \\
    python remote-import.py

    # Manifest mode (auto-detected — DATA_DIR/manifest.json is present):
    SHEPARD_URL=https://shepard.nuclide.systems \\
    SHEPARD_API_KEY=<your-key> \\
    COLLECTION_NAME="My Raw Import" \\
    DATA_DIR=/path/to/data \\
    python remote-import.py

    # Dry-run (validates + prints what would happen, no API calls):
    python remote-import.py --dry-run

Environment variables
---------------------
    SHEPARD_URL       Base URL of the Shepard instance.
                      Default: https://shepard.nuclide.systems
    SHEPARD_API_KEY   X-API-KEY value.  Required (no default).
    COLLECTION_NAME   Name of the target Collection.  Required.
    DATA_DIR          Local directory to import.  Default: current directory.

Modes
-----
Directory mode
    Walks DATA_DIR one level deep.  Each *subdirectory* becomes a DataObject.
    Files directly inside that subdirectory become singleton FileReferences
    attached to that DataObject (via POST /v2/files).

    Files at the top level of DATA_DIR (not inside any subdirectory) are
    attached to a special DataObject named "_root".

    Deeper nesting (DATA_DIR/subdir/nested/file) is flattened: nested files
    are still attached to the first-level subdir's DataObject.  Use manifest
    mode if you need a deeper hierarchy.

Manifest mode
    If DATA_DIR/manifest.json exists the script reads it instead of walking
    the filesystem.  Format:

        {
          "description": "optional collection description",
          "attributes": { "key": "value" },
          "dataObjects": [
            {
              "name": "Run 001",
              "description": "first test run",
              "attributes": { "campaign": "Q3" },
              "files": [
                "relative/path/to/file.csv",
                "another.h5"
              ]
            }
          ]
        }

    All file paths in the manifest are relative to DATA_DIR.

Idempotency
-----------
- Collection: looked up by exact name.  The ?name= filter is substring on the
  server, so results are filtered client-side for exact equality.
- DataObject: looked up by exact name within the Collection (same client-side
  exact-match caveat).
- File: a file is considered present if a FileReference with the same filename
  already appears in the DataObject's reference list (name comparison).
  MD5 verification is intentionally omitted to keep the initial-commit path fast.
  Re-run with different content but the same filename will skip the upload.

API surface used
----------------
  POST /shepard/api/collections                           create Collection
  GET  /shepard/api/collections?name=...                  find by name
  POST /shepard/api/collections/{id}/dataObjects          create DataObject
  GET  /shepard/api/collections/{id}/dataObjects?name=... find by name
  POST /v2/files?parentDataObjectAppId={appId}            upload file (singleton)
  GET  /v2/files/{appId}                                  file metadata (for skip check)

  The /v2/files upload path is the FR1b singleton — one multipart call creates
  the FileReference and stores the bytes in a single round-trip. This is
  deliberately preferred over the v1 three-step dance (create FileContainer →
  upload bytes → link FileReference) because this script's goal is minimum
  friction for an initial commit.

Credential safety
-----------------
  The API key is never echoed in log output.  All headers are redacted in
  error reports.  The key is read once from SHEPARD_API_KEY at startup.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

try:
    import requests
    from requests import Response, Session
except ImportError:
    print(
        "ERROR: 'requests' is not installed.  Run:  pip install requests",
        file=sys.stderr,
    )
    sys.exit(1)

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

SHEPARD_URL = os.environ.get("SHEPARD_URL", "https://shepard.nuclide.systems").rstrip("/")
SHEPARD_API_KEY = os.environ.get("SHEPARD_API_KEY", "")
COLLECTION_NAME = os.environ.get("COLLECTION_NAME", "")
DATA_DIR = Path(os.environ.get("DATA_DIR", "."))

MANIFEST_FILENAME = "manifest.json"

# ---------------------------------------------------------------------------
# Plan dataclasses (used in both dry-run and live modes)
# ---------------------------------------------------------------------------


@dataclass
class FilePlan:
    """One file to upload, attached to a DataObject."""
    local_path: Path
    display_name: str  # shown in progress and used for skip-check


@dataclass
class DataObjectPlan:
    """One DataObject to create (or reuse) with a list of files."""
    name: str
    description: str = ""
    attributes: dict[str, str] = field(default_factory=dict)
    files: list[FilePlan] = field(default_factory=list)


@dataclass
class ImportPlan:
    """The complete import specification derived from filesystem or manifest."""
    collection_name: str
    collection_description: str = ""
    collection_attributes: dict[str, str] = field(default_factory=dict)
    data_objects: list[DataObjectPlan] = field(default_factory=list)

    def total_files(self) -> int:
        return sum(len(do.files) for do in self.data_objects)


# ---------------------------------------------------------------------------
# Plan builders
# ---------------------------------------------------------------------------


def build_directory_plan(data_dir: Path, collection_name: str) -> ImportPlan:
    """Walk data_dir one level deep and build an ImportPlan."""
    plan = ImportPlan(collection_name=collection_name)

    root_files: list[FilePlan] = []
    for item in sorted(data_dir.iterdir()):
        if item.name == MANIFEST_FILENAME:
            continue
        if item.is_file():
            root_files.append(FilePlan(local_path=item, display_name=item.name))
        elif item.is_dir():
            do_plan = DataObjectPlan(name=item.name)
            for f in sorted(item.rglob("*")):
                if f.is_file():
                    # Flatten nested paths: display_name preserves relative path
                    # within the subdir so filenames stay unique on re-runs.
                    rel = f.relative_to(item)
                    do_plan.files.append(FilePlan(local_path=f, display_name=str(rel)))
            plan.data_objects.append(do_plan)

    if root_files:
        # Files at the top level go into a synthetic "_root" DataObject.
        plan.data_objects.insert(0, DataObjectPlan(name="_root", files=root_files))

    return plan


def build_manifest_plan(data_dir: Path, collection_name: str) -> ImportPlan:
    """Read manifest.json and build an ImportPlan."""
    manifest_path = data_dir / MANIFEST_FILENAME
    with manifest_path.open(encoding="utf-8") as fh:
        raw: dict[str, Any] = json.load(fh)

    plan = ImportPlan(
        collection_name=collection_name,
        collection_description=raw.get("description", ""),
        collection_attributes=raw.get("attributes", {}),
    )

    for do_raw in raw.get("dataObjects", []):
        do_plan = DataObjectPlan(
            name=do_raw["name"],
            description=do_raw.get("description", ""),
            attributes=do_raw.get("attributes", {}),
        )
        for rel_path in do_raw.get("files", []):
            full_path = data_dir / rel_path
            do_plan.files.append(
                FilePlan(local_path=full_path, display_name=Path(rel_path).name)
            )
        plan.data_objects.append(do_plan)

    return plan


def build_plan(data_dir: Path, collection_name: str) -> ImportPlan:
    """Auto-detect mode and return the appropriate ImportPlan."""
    manifest = data_dir / MANIFEST_FILENAME
    if manifest.exists():
        print(f"[mode] manifest detected: {manifest}")
        return build_manifest_plan(data_dir, collection_name)
    else:
        print(f"[mode] directory scan: {data_dir}")
        return build_directory_plan(data_dir, collection_name)


# ---------------------------------------------------------------------------
# Dry-run printer
# ---------------------------------------------------------------------------


def dry_run(plan: ImportPlan) -> None:
    """Print what would be done without making any API calls."""
    print()
    print("=== DRY RUN — no changes will be made ===")
    print()
    print(f"Collection:  {plan.collection_name!r}")
    if plan.collection_description:
        print(f"Description: {plan.collection_description!r}")
    if plan.collection_attributes:
        print(f"Attributes:  {plan.collection_attributes}")
    print()

    for do_plan in plan.data_objects:
        print(f"  DataObject: {do_plan.name!r}")
        if do_plan.description:
            print(f"    description: {do_plan.description!r}")
        if do_plan.attributes:
            print(f"    attributes:  {do_plan.attributes}")
        for fp in do_plan.files:
            size = fp.local_path.stat().st_size if fp.local_path.exists() else -1
            size_str = _human_size(size) if size >= 0 else "NOT FOUND"
            print(f"    file: {fp.display_name!r}  ({size_str}  ← {fp.local_path})")
        if not do_plan.files:
            print("    (no files)")
        print()

    print(
        f"Total: {len(plan.data_objects)} DataObject(s), "
        f"{plan.total_files()} file(s)"
    )
    _check_missing_files(plan)


def _check_missing_files(plan: ImportPlan) -> None:
    missing = [
        fp.local_path
        for do in plan.data_objects
        for fp in do.files
        if not fp.local_path.exists()
    ]
    if missing:
        print()
        print(f"WARNING: {len(missing)} file(s) listed in the plan do not exist:")
        for p in missing:
            print(f"  {p}")


def _human_size(n: int) -> str:
    for unit in ("B", "KB", "MB", "GB", "TB"):
        if n < 1024:
            return f"{n:.1f} {unit}"
        n /= 1024  # type: ignore[assignment]
    return f"{n:.1f} PB"


# ---------------------------------------------------------------------------
# HTTP client
# ---------------------------------------------------------------------------


class ShepardClient:
    """Thin wrapper around requests.Session for Shepard API calls.

    The API key is stored once and never emitted in log output.
    HTTP errors are printed with response body but do not abort the import.
    """

    def __init__(self, base_url: str, api_key: str) -> None:
        self._base = base_url
        self._session = Session()
        # Redacted in all __repr__ paths by design — the Session stores it
        # in headers, which we never print.
        self._session.headers.update({"X-API-KEY": api_key})

    # ── Collections ──────────────────────────────────────────────────────────

    def find_collection_by_name(self, name: str) -> dict | None:
        """Return the first Collection with *exactly* this name, or None."""
        resp = self._get(f"{self._base}/shepard/api/collections", params={"name": name})
        if resp is None:
            return None
        items: list[dict] = resp.json()
        for item in items:
            if item.get("name") == name:
                return item
        return None

    def create_collection(self, name: str, description: str = "", attributes: dict | None = None) -> dict | None:
        """Create a Collection and return the server entity, or None on error."""
        body: dict[str, Any] = {"name": name}
        if description:
            body["description"] = description
        if attributes:
            body["attributes"] = attributes
        resp = self._post(f"{self._base}/shepard/api/collections", json=body)
        return resp.json() if resp is not None else None

    # ── DataObjects ───────────────────────────────────────────────────────────

    def find_data_object_by_name(self, collection_id: int, name: str) -> dict | None:
        """Return the DataObject with *exactly* this name inside collection_id."""
        url = f"{self._base}/shepard/api/collections/{collection_id}/dataObjects"
        resp = self._get(url, params={"name": name})
        if resp is None:
            return None
        items: list[dict] = resp.json()
        for item in items:
            if item.get("name") == name:
                return item
        return None

    def create_data_object(
        self,
        collection_id: int,
        name: str,
        description: str = "",
        attributes: dict | None = None,
    ) -> dict | None:
        """Create a DataObject and return the server entity, or None on error."""
        body: dict[str, Any] = {"name": name}
        if description:
            body["description"] = description
        if attributes:
            body["attributes"] = attributes
        url = f"{self._base}/shepard/api/collections/{collection_id}/dataObjects"
        resp = self._post(url, json=body)
        return resp.json() if resp is not None else None

    # ── Files (v2 singleton) ─────────────────────────────────────────────────

    def list_files_for_data_object(self, data_object_app_id: str) -> list[str]:
        """Return display names of FileReferences already attached to this DataObject.

        Uses GET /v2/files/{appId} to enumerate — but the v2 surface only exposes
        singleton GET by appId, not a list.  The actual file list is on the DataObject
        response (referenceIds), but referenceIds are OGM longs pointing to *all*
        reference types (timeseries, structured, file, …) and we'd need to resolve each.

        Pragmatic approach for an initial-commit script: we DON'T fetch existing
        files for the skip-check because the v1 fileReferences list endpoint
        (GET /shepard/api/collections/{cid}/dataObjects/{doid}/fileReferences)
        requires the legacy OGM collectionId + dataObjectId pair — which we have.
        We use that endpoint for the skip-check.
        """
        # Not used directly — see list_file_references_v1 instead.
        return []

    def list_file_references_v1(self, collection_id: int, data_object_id: int) -> list[dict]:
        """List FileReferences for a DataObject using the v1 endpoint."""
        url = (
            f"{self._base}/shepard/api/collections/{collection_id}"
            f"/dataObjects/{data_object_id}/fileReferences"
        )
        resp = self._get(url)
        if resp is None:
            return []
        return resp.json()

    def upload_file(self, file_path: Path, parent_data_object_app_id: str, display_name: str) -> dict | None:
        """Upload a file as a v2 singleton FileReference.

        POST /v2/files?parentDataObjectAppId={appId}
        Form field: file
        """
        url = f"{self._base}/v2/files"
        params = {"parentDataObjectAppId": parent_data_object_app_id, "name": display_name}
        try:
            with file_path.open("rb") as fh:
                resp = self._post_multipart(url, params=params, file_field="file", file_obj=fh, filename=file_path.name)
        except OSError as exc:
            print(f"  [error] could not open {file_path}: {exc}")
            return None
        return resp.json() if resp is not None else None

    # ── Low-level helpers ─────────────────────────────────────────────────────

    def _get(self, url: str, params: dict | None = None) -> Response | None:
        try:
            resp = self._session.get(url, params=params, timeout=30)
            if not resp.ok:
                self._log_error("GET", url, resp)
                return None
            return resp
        except requests.RequestException as exc:
            print(f"  [network error] GET {_redact_url(url)}: {exc}")
            return None

    def _post(self, url: str, json: dict | None = None) -> Response | None:
        try:
            resp = self._session.post(url, json=json, timeout=30)
            if not resp.ok:
                self._log_error("POST", url, resp)
                return None
            return resp
        except requests.RequestException as exc:
            print(f"  [network error] POST {_redact_url(url)}: {exc}")
            return None

    def _post_multipart(
        self,
        url: str,
        params: dict,
        file_field: str,
        file_obj: Any,
        filename: str,
        timeout: int = 300,
    ) -> Response | None:
        files = {file_field: (filename, file_obj)}
        try:
            resp = self._session.post(url, params=params, files=files, timeout=timeout)
            if not resp.ok:
                self._log_error("POST (multipart)", url, resp)
                return None
            return resp
        except requests.RequestException as exc:
            print(f"  [network error] POST {_redact_url(url)}: {exc}")
            return None

    @staticmethod
    def _log_error(method: str, url: str, resp: Response) -> None:
        # Never print headers (which contain the API key).
        try:
            body = resp.text[:500]
        except Exception:
            body = "<unreadable>"
        print(
            f"  [http error] {method} {_redact_url(url)} → "
            f"HTTP {resp.status_code}: {body}"
        )


def _redact_url(url: str) -> str:
    """Strip any ?api_key= style query params just in case, keep the path."""
    # The API key is sent as a header, not in the URL; this is belt-and-braces.
    return url.split("?")[0] if "api_key" in url.lower() else url


# ---------------------------------------------------------------------------
# Live import executor
# ---------------------------------------------------------------------------


def run_import(plan: ImportPlan, client: ShepardClient) -> None:
    """Execute the plan against the live Shepard instance."""
    total_files = plan.total_files()
    uploaded = 0
    skipped = 0
    errors = 0

    def _progress(current: int, total: int, label: str) -> None:
        pct = int(100 * current / total) if total > 0 else 100
        print(f"  [{pct:3d}%] {label}")

    # ── 1. Collection ─────────────────────────────────────────────────────────
    print(f"\n[collection] looking up {plan.collection_name!r} ...")
    collection = client.find_collection_by_name(plan.collection_name)
    if collection:
        print(f"[collection] found (id={collection['id']}, appId={collection.get('appId')})")
    else:
        print(f"[collection] not found — creating ...")
        collection = client.create_collection(
            plan.collection_name,
            description=plan.collection_description,
            attributes=plan.collection_attributes or None,
        )
        if collection is None:
            print("[collection] FAILED to create.  Aborting.")
            sys.exit(1)
        print(f"[collection] created (id={collection['id']}, appId={collection.get('appId')})")

    collection_id: int = collection["id"]

    # ── 2. DataObjects + files ────────────────────────────────────────────────
    total_dos = len(plan.data_objects)
    for do_idx, do_plan in enumerate(plan.data_objects, start=1):
        print(
            f"\n[dataobject {do_idx}/{total_dos}] {do_plan.name!r} "
            f"({len(do_plan.files)} file(s))"
        )

        # Find or create DataObject
        do = client.find_data_object_by_name(collection_id, do_plan.name)
        if do:
            print(f"  [skip] DataObject already exists (id={do['id']}, appId={do.get('appId')})")
        else:
            do = client.create_data_object(
                collection_id,
                do_plan.name,
                description=do_plan.description,
                attributes=do_plan.attributes or None,
            )
            if do is None:
                print("  [error] failed to create DataObject — skipping all its files")
                errors += len(do_plan.files)
                continue
            print(f"  [created] DataObject id={do['id']}  appId={do.get('appId')}")

        do_app_id: str | None = do.get("appId")
        do_legacy_id: int = do["id"]

        if do_app_id is None:
            print(
                "  [warning] DataObject has no appId (pre-L2a row). "
                "File uploads via /v2/files require appId — skipping files for this DO."
            )
            errors += len(do_plan.files)
            continue

        # Build set of already-present file names for the skip-check.
        existing_refs = client.list_file_references_v1(collection_id, do_legacy_id)
        # v1 FileReference has a "name" field from BasicReferenceIO (name of
        # the reference, which defaults to the filename in the upload path).
        existing_names: set[str] = {ref.get("name", "") for ref in existing_refs}

        # Upload each file
        for fp in do_plan.files:
            if not fp.local_path.exists():
                print(f"  [skip] {fp.display_name!r} — local file not found at {fp.local_path}")
                errors += 1
                continue

            if fp.display_name in existing_names:
                print(f"  [skip] {fp.display_name!r} — already present in DataObject")
                skipped += 1
                uploaded += 1  # count toward progress
                _progress(uploaded, total_files, fp.display_name)
                continue

            size_str = _human_size(fp.local_path.stat().st_size)
            print(f"  [upload] {fp.display_name!r}  ({size_str})")
            result = client.upload_file(fp.local_path, do_app_id, fp.display_name)
            uploaded += 1
            if result:
                _progress(uploaded, total_files, fp.display_name)
            else:
                print(f"  [error] upload failed for {fp.display_name!r}")
                errors += 1

    # ── 3. Summary ────────────────────────────────────────────────────────────
    print()
    print("=== Import complete ===")
    print(f"  DataObjects: {total_dos}")
    print(f"  Files processed: {uploaded}")
    print(f"  Skipped (already present): {skipped}")
    print(f"  Errors: {errors}")
    if errors:
        print("  (re-run the script to retry failed items — it is idempotent)")


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    p.add_argument(
        "--dry-run",
        action="store_true",
        help="Validate and print what would be done; make no API calls.",
    )
    return p.parse_args()


def validate_env(dry_run_mode: bool) -> None:
    """Fail fast on missing required config."""
    errors: list[str] = []

    if not COLLECTION_NAME:
        errors.append("COLLECTION_NAME is required.")
    if not DATA_DIR.is_dir():
        errors.append(f"DATA_DIR={DATA_DIR!r} does not exist or is not a directory.")
    if not dry_run_mode and not SHEPARD_API_KEY:
        errors.append("SHEPARD_API_KEY is required (not needed for --dry-run).")

    if errors:
        for e in errors:
            print(f"[config error] {e}", file=sys.stderr)
        sys.exit(1)


def main() -> None:
    args = parse_args()
    validate_env(dry_run_mode=args.dry_run)

    print(f"[config] SHEPARD_URL    = {SHEPARD_URL}")
    print(f"[config] COLLECTION_NAME= {COLLECTION_NAME!r}")
    print(f"[config] DATA_DIR       = {DATA_DIR.resolve()}")
    if args.dry_run:
        print("[config] SHEPARD_API_KEY= (not required for dry-run)")
    else:
        # Show length hint without exposing the key.
        key_hint = f"<set, {len(SHEPARD_API_KEY)} chars>" if SHEPARD_API_KEY else "<NOT SET>"
        print(f"[config] SHEPARD_API_KEY= {key_hint}")

    plan = build_plan(DATA_DIR, COLLECTION_NAME)

    if args.dry_run:
        dry_run(plan)
        return

    client = ShepardClient(SHEPARD_URL, SHEPARD_API_KEY)
    run_import(plan, client)


if __name__ == "__main__":
    main()
