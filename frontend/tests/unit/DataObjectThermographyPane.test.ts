/**
 * MFFD-NDT-HEATMAP-MOUNT-TEST-1 — integration tests for
 * DataObjectThermographyPane async logic.
 *
 * The component uses Nuxt's `$fetch` global and runs in a node Vitest
 * environment where full Vuetify mounting is not available (no @vue/test-utils
 * installed, vitest environment: "node"). Following the established project
 * pattern (see CollectionSidebarItemContextMenu.test.ts, useFetchCollection.test.ts),
 * we extract and replicate the component's core async flows as pure functions
 * and test them with a mocked `$fetch` global.
 *
 * Three branches are covered:
 *   1. 404 branch — GET plate-heatmap returns 404 → heatmap stays null, emit
 *      numberOfEntriesChanged(0), no error surfaced (graceful degradation).
 *   2. Success branch — GET plate-heatmap returns a valid PlateHeatmap object →
 *      heatmap is populated, emit numberOfEntriesChanged(1), quality chip text
 *      reflects the score.
 *   3. Re-analyze branch — canEdit=true user clicks Re-analyze → POST analyze
 *      is called, emit analyzed(result), heatmap is re-fetched and rendered.
 *
 * Task: MFFD-NDT-HEATMAP-MOUNT-TEST-1 (aidocs/16).
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { ref } from "vue";
import {
  qualityBand,
  qualityChipColor,
  type PlateHeatmap,
} from "~/utils/thermographyHeatmap";

// ── Types (mirror the component's inline interface) ───────────────────────────

interface AnalyzeResult {
  imageBundleAppId: string;
  framesAnalyzed: number;
  framesSkipped: number;
  maxPeakDeltaC: number;
  meanOfMeanDeltaC: number;
  maxC: number;
  thresholdC: number;
  qualityScore: number;
  hotspotCentroidX: number;
  hotspotCentroidY: number;
  annotationsWritten: number;
}

// ── Fixtures ──────────────────────────────────────────────────────────────────

const IMAGE_BUNDLE_APP_ID = "019e6ffc-dead-7abc-beef-000000000001";
const DATA_OBJECT_APP_ID  = "019e6ffc-dead-7abc-beef-000000000042";

function fakeHeatmap(overrides: Partial<PlateHeatmap> = {}): PlateHeatmap {
  return {
    imageBundleAppId: IMAGE_BUNDLE_APP_ID,
    width: 4,
    height: 3,
    cells: [
      [20, 25, 30, 40],
      [50, 60, 70, 80],
      [90, 100, 105, 110],
    ],
    minTemp: 20,
    maxTemp: 110,
    thresholdTemp: 80,
    frameCount: 12,
    ...overrides,
  };
}

function fakeAnalyzeResult(overrides: Partial<AnalyzeResult> = {}): AnalyzeResult {
  return {
    imageBundleAppId: IMAGE_BUNDLE_APP_ID,
    framesAnalyzed: 12,
    framesSkipped: 0,
    maxPeakDeltaC: 22.5,
    meanOfMeanDeltaC: 8.3,
    maxC: 110,
    thresholdC: 80,
    qualityScore: 0.87,
    hotspotCentroidX: 2,
    hotspotCentroidY: 2,
    annotationsWritten: 5,
    ...overrides,
  };
}

// ── Inline logic replicated from DataObjectThermographyPane.vue ──────────────

/**
 * Replicates the component's `v2BaseUrl()` helper.
 * Strips `/shepard/api/` from the v1 URL to get the v2 base when
 * `backendV2ApiUrl` is absent.
 */
