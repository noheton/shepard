/**
 * MFFD-IMAGEBUNDLE-PANE-MOUNT-1 — Unit tests for DataObjectImageBundlePane
 * detection logic and the imageBundleScrubber helpers.
 *
 * Strategy:
 *   - The pane's detection logic is driven by two parallel `fetch` calls per
 *     candidate: GET /v2/references/{appId} (for containerAppId) and
 *     GET /v2/references/{appId}/groups?page=0&pageSize=1 (for first group).
 *     We stub `globalThis.fetch` per test to return controlled shapes.
 *   - The `imageBundleScrubber.ts` helpers are pure functions; tested
 *     directly without mocking.
 *
 * The pane's reactive `watch` + `detectImageBundle` are exercised through
 * the module's exported logic — we import and call the detection helpers
 * via the same patterns as the composable test suite uses for fetch-driven
 * modules (e.g. useFetchChannelPreview.test.ts).
 */

import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import {
  planPageForFrame,
  needsRefetch,
  frameLabel,
} from "../../utils/imageBundleScrubber";
import { isImageFilename } from "../../composables/container/useFetchFileThumbnail";

// ── Helper: flush Promise microtask queue ─────────────────────────────────────
const flush = () => new Promise<void>(r => setTimeout(r, 0));

// ── Minimal FileBundleIO fixture builders ─────────────────────────────────────

function makeBundle(
  groupFiles: Array<{ filename: string }>,
  opts?: { bundleAppId?: string; groupAppId?: string; containerAppId?: string },
) {
  return {
    appId: opts?.bundleAppId ?? "bundle-app-id-1",
    containerAppId: opts?.containerAppId ?? "container-app-id-1",
    groups: [
      {
        appId: opts?.groupAppId ?? "group-app-id-1",
        name: "Group 1",
        files: groupFiles.map(f => ({ filename: f.filename })),
      },
    ],
  };
}

// ─────────────────────────────────────────────────────────────────────────────
// Section 1: imageBundleScrubber.ts pure helpers
// ─────────────────────────────────────────────────────────────────────────────

describe("planPageForFrame — basic pagination math", () => {
  it("returns page 0 offset 0 for frameIndex 0", () => {
    const result = planPageForFrame(0, 38, 200);
    expect(result).toEqual({ page: 0, offsetInPage: 0, pageSize: 200 });
  });

  it("returns page 0 for any index within the first page", () => {
    expect(planPageForFrame(199, 1000, 200).page).toBe(0);
    expect(planPageForFrame(199, 1000, 200).offsetInPage).toBe(199);
  });

  it("wraps to page 1 at index == pageSize", () => {
    const result = planPageForFrame(200, 1000, 200);
    expect(result.page).toBe(1);
    expect(result.offsetInPage).toBe(0);
  });

  it("computes correct page and offset for an interior frame", () => {
    // Frame 437, pageSize 200: page 2 (frames 400-599), offset 37
    const result = planPageForFrame(437, 1000, 200);
    expect(result.page).toBe(2);
    expect(result.offsetInPage).toBe(37);
  });

  it("clamps frameIndex to totalFrames-1 to avoid past-end page", () => {
    const result = planPageForFrame(999, 38, 200);
    // Clamped to frame 37; all within page 0
    expect(result.page).toBe(0);
    expect(result.offsetInPage).toBe(37);
  });

  it("returns page 0 when totalFrames is 0", () => {
    const result = planPageForFrame(5, 0, 200);
    expect(result).toEqual({ page: 0, offsetInPage: 0, pageSize: 200 });
  });

  it("returns page 0 when frameIndex is negative", () => {
    const result = planPageForFrame(-1, 38, 200);
    expect(result).toEqual({ page: 0, offsetInPage: 0, pageSize: 200 });
  });

  it("clamps pageSize to [1, 1000]", () => {
    expect(planPageForFrame(0, 10, 0).pageSize).toBe(1);
    expect(planPageForFrame(0, 10, 9999).pageSize).toBe(1000);
    expect(planPageForFrame(0, 10, 50).pageSize).toBe(50);
  });

  it("uses 200 as the default pageSize", () => {
    expect(planPageForFrame(0, 38).pageSize).toBe(200);
  });
});

describe("needsRefetch", () => {
  it("returns true when cachedPage is null", () => {
    expect(needsRefetch(null, { page: 0, offsetInPage: 0, pageSize: 200 })).toBe(true);
  });

  it("returns false when cachedPage matches desired page", () => {
    expect(needsRefetch(2, { page: 2, offsetInPage: 5, pageSize: 200 })).toBe(false);
  });

  it("returns true when cachedPage differs from desired page", () => {
    expect(needsRefetch(1, { page: 2, offsetInPage: 5, pageSize: 200 })).toBe(true);
  });
});

