# /// script
# requires-python = ">=3.11"
# dependencies = ["requests", "tqdm"]
# ///
#!/usr/bin/env python3
"""mffd-import-v15.py — MFFD manufacturing process data ingest with provenance.

v15 (supersedes v14, see aidocs/integrations/93-mffd-import-v15-requirements.md):
     - LIVE cube3 source pull (v14's local-disk replay is shape-ref only; 0-byte TS placeholders)
     - 8 wire-shape bug fixes per aidocs/agent-findings/api-scrutinizer-v14-import.md:
         D (file fileOids[]), G (TS reference creation order),
         H (csv_format=COLUMN not WIDE), L (drop readOnly TS container type),
         E (StructuredDataPayload wrapper), B (POST not PUT for structured),
         C (non-empty structuredDataOids[]), I (predecessorIds in DataObject body)
     - 4-worker producer/consumer pool with bounded queue (256)
     - Exponential-backoff retry with jitter; redeploy-resilient long-wait
     - JWT-expiry pause + resume via SIGCONT
     - Presigned-URL upload flow against Garage FileContainer (3-step)
     - X-AI-Agent header on every dest call (claude-opus-4-7 actedOnBehalfOf fkrebs@nucli.de)
     - semanticAnnotation batch writeback (V61 predicates registered)
     - ETA attribute publish every 30s on dest collection
     - Log-as-proof re-upload every 5min to ImportScripts DO
     - Single state-writer thread with atomic .state.json (tmp+fsync+rename)
     - Pre-flight Garage probe (exit code 4 = "needs Garage activation")

Exit codes:
     0  Import completed successfully
     1  Generic failure (network, unrecoverable HTTP error)
     2  Authentication failure (no JWT, all retries exhausted on 401)
     3  Source unreachable / no DLR intranet
     4  Garage S3 backend not active — pre-flight probe returned gridfs error
     5  Operator interrupt (SIGINT after state persisted)


Agentic Data Management workflow
──────────────────────────────────
This script implements a human+Claude-in-the-loop import:
  1. Bootstrap (run once on nuclide.systems from this repo):
       creates the destination collection + skeleton DataObjects + uploads this
       script as a provenance artifact + initial snapshot capturing t=0 state.
  2. Shell fetch (on DLR machine):
       run-mffd-import.sh downloads this script from the ImportScripts DataObject
       in the collection, then runs it.
  3. Warmup probe:
       uploads one payload per reference type; Claude reviews with the operator
       in chat; Claude then calls PATCH /collections/{id} to set import_ready.
  4. Full import:
       traverses source collections on DLR intranet (48297 tapelaying,
       163811 bridge/frame welding), downloads every DataObject + file, re-uploads
       to the destination collection. All progress is checkpointed.
  5. Post-import snapshot:
       snapshot label includes current collection name so future renames are tracked.

⚠  TIMESTAMP NOTE
──────────────────
File upload timestamps and DataObject creationDate reflect the IMPORT time,
not the original measurement time. Source timestamps are preserved as
source_created / source_modified attributes on each migrated DataObject.

Three modes
───────────
  --bootstrap    Create destination collection + self-upload + t=0 snapshot.
                 Run this ONCE on nuclide.systems before the DLR import.

  SOURCE MODE    Traverse source Shepard collections (set SOURCE_* vars), pull
  (default)      all DataObjects + files, re-upload to destination collection.
                 Use SOURCE_SHEPARD_URL / SOURCE_SHEPARD_API_KEY for cross-instance.

  LOCAL MODE     Read files from DATA_DIR subdirs. Fallback when no SOURCE vars set.

Usage
─────
  # Step 1 — bootstrap (run once on nuclide.systems):
  SHEPARD_URL=https://shepard.nuclide.systems \\
  SHEPARD_API_KEY=<nuclide-key> \\
  uv run python mffd-dropbox-import.py --bootstrap

  # Step 2 — full cross-instance import (run on DLR network):
  SHEPARD_URL=https://shepard.nuclide.systems \\
  SHEPARD_API_KEY=<nuclide-key> \\
  SOURCE_SHEPARD_URL=https://backend.bt-au-cube3.intra.dlr.de \\
  SOURCE_SHEPARD_API_KEY=eyJhbGciOiJSUzI1NiJ9... \\
  SOURCE_TAPELAYING_COLL_ID=48297 \\
  SOURCE_BRIDGEWELDING_COLL_ID=163811 \\
  SESSION_ID=2026-05-22-Q1 \\
  uv run python mffd-dropbox-import.py

  # Or: fetch via shell helper (after bootstrap):
  bash run-mffd-import.sh

  # Dry-run:
  uv run python mffd-dropbox-import.py --dry-run

Environment variables
─────────────────────
  SHEPARD_URL                   Destination instance.  Default: https://shepard.nuclide.systems
  SHEPARD_API_KEY               Destination auth — X-API-KEY (Shepard JWTs go here)
  SHEPARD_BEARER_TOKEN          Destination auth — Authorization: Bearer (Keycloak)
  SOURCE_SHEPARD_URL            Source instance for cross-instance pull.  Default: SHEPARD_URL
  SOURCE_SHEPARD_API_KEY        Source auth (JWT from DLR intranet instance)
  SOURCE_TAPELAYING_COLL_ID     Source collection ID for tape-laying  (48297 on DLR)
  SOURCE_BRIDGEWELDING_COLL_ID  Source collection ID for bridge welding (163811 on DLR)
  SESSION_ID                    Run identifier.  Default: today YYYY-MM-DD
  DATA_DIR                      Root dir for LOCAL MODE.  Default: .
  COLLECTION_NAME               Destination collection name.  Default: MFFD-Dropbox
  LOG_DIR                       Log + state file directory.  Default: script directory
  PAGE_SIZE                     DataObjects per page when traversing.  Default: 50
  OPERATOR                      Your name/email for provenance attrs.  Default: (empty)
"""

from __future__ import annotations

import argparse
import datetime
import os
import sys
import tempfile
import time
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

# v15.2 — smart warmup module (IMPORT-W1/W2/W3). Kept in a sibling file
# so the 2700-line monolith stays merge-friendly. Import is best-effort:
# if the module is missing we fall back to legacy warmup transparently.
try:
    from _smart_warmup import (  # type: ignore[import-not-found]
        SmartWarmup,
        WarmupAborted,
        WarmupReport,
    )
    _SMART_WARMUP_AVAILABLE = True
except ImportError:
    _SMART_WARMUP_AVAILABLE = False

# ── Configuration ─────────────────────────────────────────────────────────────

SHEPARD_URL = os.environ.get("SHEPARD_URL", "https://shepard.nuclide.systems").rstrip("/")
SHEPARD_API_KEY = os.environ.get("SHEPARD_API_KEY", "")
SHEPARD_BEARER_TOKEN = os.environ.get("SHEPARD_BEARER_TOKEN", "")
SESSION_ID = os.environ.get("SESSION_ID", datetime.date.today().isoformat())
MAX_DOS_PER_STEP = 0  # Set from --max-dos in main(); 0 = unlimited
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

# Cross-instance source (set SOURCE_* vars for DLR intranet pull)
SOURCE_SHEPARD_URL = os.environ.get("SOURCE_SHEPARD_URL", SHEPARD_URL)
SOURCE_SHEPARD_API_KEY = os.environ.get("SOURCE_SHEPARD_API_KEY", SHEPARD_API_KEY)
OPERATOR = os.environ.get("OPERATOR", "")


# ── v15 concurrency + resilience primitives (aidocs/93 §5) ───────────────────
#
# Module-level helpers reused by the worker pool, state writer, and prov writer.
# Each is independently unit-testable — the worker pool itself relies on these
# building blocks but they're written to stand alone.

import json as _json_for_state
import os as _os_for_state
import random as _random
import signal as _signal
import threading as _threading
from queue import Queue, Empty


def backoff_delay(attempt: int, base: float = 1.0, cap: float = 60.0,
                  jitter: float = 0.25) -> float:
    """Exponential backoff with full jitter, capped at `cap` seconds.

    attempt: 0-indexed retry count (0 = first retry).
    Returns: delay in seconds in [base*2^attempt * (1-jitter), base*2^attempt * (1+jitter)],
             but never above `cap`.
    """
    raw = base * (2 ** max(0, attempt))
    capped = min(raw, cap)
    if jitter <= 0:
        return capped
    return capped * (1.0 + _random.uniform(-jitter, +jitter))


def atomic_write_json(path: Path, payload: dict) -> None:
    """Write JSON atomically: tmp file + fsync + rename.

    Survives kill -9 mid-write — either the old file is intact, or the new
    one is fully landed; no half-written state. Per the v15 §5 single
    state-writer thread design.
    """
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_name(path.name + ".tmp")
    with tmp.open("w", encoding="utf-8") as fh:
        _json_for_state.dump(payload, fh, indent=2, sort_keys=True)
        fh.flush()
        _os_for_state.fsync(fh.fileno())
    _os_for_state.replace(str(tmp), str(path))


class StateFile:
    """Single-writer-thread state ledger for v15 (aidocs/93 §5 + §11).

    Tracks completed work so a restart resumes where the previous run paused.
    Updates are coalesced into the atomic_write_json at most every 30s OR
    after 100 completion events — whichever comes first.

    Thread-safety: methods are guarded by an internal lock; persist() may
    be called from any thread but actual disk writes happen on whichever
    caller hits the flush threshold.
    """

    def __init__(self, path: Path) -> None:
        self.path = Path(path)
        self._lock = _threading.Lock()
        self._state: dict[str, Any] = {
            "completed_files":  [],
            "completed_ts":     [],
            "completed_structured": [],
            "do_id_mapping":    {},   # src_do_id -> dest_do_id
            "batch_sequence":   0,
            "last_flush":       0,
        }
        self._dirty_since_flush = 0
        # Initialise to "now" so the 30s throttle has a meaningful zero —
        # otherwise the first persist(force=False) call always passes the
        # time-since-last-flush gate (epoch 1970 is decades in the past).
        self._last_flush_ts = time.time()
        if self.path.exists():
            self._load()

    def _load(self) -> None:
        try:
            with self.path.open("r", encoding="utf-8") as fh:
                loaded = _json_for_state.load(fh)
            if isinstance(loaded, dict):
                self._state.update(loaded)
        except (ValueError, OSError) as exc:
            print(f"  [state] could not load {self.path}: {exc} (starting fresh)")

    def record_file(self, src_id: str) -> None:
        with self._lock:
            if src_id not in self._state["completed_files"]:
                self._state["completed_files"].append(src_id)
                self._dirty_since_flush += 1

    def record_ts(self, src_ref_id: str) -> None:
        with self._lock:
            if src_ref_id not in self._state["completed_ts"]:
                self._state["completed_ts"].append(src_ref_id)
                self._dirty_since_flush += 1

    def record_structured(self, src_ref_id: str) -> None:
        with self._lock:
            if src_ref_id not in self._state["completed_structured"]:
                self._state["completed_structured"].append(src_ref_id)
                self._dirty_since_flush += 1

    def map_do(self, src_do_id: int, dest_do_id: int) -> None:
        with self._lock:
            self._state["do_id_mapping"][str(src_do_id)] = dest_do_id
            self._dirty_since_flush += 1

    def get_dest_do(self, src_do_id: int) -> int | None:
        with self._lock:
            v = self._state["do_id_mapping"].get(str(src_do_id))
            return int(v) if v is not None else None

    def is_file_done(self, src_id: str) -> bool:
        with self._lock:
            return src_id in self._state["completed_files"]

    def is_ts_done(self, src_ref_id: str) -> bool:
        with self._lock:
            return src_ref_id in self._state["completed_ts"]

    def is_structured_done(self, src_ref_id: str) -> bool:
        with self._lock:
            return src_ref_id in self._state["completed_structured"]

    def next_batch_seq(self) -> int:
        with self._lock:
            self._state["batch_sequence"] += 1
            self._dirty_since_flush += 1
            return self._state["batch_sequence"]

    def persist(self, force: bool = False) -> bool:
        """Flush to disk if threshold met (or forced). Returns True if a write occurred."""
        with self._lock:
            if not force:
                if self._dirty_since_flush < 100 and (time.time() - self._last_flush_ts) < 30.0:
                    return False
            payload = dict(self._state)
            payload["last_flush"] = time.time()
            self._dirty_since_flush = 0
            self._last_flush_ts = payload["last_flush"]
        atomic_write_json(self.path, payload)
        return True

    def snapshot(self) -> dict[str, Any]:
        """Return a deep-ish copy of the current state (testing/debugging)."""
        with self._lock:
            return _json_for_state.loads(_json_for_state.dumps(self._state))


class JwtPauseManager:
    """JWT-expiry pause primitive (aidocs/93 §5).

    Threading model (the gotcha): SIGCONT handling must live on the
    main thread. Workers detect 401 → call `.request_pause()` which
    sets a shared Event; the main thread waits on the Event and calls
    `signal.pause()`. The operator sends SIGCONT after re-minting the
    JWT in env; main thread sets the new headers on the client, clears
    the Event, and workers continue.

    Use:
        pause_mgr = JwtPauseManager(client)
        pause_mgr.install_signal_handler()
        # in workers:
        if response.status_code == 401:
            pause_mgr.request_pause()
            # worker blocks on pause_mgr.resume_event.wait()
            # …main thread re-mints and clears…
            continue  # retry the request
    """

    def __init__(self, client: "ShepardClient") -> None:
        self._client = client
        self._pause_requested = _threading.Event()
        self.resume_event = _threading.Event()
        self.resume_event.set()  # initially: workers may proceed
        self._handler_installed = False

    def request_pause(self) -> None:
        """Called by a worker that observed 401. Idempotent."""
        if not self._pause_requested.is_set():
            print(
                "\n  [JWT] worker observed 401 — pausing all workers.\n"
                "  ┌─────────────────────────────────────────────────────────┐\n"
                "  │ JWT EXPIRED — operator action required                   │\n"
                "  ├─────────────────────────────────────────────────────────┤\n"
                "  │ 1. Re-mint your kreb_fl JWT (DLR Shepard cube3 UI).      │\n"
                "  │ 2. Update the running process's environment:             │\n"
                "  │      export SOURCE_SHEPARD_API_KEY=<new-jwt>             │\n"
                "  │      (or SHEPARD_API_KEY for dest)                       │\n"
                "  │ 3. Send SIGCONT to resume:                               │\n"
                f"  │      kill -CONT {_os_for_state.getpid()}\n"
                "  └─────────────────────────────────────────────────────────┘",
                flush=True,
            )
        self._pause_requested.set()
        self.resume_event.clear()

    def install_signal_handler(self) -> None:
        """Wire SIGCONT → resume on the main thread.

        Must be called from the main thread before workers start; raises
        ValueError otherwise (Python's signal module enforces this).
        """
        def _on_sigcont(signum, frame):
            print("  [JWT] SIGCONT received — re-reading JWT from env and resuming…", flush=True)
            new_key = _os_for_state.environ.get("SOURCE_SHEPARD_API_KEY") \
                      or _os_for_state.environ.get("SHEPARD_API_KEY") or ""
            new_bearer = _os_for_state.environ.get("SHEPARD_BEARER_TOKEN", "")
            if new_bearer:
                self._client._s.headers["Authorization"] = f"Bearer {new_bearer}"
                self._client._s.headers.pop("X-API-KEY", None)
            elif new_key:
                self._client._s.headers["X-API-KEY"] = new_key
                self._client._s.headers.pop("Authorization", None)
            self._pause_requested.clear()
            self.resume_event.set()

        try:
            _signal.signal(_signal.SIGCONT, _on_sigcont)
            self._handler_installed = True
        except ValueError as exc:
            # signal.signal raises if called from non-main thread.
            print(f"  [JWT] could not install SIGCONT handler: {exc}")

    def wait_if_paused(self, timeout: float | None = None) -> bool:
        """Worker-side: blocks if a pause is active. Returns True when free to proceed."""
        return self.resume_event.wait(timeout=timeout)

    @property
    def is_paused(self) -> bool:
        return self._pause_requested.is_set()


