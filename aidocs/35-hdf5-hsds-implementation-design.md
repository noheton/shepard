# HDF5 / HSDS Implementation Design (E7 → A5 series)

**Scope.** Implementation-level design for backlog series **A5** ("HDF5
/ HSDS support as a new payload type"), refining epic **E7**
(`aidocs/20-epic-roadmap.md:302-381`) and demand-gauge §3.6
(`aidocs/21-user-interest-gauge.md:68-78`). Picks one architecture
along the four open decisions in E7, sketches the wire shapes,
permission bridge, migration path, and phasing.

**Status.** Implementation design. No code or migration scripts
shipped. Ready to dispatch as **A5a** once L2c lands (so HDF endpoints
can launch directly into the `/v2/` `appId` URL space).

**Snapshot date.** 2026-05-08.

**Companion docs.** Reads as a fourth member of the C-section, after
`12`/`13`/`14`/`25`. Cross-references epic E7 for the *why* and
`21-user-interest-gauge.md` §3.6 for demand sizing.

---

## 1. Hard constraint, restated

End users have existing analysis code on `h5py` / `PyTables` /
`pandas.read_hdf`. **Any HDF5 surface shepard ships must keep that
code working without translation layers.** Practically:

- **`h5pyd` over HSDS is the canonical online access path.** A user
  writes `h5pyd.File(uri, endpoint=..., api_key=...)` and gets the same
  group / dataset / attribute / slicing semantics as `h5py.File(local)`.
- **A "download original file" fallback** returns the byte-identical
  HDF5 file so plain `h5py.File(local_path)` keeps working when network
  access to HSDS isn't viable.
- **A bespoke (non-`h5py`-compatible) JSON-over-REST representation
  is a non-goal** — would force users to migrate code; rejected per E7.

This rules out the "HDF5 as opaque payload only" option from
`aidocs/21 §3.6 (a)` as a sufficient answer.

## 2. Architecture decision

Three architectures were on the table (per E7's open decisions):

| # | Shape | Verdict |
|---|---|---|
| (i) | **Embed HSDS** as an in-process Java rewrite | Rejected. HSDS is a Python service (`aiohttp`-based), 30K+ LoC. Re-implementing in Java is a multi-quarter project before we'd have parity, and bug-for-bug compatibility is the actual deliverable. |
| (ii) | **HSDS as a sidecar** that shepard brokers | **Recommended.** Vendor a known-good HSDS image; shepard's backend brokers permissions and proxies value-reads. |
| (iii) | **HSDS as an external dependency** the operator runs themselves | Allowed but not the default. Useful when the operator already runs HSDS for other workloads. Same broker shape as (ii). |

Recommendation: ship **(ii) sidecar by default** with **(iii)
external** supported via config. The all-in-one Docker image (ADR-003)
gains a `hsds` service in `infrastructure/docker-compose.yml` under a
`hdf` profile (off by default, like `spatial`).

Rationale:
- `h5pyd` parity is the deliverable, not "we wrote our own HDF5
  service." Vendoring HSDS gets us that on day 1.
- HSDS is storage-agnostic (POSIX / S3 / MinIO / Azure Blob); the
  default POSIX path keeps the all-in-one stack self-contained, and
  the S3 path lights up for operators with object storage.
- HSDS has Keycloak integration first-class — shepard already requires
  OIDC, so the realm can be shared. **This is the auth-bridge unblocker.**

## 3. Storage layer

| Storage | Default? | When |
|---|---|---|
| POSIX (volume mount) | **Yes** | All-in-one stack. Mount `/opt/shepard/hsds_data` next to the existing data volumes. |
| S3 / MinIO | Opt-in | Operators with object storage. Setting `SHEPARD_HDF_STORAGE=s3` flips HSDS env. |
| Azure Blob | Opt-in | Same as S3 path; HSDS supports it via `AZURE_CONNECTION_STRING`. |

Storage choice is **per-deployment**, not per-container. A container
created on POSIX cannot be served from S3 without an HSDS-side data
migration; that's an operator concern, not API surface.

Capacity planning rule of thumb (HSDS docs): plan for ~1.2× the raw
HDF5 size on disk because of HSDS's chunk-store overhead.

## 4. URL hierarchy mapping

shepard's existing payload-types follow the pattern
`/<kind>-containers/{containerId}` plus per-payload paths. HDF inherits
the same shape, with the *path inside the container* mirroring HSDS's
hierarchy verbatim:

| shepard URL | Maps to | Example |
|---|---|---|
| `POST /hdf-containers` | Provision a new HSDS domain | Returns container appId + HSDS domain URI |
| `GET /hdf-containers/{appId}` | List the domain root + groups | shepard projects, HSDS's `/` |
| `GET /hdf-containers/{appId}/groups/{path}` | An HDF5 group | mirrors HSDS `/groups/{groupId}` |
| `GET /hdf-containers/{appId}/datasets/{path}/value` | Read dataset values (with slicing) | mirrors HSDS `/datasets/{datasetId}/value` |
| `GET /hdf-containers/{appId}/datasets/{path}/attributes` | List attributes | mirrors HSDS `/datasets/{datasetId}/attributes` |
| `GET /hdf-containers/{appId}/file` | Download the byte-identical HDF5 file | A5d fallback |

The path components are **HDF5 paths**, not HSDS internal IDs —
that's what `h5pyd` clients write. shepard's broker translates
`/{appId}/groups/sensors/temp` → HSDS's
`POST /groups/{containerDomainId}/path-resolve` → HSDS's
`/groups/{groupId}` (or `/datasets/{id}` for leaves).

The body and query semantics are **byte-for-byte HSDS-compatible** so
`h5pyd` works. shepard adds nothing to the request/response shape on
the broker path — wrapping it would break `h5pyd` parity.

## 5. Auth bridge (the trickiest piece)

`h5pyd`'s `File(uri, endpoint=..., api_key=...)` constructor accepts
HTTP Basic, an `api_key`, or a Bearer token. The bridge requirement
is: **a shepard API key works against the HSDS endpoint without the
user managing two credentials.**

Three options were considered:

| Option | How it works | Verdict |
|---|---|---|
| (a) **HSDS reads shepard API keys directly** | HSDS forwards `X-API-KEY` to shepard; shepard validates; HSDS proceeds | Rejected. Requires patching HSDS. Drift on every HSDS upgrade. |
| (b) **Shared Keycloak realm + token relay** | shepard mints a short-lived JWT signed by the shared Keycloak; client sends it as `Authorization: Bearer …` to HSDS; HSDS validates with the realm's JWKS | **Recommended.** Zero HSDS code change. Standard OIDC flow. |
| (c) **HSDS in front of shepard** | HSDS is the front door; shepard hangs off as a metadata sidecar | Wrong inversion of responsibilities — shepard owns identity, permissions, and the unified search. |

**Recommended path (b) in detail:**

1. shepard backend gains an `/api-keys/{id}/hsds-token` endpoint that
   mints a short-lived (default `PT5M`) JWT carrying `sub=<username>`,
   `aud=hsds`, and the user's HSDS scope (see §6).
2. The token is signed with the **same Keycloak realm key** HSDS
   already validates against (`HSDS_USE_KEYCLOAK=true`,
   `HSDS_KEYCLOAK_URI=...`). No HSDS-side patching.
3. `h5pyd.File(...)` is invoked with the bearer JWT
   (`h5pyd.File(uri, endpoint=hsds_url, bearer_token=jwt)`).
4. The companion `clients/python` exposes a 3-line helper:
   ```python
   from shepard_client_hsds import open_container
   f = open_container(container_appid, api_key=APIKEY, host=HOST)
   # f is an h5pyd.File — same API as h5py
   ```
   The helper does the `/api-keys/{id}/hsds-token` call internally and
   refreshes on `PT5M` expiry.

This bridge is **A5e**. Until A5e ships, users can use HTTP Basic
against HSDS directly with credentials provisioned by an admin —
the fallback path stays open.

## 6. Permission bridge

shepard permissions are **graph-edge based**: a `User` or `UserGroup`
has explicit READ/WRITE/MANAGE rights to a `Collection`, `DataObject`,
or container. HSDS permissions are **POSIX-style domain ACLs**: each
domain has owner / read / write / manage / etc.

The bridge translates:

| shepard | HSDS |
|---|---|
| `Permissions{owner: alice, readers: [bob], writers: [carol]}` on `HdfContainer X` | HSDS domain `/<container-appid>` ACL: `alice` owner, `bob` read, `carol` read+write |
| Permission change in shepard (e.g., add `dave` to readers) | shepard's `PermissionsService` post-commit hook calls HSDS `PUT /acls/{containerDomain}/{username}` |

**Sync direction.** shepard is the **source of truth**. HSDS ACLs are
derived. A direct mutation on the HSDS side via `h5pyd` is **not
allowed** — the broker rejects `PUT /acls/...` on the proxy with a
RFC 7807 explainer pointing at shepard's permission endpoints. This
keeps the security audit story coherent.

**Group-mode ACLs.** HSDS supports per-group ACLs within a domain;
shepard's permission graph is per-container today, not per-group.
**A5b explicitly does not flow shepard permissions down to per-group
ACLs.** If a future use case wants finer-grained access, that's a
new permission shape on the shepard side first. Out of scope here.

**Public containers.** A shepard `HdfContainer` flagged PUBLIC sets
the HSDS domain ACL to `default: read`. Reverting to PRIVATE removes
the default ACL. This is the only "magic" rule.

## 7. CRUD surface

Mirrors today's `FileContainer` / `TimeseriesContainer` patterns.

| Endpoint | Body | Behaviour |
|---|---|---|
| `POST /hdf-containers` | `{name, description, attributes}` | Provisions HSDS domain `/<appId>`; persists `HdfContainer` Neo4j node + the HSDS domain URI as `hsdsDomain` property |
| `GET /hdf-containers/{appId}` | — | Read-through to HSDS; returns shepard metadata + HSDS root group listing |
| `DELETE /hdf-containers/{appId}` | — | Soft-delete in shepard (`deletedAt`), schedule HSDS domain delete via the existing data-marked-for-deletion CLI (L1) |
| `POST /data-objects/{id}/hdf-references` | `{containerId, datasetPath, name, attributes}` | Creates an `HdfReference` pointing at a specific dataset path inside the container (mirrors `FileReference`'s OID pattern) |
| `GET /hdf-containers/{appId}/file` | — | A5d: returns byte-identical HDF5 file (HSDS's bulk-export capability) |

**`HdfReference` is the per-DataObject anchor** for a specific dataset
inside a container. This is where annotations attach (E6 /
`AnnotatableHdfDataset`). One container can have many references —
typical pattern: one container per logical experiment, one reference
per analyzed dataset.

## 8. Neo4j model

Two new `@NodeEntity` classes alongside the existing payload types:

```java
@NodeEntity
public class HdfContainer extends AbstractEntity implements HasAppId {
  // appId from L2a; persisted hsdsDomain URI; storage backend label
  private String hsdsDomain;
  private String storageBackend;  // "posix" | "s3" | "azure"
}

@NodeEntity
public class HdfReference extends BasicReference {
  private String datasetPath;     // HDF5 path inside the container
  // appId / annotations / lab journal inherited from BasicReference
}
```

Neither class needs any data migration — they're new labels with no
pre-existing rows. The `HasAppId` mixin from L2a applies; the
DAO seam mints UUID v7s on first save.

**Migration `V13__Add_appId_constraint_HdfContainer_HdfReference.cypher`**:
two `REQUIRE n.appId IS UNIQUE` statements, idempotent, ships with A5a.
Listed in `aidocs/34-upstream-upgrade-path.md` as ZERO-status
(additive, no admin action).

## 9. Download-original-file fallback (A5d)

HSDS supports a bulk-export endpoint that streams the entire domain
as an HDF5 file. shepard's `GET /hdf-containers/{appId}/file` proxies
this with the standard `Content-Disposition: attachment; filename=...`
header so a browser save-as works.

For containers in the **gigabyte range**: range requests pass through
to HSDS's underlying object store (S3 supports range; POSIX path
emits via `sendfile`). No per-byte latency tax on the Java side.

For containers **without HSDS** (operator opted-out): the fallback is
served from a flat-file mirror under `/opt/shepard/hsds_data/` (POSIX
backend). Without HSDS but with `h5py.File(local_path)`, users can
still read the file after `wget`-ing it.

## 10. Phasing — A5a … A5e

| ID | Phase | Size | Gate |
|---|---|---|---|
| **A5a** | HSDS sidecar + `HdfContainer` create/read/delete + Neo4j model + V13 migration. HTTP Basic auth (admin-managed). | M | L2c (so endpoints land at `/v2/<appId>`) |
| **A5b** | Permission bridge. shepard permission changes flow to HSDS ACLs via `PermissionsService` post-commit hook. | M | A5a |
| **A5c** | `HdfReference` + annotation hookup via E6. Per-dataset annotations. | S | A5a + E6 |
| **A5d** | Download-original-file fallback (`GET /file`). | S | A5a |
| **A5e** | Auth bridge. shepard API keys → short-lived JWT for HSDS (Keycloak realm shared). 3-line `clients/python` helper. | S–M | A5a + L5 (already shipped) |

Recommended order: **A5a → A5d → A5e → A5b → A5c**. A5d unlocks the
fallback story (A5a is useless without it for anyone who wants
offline analysis); A5e unlocks the `h5pyd` ergonomics; A5b and A5c
are the maturity passes.

## 11. Test strategy

- **`h5pyd` parity IT.** A new module `backend/src/test/integration/hdf` (or a
  Python-side test under `clients/python/tests/`) opens a known
  fixture container with `h5pyd.File(...)`, asserts group navigation,
  dataset slicing (`f["temps"][10:20]`), attribute reads. Mirrors
  the upstream `h5pyd/test_app.py` shape against shepard's broker.
- **`h5py.File(local)` parity for the A5d fallback.** Download via
  `GET /file`, open with `h5py`, assert byte-identical structure.
  Catches any HSDS export-side drift.
- **Permission propagation IT.** Add a reader via shepard REST,
  assert HSDS ACL reflects within `PT500MS`. Remove a reader, assert
  HSDS ACL flips. (A5b acceptance.)
- **Auth-bridge IT.** Mint a JWT via the new endpoint, hit HSDS
  directly, assert 200. Let the JWT expire (`PT5S` test fixture),
  assert 401.

CI integration: HSDS testcontainer (`hdfgroup/hsds:latest`) on the
`@QuarkusIntegrationTest` profile under the `hdf` Maven activation.

## 12. Risks

- **HSDS upstream maintenance velocity.** If HSDS goes unmaintained,
  we own a Python service we did not write. Mitigation: pin a known-good
  tag in `infrastructure/docker-compose.yml`; have a forking plan.
- **Token relay against shared Keycloak.** Requires the operator to
  configure HSDS for Keycloak. Documented in `docs/admin.md`; falls
  back to HTTP Basic if not configured (A5a's day-1 path).
- **Permission bridge drift.** shepard's permission model may diverge
  faster than the HSDS ACL bridge can keep up. Mitigation: A5b ships
  a "rebuild ACLs from scratch" admin endpoint that re-derives every
  HSDS ACL from shepard's graph, callable when drift is suspected.
- **Storage migration mid-life.** POSIX → S3 migration is non-trivial.
  Out of scope for A5; an admin-runbook task that lives in
  `aidocs/22-admin-cli-draft.md` and triggers HSDS's offline export →
  re-import workflow.
- **Container size.** HSDS's chunk overhead means a 100 GB HDF5 file
  becomes ~120 GB on disk. Document this clearly in `docs/admin.md`'s
  capacity-planning section.

## 13. Open questions (defer answers; do not block A5a)

1. **Per-dataset permissions.** Some users will eventually want
   "Bob can read `/sensors/temp` but not `/sensors/pressure`."
   shepard's permission model doesn't go that fine today. Punt to a
   new permission-shape design when a real ask lands.
2. **Cross-container links.** HDF5 supports external links between
   files. Should shepard surface them as inter-container references
   (E6/E7-related)? **Defer to E7 vNext.**
3. **`h5pyd`-side caching.** `h5pyd` has client-side chunk caching.
   shepard's broker layer is on the request path; revisit only if
   benchmarks show meaningful round-trip pain.
4. **Backup story.** HSDS data files (POSIX or object store) need
   their own backup discipline separate from the Neo4j / Mongo /
   Timescale tarball pattern in `docs/deploy-oracle-free.md`. Document
   when A5a lands; suggest `hsadmin` HSDS tooling.

## 14. Cross-references

- `aidocs/20-epic-roadmap.md` §E7 — epic-level scope and the hard
  constraint that motivates this design.
- `aidocs/21-user-interest-gauge.md` §3.6 — demand sizing
  ("low-medium," one named asker).
- `aidocs/16-dispatcher-backlog.md` — A5 series queueing entry will
  follow this design landing.
- `aidocs/25-neo4j-id-migration-design.md` — L2c gates A5a so HDF
  endpoints launch directly at `/v2/<appId>`.
- `aidocs/34-upstream-upgrade-path.md` — A5a's V13 migration enters
  the tracker as ZERO-status (new labels, no existing rows).
- `infrastructure/docker-compose.yml` — A5a adds the `hsds` service
  under a `hdf` profile, off by default.
- `docs/admin.md` — capacity planning + storage backend choice
  documented when A5a ships.
