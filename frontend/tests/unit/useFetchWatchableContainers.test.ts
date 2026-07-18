import { describe, it, expect } from "vitest";
import {
  mapWatchableContainerOptions,
  WATCH_KIND_TO_WIRE,
  WATCHABLE_CONTAINER_SEARCH_DEBOUNCE_MS,
  type WatchableContainerOption,
} from "~/composables/context/useFetchWatchableContainers";

describe("WATCH_KIND_TO_WIRE", () => {
  it("maps each WatchedContainerKind to the /v2/containers wire kind token", () => {
    // These tokens are the ContainerKindHandler.kind() values on the backend —
    // a mismatch here would send the picker an unknown `kind` (400 → empty).
    expect(WATCH_KIND_TO_WIRE.TIMESERIES).toBe("timeseries");
    expect(WATCH_KIND_TO_WIRE.FILE).toBe("file");
    expect(WATCH_KIND_TO_WIRE.STRUCTURED_DATA).toBe("structured-data");
  });
});

describe("mapWatchableContainerOptions", () => {
  it("maps raw container items to { appId, name } picker options", () => {
    const opts = mapWatchableContainerOptions([
      { appId: "c-1", name: "Bench A" },
      { appId: "c-2", name: "Bench B" },
    ]);
    expect(opts).toEqual<WatchableContainerOption[]>([
      { appId: "c-1", name: "Bench A" },
      { appId: "c-2", name: "Bench B" },
    ]);
  });

  it("orders options in natural (numeric-aware) order, not lexicographic", () => {
    const opts = mapWatchableContainerOptions([
      { appId: "c-10", name: "Bench 10" },
      { appId: "c-2", name: "Bench 2" },
      { appId: "c-1", name: "Bench 1" },
    ]);
    // Lexicographic order would give 1, 10, 2; natural order gives 1, 2, 10.
    expect(opts.map((o) => o.name)).toEqual(["Bench 1", "Bench 2", "Bench 10"]);
  });

  it("is case-insensitive in ordering", () => {
    const opts = mapWatchableContainerOptions([
      { appId: "c-b", name: "beta" },
      { appId: "c-A", name: "Alpha" },
    ]);
    expect(opts.map((o) => o.name)).toEqual(["Alpha", "beta"]);
  });

  it("skips items without an appId (cannot be addressed by the watch API)", () => {
    const opts = mapWatchableContainerOptions([
      { appId: undefined, name: "legacy pre-appId container" },
      { appId: null, name: "also legacy" },
      { appId: "c-1", name: "modern" },
    ]);
    expect(opts).toEqual<WatchableContainerOption[]>([{ appId: "c-1", name: "modern" }]);
  });

  it("falls back to the appId as the display name when name is absent", () => {
    const opts = mapWatchableContainerOptions([{ appId: "c-1", name: null }]);
    expect(opts).toEqual<WatchableContainerOption[]>([{ appId: "c-1", name: "c-1" }]);
  });

  it("returns an empty array for empty input (fail-soft)", () => {
    expect(mapWatchableContainerOptions([])).toEqual([]);
  });

  it("exposes a sane debounce window for search-as-you-type", () => {
    expect(WATCHABLE_CONTAINER_SEARCH_DEBOUNCE_MS).toBeGreaterThan(0);
    expect(WATCHABLE_CONTAINER_SEARCH_DEBOUNCE_MS).toBeLessThanOrEqual(1000);
  });
});
