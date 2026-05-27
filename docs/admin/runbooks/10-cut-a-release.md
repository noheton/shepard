---
layout: default
title: "Runbook — Cut a shepard release"
description: "Pre-flight checklist, version bump, tag, image build, GitHub Release creation with SBOM attachment, and post-release verification. Supersedes docs/ops/cut-a-release.md."
stage: feature-defined
last-stage-change: 2026-05-26
audience: instance-admin
host: operator-machine
tested: "— (procedure derived from codebase; reviewed 2026-05-26)"
---

# Cut a shepard release

> **When to use this runbook**: You are a maintainer and need to cut a versioned
> release of the shepard fork. This runbook covers the full pre-flight checklist
> from `aidocs/strategy/85-github-project-management-policies.md §5`, the version
> bump, git tag, image push, GitHub Release creation, and SBOM attachment.

This runbook supersedes `docs/ops/cut-a-release.md` (which now redirects here).

---

## Prerequisites

- Write access to `github.com/noheton/shepard` (push tags + create releases).
- `gh` CLI authenticated (`gh auth status`).
- `docker` logged in to GHCR (`docker login ghcr.io`).
- `make` available; the Makefile in `/opt/shepard/` has the build targets.
- All PRs intended for this release have been merged to `main`.
- Working directory: `/opt/shepard/`.

---

## Pre-flight checklist (from `aidocs/strategy/85 §5`)

Run `scripts/trace-feature.sh <ID>` for every shipped feature in this release cycle.
Each feature must resolve across nine surfaces — if any returns empty, traceability
is broken; fix the missing surface before tagging.

```bash
# [operator-machine]
cd /opt/shepard

# For each shipped feature ID in this release (example: ADMIN-RUNBOOKS-LIBRARY)
bash scripts/trace-feature.sh ADMIN-RUNBOOKS-LIBRARY
```

Expected: all nine surfaces (`aidocs/16` backlog row, `aidocs/34` upgrade-tracker
row, `aidocs/44` feature-matrix row, `aidocs/42` vision update if user-visible,
doc-stage index, reference docs, tests, code commit, and GitHub Issue if applicable)
return non-empty for each shipped feature.

Verify each item in the checklist table:

| Gate | Command / check | Expected |
|---|---|---|
| `aidocs/34` upgrade-tracker | `grep "ADMIN-RUNBOOKS" aidocs/34-upstream-upgrade-path.md` | Row present |
| `aidocs/44` feature matrix | `grep "ADMIN-RUNBOOKS" aidocs/44-fork-vs-upstream-feature-matrix.md` | Row present, status `✓` |
| Doc-stage index clean | `python3 scripts/regenerate-doc-stage-index.py --check` | No drift |
| Model inventory | `grep "<release>" aidocs/data/00-model-inventory.md` | Updated for new entities |
| Security gates green | GitHub Actions → all workflows on `main` green | No failures |
| Coverage ≥ 60% | `mvn verify -pl backend -Djacoco.haltOnFailure=true` (local or CI) | BUILD SUCCESS |
| SBOM will be auto-attached | `aidocs/security/` — anchore/sbom-action in `build-images.yml` | Confirmed in workflow |
| `aidocs/42` vision current | Review `aidocs/42-vision.md` for this release's user-visible features | Accurate |

Fix any failing gate before proceeding.

---

## Steps

### 1. Determine the version number

Shepard follows CalVer (`YYYY.MM.PATCH`) or SemVer (`MAJOR.MINOR.PATCH`) — check
the most recent tag:

```bash
# [operator-machine]
git tag --sort=-version:refname | head -5
```

Determine the next version (e.g. `5.7.0` for a feature release, `5.6.1` for a
patch):

```bash
export VERSION="5.7.0"
```

### 2. Bump the version in pom.xml and package.json

```bash
# [operator-machine]
# Backend
sed -i "s|<version>.*-SNAPSHOT</version>|<version>${VERSION}</version>|" \
  backend/pom.xml

# Frontend
jq --arg v "${VERSION}" '.version = $v' frontend/package.json \
  > /tmp/pkg.json && mv /tmp/pkg.json frontend/package.json

git add backend/pom.xml frontend/package.json
git commit -m "chore(release): bump version to ${VERSION}"
```

### 3. Update `CITATION.cff` with the release commit SHA

Before tagging, pin the `commit:` field in `CITATION.cff` to the SHA of the
release commit. This makes GitHub's "Cite this repository" UI render a
commit-pinned citation for the tag.

```bash
# [operator-machine]
RELEASE_COMMIT=$(git rev-parse HEAD)
sed -i "s|^commit: .*|commit: \"${RELEASE_COMMIT}\"  # Updated to the tagged commit SHA by the release workflow (docs/ops/cut-a-release.md)|" \
  CITATION.cff
git add CITATION.cff
git commit -m "chore(release): pin CITATION.cff commit SHA to ${RELEASE_COMMIT}"
```

