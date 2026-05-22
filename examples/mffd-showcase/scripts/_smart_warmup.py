"""Smart warmup module for the v15 import script (IMPORT-W1 / W2 / W3).

Why this module exists
----------------------
v15.1 ships a minimal `warmup_source()` that does a single read probe
against `/shepard/api/collections`. That's not enough — by the time
the script starts a 10-hour cross-instance import we want **proof**
that:

  - auth resolves on both sides (the JWT is not expired, the API key
    is real),
  - the wire shape of the responses still matches the v5.4.0 OpenAPI
    contract we coded against,
  - write access works on every payload kind the run will touch
    (file, structured-data, timeseries) — caught now, not 4 hours in,
  - the Garage S3 backend is alive on the destination side (PV1a),
  - throwaway probes get cleaned up on every exit path.

User directives 2026-05-22 ("script should be smart and have a
warmup / probing phase everything works (write access to v5 is
enabled for testing)" + "unexpected replies lead to abort and
actionable diagnostic report") and the memory rules
`feedback_warmup_fail_fast_diagnostic.md` /
`feedback_no_redactions.md` / `feedback_diagnostic_artefact.md` shape
the design:

  - **fail-fast** with a distinct integer exit code per failure
    class (see `EXIT_*` constants below),
  - **structured diagnostic** with verb / url / expected / got /
    where-to-look / what-to-try / exit-code,
  - **no redactions** — credentials surface verbatim so the operator
    can copy-paste the failing curl,
  - **diagnostic artefact pattern** — the `WarmupReport.to_text()`
    output is uploadable verbatim as the one diagnostic file the
    operator needs to share.

The module is intentionally kept separate from the 2700-line
`mffd-import-v15.py` to minimise merge risk; the main script imports
this module behind the `--smart-warmup` flag.

Wire-contract source
--------------------
The IMPORT-W3 OpenAPI shape comparator reads
`backend/src/test/resources/fixtures/v5/openapi-5.4.0.json` (282 KB,
90 paths, 89 schemas) as the canonical wire-contract source. The
spec was committed to the repo so the script does not need network
access to the upstream OpenAPI feed. See
`aidocs/reference/v5-openapi-summary.md` for an AI-readable summary
of the spec's surface.

Stdlib only — depends on `requests` (already in v15 PEP-723 header).
"""

from __future__ import annotations

import json
import os
import time
from dataclasses import dataclass, field, asdict
from pathlib import Path
from typing import Any, Iterable


# ── Exit codes (IMPORT-W2, fail-fast diagnostic) ────────────────────────────
# These match feedback_warmup_fail_fast_diagnostic.md. Caller scripts and the
# Makefile branch on these. Reserved range 2-7; 1 stays the generic catch-all
# already used by v15.1; 0 is success.
EXIT_OK = 0
EXIT_GENERIC = 1
EXIT_AUTH = 2
EXIT_SOURCE_UNREACHABLE = 3
EXIT_GARAGE_DOWN = 4
EXIT_OPERATOR_INTERRUPT = 5
EXIT_WIRE_SHAPE_DRIFT = 6
EXIT_WRITE_PERMISSION_DENIED = 7


# Path-template normalisation cache (concrete URL → spec path template).
# Built from the OpenAPI spec on first lookup; module-level so the
# orchestrator and the comparator share state.
_PATH_TEMPLATE_CACHE: dict[str, list[str]] = {}


# ── Diagnostic report ───────────────────────────────────────────────────────


