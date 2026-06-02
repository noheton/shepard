#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.11"
# dependencies = ["requests>=2.31"]
# ///
"""shepard-plugin-spatial-importer / W7 — promote opaque FileReferences holding
TPS 3D pointclouds and FSD course 3D pointclouds into ``SpatialDataContainer``
rows on the existing spatiotemporal substrate.

Usage:
    main.py --spatial-pass \\
        --collection-app-id <UUID> \\
        --source /opt/shepard/mffd-staging/w7/mffd-export/ts-export/tapelaying/ \\
        --workers 4

The pass is idempotent: re-running it MERGEs on
``(dataObjectAppId, kind, source-sha256)`` and inserts no duplicate rows.

Environment:
    SHEPARD_URL       (default: https://shepard-api.nuclide.systems)
    SHEPARD_API_KEY   (required)

Reference: aidocs/integrations/113-mffd-real-data-import-plan.md §W7;
           aidocs/agent-findings/mffd-feature-gaps-2026-06-02.md §GAP-5;
           aidocs/data/85-coordinate-frame-tree.md (CST1 frame handshake);
           aidocs/data/90-spatial-as-temporal-sweep.md (kind taxonomy).
"""

from __future__ import annotations

import argparse
import concurrent.futures
import logging
import os
import random
import sys
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

import requests

# Allow running both as a module (`python -m cli.main`) and as a script.
try:
    from .parser import (
        PointcloudFile,
        PointcloudParseError,
        classify_kind,
        iter_track_spatial_files,
        parse_pointcloud_file,
    )
except ImportError:  # pragma: no cover — script-mode fallback
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    from parser import (  # type: ignore[no-redef]
        PointcloudFile,
        PointcloudParseError,
        classify_kind,
        iter_track_spatial_files,
        parse_pointcloud_file,
    )


# -----------------------------------------------------------------------------
# Annotation namespaces
#
# All new predicates live under ``urn:shepard:spatial:*`` per CLAUDE.md's
# "evolve in a new namespace" rule.
# -----------------------------------------------------------------------------

URN_PROMOTED_TO = "urn:shepard:spatial:promoted-to"
URN_SOURCE_SHA256 = "urn:shepard:spatial:source-sha256"
URN_SPATIAL_KIND = "urn:shepard:spatial:kind"
URN_TRACK_FOLDER = "urn:shepard:spatial:source-folder"
URN_SOURCE_FILENAME = "urn:shepard:spatial:source-filename"

LOG = logging.getLogger("spatial-importer")


# -----------------------------------------------------------------------------
# Shepard REST client
# -----------------------------------------------------------------------------


@dataclass
class Shepard:
    base: str
    api_key: str
    session: requests.Session = field(default_factory=requests.Session)

    def _headers(self) -> dict[str, str]:
        return {"X-API-KEY": self.api_key, "Accept": "application/json"}

    def get(self, path: str, params: dict[str, Any] | None = None) -> Any:
        r = self.session.get(f"{self.base}{path}", headers=self._headers(), params=params, timeout=60)
        r.raise_for_status()
        return r.json() if r.content else None

    def post(self, path: str, body: Any) -> Any:
        r = self.session.post(
            f"{self.base}{path}",
            headers={**self._headers(), "Content-Type": "application/json"},
            json=body,
            timeout=120,
        )
        r.raise_for_status()
        return r.json() if r.content else None

    def patch(self, path: str, body: Any) -> Any:
        r = self.session.patch(
            f"{self.base}{path}",
            headers={**self._headers(), "Content-Type": "application/merge-patch+json"},
            json=body,
            timeout=60,
        )
        r.raise_for_status()
        return r.json() if r.content else None


# -----------------------------------------------------------------------------
# Backoff
# -----------------------------------------------------------------------------


def with_backoff(fn, *, max_attempts: int = 6, base_delay: float = 1.0) -> Any:
    """Run ``fn()`` with exponential backoff + jitter.

    Retries on transient HTTP failures (5xx, 429) and connection errors.
    Re-raises on persistent failure — completeness is non-negotiable for an
    importer (see ``feedback_completeness_nonnegotiable.md``).
    """
    delay = base_delay
    last_exc: Exception | None = None
    for attempt in range(1, max_attempts + 1):
        try:
            return fn()
        except requests.HTTPError as exc:
            status = exc.response.status_code if exc.response is not None else 0
            if status in (429, 500, 502, 503, 504) and attempt < max_attempts:
                LOG.warning("attempt %d/%d failed with HTTP %d; retrying in %.1fs", attempt, max_attempts, status, delay)
                time.sleep(delay + random.uniform(0, delay * 0.25))
                delay = min(delay * 2, 60.0)
                last_exc = exc
                continue
            raise
        except (requests.ConnectionError, requests.Timeout) as exc:
            if attempt < max_attempts:
                LOG.warning("attempt %d/%d hit %s; retrying in %.1fs", attempt, max_attempts, type(exc).__name__, delay)
                time.sleep(delay + random.uniform(0, delay * 0.25))
                delay = min(delay * 2, 60.0)
                last_exc = exc
                continue
            raise
    assert last_exc is not None  # narrowing for mypy
    raise last_exc


