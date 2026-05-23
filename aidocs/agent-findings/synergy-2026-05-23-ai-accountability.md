---
stage: fragment
last-stage-change: 2026-05-23
---

# S-08 — AI accountability dashboard: MCP × Permission audit log × F(AI)²R

## Synergy

Cross-join the **permission audit log** (F3, shipped) with the
**F(AI)²R `:AiActivity` nodes** (TPL9, designed) and the **MCP call
log** (shepard-plugin-mcp, shipped) by `actor` + timestamp. The
result is a per-agent activity ledger: what the AI touched, what
permissions it used, what claims it asserted, what artefacts it
produced — one queryable view. EU AI Act Article 50 + EASA Learning
Assurance + DLR FAIR mandate all consume the same dataset.

## Elements (named anchors)

- **Shipped feature:** Permission audit log (F3) —
  `aidocs/16 F3`, `aidocs/44 §2 row F3`,
  `backend/.../AdminPermissionAuditApi`,
  frontend `PermissionAuditLogPane.vue`.
- **Shipped feature:** ProvenanceCaptureFilter (PROV1a) — automatic
  `:Activity` capture per mutation.
- **Plugin (shipped):** `shepard-plugin-mcp` — task #30,
  `aidocs/platform/30`, `aidocs/platform/88`. MCP transport is
  OIDC-authenticated; agent identity is in the token.
- **Ontology (designed):** F(AI)²R (TPL9 in `aidocs/semantics/95`
  Part 15) — `fair2r:AIActivity`, `fair2r:hasAgent`,
  `fair2r:claimStatus` ladder.
- **Regulatory frame:** EU AI Act Art. 50 (August 2026 deadline),
  EASA AI Roadmap 2.0, DLR D4 mandate (MCP/Claude as first-class
  interface).
- **Memory:** `project_ai_human_collab_provenance` — three-mode
  attribution badges (🧑/🤖/🤝) per artefact.

## Why this is non-obvious

- Permission audit log was built for the security perimeter — "who
  granted/revoked permission to what?" The design (F3) does not
  mention AI agents.
- F(AI)²R was designed for *content* attribution — "what claims did
  the AI make?" It does not mention permissions.
- MCP plugin was designed for *transport* — "let agents call
  Shepard." It does not retain a structured call log.
- All three exist in the same instance, identified by the same
  OIDC actor URN, time-stamped against the same clock. They have
  never been joined.
- The cross-join surfaces a stratum nobody owns: **AI accountability
  as a queryable surface**. Compliance dashboards, incident
  forensics, contributor highlights — all flow from this single
  view.
- The EU AI Act deadline (August 2026) is < 3 months out at time of
  writing. Most platforms will surface AI attribution as an
  *afterthought metadata field*. Shepard can surface it as a
  *first-class queryable*.

## Concrete output

### 1. Materialised view: `:AgentActivityLedger`

Periodic Cypher job (every 5 min) builds a Neo4j view node per
agent per Collection per day:

```cypher
MATCH (act:fair2r:AIActivity)-[:WAS_ASSOCIATED_WITH]->(agent:Agent)
WHERE act.endedAtTime >= datetime() - duration('P1D')
WITH agent, date(act.startedAtTime) AS day, collect(act) AS activities
MATCH (perm:PermissionAuditEvent)
WHERE perm.actor = agent.actorUrn
  AND date(perm.at) = day
WITH agent, day, activities, collect(perm) AS perms
MERGE (ledger:AgentActivityLedger {agent: agent.actorUrn, day: day})
SET ledger.activityCount    = size(activities),
    ledger.permissionEvents = size(perms),
    ledger.grantedClaims    = size([a IN activities
                                    WHERE a.claimStatus
                                    = 'fair2r:human-confirmed']),
    ledger.unverifiedClaims = size([a IN activities
                                    WHERE a.claimStatus
                                    = 'fair2r:unverified'])
```

### 2. New admin endpoint

```http
GET /v2/admin/ai-accountability/ledger
  ?agent=<actorUrn>
  &from=<iso8601>
  &to=<iso8601>
  &collection=<appId>     # optional scope
```

Returns:

```json
{
  "agent": "urn:keycloak:gpt-4o-research-agent-001",
  "from": "2026-05-01T00:00:00Z",
  "to":   "2026-05-23T00:00:00Z",
  "ledger": [
    {
      "day": "2026-05-22",
      "activityCount": 47,
      "permissionEvents": 3,
      "grantedClaims": 12,
      "unverifiedClaims": 28,
      "mostFrequentMethod": "m4i:anomaly-detection-v3",
      "touchedDataObjects": 14
    }
  ]
}
```

`@RolesAllowed("instance-admin")` — admin-only by default; per-user
"my agents" view is a follow-up.

### 3. UI: AI Accountability tab in AdminPane

A new tab next to `PermissionAuditLogPane.vue` and `FeatureTogglePane.vue`.
Filterable by agent / time / collection. Each row clickable to a
drill-down showing the actual `:AiActivity` chain — what the agent
did, what it produced, what permissions it used.

### 4. Article 50 export shape

```http
GET /v2/admin/ai-accountability/export
  ?from=<iso8601>
  &to=<iso8601>
  &format=eu-ai-act-art50
```

Returns a JSON-LD document conformant to the EU AI Act Code of
Practice's recommended provenance shape (once published; the
current draft format works for v1). One file an auditor can ingest
verbatim.

