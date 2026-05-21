"""explore-bridge-welding.py — deep content discovery for an unknown welding dataset.

Complements analyze-export.py (which gives macro stats) by diving into the *content*
of references: what timeseries channels exist, what file types are present, what
structured-data schemas look like.

Use this when you receive a new shepard export and don't yet know its structure.
Run analyze-export.py first for counts and hierarchy; run this script for content.

Output:
  - Printed summary to stdout
  - Optional --report discovery-report.md for a portable markdown file

Pure stdlib. No network calls. Safe to run on any shepard export tree.
"""

from __future__ import annotations

import argparse
import csv
import json
import sys
from collections import Counter, defaultdict
from io import StringIO
from pathlib import Path
from typing import Any


# ── Helpers ────────────────────────────────────────────────────────────────

def _read_json(p: Path) -> Any:
    try:
        return json.loads(p.read_text(encoding="utf-8", errors="replace"))
    except Exception:
        return None


def _fmt(n: int) -> str:
    if n < 1024:
        return f"{n} B"
    if n < 1024 ** 2:
        return f"{n / 1024:.1f} KiB"
    if n < 1024 ** 3:
        return f"{n / 1024 ** 2:.1f} MiB"
    return f"{n / 1024 ** 3:.2f} GiB"


def _type_of_value(v: Any) -> str:
    if v is None:
        return "null"
    if isinstance(v, bool):
        return "bool"
    if isinstance(v, int):
        return "int"
    if isinstance(v, float):
        return "float"
    if isinstance(v, list):
        return f"array[{len(v)}]"
    if isinstance(v, dict):
        return f"object{{{','.join(list(v.keys())[:4])}}}"
    s = str(v)
    if len(s) > 40:
        return f"str({len(s)})"
    return repr(s)


# ── Timeseries exploration ──────────────────────────────────────────────────

def _explore_timeseries(refs_dir: Path, max_sample: int = 5) -> dict:
    """Read CSV headers + a few data rows from sampled timeseries references.
    Also build a channel inventory across all sampled CSVs."""
    channel_inventory: Counter = Counter()  # column name → occurrence count
    unit_hints: dict[str, set] = defaultdict(set)  # column name → seen values (guessing units)
    samples: list[dict] = []

    csv_files = sorted(refs_dir.glob("ts-*.csv"))
    for csv_path in csv_files[:max_sample]:
        meta_path = refs_dir / (csv_path.stem + ".json")
        meta = _read_json(meta_path) or {}

        rows = []
        header = []
        try:
            text = csv_path.read_text(encoding="utf-8", errors="replace")
            reader = csv.reader(StringIO(text))
            for i, row in enumerate(reader):
                if i == 0:
                    header = row
                    for col in header:
                        channel_inventory[col] += 1
                elif i <= 3:
                    rows.append(row)
                else:
                    break
        except Exception as e:
            rows = [["ERROR", str(e)]]

        # Try to sniff units from column names (e.g. "temp_C", "force_kN")
        for col in header:
            lower = col.lower()
            for hint in ("_c", "_k", "_mpa", "_kn", "_n", "_mm", "_hz", "_s", "_bar",
                         "_pct", "_psi", "_a", "_v", "_w", "_rpm", "_deg", "_rad"):
                if lower.endswith(hint):
                    unit_hints[col].add(hint.lstrip("_"))

        samples.append({
            "file": csv_path.name,
            "meta_name": meta.get("name") or meta.get("timeseriesContainerId", "?"),
            "measurement": meta.get("measurement"),
            "device": meta.get("device"),
            "location": meta.get("location"),
            "symbolic_name": meta.get("symbolicName") or meta.get("symbolic_name"),
            "field": meta.get("field"),
            "row_count": meta.get("rowCount") or meta.get("row_count"),
            "header": header,
            "data_rows": rows,
            "size": _fmt(csv_path.stat().st_size),
        })

    return {
        "total_csv_files": len(csv_files),
        "sampled": len(samples),
        "channel_inventory": channel_inventory.most_common(40),
        "unit_hints": {k: sorted(v) for k, v in unit_hints.items()},
        "samples": samples,
    }


