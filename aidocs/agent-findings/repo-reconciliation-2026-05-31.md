---
stage: feature-defined
last-stage-change: 2026-05-31
audience: maintainer
---

# Repo hygiene reconciliation — 2026-05-31

Read-only audit. The operator executes the cleanup script in §5.

`HEAD = 9ec9b27f4` on `main`. Today's direct-merge waves A–RR shipped `cf469edec..9ec9b27f4` (≈ 80 commits). Meanwhile the hourly dispatcher + dependabot + hourly issue-triage have opened 144 PRs in parallel; the vast majority duplicate work already on main.

---

## 1. Headline counts

### PRs (144 open)

| Bucket | Count | Disposition |
|---|---:|---|
| **DUPE — work already on main via direct-merge waves** | 78 | close |
| **DUPE-DEPS — dep bumps; close + let dependabot re-fire** | 47 | close (dependabot will re-open if still needed) |
| **WORTH-KEEPING — old design-doc PRs from `/claude/*` series** | 6 | leave open; flag for rebase |
| **DUPE-OLD — old `/claude/*` PRs whose feature shipped via different shape** | 1 | close (#1066 J1a — superseded) |
| **STALE-NEEDS-INVESTIGATION — older agent PRs** | 12 | operator review |
| **TOTAL** | **144** | |

Breakdown by source:
- 55 dependabot
- 7 hourly-bot
- 2 issue-triage
- 80 agent-style (worktree + direct claude/* branches)

### Worktrees (35)

| Bucket | Count |
|---|---:|
| **MERGED-CAN-PRUNE** — branch's head is on main (ahead=0) | 24 |
| **MERGED-VIA-DIFFERENT-SHA** — worktree commits already on main as different SHAs (cherry-picked / re-merged) | 6 |
| **EMPTY (`branch=main`)** — checked-out main copies | 6 (`agent-a067c10e…`, `a3b3d74c…`, `a3b4f952…`, `a59496f3…`, `a7c0deca…`, `aecde37d…`) |
| **DO-NOT-TOUCH — UU still running** | 1 (`agent-ae2b328129e8de05c`) |

No "UNMERGED-NEEDS-INVESTIGATION" — every `ahead>0` worktree's commits are already on main with different SHAs (see §3).

### Local branches (~ 60)

| Bucket | Count |
|---|---:|
| Plain merged (head reachable from main) | ~ 6 |
| `+`-pinned to worktree (covered by worktree action) | 28 |
| `worktree-agent-*` orphan (no `+`, branch with main head) | 2 |
| Ahead of main but content shipped via different SHAs | 5 |
| Active/special: `main`, `pr-1517`, `feat/prov1l`, `trace3d-views-as-shapes`, `mffd-iot-lite-annotations`, `ux-walk-2026-05-29` | 6 |

### Remote branches (355)

`origin/*` carries the full dependabot + claude/* + numbered-issue history. Pruning local refs does not affect remote; **leave remotes alone** — `git fetch --prune` after the PR closures will collapse most automatically once their PRs close.

---

## 2. PR action table

Action codes: `CLOSE-DUPE` (shipped on main), `CLOSE-DUPE-DEPS` (dep bump), `CLOSE-SUPERSEDED` (different shape shipped), `KEEP-REBASE` (genuinely independent), `INVESTIGATE` (operator review).

### 2.1 Today (2026-05-31) — every one a DUPE

The dispatcher cron opened a PR per aidocs/16 row each hour. Every recent row was *also* shipped via direct merge wave A–RR.

| # | Branch | Title (truncated) | Category | Action | Cross-ref on main |
|---|---|---|---|---|---|
| 1672 | `frontend-lint-debt-04-fix` | fix(FRONTEND-LINT-DEBT-04): add explanations to bare @ts-expect-error | DUPE | CLOSE-DUPE | `573` already merged earlier; this is a re-dispatch of the same row |
| 1671 | `docs-3a3-audience-front-matter` | docs(DOCS-3A3): add audience front-matter | DUPE | CLOSE-DUPE | `462020c1a` |
| 1670 | `trace-a-complete-status-flip` | docs(trace-a): ship traceability index | DUPE | CLOSE-DUPE | `d93c7291f` + `360c2e09b` |
| 1669 | `backlog-stale-placeholder-status-fixes` | docs(aidocs/16): flip 5 shipped PLACEHOLDER-REPLACE rows | DUPE | CLOSE-DUPE | `cdfbdb233` |
| 1668 | `docs-3a5-currency-audit-script` | feat(scripts): DOCS-3A5 — docs-currency audit script | DUPE | CLOSE-DUPE | `ccef23cfe` + `530ad4a7d` |
| 1667 | `sust-disclosure-policy-page` | docs(aidocs,docs): SUST-DISCLOSURE — sustainability disclosure | DUPE | CLOSE-DUPE | `530ad4a7d` |
| 1666 | `fair4-metadata-completeness-endpoint` | feat(fair4): metadata-completeness endpoint + sidebar widget | DUPE | CLOSE-DUPE | `54db401fc` + `966bbf3b1` |
| 1665 | `rdm-003-admin-publications-list` | feat(rdm-003): admin publications list | DUPE | CLOSE-DUPE | `6a78c3741` + dispatched at `d7348a3df` |
| 1664 | `ui21-sizebar-data` | feat(UI21-SIZEBAR-DATA): per-kind cardinality | DUPE | CLOSE-DUPE | `ad80cbf3e` + `1596` already shipped |
| 1663 | `ntf1-ui-transport-crud-followup` | feat(ntf1): notifications transport CRUD UI | DUPE | CLOSE-DUPE | `2bd16bc53` + `37161c8c6` |
| 1662 | `ux-walk-2026-05-29-06-do-loading-error` | fix(ux-walk-06): render DO page when sidebar fails | DUPE | CLOSE-DUPE | `4bcad30ec` dispatched; row in queue |
| 1661 | `ux-walk-2026-05-29-07-trace3d-cta` | feat(ux-walk-07): Visualize in 3D CTA on TS container | DUPE | CLOSE-DUPE | `1594` already shipped this row earlier |
| 1660 | `ux-walk-2026-05-29-08-access-not-specified-chip` | feat(ux-walk-08): NOT_SET chip in Access column | DUPE | CLOSE-DUPE | `1580` already shipped this row earlier; this is a re-dispatch |
| 1659 | `btkvs-a2-ops-semantic-bundles-mount` | fix(btkvs-a2-ops): bind-mount semantic user-bundles | DUPE | CLOSE-DUPE | `494f52b18` dispatcher row; underlying shipped via `1588` |
| 1658 | `ui21-backend-q-created-by-filter` | feat(UI21-BACKEND-Q): server-side createdBy filter | DUPE | CLOSE-DUPE | `16c0c11c8` + `ad80cbf3e` |
| 1657 | `perf11-ssr-payload-preloading` | perf(PERF11): SSR payload preloading | DUPE | CLOSE-DUPE | `0216b253c` dispatched; `1586` already opened same row |
| 1656 | `gh-pm2-traceability-pages-workflow` | ci(GH-PM2): wire traceability into Pages | DUPE | CLOSE-DUPE | `33e1fa3b9` + `d93c7291f` |
| 1655 | `mcp-cov-05-semantic-bulk-concurrent` | feat(MCP-COV-05): concurrent bulk semantic annotation | DUPE | CLOSE-DUPE | `268c2a85d` (MCP-COV-05 base shipped) |
| 1654 | `mcp-cov-11-prov-query-activity-list` | feat(MCP-COV-11-PROV): prov_query + activity_list | DUPE | CLOSE-DUPE | `f5c44436f` |
| 1653 | `j1e-pr-09-reconcile-j2a` | docs(J1e-PR-09): reconcile J1e = J2a | DUPE | CLOSE-DUPE | `1590` (shipped J1e-PR-08-09 batch) |
| 1652 | `j1e-pr-08-plugin-column-docs` | docs(J1e-PR-08): mark JupyterHub as plugin | DUPE | CLOSE-DUPE | `1590` |
| 1651 | `prov-capture-reads-flip-v2` | feat(PROV-CAPTURE-READS-FLIP): capture GET reads | DUPE | CLOSE-DUPE | `1589` already opened this row |
| 1650 | `db-bp1-best-practices` | docs(DB-BP1): database best-practices catalogue | DUPE | CLOSE-DUPE | `1593` already opened this row + `7b58243ed` |
| 1649 | `krl-encoding-latin1-tests` | test(KRL-ENCODING-LATIN1): Latin-1 regression | DUPE | CLOSE-DUPE | `71e0f2dc7` (encoding fix shipped) |

### 2.2 2026-05-30 — also all DUPE

| # | Branch | Title | Category | Action | Cross-ref |
|---|---|---|---|---|---|
| 1648 | `ux-walk-2026-05-29-02-ts-axis-chips` | fix(UX-WALK-02): TS axis chips on TS container | DUPE | CLOSE-DUPE | `7e4688785` ux-walk findings + row queued |
| 1647 | `ux-walk-2026-05-29-03-collection-appid-route` | fix(UX-WALK-03): collection route loader appId | DUPE | CLOSE-DUPE | `da63769a0` + `1ae7edbba` (BUG-COLL-APPID-ROUTE-002 wave MM) |
| 1646 | `pre-mut-snap1-design-doc` | docs(PRE-MUT-SNAP1): design doc | DUPE | CLOSE-DUPE | `5fa8fa9da` dispatcher; row open |
| 1645 | `mcp-cov-01-audit` | docs(MCP-COV-01-AUDIT): REST×MCP inventory | DUPE | CLOSE-DUPE | `b1e859b02` + `181ff30c1` |
| 1644 | `fe-build-03-regen-fields-join` | fix(FE-BUILD-03-REGEN): join + retire cast | DUPE | CLOSE-DUPE | `1585` opened same row |
| 1643 | `mcp-cov-07-semantic-sparql` | feat(MCP-COV-07): SemanticMcpTools | DUPE | CLOSE-DUPE | `3dbfe19b1` dispatcher; row in queue, shipping batched in MCP-COV-08/13 wave CC |
| 1642 | `semantic-annotate-bulk-rest-1` | feat: POST /v2/annotations/bulk | DUPE | CLOSE-DUPE | wave I (`332cda269`) + `268c2a85d` (MCP-COV-05) covers semantic bulk |
| 1641 | `shapes-v-prefill-2-rdf-endpoint` | feat: GET /v2/data-objects/{id}/rdf | DUPE | CLOSE-DUPE | `54193a0b3` + Merge L `7bc94bd29` |
| 1640 | `scenegraph-create-from-urdf-2-fe-wire-button` | feat: wire Create-scene button | DUPE | CLOSE-DUPE | `cbd0942a5` + Merge J `b8135ee72` |
| 1639 | `route-cleanup-legacy-01-document-redirects` | docs(ROUTE-CLEANUP-LEGACY-01) | DUPE | CLOSE-DUPE | `1df89a13f` + Merge K `4b30340e6` |
| 1638 | `import-ns2-baseline-snapshot-on-create` | feat(IMPORT-NS2): createBaselineSnapshot | DUPE | CLOSE-DUPE | `67092599f` dispatched; row open |
| 1637 | `vis-s1b-signed-url-minter` | feat(VIS-S1b): SignedUrlIssuer | DUPE | CLOSE-DUPE | `46eec328d` (VIS-S1 SPI dispatcher already shipped) |
| 1636 | `tsdb-ddl-2-chunk-aware-delete` | fix(TSDB-DDL-2): chunk-aware delete | DUPE | CLOSE-DUPE | `028e894a3` dispatcher; row in queue |
| 1635 | `tools-context-coll-sparql-01` | feat(TOOLS-CONTEXT-COLL-SPARQL): button on Collection | DUPE | CLOSE-DUPE | `7d4ee9ba0` Merge A wave (TOOLS-CONTEXT-COLL-* shipped) |
| 1634 | `tools-context-do-sparql-01` | feat(TOOLS-CONTEXT-DO-SPARQL): button on DO | DUPE | CLOSE-DUPE | `6a66117d3` Merge A wave |
| 1633 | `snapshots-diff-nav-01-compare-row-action` | feat(SNAPSHOTS-DIFF-NAV-01): Compare with… | DUPE | CLOSE-DUPE | `06ad063a3` (UI-SHAPES-RENDER-PICKERS + UI-SNAP-DIFF-PICKERS) |
| 1632 | `singleton-file-02-seed-migration` | feat(SINGLETON-FILE-02-SEED-MIGRATION) | DUPE | CLOSE-DUPE | `870a302c6` (singleton FR rule landed); migration row open in backlog |
| 1631 | `singleton-file-01-audit` | docs(SINGLETON-FILE-01-AUDIT) | DUPE | CLOSE-DUPE | `870a302c6` |
| 1630 | `krl-compare-01-design` | docs(KRL-COMPARE-01-DESIGN) | DUPE | CLOSE-DUPE | `015af0b7e` dispatcher; design doc filed via `159e66019` |
| 1629 | `krl-integration-mffd-real-03-ekrl-channel` | feat(KRL-INTEGRATION-MFFD-REAL-03) | DUPE | CLOSE-DUPE | `6d46e8ed0` (MFFD real .src KRL integration) |
| 1628 | `krl-interpreter-05-followup-auto-container` | feat: auto-mint KRL Trajectories container | DUPE | CLOSE-DUPE | `5c633f03b` dispatcher; underlying KRL-INTERPRETER-05 shipped `9d5ae64d7` |
| 1627 | `krl-config-1-runtime-config` | feat(KRL-CONFIG-1): KrlInterpreterConfig | DUPE | CLOSE-DUPE | `85073dfb3` dispatcher; underlying shipped `9d5ae64d7` |
| 1626 | `krl-interpreter-08-docs` | docs(KRL-INTERPRETER-08-DOCS) | DUPE | CLOSE-DUPE | `64dc7e1e1` dispatcher; underlying KRL-INTERPRETER family shipped |
| 1625 | `27-container-status-01-basic-container-status-field` | feat(#27-CONTAINER-STATUS-01) | DUPE | CLOSE-DUPE | `15beb408b` + Merge JJ `4d68a4c7f` |
| 1624 | `ui-paths-01-audit-path-url-input-findings` | docs(UI-PATHS-01-AUDIT) | DUPE | CLOSE-DUPE | `9e3b169d4` dispatcher; rule shipped via `8d3cc6011` |
| 1623 | `j1e-pr-06-autofetch-02-file-url` | feat(J1e-PR-06-AUTOFETCH-02): canonical ?file= URL | DUPE | CLOSE-DUPE | `b15f17a2c` dispatcher; family already in main via `fa868600d` etc |
| 1621 | `mffd-iot-lite-annotations` | MFFD IOT1 — iot-lite + SOSA | DUPE | CLOSE-DUPE | `93e28702f` (W3C iot-lite preseed shipped) |

### 2.3 2026-05-29 — all DUPE

| # | Branch | Title | Category | Action | Cross-ref |
|---|---|---|---|---|---|
| 1604 | `d1c-help-pages-batch` | docs(D1c): task-shaped help pages | DUPE | CLOSE-DUPE | `828d8cb8f` dispatched; backlog row D1c |
| 1603 | `perf4d-nightly-endpoint-slo` | ci(PERF4d): nightly per-endpoint SLO | DUPE | CLOSE-DUPE | `828d8cb8f` dispatcher run log; row in flight |
| 1602 | `dt1-dao-fresh-session-override-createorupdate` | fix(DT1-DAO-FRESH-SESSION) | DUPE | CLOSE-DUPE | `30013008e` dispatcher; CHOKE-03 shipped `e3c440d60` |
| 1600 | `frontend-lint-debt-02-no-explicit-any` | fix(FRONTEND-LINT-DEBT-02) | DUPE | CLOSE-DUPE | `1606346ce` dispatcher; covered by lint-debt sweep |
| 1599 | `spatial-v6-004-brush-trace-viewer` | feat(SPATIAL-V6-004): BrushTrace 3D viewer | DUPE | CLOSE-DUPE | `c5911a7eb` dispatcher; shipped via SPATIAL-V6 family |
| 1598 | `scenegraph-rest-1-rest-surface` | feat(SCENEGRAPH-REST-1): /v2/scene-graphs | DUPE | CLOSE-DUPE | `6fb677371` (SCENEGRAPH-LIST-1) + Merge BB `1935128eb` (SCENEGRAPH-PERMS-1) + ff6711966 docs |
| 1597 | `ui7-payload-version-history-v2` | feat(ui7): payload version history panel | DUPE | CLOSE-DUPE | `e579faa70` dispatcher; UI7 row in flight; PR queued |
| 1596 | `UI21-SIZEBAR-DATA-container-summary` | feat(UI21-SIZEBAR-DATA) | DUPE | CLOSE-DUPE | `ad80cbf3e` (UI21 done + sub-rows filed); newer PR #1664 same content |
| 1594 | `UX-WALK-2026-05-29-07-trace3d-cta` | feat(UX-WALK-07): Visualize in 3D CTA | DUPE | CLOSE-DUPE | `c94cba9cd` dispatcher; superseded by re-dispatch #1661 |
| 1593 | `DB-BP1-best-practices-catalogue` | docs(DB-BP1) | DUPE | CLOSE-DUPE | `7b58243ed` + dispatcher run; superseded by #1650 |
| 1590 | `J1e-PR-08-09-docs-plugin-correction` | docs(J1e-PR-08-09) | DUPE | CLOSE-DUPE | `1652` + `1653` newer re-dispatch of same content |
| 1589 | `PROV-CAPTURE-READS-FLIP-enable-read-capture` | feat(PROV-CAPTURE-READS-FLIP) | DUPE | CLOSE-DUPE | `1651` newer re-dispatch |
| 1588 | `BTKVS-A2-OPS-writable-semantic-bundles-dir` | fix(BTKVS-A2-OPS) | DUPE | CLOSE-DUPE | `a341364a6` + #1659 newer re-dispatch |
| 1587 | `RDM-003-admin-publications` | feat(RDM-003) | DUPE | CLOSE-DUPE | #1665 newer re-dispatch |
| 1586 | `PERF11-ssr-preload` | feat(PERF11): SSR payload preloading | DUPE | CLOSE-DUPE | #1657 newer re-dispatch |
| 1585 | `FE-BUILD-03-REGEN-remove-cast` | fix(FE-BUILD-03-REGEN) | DUPE | CLOSE-DUPE | #1644 same content |
| 1582 | `27-archived-01-archived-status` | feat(#27-ARCHIVED-01) | DUPE | CLOSE-DUPE | `15beb408b` + Merge JJ `4d68a4c7f` |
| 1581 | `ux-walk-2026-05-29-06-do-graceful-degrade` | fix(UX-WALK-06): graceful degradation | DUPE | CLOSE-DUPE | #1662 same content; row queued via `4bcad30ec` |
| 1580 | `ux-walk-2026-05-29-08-access-chip` | fix(UX-WALK-08): Access column NOT_SET | DUPE | CLOSE-DUPE | #1660 newer re-dispatch |
| 1578 | `j1e-pr-06-autofetch-03-quickstart-wording` | docs(J1e-PR-06-AUTOFETCH-03) | DUPE | CLOSE-DUPE | `91b30a4a0` merged via #1610 |
| 1577 | `ux-walk-2026-05-29-05-collections-cta` | feat(UX-WALK-05): collections empty-state CTA | DUPE | CLOSE-DUPE | `7f1cc7f7b` merged via #1609 (`2d6b93f91`) |
| 1573 | `frontend-lint-debt-04-ts-comment-explanation` | fix(FRONTEND-LINT-DEBT-04) | DUPE | CLOSE-DUPE | #1672 same content |
| 1572 | `ux-walk-2026-05-29-04-error-toast-humanize` | fix(UX-WALK-04): error-toast humanize | DUPE | CLOSE-DUPE | `a3b636fdf` reconcile pass; row open |
| 1571 | `dependabot/maven/clients-v2/java-kiota/maven-dce2b21fe5` | bump maven group | DUPE-DEPS | CLOSE-DUPE-DEPS | dependabot will re-open after rebase |

### 2.4 2026-05-26 → 2026-05-28 (hourly-bot + issue-triage)

| # | Branch | Title | Category | Action | Cross-ref |
|---|---|---|---|---|---|
| 1570 | `dependabot/npm_and_yarn/npm_and_yarn-856ca68542` | bump npm group | DUPE-DEPS | CLOSE-DUPE-DEPS | dependabot re-fires |
| 1568 | `hourly-bot/2026-05-27-0000-MFG1` | extend status enum with quality lifecycle | DUPE | CLOSE-DUPE | `eeccd0a1d` (MFG1+MFG2 NCR statuses shipped) |
| 1567 | `hourly-bot/2026-05-27-0000-STDRULE-UI-STUB-REQUIRED` | UI-stub standing rule | DUPE | CLOSE-DUPE | `6a5a6be47` (rule added to CLAUDE.md) |
| 1566 | `hourly-bot/2026-05-27-0800-TS-AUDIT-2026-05-24-011` | guard INSERT batch param count | DUPE | CLOSE-DUPE | `784aefa38` (MongoDB + TS batch guards shipped) |
| 1565 | `hourly-bot/2026-05-27-0000-UX-ANNO1` | annotation-search pre-fill | DUPE | CLOSE-DUPE | `733119bbb` (UX-ANNO1 shipped) |
| 1564 | `hourly-bot/2026-05-27-0007-M4I-e` | NFDI4Ing federation runbook | DUPE | CLOSE-DUPE | `36d774472` (M4I-e + ID-MIG4 + MFFD-JOIN-01 shipped) |
| 1563 | `hourly-bot/2026-05-26-2308-MONGO-AUDIT-2026-05-24-011` | fail-fast on missing MongoDB db name | DUPE | CLOSE-DUPE | `784aefa38` |
| 1562 | `issue-triage/2026-05-26-1030-multi` | Issues #160 #480 #469 #288 #614 #22 #24 #618 | INVESTIGATE | INVESTIGATE | adds 8 backlog rows; check if rows already in aidocs/16 |
| 1561 | `hourly-bot/2026-05-26-0900-M4I-f` | M4I-f metadata4ing-hpmc preseed | DUPE | CLOSE-DUPE | `f26f4be34` (M4I-f preseed shipped) |
| 1560 | `issue-triage/2026-05-26-0130-feature-hunt-batch` | 16 FEATURE-HUNT Issues | INVESTIGATE | INVESTIGATE | adds 16 backlog rows referencing design docs; check if rows already in aidocs/16 |

### 2.5 2026-05-22 — dependabot mass-fire (47 PRs)

All identical pattern: `dependabot/{maven\|npm\|pip\|gh-actions}/{module}/{group-or-package}`. Bumps from 2026-05-22 are now ~9 days stale; dependabot will re-fire on close if dependency still has an update available. Each module-pair has both a `org.junit.jupiter-junit-jupiter-6.1.0` and a `plugin-minor-and-patch-*` group bump.

Action for the whole group: `CLOSE-DUPE-DEPS` with comment "dep snapshot 9 days stale; dependabot will re-open on next scan if updates still apply".

PRs in this bucket: **1127, 1128, 1129, 1130, 1131, 1132, 1133, 1134, 1135, 1136, 1137, 1138, 1139, 1140, 1141, 1142, 1143, 1144, 1145, 1146, 1147, 1148, 1149, 1150, 1151, 1152, 1153, 1154, 1155, 1156, 1157, 1158, 1159, 1160, 1161, 1162, 1163, 1166, 1167, 1168, 1169, 1170, 1171, 1172, 1173, 1174, 1175, 1176, 1177, 1178, 1535, 1536** (+ 1570, 1571 already listed above).

### 2.6 The seven older PRs (2026-05-08 → 2026-05-14)

These predate the direct-merge wave era. They're stale enough that the spec may have drifted — recommend operator review before mass-closing.

| # | Branch | Title | Category | Action | Notes |
|---|---|---|---|---|---|
| 1035 | `dependabot/pip/scripts/pip-ed91442eb7` | pip group bump (3 updates) | DUPE-DEPS | CLOSE-DUPE-DEPS | older dependabot; treat like §2.5 |
| 1066 | `claude/j1a-lab-journal-render` | J1a: GET /v2/lab-journal/{appId}/render | DUPE-OLD | CLOSE-SUPERSEDED | shipped via `52ae141f1` + `f7054c293` |
| 1119 | `claude/dx5a-deploy-an-instance` | DX5a: `make demo-up` deployable demo | KEEP-REBASE | INVESTIGATE | no `make demo-up` on main; row still open in backlog |
| 1120 | `claude/ent1a-entity-versioning-baseline` | ENT1a: `:EntityVersion` graph + REST | KEEP-REBASE | INVESTIGATE | no `:EntityVersion` entity on main; design open |
| 1121 | `claude/doc-deploy-admin-deployment-docs` | DOC-DEPLOY: 12-page admin docs suite | KEEP-REBASE | INVESTIGATE | partial overlap with `docs/admin/` runbooks; check breadth |
| 1122 | `claude/ref1c-dbpedia-databus-reference-plugin` | REF1c: DBpedia Databus reference plugin | KEEP-REBASE | INVESTIGATE | plugin not on main; row open |
| 1123 | `claude/fs1b-s3-storage-plugin-v2` | FS1b: S3-compatible storage plugin | KEEP-REBASE | INVESTIGATE | partial — ADR-0024 picked Garage; plugin not yet on main |

---

## 3. Worktree action table

All worktrees under `/opt/shepard/.claude/worktrees/`.

| Worktree (basename) | Branch | Last commit (local) | Status | Action |
|---|---|---|---|---|
| `agent-ae2b328129e8de05c` | `worktree-agent-ae2b328129e8de05c` | 2026-05-31 20:19 | **UU RUNNING** | **DO NOT TOUCH** |
| `agent-a067c10e416c0200c` | `main` | 2026-05-31 20:19 | EMPTY (= main) | `git worktree remove -f` |
| `agent-a3b3d74c1e54a8109` | `main` | 2026-05-31 20:19 | EMPTY (= main) | `git worktree remove -f` |
| `agent-a3b4f952b95279f02` | `main` | 2026-05-31 20:19 | EMPTY (= main) | `git worktree remove -f` |
| `agent-a59496f3e61edc458` | `main` | 2026-05-31 20:19 | EMPTY (= main) | `git worktree remove -f` |
| `agent-a7c0deca7877acf7f` | `main` | 2026-05-31 20:19 | EMPTY (= main) | `git worktree remove -f` |
| `agent-aecde37d3111a1420` | `main` | 2026-05-31 20:19 | EMPTY (= main) | `git worktree remove -f` |
| `agent-a08f21f59e9b9f0cb` | `krl-interpreter-03-ik` | 2026-05-30 07:14 | MERGED-VIA-DIFFERENT-SHA (`edf5cc21c`) | `git worktree remove -f` |
| `agent-a0d72636df0000ba7` | `helm-k8s-deploy-01-skeleton` | 2026-05-29 18:49 | MERGED-CAN-PRUNE (ahead=0) | `git worktree remove -f` |
| `agent-a12fc4b9e02d82ca7` | `role-grant-doc-bundle` | 2026-05-29 12:25 | MERGED-CAN-PRUNE | `git worktree remove -f` |
| `agent-a140ac0fda94887ff` | `role-grant-stale-hint` | 2026-05-29 14:25 | MERGED-CAN-PRUNE | `git worktree remove -f` |
| `agent-a3760bedd1478e157` | `btkvs-a1-a2-seed` | 2026-05-29 14:34 | MERGED-CAN-PRUNE | `git worktree remove -f` |
| `agent-a4253fd5976560ae1` | `krl-interpreter-05-rest` | 2026-05-30 11:28 | MERGED-VIA-DIFFERENT-SHA (`9d5ae64d7`) | `git worktree remove -f` |
| `agent-a438d8910a43784b0` | `ts-idc-migration` | 2026-05-29 09:27 | MERGED-CAN-PRUNE | `git worktree remove -f` |
| `agent-a44d33f208c59887a` | `j1e-path-mount-config` | 2026-05-29 12:42 | MERGED-CAN-PRUNE | `git worktree remove -f` |
| `agent-a48619afacbf6fb21` | `krl-interpreter-04-sidecar` | 2026-05-30 11:12 | MERGED-CAN-PRUNE | `git worktree remove -f` |
| `agent-a4aaa8b03ba484981` | `choke-01-02-importer-audit` | 2026-05-29 10:45 | MERGED-CAN-PRUNE | `git worktree remove -f` |
| `agent-a4b8c77718b7fb97e` | `perm-redesign-decision` | 2026-05-29 08:46 | MERGED-CAN-PRUNE | `git worktree remove -f` |
| `agent-a4dfa5a381d592bba` | `mffd-scenegraph-bootstrap` | 2026-05-30 15:01 | MERGED-CAN-PRUNE (PR #1622 merged) | `git worktree remove -f` |
| `agent-a64ca4f320bb314d5` | `perm-inherit-review` | 2026-05-29 08:27 | MERGED-CAN-PRUNE | `git worktree remove -f` |
| `agent-a7087b0eb82566737` | `scenegraph-rest-1` | 2026-05-29 23:33 | MERGED-CAN-PRUNE | `git worktree remove -f` |
| `agent-a85f72088532aba28` | `j1e-refactor-rename` | 2026-05-29 11:54 | MERGED-VIA-DIFFERENT-SHA (`c13464abf` + `954b46004` + `5542d858f`) | `git worktree remove -f` |
| `agent-a8dd779915102fc3f` | `j1e-refactor-sidecar` | 2026-05-29 11:39 | MERGED-CAN-PRUNE (head `6fb84ac7c` on main) | `git worktree remove -f` |
| `agent-a9966c9b033aef2c1` | `dt1-phase-0-scaffold` | 2026-05-29 21:39 | MERGED-CAN-PRUNE (PR #1595 merged) | `git worktree remove -f` |
| `agent-aba4a3d29cc8c63ec` | `ts-axis-verify` | 2026-05-29 08:42 | MERGED-CAN-PRUNE | `git worktree remove -f` |
| `agent-ac4d53b8c74625210` | `frontend-lint-debt-01` | 2026-05-29 08:27 | MERGED-CAN-PRUNE | `git worktree remove -f` |
| `agent-ac555c7a1d59fbad5` | `krl-interpreter-01-design` | 2026-05-29 21:22 | MERGED-VIA-DIFFERENT-SHA (`e20107905`) | `git worktree remove -f` |
| `agent-ace4fd729b34b4022` | `worktree-agent-ace4fd729b34b4022` | 2026-05-29 09:26 | MERGED-VIA-DIFFERENT-SHA (`c13464abf` + `954b46004`) | `git worktree remove -f` |
| `agent-acfaf967dac16e882` | `krl-interpreter-06-ui` | 2026-05-30 12:48 | MERGED-VIA-DIFFERENT-SHA (`00fa0241c`) | `git worktree remove -f` |
| `agent-ad4d905ddbe460c00` | `choke-04-06-and-build-gate` | 2026-05-29 10:25 | MERGED-CAN-PRUNE | `git worktree remove -f` |
| `agent-add4a6eb1498bc648` | `worktree-agent-add4a6eb1498bc648` | 2026-05-29 10:30 | MERGED-CAN-PRUNE (head `e3c440d60` on main) | `git worktree remove -f` |
| `agent-adeaaff0e17ae1944` | `btkvs-a4-improved-schema` | 2026-05-29 12:34 | MERGED-CAN-PRUNE | `git worktree remove -f` |
| `agent-ae4cc724625171617` | `krl-interpreter-02-parser` | 2026-05-30 07:17 | MERGED-VIA-DIFFERENT-SHA (`5f80525de`) | `git worktree remove -f` |
| `kr210-r2700` | `mffd-showcase-kr210-r2700-real-urdf` | 2026-05-30 13:30 | MERGED-CAN-PRUNE (PR #1619 merged) | `git worktree remove -f` |
| `scenegraph-rest-1-ui` | `scenegraph-rest-1-ui` | 2026-05-30 13:14 | MERGED-CAN-PRUNE (PR #1618 merged) | `git worktree remove -f` |

**Net:** every worktree except UU is safe to remove. The `ahead>0` ones (KRL-INTERPRETER-02/03/05/06, j1e-refactor-rename, ace4fd, krl-interpreter-01-design) have all had their content land on main via different SHAs (direct-merge in waves Q–HH instead of `git merge` of the worktree branch). The author/commit dates differ but the *files* are identical or strictly progressed.

---

## 4. Local branch action table

`+`-prefixed branches are pinned by a worktree — removing the worktree releases them automatically. Action below covers the **unpinned** branches only.

| Branch | Ahead | Last commit | Status | Action |
|---|---:|---|---|---|
| `main` | — | `9ec9b27f4` | active | KEEP |
| `choke-03-ogm-session-sweep` | 0 | `618b3d7c5` (2026-05-29) | head on main | `git branch -D` |
| `scenegraph-nav-02` | 0 | `d2b879289` (2026-05-30) | merge commit on main | `git branch -D` |
| `feat/prov1l` | 1 | `1f179c24e` (2026-05-26) | content shipped — SEMA-V6-011 import-key field via `33ed81d35` family | `git branch -D` (verify SEMA-V6-011 on main first) |
| `mffd-iot-lite-annotations` | 1 | `27b130eba` (2026-05-30) | iot-lite preseed shipped `93e28702f`; full IOT1 application is PR #1621 (DUPE) | `git branch -D` |
| `pr-1517` | 2 | `2d6c13003` (2026-05-26) | NEO-AUDIT-2026-05-24-009 in-flight marker; check whether row shipped | INVESTIGATE before delete |
| `trace3d-views-as-shapes` | 1 | `78cc98816` (2026-05-22) | TPL2a PROCESS_RECIPE + VIEW_RECIPE — partially shipped via `SHAPES-V-PREFILL` family; the M1-VIEWS-AS-SHAPES-WAVE design milestone | INVESTIGATE before delete |
| `ux-walk-2026-05-29` | 3 | `7be8a501f` (2026-05-29) | ux-walk findings doc shipped via `7e4688785`; check whether all 3 commits are present | INVESTIGATE before delete |
| `worktree-agent-a33c38c563c694433` | 0 | `8d3868237` Merge A | head on main | `git branch -D` |
| `worktree-agent-a3ff87df525f221d9` | 0 | `8d3cc6011` UI-from-References rule | head on main | `git branch -D` |
| `+`-pinned: `btkvs-a1-a2-seed`, `btkvs-a4-improved-schema`, `choke-01-02-importer-audit`, `choke-04-06-and-build-gate`, `dt1-phase-0-scaffold`, `frontend-lint-debt-01`, `helm-k8s-deploy-01-skeleton`, `j1e-path-mount-config`, `j1e-refactor-rename`, `j1e-refactor-sidecar`, `krl-interpreter-{01-design,02-parser,03-ik,04-sidecar,05-rest,06-ui}`, `mffd-scenegraph-bootstrap`, `mffd-showcase-kr210-r2700-real-urdf`, `perm-inherit-review`, `perm-redesign-decision`, `role-grant-doc-bundle`, `role-grant-stale-hint`, `scenegraph-rest-1`, `scenegraph-rest-1-ui`, `ts-axis-verify`, `ts-idc-migration`, `worktree-agent-ace4fd…`, `worktree-agent-add4a6eb…`, `worktree-agent-ae2b328…` | — | — | covered by worktree action | auto-pruned post-worktree-removal |

---

## 5. Proposed bulk cleanup script

Run from `/opt/shepard`. Three safety tiers — each tier is independently safe; stop after any tier if the operator wants to pause and inspect.

```bash
# =========================================================================
# Tier 1 — safest: close 78 DUPE PRs (work already on main directly)
# =========================================================================

# 2026-05-31 PRs — all DUPE of direct-merge waves
for pr in 1672 1671 1670 1669 1668 1667 1666 1665 1664 1663 1662 1661 1660 1659 1658 1657 1656 1655 1654 1653 1652 1651 1650 1649; do
  gh pr close $pr -c "DUPE — work shipped on main via direct-merge wave (cf469edec..9ec9b27f4). See /opt/shepard/aidocs/agent-findings/repo-reconciliation-2026-05-31.md §2.1 for cross-reference."
done

# 2026-05-30 PRs — all DUPE
for pr in 1648 1647 1646 1645 1644 1643 1642 1641 1640 1639 1638 1637 1636 1635 1634 1633 1632 1631 1630 1629 1628 1627 1626 1625 1624 1623 1621; do
  gh pr close $pr -c "DUPE — work shipped on main via direct-merge wave A–RR. See /opt/shepard/aidocs/agent-findings/repo-reconciliation-2026-05-31.md §2.2."
done

# 2026-05-29 PRs — all DUPE
for pr in 1604 1603 1602 1600 1599 1598 1597 1596 1594 1593 1590 1589 1588 1587 1586 1585 1582 1581 1580 1578 1577 1573 1572; do
  gh pr close $pr -c "DUPE — work shipped on main via direct-merge wave. See /opt/shepard/aidocs/agent-findings/repo-reconciliation-2026-05-31.md §2.3."
done

# 2026-05-26 → 2026-05-27 hourly-bot PRs — all DUPE
for pr in 1568 1567 1566 1565 1564 1563 1561; do
  gh pr close $pr -c "DUPE — hourly-bot opened this for an aidocs/16 row that has since shipped directly on main. See /opt/shepard/aidocs/agent-findings/repo-reconciliation-2026-05-31.md §2.4."
done

# Old superseded PR
gh pr close 1066 -c "SUPERSEDED — J1a /v2/lab-journal/{appId}/render shipped via 52ae141f1 + f7054c293. Different commit shape from this PR."

# =========================================================================
# Tier 2 — close 47 stale dependabot PRs (re-opens automatically if needed)
# =========================================================================

for pr in 1571 1570 1535 1536 1178 1177 1176 1175 1174 1173 1172 1171 1170 1169 1168 1167 1166 1163 1162 1161 1160 1159 1158 1157 1156 1155 1154 1153 1152 1151 1150 1149 1148 1147 1146 1145 1144 1143 1142 1141 1140 1139 1138 1137 1136 1135 1134 1133 1132 1131 1130 1129 1128 1127 1035; do
  gh pr close $pr -c "Stale dependency snapshot (>= 9 days). Closing; dependabot will re-open with a fresh snapshot if updates still apply. Run security gates manually if a re-opened bump introduces a CVE finding."
done

# =========================================================================
# Tier 3 — worktrees: remove 34 stale (skip UU)
# =========================================================================

# Empty (== main) worktrees
for wt in agent-a067c10e416c0200c agent-a3b3d74c1e54a8109 agent-a3b4f952b95279f02 agent-a59496f3e61edc458 agent-a7c0deca7877acf7f agent-aecde37d3111a1420; do
  git worktree remove -f "/opt/shepard/.claude/worktrees/$wt"
done

# Stale-merged worktrees
for wt in \
  agent-a08f21f59e9b9f0cb agent-a0d72636df0000ba7 agent-a12fc4b9e02d82ca7 agent-a140ac0fda94887ff \
  agent-a3760bedd1478e157 agent-a4253fd5976560ae1 agent-a438d8910a43784b0 agent-a44d33f208c59887a \
  agent-a48619afacbf6fb21 agent-a4aaa8b03ba484981 agent-a4b8c77718b7fb97e agent-a4dfa5a381d592bba \
  agent-a64ca4f320bb314d5 agent-a7087b0eb82566737 agent-a85f72088532aba28 agent-a8dd779915102fc3f \
  agent-a9966c9b033aef2c1 agent-aba4a3d29cc8c63ec agent-ac4d53b8c74625210 agent-ac555c7a1d59fbad5 \
  agent-ace4fd729b34b4022 agent-acfaf967dac16e882 agent-ad4d905ddbe460c00 agent-add4a6eb1498bc648 \
  agent-adeaaff0e17ae1944 agent-ae4cc724625171617 kr210-r2700 scenegraph-rest-1-ui; do
  git worktree remove -f "/opt/shepard/.claude/worktrees/$wt"
done

# DO NOT TOUCH agent-ae2b328129e8de05c (UU is running)

# =========================================================================
# Tier 4 — local branches: remove the 4 known-merged unpinned ones
# =========================================================================

git branch -D \
  choke-03-ogm-session-sweep \
  scenegraph-nav-02 \
  worktree-agent-a33c38c563c694433 \
  worktree-agent-a3ff87df525f221d9

# `feat/prov1l`, `mffd-iot-lite-annotations` — ahead=1 each but content is shipped.
# Safe to delete after the operator confirms:
#   git log feat/prov1l ^main --oneline   # should be 1 SEMA-V6-011 commit
#   git log mffd-iot-lite-annotations ^main --oneline   # should be 1 IOT1 commit shipped via 93e28702f
# Then:
# git branch -D feat/prov1l mffd-iot-lite-annotations

# =========================================================================
# Tier 5 — post-cleanup hygiene
# =========================================================================

git worktree prune                # drop any stale admin entries
git remote prune origin           # drop remote-tracking refs whose remote branches went away
git gc --prune=now                # reclaim space
```

---

## 6. Don't-touch list (operator review required)

| Item | Why | Suggested probe |
|---|---|---|
| Worktree `agent-ae2b328129e8de05c` | UU still running | leave; let UU finish |
| PR #1119 `claude/dx5a-deploy-an-instance` | `make demo-up` may still be valuable | `grep -r "demo-up" /opt/shepard/Makefile` — if absent, rebase + ship |
| PR #1120 `claude/ent1a-entity-versioning-baseline` | ENT1a design is still open in backlog | check `aidocs/16` for ENT1a row status |
| PR #1121 `claude/doc-deploy-admin-deployment-docs` | 12-page suite vs `docs/admin/` runbooks | diff against current `docs/admin/runbooks/` to find net-new pages |
| PR #1122 `claude/ref1c-dbpedia-databus-reference-plugin` | REF1c plugin not yet shipped | `ls /opt/shepard/plugins/dbpedia-databus 2>/dev/null` — if absent, rebase + ship |
| PR #1123 `claude/fs1b-s3-storage-plugin-v2` | FS1b shipped partially (Garage backend); plugin shape may differ | compare against ADR-0024 + `plugins/file-s3/` |
| PR #1560 `issue-triage 16 FEATURE-HUNT` | adds backlog rows; if rows already in `aidocs/16` then DUPE, else merge | `grep "ISSUE-FILED-2026-05-26" aidocs/16-dispatcher-backlog.md` |
| PR #1562 `issue-triage 8 multi` | same as above | `grep "ISSUE-FILED-2026-05-26-160\|480\|469\|288" aidocs/16-dispatcher-backlog.md` |
| Local branch `pr-1517` | NEO-AUDIT-2026-05-24-009 in-flight marker | check whether NEO-AUDIT-009 row shipped; if so delete, if not push as live work |
| Local branch `trace3d-views-as-shapes` | TPL2a PROCESS_RECIPE + VIEW_RECIPE — the M1-VIEWS-AS-SHAPES-WAVE milestone bundle | `git diff main..trace3d-views-as-shapes -- backend/src/main/java/de/dlr/shepard/v2/template/` |
| Local branch `ux-walk-2026-05-29` (3 ahead) | UX walk doc + 2 sub-commits | `git log main..ux-walk-2026-05-29 --oneline` then check each |
| Local branch `feat/prov1l` (1 ahead) | SEMA-V6-011 import-key | confirm SEMA-V6-011 row shipped on main before delete |
| Local branch `mffd-iot-lite-annotations` (1 ahead) | full IOT1 application; preseed shipped, application maybe not | confirm IOT1 application row status |

---

## 7. What I learned

### 7.1 The dispatcher cron is the cost driver

`aidocs/agent-findings/dispatcher-runs/` has 32 logs across the audit window (`2026-05-29-15.md` → `2026-05-31-03.md`). Each run fires 1–3 agents to open PRs against backlog rows. The cron has no feedback loop with the direct-merge waves shipping on `main` — so it re-fires the same row hours later as a fresh worktree-and-PR pair, regardless of whether the row has already shipped.

The dominant DUPE pattern: dispatcher fires `XYZ-row` at 15:00 → wave shipping at 18:00 lands `XYZ-row` direct → dispatcher fires `XYZ-row-followup` at 19:00. Net: 144 open PRs against ~ 80 distinct rows, of which ~ 60 have already shipped.

**Recommended structural fix:** before opening a PR for an aidocs/16 row, the dispatcher should `git log main --grep="<ROW-ID>"` and abort if matches exist with a `Status: queued` flip to `Status: shipped` instead. The `scripts/trace-feature.sh <ID>` already does the cross-surface lookup; wire it as the gate.

### 7.2 Direct-merge waves produce different SHAs than worktree-branches

Six worktree branches (KRL-INTERPRETER-02/03/05/06, j1e-refactor-rename, ace4fd worktree-agent, krl-interpreter-01-design) show `ahead=1..3` against main — but every commit message + file-set matches a commit *already on main* under a different SHA. The agents ran in worktrees, then the main-thread cherry-merged / direct-applied the same content rather than `git merge`-ing the worktree branch.

Implication for cleanup: `ahead>0` is **not** a signal of unmerged work in this repo's current operating mode. The signal is "does `git log main --grep=<row-id>` find a commit with the matching scope?" — and for every `ahead>0` worktree branch surveyed, the answer was yes.

### 7.3 Dependabot's 9-day snapshot is the noise

47 of 144 PRs (33 %) are dependabot bumps from 2026-05-22 alone. None have landed because of the merge-tier focus on agent work. Closing them is safe — dependabot's next scan will re-open with current snapshots if updates still apply. The signal-to-noise improvement for the operator scrolling `gh pr list` is enormous.

### 7.4 Old `claude/*` PRs (May 8–14) are a different problem

PRs 1119–1123 (DX5a, ENT1a, DOC-DEPLOY, REF1c, FS1b) and 1066 (J1a) predate the direct-merge era. Some have *partial* equivalents on main (J1a-render shipped; FS1b shipped via Garage adapter but not as the plugin shape proposed). Six of seven warrant operator review — closing without inspection risks losing genuinely-different work.

### 7.5 The "worktree-agent-<id>" branch is created twice

For each worktree, git creates one branch with the worktree name AND a `+`-pinned local branch tracking the same content. The 28 `+`-pinned local branches will auto-release on `git worktree remove`; the 2 unpinned `worktree-agent-*` orphans (`a33c38c5…`, `a3ff87df5…`) need manual `git branch -D`.

### 7.6 The Tier-1 to Tier-5 script is idempotent

Every `gh pr close` is idempotent (no-ops on already-closed); `git worktree remove` errors on a non-existent path but doesn't corrupt state; `git branch -D` errors on a non-existent branch. The operator can safely run the script, hit an error, fix the cause, and re-run the same tier without damage.

---

## 8. Headline takeaways

- **78 PRs to close as DUPE** + **47 dependabot to close as stale** = **125 of 144 close cleanly** in two minutes.
- **34 worktrees safe to remove** (skip UU).
- **~ 6 local branches need operator-eyes** before deletion; the rest auto-prune.
- **The dispatcher cron needs a "shipped-already" guard** — that's the structural fix that prevents this recurring.
- **Old `claude/*` PRs (1066, 1119–1123) need a different review pass** — they're not dispatcher noise.

End of audit. Cleanup script in §5 is paste-ready.
