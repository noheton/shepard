import { describe, it, expect } from "vitest";
import {
  mapHdfContainerOptions,
  HDF_CONTAINER_WIRE_KIND,
  HDF_CONTAINER_SEARCH_DEBOUNCE_MS,
  type HdfContainerOption,
} from "~/composables/context/useFetchHdfContainerOptions";

describe("HDF_CONTAINER_WIRE_KIND", () => {
  it("is the HdfContainerKindHandler.kind() token on the backend", () => {
    // A mismatch here would send the picker an unknown `kind` (400 → empty list).
    expect(HDF_CONTAINER_WIRE_KIND).toBe("hdf");
  });
});

describe("mapHdfContainerOptions", () => {
  it("maps raw container items to { appId, name } picker options", () => {
    const opts = mapHdfContainerOptions([
      { appId: "c-1", name: "Vibration HDF" },
      { appId: "c-2", name: "Thermal HDF" },
    ]);
    expect(opts).toEqual<HdfContainerOption[]>([
      // naturally ordered: "Thermal" before "Vibration"
      { appId: "c-2", name: "Thermal HDF" },
      { appId: "c-1", name: "Vibration HDF" },
    ]);
  });

  it("orders options in natural (numeric-aware) order, not lexicographic", () => {
    const opts = mapHdfContainerOptions([
      { appId: "c-10", name: "Run 10" },
      { appId: "c-2", name: "Run 2" },
      { appId: "c-1", name: "Run 1" },
    ]);
    // Lexicographic order would give 1, 10, 2; natural order gives 1, 2, 10.
    expect(opts.map((o) => o.name)).toEqual(["Run 1", "Run 2", "Run 10"]);
  });

  it("is case-insensitive in ordering", () => {
    const opts = mapHdfContainerOptions([
      { appId: "c-b", name: "beta" },
      { appId: "c-A", name: "Alpha" },
    ]);
    expect(opts.map((o) => o.name)).toEqual(["Alpha", "beta"]);
  });

  it("skips items without an appId (cannot be addressed by the hdf-reference API)", () => {
    const opts = mapHdfContainerOptions([
      { appId: undefined, name: "legacy pre-appId container" },
      { appId: null, name: "also legacy" },
      { appId: "c-1", name: "modern" },
    ]);
    expect(opts).toEqual<HdfContainerOption[]>([{ appId: "c-1", name: "modern" }]);
  });

  it("falls back to the appId as the display name when name is absent", () => {
    const opts = mapHdfContainerOptions([{ appId: "c-1", name: null }]);
    expect(opts).toEqual<HdfContainerOption[]>([{ appId: "c-1", name: "c-1" }]);
  });

  it("returns an empty array for empty input (fail-soft)", () => {
    expect(mapHdfContainerOptions([])).toEqual([]);
  });

  it("exposes a sane debounce window for search-as-you-type", () => {
    expect(HDF_CONTAINER_SEARCH_DEBOUNCE_MS).toBeGreaterThan(0);
    expect(HDF_CONTAINER_SEARCH_DEBOUNCE_MS).toBeLessThanOrEqual(1000);
  });
});