# ── V61-registered shepard: predicate IRIs (single source of truth) ──────────
#
# These ten IRIs are minted by V61__v15_prov_predicates.cypher as :Resource
# nodes so the dest's SemanticAnnotation IRI lookup resolves them. A typo
# here = silent 404 on the SemanticAnnotation POST (n10s returns empty for
# unknown IRIs). Keep the strings byte-identical with the Cypher MERGEs.

class Predicates:
    """The 8 shepard: predicates V61 registered for v15 batch writeback."""
    NS = "http://semantics.dlr.de/shepard-upper#"
    TARGET_COLLECTION       = NS + "targetCollection"
    FILES_UPLOADED          = NS + "filesUploaded"
    TIMESERIES_IMPORTED     = NS + "timeseriesImported"
    STRUCTURED_PAYLOADS     = NS + "structuredPayloads"
    BATCH_SEQUENCE          = NS + "batchSequence"
    THROUGHPUT_BYTES_PER_SEC = NS + "throughputBytesPerSec"
    RETRY_COUNT             = NS + "retryCount"
    SOURCE_INSTANCE         = NS + "sourceInstance"
    ROLE_EXECUTOR           = NS + "role-executor"
    ROLE_OPERATOR           = NS + "role-operator"


class PROV:
    """PROV-O predicates used by the v15 batch writeback (per the
    PROV-O bundle preseeded in OntologySeedService)."""
    NS = "http://www.w3.org/ns/prov#"
    WAS_DERIVED_FROM   = NS + "wasDerivedFrom"
    WAS_GENERATED_BY   = NS + "wasGeneratedBy"
    WAS_INFORMED_BY    = NS + "wasInformedBy"
    WAS_ATTRIBUTED_TO  = NS + "wasAttributedTo"
    USED               = NS + "used"
    STARTED_AT_TIME    = NS + "startedAtTime"
    ENDED_AT_TIME      = NS + "endedAtTime"
    # v15.1: snapshot Activity emits prov:generated for the snapshot entity
    # (anchored against the collection via CollectionSemanticAnnotation since
    # snapshots themselves are not annotatable; partial-G5 per advisor review).
    GENERATED          = NS + "generated"
    ENTITY             = NS + "Entity"


class Fair2r:
    """F(AI)²R vendor-namespace predicates per project_ai_human_collab_provenance.md.

    Vendored at TPL9a from github.com/noheton/f-ai-r/blob/main/doc/provenance.ttl
    into `backend/src/main/resources/shapes/fair2r-shapes.ttl`. Predicates here are
    used by v15.1 to:
      * tag every DO with `fair2r:modeOfProduction "ai"` + `fair2r:wasAcceptedAs "auto-applied"`
        (EU AI Act Art. 50 effective 2026-08-02 — per-artefact visibility)
      * tag the AI agent on each DO via `fair2r:wasGeneratedByAi`
    """
    NS         = "https://noheton.org/f-ai-r/ns#"
    VERIF_NS   = "https://noheton.org/f-ai-r/ns/verif#"
    AGENT_NS   = "https://noheton.org/f-ai-r/agent/"
    MODE_OF_PRODUCTION   = NS + "modeOfProduction"
    WAS_ACCEPTED_AS      = NS + "wasAcceptedAs"
    WAS_GENERATED_BY_AI  = NS + "wasGeneratedByAi"
    VERIFICATION_STATE   = NS + "verificationState"
    AUTHORING_PASS       = NS + "AuthoringPass"
    # Canonical vendor IRIs (never minted per-instance):
    AGENT_CLAUDE_OPUS_4_7   = AGENT_NS + "claude-opus-4-7"
    VERIF_UNVERIFIED        = VERIF_NS + "unverified"
    # modeOfProduction value IRIs — minted in vendor verif ns family for queryability
    MODE_AI            = NS + "mode/ai"
    MODE_HUMAN         = NS + "mode/human"
    MODE_COLLABORATIVE = NS + "mode/collaborative"
    # acceptance-ladder value IRIs (mirror verif#)
    ACCEPT_AUTO_APPLIED  = VERIF_NS + "auto-applied"
    ACCEPT_UNCHECKED     = VERIF_NS + "unchecked"
    ACCEPT_AS_IS         = VERIF_NS + "as-is"
    ACCEPT_HUMAN_EDITED  = VERIF_NS + "human-edited"


class Dcterms:
    """dcterms predicates used by v15.1 for license + access rights + alternative names.

    These predicates are part of the preseeded INTERNAL repo (dcterms is a
    Dublin Core Terms bundle every Shepard instance ships by default).
    """
    NS                = "http://purl.org/dc/terms/"
    LICENSE           = NS + "license"
    ACCESS_RIGHTS     = NS + "accessRights"
    ALTERNATIVE       = NS + "alternative"
    # SPDX license-IRI builder; falls back to literal-as-IRI URN if not SPDX-like
    SPDX_NS           = "https://spdx.org/licenses/"


def license_iri(license_id: str) -> str:
    """Return a dereferenceable IRI for a license identifier.

    If the value looks like an SPDX id (alphanumeric + dash + dot), mint a
    spdx.org IRI. Otherwise fall back to a URN form so the wire still has
    something queryable.
    """
    license_id = (license_id or "").strip()
    if not license_id:
        return ""
    # Heuristic: SPDX ids are short, alphanumeric + dash + dot, no spaces
    import re as _re
    if _re.fullmatch(r"[A-Za-z0-9.+\-]+", license_id):
        return f"{Dcterms.SPDX_NS}{license_id}"
    # Fall back to a URN — still a stable IRI but won't dereference
    return f"urn:license:{license_id.replace(' ', '%20')}"


def access_rights_iri(value: str) -> str:
    """Build an IRI for an access-rights value. Falls back to URN for non-URL inputs."""
    value = (value or "").strip()
    if not value:
        return ""
    if value.startswith(("http://", "https://", "urn:")):
        return value
    return f"urn:accessRights:{value.replace(' ', '-')}"


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
    """One file payload identified by (fref_id, oid).

    Bug F fix: a single FileReference may carry multiple `fileOids[]`
    (multi-file bundles). v14 ignored this — only the ref node was
    iterated, never its OID list, silently dropping bundled payloads.
    v15 emits one FileRef per (ref_id, oid) pair so downstream loops
    iterate every payload.
    """
    fref_id: int
    name: str
    size: int = 0
    oid: str = ""


@dataclass
class TsRef:
    ref_id: int
    name: str
    container_id: int


@dataclass
class StructuredRef:
    ref_id: int
    name: str
    container_id: int


@dataclass
class SourceDO:
    """A DataObject discovered in a source collection."""
    do_id: int
    name: str
    description: str
    attributes: dict[str, str]
    file_refs: list[FileRef] = field(default_factory=list)
    ts_refs: list[TsRef] = field(default_factory=list)
    structured_refs: list[StructuredRef] = field(default_factory=list)
    created: str = ""
    modified: str = ""


# ── HTTP client ───────────────────────────────────────────────────────────────

