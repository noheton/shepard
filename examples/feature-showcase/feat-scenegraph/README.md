# feat-scenegraph

**Proves:** B4 — a scene-graph as a `MAPPING_RECIPE`. Per aidocs/platform/191
decision #2, scene-graph is not its own endpoint; a synthetic 3-DOF URDF
`FileReference` (FR1b singleton) + joint-trajectory `TimeseriesReference` are bound
in a `MAPPING_RECIPE` template and materialized into a played Trace3D "play envelope"
(frame tree + joint binding plan) via `POST /v2/mappings/{appId}/materialize`,
dispatched to the `vis-trace3d` `SceneGraphPlayTransformExecutor` (`TransformExecutor`
SPI). The URDF kinematic tree is parsed on demand — never a stored graph.

**Run:**
```bash
/tmp/reseed-venv/bin/python seed.py --apikey "$(cat /tmp/reseed_apikey.txt)" \
  --host http://localhost:8080 --reset
```

Verified live: 4 frames / 3 joints / 3 channel→joint bindings materialized.
Idempotent; `--reset` deletes the collection first. If `vis-trace3d` is disabled the
materialize 404s ("no TransformExecutor registered") and the seed skips gracefully.
