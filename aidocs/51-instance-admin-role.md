# Instance-Admin Role — Design (A0 + C3 + F8)

**Scope.** Implement shepard's **instance-admin role** — the
shepard-instance-wide role that gates admin endpoints, future
admin-CLI commands, feature-toggle mutations, and cleanup
operations. Bundles three logically-coupled backlog items:

- **A0** — admin-role mechanism (the headline)
- **C3** — `PermissionsService.getRoles` full-access fallback fix
- **F8** — configurable OIDC roles-claim path

**Status.** Design done. Ready for dispatch once H4 + P4 v2 land.
**Snapshot date.** 2026-05-08.
**Originating items.** `aidocs/16` rows A0 / C3 / F8;
`aidocs/07-security-issues.md` C3; `aidocs/22-admin-cli-draft.md`
§4.6 / §4.11; `aidocs/24-permission-system-review.md` F-series;
`aidocs/36-user-profile-and-settings-design.md` (profile UI
integration). User session iteration locked decisions 1-7 below.

---

## 1. Decisions locked (in this design's scope)

1. **Role tiers — single role for v1.** Just `instance-admin`.
   `viewer` / `auditor` deferred behind real demand. Future tiers
   parallel via the same `Role` node + the same CLI sub-command
   shape.
2. **C3 bundled.** `PermissionsService.getRoles`'s full-access
   fallback when an entity has no Permissions node (`aidocs/07`
   C3) is fixed in the same slice. Inverting it to fail-closed
   without admin-role infrastructure would lock everyone out;
   the two ship together.
3. **API-key minting — explicit roles field.** `POST /apikeys`
   body grows a `roles: [...]` field. No implicit inheritance —
   admin users don't accidentally mint admin-class keys for
   routine use.
4. **Bootstrap token — file on disk.** First start (no
   instance-admin exists) writes a one-time token to
   `/opt/shepard/.bootstrap-token` (mode `0600`); the operator
   reads it for the first `bootstrap-admin` invocation; the
   token deletes itself on consumption. Mirrors Vault unseal /
   RabbitMQ default-user-cookie.
5. **IdP-grant precedence.** Internal Neo4j `:HAS_ROLE` is
   removed once the IdP claim arrives for the same user — IdP
   becomes long-term source of truth; the shepard-internal
   grant is genuinely just a bootstrap / break-glass.
6. **Dual-source role check.** A user is instance-admin if EITHER
   (a) their JWT carries the configured role claim, OR (b) their
   `User` node has a `:HAS_ROLE` edge. Both surface as a single
   `roles` list on the principal.
7. **Naming**: hyphenated `instance-admin` for IDs / config /
   Neo4j / `@RolesAllowed`; **"Instance Admin"** (title-case,
   two words) for human-facing display.

---

## 2. Why this slice exists

Today's reality:

