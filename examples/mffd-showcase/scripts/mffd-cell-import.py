#!/usr/bin/env python3
r"""
mffd-cell-import.py — import the MFFD "cell geometry" (W5) wave into a freshly-reset
live Shepard instance, from LOCAL data only.

This is the SMALL wave after spot-welding (W8a, ``mffd-spotwelding-import.py``),
NDT-thermography (W6, ``mffd-thermography-import.py``), and bridge-welding (W3,
``mffd-bridge-replay.py``). It reuses the proven idempotency discipline from those
scripts: the ``Client`` (retry-forever backoff) and ``load_api_key`` helpers are
imported directly from the bridge script (which is NOT modified), and the
reuse-before-create-on-a-stable-source-id pattern mirrors the spot-welding and
thermography importers — both rely on "upload an artefact, the server-side
``FileParserPlugin`` enriches it" rather than a manifest replay. This is the
closest analogue family.

Source (LOCAL, read-only)
-------------------------
``/mnt/pve/unas/dump/dataset/RoboDK Cell Geometry/MFZ.rdk`` — a single 12 MB
RoboDK station file describing the MFFD AFP cell ("MFZ" — the
Multifunktionsrumpfdemonstrator-Fertigungszelle). Bytes are read only from local
disk; nothing is ever fetched from a remote backend. The sibling ``*.url`` link
files and ``Gemini-Temporary Chat.md`` are NOT data and are ignored.

How rdk ingestion works in the CURRENT codebase
-----------------------------------------------
Same shape as svdx (W8a) and thermography (W6): there is NO
``shepard-importer --source rdk-urdf`` CLI (the README's example is fictional) and
NO dedicated rdk ingest REST endpoint. Uploading the ``.rdk`` as a singleton
FileReference (multipart ``POST /v2/files?parentDataObjectAppId=…&name=…``)
automatically triggers the ``fileformat-robotics`` ``RdkTextScrapeParser``
server-side: ``SingletonFileReferenceService.createSingleton`` calls
``parserRegistry.anyAccepts(null, filename)`` (true for ``.rdk``), buffers the
bytes, persists them, then ``parserRegistry.runAll(...)`` runs
``RdkTextScrapeParser.parse``, which decompresses the zlib payload, walks the
length-prefixed UTF-16LE string records, and writes ``urn:shepard:rdk:*`` semantic
annotations onto the new FileReference's appId (NOT the DataObject — the parser's
subject preference is the FileReference). The eight predicates are:
``urn:shepard:rdk:{appVersion,platform,programSource,cadRef,stepRef,apiEndpoint,
robotController,companionSpatialAnalyzer}`` (the last only when an ``.xit``/``.xit64``
sibling exists in the same container — none here). The parser is best-effort and
fire-and-forget; it never blocks the upload.

KNOWN GAP: ``POST /v2/annotations`` with ``subjectKind=Reference`` returns a
permanent 403 even for instance-admin. So EVERY annotation THIS importer writes
goes on the DATAOBJECT, never on the FileReference. (The parser's own writes go
onto the FileReference appId — that's a server-side ``AnnotationWriter`` callback,
unaffected by the REST 403.)

What this importer creates
--------------------------
One DataObject named ``MFZ`` (the AFP cell) in the ``mffd-cell`` collection, with
``MFZ.rdk`` uploaded as a singleton FileReference (FR1b, auto-parsed). On the
DataObject it writes (all on the DataObject):
  - ``urn:shepard:source:provenance = RoboDK Cell Geometry/MFZ.rdk``
  - ``urn:shepard:source:rdk-file   = MFZ.rdk``  (stable cross-run key)

Idempotency
-----------
The ``rdk-file`` basename is the stable source id. A ``LiveIndex`` built at startup
maps that literal → DO appId (via the source-id annotation, intersected with the
LIVE DataObject set so a soft-deleted tombstone never gets reused), DO name →
appId, and the DO's existing file-reference labels. Reuse-before-create: a DO
present in the index is reused, never re-created; a file ref already attached
(confirmed by a fresh re-read to defeat the by-data-object projection's lag) is
skipped; annotation writes are diff-based. Re-runs converge to exactly 1 DO + 1
file ref and create ZERO new graph nodes.

Completeness (non-negotiable)
-----------------------------
The thermography wave hit silent 0-byte uploads (10/744 multipart POSTs returned
201 but stored an empty payload). So after upload we VERIFY the stored content
length matches the source size and re-upload (deleting the bad ref) until it does.
Transient transport/5xx errors retry-forever with backoff (the shared ``Client``).

Usage
-----
    python3 mffd-cell-import.py --dry-run
    python3 mffd-cell-import.py --verbose
    python3 mffd-cell-import.py            # full run, the single MFZ.rdk file
"""
from __future__ import annotations