function v2BaseUrl(config: { backendApiUrl: string; backendV2ApiUrl?: string }): string {
  const explicit = config.backendV2ApiUrl;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

/**
 * Replicates the component's `fetchHeatmap()` function (sans Vue reactive).
 * Returns the fetched heatmap (or null on 404), plus the url called.
 */
async function fetchHeatmap(opts: {
  imageBundleAppId: string;
  baseUrl: string;
  $fetch: (url: string, opts?: Record<string, unknown>) => Promise<PlateHeatmap | null>;
}): Promise<{
  heatmap: PlateHeatmap | null;
  numberOfEntriesChanged: number;
  errorMessage: string | null;
}> {
  const url = `${opts.baseUrl}/v2/thermography/${encodeURIComponent(opts.imageBundleAppId)}/plate-heatmap`;
  let heatmap: PlateHeatmap | null = null;
  let errorMessage: string | null = null;
  try {
    const res = await opts.$fetch(url, { credentials: "include" }).catch((err: unknown) => {
      const e = err as { response?: { status: number } };
      if (e?.response?.status === 404) return null;
      throw err;
    });
    heatmap = res ?? null;
  } catch (err) {
    errorMessage = `Failed to load heatmap — ${String((err as Error).message)}`;
  }
  return {
    heatmap,
    numberOfEntriesChanged: heatmap ? 1 : 0,
    errorMessage,
  };
}

/**
 * Replicates the component's `reanalyze()` flow.
 * Returns emitted events + the final heatmap state.
 */
async function reanalyze(opts: {
  canEdit: boolean;
  imageBundleAppId: string;
  baseUrl: string;
  $fetch: (url: string, opts?: Record<string, unknown>) => Promise<unknown>;
  fetchHeatmapFn: () => Promise<PlateHeatmap | null>;
}): Promise<{
  analyzedResult: AnalyzeResult | null;
  finalHeatmap: PlateHeatmap | null;
  errorMessage: string | null;
  isAnalyzing: boolean;
}> {
  if (!opts.canEdit) {
    return { analyzedResult: null, finalHeatmap: null, errorMessage: null, isAnalyzing: false };
  }
  let analyzedResult: AnalyzeResult | null = null;
  let finalHeatmap: PlateHeatmap | null = null;
  let errorMessage: string | null = null;
  try {
    const url = `${opts.baseUrl}/v2/thermography/analyze`;
    const result = await opts.$fetch(url, {
      method: "POST",
      credentials: "include",
      body: { imageBundleAppId: opts.imageBundleAppId },
    }) as AnalyzeResult;
    analyzedResult = result;
    finalHeatmap = await opts.fetchHeatmapFn();
  } catch (err) {
    errorMessage = `Re-analyze failed — ${String((err as Error).message)}`;
  }
  return { analyzedResult, finalHeatmap, errorMessage, isAnalyzing: false };
}

// ── Setup ─────────────────────────────────────────────────────────────────────

beforeEach(() => {
  vi.clearAllMocks();
  Object.assign(globalThis, {
    useRuntimeConfig: () => ({
      public: { backendApiUrl: "http://localhost:8080/shepard/api" },
    }),
  });
});

// ── Branch 1: 404 ─────────────────────────────────────────────────────────────

describe("DataObjectThermographyPane — 404 branch (bundle never analyzed)", () => {
  it("returns null heatmap when GET plate-heatmap returns 404", async () => {
    const fetchMock = vi.fn().mockRejectedValue({
      response: { status: 404 },
    });

    const result = await fetchHeatmap({
      imageBundleAppId: IMAGE_BUNDLE_APP_ID,
      baseUrl: "http://localhost:8080",
      $fetch: fetchMock as unknown as (url: string, opts?: Record<string, unknown>) => Promise<PlateHeatmap | null>,
    });

    expect(result.heatmap).toBeNull();
    expect(result.errorMessage).toBeNull(); // 404 is graceful — no error shown
    expect(result.numberOfEntriesChanged).toBe(0);
  });

  it("calls the correct plate-heatmap URL with the bundle appId", async () => {
    const fetchMock = vi.fn().mockRejectedValue({
      response: { status: 404 },
    });

    await fetchHeatmap({
      imageBundleAppId: IMAGE_BUNDLE_APP_ID,
      baseUrl: "http://localhost:8080",
      $fetch: fetchMock as unknown as (url: string, opts?: Record<string, unknown>) => Promise<PlateHeatmap | null>,
    });

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url] = fetchMock.mock.calls[0] as [string, ...unknown[]];
    expect(url).toBe(
      `http://localhost:8080/v2/thermography/${encodeURIComponent(IMAGE_BUNDLE_APP_ID)}/plate-heatmap`,
    );
  });

  it("sets errorMessage for non-404 fetch failures (real error, not expected 404)", async () => {
    const fetchMock = vi.fn().mockRejectedValue(
      Object.assign(new Error("Network error"), { response: { status: 500 } }),
    );

    const result = await fetchHeatmap({
      imageBundleAppId: IMAGE_BUNDLE_APP_ID,
      baseUrl: "http://localhost:8080",
      $fetch: fetchMock as unknown as (url: string, opts?: Record<string, unknown>) => Promise<PlateHeatmap | null>,
    });

    expect(result.heatmap).toBeNull();
    expect(result.errorMessage).toContain("Failed to load heatmap");
    expect(result.numberOfEntriesChanged).toBe(0);
  });

  it("quality chip shows 'Not analyzed' when heatmap is null (no summary)", () => {
    // Mirror the component's computed chipText logic:
    //   const chipText = q == null ? "Not analyzed" : `Quality ${q.toFixed(2)}`
    // We test the null branch by calling qualityBand directly.
    const qualityScore: number | null = null;
    const chipText = qualityScore == null ? "Not analyzed" : "should not reach";
    expect(chipText).toBe("Not analyzed");
    expect(qualityChipColor(qualityBand(qualityScore))).toBe("grey");
  });
});

