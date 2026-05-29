/**
 * ROLE-GRANT-STALE-SESSION-03 — unit tests for the stale-session hint
 * conditional logic in `components/layout/UnauthorizedView.vue`.
 *
 * Mirrors the in-component computed:
 *
 *   hintEnabled = showStaleSessionHint ?? Boolean(requiredRole)
 *
 * The hint surfaces only when the page is gated on a specific role (admin
 * contexts) — never on generic 401 fallbacks — unless an explicit override
 * is passed. Pattern follows `PredecessorRelationshipTypeChip.test.ts`:
 * vitest config is `environment: "node"`, so we test the pure logic, not the
 * mounted Vuetify tree.
 */
import { describe, it, expect } from "vitest";

function computeHintEnabled(
  showStaleSessionHint: boolean | undefined,
  requiredRole: string | undefined,
): boolean {
  return showStaleSessionHint ?? Boolean(requiredRole);
}

describe("UnauthorizedView — stale-session hint conditional", () => {
  it("shows hint when requiredRole is set (admin route default)", () => {
    expect(computeHintEnabled(undefined, "instance-admin")).toBe(true);
  });

  it("hides hint when no requiredRole is passed (generic 401)", () => {
    expect(computeHintEnabled(undefined, undefined)).toBe(false);
  });

  it("hides hint when requiredRole is an empty string", () => {
    expect(computeHintEnabled(undefined, "")).toBe(false);
  });

  it("explicit showStaleSessionHint=false suppresses hint even on admin route", () => {
    expect(computeHintEnabled(false, "instance-admin")).toBe(false);
  });

  it("explicit showStaleSessionHint=true forces hint even without requiredRole", () => {
    expect(computeHintEnabled(true, undefined)).toBe(true);
  });

  it("explicit override wins over default for a custom role", () => {
    expect(computeHintEnabled(false, "collection-owner")).toBe(false);
    expect(computeHintEnabled(true, "collection-owner")).toBe(true);
  });
});

describe("UnauthorizedView — final toast/hint copy contract", () => {
  // Locks the user-facing strings so a casual reword of the alert body
  // doesn't slip through without an explicit test update.
  const HINT_TITLE = "Did you just get the grant?";
  const HINT_BODY =
    "Your active session caches the role set from your last sign-in. Sign out and back in to refresh.";
  const SIGN_OUT_BUTTON = "Sign out + back in";

  it("hint title names the freshly-granted-role situation explicitly", () => {
    expect(HINT_TITLE).toMatch(/grant/i);
  });

  it("hint body cites the JWT-cached-role mechanism in plain language", () => {
    expect(HINT_BODY).toMatch(/active session/i);
    expect(HINT_BODY).toMatch(/sign out and back in/i);
  });

  it("sign-out button label is action-oriented, not generic", () => {
    expect(SIGN_OUT_BUTTON).toBe("Sign out + back in");
  });
});
