# feat-admin-config

Proves **V2CONV-A4**: the generic `/v2/admin/config/{feature}` registry that
replaced the per-feature bespoke config resources. Lists features, reads the
`ror` config, PATCHes it (RFC 7396 merge-patch), reads it back, then clears it
again so the seed leaves no residue. Also asserts 404 on an unknown feature.

This is the one Core V2CONV seed with **no `feat-<slug>` Collection** — A4 is an
instance-level admin surface with no per-collection entity.

```bash
/tmp/reseed-venv/bin/python examples/feature-showcase/feat-admin-config/seed.py \
    --host http://localhost:8080/shepard/api --apikey "$(cat /tmp/reseed_apikey.txt)"
```

Idempotent (always clears the field it sets). Requires an instance-admin key.