# ── File exploration ────────────────────────────────────────────────────────

def _explore_files(refs_dir: Path, max_sample: int = 5) -> dict:
    """Catalog file extensions across all file-reference downloads.
    Also sample a few file reference metadata files."""
    ext_counter: Counter = Counter()
    ext_size: Counter = Counter()  # bytes per extension
    ext_examples: dict[str, list[str]] = defaultdict(list)

    meta_samples: list[dict] = []
    meta_paths = sorted(refs_dir.glob("file-*.json"))

    for meta_path in meta_paths:
        if meta_path.name.endswith(".done"):
            continue
        meta = _read_json(meta_path)
        if meta and len(meta_samples) < max_sample:
            meta_samples.append({
                "file": meta_path.name,
                "name": meta.get("name"),
                "description": (meta.get("description") or "")[:120],
                "file_oids": meta.get("fileOids") or meta.get("file_oids") or [],
                "annotations": len(meta.get("_annotations") or []),
            })

        # Walk download directory for this ref
        dl_dir = refs_dir / meta_path.stem  # e.g. references/file-42/
        if dl_dir.is_dir():
            for f in dl_dir.rglob("*"):
                if f.is_file():
                    ext = f.suffix.lower() or "(no ext)"
                    size = f.stat().st_size
                    ext_counter[ext] += 1
                    ext_size[ext] += size
                    if len(ext_examples[ext]) < 3:
                        ext_examples[ext].append(f.name)

    return {
        "total_meta_files": len(meta_paths),
        "ext_catalog": [
            {
                "ext": ext,
                "count": ext_counter[ext],
                "total_size": _fmt(ext_size[ext]),
                "examples": ext_examples[ext],
            }
            for ext, _ in ext_counter.most_common(30)
        ],
        "meta_samples": meta_samples,
    }


# ── Structured-data exploration ─────────────────────────────────────────────

def _explore_structured_data(refs_dir: Path, max_sample: int = 5) -> dict:
    """Sample structured-data reference metadata + payload shapes."""
    samples: list[dict] = []

    for meta_path in sorted(refs_dir.glob("sd-*.json"))[:max_sample]:
        if meta_path.name.endswith(".done"):
            continue
        meta = _read_json(meta_path) or {}
        payload_schema: dict[str, str] = {}

        # Look at the first payload JSON file
        dl_dir = refs_dir / meta_path.stem
        if dl_dir.is_dir():
            for payload_file in sorted(dl_dir.glob("*.json"))[:1]:
                payload = _read_json(payload_file) or {}
                if isinstance(payload, dict):
                    payload_schema = {k: _type_of_value(v) for k, v in payload.items()}
                elif isinstance(payload, list) and payload:
                    first = payload[0]
                    if isinstance(first, dict):
                        payload_schema = {k: _type_of_value(v) for k, v in first.items()}
                    else:
                        payload_schema = {"[0]": _type_of_value(first)}

        samples.append({
            "file": meta_path.name,
            "name": meta.get("name"),
            "description": (meta.get("description") or "")[:120],
            "oids": meta.get("structuredDataOids") or meta.get("structured_data_oids") or [],
            "payload_schema": payload_schema,
        })

    return {
        "total_sd_refs": len(list(refs_dir.glob("sd-*.json"))),
        "sampled": len(samples),
        "samples": samples,
    }


# ── DataObject tree sampling ────────────────────────────────────────────────

