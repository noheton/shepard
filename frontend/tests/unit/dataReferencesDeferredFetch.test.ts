/**
 * BUG-COLL-APPID-ROUTE-007-PAGE — useDataReferencesByDataObject deferred fetch.
 *
 * The DataObject detail page's route params are now the v2 appId (UUID). The v1
 * `/shepard/api/...` reference endpoints this composable calls need the NUMERIC
 * ids, which arrive only once the v2 entities load. The composable therefore
 * accepts `MaybeRefOrGetter<number | undefined>` and MUST NOT fire any v1 call
 * until both ids resolve — proving a UUID route param can never leak into a
 * numeric-id endpoint.
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { ref } from "vue";
import { useDataReferencesByDataObject } from "~/composables/context/useFetchDataReferences";

const getAllTimeseriesReferences = vi.fn();
const getAllFileReferences = vi.fn();
const getAllStructuredDataReferences = vi.fn();

// Each generated Api class resolves to a stub exposing only the methods this
// composable touches. useShepardApi returns a ref-wrapped instance.
vi.mock("~/composables/common/api/useShepardApi", () => ({
  useShepardApi: () =>
    ref({
      getAllTimeseriesReferences,
      getAllFileReferences,
      getAllStructuredDataReferences,
    }),
}));

// Lightweight stand-in objects for the generated Api class tokens — the
// composable only uses them as `useShepardApi(Api)` keys, which our mock
// ignores. An object literal avoids the no-extraneous-class lint rule.
vi.mock("@dlr-shepard/backend-client", () => ({
  TimeseriesReferenceApi: {},
  FileReferenceApi: {},
  StructuredDataContainerApi: {},
  StructuredDataReferenceApi: {},
  TimeseriesContainerApi: {},
  FileContainerApi: {},
  instanceOfFileReference: () => false,
  instanceOfTimeseriesReference: () => false,
}));

// onDataObjectUpdated touches a component-scoped event bus; stub to a no-op so
// the composable can run outside a component.
vi.stubGlobal("onDataObjectUpdated", () => {});
vi.stubGlobal("isDeleted", () => false);

const flush = () => new Promise<void>(r => setTimeout(r, 0));

beforeEach(() => {
  vi.clearAllMocks();
  getAllTimeseriesReferences.mockResolvedValue([]);
  getAllFileReferences.mockResolvedValue([]);
  getAllStructuredDataReferences.mockResolvedValue([]);
});

describe("useDataReferencesByDataObject — deferred numeric-id fetch", () => {
  it("does NOT fire a v1 call while the ids are undefined (UUID-route guard)", async () => {
    useDataReferencesByDataObject(undefined, undefined);
    await flush();
    expect(getAllTimeseriesReferences).not.toHaveBeenCalled();
    expect(getAllFileReferences).not.toHaveBeenCalled();
    expect(getAllStructuredDataReferences).not.toHaveBeenCalled();
  });

  it("fires the v1 calls with the numeric ids once they resolve", async () => {
    const collectionId = ref<number | undefined>(undefined);
    const dataObjectId = ref<number | undefined>(undefined);
    useDataReferencesByDataObject(collectionId, dataObjectId);
    await flush();
    expect(getAllTimeseriesReferences).not.toHaveBeenCalled();

    // The v2 entities load → numeric ids become available.
    collectionId.value = 2107;
    dataObjectId.value = 5500;
    await flush();

    expect(getAllTimeseriesReferences).toHaveBeenCalledWith({
      collectionId: 2107,
      dataObjectId: 5500,
    });
    expect(getAllFileReferences).toHaveBeenCalledWith({
      collectionId: 2107,
      dataObjectId: 5500,
    });
    expect(getAllStructuredDataReferences).toHaveBeenCalledWith({
      collectionId: 2107,
      dataObjectId: 5500,
    });
  });

  it("accepts plain numbers and fetches immediately", async () => {
    useDataReferencesByDataObject(10, 20);
    await flush();
    expect(getAllTimeseriesReferences).toHaveBeenCalledWith({
      collectionId: 10,
      dataObjectId: 20,
    });
  });
});
