/**
 * DB-OPT5 — usePagedDataObjects sends the panel-shaped ?fields= allow-list
 *
 * Verifies:
 *   1. The v2 API path passes a non-empty `fields` CSV
 *   2. The CSV includes every field the collection-detail panel reads
 *   3. Items hydrate without nulls on the trimmed response
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { ref } from "vue";
import { usePagedDataObjects } from "~/composables/context/usePagedDataObjects";
import { useShepardApi } from "~/composables/common/api/useShepardApi";
import { useV2ShepardApi } from "~/composables/common/api/useV2ShepardApi";

vi.mock("~/composables/common/api/useShepardApi", () => ({
  useShepardApi: vi.fn(),
}));
vi.mock("~/composables/common/api/useV2ShepardApi", () => ({
  useV2ShepardApi: vi.fn(),
}));

// V2-SWEEP-001-CLIENT-REGEN: the regenerated client dropped the hand-rolled
// `listDataObjectsWithCount`; the composable now calls `listDataObjectsRaw` and
// reads the total from the Content-Range response header. The mock returns an
// ApiResponse-shaped object ({ raw: { headers }, value() }).
const mockListDataObjectsRaw = vi.fn();
const mockGetAllDataObjects = vi.fn();

/** Build an ApiResponse-shaped mock with the given items + Content-Range header. */
function rawResponse(items: unknown[], total: number | null) {
  const headers = new Headers();
  if (total !== null) {
    const last = items.length > 0 ? items.length - 1 : -1;
    headers.set("Content-Range", `dataobjects 0-${last}/${total}`);
  }
  return {
    raw: { headers },
    value: () => Promise.resolve(items),
  };
}

beforeEach(() => {
  vi.clearAllMocks();
  (useShepardApi as ReturnType<typeof vi.fn>).mockReturnValue(
    ref({ getAllDataObjects: mockGetAllDataObjects }),
  );
  (useV2ShepardApi as ReturnType<typeof vi.fn>).mockReturnValue(
    ref({ listDataObjectsRaw: mockListDataObjectsRaw }),
  );
});

const flush = () => new Promise<void>(r => setTimeout(r, 0));

describe("DB-OPT5 — usePagedDataObjects sends panel-shaped ?fields=", () => {
  it("passes a non-empty fields CSV to the v2 API", async () => {
    mockListDataObjectsRaw.mockReturnValue(rawResponse([], 0));
    const collectionAppId = ref<string | null>("018f9c5a-7e26-7000-a000-000000000010");
    const name = ref("");
    const status = ref<"" | string>("");
    const page = ref(0);

    usePagedDataObjects({
      collectionId: 42,
      collectionAppId,
      name,
      status,
      page,
      pageSize: 25,
      includeTimeBounds: true,
    });

    await flush();

    expect(mockListDataObjectsRaw).toHaveBeenCalledTimes(1);
    const req = mockListDataObjectsRaw.mock.calls[0]![0];
    expect(req.fields).toBeTruthy();
    expect(typeof req.fields).toBe("string");
    expect(req.fields.length).toBeGreaterThan(20);
  });

  it("includes every field the CollectionDataObjectsPanel renders", async () => {
    mockListDataObjectsRaw.mockReturnValue(rawResponse([], 0));
    const collectionAppId = ref<string | null>("018f9c5a-7e26-7000-a000-000000000010");
    const name = ref("");
    const status = ref<"" | string>("");
    const page = ref(0);

    usePagedDataObjects({
      collectionId: 42,
      collectionAppId,
      name,
      status,
      page,
      pageSize: 25,
      includeTimeBounds: true,
    });

    await flush();

    const req = mockListDataObjectsRaw.mock.calls[0]![0];
    const fields = (req.fields as string).split(",").map(s => s.trim());
    // Identity / navigation
    expect(fields).toContain("id");
    expect(fields).toContain("appId");
    expect(fields).toContain("name");
    expect(fields).toContain("status");
    expect(fields).toContain("createdAt");
    // Length-based count derivations
    expect(fields).toContain("referenceIds");
    expect(fields).toContain("childrenIds");
    expect(fields).toContain("incomingIds");
    // v2 ref counts
    expect(fields).toContain("timeseriesCount");
    expect(fields).toContain("fileCount");
    expect(fields).toContain("structuredDataCount");
    // Time bounds (rendered when includeTimeBounds=true)
    expect(fields).toContain("timeBoundsStart");
    expect(fields).toContain("timeBoundsEnd");
  });

  it("populates items even when trimmed response omits description+attributes", async () => {
    // Simulate a default-trim response: no description, no attributes,
    // but every panel-rendered field present.
    const trimmedItem = {
      id: 84,
      appId: "018f9c5a-7e26-7000-a000-000000000020",
      name: "TR-001",
      status: "READY",
      createdAt: "2026-05-28T10:00:00.000Z",
      referenceIds: [1, 2, 3],
      childrenIds: [4],
      incomingIds: [],
      timeseriesCount: 3,
      fileCount: 1,
      structuredDataCount: 0,
      timeBoundsStart: 1_000_000_000,
      timeBoundsEnd: 9_000_000_000,
    };
    mockListDataObjectsRaw.mockReturnValue(rawResponse([trimmedItem], 1));

    const collectionAppId = ref<string | null>("018f9c5a-7e26-7000-a000-000000000010");
    const name = ref("");
    const status = ref<"" | string>("");
    const page = ref(0);

    const { items, totalItems, loading } = usePagedDataObjects({
      collectionId: 42,
      collectionAppId,
      name,
      status,
      page,
      pageSize: 25,
      includeTimeBounds: true,
    });

    await flush();

    expect(loading.value).toBe(false);
    expect(totalItems.value).toBe(1);
    expect(items.value.length).toBe(1);
    expect(items.value[0]!.id).toBe(84);
    expect(items.value[0]!.name).toBe("TR-001");
    expect(items.value[0]!.timeseriesCount).toBe(3);
    // The fact that description/attributes are absent on the wire must not break rendering.
  });
});