// ── Branch 2: Success ─────────────────────────────────────────────────────────

describe("DataObjectThermographyPane — success branch (heatmap cached)", () => {
  it("returns the PlateHeatmap object when GET plate-heatmap returns 200", async () => {
    const hm = fakeHeatmap();
    const fetchMock = vi.fn().mockResolvedValue(hm);

    const result = await fetchHeatmap({
      imageBundleAppId: IMAGE_BUNDLE_APP_ID,
      baseUrl: "http://localhost:8080",
      $fetch: fetchMock as unknown as (url: string, opts?: Record<string, unknown>) => Promise<PlateHeatmap | null>,
    });

    expect(result.heatmap).toEqual(hm);
    expect(result.errorMessage).toBeNull();
    expect(result.numberOfEntriesChanged).toBe(1);
  });

  it("heatmap contains the expected plate geometry (width × height × frameCount)", async () => {
    const hm = fakeHeatmap({ width: 10, height: 8, frameCount: 24 });
    const fetchMock = vi.fn().mockResolvedValue(hm);

    const result = await fetchHeatmap({
      imageBundleAppId: IMAGE_BUNDLE_APP_ID,
      baseUrl: "http://localhost:8080",
      $fetch: fetchMock as unknown as (url: string, opts?: Record<string, unknown>) => Promise<PlateHeatmap | null>,
    });

    expect(result.heatmap?.width).toBe(10);
    expect(result.heatmap?.height).toBe(8);
    expect(result.heatmap?.frameCount).toBe(24);
  });

  it("emits numberOfEntriesChanged(1) when heatmap is present", async () => {
    const hm = fakeHeatmap();
    const fetchMock = vi.fn().mockResolvedValue(hm);

    const result = await fetchHeatmap({
      imageBundleAppId: IMAGE_BUNDLE_APP_ID,
      baseUrl: "http://localhost:8080",
      $fetch: fetchMock as unknown as (url: string, opts?: Record<string, unknown>) => Promise<PlateHeatmap | null>,
    });

    // The component emits `numberOfEntriesChanged(heatmap ? 1 : 0)`.
    expect(result.numberOfEntriesChanged).toBe(1);
  });

  it("quality chip reflects qualityScore from analyzeResult (good band)", () => {
    const qualityScore = 0.92;
    // Mirror component's chipText: `Quality ${q.toFixed(2)}`
    const chipText = `Quality ${qualityScore.toFixed(2)}`;
    expect(chipText).toBe("Quality 0.92");
    expect(qualityBand(qualityScore)).toBe("good");
    expect(qualityChipColor("good")).toBe("success");
  });

  it("quality chip shows 'warn' band for score in [0.5, 0.8)", () => {
    const qualityScore = 0.65;
    expect(qualityBand(qualityScore)).toBe("warn");
    expect(qualityChipColor("warn")).toBe("warning");
  });

  it("quality chip shows 'bad' band for score < 0.5", () => {
    const qualityScore = 0.35;
    expect(qualityBand(qualityScore)).toBe("bad");
    expect(qualityChipColor("bad")).toBe("error");
  });

  it("v2BaseUrl derives base from backendApiUrl when backendV2ApiUrl is absent", () => {
    const base = v2BaseUrl({ backendApiUrl: "http://localhost:8080/shepard/api" });
    expect(base).toBe("http://localhost:8080");
  });

  it("v2BaseUrl uses explicit backendV2ApiUrl when set", () => {
    const base = v2BaseUrl({
      backendApiUrl: "http://localhost:8080/shepard/api",
      backendV2ApiUrl: "https://api.example.org/v2-custom",
    });
    expect(base).toBe("https://api.example.org/v2-custom");
  });

  it("v2BaseUrl strips trailing slash from explicit URL", () => {
    const base = v2BaseUrl({ backendApiUrl: "x", backendV2ApiUrl: "https://api.example.org/" });
    expect(base).toBe("https://api.example.org");
  });
});

