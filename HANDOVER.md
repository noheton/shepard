# Shepard fork — dev machine handover context
**Date**: 2026-05-14  **Main branch**: `ef2b88a1` (FS1a)

## Repository
`noheton/shepard` at `/opt/shepard`

## What this fork is
Fork of `gitlab.com/dlr-shepard/shepard 5.2.0`. Upstream API (`/shepard/api/...`) frozen;
all additions under `/v2/`. Plugin-first architecture (ADR-0023): new features ship as
drop-in JARs in `plugins/`. See `CLAUDE.md` for standing rules.

## Current main state (merged)
| Feature | Key files |
|---------|-----------|
| PM1a–e  | Plugin SPI, admin REST/CLI, persistence, semver, signature — `backend/.../plugin/` |
| KIP1g   | KIP resolver as plugin — `plugins/kip/` |
| KIP1h   | LocalMinter as plugin + optional minter + versioned PIDs — `plugins/minter-local/` |
| KIP1d   | DataCite Fabrica DOI minter plugin — `plugins/minter-datacite/` |
| UH1a/b/c| Helmholtz Unhide plugin — `plugins/unhide/` |
| FS1a    | FileStorage SPI + GridFsFileStorage in-core — `backend/.../storage/` |

## Open PRs (ready for review/merge — merge in this order)
| PR | Feature | Migration |
|----|---------|-----------|
| **#1119 DX5a** | `make demo-up` single-command demo bootstrap | none |
| **#1120 ENT1a** | Entity versioning baseline (`:EntityVersion` graph + REST) | V35 |
| **#1121 DOC-DEPLOY** | 12-page admin deployment reference suite | none |
| **#1122 REF1c** | DBpedia Databus reference plugin | V37, V38 |
| **#1123 FS1b** | S3/Garage storage plugin | V36 |

## Migration sequence (post-merge)
V30 UnhideConfig → V31 Publication versionNumber → V32 PluginRuntimeOverride →
V33 DataciteMinterConfig → V34 FilePayload providerId → **V35 EntityVersion** →
**V36 S3StorageConfig** → **V37 DbpediaDatabusReference** → **V38 DbpediaDatabusConfig**

**Next available migration number: V39**

## Next dispatch queue (after merges)
1. ENT1b — CoW file payload snapshots (needs ENT1a + FS1a ✓)
2. ENT1c — publish ↔ versioning integration
3. ENT1d — Vue version selector dropdown
4. FS1c — Garage as infrastructure-local reference (needs FS1b)
5. REF1a — Reference plugin SPI (OGM challenge; see `aidocs/69`)

## Key architecture decisions
- All new REST endpoints under `/v2/` only
- Minter is optional (503 if none installed); LocalMinter in `plugins/minter-local/`
- FileStorage is optional (503 if none active); GridFS in-core default
- `aidocs/34` = upgrade ledger (admin-facing), `aidocs/44` = feature matrix (contributor-facing)
- `aidocs/16` = dispatcher backlog (what's queued, in-flight, shipped)
- Security gates: SpotBugs + findsecbugs + CodeQL + OWASP DC + Trivy + gitleaks

## Build commands (three-pass plugin dance)
```bash
cd backend && mvn -B -DnoPlugins -DskipTests -Dquarkus.build.skip=true install
cd cli && mvn -B -DnoPlugins -DskipTests install
cd plugins/unhide && mvn -B -DskipTests install
cd plugins/kip && mvn -B -DskipTests install
cd plugins/minter-local && mvn -B -DskipTests install
cd plugins/minter-datacite && mvn -B -DskipTests install
cd backend && mvn -B -DskipTests package
```

## Run unit tests
```bash
cd backend && mvn -B -P unit-test -DskipITs verify
```

## To resume work
Start a fresh `claude` session on the dev machine (do NOT use `-r <session-id>`):
```bash
cd /opt/shepard
claude
```
Paste this file's content as the first message.

## Worktrees note
All agent worktrees on the CI machine are under `/home/user/shepard/.claude/worktrees/`.
On the dev machine, worktrees will be created fresh under `/opt/shepard/.claude/worktrees/`.
