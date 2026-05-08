# Git Integration — Design

**Scope.** Concept design for tracking artifacts in external git
repositories alongside shepard data. Defines a new payload-kind
`GitReference`, the per-user PAT settings the design needs, and the
RO-Crate / provenance integration.

**Status.** Concept design. No code or migration shipped.
**Snapshot date.** 2026-05-08.
**Originating items.** User request "how could git be integrated in
shepard to track artifacts. git pat candidate for settings."
Couples to `aidocs/36 §3.2` (the `git.pat` / `git.host` settings)
and `aidocs/30` (provenance — git commit SHA is a natural lineage
anchor).

## 1. Why git, and what shape

Researchers' analysis code lives in git (GitLab / GitHub / Gitea).
Today shepard has no first-class link to those repos:

- The most they can do is paste a URL into a `Description` attribute
  or a lab-journal entry — opaque text, no provenance, no
  freshness signal.
- RO-Crate exports cite `journalContent` prose but no commit SHA,
  so a reproducer has no idea which version of the analysis code
  produced which output.

The right shape: **a new `GitReference` payload-kind alongside
`FileReference` / `TimeseriesReference` / etc.**, anchoring a
DataObject to a `(repoUrl, ref, path)` tuple. This is consistent
with shepard's existing payload model.

## 2. Three use-modes

| Mode | What the user does | What shepard does |
|---|---|---|
| **(a) Loose link** | Pastes a git URL into a GitReference | Stores the URL; renders a clickable link in the UI; no fetch |
| **(b) Tracked artifact** | Creates a GitReference with `(repoUrl, ref, path)` | Stores the tuple; on read, fetches the file content + last-commit metadata via the user's PAT (`git.pat` / `git.host` from `aidocs/36 §3.2`); renders inline (markdown / source) |
| **(c) Pinned snapshot** | Creates a GitReference with `(repoUrl, sha, path)` | Same as (b) but pinned — `sha` is immutable, the content is reproducible. Used for RO-Crate exports. |

(a) ships first as the zero-server-state path. (b) and (c) need
the per-user PAT settings + a host-aware git client. **(c) is what
unlocks reproducible exports** — see §6.

## 3. Backend model

```java
@NodeEntity
public class GitReference extends BasicReference {
  // appId / annotations / lab journal inherited from BasicReference
  private String repoUrl;     // e.g. "https://gitlab.dlr.de/group/repo"
  private String ref;         // branch name, tag, or "HEAD"
  private String sha;         // resolved commit SHA, optional (mode c)
  private String path;        // path inside the repo
  // Provenance (set by §6 export pipeline; nullable in mode (a)):
  private String resolvedSha; // the SHA that fulfilled `ref` at last fetch
  private Instant resolvedAt; // when `resolvedSha` was captured
}
```

`BasicReference` ancestry inherits `HasAppId` (L2a). One per-DataObject
GitReference is the typical case; multiple is allowed.

**Container?** Unlike file/timeseries, git doesn't need a "container"
— a repo URL is its own anchor. Skip a `GitContainer` parent.

### 3.1 Migration

`V14__Add_appId_constraint_GitReference.cypher` — single
`REQUIRE n.appId IS UNIQUE` for the new `GitReference` label.
Idempotent, additive, ZERO row in `aidocs/34`.

## 4. Per-user PAT settings (depends on aidocs/36 secret-class pattern)

The `git.pat` and `git.host` settings from `aidocs/36 §3.2` are the
auth bridge. Workflow:

1. User adds a `git.pat` for their git host via `/me` settings UI.
   Encrypted at rest (`aidocs/36 §3.3`); `gitPatPresent: true` in
   `GET /users/me`.
2. User creates a GitReference. Backend reads the user's encrypted
   PAT, calls the git host's API (GitLab v4 / GitHub REST / Gitea
   v1) with `Authorization: Bearer <pat>`, fetches:
   - File content for `(ref, path)` (mode b/c).
   - Latest commit SHA on `ref` (mode b → recorded as `resolvedSha`).
3. Result cached for `PT5M` per `(user, repoUrl, ref, path)`. Cache
   eviction on user-side PAT rotation.

**No PAT, no fetch.** A GitReference created in mode (b)/(c) without
a PAT renders the URL but skips the inline preview. UI hint: "Add a
git PAT in your settings to enable inline preview."

**Multi-host.** v1 supports one PAT per user (the `git.host` setting
specifies which host it targets). Multi-host (one PAT per host) is
deferred — the typical research user has one git account.

## 5. Git client choice

**JGit** (`org.eclipse.jgit:org.eclipse.jgit:6.x`) is the canonical
JVM git library. Pure Java, no `git` binary on the host.

For this design, **don't use JGit**: we don't need to clone, just to
fetch single files and commit metadata via the host's REST API. A
small per-host adapter (`GitLabRestClient`, `GitHubRestClient`,
`GiteaRestClient`) keeps the dependency surface minimal.

```java
interface GitHostClient {
  GitFileContent fetchFile(String repoUrl, String ref, String path, String pat);
  String resolveRef(String repoUrl, String ref, String pat);  // -> sha
}
```

Three implementations cover ~95% of the user base. Other hosts
(BitBucket, Codeberg) added by request via the same interface.

## 6. RO-Crate / provenance integration

