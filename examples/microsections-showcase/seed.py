"""Microsections showcase — seed script.

Creates the **Microsections — PH2940 Composite Cross-Sections** Collection
and 7 sample DataObjects (PH2940-01 … PH2940-07), each carrying a
metallographic micrograph (`.tif`) plus the paired Jupyter analysis
notebook (`.ipynb`).  Sample PH2940-01 is **dual-bound** with two
notebooks — a carbon-fiber and a flax-fiber (`_flachs`) variant —
representing a comparative bio-composite study.

The dataset is real research data (not synthetic) — polished
metallographic cross-sections of fiber-reinforced thermoplastic
composites, used in the PH2940 sample series.  The notebooks compute
**Fiber Volume Fraction + Porosity** via 3-peak histogram segmentation
(voids ≈ 3, resin ≈ 97, fibers ≈ 152) + watershed / random-walker — the
canonical EN 9100 / aerospace-composite quality metrics.

This seed is intentionally minimal — it lays the structural skeleton
in shepard so that the upcoming JupyterHub plugin (J2) has a real
dataset to attach to.  The full FVF results table (per-sample fiber%,
matrix%, void%) is **not** seeded here; that becomes the J2 plugin's
acceptance test (notebook fires → results land back as
SemanticAnnotations on the sample DO).

Usage::

    python3 seed.py --host https://shepard-api.nuclide.systems \\
                    --apikey <token> \\
                    [--raw-data ./raw-data]

Idempotent — by-name lookup before create, safe to re-run.  ``--reset``
deletes and recreates the Collection from scratch.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from pathlib import Path


# ---------------------------------------------------------------------------
# Constants

COLLECTION_NAME = "Microsections — PH2940 Composite Cross-Sections"
COLLECTION_DESCRIPTION = (
    "Polished metallographic micrographs of fiber-reinforced thermoplastic "
    "composite samples (PH2940 series). Seven samples (PH2940-01 through "
    "PH2940-07); PH2940-01 is dual-bound with a carbon-fiber and a "
    "flax-fiber (`_flachs`) analysis notebook for the comparative "
    "biocomposite study. Each sample carries a 3072×2304 px 8-bit RGB "
    "TIFF micrograph + the paired Jupyter analysis notebook computing "
    "Fiber Volume Fraction + Porosity via 3-peak histogram segmentation. "
    "This is the JupyterHub-integration showcase (task J2 in `aidocs/16`)."
)

# Per-sample analysis variants.  Sample 01 has two notebooks (carbon vs flax);
# samples 02-07 each have a single notebook.
SAMPLE_VARIANTS: list[tuple[str, str, str]] = [
    # (sample_id,         notebook_filename,                  fiber_type)
    ("PH2940-01-carbon",  "PH2940-01_carbon.ipynb",          "carbon"),
    ("PH2940-01-flachs",  "PH2940-01_flachs.ipynb",          "flax"),
    ("PH2940-02",         "PH2940-02.ipynb",                 "carbon"),
    ("PH2940-03",         "PH2940-03.ipynb",                 "carbon"),
    ("PH2940-04",         "PH2940-04.ipynb",                 "carbon"),
    ("PH2940-05",         "PH2940-05.ipynb",                 "carbon"),
    ("PH2940-06",         "PH2940-06.ipynb",                 "carbon"),
    ("PH2940-07",         "PH2940-07.ipynb",                 "carbon"),
]

SAMPLE_ATTRIBUTES_COMMON = {
    "sample_series":      "PH2940",
    "specimen_kind":      "polished-microsection",
    "imaging_method":     "optical-microscopy",
    "image_format":       "TIFF-uncompressed-RGB-3072x2304",
    "analysis_method":    "histogram-3peak-segmentation",
    "analysis_kit":       "scikit-image,scipy,numpy,pandas",
    "phases_detected":    "voids,resin-matrix,fibers",
    "metric_computed":    "fiber-volume-fraction,porosity",
    "research_domain":    "fiber-reinforced-composites",
    "audit_relevance":    "EN-9100",
}


# ---------------------------------------------------------------------------
# Minimal HTTP helpers (no SDK dependency)


def _log(status: str, name: str, kind: str = "", extra: str = "") -> None:
    """Render `STATUS  name  (kind, extra)` lines so the seed output is
    grep-friendly in CI."""
    tail = ""
    if kind or extra:
        tail = f" ({kind}" + (f", {extra}" if extra else "") + ")"
    print(f"{status:<6} {name}{tail}", flush=True)


@dataclass
class Api:
    host: str   # e.g. https://shepard-api.nuclide.systems
    apikey: str

    def _url(self, path: str) -> str:
        return f"{self.host.rstrip('/')}{path}"

    def _req(
        self, method: str, path: str, *, json_body=None,
        params=None, accept="application/json",
        extra_headers=None,
    ):
        url = self._url(path)
        if params:
            url = f"{url}?{urllib.parse.urlencode(params)}"
        headers = {"X-API-KEY": self.apikey, "Accept": accept}
        if extra_headers:
            headers.update(extra_headers)
        data = None
        if json_body is not None:
            data = json.dumps(json_body).encode("utf-8")
            headers["Content-Type"] = "application/json"
        req = urllib.request.Request(url, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=60) as resp:
                body = resp.read()
                if not body:
                    return None
                if "application/json" in resp.headers.get("Content-Type", ""):
                    return json.loads(body)
                return body
        except urllib.error.HTTPError as e:
            raise RuntimeError(f"HTTP {e.code} on {method} {path}: {e.read()[:200]!r}") from e

    def get(self, path, **kw):  return self._req("GET", path, **kw)
    def post(self, path, **kw): return self._req("POST", path, **kw)
    def put(self, path, **kw):  return self._req("PUT", path, **kw)
    def delete(self, path):     return self._req("DELETE", path)

    def upload_singleton_file(
        self,
        parent_data_object_app_id: str,
        name: str,
        file_path: Path,
        content_type: str,
    ) -> dict:
        """Upload a file as a FR1b singleton FileReference attached
        directly to a DataObject. Returns the FileReferenceV2IO.

        Uses POST /v2/files?parentDataObjectAppId=...&name=... — no
        FileContainer middle-man required."""
        boundary = "----shepardseedmicrosections"
        qs = urllib.parse.urlencode({
            "parentDataObjectAppId": parent_data_object_app_id,
            "name": name,
        })
        url = self._url(f"/v2/files?{qs}")
        header = (
            f"--{boundary}\r\n"
            f'Content-Disposition: form-data; name="file"; '
            f'filename="{name}"\r\n'
            f"Content-Type: {content_type}\r\n\r\n"
        ).encode("utf-8")
        footer = f"\r\n--{boundary}--\r\n".encode("utf-8")
        body = header + file_path.read_bytes() + footer
        headers = {
            "X-API-KEY": self.apikey,
            "Accept": "application/json",
            "Content-Type": f"multipart/form-data; boundary={boundary}",
            "Content-Length": str(len(body)),
        }
        req = urllib.request.Request(url, data=body, headers=headers, method="POST")
        with urllib.request.urlopen(req, timeout=300) as resp:
            return json.loads(resp.read())


# ---------------------------------------------------------------------------
# Idempotent lookup-or-create helpers


def find_or_create_collection(api: Api, *, reset: bool) -> dict:
    listing = api.get("/v2/collections", params={"pageSize": 200})
    rows = listing if isinstance(listing, list) else listing.get("content", [])
    for c in rows:
        if c.get("name") == COLLECTION_NAME:
            if reset:
                api.delete(f"/v2/collections/{c['appId']}")
                _log("RESET", COLLECTION_NAME, "Collection")
                break
            _log("SKIP", COLLECTION_NAME, "Collection (exists)", c["appId"])
            return c
    body = {"name": COLLECTION_NAME, "description": COLLECTION_DESCRIPTION}
    created = api.post("/v2/collections", json_body=body)
    _log("OK", COLLECTION_NAME, "Collection", created["appId"])
    return created


def find_or_create_data_object(
    api: Api,
    collection_app_id: str,
    name: str,
    *,
    attributes: dict,
) -> dict:
    listing = api.get(
        f"/v2/collections/{collection_app_id}/data-objects",
        params={"pageSize": 200},
    )
    rows = listing if isinstance(listing, list) else listing.get("content", [])
    for do in rows:
        if do.get("name") == name:
            _log("SKIP", name, "DataObject (exists)", do["appId"])
            return do
    created = api.post(
        f"/v2/collections/{collection_app_id}/data-objects",
        json_body={"name": name, "attributes": attributes},
    )
    _log("OK", name, "DataObject", created["appId"])
    return created


def upload_singleton_if_absent(
    api: Api,
    collection_app_id: str,
    data_object_app_id: str,
    name: str,
    file_path: Path,
    content_type: str,
    *,
    expected_ref_count: int = 2,
) -> dict | None:
    """Upload a singleton FileReference attached to the DataObject.

    Idempotent: probes the DataObject for `referenceIds.length >=
    expected_ref_count` and skips the upload when the DataObject is
    already fully populated. The v2 DataObject view returns numeric
    `referenceIds` only (no inline names), so we use the count as a
    coarse but reliable signal.
    """
    do = api.get(
        f"/v2/collections/{collection_app_id}/data-objects/{data_object_app_id}"
    )
    existing_count = len((do or {}).get("referenceIds", []) or [])
    if existing_count >= expected_ref_count:
        _log(
            "SKIP", name,
            f"FileReference (DataObject already has {existing_count} refs)",
        )
        return None
    created = api.upload_singleton_file(
        data_object_app_id, name, file_path, content_type
    )
    _log("OK", name, "FileReference", created.get("appId", ""))
    return created


# ---------------------------------------------------------------------------
# Main seed flow


def seed(api: Api, raw_dir: Path, *, reset: bool) -> None:
    coll = find_or_create_collection(api, reset=reset)
    coll_id = coll["appId"]

    for sample_id, notebook_filename, fiber_type in SAMPLE_VARIANTS:
        # The TIF base name is the sample stem before the variant suffix.
        # PH2940-01-carbon → PH2940-01.tif; PH2940-02 → PH2940-02.tif.
        tif_stem = "-".join(sample_id.split("-")[:2])  # PH2940-01-carbon → PH2940-01
        tif_filename = f"{tif_stem}.tif"
        tif_path = raw_dir / tif_filename
        nb_path = raw_dir / notebook_filename

        if not tif_path.exists():
            _log("MISS", tif_filename, "TIFF not found in raw-data — skipping sample")
            continue
        if not nb_path.exists():
            _log("MISS", notebook_filename, "notebook not found — skipping sample")
            continue

        attrs = {
            **SAMPLE_ATTRIBUTES_COMMON,
            "sample_id": tif_stem,
            "fiber_type": fiber_type,
        }
        do = find_or_create_data_object(api, coll_id, sample_id, attributes=attrs)

        # Attach the TIF as a singleton FileReference directly on the DO.
        upload_singleton_if_absent(
            api,
            coll_id,
            do["appId"],
            f"{sample_id} micrograph",
            tif_path,
            "image/tiff",
        )

        # Attach the matching notebook as a second singleton FileReference.
        upload_singleton_if_absent(
            api,
            coll_id,
            do["appId"],
            f"{sample_id} FVF analysis notebook",
            nb_path,
            "application/x-ipynb+json",
        )

    print()
    print("seed complete.")
    print(f"  Collection id: {coll_id}")
    print(f"  Samples seeded: {len(SAMPLE_VARIANTS)}")


def main(argv: list[str]) -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--host", required=True,
                   help="Shepard API base, e.g. https://shepard-api.nuclide.systems")
    p.add_argument("--apikey", required=True,
                   help="X-API-KEY token for an instance-admin or write-capable user")
    p.add_argument("--raw-data", default=str(Path(__file__).parent / "raw-data"),
                   help="Path to the directory holding the .tif + .ipynb files "
                        "(default: ./raw-data)")
    p.add_argument("--reset", action="store_true",
                   help="Delete and recreate the Collection (destructive)")
    args = p.parse_args(argv)

    raw = Path(args.raw_data).expanduser().resolve()
    if not raw.exists():
        print(f"--raw-data not found: {raw}", file=sys.stderr)
        return 1

    api = Api(host=args.host, apikey=args.apikey)
    seed(api, raw, reset=args.reset)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
