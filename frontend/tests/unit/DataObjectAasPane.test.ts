/**
 * MISSING-aas-ui Slice 4 — DataObjectAasPane unit tests.
 *
 * Tests the three render states: disabled (501), not-found (404), and
 * success (shell loaded). The useAasShell composable is mocked so no
 * real network calls are made.
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { ref } from "vue";
import { mountSuspended } from "@nuxt/test-utils/runtime";
import DataObjectAasPane from "~/components/context/aas/DataObjectAasPane.vue";

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
  it("shows disabled alert when AAS integration is off (501)", async () => {
    mockUseAasShell.mockReturnValue(
      makeMockComposable({ isDisabled: true }) as ReturnType<typeof useAasShell>,
    );
    const wrapper = await mountSuspended(DataObjectAasPane, {
      props: { collectionAppId: COLLECTION_APP_ID, dataObjectAppId: DO_APP_ID },
    });
    expect(wrapper.text()).toContain("AAS integration is disabled");
    expect(wrapper.text()).not.toContain("urn:shepard:");
  });

  it("shows not-found alert when shell is inaccessible (404)", async () => {
    mockUseAasShell.mockReturnValue(
      makeMockComposable({ isNotFound: true }) as ReturnType<typeof useAasShell>,
    );
    const wrapper = await mountSuspended(DataObjectAasPane, {
      props: { collectionAppId: COLLECTION_APP_ID, dataObjectAppId: DO_APP_ID },
    });
    expect(wrapper.text()).toContain("not currently accessible");
  });

  it("shows error alert on fetch error", async () => {
    mockUseAasShell.mockReturnValue(
      makeMockComposable({
        error: "Failed to load AAS Shell",
      }) as ReturnType<typeof useAasShell>,
    );
    const wrapper = await mountSuspended(DataObjectAasPane, {
      props: { collectionAppId: COLLECTION_APP_ID, dataObjectAppId: DO_APP_ID },
    });
    expect(wrapper.text()).toContain("Failed to load AAS Shell");
  });

  it("renders shell IRI and submodel IRI when shell loads", async () => {
    mockUseAasShell.mockReturnValue(
      makeMockComposable({ shell: SAMPLE_SHELL }) as ReturnType<typeof useAasShell>,
    );
    const wrapper = await mountSuspended(DataObjectAasPane, {
      props: { collectionAppId: COLLECTION_APP_ID, dataObjectAppId: DO_APP_ID },
    });
    expect(wrapper.text()).toContain(
      `urn:shepard:collection:${COLLECTION_APP_ID}`,
    );
    expect(wrapper.text()).toContain(
      `urn:shepard:dataobject:${DO_APP_ID}`,
    );
  });

  it("renders shell idShort alongside the shell IRI", async () => {
    mockUseAasShell.mockReturnValue(
      makeMockComposable({ shell: SAMPLE_SHELL }) as ReturnType<typeof useAasShell>,
    );
    const wrapper = await mountSuspended(DataObjectAasPane, {
      props: { collectionAppId: COLLECTION_APP_ID, dataObjectAppId: DO_APP_ID },
    });
    expect(wrapper.text()).toContain("TestCollection");
  });

  it("renders Browse AAS Shells link when shell loaded", async () => {
    mockUseAasShell.mockReturnValue(
      makeMockComposable({ shell: SAMPLE_SHELL }) as ReturnType<typeof useAasShell>,
    );
    const wrapper = await mountSuspended(DataObjectAasPane, {
      props: { collectionAppId: COLLECTION_APP_ID, dataObjectAppId: DO_APP_ID },
    });
    expect(wrapper.text()).toContain("Browse AAS Shells");
  });

  it("passes collectionAppId to useAasShell", async () => {
    mockUseAasShell.mockReturnValue(
      makeMockComposable({ shell: SAMPLE_SHELL }) as ReturnType<typeof useAasShell>,
    );
    await mountSuspended(DataObjectAasPane, {
      props: { collectionAppId: COLLECTION_APP_ID, dataObjectAppId: DO_APP_ID },
    });
    expect(mockUseAasShell).toHaveBeenCalledWith(COLLECTION_APP_ID);
  });
});
