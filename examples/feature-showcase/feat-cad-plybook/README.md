# feat-cad-plybook

Synthetic ISO 10303-21 AP242 STEP plybook panel proving the `fileformat-cad`
plugin's `StepP21Parser`. Uploading the synthetic `.step` via `POST /v2/files`
auto-emits 7 `urn:shepard:cad:*` annotations (format, step_schema, product_name,
application, author, organisation, created_at). Synthetic — no real DLR/MFFD IP.

Run: `python seed.py --reset` (needs `API_KEY` + `BACKEND_URL`, or `--apikey`/`--host`).

What's verified live: the FileParser dispatch on upload IS wired (the recovered
`.pyc` predates this and called it a gap). Verify the emitted annotations via
Cypher: `MATCH (a:SemanticAnnotation {subjectAppId:'<fileRefAppId>'})
RETURN a.propertyIRI, a.valueName`.

RESEED-FIND: `StepP21Parser` anchors annotations on the FileReference subject, but
`GET /v2/annotations?subjectAppId=<fileRef>` returns 403 for the file's own owner —
FileReference-subject annotations have no REST read path. Fix: grant the owner Read
on its own file's annotations, or anchor CAD annotations on the parent DataObject
(as the thermography filename-grid hook does).