import argparse
import hashlib
import os
import sys

try:
    import requests
except ImportError:
    sys.stderr.write("ERROR: this script needs `requests`.  pip install requests\n")
    sys.exit(2)

# Reuse the proven Client (retry-forever backoff) + key loader from the bridge
# replay script, which lives alongside this one. We do NOT modify it.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from importlib import import_module

_bridge = import_module("mffd-bridge-replay")
Client = _bridge.Client  # type: ignore[attr-defined]
load_api_key = _bridge.load_api_key  # type: ignore[attr-defined]

# ── defaults ──────────────────────────────────────────────────────────────────
DEFAULT_HOST = "https://shepard-api.nuclide.systems"
DEFAULT_SOURCE = "/mnt/pve/unas/dump/dataset/RoboDK Cell Geometry/MFZ.rdk"
DEFAULT_KEYFILE = "/root/.claude/uploads/mffd-import-key-2026-06-17.txt"
TARGET_COLLECTION_APPID = "019ed455-68d9-7e00-8aa0-0191e99fc117"
# provenance paths are dataset-relative from the dataset root
DATASET_ROOT_MARKER = "RoboDK Cell Geometry"
# The DataObject name (the AFP cell), distinct from the file label.
DATA_OBJECT_NAME = "MFZ"

PROVENANCE_PREDICATE = "urn:shepard:source:provenance"
# Stable cross-run source id — the cell-geometry analogue of the bridge wave's
# urn:shepard:source:cube3-do-id, the spot-welding wave's source:svdx-file, and
# the thermography wave's source:otvis-file.
SOURCE_RDK_FILE_PREDICATE = "urn:shepard:source:rdk-file"

# Predicate prefix the server-side fileformat-robotics parser emits onto the
# FileReference — used only for the verification read-back / report.
RDK_PARSER_PREFIX = "urn:shepard:rdk:"


def sha256_file(path: str) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def existing_file_ref(client: Client, do_appid: str, label: str):
    """Return the FileReference dict for ``label`` already attached to the DO, or
    None. Used for skip-if-present idempotency."""
    try:
        lst = client.get_json(f"/v2/files/by-data-object/{do_appid}")
    except RuntimeError:
        return None
    if not isinstance(lst, list):
        return None
    for x in lst:
        if x.get("name") == label and not x.get("deleted"):
            return x
    return None


def content_length(client: Client, ref_appid: str) -> int:
    """Stored byte length of a FileReference's content (best effort; -1 on
    failure so the caller treats it as a mismatch and re-uploads)."""
    try:
        resp = client.request("GET", f"/v2/files/{ref_appid}/content",
                              expect=(200, 206), stream=True)
        cl = resp.headers.get("content-length")
        if cl is not None:
            n = int(cl)
            resp.close()
            return n
        data = resp.content
        return len(data)
    except (RuntimeError, ValueError, requests.RequestException):
        return -1


def find_live_do(client: Client, collection_appid: str, source_basename: str,
                 do_name: str, verbose: bool = False):
    """Idempotency-against-live: find an existing DataObject for this wave by its
    stable source-id annotation (intersected with the LIVE DO set), then by name.
    Returns the DO appId or None."""
    # Build the set of live (non-deleted) DOs in the collection.
    live_by_name: dict[str, str] = {}
    live_appids: set[str] = set()
    page = 0
    while True:
        batch = client.get_json(
            f"/v2/collections/{collection_appid}/data-objects?page={page}&pageSize=500")
        if not batch:
            break
        for d in batch:
            live_by_name[d["name"]] = d["appId"]
            live_appids.add(d["appId"])
        page += 1

    # Source-id annotations: only honour those whose subject is a LIVE DO (a
    # soft-deleted tombstone would otherwise 404 on the subsequent file upload).
    page = 0
    stale = 0
    while True:
        batch = client.get_json(
            f"/v2/annotations?predicateIri={requests.utils.quote(SOURCE_RDK_FILE_PREDICATE)}"
            f"&page={page}&pageSize=200")
        if not batch:
            break
        for a in batch:
            lit = a.get("objectLiteral")
            subj = a.get("subjectAppId")
            if lit == source_basename and subj:
                if subj in live_appids:
                    if verbose:
                        print(f"  live-index   : reuse DO {subj} via source-id "
                              f"{source_basename}", file=sys.stderr, flush=True)
                    return subj
                stale += 1
        page += 1
    if stale and verbose:
        print(f"  live-index   : {stale} stale/tombstone source-id annotation(s) dropped",
              file=sys.stderr, flush=True)

    hit = live_by_name.get(do_name)
    if hit and verbose:
        print(f"  live-index   : reuse DO {hit} via name {do_name}",
              file=sys.stderr, flush=True)
    return hit