def _explore_do_tree(export_dir: Path, max_show: int = 15) -> dict:
    """Show the DataObject name hierarchy up to max_show entries.
    Also summarise attribute keys unique to this dataset."""
    do_dir = export_dir / "data-objects"
    do_by_id: dict[int, dict] = {}
    for f in do_dir.glob("do-*.json"):
        if f.name.endswith(".done"):
            continue
        d = _read_json(f)
        if d and "id" in d:
            do_by_id[int(d["id"])] = d

    attr_keys: Counter = Counter()
    status_counts: Counter = Counter()
    ref_kind_dist: Counter = Counter()  # "1ts+0f+0sd", etc.
    for do in do_by_id.values():
        for k in (do.get("attributes") or {}).keys():
            attr_keys[k] += 1
        status_counts[do.get("status") or "?"] += 1
        ts = len(do.get("_references", {}).get("timeseries", []))
        fi = len(do.get("_references", {}).get("file", []))
        sd = len(do.get("_references", {}).get("structured", []))
        ref_kind_dist[f"{ts}ts+{fi}f+{sd}sd"] += 1

    # Build name tree (roots first, children indented)
    roots = [d for d in do_by_id.values()
             if not (d.get("parent_id") or d.get("parentId"))]
    roots.sort(key=lambda d: str(d.get("name") or ""))

    def _subtree(node: dict, indent: int, out: list, budget: list) -> None:
        if budget[0] <= 0:
            return
        attr_count = len(node.get("attributes") or {})
        ts = len(node.get("_references", {}).get("timeseries", []))
        fi = len(node.get("_references", {}).get("file", []))
        sd = len(node.get("_references", {}).get("structured", []))
        out.append(
            "  " * indent
            + f"• {node.get('name', '?')!r}  "
            + f"[status={node.get('status','?')} attrs={attr_count} ts={ts} file={fi} sd={sd}]"
        )
        budget[0] -= 1
        children_ids = node.get("children_ids") or node.get("childrenIds") or []
        if isinstance(children_ids, dict):
            children_ids = children_ids.get("childrenIds", [])
        for cid in children_ids:
            child = do_by_id.get(int(cid))
            if child:
                _subtree(child, indent + 1, out, budget)

    tree_lines: list[str] = []
    budget = [max_show]
    for root in roots:
        _subtree(root, 0, tree_lines, budget)
        if budget[0] <= 0:
            tree_lines.append(f"  … (showing first {max_show} of {len(do_by_id)} DOs)")
            break

    return {
        "total_dos": len(do_by_id),
        "status_counts": dict(status_counts),
        "ref_profile_distribution": ref_kind_dist.most_common(10),
        "attribute_keys": attr_keys.most_common(20),
        "tree_preview": tree_lines,
    }


# ── Report rendering ────────────────────────────────────────────────────────

