/**
 * FE-ROLE-DUAL-SOURCE-1 — proves `useEffectiveRoles()`:
 *   1. starts in undefined ("loading") state so the UI doesn't flash
 *      unauthorized while the profile fetch is in flight;
 *   2. populates from `profile.effectiveRoles` when the backend supplies
 *      it (BE-USERS-ME-ROLES-1, planned);
 *   3. falls back to the JWT `realm_access.roles` claim when the backend
 *      response carries no `effectiveRoles` field (today's behaviour);
 *   4. resets on sign-out so a subsequent sign-in re-fetches.
 *
 * Tests the pure logic units, mirroring the pattern in
 * `DataObjectNotebooksPane.test.ts`.
 */
import { describe, it, expect, beforeEach, vi } from "vitest";
import {
  useEffectiveRoles,
  rolesFromAccessToken,
  INSTANCE_ADMIN_ROLE,
} from "~/composables/context/useEffectiveRoles";

// `useState` is a Nuxt auto-import. Vitest hoists `vi.stubGlobal` to
// pre-import (same hoisting as `vi.mock`), so the SUT's call sees it.
const _state = new Map<string, unknown>();
vi.stubGlobal(
  "useState",
  <T,>(key: string, init: () => T): { value: T } => {
    if (!_state.has(key)) _state.set(key, init());
    return {
      get value() {
        return _state.get(key) as T;
      },
      set value(v: T) {
        _state.set(key, v);
      },
    };
  },
);

/** Mint a JWT-shaped string carrying the given realm_access.roles. */
function jwt(roles: string[]): string {
  const header = btoa(JSON.stringify({ alg: "none" }));
  const payload = btoa(JSON.stringify({ realm_access: { roles } }));
  return `${header}.${payload}.sig`;
}

beforeEach(() => {
  _state.clear();
});

describe("rolesFromAccessToken — FE-ROLE-DUAL-SOURCE-1", () => {
  it("returns [] for nullish token", () => {
    expect(rolesFromAccessToken(null)).toEqual([]);
    expect(rolesFromAccessToken(undefined)).toEqual([]);
    expect(rolesFromAccessToken("")).toEqual([]);
  });

  it("returns the realm_access.roles array", () => {
    expect(rolesFromAccessToken(jwt(["instance-admin", "user"]))).toEqual([
      "instance-admin",
      "user",
    ]);
  });

  it("returns [] when JWT carries no realm_access.roles", () => {
    expect(rolesFromAccessToken(jwt([]))).toEqual([]);
  });
});

describe("useEffectiveRoles — loading state", () => {
  it("starts undefined (don't flash unauthorized)", () => {
    const { effectiveRoles, isInstanceAdmin } = useEffectiveRoles();
    expect(effectiveRoles.value).toBeUndefined();
    expect(isInstanceAdmin()).toBeUndefined();
  });
});

describe("useEffectiveRoles — hydrateFromProfile", () => {
  it("uses profile.effectiveRoles when present (BE-USERS-ME-ROLES-1)", () => {
    const { hydrateFromProfile, effectiveRoles, isInstanceAdmin } =
      useEffectiveRoles();
    hydrateFromProfile(
      { effectiveRoles: [INSTANCE_ADMIN_ROLE, "user"] },
      jwt([]),
    );
    expect(effectiveRoles.value).toEqual([INSTANCE_ADMIN_ROLE, "user"]);
    expect(isInstanceAdmin()).toBe(true);
  });

  it("falls back to JWT roles when profile lacks effectiveRoles", () => {
    const { hydrateFromProfile, effectiveRoles, isInstanceAdmin } =
      useEffectiveRoles();
    hydrateFromProfile({}, jwt([INSTANCE_ADMIN_ROLE]));
    expect(effectiveRoles.value).toEqual([INSTANCE_ADMIN_ROLE]);
    expect(isInstanceAdmin()).toBe(true);
  });

  it("falls back to JWT when profile is null (fetch failed)", () => {
    const { hydrateFromProfile, effectiveRoles, isInstanceAdmin } =
      useEffectiveRoles();
    hydrateFromProfile(null, jwt(["user"]));
    expect(effectiveRoles.value).toEqual(["user"]);
    expect(isInstanceAdmin()).toBe(false);
  });

  it("hydrates to [] (not undefined) when both sources empty — leaves loading", () => {
    const { hydrateFromProfile, effectiveRoles, isInstanceAdmin } =
      useEffectiveRoles();
    hydrateFromProfile({}, null);
    expect(effectiveRoles.value).toEqual([]);
    expect(isInstanceAdmin()).toBe(false);
  });

  it("prefers profile.effectiveRoles over JWT (Cypher-path grant wins)", () => {
    // The whole point of FE-ROLE-DUAL-SOURCE-1: a Neo4j :HAS_ROLE grant
    // gives backend admin but NOT a fresh JWT. Profile-derived MUST win.
    const { hydrateFromProfile, isInstanceAdmin } = useEffectiveRoles();
    hydrateFromProfile({ effectiveRoles: [INSTANCE_ADMIN_ROLE] }, jwt([]));
    expect(isInstanceAdmin()).toBe(true);
  });
});

describe("useEffectiveRoles — reset", () => {
  it("returns to undefined ('loading') so the next sign-in re-fetches", () => {
    const { hydrateFromProfile, reset, effectiveRoles } = useEffectiveRoles();
    hydrateFromProfile({ effectiveRoles: ["user"] }, null);
    expect(effectiveRoles.value).toEqual(["user"]);
    reset();
    expect(effectiveRoles.value).toBeUndefined();
  });
});

describe("useEffectiveRoles — shared global state", () => {
  it("two callers see the same value (Nuxt useState key)", () => {
    const a = useEffectiveRoles();
    const b = useEffectiveRoles();
    a.hydrateFromProfile({ effectiveRoles: [INSTANCE_ADMIN_ROLE] }, null);
    expect(b.effectiveRoles.value).toEqual([INSTANCE_ADMIN_ROLE]);
    expect(b.isInstanceAdmin()).toBe(true);
  });
});