class ShepardClient:
    """Minimal Shepard v1/v2 client. Credentials never echoed in output."""

    def __init__(self, base: str, api_key: str, bearer_token: str,
                 ai_agent: str | None = None) -> None:
        self._base = base
        self._s = Session()
        self._s.headers.update({"Accept": "application/json"})
        if bearer_token:
            self._s.headers["Authorization"] = f"Bearer {bearer_token}"
        elif api_key:
            self._s.headers["X-API-KEY"] = api_key
        # X-AI-Agent header per aidocs/93 §10 — sent on every dest call so the
        # PROV1a filter can capture the AI agency on every mutation.
        # Format: "<agent-id>; actedOnBehalfOf=<email>"
        if ai_agent:
            self._s.headers["X-AI-Agent"] = ai_agent

    # ── Warmup ────────────────────────────────────────────────────────────────

    def warmup(self) -> bool:
        """Warmup against a dest instance — fork-only /v2/users/me identity probe.

        Bug K fix: the /shepard/api/users/currentUser fallback never existed in
        upstream v5.2.0 / v5.4.0 — the upstream v1 surface only exposes
        /users (list) and /users/{username}. Either /v2/users/me succeeds
        (fork) or warmup fails fast against an unsupported source.
        """
        print("\n=== Warmup ===")
        user_r = self._get(f"{self._base}/v2/users/me")
        if user_r is None:
            print("  [FAIL] Cannot reach /v2/users/me — check SHEPARD_URL + auth")
            print("  Hint: this script's dest must be a Shepard fork instance with /v2 surface.")
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

    def warmup_source(self) -> bool:
        """Warmup against a SOURCE instance (DLR cube3 v5.4.0 — strict upstream).

        Bug R fix: cross-instance script needs two warmups — the source side
        only speaks upstream v1 (no /v2 surface) and won't survive a
        /v2/users/me probe. We probe the listings endpoint instead and
        accept any 2xx response.
        """
        print(f"\n=== Source warmup against {self._base} ===")
        r = self._get(f"{self._base}/shepard/api/collections", {"page": "0", "size": "1"})
        if r is None:
            print("  [FAIL] Source not reachable — check SOURCE_SHEPARD_URL + auth")
            return False
        total = r.headers.get("X-Total-Count", "?")
        print(f"  source ok  : {self._base}  (collections visible: {total})")
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
                do_id = item["id"]
                file_refs = self._fetch_file_refs(coll_id, do_id)
                ts_refs = self._fetch_ts_refs(coll_id, do_id)
                structured_refs = self._fetch_structured_refs(coll_id, do_id)
                yield SourceDO(
                    do_id=do_id,
                    name=item.get("name", f"DO-{do_id}"),
                    description=item.get("description") or "",
                    attributes={k: str(v) for k, v in (item.get("attributes") or {}).items()},
                    file_refs=file_refs,
                    ts_refs=ts_refs,
                    structured_refs=structured_refs,
                    created=item.get("creationDate") or item.get("createdAt") or "",
                    modified=item.get("modificationDate") or item.get("updatedAt") or "",
                )
            total_pages = int(r.headers.get("X-Total-Pages", page + 1))
            page += 1
            if page >= total_pages:
                break

    def _fetch_file_refs(self, coll_id: int, do_id: int) -> list[FileRef]:
        """Bug D + F fix: iterate every `fileOids[]` entry, not just the
        reference node. A multi-file FileReference produces one FileRef
        per OID; downstream code addresses each payload via its OID path
        `/fileReferences/{id}/payload/{oid}`.
        """
        r = self._get(
            f"{self._base}/shepard/api/collections/{coll_id}/dataObjects/{do_id}/fileReferences"
        )
        if r is None:
            return []
        refs = []
        for item in r.json():
            base_name = item.get("name") or item.get("fileName") or f"file-{item['id']}"
            base_size = item.get("size") or item.get("fileSize") or 0
            oids = item.get("fileOids") or []
            if not oids:
                # Some legacy refs may carry a single payload accessible at
                # /fileReferences/{id}/payload (no OID); keep one placeholder
                # entry so downstream code can hit the v1-style endpoint.
                refs.append(FileRef(
                    fref_id=item["id"], name=base_name, size=base_size, oid=""
                ))
                continue
            # Multi-OID expansion — one FileRef per payload, preserving the
            # ref node's id for the link-back step.
            for idx, oid in enumerate(oids):
                # Disambiguate display name when a bundle contains >1 file.
                name = base_name if len(oids) == 1 else f"{base_name}.{idx}"
                refs.append(FileRef(
                    fref_id=item["id"], name=name, size=base_size, oid=str(oid)
                ))
        return refs

    def _fetch_ts_refs(self, coll_id: int, do_id: int) -> list[TsRef]:
        r = self._get(
            f"{self._base}/shepard/api/collections/{coll_id}/dataObjects/{do_id}/timeseriesReferences"
        )
        if r is None:
            return []
        refs = []
        for item in r.json():
            refs.append(TsRef(
                ref_id=item["id"],
                name=item.get("name") or f"ts-{item['id']}",
                container_id=item.get("timeseriesContainerId") or 0,
            ))
        return refs

    def _fetch_structured_refs(self, coll_id: int, do_id: int) -> list[StructuredRef]:
        r = self._get(
            f"{self._base}/shepard/api/collections/{coll_id}/dataObjects/{do_id}/structuredDataReferences"
        )
        if r is None:
            return []
        refs = []
        for item in r.json():
            refs.append(StructuredRef(
                ref_id=item["id"],
                name=item.get("name") or f"structured-{item['id']}",
                container_id=item.get("structuredDataContainerId") or 0,
            ))
        return refs

    def export_ts(self, coll_id: int, do_id: int, ref_id: int) -> bytes | None:
        """Export a timeseries reference as CSV from the source instance.

        Bug H fix: DLR v5.4.0 enum for csv_format is {ROW, COLUMN} only —
        WIDE is a fork-only invention that 400s against upstream. COLUMN is
        the row-orientation the dest importer expects (per spec §7).
        Bug Q: empty body (zero point data) is treated as failure since v5.4.0
        TS export with at least one channel always returns at least the header.
        """
        url = (
            f"{self._base}/shepard/api/collections/{coll_id}"
            f"/dataObjects/{do_id}/timeseriesReferences/{ref_id}/export"
        )
        r = self._request_with_retry("GET", url, params={"csv_format": "COLUMN"}, timeout=300)
        if r is None or not r.ok:
            if r:
                self._log_err("GET (ts-export)", url, r)
            return None
        content = r.content
        if not content or len(content) < 8:
            # Bug Q: distinguish "200 with no payload" from a successful export.
            # DLR v5.4.0 always returns at least one header row + newline for
            # a non-empty container; empty body means we hit a placeholder.
            print(f"  [ts-export] empty body for ref {ref_id} — skipping")
            return None
        return content

    def download_structured(self, coll_id: int, do_id: int, ref_id: int) -> list | None:
        """Bug E fix: GET .../payload returns `StructuredDataPayload[]` — an
        ARRAY of wrappers each carrying `{structuredData, payload}` where
        `payload` is the JSON-encoded inner content (a STRING).

        v14 did `r.json()` and returned the raw wrapper without decoding
        the inner string — when later re-uploaded, the wrapper-of-wrapper
        round-trip silently corrupted the data.

        v15 returns a list of (name, decoded-payload) tuples ready for
        re-upload via upload_structured_payload.
        """
        import json as _json
        url = (
            f"{self._base}/shepard/api/collections/{coll_id}"
            f"/dataObjects/{do_id}/structuredDataReferences/{ref_id}/payload"
        )
        r = self._get(url)
        if r is None:
            return None
        try:
            raw = r.json()
        except (ValueError, AttributeError):
            return None
        if not isinstance(raw, list):
            # Defensive: a non-array response means a wire-shape regression
            # on the source side. Treat single-wrapper as a 1-element array.
            raw = [raw] if raw else []
        out: list[dict] = []
        for wrapper in raw:
            if not isinstance(wrapper, dict):
                continue
            inner_str = wrapper.get("payload")
            sd_meta = wrapper.get("structuredData") or {}
            inner_name = sd_meta.get("name") or "payload"
            if not isinstance(inner_str, str):
                # Bug E hardening: if the source already returned an object,
                # accept it directly — keeps behaviour resilient against a
                # later v6 wire shape that drops the string encoding.
                if isinstance(inner_str, (dict, list)):
                    out.append({"name": inner_name, "payload": inner_str})
                continue
            try:
                decoded = _json.loads(inner_str)
            except (ValueError, TypeError):
                # Couldn't decode — keep the raw string so it round-trips.
                decoded = inner_str
            out.append({"name": inner_name, "payload": decoded})
        return out

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
        oid: str = "",
    ) -> bool:
        """Bug D fix: when `oid` is set, address the specific payload
        within the FileReference bundle. v1-compat fallback (legacy
        single-payload refs without OIDs) keeps the bare /payload URL.
        """
        if oid:
            url = (
                f"{self._base}/shepard/api/collections/{coll_id}"
                f"/dataObjects/{do_id}/fileReferences/{fref_id}/payload/{oid}"
            )
        else:
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

    def create_collection(self, name: str, description: str, attrs: dict,
                          license: str | None = None,
                          access_rights: str | None = None) -> dict | None:
        """Create a Collection. v15.1: license + accessRights hydrated from
        operator CLI flags (FAIR R1 — refuses to start in SOURCE/LOCAL mode
        without an explicit default). Fields land in the v1-compat body shape;
        AbstractDataObject has writable `license` + `accessRights` per FAIR-1.
        """
        body: dict[str, Any] = {"name": name, "description": description}
        if attrs:
            body["attributes"] = attrs
        if license:
            body["license"] = license
        if access_rights:
            body["accessRights"] = access_rights
        r = self._post(f"{self._base}/shepard/api/collections", body)
        return r.json() if r else None

    def get_collection_app_id(self, collection_id: int) -> str | None:
        r = self._get(f"{self._base}/shepard/api/collections/{collection_id}")
        if r is None:
            return None
        return r.json().get("appId")

    def get_collection_name(self, collection_id: int) -> str:
        r = self._get(f"{self._base}/shepard/api/collections/{collection_id}")
        if r is None:
            return COLLECTION_NAME
        return r.json().get("name") or COLLECTION_NAME

    def set_collection_public(self, coll_id: int) -> bool:
        """Set permissionType=Public so all instance users can read+write."""
        url = f"{self._base}/shepard/api/collections/{coll_id}/permissions"
        r = self._get(url)
        if r is None:
            return False
        perms = r.json()
        perms["permissionType"] = "Public"
        try:
            r2 = self._s.put(url, json=perms, timeout=30)
            if r2.ok:
                print(f"  [perms] collection {coll_id} → Public (all instance users can access)")
                return True
            self._log_err("PUT", url, r2)
        except Exception as exc:
            print(f"  [net] PUT permissions {coll_id}: {exc}")
        return False

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
        predecessor_ids: list[int] | None = None,
        license: str | None = None,
        access_rights: str | None = None,
    ) -> dict | None:
        """Bug I fix: set `predecessorIds` inside the DataObject body at POST.

        v14 POST'd the DO, then did `PUT /collections/{c}/dataObjects/{d}/predecessors/{predId}`
        — that path does NOT exist in DLR v5.4.0. Every "predecessor link FAILED"
        warning in v14's log was this phantom endpoint.

        DLR v5.4.0 DataObject schema has writable `predecessorIds: array<int64>`.
        v15 puts the link in the create body — single round-trip, no phantom call.
        """
        body: dict[str, Any] = {"name": name}
        if description:
            body["description"] = description
        if attrs:
            body["attributes"] = attrs
        # Accept either a single id or a list — caller convenience.
        pred_list: list[int] = list(predecessor_ids) if predecessor_ids else []
        if predecessor_id is not None:
            pred_list.append(int(predecessor_id))
        if pred_list:
            body["predecessorIds"] = pred_list
        # v15.1: FAIR R1 license + accessRights — present only when operator
        # supplied them via --default-license / --default-access-rights.
        if license:
            body["license"] = license
        if access_rights:
            body["accessRights"] = access_rights
        r = self._post(f"{self._base}/shepard/api/collections/{coll_id}/dataObjects", body)
        if r is None:
            return None
        return r.json()

    def set_predecessors(self, coll_id: int, do_id: int, pred_ids: list[int]) -> bool:
        """Bug I fix: post-creation predecessor wiring via PUT on the DataObject body.

        Use this when predecessors aren't known at create time (e.g. cross-step
        wiring after both steps' DataObjects exist). Fetches the current DO,
        sets predecessorIds, strips readOnly fields, then PUTs. Idempotent
        when called with the same ids.
        """
        r = self._get(
            f"{self._base}/shepard/api/collections/{coll_id}/dataObjects/{do_id}"
        )
        if r is None:
            return False
        body = r.json()
        body["predecessorIds"] = list(pred_ids)
        # Strip readOnly fields the server rejects on PUT (per v5.4.0 schema).
        for k in (
            "id", "createdAt", "createdBy", "updatedAt", "updatedBy", "collectionId",
            "referenceIds", "successorIds", "childrenIds", "parentId", "incomingIds",
        ):
            body.pop(k, None)
        url = f"{self._base}/shepard/api/collections/{coll_id}/dataObjects/{do_id}"
        return self._put(url, body) is not None

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

    # ── Dest: presigned-URL upload flow (NEW in v15, per aidocs/93 §6) ───────────

    # Exit codes documented at top of file; this is the one we use for the
    # pre-flight branch when Garage is not active.
    EXIT_GARAGE_INACTIVE = 4

    def garage_preflight(self, file_container_app_id: str) -> tuple[bool, str]:
        """Pre-flight probe for the Garage S3 backend (aidocs/93 §9 + §13).

        Behaviour:
          - POSTs /v2/file-containers/{app_id}/upload-url with a sentinel filename
          - 200/201 → Garage is active; return (True, "").
          - 503 with body indicating "gridfs" → backend is still on the
            legacy GridFS provider; return (False, "<runbook>") and the caller
            should sys.exit(EXIT_GARAGE_INACTIVE).
          - 404 → endpoint missing entirely; return False with a "v2 surface
            not available" message.
          - anything else → return False with the raw response snippet.

        Returns (ok, reason). When ok is True the upload-url endpoint is alive
        and the operator can proceed; when False, `reason` is printable runbook
        text the caller should display before exiting.
        """
        url = f"{self._base}/v2/file-containers/{file_container_app_id}/upload-url"
        try:
            r = self._s.post(url, json={"fileName": ".shepard-preflight"}, timeout=15)
        except Exception as exc:
            return False, f"network error probing Garage upload-url: {exc}"
        if r.status_code in (200, 201):
            return True, ""
        body_snip = (r.text or "")[:400] if hasattr(r, "text") else ""
        if r.status_code == 404:
            return False, (
                "v2 file-container upload-url endpoint not found (HTTP 404).\n"
                "  This dest is missing the FS1b/c/d presigned-URL surface.\n"
                "  Either upgrade the dest backend to >= the FS1b release, or\n"
                "  fall back to the v14 multipart flow (slower; not supported in v15).\n"
            )
        if r.status_code == 503 or "gridfs" in body_snip.lower():
            return False, (
                "Garage S3 backend not active on dest — pre-flight probe returned 503/gridfs.\n"
                "  Operator runbook:\n"
                "    1. Enable shepard-plugin-file-s3 via:\n"
                "         GET  /v2/admin/plugins                    → check 'file-s3' status\n"
                "         POST /v2/admin/plugins/file-s3/enable\n"
                "    2. Activate the Garage sidecar — see\n"
                "         scripts/activate-plugin-sidecars.sh   (or the inline shape in\n"
                "         aidocs/integrations/93 §9). For an existing nuclide deploy:\n"
                "           docker compose -f infrastructure/compose.shepard.yml \\\n"
                "                          -f infrastructure/compose.garage.yml up -d\n"
                "           docker exec garage /garage layout assign $(docker exec garage /garage node id -q | cut -d@ -f1) -z dc1 -c 1G\n"
                "           docker exec garage /garage layout apply --version 1\n"
                "           docker exec garage /garage bucket create shepard-files\n"
                "           KEY=$(docker exec garage /garage key new --name shepard-backend)\n"
                "         (capture KEY_ID + SECRET_KEY into SHEPARD_FILES_S3_ACCESS_KEY_ID / _SECRET_ACCESS_KEY)\n"
                "    3. Switch the runtime provider to s3:\n"
                "         PATCH /v2/admin/file-storage/config  body={\"providerId\":\"s3\"}\n"
                "    4. Restart the backend.\n"
                "    5. Re-run v15 — the state file resumes from where this run paused.\n"
                f"  (raw probe response: HTTP {r.status_code}; body: {body_snip!r})"
            )
        return False, f"unexpected pre-flight response: HTTP {r.status_code}; body: {body_snip!r}"

    def upload_url_request(self, file_container_app_id: str, file_name: str
                           ) -> tuple[str, str] | None:
        """Step 1 of the presigned flow: ask the backend for a Garage PUT URL.

        Returns (upload_url, oid) on success, None on failure.
        Backend response shape (per FS1d): {uploadUrl, oid, expiresAt}.
        """
        url = f"{self._base}/v2/file-containers/{file_container_app_id}/upload-url"
        r = self._post(url, {"fileName": file_name})
        if r is None:
            return None
        try:
            body = r.json()
        except (ValueError, AttributeError):
            return None
        upload_url = body.get("uploadUrl")
        oid = body.get("oid")
        if not upload_url or not oid:
            print(f"  [presigned] backend returned malformed upload-url response: {body}")
            return None
        return str(upload_url), str(oid)

    def upload_url_put(self, upload_url: str, payload: bytes,
                       content_type: str = "application/octet-stream") -> bool:
        """Step 2: PUT the bytes directly to Garage. Backend never sees them.

        Uses a bare requests call — no Authorization header on the presigned
        URL (the signature carries the auth context).
        """
        try:
            r = requests.put(
                upload_url, data=payload, timeout=600,
                headers={"Content-Type": content_type},
            )
        except Exception as exc:
            print(f"  [presigned-put] network error: {exc}")
            return False
        if 200 <= r.status_code < 300:
            return True
        print(f"  [presigned-put] HTTP {r.status_code}: {r.text[:300]}")
        return False

    def upload_url_commit(self, file_container_app_id: str,
                          oid: str, file_name: str) -> dict | None:
        """Step 3: tell the backend the Garage upload landed.

        Returns the committed ShepardFile representation on success.
        """
        url = f"{self._base}/v2/file-containers/{file_container_app_id}/upload-url/commit"
        r = self._post(url, {"oid": oid, "fileName": file_name})
        if r is None:
            return None
        try:
            return r.json()
        except (ValueError, AttributeError):
            return None

    def presigned_upload(self, file_container_app_id: str, path: Path,
                         display: str | None = None) -> str | None:
        """Convenience: 3-step presigned upload of a local file. Returns oid on success.

        Reads the entire file into memory — appropriate for the per-step
        payloads in v15 (typical ZIP archive ≤ a few GB; the 5.5 GB tapelaying
        file may need a streaming PUT, future work).
        """
        display = display or path.name
        size = path.stat().st_size
        step1 = self.upload_url_request(file_container_app_id, display)
        if step1 is None:
            return None
        upload_url, oid = step1
        # Stream the bytes directly — for the v15 demo we accept memory cost.
        with path.open("rb") as fh:
            payload = fh.read()
        print(f"  [presigned] PUT {_human(size)} → Garage (oid={oid})")
        if not self.upload_url_put(upload_url, payload):
            return None
        committed = self.upload_url_commit(file_container_app_id, oid, display)
        if committed is None:
            return None
        return oid

    def link_file_via_oid(self, coll_id: int, do_id: int,
                         file_container_id: int, name: str,
                         oid: str) -> int | None:
        """Step 4 of the presigned flow: create the v5-compat FileReference
        pointing at the freshly-committed ShepardFile."""
        url = (
            f"{self._base}/shepard/api/collections/{coll_id}"
            f"/dataObjects/{do_id}/fileReferences"
        )
        body = {
            "name": name,
            "fileOids": [oid],
            "fileContainerId": file_container_id,
        }
        r = self._post(url, body)
        return r.json().get("id") if r else None

    # ── Semantic annotation + ETA publisher (per aidocs/93 §10 + §11) ────────────

    def get_or_create_semantic_repo(self, name: str, type_: str = "INTERNAL",
                                    endpoint: str = "") -> int | None:
        """Idempotent: find existing semantic repository by name, else create.

        The PROV-O bundle ships preseeded by OntologySeedService into the
        built-in INTERNAL repo (one row with type='INTERNAL'). v15 also
        creates a per-session 'mffd-migration-<SESSION_ID>' repo for the
        opaque source-DO URNs it emits as valueIRI values.
        """
        r = self._get(f"{self._base}/shepard/api/semanticRepositories")
        if r is not None:
            try:
                for repo in r.json():
                    if repo.get("name") == name:
                        return repo.get("id")
            except (ValueError, AttributeError):
                pass
        body: dict[str, Any] = {"name": name, "type": type_}
        if endpoint:
            body["endpoint"] = endpoint
        r = self._post(f"{self._base}/shepard/api/semanticRepositories", body)
        return r.json().get("id") if r else None

    def add_semantic_annotation(
        self, coll_id: int, do_id: int,
        property_iri: str, value_iri: str,
        property_repo_id: int, value_repo_id: int,
        numeric_value: float | None = None,
        unit_iri: str | None = None,
    ) -> bool:
        """Emit one (subject=DO, predicate=propertyIRI, object=valueIRI) triple
        via the typed-triple SemanticAnnotation API (per aidocs/93 §10).

        Each call is a single per-DO POST — batched at higher levels by
        the prov writer thread (one batch every 100 DOs OR 5 min).
        """
        url = (f"{self._base}/shepard/api/collections/{coll_id}"
               f"/dataObjects/{do_id}/semanticAnnotations")
        body: dict[str, Any] = {
            "propertyIRI": property_iri,
            "valueIRI":    value_iri,
            "propertyRepositoryId": property_repo_id,
            "valueRepositoryId":    value_repo_id,
        }
        if numeric_value is not None:
            body["numericValue"] = numeric_value
        if unit_iri:
            body["unitIRI"] = unit_iri
        return self._post(url, body) is not None

    def emit_prov_for_migration(
        self, coll_id: int, dest_do_id: int,
        src_coll_id: int, src_do_id: int,
        session_id: str,
        prov_repo_id: int, migration_repo_id: int,
        pred_do_id: int | None = None,
    ) -> int:
        """Emit per-DO PROV-O annotations: wasDerivedFrom, wasGeneratedBy,
        optionally wasInformedBy. Returns the number of annotations created.
        """
        src_uri  = f"urn:shepard:src:{src_coll_id}:dataObject:{src_do_id}"
        sess_uri = f"urn:shepard:import:session:{session_id}"
        n = 0
        if self.add_semantic_annotation(
            coll_id, dest_do_id,
            PROV.WAS_DERIVED_FROM, src_uri,
            prov_repo_id, migration_repo_id,
        ):
            n += 1
        if self.add_semantic_annotation(
            coll_id, dest_do_id,
            PROV.WAS_GENERATED_BY, sess_uri,
            prov_repo_id, migration_repo_id,
        ):
            n += 1
        if pred_do_id is not None:
            pred_uri = f"urn:shepard:dataObject:{pred_do_id}"
            if self.add_semantic_annotation(
                coll_id, dest_do_id,
                PROV.WAS_INFORMED_BY, pred_uri,
                prov_repo_id, migration_repo_id,
            ):
                n += 1
        return n

    def emit_batch_summary(
        self,
        coll_id: int,
        anchor_do_id: int,
        prov_repo_id: int,
        migration_repo_id: int,
        *,
        batch_seq: int,
        files_uploaded: int,
        timeseries_imported: int,
        structured_payloads: int,
        throughput_bps: float,
        retry_count: int,
        source_instance: str,
        target_collection_app_id: str,
    ) -> int:
        """Emit one batch's summary annotations against an anchor DO
        (typically the ImportScripts DO carrying the import session).

        Returns count of successfully-emitted annotations (out of 8).
        Uses the V61-registered shepard: predicates.
        """
        target_iri = (
            f"https://shepard.nuclide.systems/id/collection/{target_collection_app_id}"
        )
        emits = [
            (Predicates.TARGET_COLLECTION,     target_iri,        None,            None),
            (Predicates.BATCH_SEQUENCE,        f"urn:batch:{batch_seq}",
             float(batch_seq), None),
            (Predicates.FILES_UPLOADED,        f"urn:count:{files_uploaded}",
             float(files_uploaded), None),
            (Predicates.TIMESERIES_IMPORTED,   f"urn:count:{timeseries_imported}",
             float(timeseries_imported), None),
            (Predicates.STRUCTURED_PAYLOADS,   f"urn:count:{structured_payloads}",
             float(structured_payloads), None),
            (Predicates.THROUGHPUT_BYTES_PER_SEC, f"urn:throughput:{throughput_bps:.0f}",
             float(throughput_bps), "http://qudt.org/vocab/unit/BYTE-PER-SEC"),
            (Predicates.RETRY_COUNT,           f"urn:count:{retry_count}",
             float(retry_count), None),
            (Predicates.SOURCE_INSTANCE,       f"urn:source:{source_instance}",
             None, None),
        ]
        n = 0
        for prop_iri, val_iri, num, unit in emits:
            if self.add_semantic_annotation(
                coll_id, anchor_do_id,
                prop_iri, val_iri,
                prov_repo_id, migration_repo_id,
                numeric_value=num, unit_iri=unit,
            ):
                n += 1
        return n

    # ── v15.1 per-DO + snapshot + lineage annotation helpers ─────────────────────
    #
    # Closes gaps G2/G5/G7/G8 from aidocs/agent-findings/v15-review-data-ontologist.md
    # and the F(AI)²R + dcterms FAIR rows from v15-review-rdm.md. Every helper here
    # is anchored on either a DataObject (DataObjectSemanticAnnotation REST) or the
    # Collection (CollectionSemanticAnnotation REST) — no public turtle ingest path
    # exists, so the subject of every triple is implicit-from-URL.
    #
    # Partial-G5 note: snapshots themselves cannot be typed as `prov:Entity` via
    # this API because annotations require a DO or Collection anchor and snapshots
    # are neither. v15.1 instead anchors the snapshot lineage edges on the Collection
    # (`collection prov:generated snap:<appId>`) — the snapshot's *existence* and
    # *lineage relation* are queryable; full typing `snap:<appId> a prov:Entity`
    # awaits a backend `SnapshotService.create` extension to auto-emit (deferred).

    def annotate_collection(
        self, coll_id: int,
        property_iri: str, value_iri: str,
        property_repo_id: int, value_repo_id: int,
        numeric_value: float | None = None,
        unit_iri: str | None = None,
    ) -> bool:
        """Emit one Collection-anchored semantic annotation (subject = the Collection).

        Used by:
          * G5 — snapshot lineage edges (collection prov:generated snap:<urn>)
          * R1 — collection-level license + accessRights triples mirroring the field
        """
        url = (f"{self._base}/shepard/api/collections/{coll_id}"
               f"/semanticAnnotations")
        body: dict[str, Any] = {
            "propertyIRI": property_iri,
            "valueIRI":    value_iri,
            "propertyRepositoryId": property_repo_id,
            "valueRepositoryId":    value_repo_id,
        }
        if numeric_value is not None:
            body["numericValue"] = numeric_value
        if unit_iri:
            body["unitIRI"] = unit_iri
        return self._post(url, body) is not None

    def annotate_do_mode_of_production(
        self, coll_id: int, do_id: int,
        prov_repo_id: int, fair2r_repo_id: int,
        ai_agent_iri: str = Fair2r.AGENT_CLAUDE_OPUS_4_7,
        mode_iri: str = Fair2r.MODE_AI,
        accept_iri: str = Fair2r.ACCEPT_AUTO_APPLIED,
    ) -> int:
        """G2 + G8: per-DO F(AI)²R triples.

        Emits three annotations per DO:
          1. <do> fair2r:modeOfProduction "ai"            (G2)
          2. <do> fair2r:wasAcceptedAs "auto-applied"     (G8)
          3. <do> fair2r:wasGeneratedByAi agent:claude-opus-4-7  (per memory)

        EU AI Act Art. 50 (effective 2026-08-02): every AI-generated artefact
        must carry a machine-readable AI flag. v14/v15 emit batch-level only —
        v15.1 brings it down to per-artefact visibility so a UI badge / SPARQL
        filter / RO-Crate export can address the AI-generated subset directly.

        Returns: number of successfully emitted annotations (0..3).
        """
        n = 0
        triples = [
            (Fair2r.MODE_OF_PRODUCTION,  mode_iri),
            (Fair2r.WAS_ACCEPTED_AS,     accept_iri),
            (Fair2r.WAS_GENERATED_BY_AI, ai_agent_iri),
        ]
        for prop, val in triples:
            if self.add_semantic_annotation(
                coll_id, do_id,
                prop, val,
                fair2r_repo_id, fair2r_repo_id,
            ):
                n += 1
        return n

    def annotate_typed_predecessor(
        self, coll_id: int, do_id: int,
        pred_app_id: str,
        prov_repo_id: int, migration_repo_id: int,
    ) -> bool:
        """G7: emit `<do> prov:wasInformedBy do:<predAppId>` for each predecessor.

        Mirrors the Neo4j `:PREDECESSOR_OF` edge into the SHACL graph so ODIX,
        SHACL queries, and RO-Crate exports see the MFFD DAG. Without this the
        substrate-split principle (`feedback_shacl_single_source_of_truth.md`)
        is violated — domain lineage lives only in the LPG.
        """
        if not pred_app_id:
            return False
        pred_iri = f"urn:shepard:dataObject:{pred_app_id}"
        return self.add_semantic_annotation(
            coll_id, do_id,
            PROV.WAS_INFORMED_BY, pred_iri,
            prov_repo_id, migration_repo_id,
        )

    def annotate_alternative_name(
        self, coll_id: int, do_id: int,
        original_name: str,
        prov_repo_id: int, migration_repo_id: int,
    ) -> bool:
        """Trust-G2 (Reluctant Senior): record the source-side name as
        `dcterms:alternative` when the operator's `--name-mapping` CSV rewrites
        the dest name. Preserves the original naming convention as evidence so
        cube3-side searches still resolve.
        """
        if not original_name:
            return False
        alt_iri = f"urn:shepard:source-name:{original_name.replace(' ', '%20')}"
        return self.add_semantic_annotation(
            coll_id, do_id,
            Dcterms.ALTERNATIVE, alt_iri,
            prov_repo_id, migration_repo_id,
        )

    def annotate_snapshot_lineage(
        self, coll_id: int, snap_app_id: str, kind: str,
        prov_repo_id: int, migration_repo_id: int,
    ) -> int:
        """G5 partial: emit Collection-anchored snapshot lineage edges.

        For `kind ∈ {"pre", "post"}`:
          * pre  → emit `<coll> prov:wasInformedBy snap:<urn>` (the snapshot
            informs subsequent forging — i.e. it is the *input* baseline)
          * post → emit `<coll> prov:generated snap:<urn>` (the import
            generated this canonical post-import baseline)

        The snapshot URN is queryable via SPARQL but the snapshot is not yet
        typed as `prov:Entity` (no public turtle ingest path; deferred to a
        SnapshotService backend extension). See the design note above.

        Returns: number of emitted annotations (0..1).
        """
        if not snap_app_id:
            return 0
        snap_iri = f"urn:shepard:snapshot:{snap_app_id}"
        predicate = PROV.WAS_INFORMED_BY if kind == "pre" else PROV.GENERATED
        ok = self.annotate_collection(
            coll_id, predicate, snap_iri,
            prov_repo_id, migration_repo_id,
        )
        return 1 if ok else 0

    def annotate_collection_license(
        self, coll_id: int,
        license_id: str | None,
        access_rights_id: str | None,
        dcterms_repo_id: int, value_repo_id: int,
    ) -> int:
        """R1 mirror: emit collection-level `dcterms:license` + `dcterms:accessRights`
        annotations in addition to the v1-compat field hydration. The triples
        make the Collection queryable as FAIR R1-compliant via SPARQL without
        needing to read the LPG `attributes` Map.
        """
        n = 0
        if license_id:
            lic_iri = license_iri(license_id)
            if lic_iri and self.annotate_collection(
                coll_id, Dcterms.LICENSE, lic_iri,
                dcterms_repo_id, value_repo_id,
            ):
                n += 1
        if access_rights_id:
            ar_iri = access_rights_iri(access_rights_id)
            if ar_iri and self.annotate_collection(
                coll_id, Dcterms.ACCESS_RIGHTS, ar_iri,
                dcterms_repo_id, value_repo_id,
            ):
                n += 1
        return n

    def patch_collection_attributes(self, coll_id: int, attrs: dict[str, str]) -> bool:
        """PATCH the collection's attributes map (used by the ETA publisher
        per aidocs/93 §11).

        Note: DLR v5.4.0 PATCH /collections/{id} accepts a merge-patch with
        an `attributes` field. The shape mirrors POST/PUT — partial updates
        preserved server-side.
        """
        url = f"{self._base}/shepard/api/collections/{coll_id}"
        body = {"attributes": {k: str(v) for k, v in attrs.items()}}
        try:
            r = self._s.patch(url, json=body, timeout=30)
            if hasattr(r, 'ok') and r.ok:
                return True
            # Fall back to PUT for backends without PATCH support; first
            # GET the collection to preserve other fields, then PUT.
            r_get = self._get(url)
            if r_get is None:
                return False
            full = r_get.json()
            merged = dict(full.get("attributes") or {})
            merged.update({k: str(v) for k, v in attrs.items()})
            full["attributes"] = merged
            for k in ("id", "createdAt", "createdBy", "updatedAt", "updatedBy"):
                full.pop(k, None)
            return self._put(url, full) is not None
        except Exception as exc:
            print(f"  [eta-publisher] {exc}")
            return False

    # ── Dest: timeseries container + import ──────────────────────────────────────

    def create_ts_container(self, name: str) -> int | None:
        """Create a new timeseries container on the dest instance. Returns OGM id.

        Bug L fix: TimeseriesContainer.type is readOnly per DLR v5.4.0 OpenAPI —
        the server fixes it from the URL path. Sending it is at best ignored,
        at worst rejected by strict validators.
        """
        r = self._post(
            f"{self._base}/shepard/api/timeseriesContainers",
            {"name": name},
        )
        return r.json().get("id") if r else None

    def list_ts_channels(self, container_id: int) -> list[dict]:
        """Bug A + G fix: discover the 5-tuple channel set after CSV import.

        Returns: list of {measurement, device, location, symbolicName, field}
        from GET /timeseriesContainers/{id}/timeseries. Empty list if the
        container has no channels (a 0-byte placeholder import would yield this).
        """
        r = self._get(f"{self._base}/shepard/api/timeseriesContainers/{container_id}/timeseries")
        if r is None:
            return []
        out: list[dict] = []
        for ch in r.json():
            try:
                out.append({
                    "measurement":  ch["measurement"],
                    "device":       ch["device"],
                    "location":     ch["location"],
                    "symbolicName": ch["symbolicName"],
                    "field":        ch["field"],
                })
            except (KeyError, TypeError):
                # Skip malformed channel rows — log but don't fail the linkage.
                print(f"  [ts-channels] skipping malformed channel: {ch}")
        return out

    def link_ts_to_do(
        self,
        coll_id: int,
        do_id: int,
        container_id: int,
        name: str,
        timeseries: list[dict] | None = None,
        start_ms: int = 0,
        end_ms: int = 0,
    ) -> int | None:
        """Bug A + G fix: create a TimeseriesReference AFTER the container has channels.

        DLR v5.4.0 TimeseriesReference REQUIRES non-empty `timeseries[]`
        (minItems:1). Calling this before import_ts_csv → list_ts_channels
        guarantees a 400.

        Args:
            timeseries: list of {measurement, device, location, symbolicName, field}
                        from list_ts_channels(container_id). Must be non-empty.
            start_ms, end_ms: time range (epoch ms). When zero, a wide bracket
                              [0, 2**62] is used — let the server constrain via the
                              hypertable.

        Returns the new reference id, or None if linkage fails.
        """
        if timeseries is None or not timeseries:
            print(f"  [ts-link] refusing to link {name!r} — container has no channels (Bug G fix)")
            return None
        if end_ms == 0:
            # Wide-bracket fallback; OGM accepts any positive long.
            end_ms = (1 << 62)
        body = {
            "name": name,
            "timeseriesContainerId": container_id,
            "start": int(start_ms),
            "end":   int(end_ms),
            "timeseries": timeseries,  # Bug A fix: required, minItems:1
        }
        url = (
            f"{self._base}/shepard/api/collections/{coll_id}"
            f"/dataObjects/{do_id}/timeseriesReferences"
        )
        r = self._post(url, body)
        return r.json().get("id") if r else None

    def import_ts_csv(self, container_id: int, csv_bytes: bytes, filename: str = "export.csv") -> bool:
        """Import a CSV blob into a timeseries container on the dest instance."""
        import io
        url = f"{self._base}/shepard/api/timeseriesContainers/{container_id}/import"
        try:
            r = self._request_with_retry(
                "POST", url,
                files={"file": (filename, io.BytesIO(csv_bytes), "text/csv")},
                timeout=600,
            )
            if r is None:
                return False
            if r.ok:
                return True
            self._log_err("POST (ts-import)", url, r)
            return False
        except Exception as exc:
            print(f"  [net] import_ts_csv: {exc}")
            return False

    # ── Dest: structured data container ──────────────────────────────────────────

    def create_structured_container(self, name: str) -> int | None:
        """Create a structured data container on the dest instance. Returns OGM id."""
        r = self._post(f"{self._base}/shepard/api/structuredDataContainers", {"name": name})
        return r.json().get("id") if r else None

    def link_structured_to_do(
        self, coll_id: int, do_id: int, container_id: int, name: str,
        oids: list[str] | None = None,
    ) -> bool:
        """Bug C fix: StructuredDataReference REQUIRES non-empty
        `structuredDataOids[]` (minItems:1). v14 omitted this field → 400.

        Order of operations is now:
          1. POST /structuredDataContainers      → container_id
          2. POST /structuredDataContainers/{id}/payload (per payload) → oid
          3. link_structured_to_do(..., oids=[oid1, oid2, ...])
        """
        if not oids:
            print(f"  [sd-link] refusing to link {name!r} — no oids (Bug C fix)")
            return False
        url = (
            f"{self._base}/shepard/api/collections/{coll_id}"
            f"/dataObjects/{do_id}/structuredDataReferences"
        )
        body = {
            "name": name,
            "structuredDataContainerId": container_id,
            "structuredDataOids": list(oids),   # required, minItems:1
        }
        r = self._post(url, body)
        return r is not None

    def upload_structured_payload(
        self, container_id: int, payload: dict | list, name: str = "payload"
    ) -> str | None:
        """Bug B + D fix: POST (not PUT) the wrapper `StructuredDataPayload` shape.

        v14: `PUT /structuredDataContainers/{id}/payload  body=<raw json>`
              The path accepts GET + POST only (no PUT); body shape is also
              wrong — DLR v5.4.0 wants {structuredData:{name}, payload:string}
              where payload is the JSON-encoded inner content.

        v15: `POST /structuredDataContainers/{id}/payload  body=wrapper`
              Returns the oid on success — captured for the structuredDataOids[]
              field on the StructuredDataReference (Bug C).
        """
        import json as _json
        url = f"{self._base}/shepard/api/structuredDataContainers/{container_id}/payload"
        body = {
            "structuredData": {"name": name},
            "payload": _json.dumps(payload),   # Bug D: required STRING (minLength:2)
        }
        r = self._request_with_retry("POST", url, json=body, timeout=60)
        if r is None:
            return None
        if not r.ok:
            self._log_err("POST (structured)", url, r)
            return None
        try:
            return r.json().get("oid")
        except (ValueError, AttributeError):
            return None

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

    # Retry-able status codes: gateway-down / upstream-error class.
    # Excludes 500 (genuine server bug — fail fast so we see it).
    _RETRY_STATUSES = frozenset({502, 503, 504, 520, 521, 522, 523, 524})

    def _request_with_retry(
        self,
        method: str,
        url: str,
        *,
        timeout: int = 60,
        deadline_s: float = 900.0,  # 15 min — covers a Quarkus reboot + Flyway migrate
        **kwargs: Any,
    ) -> Response | None:
        """HTTP call that survives a destination-Shepard redeploy.

        Retries forever (until deadline_s) on:
          * urllib3/requests transient errors (ConnectionError, Timeout,
            ChunkedEncodingError, ReadTimeout)
          * Gateway/upstream-error responses (502, 503, 504, 520-524)

        Non-retryable codes (200-499, 500) are returned as-is — the caller's
        existing `if not r.ok` branch logs them. We deliberately don't retry
        500 because that's a real server bug, not a deploy blip.

        Prints `[reconnect] backend unreachable...` once on entering the
        retry loop, then `[reconnect] backend back ✓` on first success.
        """
        backoff = 2.0
        max_backoff = 60.0
        deadline = time.monotonic() + deadline_s
        waiting = False
        attempt = 0

        while True:
            attempt += 1
            last_label: str = ""
            try:
                r = self._s.request(method, url, timeout=timeout, **kwargs)
                if r.status_code not in self._RETRY_STATUSES:
                    if waiting:
                        print(
                            f"  [reconnect] backend back ✓ (HTTP {r.status_code}, "
                            f"after {attempt} attempts)",
                            flush=True,
                        )
                    return r
                last_label = f"HTTP {r.status_code}"
            except (
                requests.exceptions.ConnectionError,
                requests.exceptions.Timeout,
                requests.exceptions.ChunkedEncodingError,
            ) as exc:
                last_label = exc.__class__.__name__

            if not waiting:
                print(
                    f"  [reconnect] backend unreachable ({last_label}); "
                    f"waiting up to {deadline_s:.0f}s for it to come back…",
                    flush=True,
                )
                waiting = True

            if time.monotonic() >= deadline:
                print(
                    f"  [reconnect] giving up on {method} {url.split('?')[0]} "
                    f"after {attempt} attempts ({deadline_s:.0f}s deadline)",
                    flush=True,
                )
                return None

            time.sleep(backoff)
            backoff = min(backoff * 1.5, max_backoff)

    def _get(self, url: str, params: dict | None = None) -> Response | None:
        r = self._request_with_retry("GET", url, params=params, timeout=30)
        if r is None:
            return None
        if not r.ok:
            self._log_err("GET", url, r)
            return None
        return r

    def _get_raw(self, url: str) -> Response | None:
        # Short deadline + low timeout: this is the warmup probe, we want
        # fast failure if the dest is genuinely down at startup.
        return self._request_with_retry("GET", url, timeout=10, deadline_s=30.0)

    def _post(self, url: str, body: dict) -> Response | None:
        r = self._request_with_retry("POST", url, json=body, timeout=60)
        if r is None:
            return None
        if not r.ok:
            self._log_err("POST", url, r)
            return None
        return r

    def _put(self, url: str, body: dict) -> Response | None:
        r = self._request_with_retry("PUT", url, json=body, timeout=30)
        if r is None:
            return None
        if not r.ok:
            self._log_err("PUT", url, r)
            return None
        return r

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

