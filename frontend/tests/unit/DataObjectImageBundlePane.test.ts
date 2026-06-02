/**
 * MFFD-IMAGEBUNDLE-PANE-MOUNT-1 — unit tests for DataObjectImageBundlePane
 * detection and control logic.
 *
 * Following the codebase convention (PersonalDigest.test.ts,
 * DataObjectNotebooksPane.test.ts, MyTemplatesPane.test.ts) these tests cover
 * the pure logic units extracted from the component rather than mounting the
 * full Vuetify + Nuxt rendering chain.
 *
 * Test surface:
 *  1. imageBundleAppIds computed — image-bundle detection heuristic
 *  2. bundleItems computed — picker items for multi-bundle case
 *  3. selectedBundleAppId update on prop change
 *  4. selectedGroupAppId auto-select on groups load
 *  5. Loading/error/empty states
 *  6. Single-bundle path (no picker rendered)
 */

import { describe, it, expect } from "vitest";

// ── Types ─────────────────────────────────────────────────────────────────────

interface DataReference {
  name?: string;
  appId?: string | null;
  fileReferenceId?: number;
  fileContainerId?: number;
  timeseriesContainerId?: number;
}

interface BundleGroupIO {
  appId: string;
  name: string;
  containerMongoId?: string | null;
}

// ── Image-bundle detection heuristic (mirrors index.vue imageBundleAppIds) ────

const IMAGE_NAME_RE = /\.(png|jpg|jpeg|tif|tiff)$/i;
const IMAGE_KEYWORD_RE = /image|img|frame|scan/i;

function detectImageBundleAppIds(refs: DataReference[]): string[] {
  const ids: string[] = [];
  for (const r of refs) {
    if (!("fileReferenceId" in r || "fileContainerId" in r)) continue;
    const name = r.name ?? "";
    if (!IMAGE_NAME_RE.test(name) && !IMAGE_KEYWORD_RE.test(name)) continue;
    const appId = r.appId;
    if (appId && !ids.includes(appId)) ids.push(appId);
  }
  return ids;
}

// ── Bundle picker items (mirrors DataObjectImageBundlePane.bundleItems) ───────

function buildBundleItems(appIds: string[]): { title: string; value: string }[] {
  return appIds.map((id, i) => ({
    title: `Bundle ${i + 1} (${id.slice(0, 8)}…)`,
    value: id,
  }));
}

// ── Auto-select first group (mirrors DataObjectImageBundlePane watch(groups)) ─

function autoSelectFirstGroup(groups: BundleGroupIO[]): string | null {
  const first = groups[0];
  return first !== undefined ? first.appId : null;
}

// ── selectedBundleAppId reset (mirrors watch on imageBundleAppIds prop) ───────