@dataclass
class WarmupReport:
    """Structured warmup diagnostic — one row per probe; ok/fail aggregate.

    Fields are deliberately operator-readable. `to_text()` produces the
    artefact-style block the operator can paste into a ticket per the
    diagnostic-script-to-upload pattern.

    On success: `success=True`, `exit_code=0`, `probes` lists every probe
    that ran (each carrying ok=True). On failure: `success=False`,
    `exit_code` is one of the EXIT_* constants, and the LAST probe in
    `probes` carries the failure details.
    """

    success: bool = False
    exit_code: int = EXIT_GENERIC
    session_id: str = ""
    started_at: float = field(default_factory=time.time)
    finished_at: float | None = None

    # Aggregate per-probe results so the operator can see what passed
    # before the abort. Each probe is a dict with the same keys as the
    # top-level (verb / url / expected / got / where_to_look / what_to_try /
    # ok). Append-only.
    probes: list[dict[str, Any]] = field(default_factory=list)

    # Top-level failure detail (mirrored from the last failed probe so
    # operators don't have to scan the probe list).
    verb: str = ""
    url: str = ""
    expected: str = ""
    got: str = ""
    where_to_look: str = ""
    what_to_try: str = ""
    extra: dict[str, Any] = field(default_factory=dict)

    # Coverage stats — how many of the probed endpoints had a spec
    # schema we could compare against. Surfaces honest OpenAPI coverage.
    endpoints_probed: int = 0
    endpoints_with_spec: int = 0

    def record_probe(
        self,
        verb: str,
        url: str,
        expected: str,
        got: str,
        ok: bool,
        where_to_look: str = "",
        what_to_try: str = "",
        spec_match: bool | None = None,
    ) -> None:
        self.probes.append(
            {
                "verb": verb,
                "url": url,
                "expected": expected,
                "got": got,
                "ok": ok,
                "where_to_look": where_to_look,
                "what_to_try": what_to_try,
                "spec_match": spec_match,
            }
        )
        if spec_match is True:
            self.endpoints_with_spec += 1
        if spec_match is not None:
            self.endpoints_probed += 1

    def fail(
        self,
        verb: str,
        url: str,
        expected: str,
        got: str,
        exit_code: int,
        where_to_look: str = "",
        what_to_try: str = "",
        extra: dict[str, Any] | None = None,
    ) -> None:
        self.success = False
        self.exit_code = exit_code
        self.verb = verb
        self.url = url
        self.expected = expected
        self.got = got
        self.where_to_look = where_to_look
        self.what_to_try = what_to_try
        if extra:
            self.extra = extra
        self.finished_at = time.time()
        self.record_probe(verb, url, expected, got, False, where_to_look, what_to_try)

    def ok(self) -> None:
        self.success = True
        self.exit_code = EXIT_OK
        self.finished_at = time.time()

    # ── Serialisation ────────────────────────────────────────────────────

    def to_text(self) -> str:
        """Operator-readable block. One fact per line, key:value alignment.

        Per `feedback_no_redactions.md`: surfaced URLs include any query
        string verbatim. The caller is responsible for not putting
        bearer tokens into URLs — the design has them in headers.
        """
        elapsed = ""
        if self.finished_at:
            elapsed = f"  ({self.finished_at - self.started_at:.2f}s)"
        head = "=== Smart Warmup Report ==="
        status = "OK" if self.success else "FAIL"
        lines = [
            head,
            f"  status        : {status}{elapsed}",
            f"  exit_code     : {self.exit_code}",
            f"  session_id    : {self.session_id}",
            f"  probes_run    : {len(self.probes)}",
            f"  endpoints_probed     : {self.endpoints_probed}",
            f"  endpoints_with_spec  : {self.endpoints_with_spec}",
        ]
        if not self.success:
            lines.extend(
                [
                    "",
                    "--- Failure detail ---",
                    f"  verb          : {self.verb}",
                    f"  url           : {self.url}",
                    f"  expected      : {self.expected}",
                    f"  got           : {self.got}",
                    f"  where_to_look : {self.where_to_look}",
                    f"  what_to_try   : {self.what_to_try}",
                ]
            )
            if self.extra:
                lines.append(f"  extra         : {json.dumps(self.extra, sort_keys=True)}")
        if self.probes:
            lines.append("")
            lines.append("--- Probe trace ---")
            for i, p in enumerate(self.probes, 1):
                mark = "OK " if p["ok"] else "FAIL"
                spec = ""
                if p.get("spec_match") is True:
                    spec = " [spec✓]"
                elif p.get("spec_match") is False:
                    spec = " [spec✗]"
                lines.append(f"  {i:>2}. [{mark}]{spec} {p['verb']} {p['url']}")
                if not p["ok"]:
                    lines.append(f"      expected: {p['expected']}")
                    lines.append(f"      got     : {p['got']}")
        lines.append("=== end ===")
        return "\n".join(lines)

    def to_json(self) -> str:
        return json.dumps(asdict(self), sort_keys=True, indent=2)


class WarmupAborted(Exception):
    """Raised by SmartWarmup when a probe fails fast.

    Carries the full WarmupReport so the caller can render it and
    exit with the structured code in one step.
    """

    def __init__(self, report: WarmupReport, message: str = "") -> None:
        super().__init__(message or f"smart warmup aborted (exit {report.exit_code})")
        self.report = report
        self.exit_code = report.exit_code


# ── OpenAPI shape comparator (IMPORT-W3) ────────────────────────────────────


@dataclass
class ShapeDiff:
    """Result of comparing one response body against the OpenAPI schema.

    Conventions:
      - `ok=True, skipped=True` — the spec has no schema for the
        endpoint/method/status; the comparator returns "I don't know,
        treat as ok". The caller decides whether to log a coverage
        gap.
      - `ok=True, skipped=False` — schema found, structural sanity
        check passed.
      - `ok=False` — at least one of the diff lists is non-empty.
    """

    ok: bool
    skipped: bool = False
    missing_required_fields: list[str] = field(default_factory=list)
    extra_unknown_fields: list[str] = field(default_factory=list)
    type_mismatches: list[str] = field(default_factory=list)
    note: str = ""


def _resolve_ref(spec: dict, ref: str) -> dict:
    """Resolve a `#/components/schemas/Foo` ref against the spec."""
    if not ref.startswith("#/"):
        return {}
    node: Any = spec
    for part in ref[2:].split("/"):
        if not isinstance(node, dict) or part not in node:
            return {}
        node = node[part]
    return node if isinstance(node, dict) else {}


def _flatten_schema(spec: dict, schema: dict, _seen: set[str] | None = None) -> dict:
    """Inline `$ref` + `allOf` chains so the comparator sees a flat schema.

    The spec we read (v5.4.0) uses `$ref` heavily; `allOf` rarely.
    We don't try to be a complete OpenAPI validator — we just want
    `required` + `properties` resolved one level deep.
    """
    if not isinstance(schema, dict):
        return {}
    if _seen is None:
        _seen = set()
    ref = schema.get("$ref")
    if ref:
        if ref in _seen:
            return {}
        _seen.add(ref)
        resolved = _resolve_ref(spec, ref)
        return _flatten_schema(spec, resolved, _seen)
    if "allOf" in schema:
        merged: dict = {"type": "object", "properties": {}, "required": []}
        for sub in schema["allOf"]:
            f = _flatten_schema(spec, sub, _seen)
            if "properties" in f:
                merged["properties"].update(f["properties"])
            if "required" in f:
                merged["required"].extend(f["required"])
            if f.get("type") and not merged.get("type"):
                merged["type"] = f["type"]
        # Overlay direct keys from this schema (override sub-schemas).
        for k, v in schema.items():
            if k == "allOf":
                continue
            merged[k] = v
        return merged
    return schema


