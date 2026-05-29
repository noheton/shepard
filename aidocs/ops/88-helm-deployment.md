---
stage: feature-defined
last-stage-change: 2026-05-29
---

# 88 — Helm chart for Kubernetes deployment

**Status.** Phase 1 (HELM-K8S-DEPLOY-01) shipped 2026-05-29: chart
skeleton + sub-chart skeletons + values.yaml shape + reviewer-test
smoke. Phase 2+ queued.
**Audience.** Operators (DLR institute IT, external adopters) running
shepard on Kubernetes; contributors maintaining the chart.
**Relates to.** `aidocs/16 §HELM-K8S-DEPLOY` (backlog), `aidocs/34`
upstream upgrade ledger (HELM-K8S-DEPLOY-01 row),
`aidocs/44 §13d` (deployment shapes), `aidocs/platform/47` (plugin SPI),
CLAUDE.md "## Always: mount plugin UI sidecars as paths, not subdomains".

---

## 1. Motivation

Shepard today deploys via
`docker compose -f infrastructure/docker-compose.yml [+ plugin overlays]`
on a single host. That shape is correct for the dev box, the showcase
laptop, and the single-node institutional pilot — three deployments
where compose is the most direct path from "git clone" to "running
shepard".

For institutes evaluating adoption on a multi-node K8s cluster — DLR
institutional infrastructure is increasingly K8s + ArgoCD; partner
institutions running on managed cloud K8s; PIs delegating cluster
operations to a central IT team — compose is the wrong shape. A real
Helm chart provides:

1. **Idiomatic K8s primitives** — StatefulSets for stateful substrates,
   PVCs sized per substrate, ConfigMaps for non-secret config, Secrets
   for credentials, Ingress for path-routing, NetworkPolicy for
   substrate isolation.
2. **Operator-side selectivity** — disabling a sub-chart when the
   operator brings their own managed Postgres / S3 / Keycloak is a
   `--set postgres.enabled=false` flag, not a compose-file diff.
3. **ArgoCD / Flux compatibility** — declarative ops; one PR moves the
   cluster.

Compose stays the dev / showcase / single-node default. Helm is the
production-cluster sibling. Operators choose; neither shape replaces
the other.

## 2. Reuse survey

Before deciding to ship our own chart, the following alternatives were
considered:

- **Kompose** (`compose.yml → manifests`). Mechanical translation; loses
  plugin overlays, doesn't yield idiomatic K8s primitives (Deployments
  instead of StatefulSets for substrates, no PVCs, no Ingress
  abstraction). Rejected — output is still a manual rewrite away from
  production.
- **Upstream Helm charts** for substrates — `bitnami/postgresql`,
  `bitnami/mongodb`, `neo4j/helm-charts` (standalone + cluster modes),
  Garage's own community chart, `bitnami/keycloak`. **All re-used** —
  Phase 2 sub-charts wrap these upstreams rather than reinventing. The
  skeleton sub-charts in Phase 1 are placeholders for that wrapping.
- **A K8s Operator** (KubeBuilder + CRDs). Provides day-2 ops
  imperatives (`shepard backup`, `shepard scale`, `shepard upgrade`).
  Higher engineering cost; not yet justified by adoption demand. Helm
  first; Operator later if needed.

## 3. Umbrella structure

```
deploy/helm/shepard/
├── Chart.yaml                          # umbrella
├── values.yaml                         # full operator-knob surface
├── README.md                           # operator-facing intro
├── tests/
│   ├── test-values.yaml                # exercises every conditional
│   └── render-dry-run.sh               # Phase-1 reviewer-test smoke
├── templates/
│   ├── _helpers.tpl
│   ├── NOTES.txt
│   ├── backend-deployment.yaml
│   ├── backend-service.yaml
│   ├── backend-configmap.yaml
│   ├── frontend-deployment.yaml
│   ├── frontend-service.yaml
│   ├── ingress.yaml                    # conditional on .Values.ingress.enabled
│   └── migrations-job.yaml             # conditional on .Values.migrations.mode == "job"
└── charts/
    ├── shepard-neo4j/                  # graph substrate
    ├── shepard-postgres/               # TimescaleDB + pgvector
    ├── shepard-mongodb/                # document substrate
    ├── shepard-garage/                 # Garage S3 (canonical S3 default)
    ├── shepard-keycloak/               # identity
    └── shepard-pgbouncer/              # PG connection pooler
```

