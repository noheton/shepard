---
title: "Runbook: manage semantic vocabularies"
description: Operator procedures for registering, enabling, disabling, and troubleshooting semantic vocabularies in the n10s knowledge graph
permalink: /admin/runbooks/manage-vocabularies/
layout: default
audience: admin
---
# Runbook: manage semantic vocabularies

**When to use this runbook:**
- Uploading a custom ontology (OWL/TTL) so researchers can annotate with domain terms.
- Enabling or disabling a vocabulary.
- Checking which vocabularies are currently active.
- Diagnosing missing autocomplete terms or 404s on the term-search endpoint.
- Safely removing a vocabulary from the database.

**Prerequisite:** you must have the `instance-admin` role.
API calls below use `$TOKEN` — obtain one via the admin CLI or your OIDC provider.

---

## 1. Check which vocabularies are active

```bash
curl -s -H "Authorization: Bearer $TOKEN" \
  https://shepard.example.dlr.de/v2/admin/semantic/ontologies \
  | jq '.[] | {name, enabled, termCount}'
```

Ten vocabularies are pre-seeded at startup (Dublin Core Terms, PROV-O, schema.org,
DataCite, CHAMEO, Material OWL, metadata4ing, SKOS, GeoSPARQL, Shepard internal).
All are enabled by default. See [semantic-repositories reference](/reference/semantic-repositories/)
for the full list with IRI bases.

---

## 2. Upload a custom vocabulary

Custom vocabularies must be valid OWL/Turtle (`.ttl`) or RDF/XML (`.owl`) files.

```bash
curl -s -X POST \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@/path/to/my-ontology.ttl" \
  -F "name=My Domain Ontology" \
  -F "prefix=myonto" \
  https://shepard.example.dlr.de/v2/admin/semantic/ontologies
```

On success the endpoint returns the new vocabulary's `appId`. Note it down — you
will need it to enable/disable or delete the vocabulary.

**Import takes a few seconds** for ontologies up to ~10,000 terms. Larger files
(>50,000 triples) will return `202 Accepted` and complete asynchronously. Poll
`GET /v2/admin/semantic/ontologies/{appId}` until `status` is `ACTIVE`.

---

## 3. Enable a vocabulary

```bash
curl -s -X PATCH \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"enabled": true}' \
  https://shepard.example.dlr.de/v2/admin/semantic/ontologies/{vocabAppId}
```

Terms from an enabled vocabulary immediately appear in autocomplete and SPARQL.

---

## 4. Disable a vocabulary

Disabling a vocabulary removes its terms from autocomplete and marks them as
inactive in the knowledge graph. **It does not delete existing annotations that
reference those terms** — those annotations remain intact and continue to appear
in the REST API and SPARQL results.

```bash
curl -s -X PATCH \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"enabled": false}' \
  https://shepard.example.dlr.de/v2/admin/semantic/ontologies/{vocabAppId}
```

---

## 5. Check annotation counts before deleting

Before deleting a vocabulary, verify that no annotations reference its terms.
Run this SPARQL query against your SemanticRepository:

```bash
# Get your SemanticRepository appId
REPO_ID=$(curl -s -H "Authorization: Bearer $TOKEN" \
  https://shepard.example.dlr.de/v2/admin/semantic/repositories \
  | jq -r '.[0].appId')

# Count annotations using the vocabulary
curl -s -G \
  --data-urlencode "query=SELECT (COUNT(?a) AS ?count) WHERE {
    ?a a <urn:shepard:SemanticAnnotation> ;
       <urn:shepard:vocabularyId> \"${VOCAB_APP_ID}\" .
  }" \
  -H "Authorization: Bearer $TOKEN" \
  https://shepard.example.dlr.de/v2/semantic/${REPO_ID}/sparql
```

If `count` is non-zero, do **not** delete the vocabulary. Disable it instead
(step 4) and inform users that those terms are deprecated.

---

## 6. Delete a vocabulary

Deletion is permanent. It removes the vocabulary node and all its term (`:Predicate`)
nodes from the knowledge graph. Existing `:SemanticAnnotation` nodes that reference
those predicate IRIs are **not** deleted, but their `vocabularyId` link becomes a
dangling pointer — the terms will no longer resolve in autocomplete.

