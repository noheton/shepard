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
         H (csv_format=ROW; COLUMN cannot be re-imported + interpolates),
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
import hashlib
import io
import json as _json
import os
import socket
import sys
import tempfile
import threading
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

# ── Version + observability config (v15.4 IMPORT-SU1/T1/CP1) ──────────────────

IMPORT_SCRIPT_VERSION = "15.15"

# v15.11 IMPORT-DIAG — structured diagnostic instrumentation. DiagSink emits
# one JSON line per event to stderr + the existing log file, classifies errors
# into auto-generated hints, and stamps each event with a correlation id so a
# future operator reading 200 lines of tmux scrollback can diagnose the failure
# class without code access. See `aidocs/integrations/95 §Pattern 19`.
# Mode: quiet|normal|verbose|http-trace (default normal).
DIAG_MODE: str = os.environ.get("MFFD_DIAG_MODE", "normal")

# Self-update mechanism (IMPORT-SU1): the running script polls a JSON manifest
# in dest Shepard. On newer version → sha256-verified download → checkpoint
# save → os.execv replace. One starter curl, no more.
SELFUPDATE_ENABLED = os.environ.get("MFFD_SELFUPDATE", "1") != "0"
MANIFEST_CONTAINER_ID = int(os.environ.get("MFFD_MANIFEST_CONTAINER_ID", "473932"))
MANIFEST_NAME_PREFIX = "mffd-import-manifest-v"
HEARTBEAT_S = int(os.environ.get("MFFD_HEARTBEAT_S", "60"))

# Telemetry (IMPORT-T1): metrics → TS container, events → SD container.
# Dest container IDs are pinned because the script runs from anywhere; the
# starter curl baker (Flo) creates these once, the IDs live in env defaults.
TELEMETRY_TS_CONTAINER_ID = int(os.environ.get("MFFD_TELEMETRY_TS", "593750"))
RUNLOG_SD_CONTAINER_ID = int(os.environ.get("MFFD_RUNLOG_SD", "593753"))

# Checkpoint (IMPORT-CP1): single JSON file, atomic write. Restart resumes
# from here — both after self-update os.execv and after a manual SIGTERM.
CHECKPOINT_PATH = Path(os.environ.get(
    "MFFD_CHECKPOINT",
    str(Path.home() / ".mffd-import-state.json"),
))

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

# v15.9 MFFD-IMPORT-USER-CAPTURE — populated once at startup by main()
# after a successful source-side `resolve_self()`. Read by attribute
# builders (`base_attrs` in run_source_mode) so every dest DO carries
# source_user_* attrs. Stays None when the source-user lookup fails
# (graceful degradation — script continues, just without enrichment).
SOURCE_USER_INFO: dict | None = None

# v15.11 IMPORT-DIAG — module-level DiagSink set by main() right after
# Telemetry creation. Helper sites (worker pool, per-DO body, error paths)
# read this without per-call threading. Stays None until main() runs, so
# import-only test contexts see a no-op `_diag_emit` helper below.
_DIAG: Any | None = None


