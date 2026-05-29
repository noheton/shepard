/**
 * UI21 — Unit tests for the pure helpers backing ContainerListPage.
 *
 * Pure-helper-pattern tests; no component mount. Mirrors
 * Trace3DChannelPicker.test.ts in shape.
 */
import { describe, it, expect } from "vitest";

import {
  type ContainerRow,
  type ContainerWithRefs,
  filterContainersByQuery,
  filterByColumn,
  sortContainers,
  groupByCollection,
  sizeBarFraction,
  partitionOrphans,
  urlParamsFromState,
  stateFromUrlParams,
} from "../../utils/containerListPage";
import {
  describeContainerType,
  iconForContainerType,
  colorForContainerType,
  labelForContainerType,
  urlSegmentForContainerType,
  supportsReferenceCheck,
  allContainerTypeDescriptors,
} from "../../utils/containerTypeRegistry";

// ── Fixtures ────────────────────────────────────────────────────────────────

const ROWS: ContainerRow[] = [
  {
    id: 1, name: "AFP-Tapelaying-Run1", type: "TIMESERIES",
    createdBy: "alice", createdAt: new Date("2026-05-01T10:00:00Z"),
    updatedAt: new Date("2026-05-10T09:00:00Z"),
  },
  {
    id: 2, name: "NDT-scans-ply5", type: "FILE",
    createdBy: "bob", createdAt: new Date("2026-05-02T10:00:00Z"),
    updatedAt: new Date("2026-05-09T09:00:00Z"),
  },
  {
    id: 3, name: "BridgeWelding-Frame4", type: "TIMESERIES",
    createdBy: "alice", createdAt: new Date("2026-05-03T10:00:00Z"),
    updatedAt: new Date("2026-05-08T09:00:00Z"),
  },
  {
    id: 4, name: "Report-Q1-anomaly", type: "STRUCTUREDDATA",
    createdBy: "carol", createdAt: new Date("2026-05-04T10:00:00Z"),
    updatedAt: null,
  },
];

// ── filterContainersByQuery ─────────────────────────────────────────────────

describe("filterContainersByQuery", () => {
  it("returns all when query is empty", () => {
    expect(filterContainersByQuery(ROWS, "")).toHaveLength(4);
    expect(filterContainersByQuery(ROWS, undefined)).toHaveLength(4);
    expect(filterContainersByQuery(ROWS, "   ")).toHaveLength(4);
  });

  it("matches on name (case-insensitive substring)", () => {
    const out = filterContainersByQuery(ROWS, "afp");
    expect(out.map(c => c.id)).toEqual([1]);
  });

  it("matches on type", () => {
    const out = filterContainersByQuery(ROWS, "timeseries");
    expect(out.map(c => c.id).sort()).toEqual([1, 3]);
  });

  it("matches on createdBy", () => {
    const out = filterContainersByQuery(ROWS, "alice");
    expect(out.map(c => c.id).sort()).toEqual([1, 3]);
  });

  it("matches on id as string", () => {
    // "4" deliberately also appears in row 3's name (`…-Frame4`) — the
    // expected behaviour is that the query is a substring across all
    // searchable columns, not an id-only match.
    const out = filterContainersByQuery(ROWS, "4");
    expect(out.map(c => c.id).sort()).toEqual([3, 4]);
  });

  it("matches on id-only when the query is unambiguous", () => {
    const out = filterContainersByQuery(ROWS, "2");
    expect(out.map(c => c.id)).toEqual([2]);
  });

  it("returns an empty array (not the original) when nothing matches", () => {
    const out = filterContainersByQuery(ROWS, "no-such-thing-zzz");
    expect(out).toEqual([]);
  });
});

// ── filterByColumn ──────────────────────────────────────────────────────────

describe("filterByColumn", () => {
  it("filters by createdBy", () => {
    const out = filterByColumn(ROWS, "createdBy", "carol");
    expect(out.map(c => c.id)).toEqual([4]);
  });

  it("filters by type", () => {
    const out = filterByColumn(ROWS, "type", "structureddata");
    expect(out.map(c => c.id)).toEqual([4]);
  });

  it("handles null updatedAt", () => {
    const out = filterByColumn(ROWS, "updatedAt", "2026");
    expect(out.map(c => c.id).sort()).toEqual([1, 2, 3]);
  });

  it("returns input copy when needle is empty", () => {
    const out = filterByColumn(ROWS, "name", "");
    expect(out).toHaveLength(4);
    expect(out).not.toBe(ROWS); // copy, not same array
  });
});