```bash
curl -s -X DELETE \
  -H "Authorization: Bearer $TOKEN" \
  https://shepard.example.dlr.de/v2/admin/semantic/ontologies/{vocabAppId}
```

Response `204 No Content` on success.

---

## 7. Troubleshooting

### Terms not appearing in autocomplete

1. **Check the vocabulary is enabled:**
   ```bash
   curl -s -H "Authorization: Bearer $TOKEN" \
     https://shepard.example.dlr.de/v2/admin/semantic/ontologies/{vocabAppId} \
     | jq '{name, enabled, status, termCount}'
   ```
   If `enabled` is `false`, patch it to `true` (step 3).

2. **Check n10s import completed:**
   If `status` is `IMPORTING`, wait and retry. If `FAILED`, check the
   application log for n10s parse errors:
   ```bash
   docker logs shepard-backend 2>&1 | grep -i 'n10s\|ontology\|vocabulary' | tail -40
   ```

3. **Verify the term exists in the knowledge graph:**
   ```bash
   curl -s -G \
     --data-urlencode "query=SELECT ?term ?label WHERE {
       ?term rdfs:label ?label .
       FILTER(CONTAINS(LCASE(STR(?label)), \"vibration\"))
     } LIMIT 10" \
     -H "Authorization: Bearer $TOKEN" \
     https://shepard.example.dlr.de/v2/semantic/${REPO_ID}/sparql
   ```

### Term-search endpoint returns 404

The term-search endpoint (`GET /v2/semantic/terms/search`) requires a
SemanticRepository to exist. Check:

```bash
curl -s -H "Authorization: Bearer $TOKEN" \
  https://shepard.example.dlr.de/v2/admin/semantic/repositories \
  | jq '.[].status'
```

If the list is empty, the INTERNAL SemanticRepository failed to initialise.
Check the V49 migration status:

```bash
docker logs shepard-backend 2>&1 | grep 'V49\|SemanticRepository\|n10s' | tail -20
```

If the migration ran but the repository node is missing from Neo4j, you can
trigger a manual re-seed:

```bash
curl -s -X POST \
  -H "Authorization: Bearer $TOKEN" \
  https://shepard.example.dlr.de/v2/admin/semantic/repositories/reseed
```

### Uploaded ontology parses but has no terms

Some ontologies use `skos:prefLabel` instead of `rdfs:label`. Confirm which
label predicate the file uses, then check the n10s configuration. The n10s
`handleMultival` and `languageFilter` settings control which labels are indexed.
See the [semantic-repositories reference](/reference/semantic-repositories/) for
the runtime-configurable n10s parameters.

### SPARQL returns results but terms are not in autocomplete

Autocomplete is served from the `:Predicate` registry, which is a separate
index from the raw n10s triples. If you uploaded an ontology directly into
n10s (bypassing the `/v2/admin/semantic/ontologies` endpoint), the `:Predicate`
index was not populated. Re-upload via the API to sync both stores.

---

## 8. Admin CLI equivalents

All operations above are also available through the `shepard-admin` CLI:

```bash
# List vocabularies
shepard-admin semantic status --url https://shepard.example.dlr.de --api-key $KEY

# Upload
shepard-admin semantic upload --file my-ontology.ttl --name "My Ontology" \
  --url https://shepard.example.dlr.de --api-key $KEY

# Enable / disable
shepard-admin semantic enable  --id {vocabAppId} --url … --api-key …
shepard-admin semantic disable --id {vocabAppId} --url … --api-key …

# Delete
shepard-admin semantic delete  --id {vocabAppId} --url … --api-key …
```

Use `--output json` for machine-parseable output in automation scripts.

---

## See also

- [Semantic repositories (reference)](/reference/semantic-repositories/) — full REST API for vocabulary and repository management
- [Semantic annotations (reference)](/reference/semantic-annotations/) — annotation data model and REST surface
- [Annotating data (user guide)](/help/annotating-data/)
- [Admin CLI reference](/reference/admin-cli/)