def _render_report(export_dir: Path, ts: dict, files: dict, sd: dict, tree: dict) -> str:
    lines = [
        f"# Bridge Welding Export Discovery Report",
        f"",
        f"Source: `{export_dir.resolve()}`",
        f"",
        f"---",
        f"",
        f"## DataObject tree ({tree['total_dos']} total)",
        f"",
        f"Status breakdown: {tree['status_counts']}",
        f"",
        f"Reference profile (ts+file+sd per DO):",
    ]
    for profile, count in tree["ref_profile_distribution"]:
        lines.append(f"  - `{profile}` × {count}")
    lines += [
        f"",
        f"Attribute keys found:",
    ]
    for k, c in tree["attribute_keys"]:
        lines.append(f"  - `{k}` ({c}×)")
    lines += [
        f"",
        f"DataObject name tree (first entries):",
        f"",
    ]
    lines += ["```"] + tree["tree_preview"] + ["```"]

    lines += [
        f"",
        f"---",
        f"",
        f"## Timeseries ({ts['total_csv_files']} total, {ts['sampled']} sampled)",
        f"",
        f"### Channel inventory (all column names across sampled CSVs)",
        f"",
    ]
    for col, count in ts["channel_inventory"]:
        hint = ts["unit_hints"].get(col)
        hint_str = f"  ← unit hint: {hint}" if hint else ""
        lines.append(f"  - `{col}` ({count}×){hint_str}")

    lines.append("")
    for s in ts["samples"]:
        lines += [
            f"### {s['file']}  [{s['size']}]",
            f"- measurement: `{s['measurement']}`  device: `{s['device']}`"
            f"  location: `{s['location']}`  field: `{s['field']}`",
            f"- symbolic_name: `{s['symbolic_name']}`  row_count: {s['row_count']}",
            f"- columns: {s['header']}",
        ]
        for row in s["data_rows"]:
            lines.append(f"  `{row}`")
        lines.append("")

    lines += [
        f"---",
        f"",
        f"## Files ({files['total_meta_files']} refs)",
        f"",
        f"### Extension catalog",
        f"",
    ]
    for entry in files["ext_catalog"]:
        ex = ", ".join(entry["examples"])
        lines.append(f"  - `{entry['ext']}` × {entry['count']}  ({entry['total_size']})  e.g. {ex}")
    lines.append("")
    lines.append("### Sampled file reference metadata")
    lines.append("")
    for s in files["meta_samples"]:
        lines += [
            f"**{s['file']}** — `{s['name']}`",
            f"  {s['description']}",
            f"  OIDs: {s['file_oids']}   annotations: {s['annotations']}",
            f"",
        ]

    lines += [
        f"---",
        f"",
        f"## Structured data ({sd['total_sd_refs']} total, {sd['sampled']} sampled)",
        f"",
    ]
    for s in sd["samples"]:
        lines += [
            f"**{s['file']}** — `{s['name']}`",
            f"  {s['description']}",
            f"  OIDs: {s['oids']}",
        ]
        if s["payload_schema"]:
            lines.append("  Schema:")
            for field, typ in s["payload_schema"].items():
                lines.append(f"    - `{field}`: {typ}")
        lines.append("")

    lines += [
        f"---",
        f"",
        f"## Next steps",
        f"",
        f"1. Review channel inventory above — identify the welding process channels",
        f"   (temperature, force, current, displacement, quality flags).",
        f"2. Review extension catalog — any new file types not in the AFP dataset",
        f"   need a parser or a note in the import manifest.",
        f"3. Check structured-data schemas — do they represent calibration records,",
        f"   welding parameters, or process results?",
        f"4. Run `collect-mffd-samples.py` with `--export-dir` pointing at this export",
        f"   to extract format samples for the import parser.",
    ]

    return "\n".join(lines)


# ── Main ────────────────────────────────────────────────────────────────────

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
        "--sample", type=int, default=5,
        help="How many references of each type to sample in depth (default: 5)",
    )
    ap.add_argument(
        "--tree-rows", type=int, default=20,
        help="Max DataObject tree rows to show (default: 20)",
    )
    ap.add_argument(
        "--report", metavar="PATH",
        help="Write a discovery-report.md to this path (optional)",
    )
    args = ap.parse_args(argv)

    export_dir = Path(args.export_dir)
    if not (export_dir / "manifest.json").exists():
        print(f"ERROR: {export_dir}/manifest.json not found. "
              "Point --export-dir at a directory from export-collection.py.",
              file=sys.stderr)
        return 1

    refs_dir = export_dir / "references"

    print(f"[explore] {export_dir.resolve()}", flush=True)
    print("Exploring DataObject tree ...", flush=True)
    tree = _explore_do_tree(export_dir, max_show=args.tree_rows)

    print("Exploring timeseries references ...", flush=True)
    ts = _explore_timeseries(refs_dir, max_sample=args.sample)

    print("Exploring file references ...", flush=True)
    files = _explore_files(refs_dir, max_sample=args.sample)

    print("Exploring structured-data references ...", flush=True)
    sd = _explore_structured_data(refs_dir, max_sample=args.sample)

    report = _render_report(export_dir, ts, files, sd, tree)
    print("\n" + report)

    if args.report:
        Path(args.report).write_text(report, encoding="utf-8")
        print(f"\nReport written to {args.report}", flush=True)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
