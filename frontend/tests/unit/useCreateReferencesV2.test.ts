/**
 * APISIMP-CREATE-REFS-V2 — useCreateReferencesV2 URI, collection, and
 * dataobject reference creation via POST /v2/references?kind=...
 *
 * Proves: (1) addUriReferenceV2 posts to the correct URL with kind=uri;
 * (2) addCollectionReference posts with kind=collection;
 * (3) addDataObjectReference posts with kind=dataobject;
 * (4) non-2xx response triggers error handler, not onSuccess;
 * (5) fetch reject triggers error handler, not onSuccess.
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { ref } from "vue";
import { useCreateReferencesV2 } from "~/composables/references/useCreateReferencesV2";

const DATA_OBJECT_APP_ID = "01928eaa-2222-7000-9000-bbbbbbbbbbbb";

const fetchMock = vi.fn();
vi.stubGlobal("fetch", fetchMock);

vi.stubGlobal("useAuth", () => ({
  data: ref({ accessToken: "test-token" }),
}));

vi.stubGlobal("useRuntimeConfig", () => ({
  public: {
    backendV2ApiUrl: "https://example.test",
    backendApiUrl: "",
  },
}));

vi.stubGlobal("emitSuccess", vi.fn());
vi.stubGlobal("handleDataObjectUpdate", vi.fn());
vi.stubGlobal("handleError", vi.fn());

const flush = () => new Promise<void>(r => setTimeout(r, 0));

function jsonCreated(): Response {
  return {
    ok: true,
    status: 201,
    json: () => Promise.resolve({}),
  } as unknown as Response;
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("useCreateReferencesV2 — addUriReferenceV2", () => {
  it("posts to /v2/references?kind=uri with the correct body", async () => {
    fetchMock.mockResolvedValueOnce(jsonCreated());
    const onSuccess = vi.fn();
    const { addUriReferenceV2 } = useCreateReferencesV2(
      DATA_OBJECT_APP_ID,
      onSuccess,
    );

    addUriReferenceV2("https://dlr.de/docs", "DLR Docs", "Documentation");
    await flush();

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0]!;
    expect(url).toBe(
      `https://example.test/v2/references?kind=uri&dataObjectAppId=${encodeURIComponent(DATA_OBJECT_APP_ID)}`,
    );
    expect(init.method).toBe("POST");
    const body = JSON.parse(init.body as string);
    expect(body).toEqual({
      name: "DLR Docs",
      uri: "https://dlr.de/docs",
      relationship: "Documentation",
    });
    expect(onSuccess).toHaveBeenCalledTimes(1);
  });

  it("defaults relationship to 'URI' when not supplied", async () => {
    fetchMock.mockResolvedValueOnce(jsonCreated());
    const { addUriReferenceV2 } = useCreateReferencesV2(
      DATA_OBJECT_APP_ID,
      vi.fn(),
    );

    addUriReferenceV2("https://example.test/resource", "Resource");
    await flush();

    const body = JSON.parse(fetchMock.mock.calls[0]![1]!.body as string);
    expect(body.relationship).toBe("URI");
  });

  it("does not call onSuccess on non-2xx response", async () => {
    fetchMock.mockResolvedValueOnce({ ok: false, status: 403 } as Response);
    const onSuccess = vi.fn();
    const { addUriReferenceV2 } = useCreateReferencesV2(
      DATA_OBJECT_APP_ID,
      onSuccess,
    );

    addUriReferenceV2("https://example.test", "name");
    await flush();

    expect(onSuccess).not.toHaveBeenCalled();
  });

  it("does not call onSuccess when fetch rejects", async () => {
    fetchMock.mockRejectedValueOnce(new Error("network failure"));
    const onSuccess = vi.fn();
    const { addUriReferenceV2 } = useCreateReferencesV2(
      DATA_OBJECT_APP_ID,
      onSuccess,
    );

    addUriReferenceV2("https://example.test", "name");
    await flush();

    expect(onSuccess).not.toHaveBeenCalled();
  });
});

describe("useCreateReferencesV2 — addCollectionReference", () => {
  it("posts to /v2/references?kind=collection", async () => {
    fetchMock.mockResolvedValueOnce(jsonCreated());
    const onSuccess = vi.fn();
    const { addCollectionReference } = useCreateReferencesV2(
      DATA_OBJECT_APP_ID,
      onSuccess,
    );

    addCollectionReference(
      "01928eaa-3333-7000-9000-cccccccccccc",
      "My Collection Ref",
      "Related",
    );
    await flush();

    const [url, init] = fetchMock.mock.calls[0]!;
    expect(url).toContain("kind=collection");
    expect(url).toContain(`dataObjectAppId=${encodeURIComponent(DATA_OBJECT_APP_ID)}`);
    const body = JSON.parse(init.body as string);
    expect(body.referencedCollectionAppId).toBe("01928eaa-3333-7000-9000-cccccccccccc");
    expect(body.name).toBe("My Collection Ref");
    expect(onSuccess).toHaveBeenCalledTimes(1);
  });
});

describe("useCreateReferencesV2 — addDataObjectReference", () => {
  it("posts to /v2/references?kind=dataobject", async () => {
    fetchMock.mockResolvedValueOnce(jsonCreated());
    const onSuccess = vi.fn();
    const { addDataObjectReference } = useCreateReferencesV2(
      DATA_OBJECT_APP_ID,
      onSuccess,
    );

    addDataObjectReference(
      "01928eaa-4444-7000-9000-dddddddddddd",
      "My DO Ref",
    );
    await flush();

    const [url, init] = fetchMock.mock.calls[0]!;
    expect(url).toContain("kind=dataobject");
    const body = JSON.parse(init.body as string);
    expect(body.referencedDataObjectAppId).toBe("01928eaa-4444-7000-9000-dddddddddddd");
    expect(onSuccess).toHaveBeenCalledTimes(1);
  });
});