// ── sortContainers ──────────────────────────────────────────────────────────

describe("sortContainers", () => {
  it("sorts by name ascending", () => {
    const out = sortContainers(ROWS, "name", "asc");
    expect(out.map(c => c.id)).toEqual([1, 3, 2, 4]);
  });

  it("sorts by name descending", () => {
    const out = sortContainers(ROWS, "name", "desc");
    expect(out.map(c => c.id)).toEqual([4, 2, 3, 1]);
  });

  it("sorts by createdAt ascending", () => {
    const out = sortContainers(ROWS, "createdAt", "asc");
    expect(out.map(c => c.id)).toEqual([1, 2, 3, 4]);
  });

  it("sorts nulls last", () => {
    const out = sortContainers(ROWS, "updatedAt", "asc");
    expect(out[out.length - 1]!.id).toBe(4); // updatedAt: null lands last
  });

  it("does not mutate the input array", () => {
    const ids = ROWS.map(r => r.id);
    sortContainers(ROWS, "name", "asc");
    expect(ROWS.map(r => r.id)).toEqual(ids);
  });
});

// ── groupByCollection ───────────────────────────────────────────────────────

describe("groupByCollection", () => {
  function withRefs(refs: (number[] | null)[]): ContainerWithRefs[] {
    return ROWS.map((r, i) => ({ container: r, referencingCollectionIds: refs[i] ?? null }));
  }

  it("groups orphans into the synthetic key", () => {
    const out = groupByCollection(withRefs([[], [], [], []]));
    expect(out.get("__orphans__")?.length).toBe(4);
  });

  it("groups unknowns into the synthetic key", () => {
    const out = groupByCollection(withRefs([null, null, null, null]));
    expect(out.get("__unknown__")?.length).toBe(4);
  });

  it("a row referenced by multiple collections appears in all of them", () => {
    const out = groupByCollection(withRefs([[10, 20], [10], [], [30]]));
    expect(out.get("10")?.map(c => c.id).sort()).toEqual([1, 2]);
    expect(out.get("20")?.map(c => c.id)).toEqual([1]);
    expect(out.get("__orphans__")?.map(c => c.id)).toEqual([3]);
    expect(out.get("30")?.map(c => c.id)).toEqual([4]);
  });
});

// ── sizeBarFraction ─────────────────────────────────────────────────────────

describe("sizeBarFraction", () => {
  it("scales linearly", () => {
    expect(sizeBarFraction(5, 10)).toBe(0.5);
    expect(sizeBarFraction(10, 10)).toBe(1);
    expect(sizeBarFraction(0, 10)).toBe(0);
  });

  it("clamps to [0,1]", () => {
    expect(sizeBarFraction(20, 10)).toBe(1);
    expect(sizeBarFraction(-5, 10)).toBe(0);
  });

  it("returns 0 when max is non-positive or NaN", () => {
    expect(sizeBarFraction(5, 0)).toBe(0);
    expect(sizeBarFraction(5, Number.NaN)).toBe(0);
    expect(sizeBarFraction(Number.NaN, 10)).toBe(0);
  });
});

// ── partitionOrphans ────────────────────────────────────────────────────────

describe("partitionOrphans", () => {
  it("classifies ids by their reference state", () => {
    const refs = new Map<number, number[] | null>([
      [1, []],          // orphan, deletable
      [2, [99]],        // referenced, blocked
      [3, null],        // unknown
      [4, []],          // orphan, deletable
    ]);
    const out = partitionOrphans([1, 2, 3, 4], refs);
    expect(out.deletable.sort()).toEqual([1, 4]);
    expect(out.blockedByReferences).toEqual([2]);
    expect(out.unknownReferenceState).toEqual([3]);
  });

  it("ids not present in the refs map land in unknownReferenceState", () => {
    const out = partitionOrphans([99], new Map());
    expect(out.unknownReferenceState).toEqual([99]);
    expect(out.deletable).toEqual([]);
  });

  it("an empty selection yields empty partitions", () => {
    const out = partitionOrphans([], new Map());
    expect(out.deletable).toEqual([]);
    expect(out.blockedByReferences).toEqual([]);
    expect(out.unknownReferenceState).toEqual([]);
  });
});