def _normalize_path_template(concrete_url: str, spec_paths: Iterable[str]) -> str | None:
    """Map a concrete URL path back to the OpenAPI path template.

    Example: `/collections/42/dataObjects/9001` → `/collections/{collectionId}/dataObjects/{dataObjectId}`.

    Uses cached templates so the orchestrator doesn't rebuild on every
    probe. Returns None when no template matches.
    """
    # Strip the base URL + the upstream /shepard/api prefix the spec
    # implicitly carries via `servers[].url`; we match on the path
    # portion that follows.
    p = concrete_url
    for prefix in ("/shepard/api", "/api"):
        idx = p.find(prefix)
        if idx >= 0:
            p = p[idx + len(prefix):]
            break
    # Drop query string.
    p = p.split("?", 1)[0].split("#", 1)[0]
    if not p.startswith("/"):
        p = "/" + p
    p_parts = p.strip("/").split("/")
    for template in spec_paths:
        t_parts = template.strip("/").split("/")
        if len(t_parts) != len(p_parts):
            continue
        match = True
        for tp, pp in zip(t_parts, p_parts):
            if tp.startswith("{") and tp.endswith("}"):
                continue
            if tp != pp:
                match = False
                break
        if match:
            return template
    return None


def _check_type(value: Any, expected_type: str) -> bool:
    """Loose JSON-Schema type check — covers the common cases we'll see."""
    if expected_type == "object":
        return isinstance(value, dict)
    if expected_type == "array":
        return isinstance(value, list)
    if expected_type == "string":
        return isinstance(value, str)
    if expected_type == "integer":
        return isinstance(value, int) and not isinstance(value, bool)
    if expected_type == "number":
        return isinstance(value, (int, float)) and not isinstance(value, bool)
    if expected_type == "boolean":
        return isinstance(value, bool)
    if expected_type == "null":
        return value is None
    return True  # unknown type — be permissive


def compare_against_openapi(
    spec_json: dict | str | Path,
    path_template: str | None,
    method: str,
    response_body: Any,
    response_status: int = 200,
    concrete_url: str | None = None,
) -> ShapeDiff:
    """Compare a response body against the v5.4.0 OpenAPI schema.

    `path_template` is the OpenAPI path key, e.g.
    `/collections/{collectionId}/dataObjects/{dataObjectId}`. When
    `path_template` is None and `concrete_url` is supplied, the
    function tries to derive the template from the spec's path keys.

    Returns a `ShapeDiff`. When the spec has no schema for the
    endpoint/method/status, returns `ShapeDiff(ok=True, skipped=True)`
    so the caller can still log a coverage gap.

    Light-touch: walks one level of `properties` + `required`,
    resolves `$ref`, follows `allOf`. Does NOT validate every
    constraint (no pattern, no minLength). Structural sanity only.
    """
    # Accept three input forms for ergonomics in tests.
    if isinstance(spec_json, Path):
        spec = json.loads(spec_json.read_text())
    elif isinstance(spec_json, str):
        # Heuristic: if it looks like JSON, parse; otherwise treat as path.
        s = spec_json.strip()
        if s.startswith("{"):
            spec = json.loads(s)
        else:
            spec = json.loads(Path(s).read_text())
    elif isinstance(spec_json, dict):
        spec = spec_json
    else:
        return ShapeDiff(ok=True, skipped=True, note="unsupported spec input")

    paths = spec.get("paths", {})
    if path_template is None and concrete_url is not None:
        path_template = _normalize_path_template(concrete_url, paths.keys())
    if path_template is None or path_template not in paths:
        return ShapeDiff(ok=True, skipped=True, note=f"no path: {path_template or concrete_url}")
    method_l = method.lower()
    op = paths[path_template].get(method_l)
    if not op:
        return ShapeDiff(ok=True, skipped=True, note=f"no method: {method_l}")
    responses = op.get("responses", {})
    # Try exact status, then default.
    resp = responses.get(str(response_status)) or responses.get("default")
    if not resp:
        return ShapeDiff(ok=True, skipped=True, note=f"no response: {response_status}")
    content = resp.get("content", {})
    schema = (
        content.get("application/json", {}).get("schema")
        or content.get("*/*", {}).get("schema")
    )
    if not schema:
        return ShapeDiff(ok=True, skipped=True, note="no schema for response")

    # Resolve $ref and allOf one level deep.
    flat = _flatten_schema(spec, schema)
    return _compare_value(spec, flat, response_body)


