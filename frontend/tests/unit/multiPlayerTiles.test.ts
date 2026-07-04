/**
 * MFFD-MULTIPLAYER-1 — tests for the tile-selection helper. Pins the
 * "hide when < 2 distinct payload kinds" rule and the default order.
 */
import { describe, it, expect } from "vitest";
import {
  DEFAULT_TILE_ORDER,
  selectMultiPlayerTiles,
} from "../../utils/multiPlayerTiles";

describe("selectMultiPlayerTiles", () => {
  const empty = {
    hasTimeseries: false,
    hasVideo: false,
    thermographyBundleAppId: null,
    hasSpatial: false,
  };

  it("returns an empty array when no payload kinds are present", () => {
    expect(selectMultiPlayerTiles(empty)).toEqual([]);
  });

  it("returns an empty array when only one payload kind is present (single-tile gate)", () => {
    expect(
      selectMultiPlayerTiles({ ...empty, hasTimeseries: true }),
    ).toEqual([]);
    expect(selectMultiPlayerTiles({ ...empty, hasVideo: true })).toEqual([]);
    expect(
      selectMultiPlayerTiles({ ...empty, thermographyBundleAppId: "abc" }),
    ).toEqual([]);
    expect(selectMultiPlayerTiles({ ...empty, hasSpatial: true })).toEqual([]);
  });

  it("returns both tiles when exactly two payload kinds are present", () => {
    expect(
      selectMultiPlayerTiles({
        ...empty,
        hasTimeseries: true,
        hasVideo: true,
      }),
    ).toEqual(["ts", "video"]);
  });

  it("preserves the canonical order (TS, video, thermo, spatial)", () => {
    expect(
      selectMultiPlayerTiles({
        hasTimeseries: true,
        hasVideo: true,
        thermographyBundleAppId: "bundle-1",
        hasSpatial: true,
      }),
    ).toEqual(["ts", "video", "thermo", "spatial"]);
  });

  it("skips kinds that are absent and keeps the rest in order", () => {
    expect(
      selectMultiPlayerTiles({
        hasTimeseries: true,
        hasVideo: false,
        thermographyBundleAppId: "bundle-1",
        hasSpatial: true,
      }),
    ).toEqual(["ts", "thermo", "spatial"]);
  });

  it("treats a null thermographyBundleAppId as absent thermography", () => {
    expect(
      selectMultiPlayerTiles({
        hasTimeseries: true,
        hasVideo: true,
        thermographyBundleAppId: null,
        hasSpatial: false,
      }),
    ).toEqual(["ts", "video"]);
  });

  it("exposes the canonical default order as a constant", () => {
    expect(DEFAULT_TILE_ORDER).toEqual(["ts", "video", "thermo", "spatial"]);
  });
});
