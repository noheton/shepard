# 67 — Unhide publish plugin (Helmholtz Knowledge Graph integration)

**Status.** Design — ready for slice planning.
**Snapshot date.** 2026-05-13.
**Audience.** Contributors implementing the Helmholtz KG harvest
seam; operators wondering how a shepard install shows up in
`unhide.helmholtz-metadaten.de`.

**Originating items.** User request 2026-05-13 ("plugin
integrating publishing data to docs.dev.unhide.helmholtz-metadaten.de").
Couples to `aidocs/64` provenance-architecture §4.1, `aidocs/66`
HMC KIP integration, `aidocs/47` PayloadKind / PayloadStorage SPI,
and `aidocs/16` ONT1b (metadata4ing preseed).

---

## 1. What Unhide is, in one paragraph

The Helmholtz Knowledge Graph (HKG, also called **Unhide**) is a
queryable graph aggregating digital assets across the Helmholtz
Association. Each Helmholtz centre that wants to participate
registers as a **data provider**. Unhide harvests their metadata
on a schedule, runs **inward mappings** against an internal data
model, and lands the harmonised triples in the graph. Consumers
query via SPARQL (QLever for performance, Virtuoso for features)
or the web UI at `unhide.helmholtz-metadaten.de`. Unhide is
**harvest-pull**, never push — shepard never runs code in
Unhide's infrastructure; it exposes a feed; Unhide reads.

## 2. The integration shape

A first-class shepard **plugin** under the `aidocs/47` PayloadKind
SPI seam, packaged as `shepard-plugin-unhide`. The plugin:

1. **Exposes a feed endpoint** — `GET /v2/unhide/feed.jsonld` (or
   the equivalent NQUADS / Turtle serialisation via content
   negotiation) — listing every Collection (and optionally
   DataObject) flagged for publication.
2. **Honours an `If-Modified-Since` / `since=` query param** —
   Unhide harvests incrementally; the feed pages large catalogues.
3. **Cites a KIP record per entity** — the feed entry's `dct:identifier`
   is the PID minted by `aidocs/66`'s `POST /v2/.../publish`; the
   `dcat:landingPage` is the shepard-locator URL.
4. **Body shape** is **schema.org + metadata4ing JSON-LD** — Unhide's
   inward-mappings already handle schema.org (`@type: Dataset`)
   and m4i terms (`m4i:ProcessingStep`, `m4i:Method`); the feed
   uses both so the rich provenance + the discoverability shell
   compose cleanly.

The plugin shape (vs in-tree code) matches `aidocs/47 §3.2`'s
"plugin from day 1" guidance for new payload-kind-style additions.

## 3. The publish toggle

Reuse the `:CollectionProperties` entity from **CP1a** (already
shipped). Add a `publishToHelmholtzKG: boolean` flag (defaults
`false` — operators opt in per Collection):

```cypher
MATCH (c:Collection {appId: $appId})-[:HAS_PROPERTIES]->(p:CollectionProperties)
SET p.publishToHelmholtzKG = $flag
```

REST surface: extend the existing `PATCH /v2/collections/{appId}/properties`
to accept the new field (CP1b's PATCH already exists; this is just
one more field).

Permission posture: requires **Manage** on the Collection (matches
CP1b semantics).

A Collection with `publishToHelmholtzKG = true` AND a minted PID
(per KIP1a from `aidocs/66`) appears in the feed. Collections
without a PID are skipped (cleanly, no warning — the operator
opted in to publishing but hasn't pressed the "publish" button
yet).

## 4. The feed shape

### 4.1 Top-level structure

`GET /v2/unhide/feed.jsonld` — pagination follows the existing
`?cursor=&size=` shape (per `aidocs/13 §2.6`'s cursor convention).

```json
{
  "@context": [
    "https://schema.org/",
    "http://w3id.org/nfdi4ing/metadata4ing/1.4.0/",
    {
      "shepard": "http://shepard.dlr.de/types/",
      "dcat": "http://www.w3.org/ns/dcat#",
      "prov": "http://www.w3.org/ns/prov#"
    }
  ],
  "@graph": [
    {
      "@id": "https://shepard.example.dlr.de/v2/collections/01HF…",
      "@type": ["schema:Dataset", "m4i:Dataset"],
      "dct:identifier": "https://hdl.handle.net/21.11125/abcd-efgh",
      "dcat:landingPage": "https://shepard.example.dlr.de/v2/collections/01HF…",
      "schema:name": "Cyclic-fatigue test campaign 2026-Q1",
      "schema:description": "…",
      "schema:dateCreated": "2026-01-15T10:23:00Z",
      "schema:license": "https://creativecommons.org/licenses/by/4.0/",
      "schema:creator": [
        {
          "@type": "schema:Person",
          "@id": "https://orcid.org/0000-0002-1825-0097",
          "schema:name": "Alice Researcher"
        }
      ],
      "m4i:hasProcessingStep": [
        {
          "@id": "https://shepard.example.dlr.de/v2/provenance/entity/01HF…",
          "@type": "m4i:ProcessingStep"
        }
      ]
    },
    …
  ],
  "_meta": {
    "lastModified": "2026-05-13T05:11:00Z",
    "nextCursor": "eyJpZCI6Li4uLCJ0cyI6Li4ufQ=="
  }
}
```

### 4.2 Per-entity expansion

Each entry's body is the union of:

- **schema.org core** — `name`, `description`, `creator`, `dateCreated`,
  `dateModified`, `keywords`, `license`, `identifier`, `landingPage`.
  Maps directly from shepard's `Collection` / `DataObject` fields.
- **m4i provenance** — `m4i:hasProcessingStep` references back to
  the PROV1h-rendered activity trail (`/v2/provenance/entity/{appId}`
  with `Accept: application/ld+json; profile=…m4i…`). Unhide can
  follow the link to enrich its graph.
- **KIP record reference** — the PID + locator pair embedded
  directly.

### 4.3 Harvest cadence

Default Unhide harvest cadence is **daily** per their docs. The
feed sets `Last-Modified` + supports `If-Modified-Since` so an
unchanged feed returns `304` instead of the full body.

## 5. Configuration — admin-controllable at runtime

The Unhide integration is **admin-configurable at runtime** (per
user direction, 2026-05-13), mirroring the A3b runtime feature-toggle
pattern + N1c2 `:SemanticConfig` shape. No restart needed to
enable / disable publishing, change the feed visibility, or
update the contact email.

### 5.1 The runtime state — `:UnhideConfig` singleton

A new `:UnhideConfig` Neo4j entity (`HasAppId`, single-instance per
the A3b feature-toggle pattern). Fields:

| Field | Type | Default | Purpose |
|---|---|---|---|
| `enabled` | `boolean` | `false` | Master runtime toggle. When `false`, `/v2/unhide/feed.jsonld` returns `404` (feature-off). |
| `feedPublic` | `boolean` | `false` | If `true`, the feed is `@PermitAll`. If `false`, requires the `harvest` API-key role. Default `false` because feed metadata may be licensed; operators opt in to public exposure. |
| `contactEmail` | `String` | `null` | Surfaced in the feed `_meta` so Unhide's harvester knows whom to ping. |
| `harvestKeyAppId` | `String` | `null` | `appId` of the API key Unhide's harvester uses (when `feedPublic=false`). Set when the operator mints / rotates the harvester key. |
| `lastPublishedAt` | `Date` | `null` | Latest `dateModified` across published Collections — feeds `Last-Modified` header / `If-Modified-Since` short-circuit. Maintained by the publish path, not the admin API. |
| `updatedAt` / `updatedBy` | audit fields | — | who flipped what when. |

V## migration: `Vnn__Add_appId_constraint_UnhideConfig.cypher`
(idempotent constraint add) + a startup hook ensuring exactly one
node exists. Pre-Unhide installs see no change until they flip
the toggle.

### 5.2 Deploy-time fallbacks (`application.properties`)

These remain as **install-time defaults** — the runtime
`:UnhideConfig` overrides them once the operator touches the admin
API. Same precedence pattern as A3b feature toggles and N1c2
preseed config.

| Key | Default | Purpose |
|---|---|---|
| `shepard.unhide.enabled` | `false` | Install default for the master toggle (seeds `:UnhideConfig.enabled` on first start). |
| `shepard.unhide.feed.public` | `false` | Install default for feed-public flag. |
| `shepard.unhide.contact-email` | (empty) | Install default for the contact email. |
| `shepard.unhide.feed.page-size` | `100` | Cursor-paginated page size. **Not runtime-mutable** (no operator demand for per-page-size tuning; revisit if needed). |

Precedence:
1. **Runtime `:UnhideConfig` value** wins for the mutable fields
   (enabled / feedPublic / contactEmail / harvestKeyAppId).
2. **Deploy-time property** is the fallback (used on a fresh
   install with no `:UnhideConfig` row yet, and as the seed value
   for the singleton at startup).
3. `page-size` stays deploy-time only.

### 5.3 Per-Collection toggle (unchanged from §3)

`:CollectionProperties.publishToHelmholtzKG = true|false` —
already admin-controllable via the existing CP1b `PATCH
/v2/collections/{appId}/properties` (requires Manage on the
Collection).

## 6. Admin API + CLI

Mirroring A3b features + N1c semantic-refresh shapes. All under
`/v2/admin/unhide` — `@RolesAllowed("instance-admin")`.

| Verb / path | What it does |
|---|---|
| `GET /v2/admin/unhide/config` | Returns the current `:UnhideConfig` shape (without `harvestKeyAppId` content — just whether one's set). |
| `PATCH /v2/admin/unhide/config` | RFC 7396 merge-patch on `enabled`, `feedPublic`, `contactEmail`, `harvestKeyAppId`. Returns the updated config. Fires a `PermissionsChangedEvent` (PROV1a captures it as an admin action). |
| `POST /v2/admin/unhide/harvest-key` | Mints a fresh API key with the `harvest` role and sets `:UnhideConfig.harvestKeyAppId` to its appId. Returns the JWT once (clipboard-shaped) per the existing ApiKey pattern. Rotates if one already exists. |
| `DELETE /v2/admin/unhide/harvest-key` | Revokes the current harvest key + clears `harvestKeyAppId`. |

**CLI parity** — extend `shepard-admin` with a new `unhide`
subcommand group:

```
shepard-admin unhide status                               # show current config
shepard-admin unhide enable                               # flip enabled → true
shepard-admin unhide disable                              # flip enabled → false
shepard-admin unhide set-feed-public <true|false>
shepard-admin unhide set-contact-email <email>
shepard-admin unhide rotate-harvest-key                   # POST /v2/admin/unhide/harvest-key
shepard-admin unhide revoke-harvest-key                   # DELETE /v2/admin/unhide/harvest-key
```

All commands honour `--output={human,json}` per the existing CLI
convention.

### 6.1 Audit trail

Every PATCH / harvest-key mint / revoke fires through the existing
`ProvenanceCaptureFilter` (it's a mutating admin endpoint, so PROV1a
captures it automatically — no new wiring). The `:Activity` row
carries `targetKind: "UnhideConfig"` so the audit trail can be
filtered for "who changed Unhide settings when".

## 7. The Unhide self-registration step

shepard doesn't run code in Unhide. To get harvested, the
operator does one of:

- **Self-service.** Visit
  `unhide.helmholtz-metadaten.de/dataprovider/register`,
  submit the shepard feed URL + contact, wait for HMC team to
  approve.
- **HMC outreach.** HMC's data-provider liaison reaches out to
  Helmholtz centres directly. shepard installs in the wild get
  approached when the install becomes operator-visible.

shepard's job: emit the feed at a stable URL. Discovery is the
operator's responsibility.

## 8. The data flow, end to end

```
Operator → CP1b PATCH → publishToHelmholtzKG=true
       ↓
Operator → POST /v2/collections/{appId}/publish → PID minted (KIP1a)
       ↓
       shepard exposes /v2/unhide/feed.jsonld with this Collection in the @graph
       ↓
Unhide harvester (daily cron) → GET /v2/unhide/feed.jsonld?cursor=…
       ↓
       Unhide's inward-mappings extract schema.org + m4i terms
       ↓
       Triples land in the Helmholtz KG (Virtuoso + QLever)
       ↓
External researcher → SPARQL on Unhide → finds this Collection
       ↓
       Click landing page → arrives at shepard's /v2/collections/{appId}
       ↓
       Read-permission check → 200 (if public/auth'd) or 401
```

## 9. Phasing

| Slice | What it ships | Size |
|---|---|---|
| **UH1a-baseline** | Plugin module `shepard-plugin-unhide` + the feed endpoint emitting schema.org-only entries + `:CollectionProperties.publishToHelmholtzKG` field via CP1b PATCH + cursor pagination + **`:UnhideConfig` singleton (§5.1) + admin REST surface (§6) + CLI parity** (the admin-configurable shape lands together with the baseline, not as a follow-up — operators need it from day one to flip the master toggle without restarting) | M |
| **UH1b-m4i** | Extend the feed body with m4i provenance fragments — depends on **PROV1h** content-neg shipping (so the feed can link back to m4i-shaped `/v2/provenance/entity/{appId}`) | S |
| **UH1c-kip** | Feed cites KIP record (PID + locator) per entity — depends on **KIP1a** | S |
| **UH1d-frontend** | Per-Collection "Publish to Helmholtz KG" toggle in the UI (alongside the existing CP1b properties pane) + admin `/admin` page tile for the instance-wide toggles (mirrors A3b1 admin metrics strip placement) | S–M |
| **UH1e-self-test** | `GET /v2/unhide/feed.jsonld?validate=true` returns a SHACL validation report against Unhide's expected shape — diagnostic for operators | S |

Recommended order: **UH1a → UH1d → UH1c → UH1b → UH1e**. UH1a is
useful even without KIP / PROV1h ready (and ships with the
runtime admin surface so an operator can flip the master toggle
without restarting); UH1c gates on KIP1a.

## 10. Cross-references

- `aidocs/64` provenance-architecture §4.1 — positioning Unhide
  as the harvest layer above metadata4ing + KIP.
- `aidocs/66` HMC KIP — the PID record format Unhide cites.
- `aidocs/47 §3.2` — plugin-from-day-1 SPI seam.
- `aidocs/55` PROV1 — provenance trail Unhide can drill into.

External:
- [Helmholtz Knowledge Graph docs](https://docs.unhide.helmholtz-metadaten.de/)
- [Unhide data-storage API](https://codebase.helmholtz.cloud/hmc/hmc-public/unhide/development/data_storage_api)
- [Unhide internal data model](https://codebase.helmholtz.cloud/hmc/hmc-public/unhide/development/semantics/internal-data-model)
- [Unhide inward mappings](https://codebase.helmholtz.cloud/hmc/hmc-public/unhide/development/semantics/inwardmappings)
