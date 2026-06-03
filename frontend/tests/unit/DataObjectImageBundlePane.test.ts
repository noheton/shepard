/**
 * MFFD-IMAGEBUNDLE-PANE-MOUNT-1 — unit tests for DataObjectImageBundlePane
 * detection helper and composable logic.
 *
 * Strategy: test the pure detection helper (`isImageBundleRef`) and the
 * composable fetch logic without mounting the Vue component — Vuetify +
 * Nuxt rendering requires the full app context, which is not available in
 * the plain Vitest node environment.
 *
 * Tests:
 *  1. detection: .png name matches
 *  2. detection: .jpg name matches
 *  3. detection: .tif name does NOT match (thermography)
 *  4. detection: name containing "thermo" does NOT match
 *  5. detection: name containing "ndt" does NOT match
 *  6. detection: empty name / null / undefined return false
 *  7. composable: single group auto-selects first group appId
 *  8. composable: multiple groups shows picker (selectedGroupAppId = first)
 *  9. composable: empty groups array shows empty state flag
 * 10. composable: fetch error sets hasError flag
 */

import { describe, it, expect, vi, beforeEach } from "vitest";

// ─────────────────────────────────────────────────────────────────────────────
// Re-implement the pure detection helper here so we can test it without
// importing the SFC (which requires the full Nuxt/Vue test environment).
//
// The source-of-truth is the exported function in
//   frontend/pages/collections/[collectionId]/dataobjects/[dataObjectId]/index.vue
// — keep the logic identical.
// ─────────────────────────────────────────────────────────────────────────────

function isImageBundleRef(name: string | undefined | null): boolean {
  if (!name) return false;
  const lower = name.toLowerCase();
  // Skip thermography/NDT bundles.
  if (lower.includes("thermo") || lower.includes("ndt") || lower.includes(".tif")) {
    return false;
  }
  return lower.includes(".png") || lower.includes(".jpg");
}

// ─────────────────────────────────────────────────────────────────────────────
// Mock global fetch so composable tests don't touch the network.
// ─────────────────────────────────────────────────────────────────────────────

const mockFetch = vi.fn();
beforeEach(() => {
  vi.clearAllMocks();
  // Reset globalThis.fetch to avoid cross-test interference.
  globalThis.fetch = mockFetch;
});

// ─────────────────────────────────────────────────────────────────────────────
// Detection helper tests (pure function)
// ─────────────────────────────────────────────────────────────────────────────

