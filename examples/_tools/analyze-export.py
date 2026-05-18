"""analyze-export.py — quick statistics on one or more shepard
collection export trees (produced by `export-collection.py`).

Designed for the MFFD migration prep: run it against the user's
legacy-instance export(s) and print enough structure to design the
target-instance Collection layout without grepping through 405 DO
JSON files by hand.

What it prints, per export tree:

- Collection: id, name, description length, attribute key list.
- DataObjects: count, depth distribution (root-only / one-deep / …),
  most common attribute keys, name patterns (common prefixes).
- References: counts per kind, biggest by payload bytes, smallest.
- Containers: count per kind + names.
- Semantic annotations: count, property + value frequency tables.
- Payloads: total bytes, top-N largest files.

When given multiple `--export-dir`, prints a unified summary with
overlap stats (containers shared across exports — useful for
deciding whether to merge collections or keep them separate).

Pure stdlib. No network calls.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any


def _read_json(p: Path) -> Any:
    try:
        return json.loads(p.read_text())
    except Exception:
        return None


def _format_bytes(n: int) -> str:
    if n < 1024:
        return f"{n} B"
    if n < 1024 * 1024:
        return f"{n / 1024:.1f} KiB"
    if n < 1024 * 1024 * 1024:
        return f"{n / 1024 / 1024:.1f} MiB"
    return f"{n / 1024 / 1024 / 1024:.2f} GiB"


def _common_prefix(strings: list[str]) -> str:
    """Longest common alpha prefix across a set of strings — useful
    for spotting naming conventions like 'MFFD-Skin-Panel-001..099'."""
    if not strings:
        return ""
    prefix = strings[0]
    for s in strings[1:]:
        i = 0
        while i < len(prefix) and i < len(s) and prefix[i] == s[i]:
            i += 1
        prefix = prefix[:i]
        if not prefix:
            return ""
    return prefix


def _do_depth(do_dict: dict, do_by_id: dict[int, dict]) -> int:
    depth = 0
    seen = set()
    cursor = do_dict
    while True:
        pid = cursor.get("parent_id") or cursor.get("parentId")
        if pid is None or pid in seen:
            return depth
        seen.add(pid)
        parent = do_by_id.get(pid)
        if parent is None:
            return depth
        depth += 1
        cursor = parent
        if depth > 50:
            return depth


def analyze(export_dir: Path) -> dict[str, Any]:
    if not export_dir.is_dir():
        sys.exit(f"Not a directory: {export_dir}")
    manifest = _read_json(export_dir / "manifest.json")
    if manifest is None:
        sys.exit(f"Missing or unparseable {export_dir}/manifest.json")

    # DataObject walk
    do_dir = export_dir / "data-objects"
    do_by_id: dict[int, dict] = {}
    for f in do_dir.glob("do-*.json"):
        if f.name.endswith(".done"):
            continue
        d = _read_json(f)
        if d and "id" in d:
            do_by_id[int(d["id"])] = d

    # Reference walk + payload size accounting
    refs_dir = export_dir / "references"
    ts_payloads: list[tuple[int, str]] = []  # (bytes, name)
    file_payloads: list[tuple[int, str]] = []
    sd_payloads: list[tuple[int, str]] = []
    for f in refs_dir.glob("ts-*.csv"):
        ts_payloads.append((f.stat().st_size, f.name))
    for d in refs_dir.glob("file-*"):
        if d.is_dir():
            for sub in d.rglob("*"):
                if sub.is_file():
                    file_payloads.append((sub.stat().st_size, str(sub.relative_to(refs_dir))))
    for d in refs_dir.glob("sd-*"):
        if d.is_dir():
            for sub in d.rglob("*.json"):
                sd_payloads.append((sub.stat().st_size, str(sub.relative_to(refs_dir))))

    attr_keys = Counter()
    annot_props = Counter()
    annot_values = Counter()
    names = []
    depth_dist = Counter()
    for do in do_by_id.values():
        for k in (do.get("attributes") or {}).keys():
            attr_keys[k] += 1
        for ann in do.get("_annotations") or []:
            annot_props[ann.get("propertyIri") or ann.get("property_iri") or "?"] += 1
            annot_values[ann.get("valueIri") or ann.get("value_iri") or "?"] += 1
        names.append(do.get("name") or "")
        depth_dist[_do_depth(do, do_by_id)] += 1

    # Collection-level annotations
    coll_anns = _read_json(export_dir / "annotations.json") or []

    return {
        "path": str(export_dir.resolve()),
        "manifest_totals": manifest.get("totals", {}),
        "collection": manifest.get("collection", {}),
        "containers": manifest.get("containers", {}),
        "do_count": len(do_by_id),
        "do_depth_distribution": dict(depth_dist),
        "do_name_prefix": _common_prefix(names) if len(names) >= 2 else "",
        "do_attribute_keys": attr_keys.most_common(20),
        "do_annotation_property_top": annot_props.most_common(10),
        "do_annotation_value_top": annot_values.most_common(10),
        "collection_annotations": len(coll_anns),
        "payloads": {
            "ts_count": len(ts_payloads),
            "ts_total_bytes": sum(b for b, _ in ts_payloads),
            "ts_largest": sorted(ts_payloads, reverse=True)[:5],
            "file_count": len(file_payloads),
            "file_total_bytes": sum(b for b, _ in file_payloads),
            "file_largest": sorted(file_payloads, reverse=True)[:5],
            "sd_count": len(sd_payloads),
            "sd_total_bytes": sum(b for b, _ in sd_payloads),
            "sd_largest": sorted(sd_payloads, reverse=True)[:5],
        },
    }


def print_one(rep: dict[str, Any]) -> None:
    print(f"\n=== {rep['path']} ===\n")
    coll = rep["collection"]
    print(f"Collection: id={coll.get('id')}  name={coll.get('name')!r}")
    desc = (coll.get("description") or "")
    print(f"  description: {len(desc)} chars")
    print(f"  attributes: {list((coll.get('attributes') or {}).keys())}")
    print(f"  collection-level annotations: {rep['collection_annotations']}")

    print(f"\nData objects: {rep['do_count']}")
    print(f"  depth distribution: {rep['do_depth_distribution']}")
    if rep["do_name_prefix"]:
        print(f"  common name prefix: {rep['do_name_prefix']!r}")
    print(f"  top attribute keys:")
    for k, c in rep["do_attribute_keys"]:
        print(f"    {c:4d}x  {k}")
    if rep["do_annotation_property_top"]:
        print(f"  top annotation properties:")
        for k, c in rep["do_annotation_property_top"]:
            print(f"    {c:4d}x  {k}")
    if rep["do_annotation_value_top"]:
        print(f"  top annotation values:")
        for k, c in rep["do_annotation_value_top"]:
            print(f"    {c:4d}x  {k}")

    totals = rep["manifest_totals"]
    print(f"\nReferences (from manifest):")
    print(f"  timeseries: {totals.get('timeseries_refs', 0)}")
    print(f"  file:       {totals.get('file_refs', 0)}")
    print(f"  structured: {totals.get('structured_refs', 0)}")

    print(f"\nContainers:")
    for kind, cs in rep["containers"].items():
        print(f"  {kind} ({len(cs)}):")
        for c in cs:
            print(f"    - id={c.get('id')}  name={c.get('name')!r}")

    pl = rep["payloads"]
    print(f"\nPayloads:")
    print(f"  timeseries CSV: {pl['ts_count']} files, {_format_bytes(pl['ts_total_bytes'])}")
    if pl["ts_largest"]:
        for b, n in pl["ts_largest"]:
            print(f"    {_format_bytes(b):>12}  {n}")
    print(f"  files:          {pl['file_count']} files, {_format_bytes(pl['file_total_bytes'])}")
    if pl["file_largest"]:
        for b, n in pl["file_largest"]:
            print(f"    {_format_bytes(b):>12}  {n}")
    print(f"  structured:     {pl['sd_count']} files, {_format_bytes(pl['sd_total_bytes'])}")
    if pl["sd_largest"]:
        for b, n in pl["sd_largest"]:
            print(f"    {_format_bytes(b):>12}  {n}")


def print_overlap(reps: list[dict[str, Any]]) -> None:
    if len(reps) < 2:
        return
    print("\n\n=== Cross-export overlap ===\n")

    # Container overlap by name (within each kind)
    print("Container names per export (per kind):")
    for kind in ("timeseries", "file", "structured"):
        per_export = []
        for rep in reps:
            names = {c.get("name") for c in rep["containers"].get(kind, []) if c.get("name")}
            per_export.append(names)
        shared = per_export[0].intersection(*per_export[1:]) if per_export else set()
        print(f"  {kind}: {[len(s) for s in per_export]} per export, {len(shared)} shared by name")
        if shared:
            for n in sorted(shared):
                print(f"    - {n}")

    # Attribute key overlap
    print("\nAttribute key overlap (top 20 per export):")
    sets = [set(k for k, _ in rep["do_attribute_keys"]) for rep in reps]
    shared = sets[0].intersection(*sets[1:]) if sets else set()
    print(f"  shared keys: {sorted(shared)}")

    # Naming convention
    print("\nName prefix per export:")
    for rep in reps:
        print(f"  {rep['path']}: {rep['do_name_prefix']!r}")


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--export-dir", action="append", required=True,
                    help="Path to one export tree (repeat for multiple).")
    args = ap.parse_args(argv)

    reps = [analyze(Path(d)) for d in args.export_dir]
    for r in reps:
        print_one(r)
    if len(reps) > 1:
        print_overlap(reps)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