### 4. Update the energy estimation log

Per `feedback_energy_log_per_commit.md`:

```bash
# [operator-machine]
# Append a release-cycle entry to aidocs/sustainability/00-energy-estimation-log.md
# (estimate GPU/CPU hours for CI builds in this release cycle)
```

### 5. Tag the release

```bash
# [operator-machine]
git tag -a "v${VERSION}" -m "Release v${VERSION}"
git push origin main
git push origin "v${VERSION}"
```

### 6. Build and push images

The GitHub Actions workflow `build-images.yml` triggers on tag push and:
- Builds `shepard-backend` and `shepard-frontend` images.
- Pushes to `ghcr.io/noheton/shepard-backend:${VERSION}` and `...-frontend:${VERSION}`.
- Generates a CycloneDX SBOM via `anchore/sbom-action` and attaches it as a
  workflow artifact.

Monitor the Actions run:

```bash
# [operator-machine]
gh run watch --repo noheton/shepard
```

Expected: all jobs in `build-images.yml` succeed and the SBOM artifact is uploaded.

For a local build (e.g. to test before tagging):

```bash
# [operator-machine]
make build-backend build-frontend
```

### 7. Create the GitHub Release

```bash
# [operator-machine]
# Draft the release notes from merged PRs since the last tag
PREV_TAG=$(git tag --sort=-version:refname | sed -n '2p')
echo "Changes since ${PREV_TAG}:"
gh pr list --repo noheton/shepard --state merged --base main \
  --search "merged:>${PREV_TAG}" \
  --json number,title,author \
  | jq -r '.[] | "- #\(.number) \(.title) (@\(.author.login))"'
```

Create the release with the SBOM attached:

```bash
# [operator-machine]
# Download the SBOM from the Actions artifact
SBOM_ARTIFACT_ID=$(gh run list --repo noheton/shepard --workflow build-images.yml \
  --json databaseId,status \
  | jq -r '[.[] | select(.status == "completed")] | first | .databaseId')

gh run download "${SBOM_ARTIFACT_ID}" --repo noheton/shepard \
  --dir /tmp/sbom-artefacts/

gh release create "v${VERSION}" \
  --repo noheton/shepard \
  --title "Shepard v${VERSION}" \
  --notes-file /dev/stdin \
  /tmp/sbom-artefacts/*.json \
  <<'EOF'
## What's changed

<paste or auto-generate from PR list above>

## Admin notes

- See `aidocs/34-upstream-upgrade-path.md` for any operator actions required
  when upgrading from the previous release.
- If this release includes new Neo4j migrations, the backend will apply them
  automatically on first startup.

## SBOM

CycloneDX SBOM is attached as `shepard-sbom-*.json`.
EOF
```

### 8. Verify the release page

```bash
# [operator-machine]
gh release view "v${VERSION}" --repo noheton/shepard
```

Expected: release notes present, SBOM JSON attached as an asset.

### 9. Deploy to nuclide (if this release goes to production)

```bash
# [nuclide]
cd /opt/shepard/infrastructure
# Update docker-compose.override.yml to pin the new version tag, then:
make redeploy
```

Follow `docs/admin/runbooks/01-generic-cube-hotpatch.md` for the cube instance.

### 10. Post-release smoke test

```bash
# [operator-machine]
curl -fsS "${API_BASE}/shepard/api/healthz/ready" | jq '.status'
# Expected: "UP"

curl -fsS "${API_BASE}/shepard/api/info" | jq '.version'
# Expected: "${VERSION}"
```

---

## Rollback

If the release image is defective:

1. Pin the previous tag in `docker-compose.override.yml` on both nuclide and cube.
2. `make redeploy` on both hosts.
3. Delete the GitHub Release if it was published (keep the tag — preserves the
   git history reference).
4. File a fix PR, cut a patch release.

---

## End-state verification

```bash
# [operator-machine]
gh release view "v${VERSION}" --repo noheton/shepard \
  | grep -E "^(tag|assets)"

docker pull ghcr.io/noheton/shepard-backend:${VERSION} \
  && docker inspect --format='{{index .RepoDigests 0}}' \
     ghcr.io/noheton/shepard-backend:${VERSION}
```

Expected: release tag matches; image digest is printed (proving it was pushed to GHCR).

---

## Reference docs

- `aidocs/strategy/85-github-project-management-policies.md §5` — the authoritative
  pre-flight checklist.
- `aidocs/34-upstream-upgrade-path.md` — operator upgrade ledger; must have a row
  for each admin-visible change in this release.
- `aidocs/44-fork-vs-upstream-feature-matrix.md` — contributor progress matrix.
- `docs/ops/cut-a-release.md` — redirect stub (this runbook supersedes it).
- Tracked: `ADMIN-RUNBOOKS-LIBRARY` in `aidocs/16-dispatcher-backlog.md`.
