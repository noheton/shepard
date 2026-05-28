#!/usr/bin/env python3
"""
Recovery script — write per-channel `spatial:axis` + `urn:shepard:unit`
SemanticAnnotations directly into Neo4j, bypassing the post-2026-05-26
REST endpoints that aren't on the live image yet (TS-AXIS-AUTO,
TS-SEMANTIC-REST). One-shot recovery; idempotent (MERGE on the bridge
+ annotation key tuple); safe to re-run.

When the next backend cut goes live with the channel-annotation REST
+ /v2/.../channels/spatial-roles read endpoint, this data is already in
place — no re-seed needed. The script also doubles as a reference impl
for the AI plugin's channel-unit-inference task (AI1v in aidocs/16):
the suffix → QUDT IRI map below is the deterministic floor; the
ambiguous tail (BridgePosition, valve_*, welding controller *_p / *_t)
is what an AI agent should resolve from parent-DataObject context.

Usage:
    # against the running compose stack on the host that owns it
    python3 annotate-channel-axes-and-units.py \\
        --containers 1772           # numeric container id(s), comma-separated
    # or `--all-containers` to do every TimeseriesContainer in the stack

Provenance:
    Each `:SemanticAnnotation` is stamped with
    `source = "TS-AXIS-VERIFY-manual-<date>"` /
    `"TS-CHANNEL-UNITS-suffix-heuristic-<date>"` so post-hoc audits can
    distinguish recovery rows from REST-written rows.
"""
import argparse
import os
import subprocess
import sys
import uuid


Q = "http://qudt.org/vocab/unit/"

# ── spatial:axis — same map as examples/mffd-showcase/seed.py ────────────────
AXIS_ROLES_BY_FIELD = {
    "tcp_x_mm": "x", "tcp_y_mm": "y", "tcp_z_mm": "z",
    "tcp_rx_deg": "rot_a", "tcp_ry_deg": "rot_b", "tcp_rz_deg": "rot_c",
    "force_x_N": "x", "force_y_N": "y", "force_z_N": "z",
    "torque_x_Nm": "x", "torque_y_Nm": "y", "torque_z_Nm": "z",
}
SPATIAL_DEVICES = {"KUKA-LBR-iiwa-14", "AFP-AFPT-MTLH-S1"}  # MFFD demo

# ── urn:shepard:unit — suffix-first then prefix-heuristic ────────────────────
SUFFIX_TO_QUDT = {
    "_mm_s": ("MilliM-PER-SEC", "millimeter per second"),
    "_mm":   ("MilliM",         "millimeter"),
    "_um":   ("MicroM",         "micrometer"),
    "_Nm":   ("N-M",            "newton metre"),
    "_kN":   ("KiloN",          "kilonewton"),
    "_kn":   ("KiloN",          "kilonewton"),    # lower-case variant (thrust_kn)
    "_N":    ("N",              "newton"),
    "_J":    ("J",              "joule"),
    "_K":    ("K",              "kelvin"),
    "_C":    ("DEG_C",          "degree Celsius"),
    "_degC": ("DEG_C",          "degree Celsius"),
    "_deg":  ("DEG",            "degree"),
    "_bar":  ("BAR",            "bar"),
    "_psi":  ("PSI",            "pound-force per square inch"),
    "_g":    ("G",              "g-force"),
    "_Pa":   ("PA",             "pascal"),
}
PREFIX_TO_QUDT = {
    # joint angles
    "j1_": ("DEG", "joint angle"), "j2_": ("DEG", "joint angle"),
    "j3_": ("DEG", "joint angle"), "j4_": ("DEG", "joint angle"),
    "j5_": ("DEG", "joint angle"), "j6_": ("DEG", "joint angle"),
    "j7_": ("DEG", "joint angle"),
    # generic engineering prefixes
    "acc_":    ("M-PER-SEC2",  "linear acceleration"),
    "rpm_":    ("REV-PER-MIN", "revolutions per minute"),
    "mdot_":   ("KG-PER-SEC",  "mass flow rate"),
    "vib_":    ("G",           "vibration RMS (g-force)"),
    # rocket / LUMEN conventions
    "tc_":         ("K",   "thermocouple (Kelvin)"),
    "pc_":         ("BAR", "chamber pressure"),
    "p_inj_":      ("BAR", "injector pressure"),
    "p_tank_":     ("BAR", "tank pressure"),
    "t_coolant_":  ("K",   "coolant temperature (cryo)"),
    "t_lox_":      ("K",   "LOX inlet temperature"),
    "lch4_temperature": ("K", "LCH4 temperature"),
    "turbopump_bearing_temp": ("DEG_C", "turbopump bearing temp"),
    "turbopump_vibration":    ("G",     "turbopump vibration RMS"),
    "strain_":     ("UNITLESS", "strain (dimensionless)"),
}
# Resistance-welding cap codes: CM_*, W1_*, W2_*, WC_* — disambiguate
# the suffix from "_I" (current) / "_U" (voltage).  *_t, *_p are
# deliberately left ambiguous and handed to AI1v.
WELDING_CAP_TAIL = {"_I": ("A", "current (ampere)"),
                    "_U": ("V", "voltage (volt)")}

