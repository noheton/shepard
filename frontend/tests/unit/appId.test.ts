/**
 * II3 (ui-scrutinizer-2026-05-30) — unit tests for the canonical appId
 * truncation + clipboard helpers used by `<CopyableAppIdChip>`.
 *
 * The existing `sceneGraphsLanding.test.ts` covers `truncateAppId` via
 * the re-export — these tests cover the canonical surface directly plus
 * the clipboard helper's degradation paths.
 */
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import {
  truncateAppId,
  copyAppIdToClipboard,
  readCollectionAppId,
  readDataObjectAppId,
  readContainerAppId,
} from "../../utils/appId";

describe("appId — truncateAppId", () => {
  it("uses 8…4 form for a full UUID v7", () => {
    expect(truncateAppId("0197b6a2-aaaa-7000-8000-000000000001")).toBe(
      "0197b6a2…0001",
    );
  });

  it("returns the input unchanged when already short", () => {
    expect(truncateAppId("short")).toBe("short");
    expect(truncateAppId("0123456789abc")).toBe("0123456789abc"); // 13 chars
  });

  it("returns empty string for empty / falsy input", () => {
    expect(truncateAppId("")).toBe("");
  });

  // Note: the pre-II3 `sceneGraphsLanding.truncateAppId` re-export was
  // removed to dodge a Nuxt duplicate-auto-import warning. The legacy
  // `sceneGraphsLanding.test.ts` now imports `truncateAppId` directly
  // from this module.
});

describe("appId — copyAppIdToClipboard", () => {
  // Stash the descriptor only once — repeated define/redefine of a
  // jsdom-managed prototype property triggers
  // `Object.defineProperty called on non-object` after the first
  // tear-down, so we install via `vi.stubGlobal` on `navigator` and
  // restore by calling `vi.unstubAllGlobals`.
  beforeEach(() => {
    vi.unstubAllGlobals();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  function installClipboard(writeText: (s: string) => Promise<void>): void {
    vi.stubGlobal("navigator", {
      ...globalThis.navigator,
      clipboard: { writeText },
    });
  }

  it("writes to navigator.clipboard when available and returns true", async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    installClipboard(writeText);
    const ok = await copyAppIdToClipboard("0197b6a2-aaaa-7000-8000-000000000001");
    expect(ok).toBe(true);
    expect(writeText).toHaveBeenCalledWith(
      "0197b6a2-aaaa-7000-8000-000000000001",
    );
  });

  it("returns false (no throw) when navigator.clipboard is unavailable", async () => {
    vi.stubGlobal("navigator", { ...globalThis.navigator, clipboard: undefined });
    const ok = await copyAppIdToClipboard("abc-123");
    expect(ok).toBe(false);
  });

  it("returns false (no throw) when writeText rejects", async () => {
    installClipboard(() =>
      Promise.reject(new Error("NotAllowedError")),
    );
    const ok = await copyAppIdToClipboard("abc-123");
    expect(ok).toBe(false);
  });

  it("returns false for empty input without touching the clipboard API", async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    installClipboard(writeText);
    const ok = await copyAppIdToClipboard("");
    expect(ok).toBe(false);
    expect(writeText).not.toHaveBeenCalled();
  });
});

describe("appId — read*AppId navigation accessors (V2-LINKS)", () => {
  const UUID = "019eb019-d49b-7131-b2d2-3f3107d36a4f";

  it("reads appId directly off an entity (the v2 wire shape)", () => {
    // The generated TS model only declares `id`; appId arrives on the wire.
    expect(readCollectionAppId({ id: 42, appId: UUID })).toBe(UUID);
    expect(readDataObjectAppId({ id: 99, appId: UUID })).toBe(UUID);
    expect(readContainerAppId({ id: 7, appId: UUID })).toBe(UUID);
  });

  it("falls back to additional_properties.appId when surfaced there", () => {
    expect(
      readCollectionAppId({ id: 42, additional_properties: { appId: UUID } }),
    ).toBe(UUID);
  });

  it("returns null when the appId is genuinely absent on the wire", () => {
    // A numeric-only entity must NOT yield a route segment — the v2 detail
    // route 404s on the numeric id (the operator's /collections/367014 bug).
    expect(readCollectionAppId({ id: 42 })).toBeNull();
    expect(readDataObjectAppId({ id: 99 })).toBeNull();
  });

  it("returns null for nullish / non-object input", () => {
    expect(readCollectionAppId(null)).toBeNull();
    expect(readCollectionAppId(undefined)).toBeNull();
    expect(readCollectionAppId(42)).toBeNull();
    expect(readDataObjectAppId("not-an-object")).toBeNull();
  });

  it("returns null for an empty-string appId rather than an empty segment", () => {
    expect(readCollectionAppId({ id: 42, appId: "" })).toBeNull();
  });
});