This is the high-leverage payoff. Today's RO-Crate exports
(`aidocs/31`) walk Collection / DataObject / Reference and emit
metadata; they have no concept of code provenance.

After G1c:

- Each GitReference becomes a `SoftwareSourceCode` entry in the
  RO-Crate.
- `@id` = `https://<host>/<repo>/-/blob/<resolvedSha>/<path>`
  (immutable, points at the exact code that produced the result).
- `gitsha`, `repoUrl`, `path` recorded as properties.
- For mode (b) GitReferences (no pinned SHA), the export pipeline
  resolves `ref` to a SHA at export time and records that SHA.
  **Reproducibility is then guaranteed** even if the branch moves
  later.

This matches the OpenLineage `dataset.facet.dataSource` shape from
`aidocs/30 §provenance`, so a future "provenance" view can render
git-backed ancestors uniformly with file/timeseries ancestors.

## 7. Phasing

| ID | Slice | Size | Gate |
|---|---|---|---|
| **G1a** | `GitReference` payload-kind (mode a, loose link only). Neo4j model + V14 migration + `/v2/data-objects/{id}/git-references` CRUD. UI renders as clickable link. | S | None |
| **G1b** | Mode (b) tracked-artifact + per-host adapter (`GitLabRestClient` first). Reads user's `git.pat` from `aidocs/36 §3.2`. Inline preview for markdown / source files. Caches per-`(user, repoUrl, ref, path)` for `PT5M`. | M | aidocs/36 U2-coupled (secret-class settings + `git.pat`) |
| **G1c** | Mode (c) pinned snapshot + RO-Crate `SoftwareSourceCode` integration. Resolves `ref` to SHA at export time. | M | G1b + `aidocs/31` |
| **G1d** | GitHub + Gitea adapters (the GitLab adapter from G1b is reusable as the reference shape). | S | G1b |
| **G1e** | (deferred) Webhook from git host → shepard for "the analysis code changed" notifications. Subscriptions integration. | M | G1b |
| **G1f** | (deferred) shepard pushes artifact metadata back to git as a sidecar `.shepard.yaml` file. Gitops shepard. | L | None — explicit non-goal for v1 |

Recommended order: **G1a → G1b → G1c → G1d**. G1a ships value with
zero new dependencies; G1b unlocks the inline view; G1c unlocks
reproducible exports.

## 8. Endpoints (all under `/v2/`)

Per the API-version policy in `CLAUDE.md`:

| Method + path | Purpose |
|---|---|
| `POST /v2/data-objects/{id}/git-references` | Create a GitReference on a DataObject. Body: `{repoUrl, ref?, sha?, path?, name, attributes}`. |
| `GET /v2/git-references/{appId}` | Read a GitReference (metadata + cached `resolvedSha`). |
| `GET /v2/git-references/{appId}/content` | Stream the file content (cached if mode b/c, 404 if mode a). |
| `PATCH /v2/git-references/{appId}` | Update mutable fields via merge-patch (P21x). |
| `DELETE /v2/git-references/{appId}` | Soft-delete (consistent with other reference kinds). |

`/shepard/api/...` paths get **no** new endpoints — the upstream
client surface is unchanged.

## 9. Risks

- **PAT scope creep.** Users will paste over-scoped PATs ("read +
  write all"). UI warns: "Read-only PAT recommended." Beyond
  documentation, shepard cannot enforce — that's a git-host policy
  choice.
- **PAT exfiltration.** AES-GCM at rest mitigates DB dump; in-memory
  is the residual risk. Server-key compromise is a critical-incident
  scenario that recovers via rotation + re-prompting users for new
  PATs (the rotation mechanism from `aidocs/36 §3.3`).
- **Rate limiting.** Git hosts rate-limit per token. The `PT5M`
  cache + per-user-per-tuple key keeps this tame for typical use.
  For organisations with many shared shepard users on the same
  tiny git instance, document the consideration in `docs/admin.md`.
- **Network egress.** Self-hosted shepard deployments behind a
  firewall need outbound access to the git host. Document; offer a
  per-host allowlist in v2 if requested.
- **Reproducibility lies.** Mode (b) "tracked" can drift between
  shepard view and actual git state when the branch moves. Mitigation:
  the UI shows `resolvedAt` timestamp; mode (c) "pinned" is the
  reproducible answer for exports.

## 10. Out of scope (deferred)

- **shepard as git host.** Mentioned for completeness; explicitly
  not. shepard is a research-data platform, not a code-hosting
  platform.
- **Submodule / monorepo support.** Single file at a time is the v1
  payload. Tarballing a directory is a follow-up if asked.
- **Diff view between two commits.** Out of scope; the git host's
  UI does this better.
- **Push-from-shepard.** G1f mentions but defers; gitops is a
  separate philosophy.

## 11. Cross-references

- **aidocs:** `aidocs/30` (provenance — git is a natural lineage
  source), `aidocs/31` (RO-Crate export — `SoftwareSourceCode`
  emission lands at G1c), `aidocs/36 §3.2 / §3.3` (per-user PAT
  settings + secret-class pattern).
- **Backlog:** new **G1** umbrella + G1a-G1f sub-IDs in
  `aidocs/16`. Gates: G1b on `aidocs/36 U2-coupled`; G1c on
  `aidocs/31`.
