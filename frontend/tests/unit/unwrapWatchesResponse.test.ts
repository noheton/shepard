import { describe, it, expect } from "vitest";
import { unwrapWatchesResponse } from "~/composables/context/useWatchedContainers";

/**
 * WATCH-ENVELOPE-UNWRAP regression — the /watches endpoint returns a paged
 * envelope ({items,total,page,pageSize}); the composable must extract .items,
 * not assign the whole object (which made `watches` a non-array and crashed the
 * panel's v-for on Add-watch form open).
 */
describe("unwrapWatchesResponse", () => {
  const row = {
    watchAppId: "w1",
    containerKind: "TIMESERIES" as const,
    containerAppId: "c1",
  };

  it("extracts .items from a paged envelope", () => {
    const out = unwrapWatchesResponse({ items: [row], total: 1, page: 0, pageSize: 50 });
    expect(Array.isArray(out)).toBe(true);
    expect(out).toEqual([row]);
  });

  it("returns [] for an empty envelope (the crash case)", () => {
    const out = unwrapWatchesResponse({ items: [], total: 0, page: 0, pageSize: 50 });
    expect(out).toEqual([]);
    // Must be a real array so `.length` and v-for behave.
    expect(Array.isArray(out)).toBe(true);
    expect(out.length).toBe(0);
  });

  it("passes through a bare array (older backend)", () => {
    expect(unwrapWatchesResponse([row])).toEqual([row]);
  });

  it("returns [] for non-conforming shapes", () => {
    expect(unwrapWatchesResponse(null)).toEqual([]);
    expect(unwrapWatchesResponse(undefined)).toEqual([]);
    expect(unwrapWatchesResponse({})).toEqual([]);
    expect(unwrapWatchesResponse({ items: "nope" })).toEqual([]);
  });
});
