---
stage: feature-defined
last-stage-change: 2026-05-23
---

# 65 — Admin-configurable ontology pre-seeding (with custom-bundle support)

**Status.** Design — ready to implement.
**Snapshot date.** 2026-05-13.
**Audience.** Contributors implementing the slice; operators who
want to know how to control which ontologies land in their
shepard's internal semantic repository.

**Originating items.** User request 2026-05-13: "make pre-seeding
configurable by admin which ontologies (except required) to
include; allow adding ontologies." Couples to `aidocs/48` (n10s
internal semantic repo), `aidocs/63` ADR-0019 (preseed default-on),
the N1b/ONT1a/ONT1b shipped baseline (`aidocs/16`), and N1c's
shipped `POST /v2/admin/semantic/refresh-ontologies` admin API.

---

## 1. The current state

After **N1b** + **ONT1a** + **ONT1b** the repo ships **ten**
ontology bundles under `backend/src/main/resources/ontologies/`:

| Bundle id | Licence | Notes |
|---|---|---|
| `prov-o` | W3C Document Licence | provenance baseline; required by PROV1 |
| `dublin-core` | CC BY 4.0 | FAIR metadata baseline |
| `schema-org` | CC BY-SA 3.0 | broad web-vocab |
| `foaf` | CC BY 1.0 | persons / organisations |
| `qudt` | CC BY 4.0 | units / quantities |
| `om-2` | CC BY 4.0 | engineering units alternative |
| `time` | W3C Document Licence | temporal vocab |
| `geosparql` | OGC Open Data | geospatial vocab |
| `obo-relations` | CC0 1.0 | relation predicates (ONT1a) |
| `metadata4ing` | CC BY 4.0 | engineering-research process modelling (ONT1b) |

The current operator knobs (deploy-time only):
- `shepard.semantic.internal.preseed-ontologies.enabled=true` — master toggle
- `shepard.semantic.internal.preseed-ontologies.skip-bundles=qudt,om-2` — CSV opt-out

The N1c admin endpoint `POST /v2/admin/semantic/refresh-ontologies`
refreshes already-seeded bundles to canonical content; it doesn't
manage which ones are seeded in the first place.

Two operator gaps:
1. **No runtime ON/OFF control per bundle** — only the deploy-time
   CSV skip list. Flipping it requires restarting shepard.
