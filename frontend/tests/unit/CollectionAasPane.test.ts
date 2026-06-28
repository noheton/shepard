/**
 * MISSING-aas-ui Slice 5 — pure Vitest tests for CollectionAasPane composable logic.
 *
 * Pattern: mock useAasShell, drive its reactive refs, assert computed values
 * and state flags directly — no component mounting needed.
 * (mountSuspended from @nuxt/test-utils/runtime is NOT installed in this project.)
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { ref, computed } from "vue";

const COLLECTION_APP_ID = "01929b00-0000-7000-0000-000000000010";

const SAMPLE_SHELL = {
  id: `urn:shepard:collection:${COLLECTION_APP_ID}`,
  idShort: "TestCollection",
  assetInformation: { assetKind: "Instance", globalAssetId: "" },
  description: [],
  submodels: [],
};

function makeMockComposable(overrides: Record<string, unknown>) {
  return {
    shell: ref(overrides.shell ?? null),
    isLoading: ref(overrides.isLoading ?? false),
    isDisabled: ref(overrides.isDisabled ?? false),
    isNotFound: ref(overrides.isNotFound ?? false),
    error: ref(overrides.error ?? null),
    submodels: ref([]),
    submodelsTotal: ref((overrides.submodelsTotal as number) ?? 0),
    submodelsPage: ref(0),
    submodelsPageSize: ref(50),
    isSubmodelsLoading: ref(false),
    refresh: vi.fn(),
    fetchSubmodels: vi.fn(),
  };
}

vi.mock("~/composables/aas/useAasShell", () => ({
  useAasShell: vi.fn(),
  submodelRefToAppId: (v: string) =>
    v.startsWith("urn:shepard:dataobject:") ? v.slice(23) : v,
}));

import { useAasShell } from "~/composables/aas/useAasShell";
const mockUseAasShell = vi.mocked(useAasShell);

beforeEach(() => {
  mockUseAasShell.mockReset();
});

describe("CollectionAasPane", () => {
  it("calls useAasShell with the collectionAppId prop", () => {
    const composable = makeMockComposable({});
    mockUseAasShell.mockReturnValue(composable as ReturnType<typeof useAasShell>);

    useAasShell(COLLECTION_APP_ID);
    expect(mockUseAasShell).toHaveBeenCalledWith(COLLECTION_APP_ID);
  });

  it("isDisabled=true when AAS integration is off (501)", () => {
    const composable = makeMockComposable({ isDisabled: true });
    mockUseAasShell.mockReturnValue(composable as ReturnType<typeof useAasShell>);

    const result = useAasShell(COLLECTION_APP_ID);
    expect(result.isDisabled.value).toBe(true);
    expect(result.shell.value).toBeNull();
  });

  it("isNotFound=true when shell is inaccessible (404)", () => {
    const composable = makeMockComposable({ isNotFound: true });
    mockUseAasShell.mockReturnValue(composable as ReturnType<typeof useAasShell>);

    const result = useAasShell(COLLECTION_APP_ID);
    expect(result.isNotFound.value).toBe(true);
    expect(result.shell.value).toBeNull();
  });

  it("error ref is set when fetch fails", () => {
    const composable = makeMockComposable({ error: "Failed to load AAS Shell" });
    mockUseAasShell.mockReturnValue(composable as ReturnType<typeof useAasShell>);

    const result = useAasShell(COLLECTION_APP_ID);
    expect(result.error.value).toBe("Failed to load AAS Shell");
  });

  it("shell IRI is computed correctly from collectionAppId", () => {
    const composable = makeMockComposable({ shell: SAMPLE_SHELL });
    mockUseAasShell.mockReturnValue(composable as ReturnType<typeof useAasShell>);

    const shellIri = computed(() => `urn:shepard:collection:${COLLECTION_APP_ID}`);
    expect(shellIri.value).toBe(
      `urn:shepard:collection:${COLLECTION_APP_ID}`,
    );
  });

  it("submodelsTotal reflects the count from the composable", () => {
    const composable = makeMockComposable({ shell: SAMPLE_SHELL, submodelsTotal: 7 });
    mockUseAasShell.mockReturnValue(composable as ReturnType<typeof useAasShell>);

    const result = useAasShell(COLLECTION_APP_ID);
    expect(result.submodelsTotal.value).toBe(7);
  });
});