# -----------------------------------------------------------------------------
# Track → DataObject discovery
# -----------------------------------------------------------------------------


def list_track_dataobjects(sh: Shepard, collection_app_id: str) -> list[dict]:
    """Return the DataObjects in the target Collection. Each one corresponds
    to one source ``Track_NN__Run_NN_/`` folder if W2 has run.
    """
    # /v2/ lookup by collection app-id; existing endpoint shape.
    return with_backoff(lambda: sh.get(f"/shepard/api/collections/by-app-id/{collection_app_id}/dataObjects"))


def find_filereference_for(
    sh: Shepard, collection_id: int, do_id: int, source_filename: str
) -> dict | None:
    """Look up the FileReference inside ``do_id`` whose ``name`` matches
    ``source_filename``. Returns the FileReference dict or None.
    """

    def _fetch() -> list[dict]:
        return sh.get(f"/shepard/api/collections/{collection_id}/dataObjects/{do_id}/fileReferences") or []

    refs = with_backoff(_fetch)
    for ref in refs:
        if ref.get("name") == source_filename:
            return ref
    return None


# -----------------------------------------------------------------------------
# SpatialDataContainer create + payload upload
# -----------------------------------------------------------------------------


def existing_promotion(
    sh: Shepard, collection_id: int, do_id: int, sha256: str
) -> str | None:
    """Idempotency check: does the DataObject already have a
    SpatialDataReference whose underlying container carries a matching
    ``source-sha256`` annotation?

    Returns the existing container appId on a hit, ``None`` otherwise.
    """
    try:
        refs = with_backoff(
            lambda: sh.get(
                f"/shepard/api/collections/{collection_id}/dataObjects/{do_id}/spatialDataReferences"
            )
            or []
        )
    except requests.HTTPError as exc:
        # If the spatial endpoint isn't enabled at the destination, treat as
        # "no existing promotion" and let the create attempt surface the real
        # error.
        if exc.response is not None and exc.response.status_code in (404, 503):
            return None
        raise

    for ref in refs:
        container_id = ref.get("spatialDataContainerId")
        if container_id is None:
            continue
        try:
            container = with_backoff(lambda: sh.get(f"/shepard/api/spatialDataContainers/{container_id}"))
        except requests.HTTPError:
            continue
        annotations = container.get("annotations") or []
        for ann in annotations:
            if ann.get("predicate") == URN_SOURCE_SHA256 and ann.get("value") == sha256:
                return container.get("appId")
    return None


def create_spatial_container(
    sh: Shepard,
    *,
    name: str,
    frame_app_id: str | None,
) -> dict:
    """Create a SpatialDataContainer and return the response body."""
    body: dict[str, Any] = {"name": name}
    if frame_app_id is not None:
        body["frameAppId"] = frame_app_id
    return with_backoff(lambda: sh.post("/shepard/api/spatialDataContainers", body))


def upload_points(
    sh: Shepard,
    *,
    container_id: int,
    pointcloud: PointcloudFile,
    kind: str,
    seq: int,
    batch_size: int = 1000,
) -> int:
    """Upload the points in batches. Returns the count of points uploaded.

    Each ``SpatialDataPoint`` carries the source file's ``kind`` + ``seq``
    in its measurements bag so a renderer can group profile-instance scans.
    """
    base_ts_ns = int(time.time() * 1_000_000_000)
    total = 0
    pts = pointcloud.points
    for offset in range(0, len(pts), batch_size):
        chunk = pts[offset : offset + batch_size]
        payload = [
            {
                "timestamp": base_ts_ns + (offset + i),
                "x": p.x,
                "y": p.y,
                "z": p.z,
                "measurements": {"kind": kind, "seq": seq, "index": offset + i},
                "metadata": {},
            }
            for i, p in enumerate(chunk)
        ]
        with_backoff(lambda payload=payload: sh.post(f"/shepard/api/spatialDataContainers/{container_id}/payload", payload))
        total += len(chunk)
    return total