### 5. The three-badge consequence

Per `project_ai_human_collab_provenance`, the UI carries 🧑 / 🤖 / 🤝
badges per artefact. The accountability ledger is the *aggregate*
surface across those badges. The single artefact carries the
badge; the dashboard carries the rollup.

## Real-world use case

**Persona:** the DLR compliance officer preparing for the
EU AI Act Article 50 August-2026 inspection. Today: they would have
to grep Java logs, cross-reference with manual notes, hope the
researchers remembered to document AI usage. After this synergy:
they open `/v2/admin/ai-accountability/ledger` filtered to "all
agents, last 18 months," export the JSON-LD, hand it to the
inspector. The provenance graph IS the evidence.

For a security incident — "did this AI agent escalate
permissions?" — the ledger immediately shows: agent X performed
N permission events on day D, here is the audit trail. The
EASA Learning Assurance auditor's question "what AI activities
went into training data preparation for model M?" is one SPARQL
query.

For DLR strategic narrative (`project_dlr_institutional_strategy`):
the D4 mandate "MCP / Claude as first-class interface" comes with
the implicit promise that AI use is accountable. The ledger is
how Shepard delivers that promise. Two of the audit findings
already produced — `aidocs/agent-findings/easa-ai-regulatory-positioning.md`
and `aidocs/agent-findings/easa-data-management-learning-assurance.md`
— call for exactly this shape.

## External evidence

- **EU AI Act Article 50 service desk** —
  [ai-act-service-desk.ec.europa.eu/en/ai-act/article-50](https://ai-act-service-desk.ec.europa.eu/en/ai-act/article-50)
  Takeaway: deployers of certain AI systems must "ensure individuals
  exposed are informed" and AI-generated content "marked in a
  machine-readable manner." The ledger IS the machine-readable
  surface; the badges ARE the per-artefact mark.
- ***Transparency Obligations for All AI Systems — Article 50 of the
  AI Act* (CCIA, December 2025)** —
  [ccianet.org/wp-content/uploads/2025/12/Transparency-Obligations-for-All-AI-Systems-Article-50-of-the-AI-Act-Written-by-Dr.-Joan-Barata-Mir.pdf](https://ccianet.org/wp-content/uploads/2025/12/Transparency-Obligations-for-All-AI-Systems-Article-50-of-the-AI-Act-Written-by-Dr.-Joan-Barata-Mir.pdf)
  Takeaway: detailed compliance reading from a legal-research
  perspective; confirms the "marking + deployer-level
  documentation" two-track structure that the ledger satisfies.
- ***Transparency as Architecture: Structural Compliance Gaps in
  EU AI Act Article 50 II* (arXiv 2603.26983, 2026)** —
  [arxiv.org/pdf/2603.26983](https://arxiv.org/pdf/2603.26983)
  Takeaway: the academic critique argues that platforms with
  per-artefact provenance but no aggregate accountability surface
  technically meet Article 50 but functionally do not. The ledger
  IS the aggregate surface that addresses the critique.
- ***Provenance Tracking in Large-Scale Machine Learning Systems*
  (arXiv 2507.01075, 2025)** —
  [arxiv.org/pdf/2507.01075](https://arxiv.org/pdf/2507.01075)
  Takeaway: the ML-systems community is converging on agent-level
  ledgers (vs. job-level lineage) as the audit primitive; Shepard's
  approach is consistent with the state of the art.

## Effort estimate

**S (small).** Components:

- F3 permission audit log — already shipped.
- ProvenanceCaptureFilter — already shipped.
- F(AI)²R `:AiActivity` capture (TPL9a) — designed; ~3 days of
  pre-seed + filter wiring.
- The materialised-view Cypher job + admin endpoint — ~3-5 days.
- UI tab in AdminPane — ~3 days.
- Article-50 JSON-LD exporter — ~2-3 days.

Net: ~2-3 weeks once F(AI)²R lands. The synergy is
near-shovel-ready; the gating dependency is the TPL9a F(AI)²R
pre-seed.

## Risk / counter-evidence

- The cross-join assumes a stable `actor` field across permission
  audit + activity + MCP transport. Today the permission audit
  uses Keycloak URN; MCP uses the OIDC token's `sub`; the activity
  filter uses the JWT principal. Mitigation: a one-time alignment
  pass to land on Keycloak URN everywhere (PROV1a already does
  this for activities).
- The materialised view runs every 5 min — for an instance with
  high-volume agents this could be expensive. Mitigation: per-day
  rollup is cheap; per-hour is not needed for compliance; UI can
  trigger ad-hoc refresh.
- Article 50's "machine-readable" requirement is broad; the JSON-LD
  shape may need adjustment once the Code of Practice publishes
  (~September 2026). Mitigation: keep the export endpoint
  format-parameterised (`format=eu-ai-act-art50` is one of N).
- Agents may use service-account OIDC tokens (one token per app, not
  per session), which loses the "which session did this?" axis.
  Mitigation: encourage per-session tokens for AI agents; document
  the service-account limitation in the admin guide.
- arXiv 2603.26983 critiques that machine-readable provenance
  alone is structurally insufficient — the platform must surface
  it usefully to non-experts. The UI tab + badge pattern address
  this; without UI work the ledger is just a JSON dump.
