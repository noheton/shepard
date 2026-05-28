---
stage: feedback-implemented
last-stage-change: 2026-05-28
audience: [contributor, admin]
task: 145
backlog: IMPORT-Q7-FIX
branch: 145-fileref-truncation-fix
---

# Q7 / task #145 — fileRef parser bug (BUG-FILEREF-TRUNCATION)

**Date:** 2026-05-28
**Surface:** `examples/mffd-showcase/scripts/mffd-import-v15.py`
**Root cause:** two compounding bugs, both in the source → dest file-payload
hand-off path. Fixed in branch `145-fileref-truncation-fix`.

## What I found

The dispatch asked me to disprove H1-H5 in priority order. Two bugs co-fire,
which the dispatch's H-list correctly anticipated: **H2 is the dominant
cause** (a metadata blob was uploaded as file bytes for the multi-OID case),
and **H3 is a real co-conspirator** (silent stream truncation under the
`requests` library's well-documented zero-byte / short-read failure mode).

### Bug 1 (H2 variant — the dominant cause) — forgotten `oid` keyword

The worker fan-out body called the download function with **5 positional
args**, dropping the `oid` keyword:

```python
# examples/mffd-showcase/scripts/mffd-import-v15.py:4170 (pre-fix)
ok = _src.download_file_ref(
    src_coll_id, src_do.do_id, fref.fref_id, tmp_file, fref.size
)
```

`download_file_ref` signature is
`(coll_id, do_id, fref_id, dest, size_hint=0, oid="", ...)`.
With `oid=""`, the function falls through to the bare v5 endpoint:

```
GET /shepard/api/collections/{c}/dataObjects/{d}/fileReferences/{f}/payload
```

Per the v5.4.0 OpenAPI spec
(`backend/src/test/resources/fixtures/v5/openapi-5.4.0.json`, L3475-3540)
and the live backend handler
(`backend/src/main/java/de/dlr/shepard/context/references/file/endpoints/FileReferenceRest.java:226-255`,
`@Operation(description = "Get associated files")`), this endpoint returns
**`application/json` containing an array of `ShepardFile` metadata** — not
the file bytes. So the importer:

1. Wrote a JSON metadata blob to a local tmp path.
2. Read the JSON bytes back.
3. Uploaded the JSON bytes to dest via `upload_file` (v2 multipart).
4. Dest created a `FileReference` row pointing at a payload containing
   JSON-as-binary instead of the real file content.

`_fetch_file_refs` correctly expanded multi-OID bundles into one `FileRef`
per `(fref_id, oid)` pair (lines 1107-1112), but that `oid` was never
threaded through to the caller. The expansion was effectively wasted work.

### Bug 2 (H3) — silent truncation in `download_file_ref`

Independent of bug 1, the streaming download path had no integrity check:

```python
# examples/mffd-showcase/scripts/mffd-import-v15.py:1268-1290 (pre-fix)
r = self._s.get(url, stream=True, timeout=600)
if not r.ok:
    return False
total = int(r.headers.get("Content-Length", size_hint or 0))   # ← used ONLY for tqdm
with dest.open("wb") as fh:
    for chunk in r.iter_content(chunk_size=65536):
        fh.write(chunk)
return True                                                    # ← no size verification
```

`requests` / urllib3 with `stream=True` will **silently exit `iter_content()`
on a mid-stream connection close** without raising
(psf/requests#2275, #4227, #6512; Petr Zemek, "On Incomplete HTTP Reads and
the Requests Library In Python", 2018). The function returned `True` for
short reads and zero-byte responses with a non-zero advertised
Content-Length. Under workers=8 and a flaky DLR-cube → nuclide hop, this
fires often enough to lose ~8462 DOs of payload integrity.

### Evidence chain

1. **Source code (script side):** `examples/mffd-showcase/scripts/mffd-import-v15.py:4170-4173` (call site) and `:1268-1290` (download function).
2. **Source code (backend side, v5 wire shape):** `FileReferenceRest.getFiles` returns `Response.ok(ret).build()` with `@Schema(type = SchemaType.ARRAY, implementation = ShepardFile.class)` — JSON, not bytes. `FileReferenceRest.getFilePayload(... @PathParam("oid"))` is the only endpoint that returns `MediaType.APPLICATION_OCTET_STREAM`.
3. **OpenAPI:** `backend/src/test/resources/fixtures/v5/openapi-5.4.0.json:1896-1952` confirms `FileReference.fileOids: array<string>` (min 1) — multi-OID is the schema-required shape, not a fringe case.
4. **External docs:** `requests` issues #2275, #4227, #6512; Petr Zemek's `response.raw.tell()` pattern from 2018.

### Why the completeness check didn't catch it earlier

`mffd-completeness-check.py` reads each DataObject's `fileReferences` array
and **counts the IDs**, then compares src-count vs. dest-count. A DO with
3 source FileReferences and 3 dest FileReferences passes — the byte-level
corruption is invisible to count-based diffing. That's why the bug
survived 8462 broken-DO repros: every metric the importer / verifier
exposed said "complete." The fix's structured `file_truncated` diag event
gives a future operator the grep target they didn't have.

## Opportunities

- **Strengthen completeness check** with optional `HEAD` probes against
  `/payload/{oid}` for a sampled subset (filed as IMPORT-Q7-VERIFY already
  for the cube-side smoke probe).
- **Adopt `enforce_content_length=True`** when the project moves to
  `requests` v3.x (still beta as of 2026-05; the manual guard remains
  necessary until then).
- **SHA-256 round-trip** in a future v17 importer (source-side `HEAD`
  with checksum header → upload → compare). The fix lands the byte-count
  guard as a minimum-viable defense; checksum is the gold standard.

## Ideas

- **Default-on truncation detection for ALL streamed-download call sites**
  (`download_structured`, `export_ts`, presigned downloads). The same
  `requests` issue applies. A shared `_streamed_get_to_path` helper would
  capture the pattern once.
- **Diag-channel watchdog**: emit a daily count of `file_truncated` events
  to the admin UI so a flaky source-side network surfaces operationally,
  not buried in 2 GB of import logs.

## Real-world impact

- **8462 DOs lost payload integrity** on the original DLR-cube → nuclide
  ingest. Re-ingest is gated on a new cube JWT + the next v16 run.
- Multi-OID FileReferences are the schema-required shape (`minItems: 1`),
  so this hit **every** MFFD-Dropbox attachment, not a corner case.
- Confluence-export sources (the MFFD-Dropbox provenance) sometimes
  serve chunked transfer encoding without Content-Length. The fix
  preserves the `file_unverified` accept-and-flag path for those —
  failing chunked responses would re-create the completeness problem in
  reverse.

## Gaps & blockers

- **Validation deferred.** The fix can't be exercised against live data:
  no DLR-cube JWT issued, and the live MFFD-Dropbox collection (661923)
  was wiped in today's full-instance reset. Validation gate: next v16
  import.
- **H5 (worker race) NOT eliminated.** I focused on H2 + H3 because
  they're demonstrably mechanical. A subset of the 8462 DOs may also be
  victim of the workers=8 409-and-cleanup race the dispatch flagged.
  Pre-existing v16.4 commit (`6d5338270`) addresses upload retries +
  lowers worker count, which dampens but doesn't eliminate this. Out
  of scope for this dispatch; flag for the post-validation forensic pass
  if the next v16 import shows residual count mismatches.
- **No checksum.** Even with the byte-count guard, a same-length-but-
  wrong-bytes corruption (extremely unlikely over HTTPS) would survive.
  Filed as a follow-up.

## What surprised me

1. **The bug wasn't a parser bug.** The dispatch framed it as "the
   fileRef parser." The parser (`_fetch_file_refs`) was correct —
   `fileOids`, `name`, `id` all read fine. The defect was downstream:
   the parser's output wasn't propagated through a function call. A
   forgotten keyword arg masquerading as a parser bug. Worth a note in
   future agent dispatches that "parser bug" framing can lead an agent
   astray for the first 30 minutes.

2. **The bare `/payload` endpoint returning JSON-not-bytes is, in
   hindsight, a v5 wire-shape footgun.** Two endpoints under the same
   `/payload` stem, distinguished only by trailing `/{oid}`, with
   completely different media types. The v6 successor surface should
   either split them (e.g. `/payload-list` vs `/payload/{oid}`) or
   content-negotiate on `Accept`. Filed as an idea, not in scope here.

3. **`requests` has had this open since 2015.** psf/requests#2275 was
   filed in March 2015 ("Content-Length header not checked by requests
   if not enough data is sent") and is still open as of 2026-05 with the
   final answer being "we'll fix it in v3" (which is still beta). Every
   serious downloader-in-Python project hits this eventually. The
   defensive pattern is canonical at this point — Zemek's blog post is
   one of dozens of independent reinventions.

## What ships

| Surface | File | Change |
|---|---|---|
| Fix (worker call site) | `examples/mffd-showcase/scripts/mffd-import-v15.py:4170-4173` | `oid=fref.oid, corr=corr_id` keyword args added |
| Fix (download integrity guard) | `examples/mffd-showcase/scripts/mffd-import-v15.py:1245-1335` | byte-count verification against Content-Length; unlink partials; structured diag emit |
| Diagnostic surface | same | `file_truncated` + `file_unverified` events for IMPORT-DBG1 grep |
| Regression test | `examples/mffd-showcase/scripts/test_fileref_truncation.py` | 10 cases; all pass; truncation cases verified to FAIL pre-fix |
| Backlog row | `aidocs/16-dispatcher-backlog.md:813` (`IMPORT-Q7-FIX`) | status=**done**, branch + findings citations |
| Worklog | `RESUME.md` Hot-artefacts | new row noting fix-in-code, validation deferred |

## Validation plan

1. Operator obtains fresh DLR-cube JWT.
2. Operator runs the next v16 import against MFFD-Dropbox (or any v5
   source with multi-OID FileReferences).
3. Operator runs `mffd-completeness-check.py` afterwards.
4. Operator greps the import log for `kind:file_truncated` events — count
   should be zero, or each event should correspond to a real network
   incident (and the operator's resume run should pick those up).
5. Spot-check: pick 5 dest DOs at random, download one FileReference each
   via `/v2/files/{appId}/payload`, compare bytes against source.
