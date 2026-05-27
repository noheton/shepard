/**
 * UX-PIN1 — Unit tests for `usePinnedChannels`.
 *
 * Tests exercise:
 *   - pin_addsToList
 *   - pin_deduplicates
 *   - unpin_removesFromList
 *   - isPinned_returnsTrueForPinned
 *   - loads_from_localStorage_on_init (persistence smoke)
 *
 * The module-scope singleton is reset before each test by clearing the
 * `localStorage` stub and resetting the module.  Vitest `resetModules`
 * ensures a fresh module is imported each time.
 */

import { describe, it, expect, beforeEach, vi } from "vitest";

// ── localStorage mock ─────────────────────────────────────────────────────────
// The test environment is `node` (no JSDOM), so localStorage is undefined.
// We provide a minimal in-memory stub before each test.

const storage: Record<string, string> = {};

const localStorageMock = {
  getItem: vi.fn((key: string) => storage[key] ?? null),
  setItem: vi.fn((key: string, value: string) => { storage[key] = value; }),
  removeItem: vi.fn((key: string) => { delete storage[key]; }),
  clear: vi.fn(() => { Object.keys(storage).forEach(k => delete storage[k]); }),
};

vi.stubGlobal("localStorage", localStorageMock);

// ── Helpers ───────────────────────────────────────────────────────────────────

/** Reset module registry + storage between tests so each runs isolated. */
async function freshComposable() {
  vi.resetModules();
  const mod = await import("~/composables/container/usePinnedChannels");
  return mod.usePinnedChannels();
}

// ── Fixtures ──────────────────────────────────────────────────────────────────

function ch(shepardId: string) {
  return {
    shepardId,
    containerId: 42,
    channelName: `channel-${shepardId}`,
    containerPath: "/containers/timeseries/42",
  };
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe("usePinnedChannels", () => {
  beforeEach(() => {
    localStorageMock.clear();
    vi.clearAllMocks();
  });

  it("pin_addsToList — pinning a channel adds it to pinnedChannels", async () => {
    const { pin, pinnedChannels } = await freshComposable();
    expect(pinnedChannels.value).toHaveLength(0);

    pin(ch("uuid-1"));

    expect(pinnedChannels.value).toHaveLength(1);
    expect(pinnedChannels.value[0]!.shepardId).toBe("uuid-1");
  });

  it("pin_deduplicates — pinning the same shepardId twice keeps only one entry", async () => {
    const { pin, pinnedChannels } = await freshComposable();

    pin(ch("uuid-2"));
    pin(ch("uuid-2"));

    expect(pinnedChannels.value).toHaveLength(1);
  });

  it("pin_multipleDistinct — multiple distinct channels are all stored", async () => {
    const { pin, pinnedChannels } = await freshComposable();

    pin(ch("uuid-a"));
    pin(ch("uuid-b"));
    pin(ch("uuid-c"));

    expect(pinnedChannels.value).toHaveLength(3);
  });

  it("unpin_removesFromList — unpinning removes only the target channel", async () => {
    const { pin, unpin, pinnedChannels } = await freshComposable();

    pin(ch("uuid-x"));
    pin(ch("uuid-y"));
    unpin("uuid-x");

    expect(pinnedChannels.value).toHaveLength(1);
    expect(pinnedChannels.value[0]!.shepardId).toBe("uuid-y");
  });

  it("unpin_missingId — unpinning an unknown shepardId is a no-op", async () => {
    const { pin, unpin, pinnedChannels } = await freshComposable();

    pin(ch("uuid-z"));
    unpin("does-not-exist");

    expect(pinnedChannels.value).toHaveLength(1);
  });

  it("isPinned_returnsTrueForPinned — returns true after pinning, false before", async () => {
    const { pin, isPinned } = await freshComposable();

    expect(isPinned("uuid-q")).toBe(false);
    pin(ch("uuid-q"));
    expect(isPinned("uuid-q")).toBe(true);
  });

  it("isPinned_returnsFalseAfterUnpin — returns false after unpinning", async () => {
    const { pin, unpin, isPinned } = await freshComposable();

    pin(ch("uuid-r"));
    unpin("uuid-r");

    expect(isPinned("uuid-r")).toBe(false);
  });

  it("loads_from_localStorage_on_init — pre-seeded storage is read on module load", async () => {
    // Pre-seed localStorage with a serialised channel.
    const seed = [ch("uuid-seed")];
    storage["shepard:pinnedChannels"] = JSON.stringify(seed);

    const { pinnedChannels } = await freshComposable();

    expect(pinnedChannels.value).toHaveLength(1);
    expect(pinnedChannels.value[0]!.shepardId).toBe("uuid-seed");
  });

  it("pin_persists_to_localStorage — pin() writes to localStorage", async () => {
    const { pin } = await freshComposable();

    pin(ch("uuid-persist"));

    expect(localStorageMock.setItem).toHaveBeenCalledWith(
      "shepard:pinnedChannels",
      expect.stringContaining("uuid-persist"),
    );
  });

  it("unpin_persists_to_localStorage — unpin() updates localStorage", async () => {
    const { pin, unpin } = await freshComposable();

    pin(ch("uuid-p"));
    vi.clearAllMocks(); // clear the pin() setItem call
    unpin("uuid-p");

    expect(localStorageMock.setItem).toHaveBeenCalledWith(
      "shepard:pinnedChannels",
      expect.not.stringContaining("uuid-p"),
    );
  });
});
