# feat-mapping-recipe

Proves **V2CONV-B3**: a `MAPPING_RECIPE` `:ShepardTemplate` materialized via
`POST /v2/mappings/{templateAppId}/materialize`. The recipe targets the
identity-transform shape claimed by the built-in `NoOpTransformExecutor`, so the
path works with **no plugin installed** — the executor echoes the bound input
reference appId back as the derived reference. Synthetic URI-reference input.

```bash
/tmp/reseed-venv/bin/python examples/feature-showcase/feat-mapping-recipe/seed.py \
    --host http://localhost:8080/shepard/api --apikey "$(cat /tmp/reseed_apikey.txt)"
```

Add `--reset` to delete the collection + helper template first. Idempotent.
