---
layout: default
title: Security gates and posture
description: CI security gates (SpotBugs, CodeQL, OWASP, Trivy, gitleaks), SBOM, secret rotation.
stage: deployed
last-stage-change: 2026-05-23
audience: admin
permalink: /admin/security/
---

# Security gates and posture

This page covers the security posture of the running instance and the
CI gates that protect against shipping a broken one. For the auth model
(OIDC, API keys, roles) see [Authentication]({{ '/admin/auth/' | relative_url }}).

## CI security gates

Six gates wired into CI per `CLAUDE.md §"Always: keep the security gates green"`:

| Gate | What | Where | Severity threshold |
|---|---|---|---|
| **SpotBugs + findsecbugs** | Java SAST | `spotbugs:check` in `backend-ci.yml`; `Effort=Max`, `Threshold=High` | Any High-confidence finding fails the build |
| **CodeQL** | Multi-language SAST | `codeql.yml`; Java + JS/TS, `security-extended` query set | Findings flow to Security tab + inline PR annotations |
| **OWASP Dependency-Check** | Java SCA | `security.yml` weekly + on `pom.xml` / `poetry.lock` touch | Fails at `CVSS >= 7.0`; suppress in `backend/dependency-check-suppressions.xml` with CVE id + reasoning |
| **Trivy on GHCR images** | Container CVE scan | After each push in `build-images.yml` | Fails on `CRITICAL,HIGH` with `--ignore-unfixed`; weekly schedule re-checks |
| **gitleaks** | Secret scan | `security.yml` weekly + on push | Any leak fails |
| **dependency-review** | PR-time license + new-CVE check | `security.yml` on every PR that touches dependency manifests | Bans GPL / AGPL / SSPL families; suppress in `.github/dependency-review-config.yml` with justification |

Plus **SBOM** (CycloneDX) generated for every published image via
`anchore/sbom-action` in `build-images.yml` — uploaded as workflow
artefact + attached to GitHub releases.

A PR that introduces a finding from any of these gates must either fix
the issue or land a suppression with justification in the same PR.

## Posture in the running instance

### Secrets

- The shipped `infrastructure/.env.example` carries **public placeholder
  credentials** (`POSTGRES_*`, `NEO4J_PW`, `MONGO_PASSWORD`, etc.).
  Rotate **before** the first internet-exposed deploy. `aidocs/07` H8
  flags this explicitly.
- API keys minted by users are stored as bcrypt hashes; the plaintext is
  shown once at mint time and never again.
- HSDS Basic credentials (`HSDS_USERNAME`/`HSDS_PASSWORD`) live in `.env`
  and must be mirrored onto the backend
  (`SHEPARD_HDF_HSDS_USERNAME`/`PASSWORD`). The backend fails fast if
  `SHEPARD_HDF_ENABLED=true` with blank credentials.

### CORS

`quarkus.http.cors.origins=*` ships permissive. Tighten via
`QUARKUS_HTTP_CORS_ORIGINS` for internet-exposed deployments. See
[Configuration]({{ '/admin/config/' | relative_url }}).

### TLS

`caddy` terminates TLS on 80 / 443 / 443-UDP with automatic Let's
Encrypt. Configuration lives in `infrastructure/proxy/Caddyfile`; static
SSL material in `infrastructure/proxy/ssl`.

### Internal-only ports

Two ports must **not** be exposed externally:

- `neo4j` Bolt — `7687/tcp`
- `postgis` — `5433/tcp` (mapped to container 5432)

Verify with `docker compose port neo4j 7687` (should bind to internal
network only).

### Egress

The runtime makes **no outbound calls** other than:

- Semantic SPARQL endpoints when external `:SemanticConnector` repositories
  are queried (e.g. Ontobee).
- Webhook subscribers (when configured).

Air-gapped deployments are first-class supported.

## Disclosure

Security issues: see `SECURITY.md` at the repo root. Public disclosure
window: 90 days from first vendor contact, in line with industry norm.

## Suppression discipline

A `@SuppressFBWarnings`, OWASP suppression, or dependency-review
suppression must carry a **justification** comment naming the CVE / rule
id and the reasoning. Reviewers reject bare suppressions.

## See also

- [`SECURITY.md`](https://github.com/noheton/shepard/blob/main/SECURITY.md) — disclosure policy
- [`backend/dependency-check-suppressions.xml`](https://github.com/noheton/shepard/blob/main/backend/dependency-check-suppressions.xml) — current OWASP suppressions
- [`.github/dependency-review-config.yml`](https://github.com/noheton/shepard/blob/main/.github/dependency-review-config.yml) — license policy