// ── urlParamsFromState / stateFromUrlParams ────────────────────────────────

describe("urlParamsFromState", () => {
  it("omits blank fields", () => {
    expect(urlParamsFromState({})).toEqual({});
    expect(urlParamsFromState({ q: "  " })).toEqual({});
    expect(urlParamsFromState({ owner: "" })).toEqual({});
  });

  it("upper-cases the type and rejects unknowns", () => {
    expect(urlParamsFromState({ type: "timeseries" }).type).toBe("TIMESERIES");
    expect(urlParamsFromState({ type: "FOO" }).type).toBeUndefined();
  });

  it("omits page=1 (default) but preserves higher pages", () => {
    expect(urlParamsFromState({ page: 1 }).page).toBeUndefined();
    expect(urlParamsFromState({ page: 3 }).page).toBe("3");
  });

  it("emits the group=collection sentinel only", () => {
    expect(urlParamsFromState({ group: "collection" }).group).toBe("collection");
    expect(urlParamsFromState({ group: "flat" }).group).toBeUndefined();
  });
});

describe("stateFromUrlParams", () => {
  it("round-trips a typical share URL", () => {
    const state = stateFromUrlParams({
      q: "alice", type: "FILE", owner: "alice", page: "3", group: "collection",
    });
    expect(state).toEqual({
      q: "alice", type: "FILE", owner: "alice", page: 3, group: "collection",
    });
  });

  it("rejects malformed values", () => {
    const state = stateFromUrlParams({
      type: "WAT", page: "abc", group: "purple",
    });
    expect(state.type).toBeUndefined();
    expect(state.page).toBeUndefined();
    expect(state.group).toBeUndefined();
  });

  it("handles array-shaped query params (first element wins)", () => {
    const state = stateFromUrlParams({ q: ["alice", "bob"] });
    expect(state.q).toBe("alice");
  });
});

// ── containerTypeRegistry (bundled coverage) ───────────────────────────────

describe("containerTypeRegistry", () => {
  it("returns a descriptor for every known kind", () => {
    expect(describeContainerType("TIMESERIES").label).toBe("Timeseries");
    expect(describeContainerType("FILE").icon).toBe("mdi-folder-outline");
    expect(describeContainerType("STRUCTUREDDATA").urlSegment).toBe("structureddata/");
    expect(describeContainerType("SPATIALDATA").color).toBe("success");
  });

  it("falls back gracefully for unknown / null inputs", () => {
    expect(describeContainerType(undefined).icon).toBe("mdi-database-outline");
    expect(describeContainerType(null).label).toBe("Container");
    expect(describeContainerType("WHATEVER").color).toBe("textbody2");
  });

  it("convenience accessors agree with describe()", () => {
    expect(iconForContainerType("TIMESERIES")).toBe("mdi-chart-line");
    expect(colorForContainerType("FILE")).toBe("info");
    expect(labelForContainerType("STRUCTUREDDATA")).toBe("Structured Data");
    expect(urlSegmentForContainerType("SPATIALDATA")).toBe("spatialdata/");
  });

  it("flags reference-check support per the CC1b endpoints", () => {
    expect(supportsReferenceCheck("TIMESERIES")).toBe(true);
    expect(supportsReferenceCheck("FILE")).toBe(true);
    expect(supportsReferenceCheck("STRUCTUREDDATA")).toBe(true);
    expect(supportsReferenceCheck("SPATIALDATA")).toBe(false);
    expect(supportsReferenceCheck("BASIC")).toBe(false);
  });

  it("exposes the full set for dropdowns", () => {
    const all = allContainerTypeDescriptors();
    expect(all.length).toBeGreaterThanOrEqual(5);
    const keys = all.map(d => d.key);
    expect(keys).toContain("TIMESERIES");
    expect(keys).toContain("FILE");
  });
});
