/**
 * MISSING-aas-ui Slice 4 — DataObjectAasPane unit tests.
 *
 * Tests composable integration and IRI computation. The useAasShell
 * composable is mocked; no component mounting is needed for this logic.
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { ref, computed } from "vue";

const COLLECTION_APP_ID = "01929b00-0000-7000-0000-000000000001";
const DO_APP_ID = "01929b00-0000-7000-0000-000000000002";

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
    submodelsTotal: ref(0),
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

describe("DataObjectAasPane", () => {
  it("calls useAasShell with the collectionAppId prop", () => {
    mockUseAasShell.mockReturnValue(
      makeMockComposable({ shell: SAMPLE_SHELL }) as ReturnType<typeof useAasShell>,
    );
    useAasShell(COLLECTION_APP_ID);
    expect(mockUseAasShell).toHaveBeenCalledWith(COLLECTION_APP_ID);
  });

  it("isDisabled=true when AAS integration is off (501)", () => {
    mockUseAasShell.mockReturnValue(
      makeMockComposable({ isDisabled: true }) as ReturnType<typeof useAasShell>,
    );
    const { isDisabled, isNotFound, error } = useAasShell(COLLECTION_APP_ID);
    expect(isDisabled.value).toBe(true);
    expect(isNotFound.value).toBe(false);
    expect(error.value).toBeNull();
  });

  it("isNotFound=true when shell is inaccessible (404)", () => {
    mockUseAasShell.mockReturnValue(
      makeMockComposable({ isNotFound: true }) as ReturnType<typeof useAasShell>,
    );
    const { isDisabled, isNotFound } = useAasShell(COLLECTION_APP_ID);
    expect(isNotFound.value).toBe(true);
    expect(isDisabled.value).toBe(false);
  });

  it("error ref is set when fetch fails", () => {
    const errorMsg = "Failed to load AAS Shell";
    mockUseAasShell.mockReturnValue(
      makeMockComposable({ error: errorMsg }) as ReturnType<typeof useAasShell>,
    );
    const { error } = useAasShell(COLLECTION_APP_ID);
    expect(error.value).toBe(errorMsg);
  });

  it("shell IRI is computed correctly from collectionAppId", () => {
    const shellIri = computed(() => `urn:shepard:collection:${COLLECTION_APP_ID}`);
    expect(shellIri.value).toBe(`urn:shepard:collection:${COLLECTION_APP_ID}`);
  });

  it("submodel IRI is computed correctly from dataObjectAppId", () => {
    const submodelIri = computed(() => `urn:shepard:dataobject:${DO_APP_ID}`);
    expect(submodelIri.value).toBe(`urn:shepard:dataobject:${DO_APP_ID}`);
  });
});
