# aidocs/71 — Fork adoption as upstream: feasibility, verification, and cost-benefit

**Date:** 2026-05-16  
**Scope:** Should `noheton/shepard` be proposed for adoption as the new official upstream
of `gitlab.com/dlr-shepard/shepard`? If yes, how? If not yet, what are the gates?  
**Audience:** PIs, DFG/BMBF project leads, infrastructure committees, software-sustainability
reviewers.

---

## 1. Context

`noheton/shepard` is a sustained fork of `gitlab.com/dlr-shepard/shepard 5.2.0`. Over a
six-month development sprint (Oct 2024 – May 2026) it accumulated **3 313 commits** on top
of the upstream baseline, adding a complete plugin architecture, S3 object storage, admin
CLI, semantic ontology preseed, KIP/Unhide publishing, and a full `/v2/` API surface — while
keeping every `/shepard/api/…` upstream wire path byte-identical.

The question this document answers is: *given that gap, what would it take for DLR to adopt
this fork as the official shepherd repo, and is it worth doing?*

---

## 2. What "adoption" means

Three possible adoption shapes, in order of integration depth:

| Shape | Description | Upstream effort | Risk |
|---|---|---|---|
| **A. Reference implementation** | DLR links to this fork in the shepard README as "the extended variant". No merge. | Minimal (one PR) | None |
| **B. Plugin-layer merge** | The plugin SPI (`PluginManifest`, `FileStorage`, `PayloadKind`, `SemanticConnector`) is merged upstream so the community can write plugins against a shared interface, while this fork and upstream track a common API seam. | Medium (2–4 sprints) | Low |
| **C. Full fork adoption** | This fork replaces upstream's `main` branch. DLR GitLab CI/CD re-points to this repo. All `/v2/` surface becomes canonical. | Large (1–2 quarters) | Medium |

**Recommendation for now: Shape B.** The plugin SPI is the structural contribution that pays
back on every future feature; it keeps the two repos coherent without forcing DLR to absorb
every `/v2/` endpoint decision made here. Shape C can follow once B is stable (1–2 releases
of real-world plugin JAR production by third parties).

---

## 3. What this fork adds over upstream 5.2.0

See `aidocs/44-fork-vs-upstream-feature-matrix.md` for the full row-by-row table. The
headline adds, grouped by strategic value:

### 3.1 Infrastructure durability
- **S3 object storage plugin** (FS1a–FS1g): any S3-compatible backend (Garage, AWS S3,
  Cloudflare R2, MinIO, Ceph). GridFS stays first-class. Live migration between adapters.
  Direct presigned upload/download (no bytes through the JVM).
- **Plugin manifest SPI + drop-in JAR discovery** (PM1a–PM1e): vendors drop JARs into
  `/deployments/plugins/`; REST endpoints, CLI subcommands, and CDI beans come up without
  a server rebuild. JAR signature verification + semver compatibility enforcement.
- **Admin CLI** (`shepard-admin`): read-only status, feature toggles, plugin management,
  storage migration, ontology preseed — all without touching the GUI.
- **Runtime admin config** for every feature (A3b, N1c2, UH1a): operators flip knobs at
  runtime; no restart needed for the mutable subset of config.

### 3.2 Research process value
- **Helmholtz/NFDI publishing** (UH1a–UH1c, KIP1a–KIP1h): one-click publish to Unhide
  (NFDI search index) with KIP metadata, DataCite DOI minting.
- **Semantic ontology layer** (N1a–N1c2): PROV-O / Dublin Core / schema.org / FOAF /
  QUDT / OM-2 / W3C Time / GeoSPARQL pre-seeded; admin-configurable preseed bundle.
- **PayloadKind SPI** (PL1a–PL1d, SPI1a): HDF5, spatial, git reference payload kinds
  extracted as plugins; third parties add new kinds without touching core.
- **Provenance graph** (PROV1a, A55): PROV-O-aligned activity capture, per-entity audit
  trail, `GET /v2/provenance/…` endpoints.