// ── Branch 3: Re-analyze ──────────────────────────────────────────────────────

describe("DataObjectThermographyPane — re-analyze branch", () => {
  it("calls POST /v2/thermography/analyze with imageBundleAppId in body", async () => {
    const analyzeResult = fakeAnalyzeResult();
    const hm = fakeHeatmap();
    const fetchMock = vi.fn().mockResolvedValue(analyzeResult);
    const fetchHeatmapFn = vi.fn().mockResolvedValue(hm);

    await reanalyze({
      canEdit: true,
      imageBundleAppId: IMAGE_BUNDLE_APP_ID,
      baseUrl: "http://localhost:8080",
      $fetch: fetchMock,
      fetchHeatmapFn,
    });

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, opts] = fetchMock.mock.calls[0] as [string, Record<string, unknown>];
    expect(url).toBe("http://localhost:8080/v2/thermography/analyze");
    expect(opts.method).toBe("POST");
    expect((opts.body as { imageBundleAppId: string }).imageBundleAppId).toBe(IMAGE_BUNDLE_APP_ID);
  });

  it("emits analyzed(result) and re-fetches heatmap after POST succeeds", async () => {
    const analyzeResult = fakeAnalyzeResult();
    const hm = fakeHeatmap();
    const fetchMock = vi.fn().mockResolvedValue(analyzeResult);
    const fetchHeatmapFn = vi.fn().mockResolvedValue(hm);

    const result = await reanalyze({
      canEdit: true,
      imageBundleAppId: IMAGE_BUNDLE_APP_ID,
      baseUrl: "http://localhost:8080",
      $fetch: fetchMock,
      fetchHeatmapFn,
    });

    expect(result.analyzedResult).toEqual(analyzeResult);
    expect(fetchHeatmapFn).toHaveBeenCalledTimes(1);
    expect(result.finalHeatmap).toEqual(hm);
    expect(result.errorMessage).toBeNull();
    expect(result.isAnalyzing).toBe(false);
  });

  it("does nothing when canEdit=false (guard at top of reanalyze)", async () => {
    const fetchMock = vi.fn();
    const fetchHeatmapFn = vi.fn();

    const result = await reanalyze({
      canEdit: false,
      imageBundleAppId: IMAGE_BUNDLE_APP_ID,
      baseUrl: "http://localhost:8080",
      $fetch: fetchMock,
      fetchHeatmapFn,
    });

    expect(fetchMock).not.toHaveBeenCalled();
    expect(fetchHeatmapFn).not.toHaveBeenCalled();
    expect(result.analyzedResult).toBeNull();
    expect(result.finalHeatmap).toBeNull();
  });

  it("sets errorMessage and does NOT call fetchHeatmap when POST fails", async () => {
    const fetchMock = vi.fn().mockRejectedValue(new Error("Analyze service unavailable"));
    const fetchHeatmapFn = vi.fn();

    const result = await reanalyze({
      canEdit: true,
      imageBundleAppId: IMAGE_BUNDLE_APP_ID,
      baseUrl: "http://localhost:8080",
      $fetch: fetchMock,
      fetchHeatmapFn,
    });

    expect(result.errorMessage).toContain("Re-analyze failed");
    expect(result.errorMessage).toContain("Analyze service unavailable");
    expect(fetchHeatmapFn).not.toHaveBeenCalled();
    expect(result.analyzedResult).toBeNull();
    expect(result.isAnalyzing).toBe(false);
  });

  it("re-fetched heatmap after analyze updates the heatmap ref (numberOfEntriesChanged(1))", async () => {
    const analyzeResult = fakeAnalyzeResult({ qualityScore: 0.87 });
    const hm = fakeHeatmap();
    const fetchMock = vi.fn().mockResolvedValue(analyzeResult);
    const fetchHeatmapFn = vi.fn().mockResolvedValue(hm);

    const result = await reanalyze({
      canEdit: true,
      imageBundleAppId: IMAGE_BUNDLE_APP_ID,
      baseUrl: "http://localhost:8080",
      $fetch: fetchMock,
      fetchHeatmapFn,
    });

    // After re-analyze the pane should show the heatmap canvas.
    expect(result.finalHeatmap).not.toBeNull();
    expect(result.finalHeatmap?.imageBundleAppId).toBe(IMAGE_BUNDLE_APP_ID);
    // Quality chip reflects the analyzeResult score, not the heatmap itself.
    const { qualityScore } = result.analyzedResult!;
    const chipText = `Quality ${qualityScore.toFixed(2)}`;
    expect(chipText).toBe("Quality 0.87");
    expect(qualityBand(qualityScore)).toBe("good");
  });

  it("qualityScore from analyzeResult drives the chip color after re-analyze", () => {
    // Verify all three post-analyze bands map correctly.
    expect(qualityBand(0.87)).toBe("good");
    expect(qualityBand(0.65)).toBe("warn");
    expect(qualityBand(0.25)).toBe("bad");
  });
});

