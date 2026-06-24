/**
 * TEMPLATE-ICONS-2-FE-RENDER-POINTS-EXPAND — unit tests for the
 * template-icon cache composable.
 *
 * We stub TemplatesApi.getTemplate so no network is involved.
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { ref, nextTick } from "vue";
import type { ShepardTemplate } from "@dlr-shepard/backend-client";

// --- stub the API composable before importing the composable under test ---
const mockGetTemplate = vi.fn<(args: { appId: string }) => Promise<ShepardTemplate>>();
vi.mock("~/composables/common/api/useV2ShepardApi", () => ({
  useV2ShepardApi: () => ref({ getTemplate: mockGetTemplate }),
}));

// import after mocking
const { useTemplateIconByAppId } = await import(
  "../../composables/useTemplateIconByAppId"
);

function makeTemplate(
  appId: string,
  iconKey: string | null = null,
): ShepardTemplate {
  return {
    appId,
    name: `Template ${appId}`,
    templateKind: "DATAOBJECT_RECIPE",
    version: 1,
    body: "{}",
    retired: false,
    iconKey,
  };
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("useTemplateIconByAppId — initial state (cache empty)", () => {
  it("returns the per-kind default before the template loads", () => {
    mockGetTemplate.mockReturnValue(new Promise(() => {})); // never resolves
    const ids = ref<(string | null)[]>(["tpl-001"]);
    const { iconFor } = useTemplateIconByAppId(ids);
    // cache is empty → falls back to DataObject default
    expect(iconFor("tpl-001", "DataObject")).toBe("mdi-circle-medium");
  });

  it("returns generic default when appId is null or undefined", () => {
    const ids = ref<(string | null | undefined)[]>([]);
    const { iconFor } = useTemplateIconByAppId(ids);
    expect(iconFor(null, "DataObject")).toBe("mdi-circle-medium");
    expect(iconFor(undefined, "DataObject")).toBe("mdi-circle-medium");
  });
});

describe("useTemplateIconByAppId — after template loads", () => {
  it("returns the template iconKey once the fetch resolves", async () => {
    mockGetTemplate.mockResolvedValue(makeTemplate("tpl-002", "mdi-layers"));
    const ids = ref<string[]>(["tpl-002"]);
    const { iconFor } = useTemplateIconByAppId(ids);

    await nextTick(); // watcher fires
    await nextTick(); // promise resolves

    expect(iconFor("tpl-002", "DataObject")).toBe("mdi-layers");
  });

  it("falls back to per-kind default when template has no iconKey", async () => {
    mockGetTemplate.mockResolvedValue(makeTemplate("tpl-003", null));
    const ids = ref<string[]>(["tpl-003"]);
    const { iconFor } = useTemplateIconByAppId(ids);

    await nextTick();
    await nextTick();

    expect(iconFor("tpl-003", "DataObject")).toBe("mdi-circle-medium");
  });
});

describe("useTemplateIconByAppId — deduplication", () => {
  it("fetches each unique appId only once even when listed multiple times", async () => {
    mockGetTemplate.mockResolvedValue(makeTemplate("tpl-004", "mdi-radar"));
    // same appId appears 3× (3 rows share the same template)
    const ids = ref<string[]>(["tpl-004", "tpl-004", "tpl-004"]);
    useTemplateIconByAppId(ids);

    await nextTick();
    await nextTick();

    expect(mockGetTemplate).toHaveBeenCalledTimes(1);
    expect(mockGetTemplate).toHaveBeenCalledWith({ appId: "tpl-004" });
  });

  it("fetches N distinct appIds and returns correct icons for each", async () => {
    mockGetTemplate
      .mockImplementation(async ({ appId }) => makeTemplate(appId, `mdi-${appId}`));

    const ids = ref<string[]>(["a", "b", "c"]);
    const { iconFor } = useTemplateIconByAppId(ids);

    await nextTick();
    await nextTick();

    expect(iconFor("a")).toBe("mdi-a");
    expect(iconFor("b")).toBe("mdi-b");
    expect(iconFor("c")).toBe("mdi-c");
    expect(mockGetTemplate).toHaveBeenCalledTimes(3);
  });
});

describe("useTemplateIconByAppId — fail-soft on API error", () => {
  it("returns per-kind default when fetch throws", async () => {
    mockGetTemplate.mockRejectedValue(new Error("network error"));
    const ids = ref<string[]>(["tpl-err"]);
    const { iconFor } = useTemplateIconByAppId(ids);

    await nextTick();
    await nextTick();

    expect(iconFor("tpl-err", "Collection")).toBe("mdi-folder-multiple");
  });
});

describe("useTemplateIconByAppId — reactive new appIds", () => {
  it("fetches a new appId when the list gains an entry", async () => {
    mockGetTemplate.mockImplementation(async ({ appId }) =>
      makeTemplate(appId, `icon-${appId}`),
    );

    const ids = ref<string[]>([]);
    const { iconFor } = useTemplateIconByAppId(ids);

    // Nothing fetched yet
    expect(mockGetTemplate).not.toHaveBeenCalled();

    // Add a new template appId
    ids.value = ["tpl-new"];
    await nextTick();
    await nextTick();

    expect(mockGetTemplate).toHaveBeenCalledTimes(1);
    expect(iconFor("tpl-new", "DataObject")).toBe("icon-tpl-new");
  });
});