- `JWTPrincipal.getRoles()` is "always an empty array" (per
  `aidocs/24` review; M8 noted the doc invariant
  "permissions are checked inside …Service" isn't code-enforced).
- `@RolesAllowed("admin")` therefore doesn't work.
- No admin endpoints can ship — A3b (admin features), P3c, and
  the whole **L1 admin-CLI Phase 2** (cleanup commands) are
  blocked.
- F1 declarative `@Authz` needs a role mechanism; F1 is blocked.
- C3's full-access fallback is a critical security finding that
  can't be fixed without a way to grant legitimate admin access.

**Net**: A0 + C3 + F8 are a tight triangle of dependencies where
shipping one without the others either fails-open (current state)
or fails-closed-everywhere (would be the C3 fix in isolation).
This design ships the triangle as one slice.

---

## 3. The model

### 3.1 Neo4j entities

```java
@NodeEntity
public class Role implements HasAppId {
  @Property private String name;       // "instance-admin"
  @Property private String displayName; // "Instance Admin"
  // appId / createdAt etc. inherited
}
```

A small singleton: one `Role` node per role name. v1 has exactly
one row (`instance-admin`). Future tiers (`viewer` / `auditor`)
add rows.

The relationship from `User` to `Role`:

```cypher
(:User {username: $sub})-[:HAS_ROLE {grantedBy: $grantingUser, grantedAt: $ts}]->(:Role {name: "instance-admin"})
```

The relationship's properties record **who granted the role and
when** — audit trail without a separate audit table (until F3
ships its richer Postgres-backed audit log).

### 3.2 Configuration

`backend/src/main/resources/application.properties`:

```
# F8 — configurable roles-claim path. Default targets Keycloak's shape.
shepard.oidc.roles-claim-path=realm_access.roles

# A0 — name of the role string within the claim that maps to
# instance-admin. Default empty → IdP grants no instance-admin
# (shepard-internal :HAS_ROLE is the only path).
shepard.instance-admin.role=
```

Example operator override (Keycloak deployment with role
`shepard-instance-admin` minted into the realm-access claim):

```
shepard.instance-admin.role=shepard-instance-admin
```

Pocket ID / Authentik / Azure AD operators set both keys per
`aidocs/22 §4.11a.5`. Default-empty `shepard.instance-admin.role`
means "no IdP-side admin in this deployment" → Neo4j-internal is
the only path → safe-by-default.

### 3.3 Dual-source role resolution

`JWTFilter.parsePrincipalFromAccessToken` (post-A0):

1. Parse JWT as today.
2. Walk `shepard.oidc.roles-claim-path` via Jackson (F8); collect
   the role-strings from that path.
3. If `shepard.instance-admin.role` is non-empty AND the configured
   role-string is in the collected set → add `"instance-admin"` to
   `principal.roles`.
4. Look up the user's `User` node; if a `:HAS_ROLE`
   edge exists to the `Role {name: "instance-admin"}` node →
   add `"instance-admin"` to `principal.roles` (deduplicated).
5. Continue.

For the API-key path: `parseApiKey` similarly populates
`principal.roles` from the API key's stored `roles: Set<String>`
(see §4.2).

### 3.4 Per-decision-5 behaviour: shepard-internal grant
auto-removal

A startup hook (or a scheduled `@Scheduled` job, frequency
`PT1H`) walks `:HAS_ROLE` edges. For each edge from User U to
Role R, if there's been a **successful login** for U within the
last 24h AND U's most recent JWT had the IdP-claim role for R,
the edge is removed. **The user keeps the role** — their next
login proves it via the IdP — but shepard's internal copy is
cleaned up so the IdP becomes the source of truth.

Rationale (per decision 5): the bootstrap is a one-time event;
once the IdP catches up to the bootstrap user's role, internal
state should evaporate so there's no drift. If the IdP never
catches up (operator never wires up the realm-mapper), the
internal `:HAS_ROLE` edges persist indefinitely — that's also
correct.

If the user logs in but the JWT does NOT carry the role: the
edge stays. If the operator revoked the IdP role intending to
kill admin access, the cleanup hook would not strip the
shepard-internal grant — admin access continues. **Operators
who want to fully revoke must use `shepard-admin instance-admin
revoke <user>`**, not just delete the IdP role. Documented.

---

## 4. The API key path

### 4.1 Schema additions

Per L5, API keys are hybrid Neo4j-row + JJWT-encoded with
`validUntil`. Add:

```java
@NodeEntity
public class ApiKey {
  // ... existing fields including validUntil from L5 ...
  @Property private Set<String> roles = new HashSet<>();
}
```

The JWT minted on key issue carries a `roles: ["instance-admin"]`
custom claim (or whatever subset of the minter's roles was
requested).

### 4.2 Mint flow (per decision 3 — explicit)

`POST /apikeys` body grows:

```json
{
  "name": "production-cleanup-script",
  "validUntil": "2026-12-31T23:59:59Z",
  "roles": ["instance-admin"]
}
```

Validation:
- Each role in the request body MUST be in the **caller's own
  roles** (no privilege escalation).
- Each role MUST be in `shepard.apikey.role-allowlist` (default
  singleton `[instance-admin]`; operator can shrink to `[]` to
  forbid role-bearing keys entirely).
- Empty `roles: []` is fine — the resulting key has no role
  claims, which matches today's behaviour.

UI hint on the `/me` API-keys pane (post-`aidocs/36` U1c): the
"Create key" form shows a checkbox `[ ] Inherit my Instance Admin
role` only when the user has the role. Default unchecked.

### 4.3 Validation on read

`JWTFilter.parseApiKey`:
- Pull the `roles` claim from the API-key JWT.
- Cross-check against the API key's stored `roles: Set<String>` in
  Neo4j (the JWT is signed but the Neo4j row is the authoritative
  revocation surface — same shape as L5's expiry handling).
- Surface to `principal.roles`.

Mismatch (JWT says roles X, Neo4j says roles Y) → 401 with
`auth.invalid_token` (`aidocs/16` H4 catalogue). Treats it as a
forged token.

---

## 5. Bootstrap mechanism

### 5.1 First-start hook

A startup hook (post-A1e fail-fast, after migrations): if
**zero** users have the `:HAS_ROLE → instance-admin` edge AND
**zero** API keys carry `instance-admin` in their `roles` set,
shepard is in **bootstrap state**.

In bootstrap state:
- Generate a UUID v4 random token.
- Write to `/opt/shepard/.bootstrap-token`, mode `0600`, owner
  whoever the JVM runs as.
- Log a single-line `INFO`: "BOOTSTRAP: shepard has no
  instance-admin yet. Run `shepard-admin instance-admin bootstrap
  --token <see /opt/shepard/.bootstrap-token> --user <username>`
  to grant the role."
- Set a Neo4j flag node (`(:BootstrapState {tokenHash: $sha256, createdAt: ...})`) so subsequent restarts know the token is outstanding.

Subsequent restarts in bootstrap state regenerate the token (the
old one is invalidated by the `tokenHash` mismatch). Idempotent.

### 5.2 The bootstrap CLI command

```
shepard-admin instance-admin bootstrap --user <username>
```

The CLI reads `/opt/shepard/.bootstrap-token` (operator must
have file access — typically `sudo cat`-able by the shepard
operator). Sends to a new `POST /v2/admin/bootstrap` endpoint with
body `{token: <token>, username: <username>}`. The endpoint:

1. Verifies bootstrap state (zero existing instance-admins + the
   `:BootstrapState` node exists).
2. Verifies `sha256(token) == BootstrapState.tokenHash`.
3. Creates the `:HAS_ROLE` edge for the named user, attributing
   the grant to `bootstrap` (a synthetic granter name; not a real
   `User` node).
4. Deletes the `:BootstrapState` node.
5. Returns the new role assignment.

The CLI then **deletes `/opt/shepard/.bootstrap-token`** locally
on the operator's machine so a leaked-token-in-bash-history
window is closed.

### 5.3 First-time-UX in the init wizard

The `shepard-admin init` wizard (per `aidocs/22 §4.11`) gains a
final step: "Who is your instance admin?" — collects a username
that the operator's IdP will surface (or "skip — I'll bootstrap
manually later"). On commit:

- Wizard configures the IdP wiring (Keycloak realm mapping or
  Pocket ID custom-claim per `aidocs/22 §4.11a.3`).
- Wizard runs `shepard-admin instance-admin bootstrap --user
  <picked-name>` against the freshly-started shepard.
- Three commands → admin-ready: `git clone … && shepard-admin
  init && docker compose up -d`.

---

## 6. CLI commands

```
shepard-admin instance-admin bootstrap --user <username>
shepard-admin instance-admin grant      <username>
shepard-admin instance-admin revoke     <username>
shepard-admin instance-admin list
```

`grant` and `revoke` require the caller to **already be**
instance-admin. They mutate the `:HAS_ROLE` edge directly via a
new `POST /v2/admin/instance-admins` / `DELETE /v2/admin/instance-admins/{username}`.

`list` reads `GET /v2/admin/instance-admins` and prints a table:

```
USERNAME                              SOURCE       GRANTED BY        GRANTED AT
alice@example.dlr.de                  IdP          —                 2026-05-08
haas_tb (Keycloak f:UUID:haas_tb)     Neo4j edge   bootstrap         2026-04-12
```

Source column distinguishes IdP-claim-only / Neo4j-edge-only /
both. Per decision 5, `both` is transitional — the auto-cleanup
job runs hourly to drop redundant edges.

The TUI mode (per `aidocs/22 §4.x`) of `instance-admin list` is
the **picker** for `revoke` — operator drills in, ticks a row,
confirms.

---

## 7. JAX-RS plumbing

`@RolesAllowed("instance-admin")` works via standard JAX-RS
`SecurityContext.isUserInRole(role)`. Wired through:

1. `JWTFilter` puts the `JWTPrincipal` (with populated `roles`)
   on the `SecurityContext`.
2. `JWTPrincipal.isUserInRole(String)` consults its `roles`
   field.

Quarkus's `@RolesAllowed` integration works out of the box once
this plumbing is right.

**For the F1 declarative `@Authz` evolution**: F1 designs a
seam where action-resource gates can be expressed declaratively
(`@Authz(action = DELETE, resource = COLLECTION)`). A0 ships the
boring `@RolesAllowed` first; F1 introduces the declarative layer
on top later without breaking A0's behaviour.

---

## 8. C3 fix bundled

`PermissionsService.getRoles` (`backend/.../auth/permission/services/PermissionsService.java:258-262`):

**Before:**
```java
if (permissionsEntity.isEmpty()) {
  return new Roles(false, true, true, true);  // legacy entity full-access default
}
```

**After:**
```java
if (permissionsEntity.isEmpty()) {
  return new Roles(false, false, false, false);  // fail-closed
}
```

Plus a startup audit (or a Cypher migration) that reports any
existing `BasicEntity` lacking a `:has_permissions` edge. With
A0's instance-admin role available, those audit findings are
operationally fixable: the admin can run `shepard-admin
permissions backfill` (new CLI command, lands in this slice) to
attach a default Permissions node naming the operator-configured
"shepard-default-owner" user.

For a deployment with **only** legacy entities (someone upgraded
from a really old shepard), the backfill is essential before
flipping the default. The slice ships in this order:

1. Migration: `V13__Backfill_orphan_permissions.cypher` —
   creates a default Permissions node attached to every
   permission-less entity, using `shepard.permissions.default-owner`
   config (operator must set; refuses to start if unset and
   orphan entities exist).
2. The `getRoles` flip.
3. The audit report endpoint at `GET /v2/admin/permission-audit`.

**Operator runbook** in the same PR's `aidocs/34` row:
"set `shepard.permissions.default-owner` to a real user before
the first start; otherwise V13 refuses to apply."

---

## 9. Display surfaces

### 9.1 `/me` profile page (`aidocs/36` U1c)

The profile page gains a **Roles** section, read-only:

```
Roles
─────
  Instance Admin                Source: IdP
  (no other roles)
```

Sources displayed: `IdP` (claim-derived) / `Local` (Neo4j edge) /
`Bootstrap` (initial). For users without any role, the section
shows a single line: `(none)`.

### 9.2 `/admin` page (`aidocs/36` U1c, the operator-facing split)

A new sub-pane "Instance admins" lists users with the role
(same data as `shepard-admin instance-admin list`). Each row has
**Revoke** action (with confirmation) — admin-only, operator
side of the split.

### 9.3 Audit-trail badge

Per `aidocs/36 §3` `effectiveDisplayName`: the "Created by …"
audit-trail render gains a small **"Instance Admin"** badge next
to the name when the creator carries the role at view-time.

```
Created: 14 Oct 2025 by Alice Smith [Instance Admin]
```

The badge reflects the **current** role state, not the
state-at-creation-time. Historical "this user was admin when they
did X" is the F3 audit-log's job (post-`aidocs/24` F3).

### 9.4 Role-in-current-context indicator (user request 2026-05-12)

A casual user opening a Collection / DataObject wants to know "what
am I allowed to do here?" at a glance. The frontend shell renders a
**role-in-context chip** in the page header that derives from the
current entity's permissions for the current user:

```
[ Owner ]        ← can read + write + manage permissions
[ Editor ]       ← can read + write
[ Reader ]       ← read-only
[ No access ]    ← shouldn't normally render; page would 403 first
```

Plus the **"Instance Admin"** chip whenever
`JWTPrincipal.roles.contains("instance-admin")`, side-by-side, so
admins can see they're operating in an elevated context.

Source of truth = the existing `PermissionsService.getRoles(entity)`
call result, surfaced via the existing
`profile=relations`-style response augmentation (per `aidocs/56`).
No new endpoint needed; the existing entity GET already carries
enough info if `profile=relations|all` is requested.

Slice ID: **U1c2 — Role-in-context chip in page header.** Lives
alongside U1c "Profile + roles section" per `aidocs/36`.
Implementation order: ship after CP1 (`aidocs/58 §5`) lands so the
chip's per-Collection defaults can read from `:CollectionProperties`.

### 9.5 Admin-page metrics embed (user request 2026-05-12)

The `/admin` operator pane (§9.2) gains an **"Instance health"
strip** at the top that links to — or, where possible, embeds —
the key Grafana panels from the `monitoring` compose profile
(`docs/admin.md §Performance metrics`):

- HTTP requests/sec + p95 latency (last hour) → links to the
  "shepard — Overview" dashboard.
- JVM heap usage (last 6 h) → links.
- Permissions-cache hit ratio (current value as a single
  big-number stat) → links.

Two implementation paths considered:

1. **Link-only** (cheapest, v1). The strip is a Vue card with a
   one-line summary fetched from `/v2/admin/metrics-summary` (a
   new tiny endpoint that wraps a handful of `instant`-style
   Prometheus queries server-side, so the operator's browser
   never talks to Prometheus directly). Each summary card carries
   an external-link icon to the Grafana panel.
2. **Embed** via Grafana's `iframe` panel-share or
   `/render/d-solo/...` URL. Plays better visually but requires
   either anonymous Grafana access (a no-go for non-dev
   instances) or proxying through shepard's auth — out-of-scope
   for v1.

Slice ID: **A3b1 — Admin-page metrics strip** (link-only;
`/v2/admin/metrics-summary` endpoint server-side). Depends on
A0 (`@RolesAllowed("instance-admin")`) — already shipped — and
on the monitoring profile being up (operator runtime choice).
The embed-via-iframe path becomes A3b2, deferred.

---

## 10. Endpoints (all under `/v2/`)

Per the API-version policy in `CLAUDE.md`:

| Method + path | Purpose |
|---|---|
| `POST /v2/admin/bootstrap` | One-shot bootstrap-token → first instance-admin |
| `GET /v2/admin/instance-admins` | List users with the role + source |
| `POST /v2/admin/instance-admins` | Grant role to a user (admin-gated) |
| `DELETE /v2/admin/instance-admins/{username}` | Revoke (admin-gated) |
| `GET /v2/admin/permission-audit` | List entities lacking `:has_permissions` edges (post-C3) |

`/shepard/api/...` paths get **no** new endpoints — admin surface
lives under `/v2/`.

---

## 11. Phasing — A0 implementation slice

This is **one** slice (single PR / agent dispatch):

| Step | What |
|---|---|
| 1 | F8: configurable `shepard.oidc.roles-claim-path`; `JWTFilter` walks the dot-path |
| 2 | New `Role` entity + `:HAS_ROLE` relationship + `V13__Add_appId_constraint_Role.cypher` (V11 added) |
| 3 | C3 fix: `getRoles` fail-closed + `V14__Backfill_orphan_permissions.cypher` + `shepard.permissions.default-owner` config |
| 4 | `JWTPrincipal.roles` populated from dual-source check |
| 5 | API-key schema + mint endpoint + validate path |
| 6 | Bootstrap-token mechanism + `POST /v2/admin/bootstrap` |
| 7 | `GET/POST/DELETE /v2/admin/instance-admins` endpoints |
| 8 | `GET /v2/admin/permission-audit` |
| 9 | `shepard-admin instance-admin {bootstrap,grant,revoke,list}` CLI commands (matches `aidocs/22 §4.11` shape) |
| 10 | Backend + CLI tests |

Single agent dispatch when H4 + P4 v2 land — they don't conflict
file-wise (A0 mostly touches `auth/` + `JWTFilter`; H4 touches
`exceptions/`; P4 touches `application.properties` + `*Rest.java`
`@Path` annotations).

---

## 12. Migrations + tracker rows

- `V14__Backfill_orphan_permissions.cypher` — scans for entities
  without `:has_permissions` edges; creates default Permissions
  node attached to `shepard.permissions.default-owner`. Refuses
  to apply if orphans exist and config is unset (fail-fast).
- `V15__Add_appId_constraint_Role.cypher` — unique constraint on
  the new `Role` label.

**`aidocs/34` rows:**

- A0 + F8 → **AWARE** (operators must set `shepard.instance-admin.role` to enable IdP-side admin grants; the CLI bootstrap path works without).
- C3 → **AWARE** (operators must set `shepard.permissions.default-owner` if they have any orphan entities; refuses to apply otherwise).
- API-key role-claim shape → **AWARE** (existing API keys keep working with empty `roles`; clients that want admin-gated paths must mint new keys with `roles: ["instance-admin"]`).

**`aidocs/44` matrix:** new "instance-admin role" rows under
§3 Auth/security; F1 row updates from "blocked on A0" to "now
unblocked, queued."

---

## 13. Risks

- **Bootstrap-token leak.** Token is mode 0600 on the shepard
  host but the operator may copy it to their workstation
  insecurely. Mitigation: token has 5-min TTL after generation
  (or until consumed). Operator-side: standard "treat like a
  password" guidance in `docs/admin.md`.
- **`/opt/shepard/.bootstrap-token` not writable.** First-start
  hook fails the startup with a clear error if the configured
  bootstrap-token path is unwritable. Operator picks a writable
  path via `shepard.bootstrap.token-path` config.
- **Drift between IdP grant and Neo4j grant.** Decision 5's
  auto-cleanup hook handles the typical case. Edge case: a user
  whose IdP grant is removed but who had a shepard-internal grant
  (from an earlier `instance-admin grant`) — they keep admin
  access via the Neo4j edge. Operators who want full revocation
  must run `instance-admin revoke`. Documented in `docs/admin.md`.
- **API-key escalation via misuse of `inherit roles`.** Decision 3
  requires explicit `roles: [...]` field; combined with the
  caller-must-have-the-role check (§4.2), this is fail-safe. An
  attacker who steals a non-admin user's session can't mint
  admin keys.
- **Bootstrap-state Neo4j flag not cleaned up.** If bootstrap
  fails mid-flight, the `:BootstrapState` node persists. Idempotent
  — next start regenerates with a fresh token. Operator can
  manually `MATCH (b:BootstrapState) DETACH DELETE b` if
  troubleshooting.

---

## 14. Cross-references

- **`aidocs/16`** — A0 / C3 / F8 rows flip to done when this
  ships; F1 / A3b / L1 Phase 2 / P3c become buildable.
- **`aidocs/07`** — H4 (RFC 7807 — H4 in flight) + C3 (this
  slice) updated.
- **`aidocs/22 §4.6 / §4.11 / §4.11a`** — admin CLI;
  `instance-admin` sub-command in §4.6; bootstrap step in §4.11
  init wizard; OIDC sub-flow in §4.11a couples to F8.
- **`aidocs/24`** — F1 declarative `@Authz` (now unblocked); F3
  audit log (depends on this slice's `:HAS_ROLE` shape); F8 (now
  shipped).
- **`aidocs/25`** — L2c mentions `PermissionsService.isAllowed`
  numeric-only segment dispatch as **L2d**'s territory; A0
  doesn't touch it.
- **`aidocs/34`** — three new AWARE rows.
- **`aidocs/36 §3 / §5.1 / §5.2`** — profile page + `/admin`
  split + audit-trail render. Roles section + Revoke action +
  badge.
- **`aidocs/42 §"Where it's going"`** — instance-admin role
  unblocks "the cleanup commands users actually want" line item.
- **`aidocs/44 §3 / §13`** — new matrix rows.
- **`aidocs/47 §3.4 / §4.6a`** — admin-CLI env-driven auth
  expects an admin-role API key; this slice makes such keys
  mintable.