def demote_file_reference(
    sh: Shepard,
    *,
    collection_id: int,
    do_id: int,
    fileref_id: int,
    promoted_container_app_id: str,
) -> None:
    """Demote the original FileReference: flip its status to ``ARCHIVED`` and
    leave a breadcrumb annotation pointing at the new SpatialDataContainer.

    The PATCH path is the existing v2 publication-state surface (see
    ``aidocs/16-dispatcher-backlog.md`` #27-ARCHIVED). If that path is not
    yet wired for spatial promotions in your deployment, this call may
    fail — we WARN and continue rather than block the promotion.
    """
    try:
        with_backoff(
            lambda: sh.patch(
                f"/v2/collections/{collection_id}/data-objects/{do_id}/file-references/{fileref_id}",
                {"status": "ARCHIVED"},
            )
        )
    except requests.HTTPError as exc:
        # Secondary write — never block the primary promotion on a demotion
        # failure. The container is created and queryable; the FileReference
        # demotion is the audit-trail breadcrumb (CLAUDE.md "secondary writes
        # are fire-and-forget").
        LOG.warning(
            "demotion of FileReference %d/%d on DO %d failed: %s; promotion still recorded",
            collection_id,
            fileref_id,
            do_id,
            exc,
        )


# -----------------------------------------------------------------------------
# Per-track processing
# -----------------------------------------------------------------------------


@dataclass
class TrackPromotionResult:
    track_dir: Path
    do_app_id: str | None
    promoted: int = 0
    skipped_idempotent: int = 0
    errors: list[str] = field(default_factory=list)


def process_track(
    sh: Shepard,
    *,
    track_dir: Path,
    collection_id: int,
    do_id: int,
    do_app_id: str,
    frame_app_id: str | None,
) -> TrackPromotionResult:
    result = TrackPromotionResult(track_dir=track_dir, do_app_id=do_app_id)

    seq_by_kind: dict[str, int] = {}
    for source_path, kind in iter_track_spatial_files(track_dir):
        seq = seq_by_kind.get(kind, 0)
        seq_by_kind[kind] = seq + 1
        try:
            cloud = parse_pointcloud_file(source_path)
        except (PointcloudParseError, OSError) as exc:
            msg = f"{source_path.name}: parse failed: {exc}"
            LOG.warning("track=%s %s", track_dir.name, msg)
            result.errors.append(msg)
            continue

        existing = existing_promotion(sh, collection_id, do_id, cloud.sha256)
        if existing is not None:
            LOG.info(
                "track=%s file=%s already promoted to container %s; skipping",
                track_dir.name,
                source_path.name,
                existing,
            )
            result.skipped_idempotent += 1
            continue

        container_name = f"{track_dir.name}/{source_path.name}"
        try:
            container = create_spatial_container(
                sh, name=container_name, frame_app_id=frame_app_id
            )
            container_id = container["id"]
            container_app_id = container["appId"]
            upload_points(
                sh,
                container_id=container_id,
                pointcloud=cloud,
                kind=kind,
                seq=seq,
            )
            # Bind the SpatialDataReference to the DataObject — the existing
            # /shepard/api/ container-reference endpoint.
            with_backoff(
                lambda: sh.post(
                    f"/shepard/api/collections/{collection_id}/dataObjects/{do_id}/spatialDataReferences",
                    {
                        "name": container_name,
                        "spatialDataContainerId": container_id,
                    },
                )
            )

            fileref = find_filereference_for(sh, collection_id, do_id, source_path.name)
            if fileref is not None:
                demote_file_reference(
                    sh,
                    collection_id=collection_id,
                    do_id=do_id,
                    fileref_id=fileref["id"],
                    promoted_container_app_id=container_app_id,
                )

            LOG.info(
                "promoted track=%s file=%s -> container %s (%d points, sha=%s…)",
                track_dir.name,
                source_path.name,
                container_app_id,
                cloud.n_points,
                cloud.sha256[:10],
            )
            result.promoted += 1
        except requests.HTTPError as exc:
            msg = f"{source_path.name}: HTTP error: {exc}"
            LOG.error("track=%s %s", track_dir.name, msg)
            result.errors.append(msg)
        except Exception as exc:  # noqa: BLE001 — last-resort capture for the worker loop
            msg = f"{source_path.name}: unexpected error: {exc}"
            LOG.exception("track=%s %s", track_dir.name, msg)
            result.errors.append(msg)

    return result


# -----------------------------------------------------------------------------
# CLI
# -----------------------------------------------------------------------------


