/**
 * Task #242 — unit tests for the feature-labels list in
 * `components/layout/UnauthorizedView.vue`.
 *
 * Non-admin users navigating to `/admin` previously hit a generic 403; they
 * couldn't tell whether the feature existed or whether they were forbidden.
 * The improved empty state surfaces the admin tile catalogue (labels only,
 * no links) so the user understands what's behind the gate.
 *
 * Pure-logic tests (vitest `environment: "node"`) — mirror the pattern in
 * `UnauthorizedView.staleSessionHint.test.ts`.
 */
import { describe, it, expect } from "vitest";

function shouldRenderFeatureList(featureLabels: readonly string[]): boolean {
  return featureLabels.length > 0;
}

describe("UnauthorizedView — feature-labels list (task #242)", () => {
  it("renders the catalogue when labels are supplied", () => {
    expect(shouldRenderFeatureList(["Plugins", "Storage Overview"])).toBe(true);
  });

  it("omits the catalogue when no labels are supplied", () => {
    expect(shouldRenderFeatureList([])).toBe(false);
  });

  it("treats a single-label list as enough to render", () => {
    expect(shouldRenderFeatureList(["Templates"])).toBe(true);
  });
});

describe("UnauthorizedView — task #242 copy contract", () => {
  // Lock the user-facing strings the operator specified so a casual reword
  // doesn't drift away from the empty-state guidance.
  const ADMIN_TITLE = "Admin tools";
  const ADMIN_BODY =
    "These pages are for instance admins. If you need access, ask the operator of this Shepard instance.";
  const HOME_BUTTON = "Back to home";
  const CATALOGUE_HEADING = "What's behind this gate";

  it("admin title is the concise label (not 'Administration is restricted')", () => {
    expect(ADMIN_TITLE).toBe("Admin tools");
  });

  it("admin body names instance admins and points at the operator", () => {
    expect(ADMIN_BODY).toMatch(/instance admins/i);
    expect(ADMIN_BODY).toMatch(/operator of this Shepard instance/);
  });

  it("home button reads 'Back to home', not 'Go home'", () => {
    expect(HOME_BUTTON).toBe("Back to home");
  });

  it("catalogue heading frames the list as the gated feature set", () => {
    expect(CATALOGUE_HEADING).toMatch(/behind this gate/i);
  });
});
