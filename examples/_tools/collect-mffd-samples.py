"""collect-mffd-samples.py — extract format samples from a shepard export directory.

Reads an export produced by export-collection.py and pulls out one example of
each file type we need to write the MFFD import parser. Output goes to a small
`samples/` subdirectory (or --out path) so you can inspect everything in one place
without shipping gigabytes.

Collected items:
  1. manifest-summary.json      — containers block + totals block from manifest.json
  2. do-root-sample.json        — one root-level DataObject (Layer/PlyGroup, no parent)
  3. do-leaf-sample.json        — one leaf-level DataObject (Track, has parent, no children)
  4. ts-ref-sample.json         — one timeseries reference metadata file
  5. ts-payload-head.csv        — first 5 data rows of the matching timeseries CSV
  6. file-ref-sample.json       — one file reference metadata file
  7. sd-ref-sample.json         — one structured-data reference metadata file
  8. vcproject-snippet.xml      — first 80 lines of the first .vcproject file found
  9. krl-src-snippet.src        — first 20 lines of the first KRL .src file found

Usage:
    python collect-mffd-samples.py --export-dir ./mffd-export --out ./mffd-samples

If --export-dir is omitted the script looks for an export in the current directory.
Items 8 and 9 (robot/program files) are skipped gracefully if no such files are present.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path


def _log(msg: str) -> None:
    print(msg, flush=True)


def _write(out: Path, name: str, content: str | bytes, mode: str = "w") -> None:
    target = out / name
    if isinstance(content, bytes):
        target.write_bytes(content)
    else:
        target.write_text(content, encoding="utf-8")
    _log(f"  ✓  {name}")


def _truncate_json(path: Path, keep_keys: list[str]) -> str:
    """Return a JSON string with only the requested top-level keys."""
    obj = json.loads(path.read_text())
    subset = {k: obj[k] for k in keep_keys if k in obj}
    return json.dumps(subset, indent=2)


def _head_csv(path: Path, n_data_rows: int = 5) -> str:
    """Return header + first n_data_rows of a CSV file."""
    lines = []
    with path.open(encoding="utf-8") as f:
        for i, line in enumerate(f):
            lines.append(line.rstrip("\n"))
            if i >= n_data_rows:  # header (i=0) + n data rows
                break
    return "\n".join(lines)


def _head_text(path: Path, n_lines: int = 20) -> str:
    lines = []
    with path.open(encoding="utf-8", errors="replace") as f:
        for i, line in enumerate(f):
            if i >= n_lines:
                break
            lines.append(line.rstrip("\n"))
    return "\n".join(lines)


def _find_root_and_leaf_dos(export: Path) -> tuple[Path | None, Path | None]:
    """Scan data-objects/ and return (root_do_path, leaf_do_path)."""
    root: Path | None = None
    leaf: Path | None = None
    for p in sorted((export / "data-objects").glob("do-*.json")):
        if p.name.endswith(".done"):
            continue
        try:
            d = json.loads(p.read_text())
        except Exception:
            continue
        parent_id = d.get("parent_id") or d.get("parentId")
        children = (d.get("children_ids") or d.get("childrenIds") or [])
        if isinstance(children, dict):
            children = children.get("childrenIds", [])
        has_children = bool(children)
        if root is None and parent_id is None and has_children:
            root = p
        if leaf is None and parent_id is not None and not has_children:
            leaf = p
        if root and leaf:
            break
    return root, leaf


def _find_file_by_ext(export: Path, ext: str) -> Path | None:
    """Search inside file-ref download directories for a file with the given extension."""
    ref_dir = export / "references"
    for candidate_dir in sorted(ref_dir.iterdir()):
        if not candidate_dir.is_dir():
            continue
        if not candidate_dir.name.startswith("file-"):
            continue
        for f in sorted(candidate_dir.rglob(f"*{ext}")):
            if f.is_file():
                return f
    return None


def collect(args: argparse.Namespace) -> int:
    export = Path(args.export_dir)
    if not (export / "manifest.json").exists():
        _log(f"ERROR: {export / 'manifest.json'} not found. "
             "Point --export-dir at a directory produced by export-collection.py.")
        return 1

    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    _log(f"Export: {export.resolve()}")
    _log(f"Output: {out.resolve()}")

    # 1 ── manifest summary ──────────────────────────────────────────────────
    _write(out, "manifest-summary.json",
           _truncate_json(export / "manifest.json",
                          ["containers", "totals", "source_host", "exported_at",
                           "exporter_version", "options"]))

    # 2 + 3 ── root + leaf DataObjects ──────────────────────────────────────
    root_path, leaf_path = _find_root_and_leaf_dos(export)
    if root_path:
        _write(out, "do-root-sample.json", root_path.read_text())
    else:
        _log("  ⚠  no root-level DataObject found (no parent, has children)")

    if leaf_path:
        _write(out, "do-leaf-sample.json", leaf_path.read_text())
    else:
        _log("  ⚠  no leaf DataObject found (has parent, no children)")

    # 4 + 5 ── timeseries reference + CSV head ──────────────────────────────
    ts_ref_path: Path | None = None
    ts_csv_path: Path | None = None
    for p in sorted((export / "references").glob("ts-*.json")):
        if p.name.endswith(".done"):
            continue
        ts_ref_path = p
        stem = p.stem  # e.g. "ts-42"
        candidate_csv = export / "references" / f"{stem}.csv"
        if candidate_csv.exists():
            ts_csv_path = candidate_csv
        break

    if ts_ref_path:
        _write(out, "ts-ref-sample.json", ts_ref_path.read_text())
    else:
        _log("  ⚠  no timeseries reference found")

    if ts_csv_path:
        _write(out, "ts-payload-head.csv", _head_csv(ts_csv_path, n_data_rows=5))
    else:
        _log("  ⚠  no timeseries CSV payload found")

    # 6 ── file reference ────────────────────────────────────────────────────
    file_ref_path: Path | None = None
    for p in sorted((export / "references").glob("file-*.json")):
        if p.name.endswith(".done"):
            continue
        file_ref_path = p
        break
    if file_ref_path:
        _write(out, "file-ref-sample.json", file_ref_path.read_text())
    else:
        _log("  ⚠  no file reference found")

    # 7 ── structured-data reference ─────────────────────────────────────────
    sd_ref_path: Path | None = None
    for p in sorted((export / "references").glob("sd-*.json")):
        if p.name.endswith(".done"):
            continue
        sd_ref_path = p
        break
    if sd_ref_path:
        _write(out, "sd-ref-sample.json", sd_ref_path.read_text())
    else:
        _log("  ⚠  no structured-data reference found")

    # 8 ── .vcproject XML snippet ─────────────────────────────────────────────
    vcproj = _find_file_by_ext(export, ".vcproject")
    if vcproj:
        _write(out, "vcproject-snippet.xml", _head_text(vcproj, n_lines=80))
    else:
        _log("  ⚠  no .vcproject file found in file downloads")

    # 9 ── KRL .src snippet ────────────────────────────────────────────────────
    krl_src = _find_file_by_ext(export, ".src")
    if krl_src:
        _write(out, "krl-src-snippet.src", _head_text(krl_src, n_lines=20))
    else:
        _log("  ⚠  no KRL .src file found in file downloads")

    # ── Summary ────────────────────────────────────────────────────────────
    _log("")
    _log(f"Samples written to {out.resolve()}")
    _log("Ship this directory alongside the import parser work.")
    return 0


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    ap.add_argument(
        "--export-dir", default=".",
        help="Directory produced by export-collection.py (default: current dir)",
    )
    ap.add_argument(
        "--out", default="./mffd-samples",
        help="Output directory for collected samples (default: ./mffd-samples)",
    )
    args = ap.parse_args(argv)
    return collect(args)


if __name__ == "__main__":
    raise SystemExit(main())