def _diag_emit(kind: str, payload: dict | None = None,
               corr: str | None = None) -> None:
    """Module-level helper so call sites don't have to plumb DiagSink through
    closures. No-op when DiagSink isn't installed yet (e.g. before main()
    initialises it, or in test contexts that load the module without running)."""
    if _DIAG is not None:
        try:
            _DIAG.emit(kind, payload, corr=corr)
        except Exception:
            # Diagnostic must never break ingestion.
            pass


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
    """A DataObject discovered in a source collection.

    v15.8 IMPORT-PERF2 — lazy enrichment. `file_refs / ts_refs / structured_refs`
    default to None and are populated on demand by the per-DO loop body via
    `ShepardClient._load_file_refs / _load_ts_refs / _load_structured_refs`.
    The loop checks state-skip first, so an already-fully-done DO costs ZERO
    cube3 round-trips on resume (vs. 3 in v15.7).

    Callers that need the refs unconditionally (legacy code) can still write
    `src_do.file_refs = client._load_file_refs(coll, src_do.do_id)` etc., or
    use the helper accessors on the client. The fields stay assignable for
    back-compat with non-import callers (tests, mffd-dropbox-import, ...).
    """
    do_id: int
    name: str
    description: str
    attributes: dict[str, str]
    file_refs: list[FileRef] | None = None
    ts_refs: list[TsRef] | None = None
    structured_refs: list[StructuredRef] | None = None
    created: str = ""
    modified: str = ""
    # v15.8 IMPORT-PERF2 — source coll_id captured at yield-time so lazy
    # loaders can fetch refs without the caller threading state.
    _src_coll_id: int = 0


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

    # ── v15.9 MFFD-IMPORT-USER-CAPTURE — source-side user identity ────────────
    #
    # Resolves the User the script's API key belongs to on this client's
    # `_base` instance. JWT `sub` is the username candidate (cube3:
    # literal "kreb_fl"; nuclide: a UUID). Strict v5.4.0 upstream exposes
    # GET /shepard/api/users/{username} — that's the canonical lookup.
    # The dest fork additionally exposes GET /shepard/api/users (no path
    # arg) returning the caller's User; we use this as a "/users/me"
    # fallback when the JWT-sub strategy fails.
    #
    # Returned dict shape (subset — upstream v5 schema, plus fork extras):
    #   {username, firstName?, lastName?, email?, subscriptionIds[],
    #    appId?, orcid?, effectiveDisplayName?}
    # Returns None on any failure — the script MUST remain functional
    # when source user info is unfetchable (e.g. JWT lacks read perms,
    # source has stripped /users endpoint, network blip). The dest-side
    # mirror + ProvenanceCaptureFilter consumption are best-effort
    # enrichment, never a blocker.

    @staticmethod
    def _decode_jwt_sub(jwt: str) -> str | None:
        """Pure helper: extract the `sub` claim from a JWT body.

        Returns None on any decode error (malformed JWT, missing sub,
        non-string sub). Pure — no network, no logging.
        """
        if not jwt or not isinstance(jwt, str):
            return None
        parts = jwt.split(".")
        if len(parts) < 2:
            return None
        body = parts[1]
        # base64url padding (JWT bodies omit '=' per RFC 7515)
        padding = "=" * (-len(body) % 4)
        try:
            import base64 as _b64
            decoded = _b64.urlsafe_b64decode(body + padding)
            import json as _json
            claims = _json.loads(decoded.decode("utf-8"))
        except Exception:
            return None
        sub = claims.get("sub")
        if not sub or not isinstance(sub, str):
            return None
        return sub

    @staticmethod
    def _ascii_safe_header(value: str) -> str:
        """Coerce a header value to ASCII (requests/urllib3 encode as latin-1).

        DLR display names with umlauts ('Müller') would otherwise raise
        UnicodeEncodeError on every write. Non-ASCII is replaced with '?'.
        Empty values become empty strings (caller decides whether to send).
        """
        if not value:
            return ""
        return value.encode("ascii", "replace").decode("ascii")

    def resolve_self(self, api_key_for_jwt_decode: str = "") -> dict | None:
        """Look up the User whose API key this client carries.

        Strategy (graceful degradation):
          1. Decode JWT `sub` from the api_key_for_jwt_decode (or
             self._s.headers["X-API-KEY"] if no arg given), then
             GET /shepard/api/users/{sub}. This is the canonical v5
             surface and works against strict cube3 upstream.
          2. If (1) returns 404 or the sub is empty, try
             GET /shepard/api/users (no path arg) — on the nuclide
             fork this returns the caller's User (current user-self).
             Strict upstream returns a LIST here; we detect that
             (response is `[...]` not `{...}`) and bail.
          3. On any other failure (403 = no read perm, network, etc.),
             return None. The caller must handle None — never crash.

        Always returns the decoded JSON dict on success, or None.
        """
        jwt = api_key_for_jwt_decode or self._s.headers.get("X-API-KEY", "")
        # urllib3 sends headers as bytes; the Session-stored value is str.
        # Strategy 1: JWT-sub lookup.
        sub = self._decode_jwt_sub(jwt) if isinstance(jwt, str) else None
        if sub:
            try:
                r = self._request_with_retry(
                    "GET", f"{self._base}/shepard/api/users/{sub}",
                    timeout=15, deadline_s=30.0,
                )
                if r is not None and r.ok:
                    try:
                        body = r.json()
                        if isinstance(body, dict) and body.get("username"):
                            return body
                    except Exception:
                        pass
                # 404 = JWT sub doesn't match a username (e.g. SSO-derived);
                # fall through to /users (no path) fork-compat fallback.
            except Exception:
                pass
        # Strategy 2: /users (no path arg) — fork "self" endpoint.
        try:
            r = self._request_with_retry(
                "GET", f"{self._base}/shepard/api/users",
                timeout=15, deadline_s=30.0,
            )
            if r is not None and r.ok:
                try:
                    body = r.json()
                    # Fork shape: single dict. Upstream shape: list. Bail on list.
                    if isinstance(body, dict) and body.get("username"):
                        return body
                except Exception:
                    pass
        except Exception:
            pass
        return None

    def apply_source_user_headers(self, source_user: dict | None,
                                  source_instance_url: str = "") -> bool:
        """Inject X-Source-User-* headers into the Session default headers.

        Once set, every subsequent write (POST/PUT/PATCH) through self._s
        carries these headers — ProvenanceCaptureFilter on the dest reads
        them automatically (no per-call-site threading needed).

        Headers added (only when the source field is non-empty):
          - X-Source-User-Username       — e.g. "kreb_fl"
          - X-Source-User-DisplayName    — "First Last" or username fallback
          - X-Source-User-Email          — RFC 5322 mail address
          - X-Source-User-Instance       — origin shepard URL

        All values pass through _ascii_safe_header so requests/urllib3
        latin-1 encoding never raises on umlaut names.

        Returns True when at least one header was set; False otherwise.
        Idempotent — safe to call multiple times (later calls overwrite).
        """
        if not source_user:
            return False
        username = source_user.get("username") or ""
        first = source_user.get("firstName") or ""
        last = source_user.get("lastName") or ""
        email = source_user.get("email") or ""
        display = source_user.get("effectiveDisplayName") or ""
        if not display:
            display = f"{first} {last}".strip() or username

        applied = False
        if username:
            self._s.headers["X-Source-User-Username"] = self._ascii_safe_header(username)
            applied = True
        if display:
            self._s.headers["X-Source-User-DisplayName"] = self._ascii_safe_header(display)
            applied = True
        if email:
            self._s.headers["X-Source-User-Email"] = self._ascii_safe_header(email)
            applied = True
        if source_instance_url:
            self._s.headers["X-Source-User-Instance"] = self._ascii_safe_header(source_instance_url)
            applied = True
        return applied

    # ── Source collection traversal ───────────────────────────────────────────

    def iter_data_objects(self, coll_id: int) -> Iterator[SourceDO]:
        """Paginate through ALL DataObjects in a collection and yield STUBS.

        v15.8 IMPORT-PERF2 — file/TS/SD reference lists are NOT fetched here.
        The per-DO loop body fetches them lazily via `_load_*_refs` AFTER
        the state-resume skip-check has had a chance to decline the work.
        On a fully-resumed (no work remaining) collection this saves
        3 × N cube3 round-trips (per `aidocs/agent-findings/
        mffd-import-slowness-diagnose-2026-05-23.md §5 hypothesis #2`).
        """
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
                yield SourceDO(
                    do_id=do_id,
                    name=item.get("name", f"DO-{do_id}"),
                    description=item.get("description") or "",
                    attributes={k: str(v) for k, v in (item.get("attributes") or {}).items()},
                    # v15.8 PERF2: refs default to None — fetched on demand
                    # by the per-DO loop via _load_*_refs once state-skip
                    # is known to NOT shortcut all the work.
                    file_refs=None,
                    ts_refs=None,
                    structured_refs=None,
                    created=item.get("creationDate") or item.get("createdAt") or "",
                    modified=item.get("modificationDate") or item.get("updatedAt") or "",
                    _src_coll_id=coll_id,
                )
            total_pages = int(r.headers.get("X-Total-Pages", page + 1))
            page += 1
            if page >= total_pages:
                break

    # ── v15.8 IMPORT-PERF2 — lazy reference enrichment ────────────────────────
    #
    # These three accessors return the same lists `_fetch_*_refs` used to
    # eagerly fetch in `iter_data_objects`. They're called by the per-DO loop
    # body AFTER the state-skip check, so a fully-done DO costs ZERO cube3
    # round-trips on resume. The cached value lives on the SourceDO instance.

    def _load_file_refs(self, src_do: SourceDO) -> list[FileRef]:
        if src_do.file_refs is None:
            src_do.file_refs = self._fetch_file_refs(src_do._src_coll_id, src_do.do_id)
        return src_do.file_refs

    def _load_ts_refs(self, src_do: SourceDO) -> list[TsRef]:
        if src_do.ts_refs is None:
            src_do.ts_refs = self._fetch_ts_refs(src_do._src_coll_id, src_do.do_id)
        return src_do.ts_refs

    def _load_structured_refs(self, src_do: SourceDO) -> list[StructuredRef]:
        if src_do.structured_refs is None:
            src_do.structured_refs = self._fetch_structured_refs(src_do._src_coll_id, src_do.do_id)
        return src_do.structured_refs

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

        v15.3 IMPORT-TS-ROW — use ROW format (was COLUMN in v15.2 and earlier).
          1. ROW is the ONLY format the dest /timeseriesContainers/{id}/import
             endpoint accepts (CsvConverter.convertToTimeseriesWithData uses
             opencsv bean binding on CsvTimeseriesDataPoint with named columns:
             measurement, device, location, symbolicName, field, timestamp,
             value). COLUMN cannot be round-tripped — bug-H's claim was wrong.
          2. ROW is cheaper on the source: no native column-pivot / timeline
             alignment / value interpolation across channels — each row is one
             native data point read straight from the timeseries DB.
          3. ROW preserves each channel's exact native sampling (timestamps
             match the source rows verbatim). COLUMN aligns all channels onto
             a single shared timeline and may interpolate values to fill gaps,
             which silently rewrites the data.
          4. ROW is also the backend's default (CsvConverter L38: `format !=
             null ? format : CsvFormat.ROW`), so we're using what the upstream
             team meant as the canonical wire shape.

        Bug Q: empty body (zero point data) is treated as failure since v5.4.0
        TS export with at least one channel always returns at least the header.
        """
        url = (
            f"{self._base}/shepard/api/collections/{coll_id}"
            f"/dataObjects/{do_id}/timeseriesReferences/{ref_id}/export"
        )
        r = self._request_with_retry("GET", url, params={"csv_format": "ROW"}, timeout=300)
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
        # v15.3 IMPORT-SR1 — backend now requires non-blank `endpoint` on
        # all repo types (INTERNAL too), not just SPARQL. Provide a
        # deterministic URN default when caller didn't pass one.
        body: dict[str, Any] = {
            "name": name,
            "type": type_,
            "endpoint": endpoint or f"urn:shepard:repo:{name}",
        }
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
        # Bug E (v15.12): backend StructuredDataService:53 calls Document.parse() on the
        # payload string; MongoDB's BSON model rejects JSON arrays at root with
        # "readStartDocument...not when CurrentBSONType is ARRAY". Wrap lists in
        # {items: [...]} so the stored document always has an object root. Readers
        # that consume MFFD SD payloads must unwrap the single "items" key.
        inner = payload if isinstance(payload, dict) else {"items": payload, "_wrapped": "v15.12-bug-e"}
        body = {
            "structuredData": {"name": name},
            "payload": _json.dumps(inner),   # Bug D: required STRING (minLength:2)
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

    # v15.3 IMPORT-NS1 — script no longer auto-snapshots. Snapshots are a
    # decisive HUMAN action per project_snapshot_boundaries.md. The boundary
    # is real (bootstrap-t0 IS a larger transformation) but the decision +
    # the fire belong to the human.
    print(f"\n[snapshot] Reminder: bootstrap-t0 is a snapshot-worthy boundary.")
    print(f"  Snapshot is a HUMAN-decided action — script does not auto-fire.")
    print(f"  When ready: POST /v2/collections/{coll_app_id}/snapshots  body={{name:'bootstrap-t0@{coll_name}', label:'bootstrap-t0@{coll_name}'}}")

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

def _build_completeness_verdict(
    *,
    session_id: str,
    script_version: str,
    pre_totals: dict[str, int],
    grand_total: int,
    rows: list[dict],
    started_ts: float,
) -> dict:
    """v15.13 contained completeness check — pure-function aggregator.

    Compares each step's `expected_dos` (from the pre-flight count) against
    `processed_dos` (what the importer actually moved through its work loop).
    Per-kind sub-counts surface partial failures: 100% files-uploaded but
    100% SD-failed is exactly the v15.11/v15.9 BUG-E signature.

    Returns a verdict dict with `passing`, `summary`, `per_step`, `flags`.
    """
    now = time.time()
    per_step = []
    grand_processed = 0
    grand_file_failed = 0
    grand_ts_failed = 0
    grand_sd_failed = 0
    grand_file_uploaded = 0
    grand_ts_imported = 0
    grand_sd_imported = 0
    flags: list[str] = []

    for row in rows:
        step = row["step"]
        expected = int(row.get("expected_dos") or 0)
        processed = int(row.get("processed_dos") or 0)
        files = row.get("files") or {}
        tsd = row.get("timeseries") or {}
        sd = row.get("structured") or {}
        step_record = dict(row)
        step_record["counts_match"] = (expected == processed)
        step_record["any_kind_failed"] = (
            int(files.get("failed") or 0) > 0
            or int(tsd.get("failed") or 0) > 0
            or int(sd.get("failed") or 0) > 0
        )
        per_step.append(step_record)

        grand_processed += processed
        grand_file_failed += int(files.get("failed") or 0)
        grand_ts_failed += int(tsd.get("failed") or 0)
        grand_sd_failed += int(sd.get("failed") or 0)
        grand_file_uploaded += int(files.get("uploaded") or 0)
        grand_ts_imported += int(tsd.get("imported") or 0)
        grand_sd_imported += int(sd.get("imported") or 0)

        if not step_record["counts_match"]:
            flags.append(f"DO-COUNT-MISMATCH:{step}:{processed}/{expected}")
        if step_record["any_kind_failed"]:
            flags.append(f"PARTIAL-KIND-FAILURE:{step}")

    # BUG-E shape detector: many SD failures with file successes = the v15 pre-v15.12
    # signature. Flag explicitly so the operator knows whether redrive is needed.
    if grand_sd_failed > 0 and grand_sd_imported == 0 and grand_file_uploaded > 0:
        flags.append("BUG-E-SHAPE-SD-100PCT-LOSS")
    elif grand_sd_failed > 0 and grand_sd_imported > 0:
        flags.append("SD-PARTIAL-LOSS-NEEDS-REDRIVE")

    duration = max(1.0, now - started_ts)
    rate_dos_per_min = (grand_processed / duration) * 60 if grand_processed > 0 else 0.0

    passing = (
        grand_processed == grand_total
        and grand_file_failed == 0
        and grand_ts_failed == 0
        and grand_sd_failed == 0
    )

    return {
        "session": session_id,
        "script_version": script_version,
        "ts_unix": now,
        "duration_s": round(duration, 1),
        "rate_dos_per_min": round(rate_dos_per_min, 2),
        "pre_totals": pre_totals,
        "grand_total_expected": grand_total,
        "grand_total_processed": grand_processed,
        "totals": {
            "files_uploaded": grand_file_uploaded,
            "files_failed": grand_file_failed,
            "ts_imported": grand_ts_imported,
            "ts_failed": grand_ts_failed,
            "sd_imported": grand_sd_imported,
            "sd_failed": grand_sd_failed,
        },
        "per_step": per_step,
        "flags": flags,
        "passing": passing,
        "verdict": (
            "PASS" if passing
            else "FAIL-PARTIAL" if grand_processed > 0
            else "FAIL-EMPTY"
        ),
    }


def _print_completeness_banner(v: dict) -> None:
    """Human-readable end-of-migration banner."""
    print()
    print("  ┌─ MIGRATION COMPLETENESS ────────────────────────────────────────┐")
    print(f"  │  Verdict: {v['verdict']:<10}  Session: {v['session']:<32}│"[:69])
    print(f"  │  Duration: {v['duration_s']}s   Rate: {v['rate_dos_per_min']} DOs/min".ljust(67) + "│")
    print(f"  │  Expected: {v['grand_total_expected']} DOs   Processed: {v['grand_total_processed']} DOs".ljust(67) + "│")
    t = v["totals"]
    print(f"  │  Files: {t['files_uploaded']} ok / {t['files_failed']} failed".ljust(67) + "│")
    print(f"  │  TS:    {t['ts_imported']} ok / {t['ts_failed']} failed".ljust(67) + "│")
    print(f"  │  SD:    {t['sd_imported']} ok / {t['sd_failed']} failed".ljust(67) + "│")
    if v["flags"]:
        print("  │  Flags:".ljust(67) + "│")
        for f in v["flags"][:10]:
            print(f"  │    - {f}".ljust(67) + "│")
    print("  └─────────────────────────────────────────────────────────────────┘")
    print()


def _persist_completeness_artifact(verdict: dict, dest_client: "ShepardClient", coll_id: int) -> None:
    """Write JSON next to the importer + best-effort upload as a Shepard DO.

    The JSON file is the durable record. The Shepard DO is the lineage anchor —
    if upload fails (auth flap, network, dest down) the file on disk is still
    consultable. Both forms exist so the verifier can be re-run against the
    artifact later for audit.
    """
    import json as _json
    out_dir = Path(os.environ.get("MFFD_COMPLETENESS_OUT_DIR") or ".")
    try:
        out_dir.mkdir(parents=True, exist_ok=True)
    except OSError:
        out_dir = Path(".")
    name = f"mffd-completeness-{verdict['session']}.json"
    path = out_dir / name
    try:
        path.write_text(_json.dumps(verdict, indent=2, default=str), encoding="utf-8")
        print(f"  [completeness] artifact written → {path}")
    except OSError as e:
        print(f"  [completeness-warn] disk write failed: {e!r}")
        return

    # Best-effort upload as a MIGRATION-COMPLETENESS-<session> DataObject.
    try:
        body = {
            "name": f"MIGRATION-COMPLETENESS-{verdict['session']}",
            "attributes": {
                "kind": "migration-completeness-report",
                "session": verdict["session"],
                "script_version": verdict["script_version"],
                "verdict": verdict["verdict"],
                "passing": str(verdict["passing"]).lower(),
                "grand_total_expected": str(verdict["grand_total_expected"]),
                "grand_total_processed": str(verdict["grand_total_processed"]),
                "rate_dos_per_min": str(verdict["rate_dos_per_min"]),
                "flags_csv": ",".join(verdict["flags"]) if verdict["flags"] else "",
                "source_url": os.environ.get("SOURCE_SHEPARD_URL", ""),
                "dest_url": os.environ.get("SHEPARD_URL", ""),
                "tool": "mffd-import-v15.py (contained)",
            },
        }
        r = dest_client._session.post(
            f"{dest_client._base}/shepard/api/collections/{coll_id}/dataObjects",
            json=body, timeout=60,
        )
        if r.ok:
            try:
                anchor = r.json()
                print(f"  [completeness] dest anchor DO appId={anchor.get('appId')}")
            except (ValueError, AttributeError):
                pass
        else:
            print(f"  [completeness-warn] DO anchor create failed: {r.status_code}")
    except Exception as e:  # noqa: BLE001 — best-effort
        print(f"  [completeness-warn] anchor upload skipped: {e!r}")


def run_source_mode(
    dest_client: ShepardClient,
    coll_id: int,
    state: ImportState | None = None,
    source_client: ShepardClient | None = None,
    *,
    default_license: str | None = None,
    default_access_rights: str | None = None,
    name_map: dict[str, str] | None = None,
    workers: int = 1,
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

    # v15.15 BUG-F2 — PREDEFINED SD CONTAINERS.
    # The dest SD shape is fixed up-front: exactly one SD container per process
    # step, pre-created in Shepard by the operator before launch. IDs come in
    # via env. Fail-fast if either is missing — better to abort here than to
    # silently create thousands of orphan per-DO containers (the BUG-F pattern).
    _sd_env = {
        "tapelaying":    os.environ.get("MFFD_SD_CONTAINER_TAPELAYING"),
        "bridgewelding": os.environ.get("MFFD_SD_CONTAINER_BRIDGEWELDING"),
    }
    _missing = [k for k, v in _sd_env.items() if not v]
    if _missing:
        raise SystemExit(
            f"[v{IMPORT_SCRIPT_VERSION}] FATAL: predefined SD container env vars missing: {_missing}.\n"
            f"  Required: MFFD_SD_CONTAINER_TAPELAYING, MFFD_SD_CONTAINER_BRIDGEWELDING\n"
            f"  Pre-create the 2 SD containers in the dest Shepard and export their ids."
        )
    try:
        PREDEFINED_SD: dict[str, int] = {k: int(v) for k, v in _sd_env.items()}
    except ValueError as _e:
        raise SystemExit(
            f"[v{IMPORT_SCRIPT_VERSION}] FATAL: SD container env vars must be integer ids: {_sd_env}"
        ) from _e
    # Verify each predefined container is actually present in dest.
    for _step, _cid in PREDEFINED_SD.items():
        _verify_resp = dest_client._get(
            f"{dest_client._base}/shepard/api/structuredDataContainers/{_cid}"
        )
        if _verify_resp is None or getattr(_verify_resp, "status_code", 0) != 200:
            _status = getattr(_verify_resp, "status_code", "no-response") if _verify_resp else "no-response"
            raise SystemExit(
                f"[v{IMPORT_SCRIPT_VERSION}] FATAL: predefined SD container id={_cid} "
                f"for step={_step!r} not present in dest (HTTP {_status})."
            )
    print(f"  [v{IMPORT_SCRIPT_VERSION}] predefined SD containers:")
    for _step, _cid in PREDEFINED_SD.items():
        print(f"    {_step:<14} → container_id={_cid}  name=mffd-{_step}-sd")

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

    # v15.13 CONTAINED-COMPLETENESS: per-step rows collected as iterations finish,
    # used by the inline completeness pass at end of run_source_mode.
    # Schema per row: {step, src_coll_id, dest_do_id, dest_app_id, expected_dos,
    # processed_dos, files_uploaded/skipped/failed, ts_imported/skipped/failed,
    # structured_imported/failed, duration_s}.
    completeness_rows: list[dict] = []

    source_map = [
        ("tapelaying",    SOURCE_TAPELAYING_COLL_ID,    None),
        ("bridgewelding", SOURCE_BRIDGEWELDING_COLL_ID, "tapelaying"),
    ]

    # v15.13 PROGRESS-ETA: pre-flight totals banner.
    # Gives the operator + tmux readers a real "X of Y" denominator before the
    # per-step loop starts. Each `count_data_objects` is a small list-page query;
    # fetching twice (here + inside the loop) costs ~100ms — worth it for the
    # banner. Stored on the function for later ETA computation.
    pre_totals: dict[str, int] = {}
    for _step_key, _src_coll_id, _ in source_map:
        if _src_coll_id is None:
            continue
        try:
            pre_totals[_step_key] = int(_src.count_data_objects(_src_coll_id))
        except (ValueError, TypeError, AttributeError):
            pre_totals[_step_key] = -1
    grand_total = sum(v for v in pre_totals.values() if v >= 0)
    print()
    print("  ┌─ PRE-FLIGHT TOTALS ─────────────────────────────────────────────┐")
    for _step_key, _src_coll_id, _ in source_map:
        if _src_coll_id is None:
            print(f"  │  {_step_key:<14}  (skipped — coll id not set)" + " " * 12 + "│")
        else:
            t = pre_totals.get(_step_key, "?")
            print(f"  │  {_step_key:<14}  src_coll={_src_coll_id:<8}  total={t:>8} DOs   │")
    print(f"  │  GRAND TOTAL: {grand_total} source DataObject(s) to migrate".ljust(67) + "│")
    print("  └─────────────────────────────────────────────────────────────────┘")
    _diag_emit("warmup_step", {
        "step": "pre_flight_totals",
        "pre_totals": pre_totals,
        "grand_total": grand_total,
        "source_map": [{"step": k, "src_coll_id": c, "pred": p} for k, c, p in source_map],
    })
    # Track cumulative processed across the run for ETA computation.
    _processed_overall = {"dos": 0, "start_ts": time.time()}

    for step_key, src_coll_id, pred_key in source_map:
        if src_coll_id is None:
            print(f"\n[{step_key}] SOURCE_{step_key.upper()}_COLL_ID not set — skipping")
            _diag_emit("iter_end", {
                "step": step_key,
                "skipped_reason": "source_coll_id_unset",
            })
            continue

        print(f"\n[{step_key}] source collection {src_coll_id}")
        _iter_start_s = time.time()
        total_dos = _src.count_data_objects(src_coll_id)
        print(f"  {total_dos} DataObject(s) to migrate")
        _diag_emit("iter_start", {
            "step": step_key,
            "src_coll_id": src_coll_id,
            "pred_step": pred_key,
            "total_source_dos": total_dos,
        })

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
        # v15.9 MFFD-IMPORT-USER-CAPTURE — every step DO carries source-user
        # attrs when resolved at startup. Absent fields are skipped (no
        # attribute with empty value). The trio source_user_username +
        # source_user_displayName + source_user_email pairs with the
        # ProvenanceCaptureFilter dest-side enrichment (PROV-USER-ENRICH
        # backlog row) for the full attribution chain.
        if SOURCE_USER_INFO:
            su_user = SOURCE_USER_INFO.get("username") or ""
            su_first = SOURCE_USER_INFO.get("firstName") or ""
            su_last = SOURCE_USER_INFO.get("lastName") or ""
            su_email = SOURCE_USER_INFO.get("email") or ""
            su_display = SOURCE_USER_INFO.get("effectiveDisplayName") or ""
            if not su_display:
                su_display = f"{su_first} {su_last}".strip() or su_user
            if su_user:
                base_attrs["source_user_username"] = su_user
            if su_display:
                base_attrs["source_user_displayName"] = su_display
            if su_email:
                base_attrs["source_user_email"] = su_email
            base_attrs["source_user_instance"] = SOURCE_SHEPARD_URL

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
        #
        # v15.8 IMPORT-PERF1 — real worker fan-out. The per-DO body lives in
        # `_process_one_source_do` below; the driver submits one future per
        # source DO and aggregates counts as futures complete. tqdm increments
        # happen ONLY in the driver thread (per advisor: tqdm.write is locked,
        # tqdm.update from worker threads is unsafe). State writes are
        # serialised by ImportState's RLock (added in v15.8 PERF1).

        # Hoist `existing_names` to the per-step boundary — under workers,
        # N parallel calls would all ask the dest for the same list. Snapshot
        # once at step start; the state-file `is_file_done` check stays per-DO
        # and is the real correctness guard against double-upload.
        existing_names_snapshot = dest_client.list_file_refs(coll_id, dest_do_id)

        # v15.15 BUG-F2 — SD container is PREDEFINED for this step. Look up
        # the pre-created container id (validated at run_source_mode startup
        # against the dest API). All SD payloads of this step + ALL ref_names
        # land their oids into this single container.
        step_sd_container_id_for_this_step: int = PREDEFINED_SD[step_key]

        files_uploaded = 0
        files_skipped = 0
        files_failed = 0
        ts_imported = 0
        ts_skipped = 0
        ts_failed = 0
        structured_imported = 0
        structured_failed = 0

        def _process_one_source_do(src_do: SourceDO, do_bar: Any) -> dict[str, int]:
            """v15.8 IMPORT-PERF1 — per-DO unit of work.

            Captures step-scoped context via closure (dest_do_id, dest_app_id,
            ts_container_id, step_key, src_coll_id, _src, dest_client, state,
            existing_names_snapshot). Returns per-DO counts; the driver sums
            them into the per-step totals.

            All `do_bar.write(...)` calls use tqdm's lock (thread-safe per
            tqdm docs). `do_bar.update(1)` is called by the DRIVER after the
            future resolves, never from inside this function.

            v15.8 IMPORT-PERF2 — lazy enrichment + per-DO short-circuit:
              1. If `state.is_do_done(<step_key>/<src_do.name>)` returns True
                 — the previous successful run already fully processed this
                 DO — return immediately. ZERO cube3 GETs. This is the
                 "fully-done DO on resume costs 0 GETs" guarantee.
              2. Otherwise: ref-lists are still lazy-loaded HERE (not in
                 `iter_data_objects`). Per-ref state-skip checks still apply,
                 so a partially-done DO only re-does the missing pieces.
              3. At the end, if the DO completed with zero failures across
                 all three payload kinds, `mark_do_done` is called so the
                 short-circuit fires next time.
            """
            counts = {"files_uploaded": 0, "files_skipped": 0, "files_failed": 0,
                      "ts_imported": 0, "ts_skipped": 0, "ts_failed": 0,
                      "structured_imported": 0, "structured_failed": 0}

            # v15.11 IMPORT-DIAG — per-DO correlation id ties together every
            # http_request / http_response / state_save event emitted while
            # processing this source DO. Format: `<step>:<src_do_id>` keeps
            # the correlation small enough for log filtering by `grep corr`.
            corr_id = f"{step_key}:{src_do.do_id}"
            do_start_s = time.time()

            # v15.8 PERF2 per-DO short-circuit. Key by step + name so the
            # same source name in tapelaying vs bridgewelding doesn't collide.
            do_done_key = f"{step_key}/{src_do.name}"
            if state is not None and state.is_do_done(do_done_key):
                do_bar.write(f"  ↳ {src_do.name}  [skip-do — fully done in prior run]")
                _diag_emit("do_skip", {
                    "src_do_id": src_do.do_id,
                    "src_do_name": src_do.name,
                    "step": step_key,
                    "reason": "state-file-says-done",
                }, corr=corr_id)
                # Surface the saved cube3 round-trips. counts stay zero (we
                # didn't process anything; the per-step totals already
                # absorbed this work on the prior session that marked it).
                return counts

            # Lightweight header BEFORE processing so under workers>1 the
            # interleaved [ok-file]/[error-ts] lines can be traced back to
            # the source DO that produced them.
            do_bar.write(f"  ↳ {src_do.name}  (start)")
            _diag_emit("do_start", {
                "src_do_id": src_do.do_id,
                "src_do_name": src_do.name,
                "step": step_key,
            }, corr=corr_id)

            # ── Files ──────────────────────────────────────────────────────
            file_refs = _src._load_file_refs(src_do)
            with tempfile.TemporaryDirectory() as tmpdir:
                tmp = Path(tmpdir)
                for fref in file_refs:
                    dest_name = f"{src_do.name}/{fref.name}"
                    state_key = f"{step_key}/{dest_name}"

                    if state is not None and state.is_file_done(state_key):
                        do_bar.write(f"    [skip-file] {dest_name}")
                        counts["files_skipped"] += 1
                        continue

                    if dest_name in existing_names_snapshot or fref.name in existing_names_snapshot:
                        do_bar.write(f"    [skip-file] {fref.name}")
                        counts["files_skipped"] += 1
                        if state is not None:
                            state.mark_file_done(state_key)
                        continue

                    tmp_file = tmp / fref.name
                    ok = _src.download_file_ref(
                        src_coll_id, src_do.do_id, fref.fref_id, tmp_file, fref.size
                    )
                    if not ok:
                        do_bar.write(f"    [error-file] download {fref.name}")
                        counts["files_failed"] += 1
                        continue

                    ok = dest_client.upload_file(
                        dest_app_id, tmp_file, dest_name, coll_id, dest_do_id
                    )
                    if ok:
                        do_bar.write(f"    [ok-file] {dest_name}  ({_human(fref.size)})")
                        counts["files_uploaded"] += 1
                        if state is not None:
                            state.mark_file_done(state_key)
                    else:
                        do_bar.write(f"    [error-file] upload {dest_name}")
                        counts["files_failed"] += 1

            # ── Timeseries ─────────────────────────────────────────────────
            if ts_container_id is not None:
                ts_refs = _src._load_ts_refs(src_do)
                for ts_ref in ts_refs:
                    state_key = f"ts/{step_key}/{src_do.do_id}/{ts_ref.ref_id}"
                    if state is not None and state.is_ts_done(state_key):
                        do_bar.write(f"    [skip-ts] {ts_ref.name}")
                        counts["ts_skipped"] += 1
                        continue

                    do_bar.write(f"    [ts] exporting {ts_ref.name!r} from source ref {ts_ref.ref_id}")
                    csv_bytes = _src.export_ts(src_coll_id, src_do.do_id, ts_ref.ref_id)
                    if csv_bytes is None:
                        do_bar.write(f"    [error-ts] export failed for {ts_ref.name}")
                        counts["ts_failed"] += 1
                        continue

                    do_bar.write(f"    [ts] importing {len(csv_bytes):,} bytes → container {ts_container_id}")
                    ok = dest_client.import_ts_csv(
                        ts_container_id,
                        csv_bytes,
                        filename=f"{step_key}-{src_do.do_id}-{ts_ref.ref_id}.csv",
                    )
                    if ok:
                        counts["ts_imported"] += 1
                        if state is not None:
                            state.mark_ts_done(state_key)
                    else:
                        do_bar.write(f"    [error-ts] import failed for {ts_ref.name}")
                        counts["ts_failed"] += 1

            # ── Structured data ────────────────────────────────────────────
            sd_refs = _src._load_structured_refs(src_do)
            for sd_ref in sd_refs:
                state_key = f"sd/{step_key}/{src_do.do_id}/{sd_ref.ref_id}"
                if state is not None and state.is_structured_done(state_key):
                    do_bar.write(f"    [skip-sd] {sd_ref.name}")
                    continue

                payload = _src.download_structured(src_coll_id, src_do.do_id, sd_ref.ref_id)
                if payload is None:
                    do_bar.write(f"    [error-sd] download failed for {sd_ref.name}")
                    counts["structured_failed"] += 1
                    continue

                # v15.15 BUG-F2: use the predefined SD container for this step
                # (validated at run_source_mode startup, no create-on-demand).
                container_id = step_sd_container_id_for_this_step

                # v15.14 BUG-G fix — upload payload FIRST to get the oid, THEN
                # link the oid to this DO as a structuredDataReference. The
                # previous v15.13 ordering called link before upload AND without
                # the oid, so link_structured_to_do always refused (Bug C path),
                # and the oid from upload was never captured (just truthy ok).
                oid = dest_client.upload_structured_payload(container_id, payload, name=sd_ref.name)
                if not oid:
                    do_bar.write(f"    [error-sd] payload upload failed for {sd_ref.name}")
                    counts["structured_failed"] += 1
                    continue
                # Reference name carries the per-Execution identity (so the UI
                # shows which Execution this oid belongs to); container is shared.
                ref_name = f"{src_do.name}/{sd_ref.name}"
                linked = dest_client.link_structured_to_do(
                    coll_id, dest_do_id, container_id, ref_name, oids=[oid]
                )
                if linked:
                    do_bar.write(f"    [ok-sd] {sd_ref.name} -> oid={oid[:12]}...")
                    counts["structured_imported"] += 1
                    if state is not None:
                        state.mark_structured_done(state_key)
                else:
                    do_bar.write(f"    [error-sd] link-to-DO failed for {sd_ref.name}")
                    counts["structured_failed"] += 1

            # Per-DO summary footer + PERF2 mark-do-done. The footer reflects
            # actual loaded counts (file_refs, sd_refs always loaded above;
            # ts_refs only when ts_container_id is set — read via the cached
            # field on src_do, no extra GET).
            cached_ts_refs = src_do.ts_refs if src_do.ts_refs is not None else []
            payload_summary = (
                f"{len(file_refs)}f"
                f" {len(cached_ts_refs)}ts"
                f" {len(sd_refs)}sd"
            )
            do_bar.write(f"  ↳ {src_do.name}  (done — {payload_summary})")

            # v15.8 PERF2 — mark the DO done IFF every payload kind landed
            # cleanly (or was state-skipped because it was already done).
            # A single failure in any kind leaves `is_do_done` False so the
            # next resume retries the failing units.
            all_clean = (
                counts["files_failed"] == 0
                and counts["ts_failed"] == 0
                and counts["structured_failed"] == 0
            )
            if state is not None and all_clean:
                state.mark_do_done(do_done_key)

            # v15.11 IMPORT-DIAG — per-DO outcome event. `all_clean` decides
            # which kind: do_done for full success, do_error otherwise. The
            # error path carries a coarse `where` field so a future operator
            # can grep `kind:do_error` + see which payload family is failing.
            duration_ms = int((time.time() - do_start_s) * 1000)
            if all_clean:
                _diag_emit("do_done", {
                    "src_do_id": src_do.do_id,
                    "src_do_name": src_do.name,
                    "step": step_key,
                    "file_refs_written": counts["files_uploaded"],
                    "ts_refs_written": counts["ts_imported"],
                    "sd_refs_written": counts["structured_imported"],
                    "file_refs_skipped": counts["files_skipped"],
                    "ts_refs_skipped": counts["ts_skipped"],
                    "duration_ms": duration_ms,
                }, corr=corr_id)
            else:
                # Categorise which payload family failed. The `where` field
                # is the greppable signal a future Claude / operator can sort
                # error frequency by.
                fail_classes = []
                if counts["files_failed"]:
                    fail_classes.append("file")
                if counts["ts_failed"]:
                    fail_classes.append("ts")
                if counts["structured_failed"]:
                    fail_classes.append("sd")
                _diag_emit("do_error", {
                    "src_do_id": src_do.do_id,
                    "src_do_name": src_do.name,
                    "step": step_key,
                    "where": "+".join(fail_classes) or "unknown",
                    "files_failed": counts["files_failed"],
                    "ts_failed": counts["ts_failed"],
                    "structured_failed": counts["structured_failed"],
                    "duration_ms": duration_ms,
                    # The per-call http_response events carry the structured
                    # `diagnostic_hint`; the do_error event is the aggregate.
                    "diagnostic_hint": (
                        "see http_response events with same corr for "
                        "per-call status + hint"
                    ),
                }, corr=corr_id)

            return counts

        with tqdm(
            total=total_dos,
            desc=f"  DataObjects [{step_key}]",
            unit="DO",
            file=sys.stderr,
        ) as do_bar:
            if workers <= 1:
                # Sequential path — preserves v15.7 ordering semantics
                # byte-for-byte (no thread pool, no future ordering jitter).
                for src_do in _src.iter_data_objects(src_coll_id):
                    if MAX_DOS_PER_STEP and do_bar.n >= MAX_DOS_PER_STEP:
                        do_bar.write(f"  [--max-dos {MAX_DOS_PER_STEP} reached; stopping step early]")
                        break
                    do_bar.set_postfix_str(src_do.name[:30])
                    c = _process_one_source_do(src_do, do_bar)
                    files_uploaded += c["files_uploaded"]
                    files_skipped += c["files_skipped"]
                    files_failed += c["files_failed"]
                    ts_imported += c["ts_imported"]
                    ts_skipped += c["ts_skipped"]
                    ts_failed += c["ts_failed"]
                    structured_imported += c["structured_imported"]
                    structured_failed += c["structured_failed"]
                    do_bar.update(1)
            else:
                # v15.8 IMPORT-PERF1 — real fan-out. Bounded by `workers`
                # per aidocs/93 §5 (max 4 parallel GETs against cube3 +
                # 4 parallel POSTs against dest). The bound is on submitted
                # futures, not on the queue size: as one future completes
                # we submit the next source DO from the iterator. This
                # avoids the "queue up 8 457 futures eagerly" antipattern
                # which would defeat MAX_DOS_PER_STEP early-stop semantics.
                from concurrent.futures import ThreadPoolExecutor, as_completed

                do_bar.write(f"  [workers] real fan-out enabled — pool size = {workers}")
                stop_early = False
                with ThreadPoolExecutor(max_workers=workers,
                                        thread_name_prefix=f"mffd-{step_key}") as executor:
                    in_flight: dict[Any, SourceDO] = {}
                    src_iter = _src.iter_data_objects(src_coll_id)

                    def _try_submit_next() -> bool:
                        """Pull next source DO from iterator + submit. Returns
                        False when the iterator is exhausted or MAX cap hit."""
                        nonlocal stop_early
                        if stop_early:
                            return False
                        try:
                            src_do = next(src_iter)
                        except StopIteration:
                            return False
                        if MAX_DOS_PER_STEP and (do_bar.n + len(in_flight)) >= MAX_DOS_PER_STEP:
                            do_bar.write(f"  [--max-dos {MAX_DOS_PER_STEP} reached; stopping step early]")
                            stop_early = True
                            return False
                        fut = executor.submit(_process_one_source_do, src_do, do_bar)
                        in_flight[fut] = src_do
                        return True

                    # Prime the pool — submit up to `workers` futures.
                    for _ in range(workers):
                        if not _try_submit_next():
                            break

                    # Drain + refill loop. as_completed yields futures as
                    # they finish; for each completion we increment the bar
                    # in the DRIVER thread, aggregate counts, then submit
                    # the next source DO to keep the pool saturated.
                    while in_flight:
                        # Use a fresh as_completed each iteration so newly
                        # submitted futures are picked up. We pop one ready
                        # future per outer iteration.
                        done_fut = next(as_completed(list(in_flight.keys())))
                        src_do = in_flight.pop(done_fut)
                        do_bar.set_postfix_str(src_do.name[:30])
                        try:
                            c = done_fut.result()
                        except Exception as exc:
                            do_bar.write(f"  [error-do] {src_do.name}: {exc!r}")
                            c = {"files_failed": 1}
                        files_uploaded += c.get("files_uploaded", 0)
                        files_skipped += c.get("files_skipped", 0)
                        files_failed += c.get("files_failed", 0)
                        ts_imported += c.get("ts_imported", 0)
                        ts_skipped += c.get("ts_skipped", 0)
                        ts_failed += c.get("ts_failed", 0)
                        structured_imported += c.get("structured_imported", 0)
                        structured_failed += c.get("structured_failed", 0)
                        do_bar.update(1)
                        # Keep pool topped up.
                        _try_submit_next()

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

        # v15.11 IMPORT-DIAG — step boundary event so a future operator
        # can grep `kind:iter_end` + read total throughput for each step.
        _diag_emit("iter_end", {
            "step": step_key,
            "src_coll_id": src_coll_id,
            "duration_s": round(time.time() - _iter_start_s, 1),
            "total_source_dos": total_dos,
            "files_uploaded": files_uploaded,
            "files_skipped": files_skipped,
            "files_failed": files_failed,
            "ts_imported": ts_imported,
            "ts_skipped": ts_skipped,
            "ts_failed": ts_failed,
            "structured_imported": structured_imported,
            "structured_failed": structured_failed,
        })

        # v15.13 CONTAINED-COMPLETENESS: persist per-step row for inline pass
        _processed_overall["dos"] += total_dos  # type: ignore[index]
        completeness_rows.append({
            "step": step_key,
            "src_coll_id": src_coll_id,
            "dest_do_id": dest_do_id,
            "dest_do_app_id": do_app_ids.get(step_key),
            "expected_dos": total_dos,
            "processed_dos": files_uploaded + files_skipped + files_failed,
            "files": {"uploaded": files_uploaded, "skipped": files_skipped, "failed": files_failed},
            "timeseries": {"imported": ts_imported, "skipped": ts_skipped, "failed": ts_failed},
            "structured": {"imported": structured_imported, "failed": structured_failed},
            "duration_s": round(time.time() - _iter_start_s, 1),
        })

    # ── v15.13 CONTAINED-COMPLETENESS PASS ────────────────────────────────────
    # Runs in the same process. No external script, no extra source queries —
    # uses the running counters the importer already maintains. Emits a
    # `migration_completeness` diag event + writes `mffd-completeness-<session>.json`
    # + best-effort uploads the report as a MIGRATION-COMPLETENESS-<session>
    # DataObject in the destination collection (lineage anchor).
    try:
        _verdict = _build_completeness_verdict(
            session_id=SESSION_ID,
            script_version=IMPORT_SCRIPT_VERSION,
            pre_totals=pre_totals,
            grand_total=grand_total,
            rows=completeness_rows,
            started_ts=_processed_overall["start_ts"],
        )
        _diag_emit("migration_completeness", _verdict)
        _print_completeness_banner(_verdict)
        _persist_completeness_artifact(_verdict, dest_client, coll_id)
    except Exception as _exc:
        print(f"  [completeness-warn] inline pass failed: {_exc!r}")
        _diag_emit("migration_completeness_error", {"error": repr(_exc)})

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
    """v15.8 IMPORT-PERF1 — thin forwarder onto `run_source_mode(workers=N)`.

    In v15.1..v15.7 this was a fake wrapper: it instantiated a
    ThreadPoolExecutor, ran four `lambda: True` probes, then delegated to
    `run_source_mode` SEQUENTIALLY inside the executor context. `--workers 4`
    was a footgun that bought zero concurrency. See
    `aidocs/agent-findings/mffd-import-slowness-diagnose-2026-05-23.md §5`
    hypothesis #1.

    v15.8 moves the worker-pool implementation INTO `run_source_mode` so the
    per-DO loop body (the actual unit of work) is the thing that fans out,
    not the per-step driver. The wrapper stays as the script-level entry
    point and as a stable name for callers; it now does what its name says.

    Sequential fallback at `workers <= 1` is preserved byte-for-byte: the
    same sequential path executes inside `run_source_mode`. Tests in
    `tests/test_worker_pool.py` were updated in v15.8 to assert the new
    contract.
    """
    if workers <= 1:
        # Pure sequential — `run_source_mode` takes the workers==1 branch.
        return run_source_mode(
            dest_client, coll_id, state, source_client,
            default_license=default_license,
            default_access_rights=default_access_rights,
            name_map=name_map,
            workers=1,
        )

    print(f"\n[workers] v15.8 real fan-out — pool size = {workers}")
    print(f"[workers] per-step ThreadPoolExecutor, per-DO future submission")
    print(f"[workers] cube3 + dest concurrency bound: {workers} parallel in-flight per aidocs/93 §5")

    return run_source_mode(
        dest_client, coll_id, state, source_client,
        default_license=default_license,
        default_access_rights=default_access_rights,
        name_map=name_map,
        workers=workers,
    )


# ── State tracker (resume support) ───────────────────────────────────────────

import json
import time

class ImportState:
    """Persist progress to JSON so a failed run can resume cleanly.

    v15.8 IMPORT-PERF1 — thread-safe under parallel workers. All reads + writes
    are guarded by `_lock` (RLock so a `mark_*` that nests inside another
    state op cannot self-deadlock). The lock also covers `_save()`, which
    rewrites the entire JSON; under 4 workers each marking a few times per
    second this is N file rewrites per sec but bounded by the lock — never
    overlapping.
    """

    def __init__(self, path: Path) -> None:
        self._path = path
        self._data: dict = {}
        # v15.8 PERF1 — RLock guards _data + _save against concurrent workers.
        self._lock = threading.RLock()
        if path.exists():
            try:
                with path.open() as f:
                    self._data = json.load(f)
                print(f"[state] Resuming from {path}  ({len(self._data.get('completed_files',[]))} files already done)")
            except Exception:
                self._data = {}

    def _save(self) -> None:
        # Caller MUST hold _lock. _save is private and only called from
        # mutators below, which all hold the lock.
        self._path.write_text(json.dumps(self._data, indent=2))

    @property
    def warmup_done(self) -> bool:
        with self._lock:
            return bool(self._data.get("warmup_done"))

    def mark_warmup_done(self) -> None:
        with self._lock:
            self._data["warmup_done"] = True
            self._save()

    @property
    def gate_passed(self) -> bool:
        with self._lock:
            return bool(self._data.get("gate_passed"))

    def mark_gate_passed(self) -> None:
        with self._lock:
            self._data["gate_passed"] = True
            self._save()

    def is_do_done(self, do_name: str) -> bool:
        with self._lock:
            return do_name in self._data.get("completed_dos", [])

    def mark_do_done(self, do_name: str) -> None:
        with self._lock:
            self._data.setdefault("completed_dos", [])
            if do_name not in self._data["completed_dos"]:
                self._data["completed_dos"].append(do_name)
            self._save()

    def is_file_done(self, key: str) -> bool:
        with self._lock:
            return key in self._data.get("completed_files", [])

    def mark_file_done(self, key: str) -> None:
        with self._lock:
            self._data.setdefault("completed_files", [])
            if key not in self._data["completed_files"]:
                self._data["completed_files"].append(key)
            self._save()

    def is_ts_done(self, key: str) -> bool:
        with self._lock:
            return key in self._data.get("completed_ts", [])

    def mark_ts_done(self, key: str) -> None:
        with self._lock:
            self._data.setdefault("completed_ts", [])
            if key not in self._data["completed_ts"]:
                self._data["completed_ts"].append(key)
            self._save()

    def is_structured_done(self, key: str) -> bool:
        with self._lock:
            return key in self._data.get("completed_structured", [])

    def mark_structured_done(self, key: str) -> None:
        with self._lock:
            self._data.setdefault("completed_structured", [])
            if key not in self._data["completed_structured"]:
                self._data["completed_structured"].append(key)
            self._save()

    def get_ts_container(self, step_key: str) -> int | None:
        with self._lock:
            return self._data.get("ts_containers", {}).get(step_key)

    def set_ts_container(self, step_key: str, container_id: int) -> None:
        with self._lock:
            self._data.setdefault("ts_containers", {})[step_key] = container_id
            self._save()


# ── v15.4 IMPORT-T1: telemetry (metrics + runlog) ────────────────────────────

class Telemetry:
    """Background-flushed telemetry: metrics → TS container, events → SD
    container. Both are stored INSIDE the dest Shepard instance (per
    `feedback_shepard_measures_itself.md` — observability lives where the
    researcher already looks). All emit calls are lock-protected + bounded;
    failures are silent (telemetry must never break ingestion).

    Metric schema (ROW format CSV uploaded to TELEMETRY_TS_CONTAINER_ID):
        measurement=mffd_import, device=<hostname>, location=<mode>,
        symbolicName=v<version>, field=<metric_name>, timestamp=<ns>,
        value=<float>

    Event schema (one structured-data row per heartbeat, with events array):
        {ts, host, version, mode, events: [{ts,level,event,...}, ...]}
    """

    _NEVER_NEGATIVE = ("dos_processed", "ts_points_imported", "files_uploaded",
                       "structured_imported", "errors", "retries",
                       "redeploys_survived", "selfupdate_checks")

    @staticmethod
    def _scrub(s: str) -> str:
        """v15.6 IMPORT-T2 — backend `TimeseriesValidator` rejects
        Space/Comma/Point/Slash in ALL FIVE Timeseries fields (measurement,
        device, location, symbolicName, field). Replace each with '_' so a
        hostname like `cube3.intra.dlr.de` still produces a valid 5-tuple.
        """
        if not s:
            return "unknown"
        out = []
        for ch in s:
            out.append("_" if ch in " .,/" else ch)
        return "".join(out) or "unknown"

    def __init__(self, dest_client: Any, version: str, mode: str) -> None:
        self._client = dest_client
        self._version = version
        self._mode = self._scrub(mode)
        self._host = self._scrub(socket.gethostname())
        self._lock = threading.Lock()
        self._metrics: dict[str, list[tuple[int, float]]] = {}
        self._events: list[dict] = []
        self._counters: dict[str, float] = {k: 0.0 for k in self._NEVER_NEGATIVE}
        self._gauges: dict[str, float] = {}
        self._start_ts_s = time.time()
        self._last_flush_s = self._start_ts_s
        self._enabled = bool(TELEMETRY_TS_CONTAINER_ID and RUNLOG_SD_CONTAINER_ID)

    def counter(self, name: str, delta: float = 1.0) -> None:
        with self._lock:
            self._counters[name] = self._counters.get(name, 0.0) + delta

    def gauge(self, name: str, value: float) -> None:
        with self._lock:
            self._gauges[name] = float(value)

    def event(self, level: str, event: str, **kwargs: Any) -> None:
        row = {
            "ts": datetime.datetime.now(datetime.timezone.utc).isoformat(),
            "level": level,
            "event": event,
            "version": self._version,
            "mode": self._mode,
            "host": self._host,
            **kwargs,
        }
        with self._lock:
            self._events.append(row)
            if len(self._events) > 5000:
                self._events = self._events[-2000:]  # bound

    def flush(self) -> None:
        if not self._enabled:
            return
        now_s = time.time()
        with self._lock:
            counters = dict(self._counters)
            gauges = dict(self._gauges)
            events, self._events = self._events, []
            self._gauges = {}
            elapsed_since_last = now_s - self._last_flush_s
            self._last_flush_s = now_s

        # Build ROW-format CSV with both counter snapshots and current gauges.
        ts_ns = time.time_ns()
        try:
            buf = io.StringIO()
            buf.write("timestamp,measurement,device,location,symbolicName,field,value\n")
            for k, v in counters.items():
                buf.write(f"{ts_ns},mffd_import,{self._host},{self._mode},v{self._version.replace('.', '_')},counter_{k},{v}\n")
            for k, v in gauges.items():
                buf.write(f"{ts_ns},mffd_import,{self._host},{self._mode},v{self._version.replace('.', '_')},gauge_{k},{v}\n")
            buf.write(f"{ts_ns},mffd_import,{self._host},{self._mode},v{self._version.replace('.', '_')},gauge_uptime_s,{now_s - self._start_ts_s}\n")
            csv_bytes = buf.getvalue().encode("utf-8")
            self._client.import_ts_csv(TELEMETRY_TS_CONTAINER_ID, csv_bytes,
                                       filename=f"telemetry-{int(now_s)}.csv")
        except Exception as exc:
            print(f"  [telemetry] metric flush failed: {exc!r}", flush=True)

        # Push events as one structured-data row carrying the batch.
        if events:
            try:
                envelope = {
                    "ts": datetime.datetime.now(datetime.timezone.utc).isoformat(),
                    "host": self._host,
                    "version": self._version,
                    "mode": self._mode,
                    "events": events,
                }
                url = (
                    f"{self._client._base}/shepard/api/structuredDataContainers/"
                    f"{RUNLOG_SD_CONTAINER_ID}/payload"
                )
                self._client._post(url, {
                    "structuredData": None,
                    "payload": _json.dumps(envelope, default=str),
                })
            except Exception as exc:
                print(f"  [telemetry] event flush failed: {exc!r}", flush=True)


# ── v15.11 IMPORT-DIAG: structured diagnostic instrumentation ────────────────
#
# DiagSink emits one JSON line per event so a future operator (or Claude session)
# can grep the tmux scrollback / log file for `kind:do_error` + the auto-classified
# `diagnostic_hint` and act without code access. Emits ALONGSIDE Telemetry — does
# not replace it. Telemetry remains the rollup-into-Shepard channel (counters +
# event batches every HEARTBEAT_S); DiagSink is the per-event greppable line.
#
# Modes:
#   quiet       — startup/shutdown + do_error + summary only (lowest noise)
#   normal      — adds do_start/do_done + iter ticks + jwt_status + state_save
#   verbose     — adds worker_pool ticks + manifest_poll + telemetry_flush
#   http-trace  — adds every 2xx http_response (verbose mode + http traces)
#
# Atomicity: every line is written via a single `os.write(2, line + "\n")` under
# a module-level lock. Lines are bounded at 4096 bytes (payload truncation, not
# line truncation) so the kernel guarantees no interleaving with concurrent emit.
# Credential masking: any token / api-key / Authorization header is rendered as
# `sha256:<first-12-of-hex>` before it ever reaches the emit path.

class DiagSink:
    """Structured single-line JSON event emitter for self-diagnosable failures.

    The contract: every event is one line of JSON terminated by '\\n', atomic
    against concurrent threads, with a stable schema:

        {"t":<iso>, "v":<version>, "session":<session>, "pid":<pid>,
         "kind":<event-kind>, "corr":<correlation-id-or-null>,
         "payload":{...}}

    Events are emitted to stderr (so they show up in tmux), tee'd into the
    existing log file (already opened by `Tee`), and optionally batched
    forward to the existing Telemetry event channel (which lands them in
    structured-data container 593753).

    `kind` taxonomy (see aidocs/integrations/95 §Pattern 19):
      startup, warmup_step, source_user_resolved, iter_start, iter_end,
      do_start, do_skip, do_done, do_error, http_request, http_response,
      worker_pool, jwt_status, manifest_poll, state_save, telemetry_flush,
      summary, shutdown.

    Diagnostic hints (auto-classified from http_status + body content):
      jwt-expired-or-rotated (401), permission-denied (403),
      not-found (404), conflict (409), bean-validation (Quarkus violations),
      server-side (5xx), timeout (network), rate-limited (429).
    """

    _VALID_MODES = ("quiet", "normal", "verbose", "http-trace")
    _MAX_LINE_BYTES = 4096  # Linux PIPE_BUF — kernel-atomic write boundary.
    _MAX_PAYLOAD_FIELD = 500  # Truncate any string payload field to this.

    # Per-mode allowed-event sets. Membership test is cheap; the emit path
    # short-circuits on `kind not in self._allowed` before doing any work.
    _ALWAYS = frozenset({
        "startup", "shutdown", "warmup_step", "do_error",
        "summary", "source_user_resolved",
    })
    _NORMAL = _ALWAYS | frozenset({
        "do_start", "do_done", "do_skip", "iter_start", "iter_end",
        "jwt_status", "state_save",
    })
    _VERBOSE = _NORMAL | frozenset({
        "worker_pool", "manifest_poll", "telemetry_flush",
        "http_response",  # non-2xx ALWAYS; 2xx only with http-trace
    })
    _HTTP_TRACE = _VERBOSE | frozenset({"http_request"})

    def __init__(self, version: str, session: str, mode: str = "normal",
                 telemetry: Any | None = None) -> None:
        if mode not in self._VALID_MODES:
            mode = "normal"
        self._version = version
        self._session = session
        self._mode = mode
        self._telemetry = telemetry
        self._pid = os.getpid()
        self._lock = threading.Lock()
        # Rolling-window summary aggregator (60s window).
        self._summary_lock = threading.Lock()
        self._summary_window: list[dict] = []
        self._summary_start_s = time.time()
        # Allowed event set per mode (resolved once).
        self._allowed: frozenset[str]
        if mode == "quiet":
            self._allowed = self._ALWAYS
        elif mode == "normal":
            self._allowed = self._NORMAL
        elif mode == "verbose":
            self._allowed = self._VERBOSE
        else:  # http-trace
            self._allowed = self._HTTP_TRACE

    # ── public API ────────────────────────────────────────────────────────

    @property
    def mode(self) -> str:
        return self._mode

    def emit(self, kind: str, payload: dict | None = None,
             corr: str | None = None) -> None:
        """Write one JSON event line. No-op when the mode filters this kind.

        For http_response specifically: non-2xx statuses are ALWAYS emitted
        regardless of mode (the failure-class signal must not be filterable
        away). 2xx http_response is gated on http-trace mode.
        """
        if kind == "http_response" and payload is not None:
            status = payload.get("status")
            if isinstance(status, int) and status >= 400:
                pass  # Always emit non-2xx — bypass mode gating.
            elif self._mode != "http-trace":
                return
        elif kind not in self._allowed:
            return

        line = self._build_line(kind, payload or {}, corr)
        self._write_atomic(line)

        # Best-effort mirror into Telemetry (the existing observability
        # channel that rolls events into structured-data container 593753).
        # Failures here are silent — Telemetry already swallows its own
        # IO errors; we mustn't break ingestion either way.
        if self._telemetry is not None and kind not in ("http_request", "worker_pool"):
            try:
                level = "error" if kind == "do_error" else (
                    "warn" if kind == "do_skip" else "info"
                )
                self._telemetry.event(level, f"diag_{kind}",
                                      corr=corr or "", **(payload or {}))
            except Exception:
                pass

        # Update the rolling-window summary for any DO-level event.
        if kind in ("do_done", "do_error", "do_skip", "http_response"):
            self._update_summary(kind, payload or {})

    def summary_tick(self) -> dict:
        """Flush the rolling 60s window. Returns the aggregated summary
        dict (also emitted as `kind=summary`). Called from the periodic
        driver tick.
        """
        with self._summary_lock:
            window = self._summary_window
            self._summary_window = []
            elapsed = time.time() - self._summary_start_s
            self._summary_start_s = time.time()

        agg = self._aggregate_window(window, elapsed)
        self.emit("summary", agg)
        return agg

    # ── error classification (the load-bearing greppable signal) ──────────

    @staticmethod
    def classify_error(http_status: int | None,
                       error_body: str | None) -> tuple[str, str]:
        """Return (hint_code, human_action) for a given http error context.

        Pure function — testable in isolation. Hint codes are the greppable
        strings that show up in `diagnostic_hint` on `do_error` / `http_response`
        events; the human_action is a short imperative the operator can act on
        without further code reading.
        """
        if http_status is None:
            # Timeout / connection refused — surfaced as None by requests.
            return ("timeout-or-network",
                    "network or slow source; consider --workers reduction")

        if http_status == 401:
            return ("jwt-expired-or-rotated",
                    "JWT rejected; check token age + re-mint via "
                    "MFFD_REFRESH_JWT_CMD env hook (v15.10) or restart")
        if http_status == 403:
            return ("permission-denied",
                    "permission denied; check FileContainer/Collection "
                    "permissionType + writer list for the auth principal")
        if http_status == 404:
            return ("not-found",
                    "endpoint or resource not found; verify v1/v2 path + "
                    "ID type (Neo4j long vs appId UUID)")
        if http_status == 409:
            return ("conflict",
                    "conflict; likely duplicate-by-name — check state-file "
                    "consistency vs dest collection contents")
        if http_status == 429:
            return ("rate-limited",
                    "rate-limited by source/dest; reduce --workers or "
                    "increase backoff cap")

        if 400 <= http_status < 500:
            # Generic 4xx — inspect body for Quarkus bean-validation envelope.
            if error_body and ("violations" in error_body
                               or "constraintViolations" in error_body):
                return ("bean-validation",
                        "Quarkus bean-validation failed; field list is in "
                        "error_body_truncated — fix the request shape")
            return ("client-error",
                    "4xx without specific class; check request shape + "
                    "error_body_truncated for server-side detail")

        if http_status >= 500:
            return ("server-side",
                    "server-side fault; retry with backoff "
                    "(transient — usually clears within 30s)")

        return ("unclassified",
                "unrecognised status; inspect error_body_truncated manually")

    @staticmethod
    def mask_credential(token: str | None) -> str:
        """Render a token / API key as `sha256:<first-12-hex>`. Never emits
        any plaintext substring of the original. Empty/None → `none`."""
        if not token:
            return "none"
        digest = hashlib.sha256(token.encode("utf-8", "replace")).hexdigest()
        return f"sha256:{digest[:12]}"

    @staticmethod
    def jwt_age_seconds(token: str | None) -> int | None:
        """Decode the unverified JWT payload and return age in seconds since
        `iat`. Returns None on any decode failure. Never raises.
        """
        if not token or "." not in token:
            return None
        try:
            import base64
            parts = token.split(".")
            if len(parts) < 2:
                return None
            # Base64url + padding fix (urlsafe_b64decode requires pad).
            payload_b64 = parts[1]
            padding = "=" * (-len(payload_b64) % 4)
            payload_bytes = base64.urlsafe_b64decode(payload_b64 + padding)
            payload = _json.loads(payload_bytes.decode("utf-8", "replace"))
            iat = payload.get("iat")
            if not isinstance(iat, (int, float)):
                return None
            return int(time.time() - iat)
        except Exception:
            return None

    @staticmethod
    def jwt_has_exp(token: str | None) -> bool:
        """True if the unverified JWT carries an `exp` claim. Never raises."""
        if not token or "." not in token:
            return False
        try:
            import base64
            parts = token.split(".")
            if len(parts) < 2:
                return False
            payload_b64 = parts[1]
            padding = "=" * (-len(payload_b64) % 4)
            payload_bytes = base64.urlsafe_b64decode(payload_b64 + padding)
            payload = _json.loads(payload_bytes.decode("utf-8", "replace"))
            return "exp" in payload
        except Exception:
            return False

    # ── internals ────────────────────────────────────────────────────────

    def _build_line(self, kind: str, payload: dict,
                    corr: str | None) -> bytes:
        """Build one JSON-serialised line, truncating any oversize fields."""
        # Defensive: truncate large string fields so the whole line fits
        # in PIPE_BUF (atomic write boundary on Linux). Only string values
        # are truncated; ints/dicts/lists pass through.
        safe_payload = {}
        for k, v in payload.items():
            if isinstance(v, str) and len(v) > self._MAX_PAYLOAD_FIELD:
                safe_payload[k] = v[:self._MAX_PAYLOAD_FIELD] + "...[truncated]"
            else:
                safe_payload[k] = v

        event = {
            "t": datetime.datetime.now(datetime.timezone.utc).isoformat(),
            "v": self._version,
            "session": self._session,
            "pid": self._pid,
            "kind": kind,
            "corr": corr,
            "payload": safe_payload,
        }
        line = _json.dumps(event, default=str, separators=(",", ":"))
        encoded = (line + "\n").encode("utf-8", "replace")

        # If still oversized (very rare — nested structure too deep), trim
        # the payload completely and add an overflow marker. Lines must
        # never exceed PIPE_BUF or we lose atomicity.
        if len(encoded) > self._MAX_LINE_BYTES:
            event["payload"] = {"_overflow": True,
                                "_original_size": len(encoded),
                                "_keys": list(safe_payload.keys())}
            line = _json.dumps(event, default=str, separators=(",", ":"))
            encoded = (line + "\n").encode("utf-8", "replace")
        return encoded

    def _write_atomic(self, line_bytes: bytes) -> None:
        """Write the line to stderr fd as a single os.write call. Linux
        guarantees atomicity for writes ≤ PIPE_BUF (4096 bytes). The
        module-level lock is a belt-and-braces for non-Linux platforms.
        """
        with self._lock:
            try:
                os.write(2, line_bytes)
            except Exception:
                # Diagnostic must never break the import.
                pass

    def _update_summary(self, kind: str, payload: dict) -> None:
        with self._summary_lock:
            self._summary_window.append({"k": kind, "p": payload})
            # Bound to avoid unbounded growth between ticks.
            if len(self._summary_window) > 10_000:
                self._summary_window = self._summary_window[-5_000:]

    @staticmethod
    def _aggregate_window(window: list[dict], elapsed_s: float) -> dict:
        """Pure-function aggregator — unit-testable. Returns the summary
        payload (do_done_count, do_error_count, http_4xx/5xx/429, avg/p95
        duration_ms)."""
        do_done = []
        do_done_count = 0
        do_error_count = 0
        do_skip_count = 0
        http_4xx = 0
        http_5xx = 0
        http_429 = 0
        for ev in window:
            k = ev["k"]
            p = ev["p"]
            if k == "do_done":
                do_done_count += 1
                d = p.get("duration_ms")
                if isinstance(d, (int, float)):
                    do_done.append(float(d))
            elif k == "do_error":
                do_error_count += 1
            elif k == "do_skip":
                do_skip_count += 1
            elif k == "http_response":
                s = p.get("status")
                if isinstance(s, int):
                    if s == 429:
                        http_429 += 1
                    elif 400 <= s < 500:
                        http_4xx += 1
                    elif s >= 500:
                        http_5xx += 1
        avg_ms = (sum(do_done) / len(do_done)) if do_done else 0.0
        if do_done:
            sorted_dur = sorted(do_done)
            p95_idx = max(0, int(len(sorted_dur) * 0.95) - 1)
            p95_ms = sorted_dur[p95_idx]
        else:
            p95_ms = 0.0
        return {
            "window_s": round(elapsed_s, 1),
            "do_done_count": do_done_count,
            "do_error_count": do_error_count,
            "do_skip_count": do_skip_count,
            "avg_do_duration_ms": round(avg_ms, 1),
            "p95_do_duration_ms": round(p95_ms, 1),
            "http_4xx_count": http_4xx,
            "http_5xx_count": http_5xx,
            "http_429_count": http_429,
        }


# ── v15.4 IMPORT-SU1: self-update mechanism ──────────────────────────────────

class Manifest:
    """Polls dest Shepard for the latest published manifest JSON. The newest
    file in MANIFEST_CONTAINER_ID whose name starts with MANIFEST_NAME_PREFIX
    is the source of truth. Manifest schema:
        {"version": "15.5", "script_oid": "<uuid>",
         "script_sha256": "<hex>", "script_filename": "mffd-import-v15.py",
         "released_at": "2026-05-23T..."}
    """

    def __init__(self, dest_client: Any) -> None:
        self._client = dest_client

    def latest(self) -> dict | None:
        # v15.6 IMPORT-SU2 — correct v5 path is /payload (lists files in
        # container), not /files (which 404s — there is no such endpoint).
        url = (
            f"{self._client._base}/shepard/api/fileContainers/"
            f"{MANIFEST_CONTAINER_ID}/payload"
        )
        r = self._client._get(url)
        if r is None:
            return None
        try:
            files = r.json()
        except Exception:
            return None
        cands = [
            f for f in (files or [])
            if isinstance(f, dict)
            and (f.get("filename") or "").startswith(MANIFEST_NAME_PREFIX)
            and (f.get("filename") or "").endswith(".json")
        ]
        if not cands:
            return None
        cands.sort(key=lambda f: f.get("createdAt", ""), reverse=True)
        oid = cands[0].get("oid")
        if not oid:
            return None
        r2 = self._client._get(
            f"{self._client._base}/shepard/api/fileContainers/"
            f"{MANIFEST_CONTAINER_ID}/payload/{oid}"
        )
        if r2 is None:
            return None
        try:
            return r2.json()
        except Exception:
            return None


class SelfUpdater(threading.Thread):
    """Background heartbeat thread that polls the manifest every
    HEARTBEAT_S seconds. On version drift + sha256-verified download:
      1. Writes the new script to a temp file next to __file__
      2. Sets `update_event` — the main loop polls this between batches
      3. When main loop sees the event: graceful checkpoint + flush +
         atomic rename(temp → __file__) + os.execv to replace the process.
    """

    def __init__(self, current_version: str, dest_client: Any,
                 telemetry: Telemetry) -> None:
        super().__init__(daemon=True, name="SelfUpdater")
        self._current = current_version
        self._client = dest_client
        self._tel = telemetry
        self._manifest = Manifest(dest_client)
        self.update_event = threading.Event()
        self.stop_event = threading.Event()
        self.pending_path: Path | None = None
        self.pending_version: str | None = None

    def stop(self) -> None:
        self.stop_event.set()

    def run(self) -> None:
        # Initial 5s grace so the main loop has settled.
        if self.stop_event.wait(5.0):
            return
        while not self.stop_event.is_set():
            self._tel.counter("selfupdate_checks")
            try:
                latest = self._manifest.latest()
                if latest and isinstance(latest, dict):
                    new_v = str(latest.get("version") or "").strip()
                    if new_v and new_v != self._current:
                        self._handle_update(latest, new_v)
            except Exception as exc:
                self._tel.event("warn", "selfupdate_error",
                                exc=type(exc).__name__, msg=str(exc)[:200])
            if self.stop_event.wait(float(HEARTBEAT_S)):
                return

    def _handle_update(self, manifest: dict, new_v: str) -> None:
        new_oid = manifest.get("script_oid")
        new_sha = manifest.get("script_sha256")
        new_name = manifest.get("script_filename", "mffd-import-v15.py")
        if not new_oid:
            self._tel.event("warn", "manifest_missing_oid",
                            version=new_v, manifest=manifest)
            return
        self._tel.event("info", "update_available",
                        current=self._current, target=new_v, oid=new_oid)
        url = (
            f"{self._client._base}/shepard/api/fileContainers/"
            f"{MANIFEST_CONTAINER_ID}/payload/{new_oid}"
        )
        r = self._client._get(url)
        if r is None:
            self._tel.event("error", "update_download_failed",
                            target=new_v, oid=new_oid)
            return
        body = r.content
        if new_sha:
            got = hashlib.sha256(body).hexdigest()
            if got != new_sha:
                self._tel.event("error", "update_sha256_mismatch",
                                target=new_v, expected=new_sha, actual=got)
                return
        # Stage to a temp path; main loop renames + execv-replaces atomically.
        tmp = Path(__file__).with_suffix(f".v{new_v}.new")
        tmp.write_bytes(body)
        self.pending_path = tmp
        self.pending_version = new_v
        self._tel.event("info", "update_ready_for_exec",
                        target=new_v, path=str(tmp))
        self.update_event.set()


def apply_pending_update(updater: SelfUpdater, telemetry: Telemetry,
                         checkpoint: "Checkpoint") -> None:
    """Called from the main loop when updater.update_event is set. Atomic
    rename + os.execv the new script. Returns only if the update could not
    be applied (caller decides whether to continue or abort)."""
    if not updater.pending_path or not updater.pending_version:
        return
    new_path = updater.pending_path
    new_v = updater.pending_version
    try:
        # Ensure the checkpoint reflects "we are about to execv" so the new
        # process has a known-good resume point.
        telemetry.event("info", "applying_update", target=new_v)
        telemetry.flush()
        cur = checkpoint.load() or {}
        cur["last_execv_at"] = datetime.datetime.now(datetime.timezone.utc).isoformat()
        cur["last_execv_from"] = IMPORT_SCRIPT_VERSION
        cur["last_execv_to"] = new_v
        checkpoint.save(cur)
        # Atomic replace
        final = Path(__file__)
        new_path.replace(final)
        # execv preserves argv + env. Mode + checkpoint allow resume.
        os.execv(sys.executable, [sys.executable, str(final), *sys.argv[1:]])
    except Exception as exc:
        telemetry.event("error", "update_execv_failed",
                        exc=type(exc).__name__, msg=str(exc)[:200])
        # Fall through: caller continues with old version.


# ── v15.4 IMPORT-CP1: checkpoint file (resume across self-update / kill) ─────

class Checkpoint:
    """Single-JSON checkpoint, atomic write via existing atomic_write_json.
    Schema (free-form within reason):
        {"version": "15.4", "mode": "source" | "bootstrap" | "verify",
         "src_coll_id": <int>, "dest_coll_id": <int>,
         "started_at": "...", "last_saved_at": "...",
         "processed_src_do_ids": [...],
         "skipped_src_do_ids": [...],
         "counters": {...},
         "last_execv_at": "...", "last_execv_from": "15.3", "last_execv_to": "15.4"}
    """

    def __init__(self, path: Path) -> None:
        self._path = path
        self._lock = threading.Lock()

    def load(self) -> dict | None:
        if not self._path.exists():
            return None
        try:
            return _json.loads(self._path.read_text())
        except Exception:
            return None

    def save(self, state: dict) -> None:
        with self._lock:
            state["last_saved_at"] = datetime.datetime.now(
                datetime.timezone.utc
            ).isoformat()
            atomic_write_json(self._path, state)

    def update(self, **kwargs: Any) -> dict:
        with self._lock:
            cur = {}
            if self._path.exists():
                try:
                    cur = _json.loads(self._path.read_text())
                except Exception:
                    cur = {}
            cur.update(kwargs)
            cur["last_saved_at"] = datetime.datetime.now(
                datetime.timezone.utc
            ).isoformat()
            atomic_write_json(self._path, cur)
            return cur

    def clear(self) -> None:
        with self._lock:
            try:
                self._path.unlink()
            except FileNotFoundError:
                pass


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
    # v15.11 IMPORT-DIAG — structured diagnostic instrumentation. See
    # `aidocs/integrations/95 §Pattern 19`. Single greppable JSON line per
    # event with auto-classified diagnostic hints. Env fallback:
    # MFFD_DIAG_MODE={quiet,normal,verbose,http-trace}.
    ap.add_argument(
        "--diag-mode",
        dest="diag_mode",
        type=str,
        default=os.environ.get("MFFD_DIAG_MODE", "normal"),
        choices=["quiet", "normal", "verbose", "http-trace"],
        help=(
            "v15.11 IMPORT-DIAG: structured-event verbosity. "
            "`quiet` = startup/shutdown + errors + summary only; "
            "`normal` (default) = adds do_start/done/skip + jwt + state; "
            "`verbose` = adds worker_pool ticks + manifest_poll + telemetry_flush; "
            "`http-trace` = adds every 2xx http response (debug only). "
            "Non-2xx http responses are ALWAYS emitted regardless of mode."
        ),
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
    # v15.5 IMPORT-CFG1: env-var fallbacks (MFFD_DEFAULT_LICENSE +
    # MFFD_DEFAULT_ACCESS_RIGHTS) so the runner can carry MFFD-appropriate
    # defaults without explicit CLI flags. Exit code 9 (was 1) so runner.sh
    # can distinguish "config error — STOP" from "transient — RETRY".
    if not args.default_license:
        args.default_license = os.environ.get("MFFD_DEFAULT_LICENSE", "").strip() or None
    if not args.default_access_rights:
        args.default_access_rights = os.environ.get("MFFD_DEFAULT_ACCESS_RIGHTS", "").strip() or None
    if not args.bootstrap and not args.dry_run and not args.verify_imported:
        if not args.default_license or not args.default_access_rights:
            print(
                "ERROR: v15.1 SOURCE/LOCAL imports require --default-license "
                "and --default-access-rights (FAIR R1 per aidocs/agent-findings/"
                "v15-review-rdm.md).\n"
                "  CLI: --default-license=CC-BY-4.0 --default-access-rights='restricted access'\n"
                "  Env: MFFD_DEFAULT_LICENSE=proprietary MFFD_DEFAULT_ACCESS_RIGHTS='restricted access'\n"
                "  for DLR MFFD industrial IP: license=proprietary access-rights='restricted access'\n"
                "  EXIT 9 = config error (operator must act). Runner.sh stops; does NOT retry.",
                file=sys.stderr,
            )
            sys.exit(9)

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

    # v15.4 IMPORT-T1/SU1/CP1 — start observability stack BEFORE warmup so a
    # failed warmup is itself captured as an event the operator can see in
    # shepard. Mode label is preliminary here; refined once we know the
    # branch (bootstrap / source / verify / local).
    _mode_for_telemetry = (
        "bootstrap" if args.bootstrap
        else "verify" if args.verify_imported
        else ("source" if (cross_instance and source_mode) else "local")
    )
    telemetry = Telemetry(dest_client, IMPORT_SCRIPT_VERSION, _mode_for_telemetry)
    # v15.11 IMPORT-DIAG — instantiate DiagSink BEFORE manifest poller starts.
    # Mode is whichever the CLI / env said. The sink will mirror events into
    # `telemetry` so the existing structured-data-container channel still gets
    # them; the wire shape is the new greppable JSON-per-line on stderr.
    global _DIAG  # consumed by helper sites further down without per-call threading
    _DIAG = DiagSink(IMPORT_SCRIPT_VERSION, SESSION_ID,
                     mode=args.diag_mode, telemetry=telemetry)
    try:
        script_path = Path(__file__)
        script_sha = hashlib.sha256(script_path.read_bytes()).hexdigest()
    except Exception:
        script_sha = "unknown"
    _DIAG.emit("startup", {
        "version": IMPORT_SCRIPT_VERSION,
        "mode": _mode_for_telemetry,
        "diag_mode": args.diag_mode,
        "session": SESSION_ID,
        "shepard_url": SHEPARD_URL,
        "source_url": SOURCE_SHEPARD_URL,
        "source_tapelaying_coll_id": SOURCE_TAPELAYING_COLL_ID,
        "source_bridgewelding_coll_id": SOURCE_BRIDGEWELDING_COLL_ID,
        "workers": args.workers,
        "max_dos_per_step": args.max_dos,
        "script_sha256": script_sha,
        "manifest_container_id": MANIFEST_CONTAINER_ID,
        "telemetry_ts_container_id": TELEMETRY_TS_CONTAINER_ID,
        "runlog_sd_container_id": RUNLOG_SD_CONTAINER_ID,
        "selfupdate_enabled": SELFUPDATE_ENABLED,
        "auth_mode": "Bearer" if SHEPARD_BEARER_TOKEN else "X-API-KEY",
        "dest_token_masked": DiagSink.mask_credential(
            SHEPARD_BEARER_TOKEN or SHEPARD_API_KEY),
        "dest_jwt_age_seconds": DiagSink.jwt_age_seconds(
            SHEPARD_BEARER_TOKEN or SHEPARD_API_KEY),
        "source_token_masked": DiagSink.mask_credential(SOURCE_SHEPARD_API_KEY),
        "source_jwt_age_seconds": DiagSink.jwt_age_seconds(SOURCE_SHEPARD_API_KEY),
        "pid": os.getpid(),
    })
    checkpoint = Checkpoint(CHECKPOINT_PATH)
    prior_state = checkpoint.load()
    if prior_state:
        last_from = prior_state.get("last_execv_from")
        last_to = prior_state.get("last_execv_to")
        if last_from and last_to and last_to == IMPORT_SCRIPT_VERSION:
            print(f"[v{IMPORT_SCRIPT_VERSION}] resumed after self-update v{last_from} → v{last_to}")
            telemetry.event("info", "process_started_after_execv",
                            from_version=last_from, to_version=last_to)
            telemetry.counter("redeploys_survived", 0)  # mark counter exists
        else:
            print(f"[v{IMPORT_SCRIPT_VERSION}] resumed from checkpoint (last saved {prior_state.get('last_saved_at')})")
            telemetry.event("info", "process_started_with_checkpoint",
                            checkpoint_age_s=None)
    else:
        telemetry.event("info", "process_started_fresh", mode=_mode_for_telemetry)
    checkpoint.update(
        version=IMPORT_SCRIPT_VERSION,
        mode=_mode_for_telemetry,
        started_at=datetime.datetime.now(datetime.timezone.utc).isoformat(),
        pid=os.getpid(),
    )
    updater = SelfUpdater(IMPORT_SCRIPT_VERSION, dest_client, telemetry)

    # v15.7 IMPORT-SIGHUP — ignore SIGHUP so terminal-disconnect / SSH-drop
    # doesn't kill the import. tmux/nohup remain the proper deployment shape,
    # but this catches the operator who forgot. SIGTERM + SIGINT remain the
    # canonical "stop the script" signals.
    try:
        import signal as _signal_v157
        _signal_v157.signal(_signal_v157.SIGHUP, _signal_v157.SIG_IGN)
        print(f"[v{IMPORT_SCRIPT_VERSION}] SIGHUP ignored — terminal disconnect won't kill the import")
    except (AttributeError, ValueError, OSError) as _exc:
        # SIGHUP not available (Windows) or not main thread — non-fatal.
        print(f"[v{IMPORT_SCRIPT_VERSION}] could not install SIGHUP handler: {_exc}")

    if SELFUPDATE_ENABLED:
        updater.start()
        print(f"[v{IMPORT_SCRIPT_VERSION}] self-updater started (heartbeat={HEARTBEAT_S}s, "
              f"manifest=fileContainer/{MANIFEST_CONTAINER_ID}/{MANIFEST_NAME_PREFIX}*.json)")
    else:
        print(f"[v{IMPORT_SCRIPT_VERSION}] self-updater DISABLED (MFFD_SELFUPDATE=0)")

    # v15.7 IMPORT-TLOOP — Background flusher with self-heal. The loop now
    # catches BaseException (incl. silent SystemExit / unraisable handler
    # results) and increments a `telemetry_loop_errors` counter. If the
    # loop dies entirely we re-spawn it once (a single self-heal so we
    # don't hide a real bug).
    _tel_loop_restarts = {"n": 0}
    def _telemetry_loop() -> None:
        nonlocal_n = _tel_loop_restarts  # bind for nested usage
        try:
            while not updater.stop_event.is_set():
                if updater.stop_event.wait(float(HEARTBEAT_S)):
                    return
                try:
                    telemetry.flush()
                except BaseException as exc:  # incl. SystemExit, KeyboardInterrupt
                    print(f"  [telemetry] background flush error: {exc!r}", flush=True)
                    try:
                        telemetry.counter("telemetry_flush_errors")
                    except Exception:
                        pass
        except BaseException as exc:
            print(f"  [telemetry] LOOP CRASHED: {exc!r}", flush=True)
            if nonlocal_n["n"] == 0:
                nonlocal_n["n"] = 1
                print(f"  [telemetry] self-heal: re-spawning loop", flush=True)
                threading.Thread(target=_telemetry_loop, daemon=True,
                                 name="TelemetryFlusher-respawn").start()

    _tel_thread = threading.Thread(target=_telemetry_loop, daemon=True,
                                   name="TelemetryFlusher")
    if SELFUPDATE_ENABLED:
        _tel_thread.start()

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

    # ── v15.9 MFFD-IMPORT-USER-CAPTURE — resolve source-side user identity ────
    # Run once at startup, after dest warmup is OK so the failure can be
    # event-logged into dest telemetry. Failure is NEVER fatal: the script
    # ships rich per-DO attribution when the lookup succeeds, and plain
    # `source_collection_id` when it doesn't. Per advisor: wrap in a
    # top-level try/except so a bug in the new code path can't kill the
    # 8 hour MFFD import.
    global SOURCE_USER_INFO
    if cross_instance and SOURCE_SHEPARD_URL and SOURCE_SHEPARD_API_KEY \
            and not args.bootstrap and not args.verify_imported:
        try:
            print(f"\n=== Source user identity capture (v15.9) ===")
            print(f"  source: {SOURCE_SHEPARD_URL}")
            _su_client = ShepardClient(SOURCE_SHEPARD_URL, SOURCE_SHEPARD_API_KEY, "")
            su_info = _su_client.resolve_self(api_key_for_jwt_decode=SOURCE_SHEPARD_API_KEY)
            if su_info:
                SOURCE_USER_INFO = su_info
                su_name = (
                    su_info.get("effectiveDisplayName")
                    or f"{su_info.get('firstName','')} {su_info.get('lastName','')}".strip()
                    or su_info.get("username", "?")
                )
                su_email = su_info.get("email") or "(no email)"
                print(f"  ✓ resolved : {su_name} <{su_email}>")
                print(f"    username : {su_info.get('username','?')}")
                # Apply X-Source-User-* headers to the DEST session so every
                # write carries them; ProvenanceCaptureFilter consumes
                # automatically (no per-call-site threading required).
                applied = dest_client.apply_source_user_headers(
                    su_info, source_instance_url=SOURCE_SHEPARD_URL
                )
                if applied:
                    print(f"  ✓ dest session now sends X-Source-User-* on every write")
                telemetry.event(
                    "info", "source_user_resolved",
                    username=su_info.get("username", ""),
                    display_name=su_name,
                    has_email=bool(su_info.get("email")),
                    source_instance=SOURCE_SHEPARD_URL,
                )
                telemetry.counter("source_user_resolved", 1.0)
            else:
                print(f"  [warn] source user lookup returned no result")
                print(f"  [warn] continuing without source_user_* enrichment "
                      f"(see PROV-USER-ENRICH backlog row)")
                telemetry.event(
                    "warn", "source_user_unresolved",
                    source_instance=SOURCE_SHEPARD_URL,
                    reason="resolve_self returned None",
                )
                telemetry.counter("source_user_resolved", 0.0)
        except Exception as _su_exc:
            # Per advisor: top-level guard. ANY failure (network, JSON, JWT
            # decode, attribute missing) drops back to the no-enrichment
            # path. Never crash the import on a metadata enrichment.
            print(f"  [warn] source user capture failed: {_su_exc!r}")
            print(f"  [warn] continuing without source_user_* enrichment")
            try:
                telemetry.event(
                    "warn", "source_user_capture_error",
                    exc=type(_su_exc).__name__,
                    msg=str(_su_exc)[:200],
                )
                telemetry.counter("source_user_resolved", 0.0)
            except Exception:
                pass

    # v15.3 IMPORT-Q7-VERIFY (in-script) — fetch ONE of each data type from
    # SOURCE before mass-importing. Catches the 0-byte-source failure mode
    # (task #145 Q7) BEFORE the script wastes time ferrying empty bytes to dest.
    # Exit code 8 = source-content-empty. Skip for bootstrap/verify/local modes.
    source_mode_ck = cross_instance and not args.bootstrap and not args.verify_imported
    if source_mode_ck and SOURCE_SHEPARD_URL and SOURCE_SHEPARD_API_KEY:
        src_probe = ShepardClient(SOURCE_SHEPARD_URL, SOURCE_SHEPARD_API_KEY, "")
        seed_cid = SOURCE_TAPELAYING_COLL_ID or SOURCE_BRIDGEWELDING_COLL_ID
        print(f"\n=== Source content probe (one file + one TS + one structured) ===")
        print(f"  source: {SOURCE_SHEPARD_URL}  seed-coll: {seed_cid}")
        probe_results = {"file": "skip", "ts": "skip", "sd": "skip"}
        if seed_cid:
            try:
                r = src_probe._get(
                    f"{SOURCE_SHEPARD_URL}/shepard/api/collections/{seed_cid}/dataObjects",
                    {"size": "50"},
                )
                dos = r.json() if r is not None else []
                # Walk DOs looking for one with each ref kind
                for d in dos[:50]:
                    if probe_results["file"] == "skip":
                        frs = src_probe._get(
                            f"{SOURCE_SHEPARD_URL}/shepard/api/collections/{seed_cid}/dataObjects/{d['id']}/fileReferences"
                        )
                        frlist = frs.json() if frs is not None else []
                        if frlist and frlist[0].get("fileOids"):
                            oid = frlist[0]["fileOids"][0]
                            head = src_probe._s.head(
                                f"{SOURCE_SHEPARD_URL}/shepard/api/collections/{seed_cid}/dataObjects/{d['id']}/fileReferences/{frlist[0]['id']}/payload/{oid}",
                                timeout=20,
                            )
                            sz = int(head.headers.get("Content-Length", "0") or 0)
                            probe_results["file"] = f"OK {sz}B" if sz > 0 else f"EMPTY (0 bytes)"
                    if probe_results["ts"] == "skip":
                        tsrs = src_probe._get(
                            f"{SOURCE_SHEPARD_URL}/shepard/api/collections/{seed_cid}/dataObjects/{d['id']}/timeseriesReferences"
                        )
                        tslist = tsrs.json() if tsrs is not None else []
                        if tslist and tslist[0].get("timeseries"):
                            # v15.3: don't trust channel-COUNT — actually fetch
                            # time/value data via /export (ROW format — see
                            # IMPORT-TS-ROW notes on export_ts). Channel
                            # listings can exist with zero points behind them;
                            # only the export endpoint proves real data.
                            ts_ref_id = tslist[0]["id"]
                            ch_n = len(tslist[0]["timeseries"])
                            csv = src_probe.export_ts(seed_cid, d["id"], ts_ref_id)
                            if csv is None:
                                probe_results["ts"] = f"EMPTY (ref {ts_ref_id}: 0 points across {ch_n} channels)"
                            else:
                                # ROW format: header line + one line per native
                                # data point (timestamp, measurement, device,
                                # location, symbolicName, field, value). Total
                                # lines therefore counts points across all
                                # channels, not rows-per-timestamp.
                                lines = [ln for ln in csv.decode("utf-8", "replace").splitlines() if ln.strip()]
                                data_points = max(0, len(lines) - 1)
                                if data_points == 0:
                                    probe_results["ts"] = f"EMPTY (ref {ts_ref_id}: header-only, 0 data points across {ch_n} channels)"
                                else:
                                    # Sample the first data row so the operator
                                    # sees an actual (timestamp,…,value) tuple.
                                    sample = lines[1][:120] if len(lines) > 1 else "?"
                                    probe_results["ts"] = f"OK ({ch_n} channels, {data_points} points; first row: {sample!r})"
                    if probe_results["sd"] == "skip":
                        sdrs = src_probe._get(
                            f"{SOURCE_SHEPARD_URL}/shepard/api/collections/{seed_cid}/dataObjects/{d['id']}/structuredDataReferences"
                        )
                        sdlist = sdrs.json() if sdrs is not None else []
                        if sdlist:
                            probe_results["sd"] = f"OK ({len(sdlist)} refs found)"
                    if all(v != "skip" for v in probe_results.values()):
                        break
            except Exception as exc:
                probe_results = {k: f"ERROR {type(exc).__name__}" for k in probe_results}
        for kind, res in probe_results.items():
            marker = "❌" if "EMPTY" in res or "ERROR" in res else "✓"
            print(f"  {marker} {kind:10s} : {res}")
        if any("EMPTY" in v for v in probe_results.values()):
            print("\n  ABORT: source returned empty bytes for one or more probes.", file=sys.stderr)
            print("  This matches task #145 Q7 — source has placeholder rows but no content.", file=sys.stderr)
            print("  The import would only ferry zero-byte files to dest. Stop here.", file=sys.stderr)
            tee.close()
            sys.stdout = tee._stdout  # type: ignore[attr-defined]
            sys.exit(8)

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
        # v15.3 IMPORT-NS1 — pre-import G1 snapshot is a decisive HUMAN
        # action per project_snapshot_boundaries.md. Script announces the
        # boundary; the human fires.
        pre_snap_app_id: str | None = None
        if coll_app_id:
            current_name = dest_client.get_collection_name(coll_id)
            pre_label = f"v15-import-pre-{SESSION_ID}@{current_name}"
            print(f"\n[snapshot] Reminder: pre-import boundary — fire when ready:")
            print(f"  POST /v2/collections/{coll_app_id}/snapshots  name={pre_label!r} label={pre_label!r}")
            print(f"  Script does not auto-fire snapshots (project_snapshot_boundaries.md).")

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

        # v15.3 IMPORT-NS1 — post-import G4 snapshot is a decisive HUMAN
        # action per project_snapshot_boundaries.md. Script announces the
        # boundary; the human fires. (The G5 lineage triples below still
        # need a snapshot appId to anchor on — left for the human to fill in.)
        post_snap_app_id: str | None = None
        if coll_app_id:
            current_name = dest_client.get_collection_name(coll_id)
            label = f"v15-as-imported-{SESSION_ID}@{current_name}"
            print(f"\n[snapshot] Reminder: post-import boundary — fire when ready:")
            print(f"  POST /v2/collections/{coll_app_id}/snapshots  name={label!r} label={label!r}")
            print(f"  Script does not auto-fire snapshots (project_snapshot_boundaries.md).")

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
        # v15.4 IMPORT-T1/SU1 — graceful observability shutdown:
        #   1) Final telemetry flush so the operator sees the closing state.
        #   2) Stop the heartbeat thread.
        #   3) If a self-update is pending, apply it BEFORE releasing the
        #      lock + closing the tee — os.execv replaces the process so
        #      no Python finally-handlers run after; we MUST persist the
        #      checkpoint above this point, which we already do per-batch.
        # v15.11 IMPORT-DIAG — emit one final summary tick + shutdown event so
        # an operator looking at `tmux capture-pane -p | tail -100` after a
        # clean exit sees the run rollup immediately.
        try:
            final_summary = _DIAG.summary_tick() if _DIAG is not None else {}
        except Exception:
            final_summary = {}
        try:
            _diag_emit("shutdown", {
                "update_pending": bool(updater.update_event.is_set()),
                "pending_version": updater.pending_version or "",
                "final_summary": final_summary,
                "exit_reason": "graceful",
            })
        except Exception:
            pass
        try:
            telemetry.event("info", "process_shutting_down",
                            update_pending=bool(updater.update_event.is_set()))
            telemetry.flush()
        except Exception:
            pass
        updater.stop()
        if updater.update_event.is_set() and updater.pending_path:
            print(f"\n[v{IMPORT_SCRIPT_VERSION}] applying pending self-update → v{updater.pending_version}…", flush=True)
            apply_pending_update(updater, telemetry, checkpoint)
            # If apply_pending_update returns, execv failed — fall through
            # and exit normally so the wrapper script (if any) can restart.
        lock.release()
        tee.close()
        sys.stdout = tee._stdout  # type: ignore[attr-defined]
        print(f"\nLog written to: {log_path}")


if __name__ == "__main__":
    main()