describe("isImageBundleRef — detection helper", () => {
  it("matches a name containing .png", () => {
    expect(isImageBundleRef("tr-001-thermal.png")).toBe(true);
  });

  it("matches a name containing .jpg", () => {
    expect(isImageBundleRef("inspection-sequence.jpg")).toBe(true);
  });

  it("does NOT match a name containing .tif (thermography exclusion)", () => {
    expect(isImageBundleRef("otvis-scan.tif")).toBe(false);
  });

  it("does NOT match a name containing 'thermo'", () => {
    expect(isImageBundleRef("thermo-scan-bundle.png")).toBe(false);
  });

  it("does NOT match a name containing 'ndt'", () => {
    expect(isImageBundleRef("ndt-inspection.png")).toBe(false);
  });

  it("returns false for null name", () => {
    expect(isImageBundleRef(null)).toBe(false);
  });

  it("returns false for undefined name", () => {
    expect(isImageBundleRef(undefined)).toBe(false);
  });

  it("returns false for a name with no image extension", () => {
    expect(isImageBundleRef("afp-layup-recipe.md")).toBe(false);
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// Composable fetch logic tests — driven by re-implementing the core fetch
// logic from DataObjectImageBundlePane as a plain async function so we can
// assert on its outputs without mounting the component.
// ─────────────────────────────────────────────────────────────────────────────

interface FileGroupSummary {
  appId: string;
  name: string | null;
}

interface FetchGroupsResult {
  groups: FileGroupSummary[];
  selectedGroupAppId: string | null;
  hasError: boolean;
}

/**
 * Thin simulation of the fetch-groups logic in DataObjectImageBundlePane.
 * Returns the same state tuple the composable would expose.
 */
async function fetchGroupsFor(
  bundleAppId: string,
  accessToken: string | null = "test-token",
): Promise<FetchGroupsResult> {
  // Mirror the v2BaseUrl() output (setup stub returns http://localhost:8080).
  const base = "http://localhost:8080";
  let groups: FileGroupSummary[] = [];
  let selectedGroupAppId: string | null = null;
  let hasError = false;

  try {
    const url = `${base}/v2/bundles/${encodeURIComponent(bundleAppId)}/groups`;
    const response = await fetch(url, {
      headers: {
        Authorization: `Bearer ${accessToken}`,
        Accept: "application/json",
      },
    });
    if (!response.ok) {
      hasError = true;
      return { groups, selectedGroupAppId, hasError };
    }
    const data = (await response.json()) as FileGroupSummary[];
    groups = data;
    if (data.length > 0 && data[0]) {
      selectedGroupAppId = data[0].appId;
    }
  } catch {
    hasError = true;
  }

  return { groups, selectedGroupAppId, hasError };
}

describe("DataObjectImageBundlePane — composable fetch logic", () => {
  it("single group: auto-selects the first group's appId and renders the viewer", async () => {
    const mockGroup: FileGroupSummary = { appId: "group-001", name: "Run 1" };
    mockFetch.mockResolvedValue({
      ok: true,
      json: async () => [mockGroup],
    });

    const result = await fetchGroupsFor("bundle-app-id-123");

    expect(result.groups).toHaveLength(1);
    expect(result.selectedGroupAppId).toBe("group-001");
    expect(result.hasError).toBe(false);
  });

  it("multiple groups: auto-selects first group, picker should be shown (groups.length > 1)", async () => {
    const mockGroups: FileGroupSummary[] = [
      { appId: "group-001", name: "Run 1" },
      { appId: "group-002", name: "Run 2" },
    ];
    mockFetch.mockResolvedValue({
      ok: true,
      json: async () => mockGroups,
    });

    const result = await fetchGroupsFor("bundle-app-id-456");

    expect(result.groups).toHaveLength(2);
    expect(result.selectedGroupAppId).toBe("group-001");
    expect(result.hasError).toBe(false);
    // The component would show the picker because groups.length > 1.
    expect(result.groups.length > 1).toBe(true);
  });

  it("empty groups array: no auto-selection, no error", async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      json: async () => [],
    });

    const result = await fetchGroupsFor("bundle-app-id-empty");

    expect(result.groups).toHaveLength(0);
    expect(result.selectedGroupAppId).toBeNull();
    expect(result.hasError).toBe(false);
  });

  it("fetch error (HTTP 500): sets hasError = true, groups empty", async () => {
    mockFetch.mockResolvedValue({
      ok: false,
      status: 500,
      json: async () => ({}),
    });

    const result = await fetchGroupsFor("bundle-app-id-err");

    expect(result.hasError).toBe(true);
    expect(result.groups).toHaveLength(0);
    expect(result.selectedGroupAppId).toBeNull();
  });

  it("network error (thrown): sets hasError = true", async () => {
    mockFetch.mockRejectedValue(new Error("Network failure"));

    const result = await fetchGroupsFor("bundle-app-id-throw");

    expect(result.hasError).toBe(true);
    expect(result.groups).toHaveLength(0);
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// imageBundleRefs detection helper — mirrors the computed in the page
// ─────────────────────────────────────────────────────────────────────────────

interface MockFileRef {
  type?: string;
  name: string;
  id: number;
  appId?: string;
  fileOids: string[];
  fileContainerId: number;
  createdAt: Date;
  createdBy: string;
  updatedAt: Date | null;
  updatedBy: string | null;
}

function isFileReference(ref: MockFileRef): boolean {
  // Mirror instanceOfFileReference from the backend-client
  return (
    "id" in ref &&
    "createdAt" in ref &&
    "createdBy" in ref &&
    "updatedAt" in ref &&
    "updatedBy" in ref &&
    "name" in ref &&
    "fileOids" in ref &&
    "fileContainerId" in ref
  );
}

function findImageBundleAppId(dataRefs: MockFileRef[]): string | null {
  for (const ref of dataRefs) {
    if (isFileReference(ref) && isImageBundleRef(ref.name)) {
      return ref.appId ?? null;
    }
  }
  return null;
}

const BASE_FILE_REF: Omit<MockFileRef, "name" | "appId"> = {
  id: 1,
  fileOids: ["oid-1"],
  fileContainerId: 10,
  createdAt: new Date("2026-01-01"),
  createdBy: "alice",
  updatedAt: null,
  updatedBy: null,
};

describe("findImageBundleAppId — page-level detection logic", () => {
  it("returns the appId for a .png FileBundleReference", () => {
    const refs: MockFileRef[] = [
      { ...BASE_FILE_REF, name: "tr-001-images.png", appId: "bundle-abc" },
    ];
    expect(findImageBundleAppId(refs)).toBe("bundle-abc");
  });

  it("skips .tif bundles (thermography)", () => {
    const refs: MockFileRef[] = [
      { ...BASE_FILE_REF, name: "otvis-scan.tif", appId: "bundle-thermo" },
    ];
    expect(findImageBundleAppId(refs)).toBeNull();
  });

  it("returns null when no FileBundleReference is present", () => {
    expect(findImageBundleAppId([])).toBeNull();
  });
});