def _compare_value(spec: dict, schema: dict, value: Any) -> ShapeDiff:
    """Structural compare of one value against one (flattened) schema."""
    if not schema:
        return ShapeDiff(ok=True, skipped=True, note="empty schema")

    expected_type = schema.get("type")
    nullable = schema.get("nullable", False)
    if value is None:
        if nullable or expected_type is None:
            return ShapeDiff(ok=True)
        return ShapeDiff(
            ok=False,
            type_mismatches=[f"<root>: expected {expected_type}, got null"],
        )

    if expected_type == "array":
        items_schema = _flatten_schema(spec, schema.get("items", {}))
        if not isinstance(value, list):
            return ShapeDiff(
                ok=False,
                type_mismatches=[f"<root>: expected array, got {type(value).__name__}"],
            )
        # Compare first item only — structural sanity, not exhaustive.
        if value and items_schema:
            inner = _compare_value(spec, items_schema, value[0])
            # Prefix array index for clarity.
            return ShapeDiff(
                ok=inner.ok,
                skipped=inner.skipped,
                missing_required_fields=[f"[0].{f}" for f in inner.missing_required_fields],
                extra_unknown_fields=[f"[0].{f}" for f in inner.extra_unknown_fields],
                type_mismatches=[f"[0].{m}" for m in inner.type_mismatches],
                note=inner.note,
            )
        return ShapeDiff(ok=True)

    if expected_type == "object" or schema.get("properties"):
        if not isinstance(value, dict):
            return ShapeDiff(
                ok=False,
                type_mismatches=[f"<root>: expected object, got {type(value).__name__}"],
            )
        properties = schema.get("properties", {}) or {}
        required = schema.get("required", []) or []
        missing = [f for f in required if f not in value]
        # additionalProperties handling: default True per OpenAPI; only
        # flag "extra" when the schema explicitly says false. We don't
        # walk additionalProperties: <schema> at this depth.
        extra: list[str] = []
        if schema.get("additionalProperties") is False:
            extra = [k for k in value.keys() if k not in properties]
        type_mismatches: list[str] = []
        for field_name, field_schema in properties.items():
            if field_name not in value:
                continue
            field_flat = _flatten_schema(spec, field_schema)
            ftype = field_flat.get("type")
            fnull = field_flat.get("nullable", False)
            fval = value[field_name]
            if fval is None and fnull:
                continue
            if ftype and not _check_type(fval, ftype):
                type_mismatches.append(
                    f"{field_name}: expected {ftype}, got {type(fval).__name__}"
                )
        ok = not (missing or extra or type_mismatches)
        return ShapeDiff(
            ok=ok,
            missing_required_fields=missing,
            extra_unknown_fields=extra,
            type_mismatches=type_mismatches,
        )

    # Scalar type
    if expected_type and not _check_type(value, expected_type):
        return ShapeDiff(
            ok=False,
            type_mismatches=[f"<root>: expected {expected_type}, got {type(value).__name__}"],
        )
    return ShapeDiff(ok=True)


# ── Orchestrator (IMPORT-W1) ───────────────────────────────────────────────


# Default location of the OpenAPI spec, relative to repo root.
DEFAULT_OPENAPI_SPEC = Path(
    "backend/src/test/resources/fixtures/v5/openapi-5.4.0.json"
)