def do_existing_predicates(client: Client, do_appid: str) -> set:
    """Set of annotation predicate IRIs already present on a DataObject (so a
    re-run writes only the missing ones)."""
    preds: set = set()
    try:
        page = 0
        while True:
            batch = client.get_json(
                f"/v2/annotations?subjectAppId={do_appid}&page={page}&pageSize=200")
            if not batch:
                break
            for a in batch:
                p = a.get("predicateIri")
                if p:
                    preds.add(p)
            page += 1
    except RuntimeError:
        pass
    return preds


def annotate(client: Client, do_appid: str, predicate: str, value: str,
             dry: bool, verbose: bool) -> bool:
    """Write one annotation ON THE DATAOBJECT (Reference annotations 403)."""
    if dry:
        return True
    body = {
        "subjectAppId": do_appid,
        "subjectKind": "DataObject",
        "predicateIri": predicate,
        "objectLiteral": value,
        "sourceMode": "ai",
    }
    try:
        # retry_on_403: the :Permissions wiring for a freshly-created DataObject
        # can briefly lag, so an annotation POST right after the DO create
        # occasionally 403s. Bounded-retry rather than dropping the annotation.
        client.post_json("/v2/annotations", body, retry_on_403=True)
        return True
    except RuntimeError as e:
        if verbose:
            print(f"  [annotation WARN] DO {do_appid} {predicate}: {e}",
                  file=sys.stderr, flush=True)
        return False


def parser_annotation_summary(client: Client, ref_appid: str):
    """Read back the parser-emitted urn:shepard:rdk:* annotations on the
    FileReference for verification + the report. Returns (count, sample dict
    predicate->value)."""
    found: dict[str, str] = {}
    try:
        page = 0
        while True:
            batch = client.get_json(
                f"/v2/annotations?subjectAppId={ref_appid}&page={page}&pageSize=200")
            if not batch:
                break
            for a in batch:
                p = a.get("predicateIri") or ""
                if p.startswith(RDK_PARSER_PREFIX):
                    found.setdefault(p, a.get("objectLiteral") or "")
            page += 1
    except RuntimeError:
        pass
    return len(found), found


