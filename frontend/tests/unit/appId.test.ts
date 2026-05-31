/**
 * II3 (ui-scrutinizer-2026-05-30) — unit tests for the canonical appId
 * truncation + clipboard helpers used by `<CopyableAppIdChip>`.
 *
 * The existing `sceneGraphsLanding.test.ts` covers `truncateAppId` via
 * the re-export — these tests cover the canonical surface directly plus
 * the clipboard helper's degradation paths.
 */
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { truncateAppId, copyAppIdToClipboard } from "../../utils/appId";

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
