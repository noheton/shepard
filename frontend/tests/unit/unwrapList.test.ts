import { describe, it, expect } from "vitest";
import { unwrapList } from "~/utils/unwrapList";

describe("unwrapList", () => {
  it("extracts .items from a paged envelope", () => {
    expect(unwrapList({ items: [1, 2], total: 2, page: 0, pageSize: 50 })).toEqual([1, 2]);
  });

  it("returns a real empty array for an empty envelope (the crash case)", () => {
    const out = unwrapList({ items: [], total: 0, page: 0, pageSize: 50 });
    expect(Array.isArray(out)).toBe(true);
    expect(out.length).toBe(0);
  });

  it("passes a bare array through unchanged (older/unpaged endpoint)", () => {
    expect(unwrapList([{ a: 1 }])).toEqual([{ a: 1 }]);
  });

  it("returns [] for non-conforming shapes", () => {
    expect(unwrapList(null)).toEqual([]);
    expect(unwrapList(undefined)).toEqual([]);
    expect(unwrapList({})).toEqual([]);
    expect(unwrapList({ items: "nope" })).toEqual([]);
    expect(unwrapList(42)).toEqual([]);
  });
});