def main():
    ap = argparse.ArgumentParser(
        description="Import the MFFD cell-geometry (.rdk, W5) wave into Shepard.")
    ap.add_argument("--source", default=DEFAULT_SOURCE,
                    help="path to the MFZ.rdk station file")
    ap.add_argument("--host", default=DEFAULT_HOST)
    ap.add_argument("--api-key", default=None)
    ap.add_argument("--keyfile", default=DEFAULT_KEYFILE)
    ap.add_argument("--collection-appid", default=TARGET_COLLECTION_APPID)
    ap.add_argument("--limit", type=int, default=None,
                    help="only first N files (this wave has one; --limit 0 = no-op)")
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--verbose", action="store_true")
    args = ap.parse_args()

    api_key = load_api_key(args.api_key, args.keyfile)
    client = Client(args.host, api_key, verbose=args.verbose)

    if not os.path.isfile(args.source):
        sys.stderr.write(f"ERROR: source file not found: {args.source}\n")
        sys.exit(1)
    basename = os.path.basename(args.source)
    size = os.path.getsize(args.source)
    relpath = f"{DATASET_ROOT_MARKER}/{basename}"

    print(f"source        : {args.source}")
    print(f"host          : {args.host}")
    print(f"collection    : {args.collection_appid}")
    print(f"mode          : {'DRY-RUN' if args.dry_run else 'LIVE'}")
    print(f"file          : {basename}  ({size} bytes, {size/1e6:.2f} MB)")

    me = client.get_json("/v2/users/me")
    print(f"auth ok       : {me.get('effectiveDisplayName')} ({me.get('appId')})")
    col = client.get_json(f"/v2/collections/{args.collection_appid}")
    print(f"collection ok : {col.get('name')}")

    # --limit semantics: this wave is a single file; --limit 0 skips it entirely
    # (parity with the multi-file waves where --limit N truncates the list).
    if args.limit is not None and args.limit <= 0:
        print("\n--limit 0 → nothing to import.")
        return

    if args.dry_run:
        print("\n── DRY-RUN plan ──")
        print(f"  would ensure 1 DataObject named '{DATA_OBJECT_NAME}' in {args.collection_appid}")
        print(f"  would upload singleton FileReference '{basename}' ({size} bytes)")
        print(f"    → server-side fileformat-robotics parser emits urn:shepard:rdk:* "
              f"annotations on the FileReference")
        print(f"  would set on the DataObject:")
        print(f"    {PROVENANCE_PREDICATE} = {relpath}")
        print(f"    {SOURCE_RDK_FILE_PREDICATE} = {basename}")
        print("  (idempotent: a re-run reuses the existing DO + file ref, 0 new nodes)")
        return

    # ── 1. DataObject (reuse-before-create) ───────────────────────────────────
    do_appid = find_live_do(client, args.collection_appid, basename,
                            DATA_OBJECT_NAME, verbose=args.verbose)
    do_created = False
    if do_appid:
        print(f"DataObject    : reused {do_appid}")
    else:
        created = client.post_json(
            f"/v2/collections/{args.collection_appid}/data-objects",
            {"name": DATA_OBJECT_NAME})
        do_appid = created["appId"]
        do_created = True
        print(f"DataObject    : created {do_appid}")

    # ── 2. provenance + stable source-id annotations on the DataObject ────────
    existing_preds = set() if do_created else do_existing_predicates(client, do_appid)
    anns_written = 0
    for pred, val in ((PROVENANCE_PREDICATE, relpath),
                      (SOURCE_RDK_FILE_PREDICATE, basename)):
        if pred in existing_preds:
            continue
        if annotate(client, do_appid, pred, val, dry=False, verbose=args.verbose):
            anns_written += 1
    print(f"DO annotations: {anns_written} written ({2 - anns_written} already present)")

    # ── 3. singleton FileReference (FR1b) — skip-if-present, else upload + verify ─
    ref = existing_file_ref(client, do_appid, basename)
    if ref is None:
        # confirming re-read defeats the by-data-object projection lag
        ref = existing_file_ref(client, do_appid, basename)
    ref_reused = ref is not None
    ref_appid = ref.get("appId") if ref else None

    if ref_reused:
        print(f"FileReference : reused {ref_appid}")
    else:
        attempt = 0
        while True:
            attempt += 1
            with open(args.source, "rb") as fh:
                resp = client.request(
                    "POST",
                    f"/v2/files?name={requests.utils.quote(basename)}"
                    f"&parentDataObjectAppId={do_appid}",
                    files={"file": (basename, fh, "application/octet-stream")},
                    expect=(200, 201),
                    retry_on_403=True,  # :Permissions wiring lag post-DO-create
                )
            new_ref = resp.json()["appId"]
            stored = content_length(client, new_ref)
            if stored == size or size == 0:
                ref_appid = new_ref
                break
            # truncated/empty store — delete the bad ref and retry (the
            # thermography wave's silent 0-byte upload guard).
            print(f"  [short-upload] {basename}: stored {stored}/{size} bytes "
                  f"(attempt {attempt}) — deleting + re-uploading",
                  file=sys.stderr, flush=True)
            try:
                client.request("DELETE", f"/v2/files/{new_ref}",
                               expect=(200, 204), retry_on_403=True)
            except RuntimeError:
                pass
        print(f"FileReference : created {ref_appid}")

    # ── 4. verify stored bytes match disk (size + sha256 round-trip) ──────────
    stored = content_length(client, ref_appid)
    byte_match = (stored == size)
    print(f"stored bytes  : {stored} (disk {size}) → {'MATCH' if byte_match else 'MISMATCH'}")

    disk_sha = sha256_file(args.source)
    try:
        dl = client.request("GET", f"/v2/files/{ref_appid}/content",
                            expect=(200, 206)).content
        dl_sha = hashlib.sha256(dl).hexdigest()
        sha_match = (dl_sha == disk_sha)
    except RuntimeError:
        dl_sha, sha_match = "(read failed)", False
    print(f"sha256        : disk={disk_sha[:16]}…  stored={dl_sha[:16] if dl_sha else dl_sha}…"
          f" → {'MATCH' if sha_match else 'MISMATCH'}")

    # ── 5. report the parser-emitted rdk annotation count + sample ────────────
    rdk_count, rdk_sample = parser_annotation_summary(client, ref_appid)
    print(f"\n── summary (live run) ──")
    print(f"  DataObject              : {DATA_OBJECT_NAME} ({do_appid}) "
          f"[{'created' if do_created else 'reused'}]")
    print(f"  FileReference           : {basename} ({ref_appid}) "
          f"[{'reused' if ref_reused else 'created'}]")
    print(f"  stored bytes            : {stored} / {size}  ({'OK' if byte_match else 'BAD'})")
    print(f"  sha256 round-trip       : {'OK' if sha_match else 'BAD'}")
    print(f"  DO provenance/source-id annotations written this run : {anns_written}")
    print(f"  parser rdk annotations (on FileReference)            : {rdk_count}")
    for p, v in sorted(rdk_sample.items()):
        print(f"    {p} = {v}")
    if rdk_count == 0:
        print("  NOTE: 0 parser annotations — the fileformat-robotics parser may be "
              "disabled, or this run reused a pre-existing ref uploaded before the "
              "plugin was active. Re-upload (delete the ref + re-run) to re-trigger.",
              file=sys.stderr)

    if not (byte_match and sha_match):
        sys.exit(1)


if __name__ == "__main__":
    main()