Every sub-chart carries its own `Chart.yaml` (`version: 0.1.0`,
`appVersion: <substrate version>`, `kubeVersion: >= 1.28.0-0`), a
`values.yaml` of substrate-specific knobs, and one `templates/`
StatefulSet or Deployment stub.

The umbrella's `Chart.yaml` `dependencies:` block lists each sub-chart
with `condition: <substrate>.enabled`. Each substrate can be disabled
independently when the operator brings their own external instance —
the `values.yaml` `externalServices.<substrate>` blocks then carry the
host / Secret references.

## 4. Sub-chart inventory

| Sub-chart | Substrate | Phase 1 stub kind | Phase 2 (HELM-K8S-DEPLOY-03) target |
|---|---|---|---|
| `shepard-neo4j` | Neo4j 5.x + n10s + APOC | `StatefulSet` stub | wrap `neo4j/helm-charts` standalone, preconfigure plugins |
| `shepard-postgres` | TimescaleDB 2.x on PG 16 + pgvector | `StatefulSet` stub | wrap `bitnami/postgresql` with init-container for extension load |
| `shepard-mongodb` | MongoDB 7.x | `StatefulSet` stub | wrap `bitnami/mongodb` with replica-set bootstrap |
| `shepard-garage` | Garage 1.x (`dxflrs/garage`) | `StatefulSet` stub | 1-node dev + 3-node HA modes; bucket bootstrap Job |
| `shepard-keycloak` | Keycloak 25.x | `Deployment` stub | wrap `bitnami/keycloak` with realm-import ConfigMap |
| `shepard-pgbouncer` | PgBouncer 1.23 | `Deployment` stub | ConfigMap with `pgbouncer.ini` + `userlist.txt` from PG Secret |

The umbrella's own `templates/` ships the **shepard-specific** shape —
backend Deployment, frontend Deployment, Ingress, optional migrations
Job. The sub-charts ship the substrate shapes. This separation lets a
sub-chart be replaced wholesale (e.g. swap the Garage sub-chart for an
external S3 reference) without touching the umbrella.

## 5. `values.yaml` shape

The full annotated shape lives in `deploy/helm/shepard/values.yaml`.
Top-level keys, with the design intent behind each:

### 5.1 `image.*`

`registry` + per-component `repository` + `tag` overrides. Defaults
target `ghcr.io/dlr-shepard/shepard-{backend,frontend}` with
`tag: ""` → falls back to `.Chart.AppVersion` (currently
`6.0.0-SNAPSHOT`). `pullSecrets` for private registries; `pullPolicy`
defaults to `IfNotPresent`.

### 5.2 `oidc.*` — split-horizon

The J1e fix (2026-05-29) demonstrated that some plugin sidecars need
the **server-side** OIDC issuer URL to be **different** from the
**browser-side** issuer URL — the browser hits a public Keycloak host
(`https://auth.example.com/realms/shepard`), the backend reaches the
same realm via an in-cluster Service DNS
(`http://shepard-keycloak.svc.cluster.local:8080/realms/shepard`). The
JWT `iss` claim is always the browser-facing URL; the backend's
back-channel call to the `well-known` JWKS endpoint is what flexes.

The chart exposes both:

```yaml
oidc:
  frontChannelIssuer: "https://auth.example.com/realms/shepard"
  backChannelIssuer: ""  # empty => same as frontChannelIssuer
```

When in-cluster Keycloak is used, the back-channel takes the Service
DNS. When an external (existing institutional) Keycloak is used, both
URLs are typically the public one.

