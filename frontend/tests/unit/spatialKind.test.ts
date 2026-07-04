/**
 * spatialKind tests — pure-logic classifier shared by the
 * DataObjectSpatialContainersPane and the spatial container detail page.
 *
 * MFFD W7 / GAP-5.
 */
import { describe, it, expect } from "vitest";
import {
  inferSpatialKindFromName,
  SPATIAL_KIND_ICONS,
  SPATIAL_KIND_LABELS,
} from "../../utils/spatialKind";

describe("inferSpatialKindFromName", () => {
  it("classifies TPS 3D pointclouds as profile", () => {
    expect(inferSpatialKindFromName("Track_244__Run_30239_/TPS 3D pointclouds.0"))
      .toBe("profile");
    expect(inferSpatialKindFromName("TPS 3D pointclouds.1")).toBe("profile");
  });

  it("classifies FSD course 3D pointclouds as trajectory", () => {
    expect(inferSpatialKindFromName("Track_244__Run_30239_/FSD course 3D pointclouds"))
      .toBe("trajectory");
  });

  it("classifies TPS raw data as brush-trace", () => {
    expect(inferSpatialKindFromName("Track_244__Run_30239_/TPS raw data.5"))
      .toBe("brush-trace");
  });

  it("falls back to other for unrecognised names", () => {
    expect(inferSpatialKindFromName("misc.csv")).toBe("other");
    expect(inferSpatialKindFromName("")).toBe("other");
    expect(inferSpatialKindFromName(null)).toBe("other");
    expect(inferSpatialKindFromName(undefined)).toBe("other");
  });

  it("is case-insensitive", () => {
    expect(inferSpatialKindFromName("TPS 3D POINTCLOUDS.0")).toBe("profile");
    expect(inferSpatialKindFromName("fsd COURSE 3d pointclouds")).toBe("trajectory");
  });
});

describe("SPATIAL_KIND_LABELS + ICONS", () => {
  it("has a label and an icon for every kind", () => {
    const kinds = ["profile", "trajectory", "brush-trace", "other"] as const;
    for (const k of kinds) {
      expect(SPATIAL_KIND_LABELS[k]).toBeDefined();
      expect(SPATIAL_KIND_LABELS[k].length).toBeGreaterThan(0);
      expect(SPATIAL_KIND_ICONS[k]).toMatch(/^mdi-/);
    }
  });
});