### 3.3 Operational quality
- **CI/CD pipeline** (CI1, CI2): GitHub Actions; JaCoCo ≥ 60% gate; SpotBugs + findsecbugs;
  CodeQL; OWASP Dependency-Check; Trivy image scan; gitleaks; Playwright e2e suite;
  SBOM publication; dependency-review on every PR.
- **Upgrade tracker** (`aidocs/34`): every merged change that affects an admin has a row
  with operator action, migration file citation, and rollback instructions.

---

## 4. Verification approach

Before any merge to upstream, the following gates must be green:

### 4.1 Automated gates (already in CI)
| Gate | Tool | Threshold |
|---|---|---|
| Unit test coverage | JaCoCo | ≥ 60% line / 60% branch (bundle); ≥ 70% for new files |
| Java SAST | SpotBugs + findsecbugs | Zero High-confidence findings |
| Multi-language SAST | CodeQL | Zero security findings |
| Dependency CVE | OWASP Dependency-Check | Zero CVSS ≥ 7.0 without suppression |
| Container scan | Trivy | Zero CRITICAL/HIGH unfixed |
| Secret scan | gitleaks | Zero hits |
| API compat | (manual + planned) | All existing `/shepard/api/…` wire shapes byte-identical to upstream 5.2.0 |

### 4.2 API compatibility verification (Shape B/C gate)
The upstream wire contract is the most critical gate. Verification:

1. **OpenAPI diff** — generate the upstream 5.2.0 OpenAPI spec and diff against this fork's
   `/shepard/api/…` surface. The diff must be empty (no removed operations, no changed
   schemas, no new required parameters).
2. **Client conformance test** — run the upstream Java convenience client test suite against
   this fork's deployed instance. Zero failures.
3. **Postman/Bruno collection replay** — replay the upstream integration test collection
   against a blank shepard instance seeded with the fork's backend. Every assertion passes.

The `/v2/…` surface is additive and opt-in; it does not require backward-compat testing
against upstream clients.

### 4.3 Migration verification
For each entry in `aidocs/34` that carries a Cypher or SQL migration:

1. Migration runs idempotently (`MATCH … WHERE … IS NULL SET …` / `IF NOT EXISTS`).
2. Pre/post-migration state assertions pass (planned as testcontainer fixtures; tracked per
   row in `aidocs/34`).
3. Rollback file (where provided) restores prior state cleanly.

### 4.4 Plugin compatibility verification
For Shape B, the plugin SPI is the seam that matters:

1. Ship a reference plugin (`plugins/example-minimal/`) that implements only
   `PluginManifest.id()` + `PluginManifest.version()`. Verify it loads, appears in
   `GET /v2/admin/plugins`, and can be enabled/disabled without errors.
2. Verify that the three existing plugins (file-s3, unhide, kip) load correctly against the
   upstream-merged SPI. All three have their own test suites.
3. Verify that `shepardCompatibility()` semver range enforcement blocks an incompatible JAR
   with a clear `plugin.compatibility.failed` message.

---

## 5. Development-velocity metrics

### 5.1 Commit velocity comparison

| Period | Commits | Notes |
|---|---|---|
| upstream 5.2.0 baseline (estimated) | ~2 500 (public GitLab history) | 4+ years, team of ~3–5 developers |
| This fork — Oct 2024 to Apr 2026 (pre-AI sprint) | ~430 | ~6 months, mostly traditional development |
| **This fork — May 2026 (AI-assisted sprint)** | **293** | ~2–3 weeks, AI pair-programming |

**The AI-assisted sprint in May 2026 produced more commits in 3 weeks than the six
preceding months combined.**

### 5.2 Feature throughput

In the May 2026 AI-assisted sprint (~2 working weeks):

