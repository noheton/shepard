#!/usr/bin/env python3
"""
scripts/perf/recommend.py — PERF2b performance recommender

Reads the last k6 run output (scripts/perf/last-run.json) and optionally
queries a Prometheus instance to produce 3-5 ranked performance tuning
suggestions. Each suggestion carries a safe_to_apply boolean (True = pure
env-var / config change with no side effects; reversible by editing .env back).

Usage:
    python3 scripts/perf/recommend.py [options]

Exit codes:
    0  — no suggestions (install looks healthy)
    1  — suggestions found (CI-friendly; use --json to process them)
    2  — error (bad args, unreadable file, unexpected exception)

Design: aidocs/ops/59-performance-testing-and-tuning.md §4 (PERF2b)
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.request
from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------

LAST_RUN_DEFAULT = os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "last-run.json"
)

PROMETHEUS_DEFAULT = "http://localhost:9090"

# Rule thresholds (edit here to tune the trigger levels)
HEAP_USAGE_THRESHOLD = 0.85          # > 85% of max → suggest -Xmx bump
GC_PAUSE_MAX_THRESHOLD = 0.200       # > 200 ms → suggest G1GC tuning
PERMS_CACHE_HIT_THRESHOLD = 0.60     # < 60% hit rate → suggest cache size
MONGO_LATENCY_THRESHOLD_MS = 50.0    # > 50 ms avg cmd → suggest pool size
SEARCH_P95_THRESHOLD_MS = 3000.0     # > 3000 ms p95 → suggest Neo4j page-cache
COLLECTIONS_P95_THRESHOLD_MS = 800.0 # > 800 ms p95 → suggest perms cache tuning

# Maximum suggestions to return
MAX_SUGGESTIONS = 5

# Prometheus query timeout (seconds)
PROMETHEUS_TIMEOUT = 3

# ---------------------------------------------------------------------------
# Canned data for --dry-run / testing
# ---------------------------------------------------------------------------

DRY_RUN_K6 = {
    "metrics": {
        "search_duration": {
            "values": {"p(95)": 4200.0, "p(99)": 6500.0, "avg": 2100.0}
        },
        "collections_list_duration": {
            "values": {"p(95)": 950.0, "p(99)": 1400.0, "avg": 420.0}
        },
    }
}

DRY_RUN_PROMETHEUS = {
    "heap_used": 3_600_000_000,
    "heap_max": 4_000_000_000,
    "gc_pause_max": 0.095,           # < threshold — won't fire
    "perms_cache_hits": 5000,
    "perms_cache_misses": 6000,      # hit rate 45% → will fire
    "mongo_avg_latency_ms": 62.0,    # > 50 ms → will fire
}

# ---------------------------------------------------------------------------
# Data model
# ---------------------------------------------------------------------------

@dataclass
class Suggestion:
    rank: int
    rule: str
    safe_to_apply: bool
    description: str
    suggested_change: str
    severity: int = field(default=0, repr=False)  # higher = more urgent


# ---------------------------------------------------------------------------
# Prometheus helpers
# ---------------------------------------------------------------------------

def _prom_query(base_url: str, promql: str) -> Optional[float]:
    """Execute a Prometheus instant query and return the first scalar result.

    Returns None on any error (network, no data, parse failure).
    """
    url = f"{base_url}/api/v1/query?query={urllib.request.quote(promql)}"
    try:
        req = urllib.request.Request(url, headers={"Accept": "application/json"})
        with urllib.request.urlopen(req, timeout=PROMETHEUS_TIMEOUT) as resp:
            body = json.loads(resp.read().decode())
    except (urllib.error.URLError, OSError, json.JSONDecodeError):
        return None

    try:
        result = body["data"]["result"]
        if not result:
            return None
        return float(result[0]["value"][1])
    except (KeyError, IndexError, TypeError, ValueError):
        return None


def _prom_sum(base_url: str, promql: str) -> Optional[float]:
    """Execute a sum() Prometheus query and return the scalar."""
    return _prom_query(base_url, f"sum({promql})")


def probe_prometheus(base_url: str) -> tuple[bool, Dict[str, Optional[float]]]:
    """Probe Prometheus for the metrics we care about.

    Returns (reachable, metrics_dict).  If not reachable, all values are None.
    """
    # A cheap reachability check: the /-/healthy endpoint
    healthy_url = f"{base_url}/-/healthy"
    reachable = False
    try:
        with urllib.request.urlopen(healthy_url, timeout=PROMETHEUS_TIMEOUT):
            reachable = True
    except (urllib.error.URLError, OSError):
        pass

    if not reachable:
        return False, {}

    metrics: Dict[str, Optional[float]] = {}

    # JVM heap
    heap_used = _prom_sum(
        base_url, 'jvm_memory_used_bytes{area="heap"}'
    )
    heap_max = _prom_sum(
        base_url, 'jvm_memory_max_bytes{area="heap"}'
    )
    metrics["heap_used"] = heap_used
    metrics["heap_max"] = heap_max

    # GC pause max
    metrics["gc_pause_max"] = _prom_query(
        base_url, 'max(jvm_gc_pause_seconds_max)'
    )

    # Permissions cache hit ratio components
    metrics["perms_cache_hits"] = _prom_query(
        base_url, 'sum(cache_hits_total{cache="permissions"})'
    )
    metrics["perms_cache_misses"] = _prom_query(
        base_url, 'sum(cache_misses_total{cache="permissions"})'
    )

    # Mongo avg latency: sum(seconds_sum) / sum(seconds_count) * 1000 → ms
    mongo_sum = _prom_query(
        base_url, "sum(mongodb_driver_commands_seconds_sum)"
    )
    mongo_count = _prom_query(
        base_url, "sum(mongodb_driver_commands_seconds_count)"
    )
    if mongo_sum is not None and mongo_count and mongo_count > 0:
        metrics["mongo_avg_latency_ms"] = (mongo_sum / mongo_count) * 1000.0
    else:
        metrics["mongo_avg_latency_ms"] = None

    return True, metrics


# ---------------------------------------------------------------------------
# k6 helpers
# ---------------------------------------------------------------------------

def load_k6_summary(path: str) -> tuple[bool, Dict[str, Any]]:
    """Load k6 last-run.json.  Returns (found, data)."""
    if not os.path.isfile(path):
        return False, {}
    try:
        with open(path, encoding="utf-8") as fh:
            return True, json.load(fh)
    except (OSError, json.JSONDecodeError) as exc:
        _warn(f"Could not read {path}: {exc}")
        return False, {}


def k6_p95(data: Dict[str, Any], metric_name: str) -> Optional[float]:
    """Extract p(95) from a k6 custom Trend metric."""
    try:
        return float(data["metrics"][metric_name]["values"]["p(95)"])
    except (KeyError, TypeError, ValueError):
        return None


# ---------------------------------------------------------------------------
# Rule catalogue
# ---------------------------------------------------------------------------

def _rule_search_threshold_breach(
    k6_data: Dict[str, Any],
) -> Optional[Suggestion]:
    """search_duration p(95) > 3000 ms → Neo4j page-cache."""
    p95 = k6_p95(k6_data, "search_duration")
    if p95 is None or p95 <= SEARCH_P95_THRESHOLD_MS:
        return None
    return Suggestion(
        rank=0,
        rule="search_threshold_breach",
        safe_to_apply=False,
        description=(
            f"search_duration p(95) = {p95:.0f}ms "
            f"(threshold: {SEARCH_P95_THRESHOLD_MS:.0f}ms). "
            "Increase Neo4j page cache: "
            "-Dneo4j.dbms.memory.pagecache.size=2g"
        ),
        suggested_change="-Dneo4j.dbms.memory.pagecache.size=2g",
        severity=90,
    )


def _rule_collections_threshold_breach(
    k6_data: Dict[str, Any],
) -> Optional[Suggestion]:
    """collections_list_duration p(95) > 800 ms → permissions cache."""
    p95 = k6_p95(k6_data, "collections_list_duration")
    if p95 is None or p95 <= COLLECTIONS_P95_THRESHOLD_MS:
        return None
    return Suggestion(
        rank=0,
        rule="collections_threshold_breach",
        safe_to_apply=True,
        description=(
            f"collections_list_duration p(95) = {p95:.0f}ms "
            f"(threshold: {COLLECTIONS_P95_THRESHOLD_MS:.0f}ms). "
            "Increase permissions cache: "
            "shepard.permissions.cache.max-size=10000"
        ),
        suggested_change="shepard.permissions.cache.max-size=10000",
        severity=80,
    )


def _rule_jvm_heap_too_small(
    prom: Dict[str, Any],
) -> Optional[Suggestion]:
    """heap used > 85% of max → suggest -Xmx increase."""
    used = prom.get("heap_used")
    max_heap = prom.get("heap_max")
    if used is None or max_heap is None or max_heap <= 0:
        return None
    ratio = used / max_heap
    if ratio <= HEAP_USAGE_THRESHOLD:
        return None
    pct = int(ratio * 100)
    return Suggestion(
        rank=0,
        rule="jvm_heap_too_small",
        safe_to_apply=True,
        description=(
            f"JVM heap at {pct}% of max "
            f"({used / 1e9:.1f} GB / {max_heap / 1e9:.1f} GB). "
            "Increase: -Xmx4g (set JVM_HEAP_MAX=4g in infrastructure/.env)"
        ),
        suggested_change="JVM_HEAP_MAX=4g",
        severity=70,
    )


def _rule_gc_pauses_high(
    prom: Dict[str, Any],
) -> Optional[Suggestion]:
    """GC pause max > 200 ms → suggest G1GC tuning."""
    gc_max = prom.get("gc_pause_max")
    if gc_max is None or gc_max <= GC_PAUSE_MAX_THRESHOLD:
        return None
    ms = gc_max * 1000
    return Suggestion(
        rank=0,
        rule="gc_pauses_high",
        safe_to_apply=False,
        description=(
            f"GC pause max = {ms:.0f}ms (threshold: "
            f"{GC_PAUSE_MAX_THRESHOLD * 1000:.0f}ms). "
            "Switch to G1GC: JVM_GC_TUNING=g1gc "
            "(add -XX:+UseG1GC -XX:MaxGCPauseMillis=100)"
        ),
        suggested_change="JVM_GC_TUNING=g1gc",
        severity=60,
    )


def _rule_permissions_cache_cold(
    prom: Dict[str, Any],
) -> Optional[Suggestion]:
    """Cache hit rate < 60% → suggest cache size increase."""
    hits = prom.get("perms_cache_hits")
    misses = prom.get("perms_cache_misses")
    if hits is None or misses is None:
        return None
    total = hits + misses
    if total <= 0:
        return None
    hit_rate = hits / total
    if hit_rate >= PERMS_CACHE_HIT_THRESHOLD:
        return None
    pct = int(hit_rate * 100)
    return Suggestion(
        rank=0,
        rule="permissions_cache_cold",
        safe_to_apply=True,
        description=(
            f"Permissions cache hit rate = {pct}% "
            f"(threshold: {int(PERMS_CACHE_HIT_THRESHOLD * 100)}%). "
            "Increase: shepard.permissions.cache.max-size=50000 and "
            "shepard.permissions.cache.ttl-minutes=15"
        ),
        suggested_change=(
            "shepard.permissions.cache.max-size=50000\n"
            "shepard.permissions.cache.ttl-minutes=15"
        ),
        severity=50,
    )


def _rule_mongo_latency_high(
    prom: Dict[str, Any],
) -> Optional[Suggestion]:
    """Mongo avg cmd latency > 50 ms → suggest connection pool increase."""
    avg_ms = prom.get("mongo_avg_latency_ms")
    if avg_ms is None or avg_ms <= MONGO_LATENCY_THRESHOLD_MS:
        return None
    return Suggestion(
        rank=0,
        rule="mongo_latency_high",
        safe_to_apply=True,
        description=(
            f"Mongo avg command latency = {avg_ms:.0f}ms "
            f"(threshold: {MONGO_LATENCY_THRESHOLD_MS:.0f}ms). "
            "Review Mongo connection pool: "
            "quarkus.mongodb.max-pool-size=50 (default: 100; "
            "check wiredTiger.cacheSizeGB in mongod.conf)"
        ),
        suggested_change="quarkus.mongodb.max-pool-size=50",
        severity=40,
    )


def evaluate_rules(
    k6_data: Dict[str, Any],
    prom: Dict[str, Any],
) -> List[Suggestion]:
    """Run all rules and return suggestions ranked by severity (highest first)."""
    candidates: List[Suggestion] = []

    for fn in [
        lambda: _rule_search_threshold_breach(k6_data),
        lambda: _rule_collections_threshold_breach(k6_data),
        lambda: _rule_jvm_heap_too_small(prom),
        lambda: _rule_gc_pauses_high(prom),
        lambda: _rule_permissions_cache_cold(prom),
        lambda: _rule_mongo_latency_high(prom),
    ]:
        result = fn()
        if result is not None:
            candidates.append(result)

    # Sort by severity descending; stable tiebreak preserves definition order
    candidates.sort(key=lambda s: s.severity, reverse=True)

    # Cap at MAX_SUGGESTIONS and assign ranks
    top = candidates[:MAX_SUGGESTIONS]
    for i, s in enumerate(top, start=1):
        s.rank = i

    return top


# ---------------------------------------------------------------------------
# Output formatting
# ---------------------------------------------------------------------------

def _safe_symbol(safe: bool) -> str:
    return "Yes" if safe else "No "


def format_human(
    suggestions: List[Suggestion],
    k6_path: str,
    prom_url: str,
    prom_reachable: bool,
    k6_found: bool,
) -> str:
    lines: List[str] = []
    lines.append("shepard performance recommendations")
    lines.append("=" * 36)

    k6_label = k6_path if k6_found else f"{k6_path} (not found — k6 checks skipped)"
    lines.append(f"[k6] last-run.json: {k6_label}")

    if prom_reachable:
        lines.append(f"[prometheus] {prom_url} (connected)")
    else:
        lines.append(
            f"[prometheus] {prom_url} "
            "(not reachable — Prometheus checks skipped)"
        )

    lines.append("")

    if not suggestions:
        lines.append("No recommendations — install looks healthy.")
        return "\n".join(lines)

    lines.append(f"# Recommendations ({len(suggestions)} found):")
    lines.append("")

    # Column widths
    rule_w = max(len(s.rule) for s in suggestions)
    rule_w = max(rule_w, 24)
    desc_w = 60

    header = (
        f" {'Rank':>4}  {'Rule':<{rule_w}}  {'Safe?':5}  {'Description'}"
    )
    sep = (
        f" {'':->4}  {'':->{ rule_w}}  {'':->5}  {'':->{ desc_w}}"
    )
    lines.append(header)
    lines.append(sep)

    for s in suggestions:
        safe_str = f"✓ Yes" if s.safe_to_apply else "✗ No "
        # Wrap description at desc_w chars
        desc_words = s.description.split()
        desc_lines: List[str] = []
        current = ""
        for word in desc_words:
            if current and len(current) + 1 + len(word) > desc_w:
                desc_lines.append(current)
                current = word
            else:
                current = (current + " " + word).strip() if current else word
        if current:
            desc_lines.append(current)

        first_line = (
            f" {s.rank:>4}  {s.rule:<{rule_w}}  {safe_str:5}  {desc_lines[0]}"
        )
        lines.append(first_line)
        indent = " " * (4 + 2 + rule_w + 2 + 5 + 2 + 1)
        for extra in desc_lines[1:]:
            lines.append(f"{indent}{extra}")

    return "\n".join(lines)


def format_json(suggestions: List[Suggestion]) -> str:
    out = []
    for s in suggestions:
        out.append(
            {
                "rank": s.rank,
                "rule": s.rule,
                "safe_to_apply": s.safe_to_apply,
                "description": s.description,
                "suggested_change": s.suggested_change,
            }
        )
    return json.dumps(out, indent=2)


# ---------------------------------------------------------------------------
# Utilities
# ---------------------------------------------------------------------------

def _warn(msg: str) -> None:
    print(f"[warn] {msg}", file=sys.stderr)


def _write_output(content: str, output_path: Optional[str]) -> None:
    if output_path:
        with open(output_path, "w", encoding="utf-8") as fh:
            fh.write(content)
            fh.write("\n")
    else:
        print(content)


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(
        prog="recommend.py",
        description=(
            "Read k6 last-run.json + optional Prometheus snapshot and produce "
            "ranked performance tuning suggestions for a shepard install. "
            "Exit 0 = healthy (no suggestions). Exit 1 = suggestions found. "
            "Exit 2 = error."
        ),
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
examples:
  python3 scripts/perf/recommend.py
  python3 scripts/perf/recommend.py --json
  python3 scripts/perf/recommend.py --json --output recs.json
  python3 scripts/perf/recommend.py --prometheus http://my-host:9090
  python3 scripts/perf/recommend.py --dry-run       # use canned data, no live I/O
        """,
    )
    p.add_argument(
        "--k6-summary",
        default=LAST_RUN_DEFAULT,
        metavar="FILE",
        help="Path to k6 handleSummary JSON (default: scripts/perf/last-run.json)",
    )
    p.add_argument(
        "--prometheus",
        default=os.environ.get("PROMETHEUS_URL", PROMETHEUS_DEFAULT),
        metavar="URL",
        help=(
            "Prometheus base URL for instant queries "
            "(default: $PROMETHEUS_URL or http://localhost:9090)"
        ),
    )
    p.add_argument(
        "--json",
        action="store_true",
        dest="json_output",
        help="Emit a JSON array instead of the human-readable table",
    )
    p.add_argument(
        "--output",
        metavar="FILE",
        help="Write output to FILE instead of stdout",
    )
    p.add_argument(
        "--dry-run",
        action="store_true",
        help=(
            "Use canned data (no live I/O) — useful for testing the rule "
            "catalogue and output format without a running stack"
        ),
    )
    return p


def main(argv: Optional[List[str]] = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)

    # --- Gather inputs -------------------------------------------------

    if args.dry_run:
        k6_found = True
        k6_data = DRY_RUN_K6
        prom_reachable = True
        prom_metrics = DRY_RUN_PROMETHEUS
    else:
        k6_found, k6_data = load_k6_summary(args.k6_summary)
        prom_reachable, prom_metrics = probe_prometheus(args.prometheus)

    # --- Evaluate rules ------------------------------------------------

    suggestions = evaluate_rules(k6_data, prom_metrics)

    # --- Format output -------------------------------------------------

    if args.json_output:
        content = format_json(suggestions)
    else:
        content = format_human(
            suggestions,
            k6_path=args.k6_summary,
            prom_url=args.prometheus,
            prom_reachable=prom_reachable,
            k6_found=k6_found,
        )

    try:
        _write_output(content, args.output)
    except OSError as exc:
        print(f"[error] Could not write output: {exc}", file=sys.stderr)
        return 2

    # --- Exit code: 1 if suggestions exist, 0 if healthy ---------------
    return 1 if suggestions else 0


if __name__ == "__main__":
    sys.exit(main())
