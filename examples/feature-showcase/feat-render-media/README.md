# feat-render-media

Proves **V2CONV-A1**: a `VIEW_RECIPE` `:ShepardTemplate` rendered via
`POST /v2/shapes/render` with content negotiation — `Accept: application/json`
returns the channel-binding view-model, `Accept: image/png` returns a raster
from the `vis-trace3d` `Trace3DPngRenderer`. Synthetic Trace3D recipe.

```bash
/tmp/reseed-venv/bin/python examples/feature-showcase/feat-render-media/seed.py \
    --host http://localhost:8080/shepard/api --apikey "$(cat /tmp/reseed_apikey.txt)"
```

**Known gap (RESEED-FIND-RENDER-PNG-LOG):** the JSON path is green; the PNG path
currently returns 422 because the renderer's rasterise-failure catch block calls
`io.quarkus.logging.Log.warnf`, which throws on the non-Jandex-indexed plugin jar
and masks the fallback. The seed degrades gracefully (reports the gap). Add
`--reset` to clean up. Idempotent.
