/**
 * UX-WALK-2026-05-29-05 — Unit tests for `useCollectionListDensity`.
 *
 * Tests exercise:
 *   - default_isComfortable        — fresh state returns "comfortable"
 *   - setDensity_updatesRef        — setDensity() mutates the reactive ref
 *   - setDensity_persistsToStorage — setDensity() writes to localStorage
 *   - loads_from_localStorage      — pre-seeded value is restored on module load
 *   - ignores_invalid_stored_value — corrupted storage falls back to default
 *   - cyclesAllValues              — all three values can be set and read back
 *
 * The module-scope singleton is reset before each test by clearing the
 * localStorage stub and calling `vi.resetModules()` — the same pattern as
 * `usePinnedChannels.test.ts`.
 */

import { describe, it, expect, beforeEach, vi } from "vitest";

// ── localStorage mock ─────────────────────────────────────────────────────────

const storage = new Map<string, string>();

const localStorageMock = {
  getItem: vi.fn((key: string) => storage.get(key) ?? null),
  setItem: vi.fn((key: string, value: string) => { storage.set(key, value); }),
  removeItem: vi.fn((key: string) => { storage.delete(key); }),
  clear: vi.fn(() => { storage.clear(); }),
};

vi.stubGlobal("localStorage", localStorageMock);

// ── Helpers ───────────────────────────────────────────────────────────────────

const STORAGE_KEY = "shepard:collections-list-density";

async function freshComposable() {
  vi.resetModules();
  const mod = await import("~/composables/context/useCollectionListDensity");
  return mod.useCollectionListDensity();
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe("useCollectionListDensity", () => {
  beforeEach(() => {
    localStorageMock.clear();
    vi.clearAllMocks();
  });

  it("default_isComfortable — fresh state returns 'comfortable'", async () => {
    const { density } = await freshComposable();
    expect(density.value).toBe("comfortable");
  });

  it("setDensity_updatesRef — setDensity() changes the reactive value", async () => {
    const { density, setDensity } = await freshComposable();

    setDensity("compact");
    expect(density.value).toBe("compact");

    setDensity("default");
    expect(density.value).toBe("default");

    setDensity("comfortable");
    expect(density.value).toBe("comfortable");
  });

  it("setDensity_persistsToStorage — setDensity() writes to localStorage", async () => {
    const { setDensity } = await freshComposable();

    setDensity("compact");

    expect(localStorageMock.setItem).toHaveBeenCalledWith(STORAGE_KEY, "compact");
  });

  it("loads_from_localStorage — pre-seeded 'compact' is restored on module load", async () => {
    storage.set(STORAGE_KEY, "compact");

    const { density } = await freshComposable();

    expect(density.value).toBe("compact");
  });

  it("loads_from_localStorage_default — pre-seeded 'default' is restored", async () => {
    storage.set(STORAGE_KEY, "default");

    const { density } = await freshComposable();

    expect(density.value).toBe("default");
  });

  it("ignores_invalid_stored_value — corrupted storage falls back to 'comfortable'", async () => {
    storage.set(STORAGE_KEY, "ultra-mega-sparse");

    const { density } = await freshComposable();

    expect(density.value).toBe("comfortable");
  });

  it("cyclesAllValues — all three values round-trip through localStorage", async () => {
    const { setDensity, density } = await freshComposable();
    const options = ["compact", "comfortable", "default"] as const;

    for (const opt of options) {
      setDensity(opt);
      expect(density.value).toBe(opt);
      expect(localStorageMock.setItem).toHaveBeenLastCalledWith(STORAGE_KEY, opt);
    }
  });

  it("DENSITY_OPTIONS — exports all three canonical values", async () => {
    const { DENSITY_OPTIONS } = await freshComposable();
    expect(DENSITY_OPTIONS).toEqual(["compact", "comfortable", "default"]);
  });
});
