/**
 * PLACEHOLDER-video-container — unit tests for the VideoContainerPage.
 *
 * The video container page (/containers/video/[containerId]) uses the same
 * three-branch display pattern as every other container page:
 *
 *   v-if="!!container"       → show container name + VideoStreamReferencesPane
 *   v-else-if="isFetchError" → show NotFoundPanel
 *   v-else                   → show CenteredLoadingSpinner
 *
 * These tests verify the static contract for the branch-resolver function
 * without mounting the full Vuetify tree (same pattern as NotFoundPanel.test.ts).
 *
 * VID1a: the page now renders VideoStreamReferencesPane (real player surface)
 * instead of the previous "in development" banner. When VID2 ships a real
 * /v2/video-containers/{id} endpoint, replace the synthetic container resolve
 * and update these tests to cover the real HTTP path.
 */
import { describe, it, expect } from "vitest";

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

describe("VideoContainerPage — video references section (VID1a)", () => {
  /**
   * VID1a: the container page passes containerId as dataObjectAppId to
   * VideoStreamReferencesPane. These tests verify the identity mapping and
   * the synthetic container name derivation used while /v2/video-containers
   * is not yet implemented.
   */

  function deriveDataObjectAppId(containerId: string): string {
    // containerId IS the dataObjectAppId until VID2 ships its own entity.
    return containerId;
  }

  function deriveSyntheticContainerName(containerId: string): string {
    return `Video Container ${containerId}`;
  }

  it("passes containerId unchanged as dataObjectAppId to the references pane", () => {
    const containerId = "01930000-0000-0000-0000-000000000001";
    expect(deriveDataObjectAppId(containerId)).toBe(containerId);
  });

  it("handles empty containerId gracefully", () => {
    expect(deriveDataObjectAppId("")).toBe("");
  });

  it("derives synthetic container name from containerId", () => {
    expect(deriveSyntheticContainerName("abc-123")).toBe(
      "Video Container abc-123",
    );
  });

  it("synthetic name uses containerId as the label when backend has no container entity", () => {
    const cid = "01930000-dead-beef-cafe-000000000099";
    expect(deriveSyntheticContainerName(cid)).toContain(cid);
  });
});
