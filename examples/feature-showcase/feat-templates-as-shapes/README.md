# feat-templates-as-shapes

Proves **V2CONV-B6/B7 + B2**: a `DATAOBJECT_RECIPE` `:ShepardTemplate` carrying a
SHACL `shapeGraph`, instantiated into DataObjects. A valid instance returns
**201**; an instance whose template-body attributes violate the shape returns
**422** with the SHACL message. Synthetic AFP-coupon data only.

```bash
/tmp/reseed-venv/bin/python examples/feature-showcase/feat-templates-as-shapes/seed.py \
    --host http://localhost:8080/shepard/api --apikey "$(cat /tmp/reseed_apikey.txt)"
```

Add `--reset` to delete the `feat-templates-as-shapes` collection + helper
templates first. Idempotent; re-running reuses existing entities.
