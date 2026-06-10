# feat-welding-svdx

Synthetic MFFD stringer-welding run: a welding `TimeseriesReference` (converter
power, weld force, nip-point temperature — the TwinCAT-Scope channels a `.svdx`
recording produces) plus a companion GoPro process `VideoReference`. Exercises
`fileformat-svdx` (welding channels) + `video`. Synthetic — no real DLR/MFFD IP.

Run: `python seed.py --reset` (needs `API_KEY` + `BACKEND_URL`, or `--apikey`/`--host`).

Design note: the real svdx surface is `POST /v2/svdx/ingest`, which ingests the
TwinCAT-Scope-Export-Tool `.csv` sibling of a real `.svdx` (tab-delimited, FILETIME
timestamps, per-channel header blocks). Faking a byte-valid synthetic pair is
fragile, so per the showcase spec this seed takes the TimeseriesReference fallback
(real, queryable welding channels). The video upload + download work live; only
the sister `GET /v2/admin/video/config` admin endpoint 500s (`VideoConfig` not
OGM-registered — RESEED-FIND, doesn't affect this seed). The 5-tuple fields reject
`Space/Comma/Point/Slash`, so TwinCAT NetID/SymbolName dots are rendered with `_`.
