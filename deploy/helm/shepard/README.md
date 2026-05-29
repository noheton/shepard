# shepard Helm chart — Phase 1 skeleton

> ⚠ **PHASE 1 SKELETON — NOT PRODUCTION-READY.**
> Templates are stubs sufficient for `helm template --dry-run` to render the
> expected resource shape and surface the operator-facing `values.yaml`
> contract. Real, production-grade templates land in Phase 2
> (HELM-K8S-DEPLOY-03). Do not deploy this against a production cluster.

For docker-compose deployments (the dev / showcase / single-node default), use
`infrastructure/docker-compose.yml` and the plugin overlays under
`plugins/<id>/compose-profile.yml`. This Helm chart is the production-cluster
sibling.

## Audience

DLR institutes and other adopters running shepard on Kubernetes (k3s, kind,
managed clusters, ArgoCD / Flux workflows). Compose stays the default for
single-node / dev / showcase.

## Quick start (dry-run only — Phase 1)

```bash
cd deploy/helm/shepard

# Build sub-chart dependencies (file:// repositories under charts/).
helm dependency build

# Render against the bundled test values and inspect the resource set.
helm template shepard . -f tests/test-values.yaml | less

# Or run the bundled smoke check.
./tests/render-dry-run.sh
```

The smoke script asserts the reviewer-test gate from
[`aidocs/16-dispatcher-backlog.md`](../../../aidocs/16-dispatcher-backlog.md)
§HELM-K8S-DEPLOY-01: at least 10 separated K8s resources render.

## What's in the box (Phase 1)

| Component | Type | Status |
|---|---|---|
| `shepard-backend` Deployment + Service + ConfigMap | umbrella template | stub |
| `shepard-frontend` Deployment + Service | umbrella template | stub |
| Ingress (path-mount for `/v2`, `/shepard/api`, plugin paths) | umbrella template | stub |
| Migrations Job (conditional `migrations.mode=job`) | umbrella template | stub |
| `shepard-neo4j` StatefulSet | sub-chart | stub |
| `shepard-postgres` (+ TimescaleDB) StatefulSet | sub-chart | stub |
| `shepard-mongodb` StatefulSet | sub-chart | stub |
| `shepard-garage` StatefulSet | sub-chart | stub |
| `shepard-keycloak` Deployment | sub-chart | stub |
| `shepard-pgbouncer` Deployment | sub-chart | stub |

## What's NOT in the box (yet — Phase 2+)

- Real production templates with healthchecks, anti-affinity,
  PodDisruptionBudgets, resource limits, Secret-backed credentials,
  ServiceMonitor / PrometheusRule — see HELM-K8S-DEPLOY-03.
- Operator runbook (install / upgrade / rollback worked example on kind +
  k3s) — see HELM-K8S-DEPLOY-04 (queued; the README link below is a
  forward reference and will 404 until that phase ships).
- PM1f SidecarsAssembler — Helm-values emission for plugin sidecars —
  HELM-K8S-DEPLOY-05.
- CI integration (`chart-testing`, `helm lint`, template diff) —
  HELM-K8S-DEPLOY-06.
- Chart publishing to ghcr.io OCI + GitHub Pages Helm repo —
  HELM-K8S-DEPLOY-07.

## values.yaml shape — top-level keys

- `image.*` — backend + frontend image registry / repo / tag overrides.
- `oidc.*` — split-horizon `frontChannelIssuer` + `backChannelIssuer`
  (the J1e fix lesson; in-cluster Keycloak gets cluster-internal DNS,
  external Keycloak gets the public URL).
- `storage.s3.*` — endpoint / region / credentials. Default targets the
  bundled `shepard-garage`; supports external S3 (Ceph + RGW, AWS, MinIO,
  institutional storage) by disabling the sub-chart.
- `ingress.*` — host, TLS, className, annotations, `extraPaths` for plugin
  UIs (path-mount per CLAUDE.md "Always: mount plugin UI sidecars as paths,
  not subdomains").
- `migrations.mode` — `disabled` (default; in-startup, current behaviour),
  `init-container`, or `job`. The init-container / Job paths depend on
  HELM-K8S-DEPLOY-02 backend refactor.
- `plugins.<name>.enabled` — per-plugin toggle; `jupyter` and
  `tableContainer` listed as examples.
- `observability.serviceMonitor.enabled` — opt-in Prometheus integration.
- `resources.*`, `persistence.*` — per-substrate placeholder sizing.

See `values.yaml` for the full annotated surface.

## Cross-references

- Design doc: [`aidocs/ops/88-helm-deployment.md`](../../../aidocs/ops/88-helm-deployment.md)
- Backlog: [`aidocs/16-dispatcher-backlog.md`](../../../aidocs/16-dispatcher-backlog.md) §HELM-K8S-DEPLOY
- Operator runbook (forward reference, not yet written —
  HELM-K8S-DEPLOY-04): `docs/admin/runbooks/15-helm-deploy.md`
- Plugin SPI / sidecar contract:
  [`aidocs/platform/47-dev-experience-and-plugin-system.md`](../../../aidocs/platform/47-dev-experience-and-plugin-system.md)
- Upstream upgrade ledger row: see
  [`aidocs/34-upstream-upgrade-path.md`](../../../aidocs/34-upstream-upgrade-path.md)

## License

Apache-2.0 — same as the parent shepard project.
