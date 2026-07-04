/**
 * ROLE-GRANT-STALE-SESSION-02 — unit tests for the pure logic of:
 *
 *   - `classifyRoleChangedBody(body)` in `useStaleRoleSession.ts` (the body
 *     classifier consumed by the auth-refresh middleware).
 *   - The computed `hintEnabled` / `hintTitle` / `hintBody` derivations in
 *     `UnauthorizedView.vue` (mirrors the in-component shape).
 *
 * Vitest config is `environment: "node"`, so we test the pure logic, not the
 * mounted Vuetify tree. Pattern follows
 * `tests/unit/UnauthorizedView.staleSessionHint.test.ts`.
 */
import { describe, expect, it } from "vitest";

import { classifyRoleChangedBody } from "~/composables/context/useStaleRoleSession";

describe("classifyRoleChangedBody — backend ApiError shape", () => {
  it("classifies the canonical Lombok @Value Jackson shape (`exception`)", () => {
    expect(
      classifyRoleChangedBody({
        status: 401,
        exception: "role_changed",
        message: "Your session was issued before a role change.",
      }),
    ).toBe("role-changed");
  });

  it("classifies the RFC-style `error` shape as well (forward compat)", () => {
    expect(
      classifyRoleChangedBody({
        error: "role_changed",
        message: "Token predates role change.",
      }),
    ).toBe("role-changed");
  });

  it("returns null for generic 401 bodies (no role_changed code)", () => {
    expect(classifyRoleChangedBody({ exception: "AuthenticationException" })).toBeNull();
    expect(classifyRoleChangedBody({ error: "invalid_token" })).toBeNull();
  });

  it("returns null for non-object inputs", () => {
    expect(classifyRoleChangedBody(null)).toBeNull();
    expect(classifyRoleChangedBody("role_changed")).toBeNull();
    expect(classifyRoleChangedBody(42)).toBeNull();
    expect(classifyRoleChangedBody(undefined)).toBeNull();
  });

  it("returns null for an empty body", () => {
    expect(classifyRoleChangedBody({})).toBeNull();
  });
});

// ---- UnauthorizedView hint copy derivations (mirrors the in-component logic) ----

type Reason = "role-changed" | undefined;

function computeHintEnabled(
  showStaleSessionHint: boolean | undefined,
  requiredRole: string | undefined,
  staleSessionReason: Reason,
): boolean {
  return showStaleSessionHint
    ?? (Boolean(requiredRole) || Boolean(staleSessionReason));
}

function computeHintTitle(reason: Reason): string {
  return reason === "role-changed"
    ? "Your role just changed"
    : "Did you just get the grant?";
}

function computeHintBody(reason: Reason): string {
  return reason === "role-changed"
    ? "An admin updated your roles. Your active session was issued before that change. Sign out and back in to continue."
    : "Your active session caches the role set from your last sign-in. Sign out and back in to refresh.";
}

describe("UnauthorizedView — stale-role-session hint upgrade (-02)", () => {
  it("forces hint on whenever a definitive role_changed reason is set", () => {
    // Even without `requiredRole`, the definitive signal forces the hint.
    expect(computeHintEnabled(undefined, undefined, "role-changed")).toBe(true);
  });

  it("still surfaces the hint on admin routes without a definitive reason (-03 fallback)", () => {
    expect(computeHintEnabled(undefined, "instance-admin", undefined)).toBe(true);
  });

  it("respects an explicit `showStaleSessionHint=false` even with a role_changed reason", () => {
    expect(computeHintEnabled(false, "instance-admin", "role-changed")).toBe(false);
  });

  it("upgrades the hint title from speculative to definitive on role-changed", () => {
    expect(computeHintTitle(undefined)).toBe("Did you just get the grant?");
    expect(computeHintTitle("role-changed")).toBe("Your role just changed");
  });

  it("upgrades the hint body copy on role-changed", () => {
    expect(computeHintBody(undefined)).toMatch(/active session caches/i);
    expect(computeHintBody("role-changed")).toMatch(/admin updated your roles/i);
    // Both wordings still guide the user to the same action.
    expect(computeHintBody(undefined)).toMatch(/sign out and back in/i);
    expect(computeHintBody("role-changed")).toMatch(/sign out and back in/i);
  });
});