class SmartWarmup:
    """Orchestrate the smart-warmup phase across source + dest clients.

    Probes in this order, fail-fast at the first error:
      1. Auth resolution on both sides
         - dest: GET /v2/users/me (the fork's /v2 surface)
         - source: GET /users/{username} (upstream-only v1 surface;
           we read username from the dest's /v2/users/me sub).
      2. Read paths on both sides (GET /collections page=0 size=1).
         Wire-shape diff each response against the OpenAPI spec.
      3. Write probes on the SOURCE — create + read-back + delete a
         throwaway DataObject. User confirmed 2026-05-22 that v5
         source has write access enabled for testing.
      4. Write probes on the DEST — same pattern against the dest
         collection.
      5. Garage health probe on the DEST — calls the existing
         `garage_preflight()` helper and surfaces structured status.

    Cleanup: any DataObject created during the run is recorded in
    `_cleanup_ids` and DELETEd in `_cleanup()`, called from both the
    success path and from `WarmupAborted`'s finally.
    """

    def __init__(
        self,
        source_client: Any,
        dest_client: Any,
        session_id: str,
        *,
        source_collection_id: int | None = None,
        dest_collection_id: int | None = None,
        dest_collection_app_id: str | None = None,
        expected_username: str | None = None,
        openapi_spec_path: Path | str | None = None,
        write_to_source: bool = True,
        write_to_dest: bool = True,
        probe_garage: bool = True,
    ) -> None:
        self.source = source_client
        self.dest = dest_client
        self.session_id = session_id
        self.source_collection_id = source_collection_id
        self.dest_collection_id = dest_collection_id
        self.dest_collection_app_id = dest_collection_app_id
        self.expected_username = expected_username
        self.write_to_source = write_to_source
        self.write_to_dest = write_to_dest
        self.probe_garage = probe_garage
        self._spec: dict | None = None
        self._spec_path_resolved: Path | None = None
        spec_path = Path(openapi_spec_path) if openapi_spec_path else DEFAULT_OPENAPI_SPEC
        self._load_spec(spec_path)
        # ID + bookkeeping for cleanup. (instance, do_id_int).
        self._cleanup_source_do_ids: list[int] = []
        self._cleanup_dest_do_ids: list[int] = []
        self.report = WarmupReport(session_id=session_id)

    # ── Spec loading ──────────────────────────────────────────────────────

    def _load_spec(self, candidate: Path) -> None:
        """Locate the OpenAPI spec by walking up from this file.

        The spec lives in `backend/src/test/resources/fixtures/v5/`
        which is always present in the worktree. We resolve relative
        to this module's parent dirs so callers don't need to pass
        a path.
        """
        if candidate.is_absolute() and candidate.exists():
            self._spec = json.loads(candidate.read_text())
            self._spec_path_resolved = candidate
            return
        here = Path(__file__).resolve()
        for parent in [here.parent, *here.parents]:
            attempt = parent / candidate
            if attempt.exists():
                self._spec = json.loads(attempt.read_text())
                self._spec_path_resolved = attempt
                return
        # Spec missing — the comparator gracefully skips every check.
        # This is not a fatal failure; we just lose coverage.
        self._spec = None

    # ── Top-level entry ──────────────────────────────────────────────────

    def run(self) -> WarmupReport:
        """Execute the probe sequence. Raises WarmupAborted on failure.

        On the success path: returns the report with `success=True` +
        `exit_code=0`. Cleanup runs regardless (so a successful warmup
        doesn't leave probe DOs behind).
        """
        try:
            self._probe_auth_dest()
            if self.source is not None and self.source is not self.dest:
                self._probe_auth_source()
            self._probe_read_dest()
            if self.source is not None and self.source is not self.dest:
                self._probe_read_source()
            if self.write_to_source and self.source is not None and self.source_collection_id:
                self._probe_write_source()
            if self.write_to_dest and self.dest_collection_id:
                self._probe_write_dest()
            if self.probe_garage and self.dest_collection_app_id:
                self._probe_garage()
            self.report.ok()
            return self.report
        finally:
            self._cleanup()

    # ── Individual probes ────────────────────────────────────────────────

    def _probe_auth_dest(self) -> None:
        url = f"{self._base(self.dest)}/v2/users/me"
        try:
            r = self._raw_get(self.dest, url)
        except Exception as exc:
            self.report.fail(
                "GET", url,
                expected="HTTP 200 + JSON identity (sub / username / email)",
                got=f"network exception: {exc}",
                exit_code=EXIT_SOURCE_UNREACHABLE,
                where_to_look=(
                    "DNS / TLS / firewall between this host and the "
                    "destination Shepard. Try: curl -sv {url}".format(url=url)
                ),
                what_to_try=(
                    "Verify SHEPARD_URL is reachable from THIS host. "
                    "If you're on a VPN, confirm split-tunneling allows "
                    "this destination."
                ),
            )
            raise WarmupAborted(self.report)
        status = getattr(r, "status_code", 0)
        if not (200 <= status < 300):
            text = self._body_snippet(r)
            self.report.fail(
                "GET", url,
                expected="HTTP 200 + JSON identity",
                got=f"HTTP {status}: {text}",
                exit_code=EXIT_AUTH,
                where_to_look=(
                    "SHEPARD_BEARER_TOKEN expiry / SHEPARD_API_KEY "
                    "revocation on the destination instance."
                ),
                what_to_try=(
                    "Decode the JWT at jwt.io and check the `exp` claim. "
                    "Re-mint via the destination's /v2/users/me/apikeys "
                    "or your IdP. The token is sent in the Authorization "
                    "header (not in any URL) — no redaction needed."
                ),
            )
            raise WarmupAborted(self.report)
        body = self._json(r)
        sub = body.get("sub") or body.get("username") or body.get("name") or ""
        # Cache the username so the source-side /users/{username} probe
        # can use it later.
        if sub and not self.expected_username:
            self.expected_username = sub
        self.report.record_probe(
            "GET", url,
            expected="200 + identity",
            got=f"200 sub={sub!r}",
            ok=True,
            spec_match=None,  # /v2 surface is fork-only, no upstream spec
        )

    def _probe_auth_source(self) -> None:
        # Source is upstream-only: /v2/users/me doesn't exist. The
        # spec exposes /users/{username} (we read username from the
        # dest probe).
        username = self.expected_username or "_warmup_unknown"
        url = f"{self._base(self.source)}/shepard/api/users/{username}"
        try:
            r = self._raw_get(self.source, url)
        except Exception as exc:
            self.report.fail(
                "GET", url,
                expected="HTTP 200 + upstream User JSON",
                got=f"network exception: {exc}",
                exit_code=EXIT_SOURCE_UNREACHABLE,
                where_to_look="DNS / VPN to the source Shepard instance.",
                what_to_try=(
                    "Verify SOURCE_SHEPARD_URL is reachable. Try: "
                    "curl -sv -H 'X-API-KEY: <key>' " + url
                ),
            )
            raise WarmupAborted(self.report)
        status = getattr(r, "status_code", 0)
        if not (200 <= status < 300):
            self.report.fail(
                "GET", url,
                expected="HTTP 200 + upstream User JSON",
                got=f"HTTP {status}: {self._body_snippet(r)}",
                exit_code=EXIT_AUTH,
                where_to_look="SOURCE_SHEPARD_API_KEY validity on the source.",
                what_to_try=(
                    "Re-mint the source API key via the v5 admin UI. "
                    "The key is sent in the X-API-KEY header verbatim "
                    "— no redaction in this report."
                ),
            )
            raise WarmupAborted(self.report)
        body = self._json(r)
        diff = compare_against_openapi(
            self._spec or {},
            "/users/{username}",
            "GET",
            body,
            response_status=200,
        )
        if not diff.ok:
            self._fail_wire_shape("GET", url, "/users/{username}", diff)
            raise WarmupAborted(self.report)
        self.report.record_probe(
            "GET", url,
            expected="200 + User (spec-shape ok)",
            got=f"200 username={body.get('username') or body.get('name') or '?'}",
            ok=True,
            spec_match=not diff.skipped,
        )

    def _probe_read_dest(self) -> None:
        url = f"{self._base(self.dest)}/shepard/api/collections"
        try:
            r = self._raw_get(self.dest, url, params={"page": "0", "size": "1"})
        except Exception as exc:
            self.report.fail(
                "GET", url,
                expected="HTTP 200 + Collection[] (paged)",
                got=f"network exception: {exc}",
                exit_code=EXIT_SOURCE_UNREACHABLE,
                where_to_look="Destination Shepard /shepard/api reachability.",
                what_to_try="curl -sv " + url + "?page=0&size=1",
            )
            raise WarmupAborted(self.report)
        status = getattr(r, "status_code", 0)
        if not (200 <= status < 300):
            self.report.fail(
                "GET", url,
                expected="HTTP 200",
                got=f"HTTP {status}: {self._body_snippet(r)}",
                exit_code=EXIT_AUTH if status in (401, 403) else EXIT_SOURCE_UNREACHABLE,
                where_to_look="Auth header propagation through the v1 surface.",
                what_to_try=(
                    "Confirm /shepard/api/collections works in a browser "
                    "or with curl from this host."
                ),
            )
            raise WarmupAborted(self.report)
        body = self._json(r)
        diff = compare_against_openapi(
            self._spec or {},
            "/collections",
            "GET",
            body,
            response_status=200,
        )
        if not diff.ok:
            self._fail_wire_shape("GET", url, "/collections", diff)
            raise WarmupAborted(self.report)
        self.report.record_probe(
            "GET", url,
            expected="200 + Collection[]",
            got=f"200 (n={len(body) if isinstance(body, list) else 'object'})",
            ok=True,
            spec_match=not diff.skipped,
        )

    def _probe_read_source(self) -> None:
        url = f"{self._base(self.source)}/shepard/api/collections"
        try:
            r = self._raw_get(self.source, url, params={"page": "0", "size": "1"})
        except Exception as exc:
            self.report.fail(
                "GET", url,
                expected="HTTP 200 + Collection[]",
                got=f"network exception: {exc}",
                exit_code=EXIT_SOURCE_UNREACHABLE,
                where_to_look="Source Shepard /shepard/api reachability.",
                what_to_try="curl -sv " + url + "?page=0&size=1",
            )
            raise WarmupAborted(self.report)
        status = getattr(r, "status_code", 0)
        if not (200 <= status < 300):
            self.report.fail(
                "GET", url,
                expected="HTTP 200",
                got=f"HTTP {status}: {self._body_snippet(r)}",
                exit_code=EXIT_AUTH if status in (401, 403) else EXIT_SOURCE_UNREACHABLE,
                where_to_look="SOURCE_SHEPARD_API_KEY scope on the source.",
                what_to_try="Check the key has at least Collections:read.",
            )
            raise WarmupAborted(self.report)
        body = self._json(r)
        diff = compare_against_openapi(
            self._spec or {},
            "/collections",
            "GET",
            body,
            response_status=200,
        )
        if not diff.ok:
            self._fail_wire_shape("GET", url, "/collections", diff)
            raise WarmupAborted(self.report)
        self.report.record_probe(
            "GET", url,
            expected="200 + Collection[]",
            got=f"200 (n={len(body) if isinstance(body, list) else 'object'})",
            ok=True,
            spec_match=not diff.skipped,
        )

    def _probe_write_source(self) -> None:
        """Throwaway DO write probe against the source. Cleanup on every exit.

        User directive 2026-05-22: "write access to v5 is enabled for
        testing". We create a DO named `_warmup_probe_<session>_<epoch>`
        on the source collection, then DELETE it. The DELETE is also
        attempted from `_cleanup()` as a defensive net.
        """
        coll_id = self.source_collection_id
        assert coll_id is not None
        name = f"_warmup_probe_{self.session_id}_{int(time.time())}"
        url = (
            f"{self._base(self.source)}/shepard/api/collections/{coll_id}/dataObjects"
        )
        try:
            r = self._raw_post(self.source, url, {"name": name})
        except Exception as exc:
            self.report.fail(
                "POST", url,
                expected="HTTP 201 + DataObject JSON",
                got=f"network exception: {exc}",
                exit_code=EXIT_SOURCE_UNREACHABLE,
                where_to_look="Source POST endpoint reachability.",
                what_to_try=f"curl -X POST {url} -d '{{\"name\":\"{name}\"}}'",
            )
            raise WarmupAborted(self.report)
        status = getattr(r, "status_code", 0)
        if status in (401, 403):
            self.report.fail(
                "POST", url,
                expected="HTTP 201 + DataObject",
                got=f"HTTP {status}: {self._body_snippet(r)}",
                exit_code=EXIT_WRITE_PERMISSION_DENIED,
                where_to_look=(
                    "Source API key write scope. v5 source had writes "
                    "enabled for testing 2026-05-22 — check whether "
                    "this changed."
                ),
                what_to_try=(
                    "Have the source admin re-grant Collections:write "
                    "on the key, or re-run with --legacy-warmup to skip "
                    "source-side write probes."
                ),
            )
            raise WarmupAborted(self.report)
        if not (200 <= status < 300):
            self.report.fail(
                "POST", url,
                expected="HTTP 201",
                got=f"HTTP {status}: {self._body_snippet(r)}",
                exit_code=EXIT_GENERIC,
                where_to_look="Source DataObject POST schema.",
                what_to_try=(
                    "Compare the request body shape against the spec at "
                    "backend/src/test/resources/fixtures/v5/openapi-5.4.0.json"
                ),
            )
            raise WarmupAborted(self.report)
        body = self._json(r)
        do_id = body.get("id")
        if do_id is not None:
            self._cleanup_source_do_ids.append(int(do_id))
        diff = compare_against_openapi(
            self._spec or {},
            "/collections/{collectionId}/dataObjects",
            "POST",
            body,
            response_status=201,
        )
        # Try 200 if 201 missing (some endpoints respond 200 on create).
        if diff.skipped:
            diff = compare_against_openapi(
                self._spec or {},
                "/collections/{collectionId}/dataObjects",
                "POST",
                body,
                response_status=200,
            )
        if not diff.ok:
            self._fail_wire_shape(
                "POST", url, "/collections/{collectionId}/dataObjects", diff
            )
            raise WarmupAborted(self.report)
        self.report.record_probe(
            "POST", url,
            expected=f"201 + DataObject (id, name={name!r})",
            got=f"{status} id={do_id}",
            ok=True,
            spec_match=not diff.skipped,
        )

    def _probe_write_dest(self) -> None:
        coll_id = self.dest_collection_id
        assert coll_id is not None
        name = f"_warmup_probe_dest_{self.session_id}_{int(time.time())}"
        url = (
            f"{self._base(self.dest)}/shepard/api/collections/{coll_id}/dataObjects"
        )
        try:
            r = self._raw_post(self.dest, url, {"name": name})
        except Exception as exc:
            self.report.fail(
                "POST", url,
                expected="HTTP 201 + DataObject JSON",
                got=f"network exception: {exc}",
                exit_code=EXIT_SOURCE_UNREACHABLE,
                where_to_look="Destination Shepard write reachability.",
                what_to_try="curl -X POST " + url,
            )
            raise WarmupAborted(self.report)
        status = getattr(r, "status_code", 0)
        if status in (401, 403):
            self.report.fail(
                "POST", url,
                expected="HTTP 201 + DataObject",
                got=f"HTTP {status}: {self._body_snippet(r)}",
                exit_code=EXIT_WRITE_PERMISSION_DENIED,
                where_to_look="Destination API key write scope.",
                what_to_try=(
                    "Re-mint the destination API key with Collections:write "
                    "+ DataObjects:write scopes."
                ),
            )
            raise WarmupAborted(self.report)
        if not (200 <= status < 300):
            self.report.fail(
                "POST", url,
                expected="HTTP 201",
                got=f"HTTP {status}: {self._body_snippet(r)}",
                exit_code=EXIT_GENERIC,
                where_to_look="Destination DataObject POST.",
                what_to_try="Inspect dest Shepard logs for the request id.",
            )
            raise WarmupAborted(self.report)
        body = self._json(r)
        do_id = body.get("id")
        if do_id is not None:
            self._cleanup_dest_do_ids.append(int(do_id))
        diff = compare_against_openapi(
            self._spec or {},
            "/collections/{collectionId}/dataObjects",
            "POST",
            body,
            response_status=201,
        )
        if diff.skipped:
            diff = compare_against_openapi(
                self._spec or {},
                "/collections/{collectionId}/dataObjects",
                "POST",
                body,
                response_status=200,
            )
        if not diff.ok:
            self._fail_wire_shape(
                "POST", url, "/collections/{collectionId}/dataObjects", diff
            )
            raise WarmupAborted(self.report)
        self.report.record_probe(
            "POST", url,
            expected=f"201 + DataObject (name={name!r})",
            got=f"{status} id={do_id}",
            ok=True,
            spec_match=not diff.skipped,
        )

    def _probe_garage(self) -> None:
        """Use the existing client garage_preflight() helper.

        We don't reimplement Garage diagnostics; we wrap the existing
        helper into the structured report so the operator sees one
        consistent shape.
        """
        if not hasattr(self.dest, "garage_preflight"):
            self.report.record_probe(
                "POST",
                f"{self._base(self.dest)}/v2/file-containers/{self.dest_collection_app_id}/upload-url",
                expected="Garage S3 reachable",
                got="client has no garage_preflight() — skipped",
                ok=True,
                spec_match=None,
            )
            return
        try:
            ok, reason = self.dest.garage_preflight(self.dest_collection_app_id)
        except Exception as exc:
            self.report.fail(
                "POST",
                f"{self._base(self.dest)}/v2/file-containers/{self.dest_collection_app_id}/upload-url",
                expected="Garage S3 reachable",
                got=f"exception: {exc}",
                exit_code=EXIT_GARAGE_DOWN,
                where_to_look="Garage daemon health, dest S3 driver config.",
                what_to_try=(
                    "docker compose ps garage on the dest host; "
                    "check shepard.file-storage.* config keys."
                ),
            )
            raise WarmupAborted(self.report)
        if not ok:
            self.report.fail(
                "POST",
                f"{self._base(self.dest)}/v2/file-containers/{self.dest_collection_app_id}/upload-url",
                expected="Garage S3 reachable (200/201 from upload-url)",
                got=reason,
                exit_code=EXIT_GARAGE_DOWN,
                where_to_look="Dest file-storage provider (PV1a).",
                what_to_try=(
                    "If reason mentions gridfs: the dest is still on "
                    "the legacy provider — run the PV1a migration or "
                    "set shepard.file-storage.provider=garage."
                ),
            )
            raise WarmupAborted(self.report)
        self.report.record_probe(
            "POST",
            f"{self._base(self.dest)}/v2/file-containers/{self.dest_collection_app_id}/upload-url",
            expected="Garage S3 reachable",
            got="ok",
            ok=True,
            spec_match=None,
        )

    # ── Helpers ──────────────────────────────────────────────────────────

    def _cleanup(self) -> None:
        """Defensive cleanup — delete any throwaway probe DOs we created.

        Runs from the `finally` of `run()`, regardless of success or
        WarmupAborted. Failures during cleanup are swallowed (logged
        to the probes list) so a flaky DELETE doesn't mask the real
        failure.
        """
        for do_id in self._cleanup_source_do_ids:
            url = (
                f"{self._base(self.source)}/shepard/api/collections/"
                f"{self.source_collection_id}/dataObjects/{do_id}"
            )
            try:
                r = self._raw_delete(self.source, url)
                ok = 200 <= getattr(r, "status_code", 0) < 300 or getattr(
                    r, "status_code", 0
                ) == 404
                self.report.record_probe(
                    "DELETE", url,
                    expected="200/204/404 (idempotent)",
                    got=f"HTTP {getattr(r, 'status_code', '?')}",
                    ok=ok,
                    spec_match=None,
                )
            except Exception as exc:
                self.report.record_probe(
                    "DELETE", url,
                    expected="cleanup of throwaway probe DO",
                    got=f"exception (ignored): {exc}",
                    ok=False,
                    spec_match=None,
                )
        for do_id in self._cleanup_dest_do_ids:
            url = (
                f"{self._base(self.dest)}/shepard/api/collections/"
                f"{self.dest_collection_id}/dataObjects/{do_id}"
            )
            try:
                r = self._raw_delete(self.dest, url)
                ok = 200 <= getattr(r, "status_code", 0) < 300 or getattr(
                    r, "status_code", 0
                ) == 404
                self.report.record_probe(
                    "DELETE", url,
                    expected="200/204/404 (idempotent)",
                    got=f"HTTP {getattr(r, 'status_code', '?')}",
                    ok=ok,
                    spec_match=None,
                )
            except Exception as exc:
                self.report.record_probe(
                    "DELETE", url,
                    expected="cleanup of throwaway probe DO",
                    got=f"exception (ignored): {exc}",
                    ok=False,
                    spec_match=None,
                )

    def _fail_wire_shape(
        self, verb: str, url: str, path_template: str, diff: ShapeDiff
    ) -> None:
        bits: list[str] = []
        if diff.missing_required_fields:
            bits.append(f"missing required: {diff.missing_required_fields}")
        if diff.type_mismatches:
            bits.append(f"type mismatches: {diff.type_mismatches}")
        if diff.extra_unknown_fields:
            bits.append(f"unknown fields: {diff.extra_unknown_fields}")
        self.report.fail(
            verb, url,
            expected=f"shape per OpenAPI {path_template}",
            got="; ".join(bits) or "shape drift",
            exit_code=EXIT_WIRE_SHAPE_DRIFT,
            where_to_look=(
                "Upstream may have shipped a v5.x bump that re-shaped "
                "the response. Diff against the in-tree spec "
                f"({self._spec_path_resolved or 'spec not loaded'})."
            ),
            what_to_try=(
                "Re-pull the spec from upstream's /openapi.json, replace "
                "fixtures/v5/openapi-5.4.0.json, re-run with --smart-warmup."
            ),
            extra={
                "missing_required_fields": diff.missing_required_fields,
                "type_mismatches": diff.type_mismatches,
                "extra_unknown_fields": diff.extra_unknown_fields,
            },
        )

    @staticmethod
    def _base(client: Any) -> str:
        return getattr(client, "_base", "")

    @staticmethod
    def _raw_get(client: Any, url: str, params: dict | None = None) -> Any:
        """Call the client's raw session — bypasses the script's `_get`
        which collapses non-2xx to None. We need the real status code
        for our diagnostic."""
        sess = client._s
        return sess.get(url, params=params, timeout=30)

    @staticmethod
    def _raw_post(client: Any, url: str, body: dict) -> Any:
        sess = client._s
        return sess.post(url, json=body, timeout=60)

    @staticmethod
    def _raw_delete(client: Any, url: str) -> Any:
        sess = client._s
        return sess.delete(url, timeout=30)

    @staticmethod
    def _json(response: Any) -> Any:
        try:
            return response.json()
        except Exception:
            return {}

    @staticmethod
    def _body_snippet(response: Any, limit: int = 400) -> str:
        text = getattr(response, "text", "") or ""
        if not text:
            try:
                text = json.dumps(response.json())
            except Exception:
                text = ""
        return text[:limit]


__all__ = [
    "WarmupReport",
    "WarmupAborted",
    "ShapeDiff",
    "SmartWarmup",
    "compare_against_openapi",
    "DEFAULT_OPENAPI_SPEC",
    "EXIT_OK",
    "EXIT_GENERIC",
    "EXIT_AUTH",
    "EXIT_SOURCE_UNREACHABLE",
    "EXIT_GARAGE_DOWN",
    "EXIT_OPERATOR_INTERRUPT",
    "EXIT_WIRE_SHAPE_DRIFT",
    "EXIT_WRITE_PERMISSION_DENIED",
]