### 5.3 `storage.s3.*` — external storage override

Default points at the bundled `shepard-garage` Service. To use external
S3 (Ceph + RGW, AWS S3, MinIO, institutional managed S3), disable the
sub-chart (`garage.enabled: false`) and set:

```yaml
storage:
  s3:
    endpoint: "https://s3.cluster.example.com"
    region: "eu-west-1"
    existingSecret: "shepard-s3-creds"
    forcePathStyle: true     # required for Garage / MinIO / RGW
```

`existingSecret` is the preferred shape — credentials never appear in
`values.yaml` directly. Inline `accessKey` + `secretKey` are accepted
as the cheap path for dev clusters; production-grade Phase 2 will
warn or refuse.

### 5.4 `ingress.*` — path-mount default

Per CLAUDE.md "## Always: mount plugin UI sidecars as paths, not
subdomains", every plugin UI gets a `path: /<plugin>` on the **same**
Ingress host. JupyterHub's path-mount under `/jupyterhub` is the
template the J1e fix established; future TableContainer admin pane,
visualisation plugin UIs, MCP browser shelf — all path-mount.

```yaml
ingress:
  enabled: true
  className: nginx
  host: shepard.example.com
  tls:
    enabled: true
    secretName: shepard-tls
  extraPaths: []   # Phase 4 (HELM-K8S-DEPLOY-05) auto-populated by PM1f
```

The `extraPaths` array lets an operator hand-add plugin paths in Phase
1; Phase 4 has the PM1f SidecarsAssembler emit these from the plugin
manifest so the chart consumer doesn't maintain them by hand.

The path-mount default trades off vs. subdomain mounting (separate
DNS A record, separate TLS cert, separate Caddy/Ingress host block):
path-mount wins on cookie-domain sharing (cleaner SSO — the Keycloak
session cookie is already valid on the host), trivial deployment (one
Ingress rule per plugin), and operator simplicity (no DNS coordination
with the cluster admin per plugin).

### 5.5 `migrations.*`

The current `MigrationsRunner` (`backend/src/main/java/.../migration/`)
runs Neo4j + Flyway migrations during backend startup with fail-fast
on error. In K8s the idiomatic shape is an init-container or a Job
that gates the Deployment. The chart exposes three modes:

```yaml
migrations:
  mode: disabled           # backend handles in-process at startup (current)
  # or "init-container":   # backend Deployment gains an init-container
  # or "job":              # separate pre-upgrade Hook Job (default for Phase 2)
  backoffLimit: 0          # fail-fast per CLAUDE.md
  ttlSecondsAfterFinished: 3600
```

**Phase 1 defaults `mode: disabled`** so the chart works against the
current backend image without backend-side changes.
HELM-K8S-DEPLOY-02 ships the backend refactor that makes
`init-container` and `job` modes real; Phase 2 (`HELM-K8S-DEPLOY-03`)
flips the default to `job` once the refactor is in.

The migration shape must stay **idempotent** and **fail-fast** per
CLAUDE.md "## Always: maintain the upstream upgrade path" — both
properties are preserved by `MigrationsRunner` today and must be
preserved by the init-container / Job refactor.

### 5.6 `plugins.<plugin>.enabled`

Per-plugin toggle. Phase 1 lists `jupyter` and `tableContainer` as
examples; Phase 4 has PM1f assemble this section dynamically from the
plugin manifest. Each plugin sub-section gates the inclusion of the
plugin's sidecar StatefulSet / Deployment + its Ingress path entry +
its substrate dependencies (e.g. `tableContainer.enabled: true`
implicitly enables a Postgres schema bootstrap Job).

### 5.7 `observability.*`

`serviceMonitor.enabled` flips on the Prometheus integration (assumes
the cluster runs the Prometheus Operator). `podAnnotations` carries
the legacy Prometheus scrape annotations for non-Operator
deployments. ServiceMonitor + PrometheusRule resources land in Phase
2 — they reference the alert rules already filed in `aidocs/ops/77`.

