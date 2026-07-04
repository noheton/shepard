# feat-unified-refs-containers

Proves **V2CONV-A2/A3**: the unified, appId-keyed `/v2/references?kind=` and
`/v2/containers?kind=` CRUD surfaces. Creates one container of each in-tree kind
(file / timeseries / structured-data), GETs each back by appId, lists them; then
creates a `kind=uri` reference and a `kind=file` singleton (via the multipart
`POST /v2/files` entry the unified path points to), lists and GETs each.
Synthetic payloads only.

```bash
/tmp/reseed-venv/bin/python examples/feature-showcase/feat-unified-refs-containers/seed.py \
    --host http://localhost:8080/shepard/api --apikey "$(cat /tmp/reseed_apikey.txt)"
```

Add `--reset` to delete the collection first. Idempotent; containers are
top-level and reused by name across runs.
