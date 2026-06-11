/**
 * TS-SEMANTIC-REST — unit tests for {@link AnnotatedChannel}, the wrapper
 * over `/v2/timeseries-containers/{containerId}/channels/{channelShepardId}/annotations`.
 *
 * Covers URL construction, the GET-404→[] swallow (pre-TS-SEMANTIC-01
 * channels), and the create / delete happy paths.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ref } from "vue";

import { AnnotatedChannel } from "../../composables/annotated";

const TOKEN = "fake-jwt-token";

beforeEach(() => {
  // useAuth() override — annotated.ts reads `accessToken` for the Bearer header.
  (globalThis as unknown as { useAuth: () => unknown }).useAuth = () => ({
    data: ref({ accessToken: TOKEN }),
  });
  (globalThis as unknown as { useRuntimeConfig: () => unknown }).useRuntimeConfig =
    () => ({
      public: { backendApiUrl: "https://api.example.com/shepard/api" },
    });
});

afterEach(() => {
  vi.restoreAllMocks();
});

function mockFetchOnce(response: Partial<Response> & { ok: boolean; status?: number; jsonBody?: unknown }) {
  const fetchMock = vi.fn().mockResolvedValue({
    ok: response.ok,
    status: response.status ?? (response.ok ? 200 : 500),
    json: async () => response.jsonBody ?? null,
  } as unknown as Response);
  globalThis.fetch = fetchMock as unknown as typeof fetch;
  return fetchMock;
}

describe("AnnotatedChannel — TS-SEMANTIC-REST wrapper", () => {
  it("derives the v2 base URL from backendApiUrl by stripping /shepard/api", async () => {
    const fetchMock = mockFetchOnce({ ok: true, jsonBody: [] });
    const a = new AnnotatedChannel("01928eaa-0000-7000-8000-000000000042", "01928eaa-1234-7000-9000-aaaaaaaaaaaa");
    await a.fetchAnnotations();
    const url = String(fetchMock.mock.calls[0]![0]);
    expect(url).toBe(
      "https://api.example.com/v2/timeseries-containers/01928eaa-0000-7000-8000-000000000042/channels/01928eaa-1234-7000-9000-aaaaaaaaaaaa/annotations",
    );
  });

  it("URL-encodes the channelShepardId path segment", async () => {
    const fetchMock = mockFetchOnce({ ok: true, jsonBody: [] });
    // unusual id with slash/colon to prove encoding
    const a = new AnnotatedChannel("01928eaa-0000-7000-8000-000000000007", "foo/bar:baz");
    await a.fetchAnnotations();
    const url = String(fetchMock.mock.calls[0]![0]);
    expect(url).toContain("/channels/foo%2Fbar%3Abaz/annotations");
  });

  it("attaches the Bearer token from useAuth", async () => {
    const fetchMock = mockFetchOnce({ ok: true, jsonBody: [] });
    const a = new AnnotatedChannel("01928eaa-0000-7000-8000-000000000001", "sid");
    await a.fetchAnnotations();
    const init = fetchMock.mock.calls[0]![1] as RequestInit;
    expect((init.headers as Record<string, string>).Authorization).toBe(
      `Bearer ${TOKEN}`,
    );
  });

  it("swallows a GET 404 into an empty list (pre-TS-SEMANTIC-01 channels)", async () => {
    mockFetchOnce({ ok: false, status: 404 });
    const a = new AnnotatedChannel("01928eaa-0000-7000-8000-000000000001", "sid-missing");
    const out = await a.fetchAnnotations();
    expect(out).toEqual([]);
  });

  it("re-throws on non-404 errors", async () => {
    mockFetchOnce({ ok: false, status: 500 });
    const a = new AnnotatedChannel("01928eaa-0000-7000-8000-000000000001", "sid");
    await expect(a.fetchAnnotations()).rejects.toThrow(/HTTP 500/);
  });

  it("POST add — sends JSON body and returns the created annotation", async () => {
    const created = { id: 999, propertyIRI: "p", valueIRI: "v" };
    const fetchMock = mockFetchOnce({ ok: true, status: 201, jsonBody: created });
    const a = new AnnotatedChannel("01928eaa-0000-7000-8000-000000000003", "sid-3");
    const out = await a.addAnnotation({
      propertyIRI: "p",
      valueIRI: "v",
      propertyRepositoryId: 1,
      valueRepositoryId: 1,
    } as unknown as Parameters<typeof a.addAnnotation>[0]);
    expect(out).toEqual(created);
    const init = fetchMock.mock.calls[0]![1] as RequestInit;
    expect(init.method).toBe("POST");
    expect(JSON.parse(String(init.body))).toMatchObject({
      propertyIRI: "p",
      valueIRI: "v",
    });
  });

  it("DELETE — targets {annotationId} subpath", async () => {
    const fetchMock = mockFetchOnce({ ok: true, status: 204 });
    const a = new AnnotatedChannel("01928eaa-0000-7000-8000-000000000003", "sid-3");
    await a.deleteAnnotation(777);
    const url = String(fetchMock.mock.calls[0]![0]);
    expect(url).toMatch(/\/channels\/sid-3\/annotations\/777$/);
    const init = fetchMock.mock.calls[0]![1] as RequestInit;
    expect(init.method).toBe("DELETE");
  });

  it("uses backendV2ApiUrl override when explicitly set", async () => {
    (globalThis as unknown as { useRuntimeConfig: () => unknown }).useRuntimeConfig =
      () => ({
        public: {
          backendApiUrl: "https://api.example.com/shepard/api",
          backendV2ApiUrl: "https://v2.example.com",
        },
      });
    const fetchMock = mockFetchOnce({ ok: true, jsonBody: [] });
    const a = new AnnotatedChannel("01928eaa-0000-7000-8000-000000000009", "sid-9");
    await a.fetchAnnotations();
    const url = String(fetchMock.mock.calls[0]![0]);
    expect(url).toMatch(/^https:\/\/v2\.example\.com\/v2\//);
  });
});
