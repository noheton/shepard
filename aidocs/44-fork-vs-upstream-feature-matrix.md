# Fork vs Upstream — Feature Matrix

**Status.** **Live.** Updated whenever a feature ships, a design doc
lands, or upstream cuts a new release.
**Snapshot date.** 2026-05-08.
**Upstream baseline.** `gitlab.com/dlr-shepard/shepard 5.2.0`.

This is the **progress tracker** comparing what's available in this
fork (`noheton/shepard main`) against the upstream 5.2.0 surface,
broken down by feature area. **Different from `aidocs/34`** — that
doc is admin-facing ("what an upgrader needs to know about each
landed change"); this doc is **contributor / PI-facing** ("how does
this fork compare across the whole feature surface, including
designed-not-yet-shipped work").

## Status legend

| Symbol | Meaning |
|---|---|
| **✓** | Shipped on this fork's `main` |
| **📐** | Designed (design doc landed; implementation queued) |
| **🚧** | Implementation in flight (agent dispatched / PR open) |
| **=** | Parity with upstream (same shape on both sides) |
| **↑** | This fork extends upstream (we ship more) |
| **—** | Not implemented anywhere |
| **⚠** | Diverges deliberately — see notes |

## Standing rule

Per `CLAUDE.md`, this matrix updates in the same PR as any feature
landing or design-doc landing — keep it consistent with `aidocs/16`
backlog and `aidocs/00-index.md`. A row that's stale is the bug.

---

## 1. DB connectivity / health / migrations

| Capability | Upstream 5.2.0 | This fork | Status | Refs |
|---|---|---|---|---|
| Bounded `MigrationsRunner.waitForConnection` w/ exponential backoff | infinite-wait loop | configurable `shepard.migrations.connection-wait-timeout` (default `PT60S`) | **✓ ↑** | A1 / `aidocs/16` row A1 / `aidocs/17` |
| Per-DB health-check separation (startup vs runtime) | combined / coarse | per-DB `state` + `kind` in `/healthz` | **✓ ↑** | A1b |
| Graceful degradation when optional DBs (PostGIS) unavailable | endpoints hang | RFC-7807 503 + `Retry-After` when `@RequiresDatabase` not satisfied; 404 when toggle OFF | **✓ ↑** | A1c |
| `MigrationsRunner.apply()` fail-fast on `MigrationsException` | swallow + log | propagates as `RuntimeException` aborting startup | **✓ ↑** | A1e (commit `0f2f512`) |
| Automated DB recovery scheduler | none | `@Scheduled(every = "${shepard.health.recovery.interval}")` default `PT15S`; new `quarkus-scheduler` dep | **✓ ↑** | A1f |
| Migration progress monitoring endpoint | none | `GET /migrations/progress` (P3) | **✓ ↑** | P3 (commit `7cc74b8`) |

## 2. Configuration / feature toggles

| Capability | Upstream | This fork | Status | Refs |
|---|---|---|---|---|
| Build-time vs runtime feature toggle mechanism | `@IfBuildProperty` only | `@ConditionalOnFeature` + runtime-toggleable | **✓ ↑** | A3 |
| Spatial-data namespace alias (`shepard.spatial-data.*` → `shepard.infrastructure.spatial.*`) | only old names | both names resolve; old logs deprecation warning; removal v6.0 | **✓ ↑** | A3c / `aidocs/A3c-namespace-migration.md` |
| Permission cache TTL/max-size config | hard-coded global defaults | `shepard.permissions.cache.ttl` (`PT5M`) + `.max-size` (`10000`) | **✓ ↑** | A4 |

## 3. Auth / API keys / security

| Capability | Upstream | This fork | Status | Refs |
|---|---|---|---|---|
| Semi-permanent API keys with expiry (`validUntil` + JWT `exp`) | none | shipped, distinguishable 401 on expiry | **✓ ↑** | L5 (commit `30c687a`) |
| `Bearer ` prefix mangle on JWTs containing the literal substring | mangles | safe `startsWith → substring(7)` | **✓ ↑** | M4 |
| Auth-header echo to warn-level logs (token-leak) | full echo | `present`/`absent` only | **✓ ↑** | M5 |
| `~/.shepard/keys/private.key` perms | umask default | `0600` via `Files.setPosixFilePermissions` (best-effort, POSIX only) | **✓ ↑** | M2 |
| Cypher injection on user-controlled property names + IRI types | injectable | parameterised + property-name allowlist | **🚧** (agent in flight) | C5 / `aidocs/07` C5 |
| CORS allowlist instead of `origins=*` | wildcard | TBD | 📐 (queued) | `aidocs/07` C2 |
| Default-credential placeholders that fail at startup if not changed | accept shipped defaults | TBD | 📐 (queued) | `aidocs/07` H8 |
| OIDC `realm_access.roles` claim path configurable (multi-IdP) | hard-coded Keycloak shape | TBD | 📐 (queued, F8) | `aidocs/22 §4.11a.4` |
| Permission system: declarative `@Authz` annotation | path-segment switch | TBD | 📐 (queued, F1) | `aidocs/24` F1 / P5 |
| Group-based sharing model (`Group` node) | none | TBD | 📐 (queued, F2) | `aidocs/24` F2 |
| Permission audit log (Postgres) | none | TBD | 📐 (queued, F3) | `aidocs/24` F3 |

## 4. Identifiers (the L2 chain)

| Capability | Upstream | This fork | Status | Refs |
|---|---|---|---|---|
| Additive `appId` (UUID v7) on every Neo4j node-write | none | shipped via `HasAppId` mixin on 28 labels; minted by `GenericDAO` seam; `V11` per-label unique constraints | **✓ ↑** | L2a (commit `fec7979`) |
| Backfill `appId` for pre-L2a rows (`V12`) | n/a | TBD | **🚧** (agent in flight) | L2b / `aidocs/25` |
| Read path uses `WHERE e.appId = $appId` | uses `id()` | TBD; gated on C5 | 📐 (queued, gated on C5) | L2c / `aidocs/25` |
| `/v2/` API exposes `appId` natively | n/a | TBD; gated on P4 + H4 | 📐 (queued) | L2d / `aidocs/25` |
| Drop `/v1/` long-id paths; flip cache key shape; drop TimescaleDB legacy column | n/a | TBD | 📐 (queued) | L2e / `aidocs/25` |

## 5. API surface — additive endpoints

| Capability | Upstream | This fork | Status | Refs |
|---|---|---|---|---|
| NDJSON streaming ingest for timeseries (`application/x-ndjson` on `POST /timeseriesContainers/{id}/payload`) | JSON-only | shipped | **✓ ↑** | P14 (commit `24d4585`) |
| Body-form selective RO-Crate export (`POST /collections/{id}/export` with `ExportSelection`) | GET-only | shipped — additive sibling, GET preserved | **✓ ↑** | R2 (commit `be0eb26`) |
| Per-payload selection (file OIDs / channel columns / time windows) | none | shipped | **✓ ↑** | R2b (commit `60a3ea1`) |
| Per-payload metadata-field redaction (closed enum of 6 fields) | none | shipped | **✓ ↑** | R2c (commit `f993e8b`) |
| Export emits permissions / versions / annotations / subscriptions documents | none | shipped (3 of 4 kinds via R2d, +subscriptions via R2d2) | **✓ ↑** | R2d / R2d2 |
| `application/merge-patch+json` PATCH semantics (P21x) | mixed shapes | shipped consistent across new endpoints | **✓ ↑** | P21x |
| `POST /sql/timeseries` curated SQL-over-HTTP for bulk reads | none | TBD; gated on C5 | 📐 (queued, P10a-c) | `aidocs/29` |

## 6. Search

| Capability | Upstream | This fork | Status | Refs |
|---|---|---|---|---|
| Unified `POST /search/v2` replacing legacy 5 routes | 5 legacy routes | TBD; gated on C5 | 📐 (queued, P7) | `aidocs/13` |
| Cursor pagination across paginated endpoints | mixed/missing | TBD | 📐 (queued, L6) | `aidocs/13` / `aidocs/18` |
| Search-as-you-type with tree/graph view | basic search page | TBD | 📐 (queued, L4) | `aidocs/13` / `aidocs/14` |
| Saved searches / search history | none | TBD | 📐 (queued) | `aidocs/13` |

## 7. Semantic annotations / knowledge graph

| Capability | Upstream | This fork | Status | Refs |
|---|---|---|---|---|
| Annotate file / structured / spatial payloads (today: only timeseries + DataObjects) | timeseries + DataObjects only | TBD | 📐 (queued, L7) | `aidocs/14` |
| Nested annotation search | basic | TBD | 📐 (queued, #658) | `aidocs/14` |
| Term-search facet (search ontology terms) | none | TBD | 📐 | `aidocs/14 §6` |
| Better feedback on missing language labels | basic | TBD | 📐 (queued, #682) | `aidocs/14` |
| Refactor Neo4j representation of semantic annotations | older shape | TBD | 📐 (queued, #659) | `aidocs/14` |

## 8. Provenance / lineage

| Capability | Upstream | This fork | Status | Refs |
|---|---|---|---|---|
| OpenLineage events across the pipeline | none | TBD | 📐 (queued) | `aidocs/30` |
| `direction=upstream/downstream/both` lineage walk endpoint | none | TBD | 📐 | `aidocs/30 §4` |
| `sh.lineage.upstream(app_id, depth=N)` Python helper | none | TBD | 📐 | `aidocs/30 §5` |

## 9. Identifiers via `/v2/` payload kinds (designed, not yet shipped)

| Capability | Upstream | This fork | Status | Refs |
|---|---|---|---|---|
| HDF5 / HSDS as a payload kind (`HdfContainer` / `HdfReference`); `h5pyd` parity | none | TBD; HSDS sidecar + shared-Keycloak token relay | 📐 (queued, A5) | `aidocs/35` |
| Git integration (`GitReference`); 3 modes (loose / tracked / pinned-snapshot); commit-SHA in RO-Crate | none | TBD | 📐 (queued, G1) | `aidocs/38` |
| Templates feature (Templates Collection of DataObject blueprints; per-Collection allow-list) | none | TBD; replaces / supersedes upstream-aspirational L3 | 📐 (queued, T1) | `aidocs/39` |
| Process design + runtime in shepard core (`ProcessDefinition` + browser-hosted stepper) | SPW desktop only | TBD | 📐 (queued, PR1) | `aidocs/40 §2` |
| Snapshots (point-in-time, immutable, reproducible reads) | `Version` is a marker only | TBD; logical snapshots backed by entity revisions | 📐 (queued, V2) | `aidocs/41` |

## 10. User profile + settings

| Capability | Upstream | This fork | Status | Refs |
|---|---|---|---|---|
| ORCID iD on user profile (#29) | none | TBD; mod 11-2 checksum, no network | 📐 (queued, U1a) | `aidocs/36` |
| `displayName` override + audit-trail render switch (#694) | username only | TBD | 📐 (queued, U1b) | `aidocs/36` |
| `/me` route (split from Configuration) | mixed Configuration page | TBD | 📐 (queued, U1c) | `aidocs/36 §5` |
| Preferences (`theme`, `language`, `timeZone`, `dateFormat`, `defaultPageSize`, `defaultLandingPage`) via `SettingDescriptor` enum + typed map | none | TBD | 📐 (queued, U1d) | `aidocs/36 §3.2 / §7` |
| Avatar (shepard-uploaded → IdP `picture` → Gravatar tier) | none | TBD | 📐 (queued, U1e) | `aidocs/36 §3.1` |
| Secret-class settings (encrypted-at-rest with `~/.shepard/keys/secrets.key`) | none | TBD | 📐 (queued, U2-coupled) | `aidocs/36 §3.3` |

## 11. Lab journal

| Capability | Upstream | This fork | Status | Refs |
|---|---|---|---|---|
| Markdown body interpretation (CommonMark + GFM) | plain text | TBD | 📐 (queued, J1a) | `aidocs/37` |
| Inline `.ipynb` static render | none | TBD | 📐 (queued, J1b) | `aidocs/37` |
| "Open in Jupyter" deep link via `editor.preferredJupyter` | none | TBD | 📐 (queued, J1c) | `aidocs/37` |
| Edit history (append-only revisions) | write-once | TBD | 📐 (queued, J1d) | `aidocs/37` |
| Display perf for large lab-journal lists (#507) | flat scroll | TBD; gated on L6 pagination | 📐 (queued) | `aidocs/37` / #507 |

## 12. AI features

| Capability | Upstream | This fork | Status | Refs |
|---|---|---|---|---|
| OpenAI-compatible BYOK + admin-fallback infrastructure (per-user `ai.apiKey` / `ai.baseUrl` / `ai.model`) | none | TBD; **shepard ships zero models** | 📐 (queued, AI1a) | `aidocs/43 §4` |
| Anomaly detection on timeseries (rolling-median + isolation-forest) | none | TBD; pure-Python, LLM-independent | 📐 (queued, AI1b) | `aidocs/43 §3.1` |
| Channel-quality scoring | none | TBD | 📐 (queued, AI1c) | `aidocs/43 §3.2` |
| Embedding-based similarity (`/data-objects/{appId}/similar`) | none | TBD; needs `/v1/embeddings` endpoint | 📐 (queued, AI1d) | `aidocs/43 §3.5` |
| **Snap dashboards** — Claude-chat-style chat sidebar with closed tool-use catalogue + Vega-Lite v5 inline rendering | none | TBD; **headline killer feature** | 📐 (queued, AI1e) | `aidocs/43 §5.8` |
| Natural-language search | none | TBD | 📐 (queued, AI1f) | `aidocs/43 §5.1` |
| Lab journal authoring assist | none | TBD | 📐 (queued, AI1g) | `aidocs/43 §5.2` |
| Semantic-annotation suggestion | none | TBD | 📐 (queued, AI1h) | `aidocs/43 §5.4` |
| Auto-summarisation of run outcomes | none | TBD | 📐 (queued, AI1i) | `aidocs/43 §5.3` |
| RO-Crate description generation | none | TBD | 📐 (queued, AI1j) | `aidocs/43 §5.5` |
| Conversational lineage (chat over the lineage graph) | none | TBD | 📐 (queued, AI1k) | `aidocs/43 §5.6` |
| Notebook scaffolding | none | TBD | 📐 (queued, AI1l) | `aidocs/43 §5.7` |

## 13. Admin tooling

| Capability | Upstream | This fork | Status | Refs |
|---|---|---|---|---|
| Admin CLI (`shepard-admin`) — read-only commands | none | designed; phased L1 | 📐 (queued) | `aidocs/22` |
| Admin CLI cleanup of soft-deleted entities (TTL) | none | designed | 📐 (queued, L1 phase 2) | `aidocs/22 §4.1` |
| Admin CLI RO-Crate import / export | none | designed | 📐 (queued, L1 phase 3) | `aidocs/22 §4.7` |
| Admin CLI feature-toggle inspection / flipping (incl. profile-bound) | none | designed | 📐 (queued) | `aidocs/22 §4.6 / §4.6a` |
| `shepard-admin init` TUI wizard for first-run `.env` (Lanterna) | none | designed | 📐 (queued, L1 phase 1) | `aidocs/22 §4.11` |
| Universal TUI mode for every command (auto-fill from server state) | none | designed | 📐 (queued) | `aidocs/22 §4.x` |
| Env-driven auth discovery (`SHEPARD_HOST` / `SHEPARD_API_KEY`) for the CLI | none | designed | 📐 (queued, L1 phase 1) | `aidocs/22 §3.4` |
| Init wizard's OIDC sub-flow (Keycloak / Pocket ID / external w/ auto-discovery) | none | designed; depends on F8 (configurable claim path) for non-Keycloak | 📐 (queued) | `aidocs/22 §4.11a` |

## 14. RO-Crate optimisation

| Capability | Upstream | This fork | Status | Refs |
|---|---|---|---|---|
| Selective export ✓ (see §5 above) | GET-only | **✓** (R2 series shipped) | **✓ ↑** | §5 |
| Streaming RO-Crate export for large Collections | possible OOM | TBD | 📐 (queued) | `aidocs/31` |
| Long-running export pattern (job-id polling) | synchronous only | TBD | 📐 (queued) | `aidocs/32` |
| Reproducible-by-snapshot exports | n/a (no snapshots) | TBD; lands at V2d | 📐 (queued, V2d) | `aidocs/41 §5` |

## 15. API versioning policy

| Capability | Upstream | This fork | Status | Refs |
|---|---|---|---|---|
| `/shepard/api/...` byte-frozen with upstream | n/a (it IS upstream) | enforced via `CLAUDE.md` standing rule | **✓** | `CLAUDE.md` / `aidocs/34` |
| `/v2/...` reserved for this fork's additive surface | n/a | enforced; all new endpoints in design docs go here | **✓** (rule) / 📐 (endpoints follow as designed) | `CLAUDE.md` |
| Generated clients split (5.x compat tag vs 6.x next tag) | single track | TBD | 📐 (planned, `aidocs/40 §4`) | `aidocs/40 §4` |

## 16. Documentation

| Capability | Upstream | This fork | Status | Refs |
|---|---|---|---|---|
| GitHub Pages docs site (Jekyll under `docs/`) | none | shipped at https://noheton.github.io/shepard/ | **✓ ↑** | `docs/` |
| Free-test-deploy guide for Oracle Cloud Free Tier | none | shipped (`docs/deploy-oracle-free.md`) | **✓ ↑** | PR #1004 |
| Self-hosted-behind-Zoraxy guide (incl. existing-host dev workflow §5a) | none | shipped (`docs/deploy-self-hosted-zoraxy.md`) | **✓ ↑** | PR #1005 / PR #1016 |
| Live researcher-facing vision doc | none | shipped (`aidocs/42-vision.md`, Live status) | **✓ ↑** | `aidocs/42` |
| Live ecosystem doc (SPW + sTC + others) | none | shipped (`aidocs/40-ecosystem.md`) | **✓ ↑** | `aidocs/40` |
| Upstream upgrade-path tracker (admin-facing) | n/a | shipped (`aidocs/34-upstream-upgrade-path.md`, Live) | **✓ ↑** | `aidocs/34` |
| **This** fork-vs-upstream feature matrix (contributor-facing) | n/a | this doc | **✓ ↑** | this doc |
| LUMEN-inspired showcase seed + analysis notebook | none | shipped (`examples/seed-showcase/`) | **✓ ↑** | PR #1001 |
| Upstream-current parallel import script (`import_upstream.py`) for the same showcase data | n/a (the upstream itself) | shipped | **✓ ↑** | PR #1001 |

## 17. Companion ecosystem

| Tool | Upstream version | This fork status | Notes |
|---|---|---|---|
| `shepard-process-wizard` (desktop JavaFX) | upstream-only | unchanged compat (frozen API); future absorption into shepard core via PR1 designed | `aidocs/40 §2` |
| `shepard-timeseries-collector` (Java OPC/UA + MQTT + KUKA RSI) | upstream-only | 10 prioritised improvements documented; some need shepard-side dependencies (P14 ✓ shipped, A1b ✓ shipped, L2c queued) | `aidocs/40 §3` |
| Generated clients (`python` / `typescript` / `java`) | upstream OpenAPI | unchanged for `/shepard/api/`; `/v2/` will need a parallel client crank when L2d lands | `aidocs/40 §4` |
| `shepard-frontend` | upstream-only | `aidocs/33` analysis covers UX improvements; W11–W2 design ranked | `aidocs/33` |
| `shepard-dataship` (publication pipeline) | upstream-only | parked under `aidocs/16` X1 | `aidocs/16` X1 |

---

## Headline state of progress

**Shipped on this fork (vs upstream 5.2.0):** 6 DB-resilience improvements, 5 config/cache improvements, 1 API-key auth feature (L5), 4 security fixes (M2/M4/M5 + the L2a additive identifier substrate), 5 endpoint-additive features (P3, P14, R2/b/c/d/d2), the GitHub Pages docs site with three deploy guides, the LUMEN showcase seed + notebook, and **two Live tracking docs** (`aidocs/34` admin-facing + this matrix contributor-facing).

**In flight (agents dispatched):** C5 (Cypher injection fix; gates L2c) and L2b (V12 backfill of `appId`).

**Designed and queued (substantial):** the entire L2 chain after L2a (b/c/d/e), unified search + pagination (`aidocs/13`), semantic-annotation expansion (`aidocs/14`), HDF5/HSDS (A5), Templates (T1), Process design+runtime (PR1), Git integration (G1), User profile (U1), Lab journal v2 + Jupyter (J1), Snapshots (V2), AI features w/ snap-dashboards killer feature (AI1), Admin CLI (L1), permission-system evolutions (F1-F8), provenance (`aidocs/30`).

**Headline next-horizon line items** (per `aidocs/42` vision):
1. Snap dashboards (AI1e) — the killer feature
2. HDF5 / HSDS (A5)
3. Templates + processes (T1 + PR1)
4. User profile + ORCID (U1)

---

## Cross-references

- **Companion docs:** `aidocs/34` (admin-facing upgrade path), `aidocs/16` (live backlog), `aidocs/42` (researcher-facing vision), `aidocs/00-index` (full design corpus index).
- **Standing rules** in `CLAUDE.md`: API-version policy, vision-currency, upstream-upgrade-path tracking, this matrix.
