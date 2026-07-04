/**
 * APISIMP-STRUCTURED-DATA-KIND slice 3 — unit tests for
 * `useCreateStructuredDataReference`.
 *
 * Verifies:
 *   - Happy path (metadata-only, no jsonPayload) hits
 *     POST /v2/references?kind=structured-data and returns the created IO.
 *   - With jsonPayload, a second PUT /v2/references/{appId}/content call is made.
 *   - HTTP errors (400, 401, 403, 404) surface the right error message and return null.
 *   - Network exceptions are caught, error is set, null returned.
 *   - If the upload step fails, the reference is still returned (non-fatal).
 *   - loading flag transitions correctly.
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { ref } from "vue";
import {
  useCreateStructuredDataReference,
  type CreateStructuredDataReferenceRequest,
} from "~/composables/references/useCreateStructuredDataReference";

const ACCESS_TOKEN = "tok-123";
const DO_APP_ID = "019e-do-0000-7000-8000-000000000001";
const CONTAINER_APP_ID = "019e-sdc-0000-7000-8000-000000000002";
const REF_APP_ID = "019e-sdr-0000-7000-8000-000000000003";

const CREATED_IO = {
  appId: REF_APP_ID,
  name: "process-step-1",
  kind: "structured-data",
  payload: {
    structuredDataContainerAppId: CONTAINER_APP_ID,
    structuredDataOids: [],
  },
};

const BASE_REQUEST: CreateStructuredDataReferenceRequest = {
  dataObjectAppId: DO_APP_ID,
  name: "process-step-1",
  containerAppId: CONTAINER_APP_ID,
};

beforeEach(() => {
  vi.clearAllMocks();
  Object.assign(globalThis, {
    useAuth: () => ({
      data: ref<{ accessToken: string }>({ accessToken: ACCESS_TOKEN }),
    }),
    useRuntimeConfig: () => ({
      public: { backendApiUrl: "http://localhost:8080/shepard/api" },
    }),
    handleError: vi.fn(),
    emitSuccess: vi.fn(),
    ref,
  });
  vi.stubGlobal("fetch", vi.fn());
});

describe("useCreateStructuredDataReference", () => {
  // ─── metadata-only create ────────────────────────────────────────────────

  it("POSTs to /v2/references?kind=structured-data and returns the created IO", async () => {
    const fetchSpy = vi.fn().mockResolvedValue({
      ok: true,
      status: 201,
      json: () => Promise.resolve(CREATED_IO),
    });
    vi.stubGlobal("fetch", fetchSpy);

    const { create } = useCreateStructuredDataReference();
    const result = await create(BASE_REQUEST);

    expect(fetchSpy).toHaveBeenCalledTimes(1);
    const [url, init] = fetchSpy.mock.calls[0]! as [string, RequestInit];
    expect(url).toContain("/v2/references");
    expect(url).toContain("kind=structured-data");
    expect(url).toContain(`dataObjectAppId=${encodeURIComponent(DO_APP_ID)}`);
    expect((init.headers as Record<string, string>)["Authorization"]).toBe(`Bearer ${ACCESS_TOKEN}`);
    expect(init.method).toBe("POST");
    expect(result).toEqual(CREATED_IO);
  });

  it("sends name and containerAppId in the POST body", async () => {
    const fetchSpy = vi.fn().mockResolvedValue({
      ok: true,
      status: 201,
      json: () => Promise.resolve(CREATED_IO),
    });
    vi.stubGlobal("fetch", fetchSpy);

    const { create } = useCreateStructuredDataReference();
    await create(BASE_REQUEST);

    const body = JSON.parse(fetchSpy.mock.calls[0]![1]!.body as string);
    expect(body.name).toBe("process-step-1");
    expect(body.structuredDataContainerAppId).toBe(CONTAINER_APP_ID);
  });

  // ─── two-step create with jsonPayload ────────────────────────────────────

  it("makes a second PUT /content call when jsonPayload is supplied", async () => {
    const fetchSpy = vi
      .fn()
      .mockResolvedValueOnce({
        ok: true,
        status: 201,
        json: () => Promise.resolve(CREATED_IO),
      })
      .mockResolvedValueOnce({
        ok: true,
        status: 200,
        json: () => Promise.resolve(CREATED_IO),
      });
    vi.stubGlobal("fetch", fetchSpy);

    const { create } = useCreateStructuredDataReference();
    const result = await create({ ...BASE_REQUEST, jsonPayload: '{"key":"value"}' });

    expect(fetchSpy).toHaveBeenCalledTimes(2);
    const [uploadUrl, uploadInit] = fetchSpy.mock.calls[1]! as [string, RequestInit];
    expect(uploadUrl).toContain(`/v2/references/${encodeURIComponent(REF_APP_ID)}/content`);
    expect(uploadUrl).toContain("filename=");
    expect(uploadInit.method).toBe("PUT");
    expect((uploadInit.headers as Record<string, string>)["Content-Type"]).toBe(
      "application/octet-stream",
    );
    expect(result).toEqual(CREATED_IO);
  });

  it("skips the upload step when jsonPayload is an empty/whitespace string", async () => {
    const fetchSpy = vi.fn().mockResolvedValue({
      ok: true,
      status: 201,
      json: () => Promise.resolve(CREATED_IO),
    });
    vi.stubGlobal("fetch", fetchSpy);

    const { create } = useCreateStructuredDataReference();
    await create({ ...BASE_REQUEST, jsonPayload: "   " });

    expect(fetchSpy).toHaveBeenCalledTimes(1);
  });

  it("skips the upload step when jsonPayload is undefined", async () => {
    const fetchSpy = vi.fn().mockResolvedValue({
      ok: true,
      status: 201,
      json: () => Promise.resolve(CREATED_IO),
    });
    vi.stubGlobal("fetch", fetchSpy);

    const { create } = useCreateStructuredDataReference();
    await create(BASE_REQUEST);

    expect(fetchSpy).toHaveBeenCalledTimes(1);
  });

  // ─── non-fatal upload failure ─────────────────────────────────────────────

  it("returns the created reference even when the upload step returns a non-ok status", async () => {
    const handleErrorSpy = vi.fn();
    Object.assign(globalThis, { handleError: handleErrorSpy });
    const fetchSpy = vi
      .fn()
      .mockResolvedValueOnce({
        ok: true,
        status: 201,
        json: () => Promise.resolve(CREATED_IO),
      })
      .mockResolvedValueOnce({ ok: false, status: 400 });
    vi.stubGlobal("fetch", fetchSpy);

    const { create } = useCreateStructuredDataReference();
    const result = await create({ ...BASE_REQUEST, jsonPayload: '{"x":1}' });

    expect(result).toEqual(CREATED_IO);
    expect(handleErrorSpy).toHaveBeenCalledWith(
      expect.any(Error),
      "uploadStructuredDataContent",
    );
  });

  // ─── HTTP error on create ────────────────────────────────────────────────

  it("returns null and sets error on 400", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({ ok: false, status: 400 }),
    );

    const { create, error } = useCreateStructuredDataReference();
    const result = await create(BASE_REQUEST);

    expect(result).toBeNull();
    expect(error.value).toMatch(/bad request/i);
  });

  it("returns null and sets error on 401", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({ ok: false, status: 401 }),
    );

    const { create, error } = useCreateStructuredDataReference();
    const result = await create(BASE_REQUEST);

    expect(result).toBeNull();
    expect(error.value).toMatch(/not authenticated/i);
  });

  it("returns null and sets error on 403", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({ ok: false, status: 403 }),
    );

    const { create, error } = useCreateStructuredDataReference();
    const result = await create(BASE_REQUEST);

    expect(result).toBeNull();
    expect(error.value).toMatch(/not authorised/i);
  });

  it("returns null and sets error on 404", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({ ok: false, status: 404 }),
    );

    const { create, error } = useCreateStructuredDataReference();
    const result = await create(BASE_REQUEST);

    expect(result).toBeNull();
    expect(error.value).toMatch(/not found/i);
  });

  // ─── network exception ───────────────────────────────────────────────────

  it("catches fetch exceptions, sets error, returns null", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockRejectedValue(new Error("ECONNREFUSED")),
    );

    const { create, error } = useCreateStructuredDataReference();
    const result = await create(BASE_REQUEST);

    expect(result).toBeNull();
    expect(error.value).toMatch(/ECONNREFUSED/);
  });

  // ─── loading flag ────────────────────────────────────────────────────────

  it("loading is false initially and after the call resolves", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: true,
        status: 201,
        json: () => Promise.resolve(CREATED_IO),
      }),
    );

    const { create, loading } = useCreateStructuredDataReference();
    expect(loading.value).toBe(false);
    await create(BASE_REQUEST);
    expect(loading.value).toBe(false);
  });

  // ─── emitSuccess ────────────────────────────────────────────────────────

  it("calls emitSuccess on a successful create", async () => {
    const emitSpy = vi.fn();
    Object.assign(globalThis, { emitSuccess: emitSpy });
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: true,
        status: 201,
        json: () => Promise.resolve(CREATED_IO),
      }),
    );

    const { create } = useCreateStructuredDataReference();
    await create(BASE_REQUEST);

    expect(emitSpy).toHaveBeenCalledWith(expect.stringContaining("process-step-1"));
  });
});
