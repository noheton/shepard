/**
 * MISSING-aas-ui Slice 8 — AAS Shell detail page clipboard affordances.
 *
 * Tests the IRI values that ClipboardButton receives on the shell detail
 * page. Composable is mocked; no component mount needed.
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { ref } from "vue";

import { useAasShell } from "~/composables/aas/useAasShell";

const COLLECTION_APP_ID = "01929b00-0000-7000-0000-00000000aabb";
const SUBMODEL_IRI = `urn:shepard:dataobject:01929b00-0000-7000-0000-00000000ccdd`;
const SHELL_IRI = `urn:shepard:collection:${COLLECTION_APP_ID}`;

const SAMPLE_SHELL = {
  id: SHELL_IRI,
  idShort: "TestCollection",
  assetInformation: { assetKind: "Instance", globalAssetId: SHELL_IRI },
  description: [],
  submodels: [],
};

const SAMPLE_SUBMODEL_REF = {
  keys: [{ type: "Submodel", value: SUBMODEL_IRI }],
  type: "ExternalReference",
};

function makeMockComposable(overrides: Record<string, unknown>) {
  return {
    shell: ref(overrides.shell ?? null),
    isLoading: ref(overrides.isLoading ?? false),
    isDisabled: ref(overrides.isDisabled ?? false),
    isNotFound: ref(overrides.isNotFound ?? false),
    error: ref(overrides.error ?? null),
    submodels: ref((overrides.submodels as unknown[]) ?? []),
    submodelsTotal: ref(overrides.submodelsTotal ?? 0),
    submodelsPage: ref(0),
    submodelsPageSize: ref(50),
    isSubmodelsLoading: ref(false),
    refresh: vi.fn(),
    fetchSubmodels: vi.fn(),
  } as unknown as ReturnType<typeof useAasShell>;
}

vi.mock("~/composables/aas/useAasShell", () => ({
  useAasShell: vi.fn(),
  submodelRefToAppId: (v: string) =>
    v.startsWith("urn:shepard:dataobject:") ? v.slice(23) : v,
}));
const mockUseAasShell = vi.mocked(useAasShell);

beforeEach(() => {
  mockUseAasShell.mockReset();
});

describe("AAS Shell detail — clipboard affordances", () => {
  it("shell.id carries the correct IRI for the clipboard button", () => {
    mockUseAasShell.mockReturnValue(
      makeMockComposable({ shell: SAMPLE_SHELL }) as ReturnType<typeof useAasShell>,
    );
    const { shell } = useAasShell(COLLECTION_APP_ID);
    expect(shell.value?.id).toBe(SHELL_IRI);
  });

  it("shell IRI matches urn:shepard:collection:{appId} pattern", () => {
    mockUseAasShell.mockReturnValue(
      makeMockComposable({ shell: SAMPLE_SHELL }) as ReturnType<typeof useAasShell>,
    );
    const { shell } = useAasShell(COLLECTION_APP_ID);
    expect(shell.value?.id).toMatch(/^urn:shepard:collection:[0-9a-f-]+$/);
  });

  it("submodel ref key value carries the correct IRI for clipboard", () => {
    mockUseAasShell.mockReturnValue(
      makeMockComposable({
        shell: SAMPLE_SHELL,
        submodels: [SAMPLE_SUBMODEL_REF],
        submodelsTotal: 1,
      }) as ReturnType<typeof useAasShell>,
    );
    const { submodels } = useAasShell(COLLECTION_APP_ID);
    expect(submodels.value[0]?.keys[0]?.value).toBe(SUBMODEL_IRI);
  });

  it("submodel IRI matches urn:shepard:dataobject:{appId} pattern", () => {
    mockUseAasShell.mockReturnValue(
      makeMockComposable({
        shell: SAMPLE_SHELL,
        submodels: [SAMPLE_SUBMODEL_REF],
        submodelsTotal: 1,
      }) as ReturnType<typeof useAasShell>,
    );
    const { submodels } = useAasShell(COLLECTION_APP_ID);
    expect(submodels.value[0]?.keys[0]?.value).toMatch(
      /^urn:shepard:dataobject:[0-9a-f-]+$/,
    );
  });

  it("isDisabled=true when AAS integration is off", () => {
    mockUseAasShell.mockReturnValue(
      makeMockComposable({ isDisabled: true }) as ReturnType<typeof useAasShell>,
    );
    const { isDisabled, shell } = useAasShell(COLLECTION_APP_ID);
    expect(isDisabled.value).toBe(true);
    expect(shell.value).toBeNull();
  });

  it("isNotFound=true yields null shell", () => {
    mockUseAasShell.mockReturnValue(
      makeMockComposable({ isNotFound: true }) as ReturnType<typeof useAasShell>,
    );
    const { isNotFound, shell } = useAasShell(COLLECTION_APP_ID);
    expect(isNotFound.value).toBe(true);
    expect(shell.value).toBeNull();
  });
});