### 5.8 `persistence.*` + per-substrate `resources.*`

Per-substrate sizing knobs. Phase 1 carries placeholders matching the
docker-compose defaults; production sizing guidance lives in the
operator runbook (HELM-K8S-DEPLOY-04, queued).

## 6. How Phase 2+ builds on this skeleton

| Phase | Backlog row | What lands |
|---|---|---|
| Phase 1 | HELM-K8S-DEPLOY-01 | **Shipped 2026-05-29.** Skeleton, values surface, reviewer-test smoke |
| Phase 2a | HELM-K8S-DEPLOY-02 | Backend `MigrationsRunner` refactor: init-container OR Job mode |
| Phase 2b | HELM-K8S-DEPLOY-03 | Real templates: healthchecks, anti-affinity, PDB, Secret refs, ServiceMonitor wiring |
| Phase 3 | HELM-K8S-DEPLOY-04 | Operator runbook in `docs/admin/runbooks/15-helm-deploy.md` (kind + k3s worked example) |
| Phase 4 | HELM-K8S-DEPLOY-05 | PM1f SidecarsAssembler emits Helm values for enabled plugin sidecars (first target: JH) |
| Phase 5 | HELM-K8S-DEPLOY-06 | CI gate: `chart-testing`, `helm lint`, template diff against previous release |
| Phase 6 | HELM-K8S-DEPLOY-07 | Publish chart as OCI artefact on ghcr.io + GitHub Pages Helm repo index |

The Phase 1 skeleton is **load-bearing for every later phase**:

- The `values.yaml` contract crystallises the operator-knob surface
  now, so Phase 2 templates expand into a known shape rather than
  re-litigating the knobs.
- The sub-chart split (umbrella + 6 substrate sub-charts) makes Phase
  2 work parallelisable — backend templates and Postgres templates
  can land in different PRs without coordination on the umbrella's
  shape.
- The path-mount Ingress shape commits the plugin UI mount pattern to
  the chart's vocabulary now; Phase 4 (PM1f assembler) walks into a
  ready API surface (`ingress.extraPaths`) rather than having to
  invent one.

## 7. Plugin-SPI cross-reference

Per CLAUDE.md "## Always: think plugin-first": the chart treats every
plugin as an independently togglable add-on with its own sidecar +
Ingress entry. The plugin manifest from
[`aidocs/platform/47-dev-experience-and-plugin-system.md`](../platform/47-dev-experience-and-plugin-system.md)
§2.6 declares each plugin's sidecar shape; PM1f SidecarsAssembler
(Phase 4 dependency) walks the manifest at chart-render time and
emits per-plugin StatefulSet / Deployment + Ingress path entries.

Until PM1f ships, an operator enables a plugin by manually setting
`plugins.<id>.enabled: true` and adding its path entry to
`ingress.extraPaths`. Phase 1's `plugins.jupyter.enabled` is the
worked example.

## 8. Path-mount default — cross-reference

CLAUDE.md "## Always: mount plugin UI sidecars as paths, not
subdomains" applies to K8s Ingress rules identically to its
docker-compose / Caddyfile origin. The chart's Ingress template
defaults every plugin path to `path: /<plugin>` with
`pathType: Prefix`; the corresponding plugin's
`base_url` / `--server.baseUrlPath` setting must match. Cookie scope
on plugin session cookies must be `Path=/<plugin>` to avoid
collisions with shepard's own cookies on the shared host.

The Phase 1 Ingress template hard-codes the path-mount shape for
backend (`/shepard/api`, `/v2`), frontend (`/`), and JupyterHub
(`/jupyterhub`) as the worked example. Phase 4 generalises through
`PM1f`.

## 9. Reviewer-test gate

```bash
cd deploy/helm/shepard
helm dependency build       # warns on file:// sub-chart packaging — fine for Phase 1
helm template . -f tests/test-values.yaml | grep -c '^---'
```

