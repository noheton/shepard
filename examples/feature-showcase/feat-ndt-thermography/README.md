# feat-ndt-thermography

Synthetic Edevis OTvis NDT scan (`S4_M13_L18_F4.OTvis`) proving the
`fileformat-thermography` plugin's S/M/L/F grid annotation set and the OTvis
decode REST surface. Synthetic — no real DLR/MFFD IP.

Run: `python seed.py --reset` (needs `API_KEY` + `BACKEND_URL`, or `--apikey`/`--host`).

What's verified live: uploading `S4_M13_L18_F4.OTvis` via `POST /v2/files`
auto-emits the four `urn:shepard:mffd:{section,module,layer,frame}` grid
annotations on the parent DataObject (the OTvisParser filename hook IS wired —
the recovered `.pyc` predates this and called it a gap). The decode REST
`GET /v2/thermography/otvis/{appId}/frames` is live (returns 422 on the synthetic
non-tar stub, as expected). The `urn:shepard:thermography:*` manifest annotations
additionally need a real OTvis tar whose `content.xml` parses.
