/**
 * MFFD-IMAGEBUNDLE-SCRUBBER-1 — tests for the frame-scrubber page-planning
 * helpers used by `ImageBundleViewer.vue`.
 */
import { describe, it, expect } from "vitest";
import {
  planPageForFrame,
  needsRefetch,
  frameLabel,
} from "~/utils/imageBundleScrubber";

describe("planPageForFrame", () => {
  it("returns page 0 / offset 0 for the first frame", () => {
    const plan = planPageForFrame(0, 38, 200);
    expect(plan.page).toBe(0);
    expect(plan.offsetInPage).toBe(0);
    expect(plan.pageSize).toBe(200);
  });

  it("computes a mid-page offset on the first page", () => {
    const plan = planPageForFrame(37, 38, 200);
    expect(plan.page).toBe(0);
    expect(plan.offsetInPage).toBe(37);
  });

  it("rolls onto page 2 at the boundary", () => {
    const plan = planPageForFrame(200, 1000, 200);
    expect(plan.page).toBe(1);
    expect(plan.offsetInPage).toBe(0);
  });

  it("clamps frameIndex to the last frame", () => {
    const plan = planPageForFrame(9999, 38, 200);
    expect(plan.page).toBe(0);
    expect(plan.offsetInPage).toBe(37);
  });

  it("clamps pageSize to the [1,1000] range", () => {
    expect(planPageForFrame(0, 10, 5000).pageSize).toBe(1000);
    expect(planPageForFrame(0, 10, 0).pageSize).toBe(1);
    expect(planPageForFrame(0, 10, -5).pageSize).toBe(1);
  });

  it("handles empty bundle (totalFrames=0)", () => {
    const plan = planPageForFrame(5, 0, 200);
    expect(plan.page).toBe(0);
    expect(plan.offsetInPage).toBe(0);
  });

  it("handles negative frameIndex", () => {
    const plan = planPageForFrame(-3, 38, 200);
    expect(plan.page).toBe(0);
    expect(plan.offsetInPage).toBe(0);
  });
});

describe("needsRefetch", () => {
  it("is true when nothing is cached", () => {
    expect(needsRefetch(null, { page: 0, offsetInPage: 0, pageSize: 200 })).toBe(
      true,
    );
  });

  it("is false when the cached page covers the desired frame", () => {
    expect(needsRefetch(2, { page: 2, offsetInPage: 5, pageSize: 200 })).toBe(
      false,
    );
  });

  it("is true when the desired page differs from the cached one", () => {
    expect(needsRefetch(0, { page: 3, offsetInPage: 5, pageSize: 200 })).toBe(
      true,
    );
  });
});

describe("frameLabel", () => {
  it("formats 1-based for accessibility", () => {
    expect(frameLabel(0, 38)).toBe("frame 1 of 38");
    expect(frameLabel(37, 38)).toBe("frame 38 of 38");
  });

  it("clamps an over-shot index", () => {
    expect(frameLabel(99, 38)).toBe("frame 38 of 38");
  });

  it("renders zero of zero when bundle is empty", () => {
    expect(frameLabel(0, 0)).toBe("frame 0 of 0");
  });
});