2. **No way to add a custom ontology** — operators with a
   domain-specific TTL (e.g. a lab's local extension of m4i) can
   only add it by editing the classpath, which round-trips through
   a shepard release.

This doc proposes a slice (**N1c2**) that closes both.

---

## 2. The shape

### 2.1 Required vs optional bundles

Extend the shipped `ontologies-manifest.json` entry schema with a
`required: boolean` field. The required set is conservative:

| Bundle | Required | Why |
|---|---|---|
| `prov-o` | **yes** | PROV1 audit-trail interop |
| `dublin-core` | **yes** | DCAT / RO-Crate baseline |
| `skos` | (added) **yes** | every other ontology refers to `skos:Concept` |
| all others | no | operator choice |

Disabling a `required: true` bundle is **refused** — both the
admin endpoint and the CLI return RFC 7807 with
`semantic.bundle.required-cannot-disable`.

### 2.2 Runtime state — `:SemanticConfig` singleton

A new `:SemanticConfig` Neo4j entity (`HasAppId`, but
single-instance per the A3b feature-toggle pattern). Fields:

| Field | Type | Notes |
|---|---|---|
| `disabledBundles` | `Set<String>` | bundle ids the operator has disabled; `required: true` bundles are never honoured here |
| `customBundles` | `Set<String>` | ids of operator-added bundles (the actual files live under `user-dir`, see §2.3) |
| `updatedAt` / `updatedBy` | audit fields | who flipped what when |

V## migration: `Vnn__Add_appId_constraint_SemanticConfig.cypher`
(idempotent constraint add) + a startup hook that ensures exactly
one node exists.

### 2.3 Custom-bundle storage

New config key
`shepard.semantic.internal.preseed-ontologies.user-dir`
(default `/var/lib/shepard/ontologies/`). Layout:

```
/var/lib/shepard/ontologies/
├── user-manifest.json       # mirrors the shipped manifest shape
├── lab-vocab.ttl
└── custom-units.ttl
```

`user-manifest.json` schema is identical to the shipped one, with
two extra fields:

```json
{
  "id": "lab-vocab",
  "iriPrefix": "https://example.dlr.de/lab-vocab/",
  "canonicalUrl": null,          // optional — null = "no canonical refresh; this is a local bundle"
  "license": "CC BY 4.0",
  "sha256": "<computed-at-add-time>",
  "byteSize": 12345,
  "required": false,             // operator-added bundles never required
  "filename": "lab-vocab.ttl",
  "addedBy": "alice",            // for the audit trail
  "addedAt": "2026-05-13T11:32:00Z"
}
```

`OntologySeedService` walks **built-in manifest first, user-manifest
second**, in that order. The bundle-id namespace is shared — an
operator trying to add a bundle with id `prov-o` is refused (409
`semantic.bundle.id-conflicts-with-builtin`).

### 2.4 Admin REST surface

All under `/v2/admin/semantic/ontologies` — `@RolesAllowed("instance-admin")`.

| Verb / path | What it does |
|---|---|
| `GET /v2/admin/semantic/ontologies` | Lists every bundle (built-in + user) with `{id, iriPrefix, license, source: "builtin"\|"user", enabled, required, lastSeededAt, sha256}` |
| `POST /v2/admin/semantic/ontologies/{id}/enable` | Removes `{id}` from `disabledBundles` (no-op if not present). 404 on unknown id. |
| `POST /v2/admin/semantic/ontologies/{id}/disable` | Adds `{id}` to `disabledBundles`. **409** with RFC 7807 `semantic.bundle.required-cannot-disable` if `required: true`. |
| `POST /v2/admin/semantic/ontologies` | Upload a new bundle. **Multipart**: `file=<ttl>` + `metadata={"id":..., "iriPrefix":..., "license":...}`. Server computes SHA-256 + byteSize. Writes to `user-dir`, appends to `user-manifest.json`, adds id to `customBundles`. Triggers an immediate seed of the new bundle. Returns the new manifest entry. **409** if id collides with built-in or another user bundle; **400** if TTL parse fails. |
| `DELETE /v2/admin/semantic/ontologies/{id}` | Remove a **user-added** bundle. Drops the file from `user-dir`, the entry from `user-manifest.json`, the id from `customBundles`. **403** with `semantic.bundle.builtin-not-removable` if the id is a built-in bundle (those land via release upgrades, not API). |
| `POST /v2/admin/semantic/ontologies/{id}/seed` | Force a re-import of `{id}` (alias to the existing N1c refresh but per-bundle). |

The existing N1c `POST /v2/admin/semantic/refresh-ontologies`
(bulk refresh) stays — it's the "refresh everything against
canonical URLs" path; the new per-bundle `seed` endpoint is for
operator-driven one-off re-imports (e.g. after adding a custom
bundle).

### 2.5 CLI parity

Extend the `shepard-admin semantic` subcommand group (from N1c):

```
shepard-admin semantic ontologies list
shepard-admin semantic ontologies enable <id>
shepard-admin semantic ontologies disable <id>
shepard-admin semantic ontologies add <file> --id=<id> --iri-prefix=<iri> --license=<license>
shepard-admin semantic ontologies remove <id>
shepard-admin semantic ontologies seed <id>          # per-bundle re-import
shepard-admin semantic refresh-ontologies             # bulk (existing, N1c)
```

Output formats: `--output={human,json}` as the rest of the CLI.

### 2.6 `OntologySeedService` changes

Pseudocode for the startup pass post-N1c2:

```java
SemanticConfig cfg = semanticConfigDAO.findSingleton();          // cached
List<OntologyEntry> builtin = manifest.loadBuiltin();
List<OntologyEntry> user    = manifest.loadUser(userDir);

for (OntologyEntry e : Stream.concat(builtin.stream(), user.stream()).toList()) {
  if (cfg.getDisabledBundles().contains(e.id())) {
    if (e.required()) {
      log.warn("Bundle '{}' is required; ignoring runtime disable", e.id());
    } else {
      log.info("Bundle '{}' is admin-disabled; skipping", e.id());
      continue;
    }
  }
  if (skipBundlesProperty.contains(e.id())) {
    log.info("Bundle '{}' is deploy-time-skipped; skipping", e.id());
    continue;
  }
  importBundle(e);
}
```

Precedence order:
1. **`required: true`** beats everything — required bundles are
   always seeded.
2. **`disabledBundles` runtime set** wins over the deploy-time CSV
   for the same bundle (matches the A3b feature-toggle precedence).
3. **`shepard.semantic.internal.preseed-ontologies.skip-bundles=`**
   deploy-time CSV is the fallback / install-time defaults.

### 2.7 The audit trail

Every enable/disable/add/remove call fires a `PermissionsChangedEvent`-shape
audit through the existing `ProvenanceCaptureFilter` (it's a
mutating admin endpoint, so PROV1a captures it automatically — no
new wiring). The actor lands on the `:Activity` row with
`targetKind: "SemanticBundle"` and `targetAppId: <bundle-id>`.

---

## 3. Phasing

| Slice | What it ships | Size |
|---|---|---|
| **N1c2-baseline** | `:SemanticConfig` entity + DAO + V## migration + `required` field on the built-in manifest + precedence rules in `OntologySeedService` | M |
| **N1c2-admin** | The five REST endpoints (`GET`, `POST .../enable`, `POST .../disable`, `POST .../{id}/seed`, `POST /v2/admin/semantic/ontologies` upload, `DELETE`) | M |
| **N1c2-cli** | Five new `shepard-admin semantic ontologies …` subcommands | S |
| **N1c2-frontend** *(deferred)* | A small admin UI on `/admin` to flip toggles + upload — design only in this doc; ships when there's evidence operators want a GUI on top of the API | S–M |

Recommended dispatch shape: **bundle baseline + admin + CLI into
one slice** (they're tightly coupled), ship as a single PR. Defer
frontend until the API is proven.

---

## 4. Backwards compatibility

- All existing config keys keep working. The deploy-time
  `skip-bundles=qudt,om-2` CSV stays valid and forms the install
  defaults for `disabledBundles` on a fresh `:SemanticConfig`.
- Fresh installs (no `:SemanticConfig` yet) — the startup hook
  creates a node with `disabledBundles = <deploy-time CSV>`,
  `customBundles = ∅`. Operators on the existing skip-bundles
  workflow see no change.
- The N1c `POST /v2/admin/semantic/refresh-ontologies` keeps its
  semantics — it refreshes every **currently enabled** bundle
  (per the precedence rules above) to canonical content.

No data migration risk; the addition is additive and idempotent.

---

## 5. Out of scope (explicit)

- **Per-Collection ontology selection.** All bundles are
  instance-wide; a single n10s graph backs the internal
  repository.
- **Mid-stream removal of seeded data.** Disabling a bundle stops
  it being re-imported on next restart / refresh; it does **not**
  delete already-imported `:Resource` triples. Cleanup is a
  separate `POST /v2/admin/semantic/ontologies/{id}/purge` design
  (not in this slice).
- **External-repo connectors.** This slice is internal-n10s only.
  External `SemanticRepositoryType.SPARQL` / `JSKOS` / `SKOSMOS`
  repos are managed via their existing CRUD (`aidocs/48`).

---

## 6. Cross-references

- `aidocs/48-internal-semantic-repository-design.md` — n10s
  internal repo design.
- `aidocs/16-dispatcher-backlog.md` — N1b / ONT1a / ONT1b / N1c
  predecessors; new N1c2 row to be added in the implementation
  PR.
- `aidocs/63` ADR-0019 — preseed default-on (the precedence rules
  here extend, not supersede, that decision).
- `aidocs/workflows/64-provenance-architecture.md` (this same PR) — the m4i
  preseed shipped via ONT1b is exactly the kind of optional
  bundle this slice lets an operator disable on a non-engineering
  install.
