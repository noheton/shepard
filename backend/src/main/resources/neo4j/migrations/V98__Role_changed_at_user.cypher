// ROLE-GRANT-STALE-SESSION-02 — INTENTIONALLY EMPTY (NOOP).
//
// :User gains a new optional `roleChangedAt` long property (millis-since-epoch)
// stamped by InstanceAdminService.grantInstanceAdmin / .revokeInstanceAdmin
// on every role mutation. JwtTokenAuthService consults it on every OIDC
// Bearer authentication: when the presented JWT's `iat` (issued-at, seconds
// since epoch) multiplied by 1000 is less than `roleChangedAt`, the token
// is rejected with HTTP 401 + body `{"error":"role_changed", ...}` so the
// user is forced through a sign-out + re-auth that picks up the new role
// set.
//
// Neo4j is schema-less on properties, so the additive nullable property
// needs no DDL change. Existing :User rows simply lack the property; OGM
// reads the absence as null. A null value means "no recorded role change
// for this user" and the gate passes through (matching the existing
// behaviour — every pre-feature row treated as never-changed).
//
// API-key principals (X-API-Key header) bypass this gate entirely — they
// are long-lived service tokens that don't carry a JWT `iat`, and the
// API key path doesn't go through `parsePrincipalFromAccessToken`.
//
// Operator runbook: no action required. To inspect users whose role set
// has been mutated since the feature shipped:
//   MATCH (u:User) WHERE u.roleChangedAt IS NOT NULL
//   RETURN u.username AS sub, u.email,
//          datetime({epochMillis: u.roleChangedAt}) AS lastRoleChange
//   ORDER BY u.roleChangedAt DESC LIMIT 50;
//
// To manually clear a user's stamp (e.g. operator override after
// confirming the user has fresh-tokened): NOT RECOMMENDED. The stamp is
// per-token-rejection state, not auditable provenance — clearing it
// re-opens the window for the stale JWT. Just let the user sign in
// again; the next JWT's `iat` will be > `roleChangedAt` and the gate
// passes naturally.
//
// Companion to: docs/admin/runbooks/14-role-grants.md (operator runbook).
// Rollback: V98_R__Role_changed_at_user.cypher
RETURN 1;