OPERATOR_USERNAME = os.environ.get("OPERATOR_USERNAME",
                                   "14bbe5f9-3d7b-4514-b83e-3c237d62e81b")  # bob


def resolve_unit(field):
    for suf, (iri, lbl) in SUFFIX_TO_QUDT.items():
        if field.endswith(suf):
            return (Q + iri, lbl, "suffix")
    for pre, (iri, lbl) in PREFIX_TO_QUDT.items():
        if field.startswith(pre):
            return (Q + iri, lbl, "prefix-heuristic")
    if field[:2] in ("CM", "W1", "W2", "WC") and len(field) >= 4 and field[2] == "_":
        tail = field[2:]
        if tail in WELDING_CAP_TAIL:
            iri, lbl = WELDING_CAP_TAIL[tail]
            return (Q + iri, lbl, "welding-cap")
    return None


def pull_channels(container_filter_sql):
    """Run psql in the timescaledb container, return list of dicts."""
    result = subprocess.run([
        "docker", "exec", "infrastructure-timescaledb-1",
        "psql", "-U", "postgres", "-d", "postgres", "-Atc",
        f"SELECT t.shepard_id, t.container_id, t.id, c.field, c.device "
        f"FROM timeseries t JOIN channel_metadata c ON c.timeseries_id=t.id "
        f"WHERE {container_filter_sql} ORDER BY t.container_id, c.field",
    ], capture_output=True, text=True, check=True)
    rows = []
    for line in result.stdout.strip().split("\n"):
        if not line:
            continue
        parts = line.split("|")
        if len(parts) != 5:
            continue
        rows.append({"shepardId": parts[0], "containerId": int(parts[1]),
                     "timeseriesId": int(parts[2]),
                     "field": parts[3], "device": parts[4]})
    return rows


