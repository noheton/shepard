---
title: minter-datacite — Quickstart
stage: deployed
last-stage-change: 2026-05-23
audience: plugin-author
synthetic_batch: true
generation_rule: feedback_no_synthetic_provenance.md
---

> 🤖 **BACKFILL — created retroactively 2026-05-23 by Claude Opus 4.7**
> per the docs-gap audit at `aidocs/agent-findings/plugin-docs-gap-audit-2026-05-23.md`.
> The plugin's behaviour is documented from the source code as it stood
> at commit `8bdc8c6163ee4ea88acde244a1c7e9672ab593a3`. If anything is
> inaccurate, the source is authoritative; please open a PR or issue.

# minter-datacite — quickstart

**Goal:** mint your first DOI against DataCite Fabrica test — the
sandbox. No production prefix needed; no real DOIs created.

Time: 10 minutes. Mostly waiting on Fabrica signup.

---

## Step 1 — Fabrica test account

Sign up at
[Fabrica](https://doi.test.datacite.org/sign-in) — DataCite issues:

- `repositoryId` (HTTP Basic user, e.g. `DLR.SHEPARD`)
- a temporary DOI prefix (typically `10.5072`)
- a password

Save all three.

---

## Step 2 — wire up shepard

```bash
shepard-admin minters datacite set-api-url https://api.test.datacite.org
shepard-admin minters datacite set-prefix 10.5072
shepard-admin minters datacite set-repository-id DLR.SHEPARD
shepard-admin minters datacite set-publisher "DLR e.V."
shepard-admin minters datacite set-landing-page-base \
  https://shepard.example.dlr.de/v2
shepard-admin minters datacite set-password    # prompts via tty
```

All seven commands target the runtime `:DataciteMinterConfig`
singleton — no restart needed for any of them.

---

## Step 3 — probe DataCite

```bash
shepard-admin minters datacite test-connection
```

Expected:

```text
reachable: true
statusCode: 200
latencyMs: 250
```

A 401 means the password is wrong; a network error means egress
is blocked. Fix this **before** flipping the master toggle.

---

## Step 4 — enable + activate

```bash
# Enable the plugin's runtime toggle.
shepard-admin minters datacite enable

# Activate as the default minter (deploy-time-only key).
cat >> application.properties <<EOF
shepard.publish.minter=datacite
EOF

# Restart shepard.
docker compose restart backend
```

`shepard.publish.minter` is deploy-time-only per the CLAUDE.md
"cluster identity / topology" exception — switching the active
PID provider is a re-bootstrap decision (runtime flips would
orphan in-flight publications).

---

## Step 5 — mint your first DOI

```bash
SHEPARD_URL=https://shepard-api.nuclide.systems
SHEPARD_API_KEY=...

# Pick any DataObject you have write access to.
DATA_OBJECT_APPID=019e4e56-...

curl -X POST \
  -H "X-API-KEY: $SHEPARD_API_KEY" \
  "$SHEPARD_URL/v2/data-objects/$DATA_OBJECT_APPID/publish" | jq .
```

Response:

```json
{
  "appId": "019e80aa-...",
  "entityAppId": "019e4e56-...",
  "kind": "data-objects",
  "pid": "10.5072/abc-def-1",
  "versionNumber": 1,
  "mintedAt": 1716470000000,
  "mintedBy": "alice"
}
```

The DOI lands in DataCite as a **draft** (the safe default —
drafts can be deleted; promoted ones can't).

---

## Step 6 — verify in Fabrica

Visit <https://doi.test.datacite.org/> → sign in →
search your prefix. You should see the draft DOI with:

- `Publisher: DLR e.V.`
- `URL: https://shepard.example.dlr.de/v2/data-objects/019e4e56-...`
- `Version: v1`
- `ResourceTypeGeneral: Dataset`
- `State: draft`

To promote to findable: use the Fabrica UI or flip
`defaultState=findable` and re-mint a new version.

---

## Step 7 — re-mint as new version

When the dataset changes (added DataObjects, fixed errors), force
a new mint:

```bash
curl -X POST -H "X-API-KEY: $SHEPARD_API_KEY" \
  "$SHEPARD_URL/v2/data-objects/$DATA_OBJECT_APPID/publish?force=true"
```

The response carries `versionNumber: 2` and a fresh DOI. DataCite
auto-stamps `relatedIdentifiers: [{relationType: "IsNewVersionOf",
relatedIdentifier: <previous-doi>}]` so DataCite Commons renders
the chain. shepard also back-fills the inverse `HasVersion` on the
previous DOI.

---

## Promoting from Fabrica test to production

```bash
shepard-admin minters datacite set-api-url https://api.datacite.org
shepard-admin minters datacite set-prefix <your-prod-prefix>
shepard-admin minters datacite set-repository-id <your-prod-id>
shepard-admin minters datacite set-password   # different password
shepard-admin minters datacite test-connection
```

No restart. Existing test-prefix DOIs remain resolvable forever
through their issuing minter.

---

## Going further

- **Concept DOI vs. version DOIs**: KIP1f (queued) adds a
  separate concept-DOI for "the dataset across all versions".
- **Custom resource-type map**: the plugin maps shepard's
  `digitalObjectType` to DataCite's `resourceTypeGeneral` — see
  [`reference.md §"Resource-type mapping"`](reference.md#resource-type-mapping).
  A future slice (`KIP1d-resource-map`) makes this admin-mutable.
- **Embargoed datasets**: track in aidocs/16 `KIP1d-embargo`.

---

## See also

- [`reference.md`](reference.md) — every endpoint, field, error.
- [`install.md`](install.md) — operator-side install.
- [DataCite Fabrica](https://doi.test.datacite.org/) — test sandbox.
- [DataCite metadata schema](https://schema.datacite.org/) — what's in a DOI.
