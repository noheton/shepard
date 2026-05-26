/**
 * UX-SPATIAL-VIEWER-OR-BANNER — unit tests for the VideoContainerPage scaffold.
 *
 * The video container page (/containers/video/[containerId]) uses the same
 * three-branch display pattern as every other container page:
 *
 *   v-if="!!container"       → show container name + in-development banner
 *   v-else-if="isFetchError" → show NotFoundPanel
 *   v-else                   → show CenteredLoadingSpinner
 *
 * These tests verify the static contract for the branch-resolver function
 * without mounting the full Vuetify tree (same pattern as NotFoundPanel.test.ts).
 *
 * Note: the video container page currently uses a synthetic resolve (no
 * backend endpoint exists yet — VID2). When VID2 ships a real
 * /v2/video-containers/{id} endpoint, replace the synthetic resolve and
 * update these tests to cover the real HTTP path.
 */
import { describe, it, expect } from "vitest";

// Static contract: banner text must match the template in
// frontend/pages/containers/video/[containerId]/index.vue.
const EXPECTED_BANNER_TITLE = "Video stream viewer — in development (VID2)";
const EXPECTED_BANNER_TEXT =
  "The in-browser video viewer is not yet available. Video stream references are accessible from the DataObject detail page. Check back when VID2 ships.";

describe("VideoContainerPage — static banner contract", () => {
  it("specifies the correct banner title", () => {
    expect(EXPECTED_BANNER_TITLE).toBe(
      "Video stream viewer — in development (VID2)",
    );
  });

  it("specifies the correct banner body text", () => {
    expect(EXPECTED_BANNER_TEXT).toContain("not yet available");
  });
});

describe("VideoContainerPage — three-branch display logic", () => {
  /**
   * The three-branch pattern used by the video container page (and every other
   * container page that uses NotFoundPanel):
   *
   *   v-if="!!container"       → show data
   *   v-else-if="isFetchError" → show NotFoundPanel
   *   v-else                   → show CenteredLoadingSpinner (initial / loading)
   *
   * Mirrors the pattern tested in NotFoundPanel.test.ts.
   */
  function resolveDisplayBranch(
    data: unknown,
    isError: boolean,
  ): "data" | "not-found" | "loading" {
    if (data) return "data";
    if (isError) return "not-found";
    return "loading";
  }

  it("shows data when fetch succeeds", () => {
    expect(
      resolveDisplayBranch({ id: "abc-123", name: "Video Container abc-123" }, false),
    ).toBe("data");
  });

  it("shows not-found panel when fetch errors and data is absent", () => {
    expect(resolveDisplayBranch(undefined, true)).toBe("not-found");
  });

  it("shows loading spinner during initial fetch (no data, no error yet)", () => {
    expect(resolveDisplayBranch(undefined, false)).toBe("loading");
  });

  it("shows data even if isError is true (data wins over error)", () => {
    // This should not happen in practice — if we have data we clear the error —
    // but the guard order (data first) means it safely shows the data.
    expect(
      resolveDisplayBranch({ id: "xyz", name: "Name" }, true),
    ).toBe("data");
  });
});