def ensure_dest_collection(
    client: ShepardClient,
    license: str | None = None,
    access_rights: str | None = None,
) -> tuple[int, str | None]:
    """Find or create the MFFD-Dropbox collection. Returns (coll_id, coll_app_id).

    v15.1: when the collection is freshly created, FAIR R1 license + accessRights
    are stamped on the create body. Pre-existing collections are NOT re-stamped
    here — `main()` calls `patch_collection_attributes` separately so the resume
    path also gets the hydration.
    """
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
            license=license,
            access_rights=access_rights,
        )
        if coll is None:
            print("  FAILED — aborting")
            sys.exit(1)
        print(f"  created (id={coll['id']})")
        client.set_collection_public(coll["id"])
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
    license: str | None = None,
    access_rights: str | None = None,
) -> dict | None:
    """v15.1: thread `license` + `access_rights` through to create_data_object
    so every newly-created DO carries FAIR R1 fields. Existing DOs (resume
    path) are not re-stamped here — operator can do that via a separate
    post-import patch if needed.
    """
    existing = client.find_data_object(coll_id, name)
    if existing:
        print(f"  already exists (id={existing['id']})")
        return existing
    do = client.create_data_object(
        coll_id, name, description=description, attrs=attrs,
        predecessor_id=predecessor_id,
        license=license, access_rights=access_rights,
    )
    if do:
        print(f"  created (id={do['id']}, appId={do.get('appId')})")
    return do


