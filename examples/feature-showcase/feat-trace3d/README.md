# feat-trace3d

**Proves:** Trace3D — a colour-mapped 3D brush trace rendered from x/y/z + scalar
`TimeseriesReference`s via the converged, stateless `POST /v2/shapes/render`
endpoint, dispatched to the `vis-trace3d` plugin's `Trace3DPngRenderer`
(`ViewRecipeRenderer` SPI). JSON returns the four DECLARED channel bindings; an
`Accept: image/png` request asks the renderer for a server-side PNG raster.

**Run:**
```bash
/tmp/reseed-venv/bin/python seed.py --apikey "$(cat /tmp/reseed_apikey.txt)" \
  --host http://localhost:8080 --reset
```

Creates the `feat-trace3d` Collection, a TimeseriesContainer + 4 TS references
(x/y/z/temp), and a VIEW_RECIPE template carrying the Trace3D shape IRI. Idempotent;
`--reset` deletes the collection first.

**Green (both render paths):** the JSON view-model returns the four DECLARED channel
bindings, and `Accept: image/png` returns a real 800×600 / 39 KB PNG raster via the
`vis-trace3d` `Trace3DPngRenderer`. The VIEW_RECIPE template round-trips by appId.

Two plugin/infra bugs were found and **fixed this PR** to get the PNG path green (see
aidocs/integrations/121 §5):
- `RESEED-FIND-TRACE3D-JANDEX` — added
  `quarkus.index-dependency.shepard-plugin-vis-trace3d` (the plugin was `state=FAILED`
  from an unindexed `io.quarkus.logging.Log` call).
- `RESEED-FIND-TRACE3D-FONTCONFIG` — `backend/Dockerfile` now installs `fontconfig` +
  `dejavu-sans-fonts` (headless AWT text init threw "Fontconfig head is null").
