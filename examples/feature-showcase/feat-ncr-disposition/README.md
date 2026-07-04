# feat-ncr-disposition

Showcases **AAA2 — Non-Conformance Report (NCR) + disposition + rework chain**.
A synthetic CFRP coupon FAILs inspection, an NCR is raised with a `REWORK`
disposition (EN 9100 §8.7 / EASA Part 21 G), a rework DataObject is linked back
by a typed `fair2r:repairs` predecessor edge, and a re-inspection PASSes — closing
the NCR. NCR posture lives in the canonical `:SemanticAnnotation` store (the EN 9100
quality `status` field is role-gated to `quality-engineer`, so the seed records it
via annotations and logs the 403 as a SKIP).

```bash
/tmp/reseed-venv/bin/python seed.py --reset   # rebuild
/tmp/reseed-venv/bin/python seed.py           # idempotent (reuse by name)
```

Synthetic data only — no real DLR/MFFD IP.