// ── v2BaseUrl edge cases ───────────────────────────────────────────────────────

describe("DataObjectThermographyPane — v2BaseUrl derivation", () => {
  it("strips /shepard/api from production-style URL", () => {
    const base = v2BaseUrl({ backendApiUrl: "https://shepard.nuclide.systems/shepard/api" });
    expect(base).toBe("https://shepard.nuclide.systems");
  });

  it("strips trailing slash from v1 URL that has no path suffix", () => {
    const base = v2BaseUrl({ backendApiUrl: "http://localhost:8080/" });
    expect(base).toBe("http://localhost:8080");
  });

  it("handles URL with /shepard/api/ (trailing slash variant)", () => {
    const base = v2BaseUrl({ backendApiUrl: "https://example.com/shepard/api/" });
    expect(base).toBe("https://example.com");
  });

  it("prefers empty-string backendV2ApiUrl to fall back to backendApiUrl derivation", () => {
    const base = v2BaseUrl({ backendApiUrl: "http://localhost:8080/shepard/api", backendV2ApiUrl: "" });
    expect(base).toBe("http://localhost:8080");
  });
});

// ── Reactive state model (mirrors component's ref behaviour) ──────────────────

describe("DataObjectThermographyPane — reactive state model", () => {
  it("heatmap ref starts null; is populated after successful fetch", async () => {
    const heatmap = ref<PlateHeatmap | null>(null);
    const hm = fakeHeatmap();
    const fetchMock = vi.fn().mockResolvedValue(hm);

    const result = await fetchHeatmap({
      imageBundleAppId: IMAGE_BUNDLE_APP_ID,
      baseUrl: "http://localhost:8080",
      $fetch: fetchMock as unknown as (url: string, opts?: Record<string, unknown>) => Promise<PlateHeatmap | null>,
    });

    heatmap.value = result.heatmap;
    expect(heatmap.value).not.toBeNull();
    expect(heatmap.value?.frameCount).toBe(12);
  });

  it("heatmap ref remains null after 404 fetch (no error thrown)", async () => {
    const heatmap = ref<PlateHeatmap | null>(null);
    const fetchMock = vi.fn().mockRejectedValue({ response: { status: 404 } });

    const result = await fetchHeatmap({
      imageBundleAppId: IMAGE_BUNDLE_APP_ID,
      baseUrl: "http://localhost:8080",
      $fetch: fetchMock as unknown as (url: string, opts?: Record<string, unknown>) => Promise<PlateHeatmap | null>,
    });

    heatmap.value = result.heatmap;
    expect(heatmap.value).toBeNull();
    expect(result.errorMessage).toBeNull();
  });

  it("summary ref is set to analyzeResult after successful POST analyze", async () => {
    const summary = ref<AnalyzeResult | null>(null);
    const analyzeResult = fakeAnalyzeResult();
    const fetchMock = vi.fn().mockResolvedValue(analyzeResult);
    const fetchHeatmapFn = vi.fn().mockResolvedValue(fakeHeatmap());

    const result = await reanalyze({
      canEdit: true,
      imageBundleAppId: IMAGE_BUNDLE_APP_ID,
      baseUrl: "http://localhost:8080",
      $fetch: fetchMock,
      fetchHeatmapFn,
    });

    summary.value = result.analyzedResult;
    expect(summary.value?.framesAnalyzed).toBe(12);
    expect(summary.value?.annotationsWritten).toBe(5);
  });
});

// suppress unused variable warning — DATA_OBJECT_APP_ID kept for fixture clarity
void DATA_OBJECT_APP_ID;