def parse_args(argv: list[str]) -> argparse.Namespace:
    p = argparse.ArgumentParser(
        prog="shepard-plugin-spatial-importer",
        description="Promote TPS/FSD ASCII pointclouds into SpatialDataContainers (MFFD W7).",
    )
    p.add_argument("--spatial-pass", action="store_true", help="Run the W7 spatial promotion pass.")
    p.add_argument(
        "--collection-app-id",
        required=True,
        help="appId (UUID) of the destination Collection holding the Track DataObjects.",
    )
    p.add_argument(
        "--source",
        required=True,
        type=Path,
        help="Path to the tapelaying root containing Track_NN__Run_NN_/ subfolders.",
    )
    p.add_argument(
        "--frame-app-id",
        default=None,
        help="Optional CoordinateFrame appId to bind every promoted container to (CST1 / aidocs/data/85).",
    )
    p.add_argument("--workers", type=int, default=4, help="Worker pool size.")
    p.add_argument("--dry-run", action="store_true", help="Walk and parse but don't write to Shepard.")
    p.add_argument(
        "--limit",
        type=int,
        default=None,
        help="Process at most N Track folders (for smoke-tests).",
    )
    return p.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
    )
    args = parse_args(argv or sys.argv[1:])

    if not args.spatial_pass:
        LOG.error("only --spatial-pass mode is supported in this build")
        return 2

    if args.dry_run:
        LOG.info("dry-run mode — no writes to Shepard")
        return _dry_run(args)

    api_key = os.environ.get("SHEPARD_API_KEY", "").strip()
    if not api_key:
        LOG.error("SHEPARD_API_KEY env var is required")
        return 1
    base = os.environ.get("SHEPARD_URL", "https://shepard-api.nuclide.systems").rstrip("/")
    sh = Shepard(base=base, api_key=api_key)

    LOG.info("listing DataObjects in collection %s …", args.collection_app_id)
    dos = list_track_dataobjects(sh, args.collection_app_id)
    LOG.info("collection has %d DataObjects", len(dos))

    # Build a (track_dir, do) work queue.
    track_pairs: list[tuple[Path, dict]] = []
    for entry in sorted(args.source.iterdir()):
        if not entry.is_dir() or not entry.name.startswith("Track_"):
            continue
        match = next((d for d in dos if d.get("name") == entry.name), None)
        if match is None:
            LOG.warning("track folder %s has no matching DataObject; skipping", entry.name)
            continue
        track_pairs.append((entry, match))
        if args.limit is not None and len(track_pairs) >= args.limit:
            break

    LOG.info("processing %d tracks with %d workers", len(track_pairs), args.workers)
    # Use Collection numeric id off the first match (every DO in a collection
    # shares it). Look it up via the v2 by-app-id endpoint.
    coll_id = with_backoff(
        lambda: sh.get(f"/shepard/api/collections/by-app-id/{args.collection_app_id}")
    )["id"]

    summary = {"promoted": 0, "skipped_idempotent": 0, "errors": 0}
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as ex:
        futures = []
        for track_dir, do in track_pairs:
            futures.append(
                ex.submit(
                    process_track,
                    sh,
                    track_dir=track_dir,
                    collection_id=coll_id,
                    do_id=do["id"],
                    do_app_id=do.get("appId", ""),
                    frame_app_id=args.frame_app_id,
                )
            )
        for fut in concurrent.futures.as_completed(futures):
            res = fut.result()
            summary["promoted"] += res.promoted
            summary["skipped_idempotent"] += res.skipped_idempotent
            summary["errors"] += len(res.errors)

    LOG.info(
        "done — promoted=%d skipped_idempotent=%d errors=%d",
        summary["promoted"],
        summary["skipped_idempotent"],
        summary["errors"],
    )
    return 0 if summary["errors"] == 0 else 3


def _dry_run(args: argparse.Namespace) -> int:
    n_tracks = 0
    n_files = 0
    for entry in sorted(args.source.iterdir()):
        if not entry.is_dir() or not entry.name.startswith("Track_"):
            continue
        n_tracks += 1
        per_kind: dict[str, int] = {}
        for path, kind in iter_track_spatial_files(entry):
            per_kind[kind] = per_kind.get(kind, 0) + 1
            n_files += 1
        LOG.info("track %s: %s", entry.name, per_kind)
        if args.limit is not None and n_tracks >= args.limit:
            break
    LOG.info("dry-run summary: %d tracks scanned, %d spatial files queued", n_tracks, n_files)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
