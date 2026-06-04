/**
 * V2CONV-A3 — Vitest for the unified `/v2/containers` create + list helpers
 * (`createV2Container`, `listV2Containers`). Mocks `fetch` and asserts the
 * request shape (path, kind query param, body) and the response parsing.
 */
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import {
  createV2Container,
  listV2Containers,
} from "../../composables/container/createV2Container";

describe("createV2Container", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("POSTs to /v2/containers with the kind query param and name body", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        id: 12,
        appId: "ctr-1",
        name: "scans",
        type: "FILE",
        kind: "file",
        payload: { oid: "mongo-1" },
      }),
    });
    vi.stubGlobal("fetch", fetchMock);

    const result = await createV2Container("file", "scans");

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toContain("/v2/containers?kind=file");
    expect(init.method).toBe("POST");
    expect(JSON.parse(init.body)).toEqual({ name: "scans" });
    expect(result?.id).toBe(12);
    expect(result?.appId).toBe("ctr-1");
    expect(result?.payload?.oid).toBe("mongo-1");
  });

  it("encodes the structured-data kind token in the query param", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ id: 1, name: "n", kind: "structured-data" }),
    });
    vi.stubGlobal("fetch", fetchMock);

    await createV2Container("structured-data", "n");
    const [url] = fetchMock.mock.calls[0];
    expect(url).toContain("kind=structured-data");
  });

  it("returns undefined and reports on a non-ok response", async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: false, status: 400 });
    vi.stubGlobal("fetch", fetchMock);

    const result = await createV2Container("timeseries", "x");
    expect(result).toBeUndefined();
  });
});

describe("listV2Containers", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("GETs with kind and optional name filter", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => [
        { id: 1, name: "a", kind: "file" },
        { id: 2, name: "ab", kind: "file" },
      ],
    });
    vi.stubGlobal("fetch", fetchMock);

    const out = await listV2Containers("file", "a");
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toContain("kind=file");
    expect(url).toContain("name=a");
    expect(init.method).toBe("GET");
    expect(out).toHaveLength(2);
  });

  it("omits the name param when no filter is given", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => [],
    });
    vi.stubGlobal("fetch", fetchMock);

    await listV2Containers("timeseries");
    const [url] = fetchMock.mock.calls[0];
    expect(url).toContain("kind=timeseries");
    expect(url).not.toContain("name=");
  });

  it("returns an empty array on a non-ok response", async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: false, status: 500 });
    vi.stubGlobal("fetch", fetchMock);

    const out = await listV2Containers("file");
    expect(out).toEqual([]);
  });
});