Must produce **≥ 10** separated resources. Current Phase 1 skeleton
renders **13**:

| Kind | Source |
|---|---|
| ConfigMap | `templates/backend-configmap.yaml` |
| Service (backend) | `templates/backend-service.yaml` |
| Service (frontend) | `templates/frontend-service.yaml` |
| Deployment (backend) | `templates/backend-deployment.yaml` |
| Deployment (frontend) | `templates/frontend-deployment.yaml` |
| Deployment (keycloak) | `charts/shepard-keycloak/templates/deployment.yaml` |
| Deployment (pgbouncer) | `charts/shepard-pgbouncer/templates/deployment.yaml` |
| StatefulSet (neo4j) | `charts/shepard-neo4j/templates/statefulset.yaml` |
| StatefulSet (postgres) | `charts/shepard-postgres/templates/statefulset.yaml` |
| StatefulSet (mongodb) | `charts/shepard-mongodb/templates/statefulset.yaml` |
| StatefulSet (garage) | `charts/shepard-garage/templates/statefulset.yaml` |
| Ingress | `templates/ingress.yaml` |
| Job (migrations) | `templates/migrations-job.yaml` |

The bundled `tests/render-dry-run.sh` enforces this gate locally; CI
wiring lands in HELM-K8S-DEPLOY-06.

## 10. Cross-references

- `aidocs/16-dispatcher-backlog.md §HELM-K8S-DEPLOY` — backlog rows
  HELM-K8S-DEPLOY-01..07.
- `aidocs/34-upstream-upgrade-path.md` — HELM-K8S-DEPLOY-01 ledger row
  (additive operator surface; ZERO upgrade action required for
  existing compose users).
- `aidocs/44-fork-vs-upstream-feature-matrix.md §13d` — deployment-shape
  matrix row.
- `aidocs/platform/47-dev-experience-and-plugin-system.md §2.6` —
  plugin sidecar declaration contract; PM1f assembler dependency for
  Phase 4.
- `aidocs/ops/77-k6-performance-metrics.md` — alerting rules that the
  Phase 2 PrometheusRule resources reference.
- `CLAUDE.md "## Always: mount plugin UI sidecars as paths, not
  subdomains"` — the path-mount default governing Phase 4 Ingress
  rules.

## 11. Out of scope

- A K8s Operator (CRDs + reconciler loop) for cluster-day-2 ops
  (upgrades, backups, scaling). Defer to a follow-on if adoption
  demand surfaces it.
- ArgoCD / Flux deployment automation. Chart consumers wire those.
- Multi-cluster federation. Single-cluster only for v1.
- Backups. Out of scope for the chart; admin runbook (Phase 3)
  references substrate-native backup tooling
  (`neo4j-admin database backup`, `pg_dump`, `mongodump`,
  `garage backup`).

## 12. Open questions

- **Bitnami licence stance.** Phase 2 likely wraps `bitnami/postgresql`
  and `bitnami/keycloak`. Bitnami's recent licence changes around
  `bitnamilegacy/` images need a contributor-side decision before
  Phase 2 commits to those upstreams. Alternatives:
  `cloudnative-pg/cloudnative-pg`, `keycloak/keycloak-operator`.
  Track in HELM-K8S-DEPLOY-03 design-time review.
- **Garage HA topology in cloud K8s.** Garage's 3-node HA mode wants
  node-local storage on different nodes; cloud K8s often gives only
  network-attached storage. Document the trade-off in the Phase 3
  runbook; default the chart to 1-node mode for dev clusters.
- **Backend `MigrationsRunner` refactor — init-container vs. Job.**
  HELM-K8S-DEPLOY-02 design pass will pick. Job has cleaner
  rollback semantics (the Job either succeeds or the Helm release
  fails); init-container is simpler but couples to the backend pod
  lifecycle. Likely answer: ship both modes; default to Job.