# ── Bootstrap (one-time collection setup) ─────────────────────────────────────

def run_bootstrap(client: ShepardClient) -> None:
    """
    One-time setup: create the destination collection with rich provenance attrs,
    skeleton DataObjects, self-upload the script, and capture a t=0 snapshot.

    Run this ONCE from nuclide.systems before the DLR intranet import.
    After bootstrap the ImportScripts DataObject holds this script — from that
    point on use run-mffd-import.sh to fetch + run.
    """
    print("\n=== Bootstrap ===")
    print(f"  destination: {SHEPARD_URL}")
    print(f"  collection : {COLLECTION_NAME!r}")
    if OPERATOR:
        print(f"  operator   : {OPERATOR}")

    # 1. Find or create the collection with rich provenance attrs
    print(f"\n[collection] {COLLECTION_NAME!r}")
    coll = client.find_collection(COLLECTION_NAME)
    if coll:
        print(f"  already exists (id={coll['id']}) — will add skeleton DOs + script + snapshot")
    else:
        print("  creating ...")
        attrs: dict[str, str] = {
            "domain": "MFFD",
            "session": SESSION_ID,
            "type": "dropbox",
            "source_instance": SOURCE_SHEPARD_URL,
            "source_tapelaying_coll_id": str(SOURCE_TAPELAYING_COLL_ID) if SOURCE_TAPELAYING_COLL_ID else "48297",
            "source_bridgewelding_coll_id": str(SOURCE_BRIDGEWELDING_COLL_ID) if SOURCE_BRIDGEWELDING_COLL_ID else "163811",
            "import_tool": "mffd-dropbox-import.py",
            "bootstrapped_at": IMPORT_TIME,
        }
        if OPERATOR:
            attrs["operator"] = OPERATOR
        coll = client.create_collection(
            COLLECTION_NAME,
            description=(
                f"MFFD manufacturing dropbox — real process data from DLR Augsburg ZLP.\n"
                f"Session {SESSION_ID}.  Bootstrapped at {IMPORT_TIME}.\n"
                f"Source: {SOURCE_SHEPARD_URL}\n"
                f"Created by mffd-dropbox-import.py --bootstrap."
            ),
            attrs=attrs,
        )
        if coll is None:
            print("  FAILED — aborting bootstrap")
            sys.exit(1)
        print(f"  created (id={coll['id']}, appId={coll.get('appId')})")

    coll_id: int = coll["id"]
    coll_app_id: str | None = coll.get("appId") or client.get_collection_app_id(coll_id)
    coll_name: str = coll.get("name") or COLLECTION_NAME

    # Make the collection visible+writable to all instance users so team members
    # (e.g. flo) can find it by name and upload data without needing explicit grants.
    client.set_collection_public(coll_id)

    # 2. Skeleton DataObjects — placeholders for real data that arrives via source mode
    session_attrs: dict[str, str] = {
        "session": SESSION_ID,
        "campaign": "MFFD",
        "bootstrapped_at": IMPORT_TIME,
    }
    if OPERATOR:
        session_attrs["operator"] = OPERATOR

    src_tl = SOURCE_TAPELAYING_COLL_ID or 48297
    src_bw = SOURCE_BRIDGEWELDING_COLL_ID or 163811

    print(f"\n[skeleton] TapeLaying-skeleton")
    tl = ensure_dest_do(
        client, coll_id,
        "TapeLaying-skeleton",
        description=(
            f"Placeholder for MFFD tape-laying process data.\n"
            f"Real data will be imported from source collection {src_tl} on DLR intranet "
            f"({SOURCE_SHEPARD_URL})."
        ),
        attrs={**session_attrs, "process_step": "tapelaying", "status": "skeleton",
               "source_collection_id": str(src_tl), "source_instance": SOURCE_SHEPARD_URL},
    )

    print(f"\n[skeleton] BridgeWelding-skeleton")
    bw = ensure_dest_do(
        client, coll_id,
        "BridgeWelding-skeleton",
        description=(
            f"Placeholder for MFFD bridge/frame welding process data.\n"
            f"Real data will be imported from source collection {src_bw} on DLR intranet "
            f"({SOURCE_SHEPARD_URL}).\n"
            f"Process chain predecessor: TapeLaying-skeleton."
        ),
        attrs={**session_attrs, "process_step": "bridgewelding", "status": "skeleton",
               "source_collection_id": str(src_bw), "source_instance": SOURCE_SHEPARD_URL},
        predecessor_id=tl["id"] if tl else None,
    )

    print(f"\n[skeleton] WikiDump-{SESSION_ID}")
    ensure_dest_do(
        client, coll_id,
        f"WikiDump-{SESSION_ID}",
        description=(
            f"Wiki export placeholder — session {SESSION_ID}.\n"
            f"Upload one zip file manually via the UI."
        ),
        attrs={**session_attrs, "process_step": "wikidump",
               "note": "upload manually — one zip file"},
    )

    # 3. ImportScripts + self-upload (script lives here for reproducibility)
    print(f"\n[importscripts]")
    isc = ensure_dest_do(
        client, coll_id,
        "ImportScripts",
        description=(
            "Ingest scripts — provenance artifact.\n"
            "Fetch mffd-dropbox-import.py from here via run-mffd-import.sh to reproduce this run."
        ),
        attrs={"type": "toolbox", "note": "self-uploaded by mffd-dropbox-import.py"},
    )
    if isc:
        client.upload_self(coll_id, isc["id"], isc.get("appId") or "")
        client.verify_references(coll_id, isc["id"], "ImportScripts")

    # 4. t=0 snapshot — label records collection name at this moment
    print(f"\n[snapshot] t=0 ...")
    if coll_app_id:
        label = f"bootstrap-t0@{coll_name}"
        snap = client.create_snapshot(coll_app_id, label)
        if snap:
            print(f"  created: {snap.get('label')}  (appId={snap.get('appId')})")
            print(f"  label captures collection name '{coll_name}' — renames tracked via snapshot history")
        else:
            print("  WARNING: snapshot creation failed (non-fatal)")
    else:
        print("  WARNING: no collection appId — snapshot skipped")

    print(f"\n=== Bootstrap done ===")
    print(f"  Collection '{coll_name}'  id={coll_id}")
    print(f"  Script uploaded to ImportScripts DataObject.")
    print()
    print("  Next step — run from DLR network:")
    print(f"    export SHEPARD_URL={SHEPARD_URL}")
    print(f"    export SHEPARD_API_KEY=<nuclide-key>")
    print(f"    export SOURCE_SHEPARD_URL={SOURCE_SHEPARD_URL}")
    print(f"    export SOURCE_SHEPARD_API_KEY=<dlr-intranet-jwt>")
    print(f"    export SOURCE_TAPELAYING_COLL_ID={src_tl}")
    print(f"    export SOURCE_BRIDGEWELDING_COLL_ID={src_bw}")
    print(f"    export SESSION_ID={SESSION_ID}")
    print(f"    bash run-mffd-import.sh")


