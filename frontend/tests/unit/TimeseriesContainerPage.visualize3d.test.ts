/**
 * UX-WALK-2026-05-29-07 — unit tests for the "Visualize in 3D" CTA on the
 * Timeseries container detail page.
 *
 * These tests cover the pure conditional-render logic that governs whether the
 * ViewRecipeBuilderDialog is mounted and whether the "Visualize in 3D" button
 * is visible. They do not mount the full Nuxt/Vuetify component tree; the fetch
 * calls and Vuetify rendering are left to the Playwright e2e suite.
 *
 * Pattern: same helper-extraction approach used in VideoContainerPage.test.ts
 * and NotFoundPanel.test.ts.
 */
import { describe, it, expect } from "vitest";

// ---------------------------------------------------------------------------
// Inline the conditional-render predicates from the template.
// These mirror the exact guards in
//   frontend/pages/containers/timeseries/[containerId]/index.vue

/**
 * Should the "Visualize in 3D" button render?
 *
 * Template guard: `v-if="containerAccessor.measurements.value.length > 0"`
 * (inside the `v-if="!editingChartView"` append-slot block).
 */
function shouldShowVisualize3DButton(
  measurementCount: number,
  editingChartView: boolean,
): boolean {
  return !editingChartView && measurementCount > 0;
}

/**
 * Should the ViewRecipeBuilderDialog be mounted?
 *
 * Template guard: `v-if="containerAccessor.measurements.value.length > 0"`
 */
function shouldMountDialog(measurementCount: number): boolean {
  return measurementCount > 0;
}

/**
 * Should the "Edit channels" button render?
 *
 * Template guard:
 *   `v-if="containerAccessor.isAllowedToEditData.value"`
 *   (inside the same `v-if="!editingChartView"` slot block).
 */
function shouldShowEditChannelsButton(
  isAllowedToEditData: boolean,
  editingChartView: boolean,
): boolean {
  return !editingChartView && isAllowedToEditData;
}

// ---------------------------------------------------------------------------

describe("Visualize in 3D button visibility", () => {
  it("shows the button when measurements are present and not in edit mode", () => {
    expect(shouldShowVisualize3DButton(5, false)).toBe(true);
  });

  it("hides the button when there are no measurements", () => {
    expect(shouldShowVisualize3DButton(0, false)).toBe(false);
  });

  it("hides the button when in channel-edit mode, even with measurements", () => {
    expect(shouldShowVisualize3DButton(5, true)).toBe(false);
  });

  it("hides the button when in edit mode AND no measurements", () => {
    expect(shouldShowVisualize3DButton(0, true)).toBe(false);
  });
});

describe("ViewRecipeBuilderDialog mount guard", () => {
  it("mounts the dialog when measurements are present", () => {
    expect(shouldMountDialog(1)).toBe(true);
  });

  it("mounts the dialog for many channels", () => {
    expect(shouldMountDialog(42)).toBe(true);
  });

  it("does NOT mount the dialog when there are no measurements", () => {
    expect(shouldMountDialog(0)).toBe(false);
  });
});

describe("Edit channels button is independent of Visualize in 3D", () => {
  it("can show Edit channels without measurements (admin with empty container)", () => {
    // Edit channels button depends on isAllowedToEditData, not measurementCount.
    expect(shouldShowEditChannelsButton(true, false)).toBe(true);
  });

  it("hides Edit channels for read-only users even with measurements present", () => {
    expect(shouldShowEditChannelsButton(false, false)).toBe(false);
  });

  it("both buttons are hidden in edit mode", () => {
    expect(shouldShowVisualize3DButton(5, true)).toBe(false);
    expect(shouldShowEditChannelsButton(true, true)).toBe(false);
  });
});