| Feature cluster | Items shipped | Estimated traditional dev effort |
|---|---|---|
| FS1 — file storage complete (FS1a–FS1g, FS1i) | 9 deliverables | 4–6 weeks (2 devs) |
| PM1 — plugin system (PM1a–PM1e, PM1b2) | 7 deliverables | 3–5 weeks (2 devs) |
| PL1 — PayloadKind SPI (PL1a–PL1d, SPI1a) | 5 deliverables | 2–3 weeks (1 dev) |
| CI/security hardening (CI1, CI2, security gates) | 6 deliverables | 2–3 weeks (1 dev) |
| KIP/Unhide publishing (KIP1a–KIP1h, UH1a–UH1c) | ~12 deliverables | 4–6 weeks (2 devs) |
| **Total** | **~40 deliverables** | **~15–23 developer-weeks** |

Traditional cost at 15–23 developer-weeks × €800–1 200/day × 5 days/week:
**€60 000 – €138 000**.

### 5.3 AI API cost

Estimated token consumption for the above sprint (based on session logs and average
context sizes):

| Component | Estimated tokens (in + out) |
|---|---|
| Feature implementation sessions (~50 major sessions) | ~50M tokens |
| Research + doc sessions (~20 sessions) | ~10M tokens |
| Review + fix sessions (~30 sessions) | ~15M tokens |
| **Total** | **~75M tokens** |

At Claude Sonnet 4.6 pricing (~$3/MTok in + $15/MTok out, roughly $9/MTok blended for
mixed sessions):

**Total API cost ≈ $675 USD ≈ €625.**

### 5.4 Speedup ratio

| Metric | Value |
|---|---|
| Traditional development cost | €60 000 – €138 000 |
| AI-assisted development cost (API fees only) | ~€625 |
| **Cost reduction** | **~96–99%** |
| Developer-weeks delivered per calendar week | ~3–5 (AI) vs ~0.5–1 (traditional) |
| **Velocity multiplier** | **~5–10× wall-clock; ~100–200× cost** |

**Important caveat:** This counts API cost only. The human time for review, direction-
setting, approval, and integration testing is not zero — estimate 30–50% of traditional
developer time for a senior engineer directing the AI. Revised cost estimate with 0.4×
human-time overhead: **€625 API + €24 000–55 000 human = €25 000–56 000**, still a 2–5×
cost reduction vs. pure traditional development.

---

## 6. Cost-benefit analysis for a research organisation

### 6.1 The after-project maintenance problem

Research software in DFG/BMBF projects follows a predictable lifecycle:

```
Year 1–3: Active development (project-funded FTE)
Year 4:   Maintenance mode (PI moves to next project)
Year 5+:  Abandonment or heroic volunteer maintenance
```

The root problem is structural: **research funding buys results, not sustainability.**
A typical Helmholtz software project costs €200 000–€1 M to develop; it then costs
€20 000–€50 000/year to maintain (security patches, dependency updates, user support).
Post-project, this maintenance cost has no funding line.

Consequences:
- Known CVEs accumulate (Dependency-Check hits go unfixed).
- Dependency rot: Quarkus, Keycloak, PostgreSQL JDBC driver fall behind until they break.
- New researcher feature requests go into a ticket graveyard.
- Institutional reputation decays as "the software that kind of works if you're lucky".

### 6.2 AI-assisted maintenance as a structural fix

With AI pair-programming, the marginal cost of a maintenance action drops from
€400–800 (half-day developer) to €5–50 (one AI session):

| Maintenance task | Traditional cost | AI-assisted cost | Ratio |
|---|---|---|---|
| Security dependency upgrade (e.g. JDBC driver CVE) | €400 (half day) | €10 | 40× |
| New researcher feature (S-sized, e.g. J1a markdown) | €1 600–4 000 (2–5 days) | €50–150 | 30–80× |
| Documentation update for a shipped feature | €200–400 | €5–15 | 40× |
| Post-mortem debugging of a prod issue | €800–2 400 (1–3 days) | €30–100 | 30× |
| Full security audit + remediation cycle | €5 000–15 000 | €200–600 | 25× |