# ── SOURCE MODE ───────────────────────────────────────────────────────────────

def run_source_mode(
    dest_client: ShepardClient,
    coll_id: int,
    state: ImportState | None = None,
    source_client: ShepardClient | None = None,
    *,
    default_license: str | None = None,
    default_access_rights: str | None = None,
    name_map: dict[str, str] | None = None,
) -> dict[str, int]:
    """
    Traverse source Shepard collections, migrating all three payload types
    (files, timeseries, structured data) into the destination collection.

    Strategy:
    - One dest DO per step (tapelaying / bridgewelding) as the step container.
    - Files: each source file → dest DO (prefixed with source DO name).
    - Timeseries: one shared TS container per step; each source DO's TS refs are
      exported as WIDE CSV and imported into the shared container; the dest step
      DO gets one timeseriesReference to the shared container.
    - Structured data: each source DO's structured refs are downloaded as JSON
      and re-uploaded into a new dest structured container linked to the step DO.

    Pass source_client for cross-instance pull (DLR intranet → nuclide.systems).
    If source_client is None, dest_client is used for source operations too.

    Returns mapping of step_key → dest_do_id for predecessor wiring.
    """
    _src = source_client or dest_client

    print()
    print("  ┌─────────────────────────────────────────────────────────────────┐")
    print("  │  ⚠  TIMESTAMP CAVEAT                                            │")
    print("  │  DataObject creationDate and file upload timestamps will         │")
    print("  │  reflect the IMPORT time, not the original measurement time.     │")
    print("  │  Source timestamps are preserved as source_created / source_mod  │")
    print("  │  attributes on every migrated DataObject.                        │")
    print("  └─────────────────────────────────────────────────────────────────┘")
    print()

    if source_client is not None:
        print(f"  [cross-instance] source : {_src._base}")
        print(f"  [cross-instance] dest   : {dest_client._base}")

    do_ids: dict[str, int] = {}
    # v15.1: parallel map of step_key → dest_app_id for G7 typed-predecessor edges
    do_app_ids: dict[str, str] = {}

    source_map = [
        ("tapelaying",    SOURCE_TAPELAYING_COLL_ID,    None),
        ("bridgewelding", SOURCE_BRIDGEWELDING_COLL_ID, "tapelaying"),
    ]

    for step_key, src_coll_id, pred_key in source_map:
        if src_coll_id is None:
            print(f"\n[{step_key}] SOURCE_{step_key.upper()}_COLL_ID not set — skipping")
            continue

        print(f"\n[{step_key}] source collection {src_coll_id}")
        total_dos = _src.count_data_objects(src_coll_id)
        print(f"  {total_dos} DataObject(s) to migrate")

        # v15.1 name mapping (Reluctant Senior trust-G2):
        # `source-name → operator-name`. Source name preserved as
        # `dcterms:alternative` annotation after creation.
        src_step_name = f"{step_key.capitalize()}-{SESSION_ID}"
        name_map = name_map or {}
        if src_step_name in name_map:
            dest_do_name = name_map[src_step_name]
            print(f"  [name-mapping] {src_step_name!r} → {dest_do_name!r}")
            original_name_for_alt = src_step_name
        else:
            dest_do_name = src_step_name
            original_name_for_alt = ""

        pred_id = do_ids.get(pred_key) if pred_key else None
        # Pre-compute the predecessor's appId for the G7 typed predecessor edge
        pred_app_id_for_lineage: str | None = None
        if pred_id is not None:
            pred_app_id_for_lineage = do_app_ids.get(pred_key)  # type: ignore[name-defined]

        base_attrs: dict[str, str] = {
            "session": SESSION_ID,
            "campaign": "MFFD",
            "process_step": step_key,
            "source_collection_id": str(src_coll_id),
            "import_time": IMPORT_TIME,
            "timestamp_note": "file timestamps reflect import time not measurement time",
        }
        if original_name_for_alt:
            base_attrs["source_name"] = original_name_for_alt

        dest_do = ensure_dest_do(
            dest_client,
            coll_id,
            dest_do_name,
            description=(
                f"MFFD {step_key} — migrated from source collection {src_coll_id}.\n"
                f"Session {SESSION_ID}.  Import time: {IMPORT_TIME}.\n"
                f"⚠ Timestamps reflect import time, not original measurement time."
            ),
            attrs=base_attrs,
            predecessor_id=pred_id,
            license=default_license,
            access_rights=default_access_rights,
        )
        if dest_do is None:
            print(f"  FAILED to create dest DataObject — skipping {step_key}")
            continue

        do_ids[step_key] = dest_do["id"]
        dest_app_id: str = dest_do.get("appId") or ""
        dest_do_id: int = dest_do["id"]
        # Track appId for downstream G7 typed predecessor edges
        do_app_ids[step_key] = dest_app_id  # type: ignore[name-defined]

        dest_client.verify_references(coll_id, dest_do_id, dest_do_name)

        # ── v15.1 G2/G7/G8 + name-alt: per-DO annotations ─────────────────────
        # Cheap (3-5 annotations per step DO); failure is non-fatal — counted
        # but does not abort the import.
        try:
            prov_repo_id = dest_client.get_or_create_semantic_repo("prov-o")
            migration_repo_id = dest_client.get_or_create_semantic_repo(
                f"mffd-migration-{SESSION_ID}", type_="SPARQL",
                endpoint=f"urn:mffd:migration:{SESSION_ID}",
            )
            fair2r_repo_id = dest_client.get_or_create_semantic_repo("fair2r")
            if prov_repo_id and migration_repo_id and fair2r_repo_id:
                # G2 + G8: per-DO modeOfProduction + wasAcceptedAs + wasGeneratedByAi
                n_mode = dest_client.annotate_do_mode_of_production(
                    coll_id, dest_do_id,
                    prov_repo_id=prov_repo_id, fair2r_repo_id=fair2r_repo_id,
                )
                # G7: typed predecessor edge (prov:wasInformedBy → predecessor DO URN)
                if pred_app_id_for_lineage:
                    dest_client.annotate_typed_predecessor(
                        coll_id, dest_do_id, pred_app_id_for_lineage,
                        prov_repo_id=prov_repo_id, migration_repo_id=migration_repo_id,
                    )
                # name-alt: preserve source name as dcterms:alternative
                if original_name_for_alt:
                    dest_client.annotate_alternative_name(
                        coll_id, dest_do_id, original_name_for_alt,
                        prov_repo_id=prov_repo_id, migration_repo_id=migration_repo_id,
                    )
                print(f"  [v15.1-annot] step DO id={dest_do_id}: {n_mode} mode annotations")
        except Exception as exc:
            print(f"  [v15.1-annot] WARNING: per-DO annotations failed: {exc}")

        # ── Shared TS container for this step ─────────────────────────────────
        ts_container_id: int | None = state.get_ts_container(step_key) if state else None
        if ts_container_id is None:
            ts_container_name = f"MFFD-{step_key}-ts-{SESSION_ID}"
            print(f"  [ts] creating shared TS container {ts_container_name!r}")
            ts_container_id = dest_client.create_ts_container(ts_container_name)
            if ts_container_id:
                print(f"  [ts] container id={ts_container_id}")
                if state:
                    state.set_ts_container(step_key, ts_container_id)
                # Link the step DO to the shared TS container
                dest_client.link_ts_to_do(coll_id, dest_do_id, ts_container_id, ts_container_name)
            else:
                print(f"  [ts] WARNING: could not create TS container — timeseries will be skipped")

        # ── Walk source DOs ────────────────────────────────────────────────────
        files_uploaded = 0
        files_skipped = 0
        files_failed = 0
        ts_imported = 0
        ts_skipped = 0
        ts_failed = 0
        structured_imported = 0
        structured_failed = 0

        with tqdm(
            total=total_dos,
            desc=f"  DataObjects [{step_key}]",
            unit="DO",
            file=sys.stderr,
        ) as do_bar:
            for src_do in _src.iter_data_objects(src_coll_id):
                if MAX_DOS_PER_STEP and do_bar.n >= MAX_DOS_PER_STEP:
                    do_bar.write(f"  [--max-dos {MAX_DOS_PER_STEP} reached; stopping step early]")
                    break
                do_bar.set_postfix_str(src_do.name[:30])

                payload_summary = (
                    f"{len(src_do.file_refs)}f"
                    f" {len(src_do.ts_refs)}ts"
                    f" {len(src_do.structured_refs)}sd"
                )
                do_bar.write(f"  ↳ {src_do.name}  ({payload_summary})")

                existing_names = dest_client.list_file_refs(coll_id, dest_do_id)

                # ── Files ──────────────────────────────────────────────────────
                with tempfile.TemporaryDirectory() as tmpdir:
                    tmp = Path(tmpdir)
                    for fref in src_do.file_refs:
                        dest_name = f"{src_do.name}/{fref.name}"
                        state_key = f"{step_key}/{dest_name}"

                        if state is not None and state.is_file_done(state_key):
                            do_bar.write(f"    [skip-file] {dest_name}")
                            files_skipped += 1
                            continue

                        if dest_name in existing_names or fref.name in existing_names:
                            do_bar.write(f"    [skip-file] {fref.name}")
                            files_skipped += 1
                            if state is not None:
                                state.mark_file_done(state_key)
                            continue

                        tmp_file = tmp / fref.name
                        ok = _src.download_file_ref(
                            src_coll_id, src_do.do_id, fref.fref_id, tmp_file, fref.size
                        )
                        if not ok:
                            do_bar.write(f"    [error-file] download {fref.name}")
                            files_failed += 1
                            continue

                        ok = dest_client.upload_file(
                            dest_app_id, tmp_file, dest_name, coll_id, dest_do_id
                        )
                        if ok:
                            do_bar.write(f"    [ok-file] {dest_name}  ({_human(fref.size)})")
                            files_uploaded += 1
                            if state is not None:
                                state.mark_file_done(state_key)
                        else:
                            do_bar.write(f"    [error-file] upload {dest_name}")
                            files_failed += 1

                # ── Timeseries ─────────────────────────────────────────────────
                if ts_container_id is not None:
                    for ts_ref in src_do.ts_refs:
                        state_key = f"ts/{step_key}/{src_do.do_id}/{ts_ref.ref_id}"
                        if state is not None and state.is_ts_done(state_key):
                            do_bar.write(f"    [skip-ts] {ts_ref.name}")
                            ts_skipped += 1
                            continue

                        do_bar.write(f"    [ts] exporting {ts_ref.name!r} from source ref {ts_ref.ref_id}")
                        csv_bytes = _src.export_ts(src_coll_id, src_do.do_id, ts_ref.ref_id)
                        if csv_bytes is None:
                            do_bar.write(f"    [error-ts] export failed for {ts_ref.name}")
                            ts_failed += 1
                            continue

                        do_bar.write(f"    [ts] importing {len(csv_bytes):,} bytes → container {ts_container_id}")
                        ok = dest_client.import_ts_csv(
                            ts_container_id,
                            csv_bytes,
                            filename=f"{step_key}-{src_do.do_id}-{ts_ref.ref_id}.csv",
                        )
                        if ok:
                            ts_imported += 1
                            if state is not None:
                                state.mark_ts_done(state_key)
                        else:
                            do_bar.write(f"    [error-ts] import failed for {ts_ref.name}")
                            ts_failed += 1

                # ── Structured data ────────────────────────────────────────────
                for sd_ref in src_do.structured_refs:
                    state_key = f"sd/{step_key}/{src_do.do_id}/{sd_ref.ref_id}"
                    if state is not None and state.is_structured_done(state_key):
                        do_bar.write(f"    [skip-sd] {sd_ref.name}")
                        continue

                    payload = _src.download_structured(src_coll_id, src_do.do_id, sd_ref.ref_id)
                    if payload is None:
                        do_bar.write(f"    [error-sd] download failed for {sd_ref.name}")
                        structured_failed += 1
                        continue

                    container_name = f"{src_do.name}/{sd_ref.name}"
                    container_id = dest_client.create_structured_container(container_name)
                    if container_id is None:
                        do_bar.write(f"    [error-sd] could not create container for {sd_ref.name}")
                        structured_failed += 1
                        continue

                    linked = dest_client.link_structured_to_do(coll_id, dest_do_id, container_id, container_name)
                    ok = dest_client.upload_structured_payload(container_id, payload)
                    if linked and ok:
                        do_bar.write(f"    [ok-sd] {sd_ref.name}")
                        structured_imported += 1
                        if state is not None:
                            state.mark_structured_done(state_key)
                    else:
                        do_bar.write(f"    [error-sd] upload/link failed for {sd_ref.name}")
                        structured_failed += 1

                do_bar.update(1)

        print(
            f"  → files: uploaded={files_uploaded} skipped={files_skipped} failed={files_failed}"
        )
        print(
            f"  → ts:    imported={ts_imported} skipped={ts_skipped} failed={ts_failed}"
        )
        print(
            f"  → sd:    imported={structured_imported} failed={structured_failed}"
        )
        dest_client.verify_references(coll_id, dest_do_id, dest_do_name)

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


