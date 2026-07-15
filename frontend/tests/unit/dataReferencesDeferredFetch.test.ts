/**
 * REFS-V2-PANELS — useDataReferencesByDataObject appId-keyed v2 fetch.
 *
 * The DataObject detail page's route params are the v2 appId (UUID). The former
 * v1 reference endpoints required the NUMERIC ids, which the v2 detail responses
 * deliberately suppress (@JsonIgnoreProperties({"id"})) — so the v1 fetch never
 * fired and the reference panels rendered empty. The composable now raw-fetches
 * the unified v2 endpoint
 *   GET /v2/references?kind={timeseries|bundle|structured-data}&dataObjectAppId={appId}
 * keyed by the appId string, flattens the v2 payload onto the v1-shaped
 * DataReference, and resolves container meta via GET /v2/containers/{appId}.
 *
 * This test proves: (1) no fetch fires while the appId is undefined; (2) once
 * the appId resolves, the v2 list + container endpoints are called with the
 * appId (never a numeric id); (3) a timeseries element reaches `dataReferences`
 * with its container name resolved and its payload flattened to the top level.
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { ref } from "vue";
import { useDataReferencesByDataObject } from "~/composables/context/useFetchDataReferences";

const DO_APP_ID = "019ed612-1f0d-73ab-b703-3ed0647dbbfc";
const TS_CONTAINER_APP_ID = "019ede2a-1111-2222-3333-444455556666";

// One timeseries ReferenceV2IO as returned live by GET /v2/references.
const TS_REFERENCE_IO = {
  name: "TPS timeseries — Track_100__Run_21688_",
  appId: "019edf1b-aaaa-bbbb-cccc-ddddeeeeffff",
  kind: "timeseries",
  payload: {
    timeseriesContainerId: 221633,
    timeseriesContainerAppId: TS_CONTAINER_APP_ID,
    start: 1670493034676000000,
    end: 1670493071720000000,
    timeseries: [
      {
        measurement: "mm",
        device: "R20",
        location: "MFZ",
        symbolicName: "VC_CutLength3",
        field: "value",
      },
    ],
    qualityScore: 0.91,
    lastScoredAt: "2022-12-08T11:51:11.720Z",
  },
};

const fetchMock = vi.fn();
vi.stubGlobal("fetch", fetchMock);

// useAuth → a session with an access token (drives the Authorization header).
vi.stubGlobal("useAuth", () => ({
  data: ref({ accessToken: "test-token" }),
}));

// v2BaseUrl is imported from createV2Container; stub the module so the composable
// resolves a deterministic base URL without Nuxt runtime config.
vi.mock("~/composables/container/createV2Container", () => ({
  v2BaseUrl: () => "https://example.test",
}));

// onDataObjectUpdated touches a component-scoped event bus; stub to a no-op so
// the composable can run outside a component. handleError is a global helper.
vi.stubGlobal("onDataObjectUpdated", () => {});
vi.stubGlobal("handleError", () => {});

const flush = () => new Promise<void>(r => setTimeout(r, 0));

/** Route a fetch call to its canned response by URL substring. */
function routeFetch(url: string): Response {
  if (url.includes("/v2/references")) {
    if (url.includes("kind=timeseries")) {
      return jsonResponse([TS_REFERENCE_IO]);
    }
    // bundle + structured-data: empty for this fixture
    return jsonResponse([]);
  }
  if (url.includes(`/v2/containers/${TS_CONTAINER_APP_ID}`)) {
    return jsonResponse({ name: "MFFD tapelaying TS" });
  }
  return jsonResponse([]);
}

function jsonResponse(body: unknown): Response {
  return {
    ok: true,
    status: 200,
    json: () => Promise.resolve(body),
  } as unknown as Response;
}

beforeEach(() => {
  vi.clearAllMocks();
  fetchMock.mockImplementation((url: string) =>
    Promise.resolve(routeFetch(url)),
  );
});

describe("useDataReferencesByDataObject — appId-keyed v2 fetch", () => {
  it("does NOT fetch while the appId is undefined", async () => {
    useDataReferencesByDataObject(undefined);
    await flush();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("fires the v2 list calls keyed by appId once it resolves", async () => {
    const appId = ref<string | undefined>(undefined);
    useDataReferencesByDataObject(appId);
    await flush();
    expect(fetchMock).not.toHaveBeenCalled();

    appId.value = DO_APP_ID;
    await flush();
    await flush();

    const calledUrls = fetchMock.mock.calls.map(c => c[0] as string);
    // All three kinds requested, each keyed by the DataObject appId.
    expect(
      calledUrls.some(
        u =>
          u.includes("kind=timeseries") &&
          u.includes(`dataObjectAppId=${DO_APP_ID}`),
      ),
    ).toBe(true);
    expect(calledUrls.some(u => u.includes("kind=bundle"))).toBe(true);
    expect(calledUrls.some(u => u.includes("kind=structured-data"))).toBe(true);
    // The container name was resolved by appId, never a numeric id.
    expect(
      calledUrls.some(u => u.includes(`/v2/containers/${TS_CONTAINER_APP_ID}`)),
    ).toBe(true);
  });

  it("flattens the v2 timeseries payload and resolves the container name", async () => {
    const { dataReferences } = useDataReferencesByDataObject(DO_APP_ID);
    await flush();
    await flush();

    expect(dataReferences.value).toBeDefined();
    expect(dataReferences.value!.length).toBe(1);
    const ref0 = dataReferences.value![0] as unknown as Record<string, unknown>;
    // Payload flattened to the top level (v1-shaped contract).
    expect(ref0.timeseriesContainerId).toBe(221633);
    expect(Array.isArray(ref0.timeseries)).toBe(true);
    expect(ref0.start).toBe(1670493034676000000);
    expect(ref0.appId).toBe("019edf1b-aaaa-bbbb-cccc-ddddeeeeffff");
    // Container meta resolved via /v2/containers/{appId}.
    expect(ref0.referencedContainerAvailability).toBe("available");
    expect(ref0.referencedContainerName).toBe("MFFD tapelaying TS");
  });

  it("accepts a plain appId string and fetches immediately", async () => {
    useDataReferencesByDataObject(DO_APP_ID);
    await flush();
    expect(fetchMock).toHaveBeenCalled();
    const urls = fetchMock.mock.calls.map(c => c[0] as string);
    expect(urls.some(u => u.includes(`dataObjectAppId=${DO_APP_ID}`))).toBe(true);
  });
});
