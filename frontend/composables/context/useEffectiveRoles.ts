/**
 * FE-ROLE-DUAL-SOURCE-1 — global cache of the caller's effective roles.
 *
 * The frontend's `hasInstanceAdminRole(token)` JWT parse only sees
 * `realm_access.roles` — IdP claims. The backend's
 * `JwtTokenAuthService.resolveDualSourceRoles` merges those with
 * Neo4j `:HAS_ROLE` edges, so a Cypher-path grant gives backend admin
 * but NOT frontend admin (the JWT was minted before the grant).
 *
 * The clean shape is to defer the check to a backend round-trip.
 * Today the v1 `/shepard/api/users` (UserApi.getCurrentUser) does not
 * yet return an `effectiveRoles` field — the new shape is tracked under
 * **BE-USERS-ME-ROLES-1** in `aidocs/16-dispatcher-backlog.md`. Until
 * the backend adds the field, this composable returns JWT-derived roles
 * (the existing behaviour) so nothing regresses. When the backend lands
 * the field, the consumer of `getCurrentUser()` writes it into this
 * composable via `setEffectiveRoles(...)` and the JWT fallback drops
 * out automatically.
 *
 * Loading state: callers MUST distinguish "still loading" from
 * "definitely not admin" so the UI doesn't flash unauthorized while
 * the profile fetch is in flight. `effectiveRoles.value === undefined`
 * means "not loaded yet" — `isInstanceAdmin.value === undefined`
 * during loading; `true` / `false` once known.
 */
import { parseJwtPayload } from "~/utils/auth";

export const INSTANCE_ADMIN_ROLE = "instance-admin";

/**
 * Pull the role list from a JWT payload's `realm_access.roles`
 * (the only client-visible source pre-BE-USERS-ME-ROLES-1).
 */
export function rolesFromAccessToken(token: string | undefined | null): string[] {
  if (!token) return [];
  const payload = parseJwtPayload(token);
  if (!payload) return [];
  const realmRoles =
    (payload.realm_access as { roles?: string[] } | undefined)?.roles ?? [];
  return [...realmRoles];
}

/**
 * Shared reactive surface. `undefined` while loading; an array (possibly
 * empty) once the profile resolves OR once the JWT fallback fires.
 */
export function useEffectiveRoles() {
  // Global so any caller (HeaderBar, admin pages, future role-gated UI)
  // shares the same source-of-truth. Nuxt `useState` is SSR-safe.
  const effectiveRoles = useState<string[] | undefined>(
    "effective-roles",
    () => undefined,
  );

  /**
   * Hydrate from the backend `/shepard/api/users` response. Looks for
   * `effectiveRoles` (BE-USERS-ME-ROLES-1, planned) on the wire shape.
   * Until that field lands the backend response carries no roles, so
   * we fall back to the JWT. Either way the post-hydrate value is a
   * concrete array (never `undefined`) so callers leave the loading
   * branch.
   */
  function hydrateFromProfile(
    profile: { effectiveRoles?: string[] } | null | undefined,
    accessToken: string | undefined | null,
  ): void {
    if (profile && Array.isArray(profile.effectiveRoles)) {
      effectiveRoles.value = [...profile.effectiveRoles];
    } else {
      effectiveRoles.value = rolesFromAccessToken(accessToken);
    }
  }

  /**
   * `undefined` while loading (don't flash unauthorized); `true`/`false`
   * once known. Use in `v-if` / computed gates and pair the loading
   * branch with the same `status === "loading"` guard the calling page
   * already has.
   */
  function isInstanceAdmin(): boolean | undefined {
    const roles = effectiveRoles.value;
    if (roles === undefined) return undefined;
    return roles.includes(INSTANCE_ADMIN_ROLE);
  }

  /**
   * Cleared on sign-out so a subsequent sign-in re-fetches.
   */
  function reset(): void {
    effectiveRoles.value = undefined;
  }

  return {
    effectiveRoles,
    hydrateFromProfile,
    isInstanceAdmin,
    reset,
  };
}