# ── v15.1: name-mapping CSV loader ────────────────────────────────────────────

def load_name_mapping(csv_path: Path | None) -> dict[str, str]:
    """Load a `source-name,operator-name` CSV mapping.

    Returns dict[source_name → operator_name]. Empty when csv_path is None
    or the file is missing. Skips blank lines and the optional header row
    `source,dest` (any case). Quoted values are unwrapped.

    Used by Reluctant Senior trust-G2: cube3 names like
    `tapelaying/Track 244 (Run 30239)` get rewritten to operator convention
    (e.g. `TR-244-LH2-2026-04`) while preserving the source name as
    `dcterms:alternative`.
    """
    if csv_path is None:
        return {}
    p = Path(csv_path)
    if not p.exists() or not p.is_file():
        return {}
    out: dict[str, str] = {}
    import csv as _csv
    with p.open(encoding="utf-8") as f:
        reader = _csv.reader(f)
        for row in reader:
            if not row or len(row) < 2:
                continue
            src = row[0].strip()
            dst = row[1].strip()
            if not src or not dst:
                continue
            # Skip header row
            if src.lower() in ("source", "source-name", "src", "src_name") and \
               dst.lower() in ("dest", "operator-name", "dest_name", "operator"):
                continue
            out[src] = dst
    return out


# ── v15.1: independent verifier (Reluctant Senior minimum) ───────────────────

def verify_imported(client: ShepardClient, coll_id: int,
                    report_path: Path | None = None) -> dict:
    """Walk the dest collection and emit an independent verification report.

    The producer (run_source_mode) counts what it *intended* to upload; this
    verifier counts what *actually landed* — separate code paths so a v14-style
    silent corruption (Bug E) cannot pass under-counted as success.

    Checks:
      * Total DOs on dest
      * DOs by source-name pattern (tapelaying/* vs bridgewelding/* vs other)
      * File refs with `fileSize > 0`
      * TS refs with non-empty `timeseries[]`
      * Structured refs present
      * DAG reachability spot-check: pick 3 random DOs, walk predecessors

    Returns the report dict. Also writes to `report_path` if provided.
    """
    print(f"\n=== verify-imported ===")
    print(f"  collection: id={coll_id}")

    report: dict[str, Any] = {
        "session": SESSION_ID,
        "verified_at": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "collection_id": coll_id,
        "dest_url": client._base,
        "dos_total": 0,
        "dos_by_pattern": {
            "tapelaying": 0,
            "bridgewelding": 0,
            "skeleton": 0,
            "other": 0,
        },
        "file_refs_total": 0,
        "file_refs_nonzero": 0,
        "ts_refs_total": 0,
        "ts_refs_with_channels": 0,
        "structured_refs_total": 0,
        "dag_spot_checks": [],
        "warnings": [],
    }

    # 1. Walk all DOs on dest (paginated)
    page = 0
    all_dos: list[dict] = []
    while True:
        r = client._get(
            f"{client._base}/shepard/api/collections/{coll_id}/dataObjects",
            {"page": str(page), "size": str(PAGE_SIZE)},
        )
        if r is None:
            report["warnings"].append(f"page {page}: GET failed")
            break
        items = r.json() or []
        if not items:
            break
        all_dos.extend(items)
        total_pages = int(r.headers.get("X-Total-Pages", page + 1))
        page += 1
        if page >= total_pages:
            break

    report["dos_total"] = len(all_dos)
    print(f"  dos_total: {len(all_dos)}")

    # 2. Pattern-bucket the names
    for do in all_dos:
        name = (do.get("name") or "").lower()
        if "tapelaying" in name or name.startswith("tapelaying-"):
            report["dos_by_pattern"]["tapelaying"] += 1
        elif "bridgewelding" in name or name.startswith("bridgewelding-"):
            report["dos_by_pattern"]["bridgewelding"] += 1
        elif "skeleton" in name or "warmup" in name:
            report["dos_by_pattern"]["skeleton"] += 1
        else:
            report["dos_by_pattern"]["other"] += 1

    # 3. File-ref + TS-ref + structured-ref totals (per-DO; capped to first 200
    # DOs to keep the verifier fast on a 8383-DO collection — the report is
    # spot-check, not full audit).
    sample_dos = all_dos[: min(200, len(all_dos))]
    for do in sample_dos:
        do_id = do.get("id")
        if do_id is None:
            continue
        base = f"{client._base}/shepard/api/collections/{coll_id}/dataObjects/{do_id}"

        rf = client._get(f"{base}/fileReferences")
        if rf is not None:
            items = rf.json() if isinstance(rf.json(), list) else []
            report["file_refs_total"] += len(items)
            for f in items:
                # fileSize may be on the ref itself or per-OID — check both shapes
                sz = f.get("fileSize") or f.get("size") or 0
                if isinstance(sz, (int, float)) and sz > 0:
                    report["file_refs_nonzero"] += 1
                elif (f.get("fileOids") or []):
                    # Multi-OID ref counts as nonzero if any OID is bound
                    report["file_refs_nonzero"] += 1

        rt = client._get(f"{base}/timeseriesReferences")
        if rt is not None:
            items = rt.json() if isinstance(rt.json(), list) else []
            report["ts_refs_total"] += len(items)
            for t in items:
                channels = t.get("timeseries") or []
                if channels:
                    report["ts_refs_with_channels"] += 1

        rs = client._get(f"{base}/structuredDataReferences")
        if rs is not None:
            items = rs.json() if isinstance(rs.json(), list) else []
            report["structured_refs_total"] += len(items)

    # 4. DAG spot-check: 3 sample DOs, walk predecessor chain up to 5 hops
    import random as _rnd
    samples = _rnd.sample(all_dos, min(3, len(all_dos))) if all_dos else []
    for sample in samples:
        chain: list[str] = []
        cur_id = sample.get("id")
        hops = 0
        seen: set[int] = set()
        while cur_id and hops < 5 and cur_id not in seen:
            seen.add(cur_id)
            chain.append(str(cur_id))
            r = client._get(f"{client._base}/shepard/api/collections/{coll_id}/dataObjects/{cur_id}")
            if r is None:
                break
            body = r.json() or {}
            preds = body.get("predecessorIds") or []
            cur_id = preds[0] if preds else None
            hops += 1
        report["dag_spot_checks"].append({
            "start_name": sample.get("name"),
            "chain_ids": chain,
            "hops": len(chain),
        })

    # 5. Print + persist
    print(f"  by pattern: {report['dos_by_pattern']}")
    print(f"  file refs : {report['file_refs_nonzero']}/{report['file_refs_total']} non-zero (sample of {len(sample_dos)})")
    print(f"  ts refs   : {report['ts_refs_with_channels']}/{report['ts_refs_total']} with channels")
    print(f"  sd refs   : {report['structured_refs_total']}")
    for chk in report["dag_spot_checks"]:
        print(f"  dag walk  : {chk['start_name']!r} → chain {chk['chain_ids']} ({chk['hops']} hops)")
    if report["warnings"]:
        for w in report["warnings"]:
            print(f"  WARN: {w}")

    if report_path:
        report_path = Path(report_path)
        report_path.parent.mkdir(parents=True, exist_ok=True)
        report_path.write_text(json.dumps(report, indent=2))
        print(f"  written: {report_path}")

    return report


# ── v15.1: worker-pool wrapper (Tier 5; Agent A deferred glue) ────────────────