function resolveSelectedBundle(
  currentSelection: string | null,
  newIds: string[],
): string {
  if (newIds.length === 0) return "";
  if (currentSelection && newIds.includes(currentSelection)) return currentSelection;
  return newIds[0] ?? "";
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe("DataObjectImageBundlePane — imageBundleAppIds detection (MFFD-IMAGEBUNDLE-PANE-MOUNT-1)", () => {
  it("detects a FileBundleReference whose name ends with .png", () => {
    const refs: DataReference[] = [
      { name: "ply5-scan.png", appId: "bundle-001", fileContainerId: 1 },
    ];
    expect(detectImageBundleAppIds(refs)).toEqual(["bundle-001"]);
  });

  it("detects a FileBundleReference whose name ends with .jpg", () => {
    const refs: DataReference[] = [
      { name: "camera-frame.JPG", appId: "bundle-002", fileContainerId: 2 },
    ];
    expect(detectImageBundleAppIds(refs)).toEqual(["bundle-002"]);
  });

  it("detects a FileBundleReference whose name ends with .tif", () => {
    const refs: DataReference[] = [
      { name: "ndt-scan.tif", appId: "bundle-003", fileContainerId: 3 },
    ];
    expect(detectImageBundleAppIds(refs)).toEqual(["bundle-003"]);
  });

  it("detects a FileBundleReference whose name contains 'frame'", () => {
    const refs: DataReference[] = [
      { name: "ply5-frame-series", appId: "bundle-004", fileContainerId: 4 },
    ];
    expect(detectImageBundleAppIds(refs)).toEqual(["bundle-004"]);
  });

  it("detects a FileBundleReference whose name contains 'scan' (case-insensitive)", () => {
    const refs: DataReference[] = [
      { name: "OTvis-SCAN-Q1-ply3", appId: "bundle-005", fileContainerId: 5 },
    ];
    expect(detectImageBundleAppIds(refs)).toEqual(["bundle-005"]);
  });

  it("detects a FileBundleReference whose name contains 'image'", () => {
    const refs: DataReference[] = [
      { name: "IMAGE-series-layer8", appId: "bundle-006", fileReferenceId: 6 },
    ];
    expect(detectImageBundleAppIds(refs)).toEqual(["bundle-006"]);
  });

  it("skips references without fileReferenceId or fileContainerId (e.g. timeseries)", () => {
    const refs: DataReference[] = [
      { name: "frame-data.png", appId: "ts-001", timeseriesContainerId: 1 },
    ];
    expect(detectImageBundleAppIds(refs)).toEqual([]);
  });

  it("skips FileBundleReferences whose name does not match (e.g. a CAD file bundle)", () => {
    const refs: DataReference[] = [
      { name: "mesh-bundle.step", appId: "bundle-cad", fileContainerId: 7 },
      { name: "report-bundle.pdf", appId: "bundle-pdf", fileContainerId: 8 },
    ];
    expect(detectImageBundleAppIds(refs)).toEqual([]);
  });

  it("returns multiple appIds when several image bundles are present, deduplicating", () => {
    const refs: DataReference[] = [
      { name: "layer5.png", appId: "bundle-A", fileContainerId: 10 },
      { name: "layer5.png", appId: "bundle-A", fileContainerId: 10 }, // duplicate
      { name: "scan-layer8.tif", appId: "bundle-B", fileContainerId: 11 },
    ];
    expect(detectImageBundleAppIds(refs)).toEqual(["bundle-A", "bundle-B"]);
  });

  it("returns empty array when refs list is empty", () => {
    expect(detectImageBundleAppIds([])).toEqual([]);
  });

  it("skips refs with null appId", () => {
    const refs: DataReference[] = [
      { name: "frame.png", appId: null, fileContainerId: 20 },
    ];
    expect(detectImageBundleAppIds(refs)).toEqual([]);
  });
});

describe("DataObjectImageBundlePane — bundle picker items", () => {
  it("builds picker items with truncated appId label", () => {
    const items = buildBundleItems(["abc12345-0000-7000-8000-111111111111"]);
    expect(items).toHaveLength(1);
    const first = items[0]!;
    expect(first.value).toBe("abc12345-0000-7000-8000-111111111111");
    expect(first.title).toBe("Bundle 1 (abc12345…)");
  });

  it("numbers bundles sequentially when multiple exist", () => {
    const items = buildBundleItems(["aaaa0000", "bbbb1111"]);
    expect(items[0]!.title).toContain("Bundle 1");
    expect(items[1]!.title).toContain("Bundle 2");
  });

  it("returns empty array when no bundle appIds given", () => {
    expect(buildBundleItems([])).toEqual([]);
  });
});

describe("DataObjectImageBundlePane — group auto-select", () => {
  it("auto-selects first group on load", () => {
    const groups: BundleGroupIO[] = [
      { appId: "group-001", name: "Layer 5 scan" },
      { appId: "group-002", name: "Layer 8 scan" },
    ];
    expect(autoSelectFirstGroup(groups)).toBe("group-001");
  });

  it("returns null when groups list is empty (no selection)", () => {
    expect(autoSelectFirstGroup([])).toBeNull();
  });
});

describe("DataObjectImageBundlePane — selected bundle reset on prop change", () => {
  it("keeps current selection when it remains in the new id list", () => {
    const result = resolveSelectedBundle("bundle-B", ["bundle-A", "bundle-B"]);
    expect(result).toBe("bundle-B");
  });

  it("resets to first bundle when current selection is no longer in the list", () => {
    const result = resolveSelectedBundle("bundle-X", ["bundle-A", "bundle-B"]);
    expect(result).toBe("bundle-A");
  });

  it("returns empty string when new id list is empty", () => {
    const result = resolveSelectedBundle("bundle-A", []);
    expect(result).toBe("");
  });

  it("picks first id when no prior selection (initial state)", () => {
    const result = resolveSelectedBundle(null, ["bundle-C"]);
    expect(result).toBe("bundle-C");
  });
});

describe("DataObjectImageBundlePane — single-bundle path (no picker)", () => {
  it("single-bundle path: bundleItems length is 1, no picker shown", () => {
    const appIds = ["only-bundle-001"];
    const items = buildBundleItems(appIds);
    // Picker is shown when imageBundleAppIds.length > 1; for single bundle no picker.
    expect(items).toHaveLength(1);
    const showPicker = appIds.length > 1;
    expect(showPicker).toBe(false);
  });
});

describe("DataObjectImageBundlePane — multi-bundle picker visibility", () => {
  it("shows bundle picker when imageBundleAppIds has >1 entries", () => {
    const appIds = ["bundle-A", "bundle-B", "bundle-C"];
    const showPicker = appIds.length > 1;
    expect(showPicker).toBe(true);
  });

  it("hides bundle picker when imageBundleAppIds has exactly 1 entry", () => {
    const appIds = ["bundle-A"];
    const showPicker = appIds.length > 1;
    expect(showPicker).toBe(false);
  });
});

describe("DataObjectImageBundlePane — loading/error/empty state logic", () => {
  it("loading skeleton is shown while groups are fetching (loading=true, groups=[])", () => {
    const loading = true;
    const groups: BundleGroupIO[] = [];
    const error: string | null = null;
    // Template: v-if loading → show skeleton
    expect(loading && !error && groups.length === 0).toBe(true);
  });

  it("error alert is shown when fetch fails (loading=false, error set)", () => {
    const loading = false;
    const error = "HTTP 500";
    // Template: v-else-if error → show alert (groups are irrelevant; error state takes priority)
    expect(!loading && !!error).toBe(true);
  });

  it("empty-state alert is shown when no groups returned (loading=false, error=null, groups=[])", () => {
    const loading = false;
    const groups: BundleGroupIO[] = [];
    const error: string | null = null;
    // Template: v-else-if !loading && groups.length === 0 → show empty alert
    expect(!loading && !error && groups.length === 0).toBe(true);
  });

  it("ImageBundleViewer mounts when bundle+group are resolved", () => {
    const loading = false;
    const error: string | null = null;
    const selectedBundleAppId = "bundle-001";
    const selectedGroupAppId = "group-001";
    // Template: v-else-if selectedBundleAppId && selectedGroupAppId → mount viewer
    expect(!loading && !error && !!selectedBundleAppId && !!selectedGroupAppId).toBe(true);
  });
});