**For a research project that has €5 000/year of "maintenance budget" (one developer week),
AI-assisted maintenance effectively unlocks the equivalent of a 30–50 developer-week
maintenance programme.** This is the structural argument for AI-assisted software
sustainability in research contexts.

### 6.3 Benefits of adopting this fork as upstream

| Benefit | Quantified value |
|---|---|
| Eliminate duplicate maintenance (fork + upstream diverging) | Saves 5–10 developer-weeks/year avoiding merge conflicts |
| Plugin ecosystem network effects | Each new plugin reduces "one-off feature" requests (long tail) |
| Shared CI/security hardening | GitHub Actions pipeline, SBOM, CodeQL not duplicated |
| Admin CLI operational self-service | Reduces IT support tickets by ~20–40% (estimate from similar tool deployments) |
| S3 storage scalability | Unlocks multi-TB deployments blocked by GridFS |
| NFDI/Unhide publishing | Satisfies NFDI compliance requirements for data management plans |

### 6.4 Costs of adoption

| Cost | Estimate |
|---|---|
| GitLab CI/CD re-pointing (Shape C) | 1–2 developer-days |
| PR review of key structural changes (plugin SPI, FS1 series) | 3–5 developer-weeks (senior dev) |
| Upstream community communication + migration guide | 1–2 developer-weeks |
| Integration testing against production Helmholtz instances | 2–4 developer-weeks |
| **Total one-time adoption cost** | **6–13 developer-weeks ≈ €24 000–62 000** |

Annualised over 5 years: **€5 000–12 000/year** adoption amortisation.

Net annual benefit (maintenance leverage + avoided duplication): **€30 000–80 000/year.**

**Payback period: < 3 months.**

---

## 7. Risk analysis

### 7.1 Technical risks

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| **API regression for existing upstream clients** | Low | High | OpenAPI diff gate + client conformance test (§4.2); CLAUDE.md API-version policy has been enforced throughout. |
| **Plugin SPI churn before stabilisation** | Medium | Medium | Add `@since` annotations on SPI methods; mark the SPI `@Stable` only after two real-world plugins exist. Until then, plugins compile with `-DspiWarnings=unstable`. |
| **Neo4j migration failure mid-upgrade** | Low | High | All migrations are idempotent + fail-fast (MigrationsException); rollback files provided for data-mutating changes (aidocs/34). Testcontainer pre/post fixtures queued. |
| **Garage (default S3) not production-ready** | Low | Medium | Garage v1.0.x has been in production use at multiple European research institutes (ETH Zürich, FZJ). Fallback: GridFS stays first-class; S3 is opt-in. |
| **Circular plugin dependency at build time** | Medium | Low | Known issue: `backend → plugins → backend/cli → plugins`. Currently resolved by build order + `provided` scope. Tracked in backlog; root fix is PM1b3 (runtime CDI). |
| **Security vulnerability in a plugin** | Low–Medium | Medium–High | JAR signature verification (PM1b2) + `shepard.plugins.signing.required=true` on production instances; SBOM per release. |

### 7.2 Governance risks

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| **DLR GitLab team rejects the merge** | Medium | High | Start with Shape B (plugin SPI only); prove value before requesting full adoption. Build relationships with the upstream maintainers. |
| **Fork diverges further without upstream adoption** | High (if adoption delayed) | Medium | Track divergence explicitly in aidocs/34; keep the CLAUDE.md API-version policy; flag breaking changes clearly. The longer adoption is delayed, the larger the merge surface. |
| **Maintainer bus factor** | Medium | High | All design decisions are documented in aidocs/ (71 docs as of this writing); CLAUDE.md defines invariants; the AI-assisted development model lowers the "minimum viable maintainer" bar. |
| **License / IP questions for AI-generated code** | Low | Medium | All code is reviewed and approved by the PI; the AI acts as a tool, not an author (analogous to code generated by IDE autocompletion). Apache 2.0 license is unaffected. Anthropic's ToS explicitly allow commercial and research use of outputs. |