def build_cypher_rows(channels, today):
    axis_rows = []
    unit_rows = []
    unit_skipped = []
    for ch in channels:
        # axis (MFFD spatial channels only)
        if ch["device"] in SPATIAL_DEVICES and ch["field"] in AXIS_ROLES_BY_FIELD:
            axis_rows.append({
                **ch,
                "role": AXIS_ROLES_BY_FIELD[ch["field"]],
                "saAppId": str(uuid.uuid4()),
            })
        # unit (every channel that has a resolvable suffix or prefix)
        unit = resolve_unit(ch["field"])
        if unit is None:
            unit_skipped.append(ch["field"])
            continue
        iri, lbl, tier = unit
        unit_rows.append({
            **ch, "unitIri": iri, "unitLabel": lbl, "tier": tier,
            "saAppId": str(uuid.uuid4()),
        })

    if not axis_rows and not unit_rows:
        return "", set(unit_skipped)

    def fmt_axis(r):
        return ("{shepardId:'%s',containerId:%d,timeseriesId:%d,"
                "role:'%s',saAppId:'%s'}" %
                (r["shepardId"], r["containerId"], r["timeseriesId"],
                 r["role"], r["saAppId"]))

    def fmt_unit(r):
        # Escape single quotes in labels just in case (none today but be safe).
        lbl = r["unitLabel"].replace("'", "\\'")
        return ("{shepardId:'%s',containerId:%d,timeseriesId:%d,"
                "unitIri:'%s',unitLabel:'%s',tier:'%s',saAppId:'%s'}" %
                (r["shepardId"], r["containerId"], r["timeseriesId"],
                 r["unitIri"], lbl, r["tier"], r["saAppId"]))

    parts = []
    if axis_rows:
        axis_arr = "[" + ",\n  ".join(fmt_axis(r) for r in axis_rows) + "]"
        parts.append(f"""WITH {axis_arr} AS axisRows
UNWIND axisRows AS r
MERGE (a:AnnotatableTimeseries {{appId: r.shepardId}})
  ON CREATE SET a.containerId = r.containerId, a.timeseriesId = r.timeseriesId
WITH a, r
MERGE (a)-[:HAS_ANNOTATION]->(sa:SemanticAnnotation {{propertyIRI: 'urn:shepard:spatial:axis', valueIRI: r.role, subjectAppId: r.shepardId}})
  ON CREATE SET
    sa.appId = r.saAppId,
    sa.propertyName = 'spatial:axis',
    sa.valueName = r.role,
    sa.subjectKind = 'Timeseries',
    sa.sourceMode = 'human',
    sa.agentUsername = '{OPERATOR_USERNAME}',
    sa.source = 'TS-AXIS-VERIFY-recovery-{today}'
RETURN count(sa) AS axis_annotations_written;""")
    if unit_rows:
        unit_arr = "[" + ",\n  ".join(fmt_unit(r) for r in unit_rows) + "]"
        parts.append(f"""WITH {unit_arr} AS unitRows
UNWIND unitRows AS r
MERGE (a:AnnotatableTimeseries {{appId: r.shepardId}})
  ON CREATE SET a.containerId = r.containerId, a.timeseriesId = r.timeseriesId
WITH a, r
MERGE (a)-[:HAS_ANNOTATION]->(sa:SemanticAnnotation {{propertyIRI: 'urn:shepard:unit', subjectAppId: r.shepardId, unitIRI: r.unitIri}})
  ON CREATE SET
    sa.appId = r.saAppId,
    sa.propertyName = 'unit',
    sa.valueIRI = r.unitIri,
    sa.valueName = r.unitLabel,
    sa.subjectKind = 'Timeseries',
    sa.sourceMode = 'human',
    sa.agentUsername = '{OPERATOR_USERNAME}',
    sa.source = 'TS-CHANNEL-UNITS-suffix-heuristic-{today}'
RETURN count(sa) AS unit_annotations_written;""")
    return "\n;\n".join(parts) + ";\n", set(unit_skipped)


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--containers", help="Comma-separated numeric container ids")
    ap.add_argument("--all-containers", action="store_true",
                    help="Annotate every TimeseriesContainer in the stack")
    ap.add_argument("--neo4j-password",
                    default=os.environ.get("NEO4J_PW"),
                    help="Defaults to $NEO4J_PW or extracted from the running container")
    ap.add_argument("--dry-run", action="store_true",
                    help="Emit the Cypher to stdout without executing")
    ap.add_argument("--today", default="2026-05-28",
                    help="Date stamp written into SemanticAnnotation.source")
    args = ap.parse_args()

    if not (args.containers or args.all_containers):
        ap.error("--containers or --all-containers required")

    if args.containers:
        ids = ",".join(s.strip() for s in args.containers.split(","))
        where = f"t.container_id IN ({ids})"
    else:
        where = "TRUE"

    pw = args.neo4j_password
    if not pw:
        result = subprocess.run(
            ["docker", "exec", "infrastructure-neo4j-1", "sh", "-c",
             "echo $NEO4J_AUTH"],
            capture_output=True, text=True, check=True)
        pw = result.stdout.strip().split("/", 1)[1]

    channels = pull_channels(where)
    print(f"-- {len(channels)} channels surveyed", file=sys.stderr)
    cypher, skipped = build_cypher_rows(channels, args.today)
    if skipped:
        print(f"-- {len(skipped)} unique field name(s) skipped as ambiguous "
              f"(handed to AI1v):", file=sys.stderr)
        for f in sorted(skipped):
            print(f"    SKIP {f}", file=sys.stderr)
    if not cypher.strip():
        print("-- nothing to write", file=sys.stderr)
        return 0

    if args.dry_run:
        sys.stdout.write(cypher)
        return 0

    proc = subprocess.run(
        ["docker", "exec", "-i", "infrastructure-neo4j-1",
         "cypher-shell", "-u", "neo4j", "-p", pw],
        input=cypher, capture_output=True, text=True)
    print(proc.stdout)
    if proc.returncode != 0:
        print(proc.stderr, file=sys.stderr)
    return proc.returncode


if __name__ == "__main__":
    sys.exit(main())
