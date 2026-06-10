# feat-semantic-sparql

Showcases **semantic annotations + the SPARQL playground + controlled vocabularies**.
Three synthetic CFRP coupons carry `:SemanticAnnotation` triples drawn from the
bootstrapped vocabularies (Dublin Core Terms, schema.org, Shepard vocab), written
via `POST /v2/annotations` and read back via `GET /v2/annotations?subjectAppId=`.
The seed then exercises the SPARQL playground (`GET /v2/semantic/internal/sparql`).

> **Known gap (RESEED-FIND):** on a freshly bootstrapped instance the n10s
> `_GraphConfig` was initialised *after* data landed, so the SPARQL HTTP endpoint
> returns 404 → the seed surfaces a 400 `sparql.upstream-error` and logs it as a
> SKIP. The annotations ARE written and queryable as `:SemanticAnnotation` nodes;
> only the RDF/SPARQL projection is blocked. Remediation: `n10s.graphconfig.init`
> on an empty graph (or `{force:true}` on a maintenance window).

```bash
/tmp/reseed-venv/bin/python seed.py --reset
```

Synthetic data only — no real DLR/MFFD IP.
