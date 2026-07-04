# feat-fair-publish

Showcases **FAIR metadata + completeness score + cite-this-dataset**. A Collection
is stamped with an SPDX `license` (CC-BY-4.0), `accessRights` (OPEN), a rich
description, a creator ORCID (`PATCH /v2/users/me`), a `schema:keywords` keyword
annotation, a `dcterms:bibliographicCitation` "cite this dataset" string, and a
DataObject — reaching **95/100** on the metadata-completeness widget
(`frontend/utils/metadataCompleteness.ts`); only the lab-journal row (5 pts) is
left for the showcase. Publishing to Helmholtz Unhide is deliberately NOT triggered
(synthetic data must not be harvested).

> **Findings (RESEED-FIND):** `GET /v2/annotations?subjectAppId=<Collection>`
> returns 403 even to the creator under API-key auth (the POSTs succeed); and
> `GET /v2/admin/unhide/config` 500s on this instance. Both are logged as SKIPs.

```bash
/tmp/reseed-venv/bin/python seed.py --reset
```

Synthetic data only — no real DLR/MFFD measurement data.
