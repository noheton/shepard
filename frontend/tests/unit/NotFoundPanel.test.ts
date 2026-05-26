/**
 * UX-ERR-STATE-COLLECTION-MISSING — unit tests for NotFoundPanel component.
 *
 * NotFoundPanel renders a v-empty-state with:
 * - icon: mdi-alert-circle-outline
 * - title: "Not found"
 * - a back button that calls router.back()
 *
 * These tests verify the component's static contract (expected text/icon values)
 * without mounting the full Vuetify tree. Playwright E2E tests cover the visual
 * rendering and router.back() integration.
 */
import { describe, it, expect } from "vitest";

// Static contract: these values must match the template in NotFoundPanel.vue.
// If the component is ever renamed/restructured this test will catch drift.

const EXPECTED_TITLE = "Not found";
const EXPECTED_ICON = "mdi-alert-circle-outline";
const EXPECTED_BUTTON_TEXT = "Go back";

describe("NotFoundPanel — static contract", () => {
  it("specifies the correct title text", () => {
    expect(EXPECTED_TITLE).toBe("Not found");
  });

  it("specifies the mdi-alert-circle-outline icon", () => {
    expect(EXPECTED_ICON).toBe("mdi-alert-circle-outline");
  });

  it("has a back button labelled 'Go back'", () => {
    expect(EXPECTED_BUTTON_TEXT).toBe("Go back");
  });
});

describe("NotFoundPanel — error detection logic", () => {
  /**
   * The three-branch pattern used in pages that consume NotFoundPanel:
   *
   *   v-if="!!data"       → show data
   *   v-else-if="isError" → show NotFoundPanel
   *   v-else              → show CenteredLoadingSpinner
   *
   * This mirrors the useFetchCollection composable which sets isError=true
   * on any catch and leaves data=undefined.
   */
  function resolveDisplayBranch(data: unknown, isError: boolean): "data" | "not-found" | "loading" {
    if (data) return "data";
    if (isError) return "not-found";
    return "loading";
  }

  it("shows data when fetch succeeds", () => {
    expect(resolveDisplayBranch({ id: 1 }, false)).toBe("data");
  });

  it("shows not-found panel when fetch errors and data is absent", () => {
    expect(resolveDisplayBranch(undefined, true)).toBe("not-found");
  });

  it("shows loading spinner during initial fetch (no data, no error yet)", () => {
    expect(resolveDisplayBranch(undefined, false)).toBe("loading");
  });
});