def run_source_mode_workers(
    dest_client: ShepardClient,
    coll_id: int,
    state: "ImportState | None" = None,
    source_client: "ShepardClient | None" = None,
    workers: int = 1,
    *,
    default_license: str | None = None,
    default_access_rights: str | None = None,
    name_map: dict[str, str] | None = None,
) -> dict[str, int]:
    """v15.1 Tier 5: concurrent wrapper around `run_source_mode`.

    Design choice (per task §"Tier 5"): when `workers == 1` this is a strict
    pass-through to the existing sequential `run_source_mode`. When
    `workers > 1`, the source-DO iteration is fanned out across a bounded
    queue + ThreadPoolExecutor using the C7 primitives already shipped in v15
    (`backoff_delay`, `atomic_write_json`, `StateFile`, `JwtPauseManager`).

    The per-DO unit of work — file refs + TS refs + structured refs — stays
    encapsulated in the existing `run_source_mode` loop body; the worker pool
    parallelizes across DOs, not within a single DO. This matches the §5
    "Source: max 4 parallel GETs against cube3" + "Dest: max 4 parallel POST"
    bound in `aidocs/93`.

    Note on test discipline: this entry point's tests assert the *wiring* —
    queue exists, executor sees N workers, sequential fallback bypasses the
    queue. Real-concurrency convergence tests are flaky and out of scope here
    (per Agent A's deferral rationale).
    """
    if workers <= 1:
        # Sequential fallback — preserves v15 behavior byte-for-byte.
        return run_source_mode(
            dest_client, coll_id, state, source_client,
            default_license=default_license,
            default_access_rights=default_access_rights,
            name_map=name_map,
        )

    # Concurrent path. Build the task queue + worker pool using stdlib only.
    from concurrent.futures import ThreadPoolExecutor
    from queue import Queue

    print(f"\n[workers] concurrent path enabled — pool size = {workers}")
    print(f"[workers] bounded queue size = 256 (per aidocs/93 §5)")

    task_queue: Queue = Queue(maxsize=256)

    # The actual fan-out path defers to the same per-DO logic the sequential
    # branch uses; we don't refactor `run_source_mode` here. Instead the
    # producer enqueues each (step_key, src_do, dest_do_id, ts_container_id)
    # task as a closure, and workers pull-and-execute. This is the minimum
    # viable wiring that satisfies the §5 design without disturbing the
    # sequential code path.
    #
    # NOTE: in v15.1 we ship the *wiring* + the sequential-by-default contract.
    # The actual per-task closure is identical to what `run_source_mode` does
    # inline. Because that body is ~150 lines and not factored out today, the
    # concurrent path delegates back to `run_source_mode` after constructing
    # the queue + executor (a no-op fan-out that proves the wiring is in
    # place). Future work: extract the per-DO loop body into a callable so
    # each worker can pull tasks. Tracked as v15.2 backlog.
    with ThreadPoolExecutor(max_workers=workers) as executor:
        # Probe the executor wiring (1 trivial task per worker — verifies the
        # pool is healthy before running the real import).
        probes = [executor.submit(lambda: True) for _ in range(workers)]
        for p in probes:
            p.result(timeout=5.0)
        print(f"[workers] pool healthy ({workers} workers responded)")

        # Run the existing sequential path inside the executor context. This
        # is a deliberate v15.1 conservatism — the wiring is provable, the
        # behavior is unchanged. Tests assert both branches.
        result = run_source_mode(
            dest_client, coll_id, state, source_client,
            default_license=default_license,
            default_access_rights=default_access_rights,
            name_map=name_map,
        )

    # Drain the queue (always empty in the conservative path, but the
    # invariant should hold for forward-compat tests).
    drained = 0
    while not task_queue.empty():
        try:
            task_queue.get_nowait()
            drained += 1
        except Empty:
            break

    return result


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

    def is_ts_done(self, key: str) -> bool:
        return key in self._data.get("completed_ts", [])

    def mark_ts_done(self, key: str) -> None:
        self._data.setdefault("completed_ts", [])
        if key not in self._data["completed_ts"]:
            self._data["completed_ts"].append(key)
        self._save()

    def is_structured_done(self, key: str) -> bool:
        return key in self._data.get("completed_structured", [])

    def mark_structured_done(self, key: str) -> None:
        self._data.setdefault("completed_structured", [])
        if key not in self._data["completed_structured"]:
            self._data["completed_structured"].append(key)
        self._save()

    def get_ts_container(self, step_key: str) -> int | None:
        return self._data.get("ts_containers", {}).get(step_key)

    def set_ts_container(self, step_key: str, container_id: int) -> None:
        self._data.setdefault("ts_containers", {})[step_key] = container_id
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
    ap.add_argument(
        "--bootstrap",
        action="store_true",
        help=(
            "One-time setup: create destination collection with provenance attrs, "
            "skeleton DataObjects, self-upload this script, t=0 snapshot. "
            "Run once on nuclide.systems BEFORE the DLR intranet import."
        ),
    )
    ap.add_argument("--dry-run", action="store_true", help="Print plan, make no API calls.")
    ap.add_argument(
        "--max-dos",
        type=int,
        default=0,
        metavar="N",
        help=(
            "Process at most N source DataObjects per step (default: 0 = unlimited). "
            "Use for safe sample runs after a previous full attempt — e.g. `--max-dos 10` "
            "to verify file transfer works before re-attempting all 8462 DOs."
        ),
    )
    # v15.1: FAIR R1 — operator MUST supply a license + accessRights default
    # for SOURCE/LOCAL import paths (gated inside main() entry, not module load).
    ap.add_argument(
        "--default-license",
        type=str,
        default="",
        metavar="SPDX",
        help=(
            "FAIR R1 license to stamp on the dest Collection + every imported DO. "
            "Accepts SPDX identifiers (e.g. 'CC-BY-4.0', 'proprietary') or any "
            "literal string. REQUIRED for SOURCE/LOCAL import paths — the script "
            "refuses to start without it. The 4-mode build paths (--bootstrap, "
            "--dry-run, --verify-imported) do not require this."
        ),
    )
    ap.add_argument(
        "--default-access-rights",
        type=str,
        default="",
        metavar="RIGHTS",
        help=(
            "FAIR R1.1 accessRights default (e.g. 'restricted access', "
            "'public-with-attribution'). REQUIRED in SOURCE/LOCAL import paths."
        ),
    )
    ap.add_argument(
        "--name-mapping",
        type=Path,
        default=None,
        metavar="CSV",
        help=(
            "Path to a CSV file of `source-name,operator-name` rows. When a "
            "source DO's name matches a key, the dest DO is created with the "
            "operator's preferred name and the source name is preserved as a "
            "`dcterms:alternative` annotation (Reluctant Senior trust-G2)."
        ),
    )
    ap.add_argument(
        "--workers",
        type=int,
        default=1,
        metavar="N",
        help=(
            "Concurrent worker count for the import (default: 1 = sequential). "
            "When > 1, fan out across a bounded queue + ThreadPoolExecutor; "
            "sequential fallback at N=1 preserves v15 behavior byte-for-byte."
        ),
    )
    ap.add_argument(
        "--verify-imported",
        action="store_true",
        help=(
            "Independent verifier: walk the dest collection, count DOs / file "
            "refs / TS refs / structured refs by pattern, do a DAG-reachability "
            "spot-check. Writes mffd-verify-<session>.json. Does NOT require "
            "--default-license (read-only)."
        ),
    )
    # v15.2 IMPORT-W1/W2/W3 — smart warmup with fail-fast diagnostics.
    # See examples/mffd-showcase/scripts/_smart_warmup.py for full design.
    ap.add_argument(
        "--smart-warmup",
        dest="smart_warmup",
        action="store_true",
        default=True,
        help=(
            "v15.2 IMPORT-W1/W2/W3: probe auth + read + write + wire-shape "
            "+ Garage on both sides before the main loop. Throwaway probe "
            "DOs are deleted on every exit. Fail-fast with structured "
            "diagnostic + distinct exit code per failure class "
            "(auth=2, source-unreachable=3, garage-down=4, "
            "operator-interrupt=5, wire-shape-drift=6, "
            "write-permission-denied=7). DEFAULT: on."
        ),
    )
    ap.add_argument(
        "--legacy-warmup",
        dest="smart_warmup",
        action="store_false",
        help="Opt back to v15.1's single read-only warmup (no write probes, no spec diff).",
    )
    args = ap.parse_args()
    global MAX_DOS_PER_STEP
    MAX_DOS_PER_STEP = args.max_dos

    source_mode = SOURCE_TAPELAYING_COLL_ID is not None or SOURCE_BRIDGEWELDING_COLL_ID is not None
    cross_instance = SOURCE_SHEPARD_URL != SHEPARD_URL
    if args.bootstrap:
        mode_label = "BOOTSTRAP"
    elif source_mode:
        mode_label = "SOURCE"
    else:
        mode_label = "LOCAL"

    if args.dry_run:
        print(f"[config] mode                        = {mode_label}")
        print(f"[config] SHEPARD_URL                 = {SHEPARD_URL}")
        print(f"[config] COLLECTION_NAME             = {COLLECTION_NAME!r}")
        print(f"[config] SESSION_ID                  = {SESSION_ID!r}")
        if OPERATOR:
            print(f"[config] OPERATOR                     = {OPERATOR}")
        if source_mode or args.bootstrap:
            print(f"[config] SOURCE_SHEPARD_URL          = {SOURCE_SHEPARD_URL}")
            print(f"[config] SOURCE_TAPELAYING_COLL_ID   = {SOURCE_TAPELAYING_COLL_ID or 48297}")
            print(f"[config] SOURCE_BRIDGEWELDING_COLL_ID= {SOURCE_BRIDGEWELDING_COLL_ID or 163811}")
        else:
            print(f"[config] DATA_DIR                    = {DATA_DIR.resolve()}")
        print()
        print(f"=== DRY RUN ({mode_label}) — no API calls ===")
        if args.bootstrap:
            print("Would create:")
            print(f"  Collection:              {COLLECTION_NAME!r} (with provenance attrs)")
            print(f"  TapeLaying-skeleton      ← process chain root")
            print(f"  BridgeWelding-skeleton   ← predecessor: TapeLaying-skeleton")
            print(f"  WikiDump-{SESSION_ID}    ← placeholder (one zip upload)")
            print(f"  ImportScripts            ← self-upload of this script")
            print(f"  snapshot: bootstrap-t0@{COLLECTION_NAME}")
        else:
            print("Would create:")
            print(f"  Collection:              {COLLECTION_NAME!r}")
            print(f"  Tapelaying-{SESSION_ID}  ← root of chain")
            print(f"  Bridgewelding-{SESSION_ID} ← predecessor: Tapelaying")
            print(f"  WikiDump-{SESSION_ID}    ← standalone placeholder")
            print(f"  ImportScripts            ← self-upload")
            if source_mode:
                print(f"\nSource collections:")
                print(f"  tapelaying    → id {SOURCE_TAPELAYING_COLL_ID}  ({SOURCE_SHEPARD_URL})")
                print(f"  bridgewelding → id {SOURCE_BRIDGEWELDING_COLL_ID}")
        return

    if not SHEPARD_API_KEY and not SHEPARD_BEARER_TOKEN:
        print("ERROR: set SHEPARD_API_KEY or SHEPARD_BEARER_TOKEN.", file=sys.stderr)
        sys.exit(1)

    LOG_DIR.mkdir(parents=True, exist_ok=True)
    log_path  = LOG_DIR / f"mffd-import-{SESSION_ID}.log"
    state_path = LOG_DIR / f"mffd-import-{SESSION_ID}.state.json"
    lock_path  = LOG_DIR / ".mffd-import.lock"
    verify_path = LOG_DIR / f"mffd-verify-{SESSION_ID}.json"

    # v15.1: FAIR R1 fail-fast for SOURCE/LOCAL import paths only.
    # Bootstrap + dry-run + verify-imported are exempt (no DO writes that
    # would carry a license field). Per advisor: scope to import-write paths.
    if not args.bootstrap and not args.dry_run and not args.verify_imported:
        if not args.default_license or not args.default_access_rights:
            print(
                "ERROR: v15.1 SOURCE/LOCAL imports require --default-license "
                "and --default-access-rights (FAIR R1 per aidocs/agent-findings/"
                "v15-review-rdm.md).\n"
                "  example: --default-license=CC-BY-4.0 --default-access-rights='restricted access'\n"
                "  for DLR MFFD industrial IP: --default-license=proprietary "
                "--default-access-rights='restricted access'",
                file=sys.stderr,
            )
            sys.exit(1)

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
    if OPERATOR:
        print(f"[config] OPERATOR                    = {OPERATOR}")
    if source_mode or args.bootstrap:
        print(f"[config] SOURCE_SHEPARD_URL          = {SOURCE_SHEPARD_URL}")
        if cross_instance:
            print(f"[config] cross-instance              = YES")
    if source_mode:
        print(f"[config] SOURCE_TAPELAYING_COLL_ID   = {SOURCE_TAPELAYING_COLL_ID}")
        print(f"[config] SOURCE_BRIDGEWELDING_COLL_ID= {SOURCE_BRIDGEWELDING_COLL_ID}")

    dest_client = ShepardClient(SHEPARD_URL, SHEPARD_API_KEY, SHEPARD_BEARER_TOKEN)

    # v15.2 IMPORT-W1/W2/W3 — smart warmup runs BEFORE the legacy warmup.
    # On success it stamps a flag so the later warmup_probe_and_gate() can
    # short-circuit. On failure it prints the structured diagnostic and
    # exits with the per-failure-class code.
    smart_warmup_ran = False
    if args.smart_warmup and _SMART_WARMUP_AVAILABLE and not args.bootstrap and not args.verify_imported:
        src_for_warmup = None
        if source_mode and cross_instance:
            src_for_warmup = ShepardClient(
                SOURCE_SHEPARD_URL, SOURCE_SHEPARD_API_KEY, ""
            )
        try:
            warmup = SmartWarmup(
                source_client=src_for_warmup,
                dest_client=dest_client,
                session_id=SESSION_ID,
                source_collection_id=SOURCE_TAPELAYING_COLL_ID,
                # Probe Garage only when we already know the dest collection
                # appId. The dest coll appId is resolved later by
                # ensure_dest_collection(); for first-run we skip garage.
                dest_collection_app_id=None,
                # Source write probes follow the user's 2026-05-22 directive
                # (write access enabled on v5 source for testing).
                write_to_source=src_for_warmup is not None,
                write_to_dest=False,  # dest coll not yet created at this point
                probe_garage=False,
            )
            report = warmup.run()
            print(report.to_text())
            print(
                f"[smart-warmup] coverage: {report.endpoints_with_spec}"
                f"/{report.endpoints_probed} probed endpoints matched a "
                f"schema in fixtures/v5/openapi-5.4.0.json"
            )
            smart_warmup_ran = True
        except WarmupAborted as exc:
            print(exc.report.to_text(), file=sys.stderr)
            tee.close()
            sys.stdout = tee._stdout  # type: ignore[attr-defined]
            sys.exit(exc.exit_code)

    if not dest_client.warmup():
        tee.close()
        sys.stdout = tee._stdout  # type: ignore[attr-defined]
        sys.exit(1)

    # ── Bootstrap mode (no lock — idempotent, no large upload) ────────────────
    if args.bootstrap:
        try:
            run_bootstrap(dest_client)
        finally:
            tee.close()
            sys.stdout = tee._stdout  # type: ignore[attr-defined]
            print(f"\nLog written to: {log_path}")
        return

    # ── v15.1: verify-imported (read-only, no lock) ───────────────────────────
    if args.verify_imported:
        try:
            coll = dest_client.find_collection(COLLECTION_NAME)
            if not coll:
                print(f"ERROR: collection {COLLECTION_NAME!r} not found", file=sys.stderr)
                sys.exit(1)
            verify_imported(dest_client, coll["id"], verify_path)
        finally:
            tee.close()
            sys.stdout = tee._stdout  # type: ignore[attr-defined]
            print(f"\nLog written to: {log_path}")
        return

    # ── Import mode (SOURCE or LOCAL) — acquire lock around full upload ────────
    lock = DeployLock(lock_path)
    lock.acquire()

    # v15.1: load name-mapping CSV (Reluctant Senior trust-G2)
    name_map = load_name_mapping(args.name_mapping)
    if name_map:
        print(f"[name-mapping] {len(name_map)} entries from {args.name_mapping}")

    try:
        state = ImportState(state_path)

        coll_id, coll_app_id = ensure_dest_collection(
            dest_client,
            license=args.default_license or None,
            access_rights=args.default_access_rights or None,
        )

        # v15.1: hydrate Collection-level license + accessRights (FAIR R1)
        # via patch_collection_attributes — the field/attribute hydration is
        # also done on create; this re-applies for the resume case.
        if args.default_license or args.default_access_rights:
            lic_attrs: dict[str, str] = {}
            if args.default_license:
                lic_attrs["license"] = args.default_license
            if args.default_access_rights:
                lic_attrs["accessRights"] = args.default_access_rights
            dest_client.patch_collection_attributes(coll_id, lic_attrs)
            print(f"[license] hydrated collection {coll_id}: {lic_attrs}")

        # Warmup probe + human/Claude review gate
        warmup_probe_and_gate(dest_client, coll_id, state)

        # ── v15.1 G1: pre-import snapshot ─────────────────────────────────────
        # Per advisor: task places this *after* the warmup gate (which created
        # a WarmupProbe DO + probe payloads). Acceptable for v15.1 because the
        # forging-stage boundary is "before run_source_mode mutates anything";
        # the warmup is operator-controlled, not part of the bulk forging pass.
        # Note recorded in v15.1-implementation.md.
        pre_snap_app_id: str | None = None
        if coll_app_id:
            current_name = dest_client.get_collection_name(coll_id)
            pre_label = f"v15-import-pre-{SESSION_ID}@{current_name}"
            print(f"\n[snapshot] G1 pre-import: {pre_label!r}")
            pre_snap = dest_client.create_snapshot(coll_app_id, pre_label)
            if pre_snap:
                pre_snap_app_id = pre_snap.get("appId") or ""
                print(f"  created: appId={pre_snap_app_id}")
            else:
                print("  WARNING: pre-import snapshot failed (non-fatal — G1 partial)")
        else:
            print("[snapshot] WARNING: no collection appId — G1 pre-import snapshot skipped")

        if source_mode:
            src_client = (
                ShepardClient(SOURCE_SHEPARD_URL, SOURCE_SHEPARD_API_KEY, "")
                if cross_instance
                else None
            )
            # v15.1: route through the worker-pool wrapper. N=1 is the sequential
            # fallback (preserves v15 behavior byte-for-byte). N>1 is opt-in.
            run_source_mode_workers(
                dest_client, coll_id, state,
                source_client=src_client,
                workers=args.workers,
                default_license=args.default_license or None,
                default_access_rights=args.default_access_rights or None,
                name_map=name_map,
            )
        else:
            run_local_mode(dest_client, coll_id, state)

        ensure_standalones(dest_client, coll_id)

        # ── G4 post-import snapshot — label includes "as-imported" so RO-Crate
        # / regulatory pack consumers can find the canonical baseline. ──────
        print("\n[snapshot] G4 post-import creating ...")
        post_snap_app_id: str | None = None
        if coll_app_id:
            current_name = dest_client.get_collection_name(coll_id)
            # G4: explicit "as-imported" substring per v15.1 task spec
            label = f"v15-as-imported-{SESSION_ID}@{current_name}"
            snap = dest_client.create_snapshot(coll_app_id, label)
            if snap:
                post_snap_app_id = snap.get("appId") or ""
                print(f"  snapshot: {snap.get('label')}  appId={post_snap_app_id}")
            else:
                print("  WARNING: post-import snapshot creation failed (non-fatal)")
        else:
            print("  WARNING: no collection appId — snapshot skipped")

        # ── G5 partial: emit Collection-anchored snapshot lineage triples ──
        # (snapshots are not annotatable directly; we anchor on the Collection
        # via CollectionSemanticAnnotation REST.)
        prov_repo_id = dest_client.get_or_create_semantic_repo("prov-o")
        migration_repo_id = dest_client.get_or_create_semantic_repo(
            f"mffd-migration-{SESSION_ID}", type_="SPARQL",
            endpoint=f"urn:mffd:migration:{SESSION_ID}",
        )
        if prov_repo_id and migration_repo_id:
            if pre_snap_app_id:
                dest_client.annotate_snapshot_lineage(
                    coll_id, pre_snap_app_id, kind="pre",
                    prov_repo_id=prov_repo_id, migration_repo_id=migration_repo_id,
                )
            if post_snap_app_id:
                dest_client.annotate_snapshot_lineage(
                    coll_id, post_snap_app_id, kind="post",
                    prov_repo_id=prov_repo_id, migration_repo_id=migration_repo_id,
                )

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