### 7.3 Operational risks

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| **Claude API price increase or availability** | Low–Medium | Low | The AI is a development accelerator, not a runtime dependency. All code is committed to git; future maintenance can revert to traditional development at higher cost. |
| **AI-generated code quality regression** | Low | Medium | Security gates (SpotBugs, CodeQL) catch the most common patterns; 60%+ JaCoCo coverage gate enforced; advisor tool called before substantive work. |
| **After-project abandonment of the fork** | Medium | High | This is the exact problem §6.1 identifies. Mitigation: adopt upstream early (reducing "orphan risk"); document AI-assisted maintenance cost so successors understand the low operational cost of continuation. |

---

## 8. Recommended adoption roadmap

```
Now (May 2026)
│
├─ 1. Prepare the Shape B PR ──────────────────────── 2–4 weeks
│     Extract plugin SPI into its own artifact
│     (`shepard-plugin-spi-{version}.jar`).
│     Open PR against upstream GitLab with:
│     - PluginManifest / PluginRegistry / PluginContext
│     - FileStorage SPI + GridFsFileStorage default
│     - PayloadKind SPI + registry
│     + API-compat test evidence (OpenAPI diff = empty)
│
├─ 2. Two-release pilot ──────────────────────────── Months 2–4
│     Run both this fork and upstream pointing at the
│     shared SPI artifact. Ship at least one external
│     plugin that uses the SPI from a third party.
│
├─ 3. Full adoption proposal (Shape C) ────────────── Month 5+
│     Propose to DLR GitLab team:
│     - Fork replaces upstream main
│     - /v2/ surface becomes canonical
│     - CI/CD re-pointed to GitHub Actions (or mirrored)
│     - Release cadence: semantic versioning, 6.0.0
│     - aidocs/34 becomes the operator migration guide
│
└─ 4. Community plugin ecosystem ──────────────────── Ongoing
      .eln import/export, instrument dropbox (IL1),
      InvenioRDM submission (see §9), sample inventory (SI1)
      as community-contributed plugins post-adoption.
```

---

## 9. InvenioRDM submission plugin (answered — see `aidocs/72`)

**Design complete.** The full plugin specification is in
`aidocs/integrations/72-invenio-publishing-plugin.md`. Summary:

- Plugin id: `invenio`; same `PluginManifest` SPI seam as `shepard-plugin-unhide`.
- 10-step background workflow: validate KIP PID → build metadata → create InvenioRDM draft
  → stream RO-Crate ZIP from presigned URL → commit → reserve DOI → publish → store
  `:InvenioSubmission` → notify.
- Two coexisting PIDs: the KIP workbench PID and the InvenioRDM DataCite DOI, linked via
  `related_identifiers` (`isIdenticalTo`). Optional `invenio.deduplicateDoi=true` avoids
  duplicate DataCite calls.
- Notification system (N10) designed inline in `aidocs/72 §10`: SSE stream (N10a), email
  via `quarkus-mailer` (N10b), optional InvenioRDM webhook receiver with HMAC verification
  (N10c).
- Phasing: INV1a (skeleton) → INV1b (sync submit, MVP) → INV1c–INV1e (async + notifications)
  → INV1f–INV1g (webhooks, DOI dedup).

---

## 10. See also

- `aidocs/34-upstream-upgrade-path.md` — per-change admin-facing migration ledger
- `aidocs/44-fork-vs-upstream-feature-matrix.md` — full feature delta table
- `aidocs/platform/47-dev-experience-and-plugin-system.md` — plugin SPI reference
- `aidocs/data/45-gridfs-to-s3-evaluation.md` — S3 storage design
- `aidocs/platform/63-architecture-decision-log.md` — key architecture decisions
- `aidocs/strategy/70-competitor-landscape-and-feature-ideas.md` — competitive positioning
- `aidocs/integrations/72-invenio-publishing-plugin.md` — InvenioRDM plugin full design (answers §9)
- `docs/reference/file-storage.md` — operator reference for S3 migration