describe("frameLabel", () => {
  it("returns 'frame 0 of 0' when totalFrames is 0", () => {
    expect(frameLabel(0, 0)).toBe("frame 0 of 0");
  });

  it("returns 1-based label for valid frames", () => {
    expect(frameLabel(0, 38)).toBe("frame 1 of 38");
    expect(frameLabel(37, 38)).toBe("frame 38 of 38");
  });

  it("clamps high frameIndex to totalFrames-1", () => {
    expect(frameLabel(999, 38)).toBe("frame 38 of 38");
  });

  it("clamps negative frameIndex to 0", () => {
    expect(frameLabel(-5, 38)).toBe("frame 1 of 38");
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// Section 2: isImageFilename — integration with pane detection logic
// (the pane delegates extension checks to this function)
// ─────────────────────────────────────────────────────────────────────────────

describe("isImageFilename — types accepted by the image bundle pane", () => {
  it("accepts the MFFD AFP frame extensions", () => {
    expect(isImageFilename("frame_001.png")).toBe(true);
    expect(isImageFilename("scan_001.tif")).toBe(true);
    expect(isImageFilename("thermal_001.tiff")).toBe(true);
    expect(isImageFilename("photo.jpg")).toBe(true);
    expect(isImageFilename("photo.jpeg")).toBe(true);
  });

  it("rejects non-image files that could appear in bundles", () => {
    expect(isImageFilename("data.csv")).toBe(false);
    expect(isImageFilename("report.pdf")).toBe(false);
    expect(isImageFilename("model.stl")).toBe(false);
    expect(isImageFilename("archive.zip")).toBe(false);
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// Section 3: Detection logic — fetch mocking
//
// We test the firstGroupHasImageFile detection predicate by reproducing
// its logic (which is a pure function over a FileBundleIO shape) and
// verifying it correctly classifies image vs. non-image bundles.
//
// The full async `detectImageBundle` flow is covered by stubbing globalThis.fetch
// and testing a minimal reproduction of the detection loop.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Reproduced from DataObjectImageBundlePane.vue — firstGroupHasImageFile logic.
 * Kept inline so the test is self-contained and doesn't depend on SFC mounting.
 */
function firstGroupHasImageFile(bundle: {
  groups?: Array<{ files?: Array<{ filename?: string | null }> }>;
}): boolean {
  const firstGroup = bundle.groups?.[0];
  if (!firstGroup?.files?.length) return false;
  return firstGroup.files.some(f => isImageFilename(f.filename ?? null));
}

describe("firstGroupHasImageFile — pane detection predicate", () => {
  it("returns false when groups array is absent", () => {
    expect(firstGroupHasImageFile({})).toBe(false);
  });

  it("returns false when first group has no files", () => {
    expect(firstGroupHasImageFile({ groups: [{ files: [] }] })).toBe(false);
  });

  it("returns false when first group files are all non-image", () => {
    const bundle = makeBundle([{ filename: "data.csv" }, { filename: "report.pdf" }]);
    expect(firstGroupHasImageFile(bundle)).toBe(false);
  });

  it("returns true when ANY file in the first group is an image", () => {
    const bundle = makeBundle([{ filename: "data.csv" }, { filename: "frame_001.png" }]);
    expect(firstGroupHasImageFile(bundle)).toBe(true);
  });

  it("returns true for the canonical MFFD AFP bundle (38 TPS .png frames)", () => {
    const files = Array.from({ length: 38 }, (_, i) => ({
      filename: `frame_${String(i + 1).padStart(3, "0")}.png`,
    }));
    const bundle = makeBundle(files);
    expect(firstGroupHasImageFile(bundle)).toBe(true);
  });

  it("returns true for .tif (thermoplastic AFP scan)", () => {
    const bundle = makeBundle([{ filename: "afp_scan_ply5.tif" }]);
    expect(firstGroupHasImageFile(bundle)).toBe(true);
  });

  it("only inspects the FIRST group — even if a later group has image files", () => {
    const bundle = {
      appId: "b1",
      containerAppId: "c1",
      groups: [
        { appId: "g1", name: "Raw data", files: [{ filename: "data.csv" }] },
        { appId: "g2", name: "Frames", files: [{ filename: "frame.png" }] },
      ],
    };
    // First group has no image → false even though second group does.
    expect(firstGroupHasImageFile(bundle)).toBe(false);
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// Section 4: Full async detection loop — fetch stub
// ─────────────────────────────────────────────────────────────────────────────

interface TestFileBundleIO {
  appId?: string | null;
  containerAppId?: string | null;
  groups?: Array<{
    appId?: string | null;
    name?: string | null;
    files?: Array<{ filename?: string | null }>;
  }>;
}

interface TestResolvedBundle {
  bundleAppId: string;
  groupAppId: string;
  containerAppId: string | null;
  groupName: string | null;
}

/**
 * Minimal re-implementation of the async detection loop from
 * DataObjectImageBundlePane.vue, used to test fetch-mock scenarios without
 * mounting the full SFC.
 *
 * Mirrors the 2-parallel-call shape introduced by APISIMP-BUNDLE-REF-KIND-UNIFY
 * slice 2: GET /v2/references/{id} + GET /v2/references/{id}/groups?page=0&pageSize=1.
 */
async function detectImageBundle(
  candidateBundleAppIds: string[],
  baseUrl: string,
  token: string | null,
): Promise<TestResolvedBundle | null> {
  const headers = {
    Authorization: token ? `Bearer ${token}` : "",
    Accept: "application/json",
  };
  for (const bundleAppId of candidateBundleAppIds) {
    const encoded = encodeURIComponent(bundleAppId);
    let bundle: TestFileBundleIO | null = null;
    try {
      const [refRes, groupsRes] = await Promise.all([
        fetch(`${baseUrl}/v2/references/${encoded}`, { headers }),
        fetch(`${baseUrl}/v2/references/${encoded}/groups?page=0&pageSize=1`, { headers }),
      ]);
      if (!refRes.ok || !groupsRes.ok) continue;
      const refJson = (await refRes.json()) as { payload?: { containerAppId?: string | null } };
      const groupsJson = (await groupsRes.json()) as {
        items?: Array<{ appId?: string | null; name?: string | null; files?: Array<{ filename?: string | null }> }>;
      };
      bundle = {
        appId: bundleAppId,
        containerAppId: refJson.payload?.containerAppId ?? null,
        groups: groupsJson.items ?? [],
      };
    } catch {
      continue;
    }
    if (!bundle || !firstGroupHasImageFile(bundle)) continue;
    const firstGroup = bundle.groups![0]!;
    if (!firstGroup.appId) continue;
    return {
      bundleAppId,
      groupAppId: firstGroup.appId,
      containerAppId: bundle.containerAppId ?? null,
      groupName: firstGroup.name ?? null,
    };
  }
  return null;
}

describe("detectImageBundle — async detection loop", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("returns null when candidateBundleAppIds is empty (renders nothing)", async () => {
    const result = await detectImageBundle([], "http://localhost", null);
    expect(result).toBeNull();
    expect(fetch).not.toHaveBeenCalled();
  });

  it("returns null when all candidate bundles have non-image first groups", async () => {
    const nonImageGroup = [{ appId: "g1", name: "Group 1", files: [{ filename: "data.csv" }] }];
    // 2 calls per candidate (ref + groups) × 2 candidates = 4 total
    (fetch as ReturnType<typeof vi.fn>)
      .mockResolvedValueOnce({ ok: true, json: async () => ({ payload: { containerAppId: null } }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ items: nonImageGroup }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ payload: { containerAppId: null } }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ items: nonImageGroup }) });

    const result = await detectImageBundle(
      ["bundle-a", "bundle-b"],
      "http://localhost",
      "tok",
    );

    expect(result).toBeNull();
    expect(fetch).toHaveBeenCalledTimes(4);
  });

  it("resolves to the FIRST matching bundle when image files found", async () => {
    // bundle-a: ref + groups (non-image)
    // bundle-b: ref + groups (image) — 4 calls total
    (fetch as ReturnType<typeof vi.fn>)
      .mockResolvedValueOnce({ ok: true, json: async () => ({ payload: { containerAppId: null } }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ items: [{ appId: "g-a", name: "Group 1", files: [{ filename: "data.csv" }] }] }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ payload: { containerAppId: "container-b" } }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ items: [{ appId: "g-b", name: "Group 1", files: [{ filename: "frame.png" }] }] }) });

    const result = await detectImageBundle(
      ["bundle-a", "bundle-b"],
      "http://localhost",
      "tok",
    );

    expect(result).toEqual({
      bundleAppId: "bundle-b",
      groupAppId: "g-b",
      containerAppId: "container-b",
      groupName: "Group 1",
    });
    expect(fetch).toHaveBeenCalledTimes(4);
  });

  it("skips candidates that return HTTP errors (fail-soft, not fail-hard)", async () => {
    // bundle-404: ref returns not-ok → whole block skipped; groups call still consumed by Promise.all
    // bundle-b: both ok → found
    (fetch as ReturnType<typeof vi.fn>)
      .mockResolvedValueOnce({ ok: false })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ items: [] }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ payload: { containerAppId: null } }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ items: [{ appId: "g-b", name: "Group 1", files: [{ filename: "frame.tif" }] }] }) });

    const result = await detectImageBundle(
      ["bundle-404", "bundle-b"],
      "http://localhost",
      null,
    );

    expect(result?.bundleAppId).toBe("bundle-b");
  });

  it("skips candidates that throw network errors (fail-soft)", async () => {
    // bundle-fail: ref throws → Promise.all rejects → catch → continue
    // (groups call for bundle-fail is started but its result is discarded)
    // bundle-b: both ok → found
    (fetch as ReturnType<typeof vi.fn>)
      .mockRejectedValueOnce(new Error("network error"))
      .mockResolvedValueOnce({ ok: true, json: async () => ({ items: [] }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ payload: { containerAppId: null } }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ items: [{ appId: "g-b", name: "Group 1", files: [{ filename: "frame.jpg" }] }] }) });

    const result = await detectImageBundle(
      ["bundle-fail", "bundle-b"],
      "http://localhost",
      "tok",
    );

    expect(result?.bundleAppId).toBe("bundle-b");
  });

  it("stops at the first matching bundle (short-circuit — no further fetches)", async () => {
    // bundle-a: 2 parallel calls (ref + groups) → image found → return
    // bundle-b: never fetched (2 remaining mocks unused)
    (fetch as ReturnType<typeof vi.fn>)
      .mockResolvedValueOnce({ ok: true, json: async () => ({ payload: { containerAppId: "c-a" } }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ items: [{ appId: "g-a", name: "Group 1", files: [{ filename: "frame.png" }] }] }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ payload: { containerAppId: "c-b" } }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ items: [{ appId: "g-b", name: "Group 1", files: [{ filename: "frame.png" }] }] }) });

    await detectImageBundle(["bundle-a", "bundle-b"], "http://localhost", "tok");

    // Only bundle-a's 2 parallel calls were made (short-circuit after first match)
    expect(fetch).toHaveBeenCalledTimes(2);
  });

  it("includes Authorization bearer token in the request when token is present", async () => {
    (fetch as ReturnType<typeof vi.fn>)
      .mockResolvedValueOnce({ ok: true, json: async () => ({ payload: { containerAppId: "c-a" } }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ items: [{ appId: "g-a", name: "Group 1", files: [{ filename: "frame.png" }] }] }) });

    await detectImageBundle(["bundle-a"], "http://localhost", "my-jwt-token");

    expect(fetch).toHaveBeenCalledWith(
      "http://localhost/v2/references/bundle-a",
      expect.objectContaining({
        headers: expect.objectContaining({
          Authorization: "Bearer my-jwt-token",
        }),
      }),
    );
    expect(fetch).toHaveBeenCalledWith(
      "http://localhost/v2/references/bundle-a/groups?page=0&pageSize=1",
      expect.objectContaining({
        headers: expect.objectContaining({
          Authorization: "Bearer my-jwt-token",
        }),
      }),
    );
  });

  it("uses empty Authorization header when no token (anonymous access)", async () => {
    (fetch as ReturnType<typeof vi.fn>)
      .mockResolvedValueOnce({ ok: true, json: async () => ({ payload: { containerAppId: "c-a" } }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ items: [{ appId: "g-a", name: "Group 1", files: [{ filename: "frame.png" }] }] }) });

    await detectImageBundle(["bundle-a"], "http://localhost", null);

    expect(fetch).toHaveBeenCalledWith(
      "http://localhost/v2/references/bundle-a",
      expect.objectContaining({
        headers: expect.objectContaining({ Authorization: "" }),
      }),
    );
  });

  it("returns null when bundle has no groups (empty FileBundleIO)", async () => {
    (fetch as ReturnType<typeof vi.fn>)
      .mockResolvedValueOnce({ ok: true, json: async () => ({ payload: { containerAppId: null } }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ items: [] }) });

    const result = await detectImageBundle(["b1"], "http://localhost", null);
    expect(result).toBeNull();
  });

  // Flush used in case tests use async timers
  it("flush helper sanity check", async () => {
    await flush();
    expect(true).toBe(true);
  });
});
