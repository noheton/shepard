/**
 * SEMA-V6-PRED-UI — unit tests for the {@link usePredicateStats} composable
 * and its URL-safe Base64 encoder.
 *
 * The encoder pair (`encodeIriForPath` here ↔ `Base64.getUrlDecoder()` on the
 * backend) is load-bearing — drift between them silently breaks every page
 * load on the predicate detail surface. Pin the wire shape.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ref, nextTick } from "vue";

import { encodeIriForPath, usePredicateStats } from "../../composables/semantic/usePredicateStats";

const TOKEN = "fake-jwt-token";

beforeEach(() => {
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

function mockFetchOnce(body: unknown, ok = true, status = 200) {
  const fetchMock = vi.fn().mockResolvedValue({
    ok,
    status,
    json: async () => body,
  } as unknown as Response);
  globalThis.fetch = fetchMock as unknown as typeof fetch;
  return fetchMock;
}

describe("encodeIriForPath — URL-safe Base64 (RFC 4648 §5)", () => {
  it("encodes a URN with colons round-trip via the standard URL-safe decoder", () => {
    const iri = "urn:shepard:material:batch";
    const b64 = encodeIriForPath(iri);
    expect(b64).not.toMatch(/[+/=]/); // URL-safe alphabet, no padding
    // Decoder equivalent — Buffer.from accepts the URL-safe alphabet via 'base64url'.
    expect(Buffer.from(b64, "base64url").toString("utf-8")).toBe(iri);
  });

  it("encodes an HTTP IRI with slashes round-trip", () => {
    const iri = "http://purl.org/dc/terms/creator";
    const b64 = encodeIriForPath(iri);
    expect(b64).not.toMatch(/[+/=]/);
    expect(Buffer.from(b64, "base64url").toString("utf-8")).toBe(iri);
  });

  it("encodes UTF-8 multi-byte characters correctly", () => {
    const iri = "urn:shepard:material:bäcker"; // 'ä' is 2 bytes in UTF-8
    const b64 = encodeIriForPath(iri);
    expect(Buffer.from(b64, "base64url").toString("utf-8")).toBe(iri);
  });

  it("never produces standard-alphabet `+` or `/` (those break path parsing)", () => {
    // Construct an IRI that would emit `+` / `/` under standard Base64:
    // bytes [0xfb, 0xff, 0xbf] encode to '+/+' in standard alphabet.
    // Force this via a synthetic input — the encoder must rewrite both.
    const iri = String.fromCharCode(0xfb, 0xff, 0xbf);
    const b64 = encodeIriForPath(iri);
    expect(b64).not.toMatch(/[+/]/);
  });
});

describe("usePredicateStats — composable", () => {
  it("constructs the URL-safe path segment + query params from inputs", async () => {
    const fetchMock = mockFetchOnce({
      predicate: "urn:p",
      annotationCount: 0,
      topValues: [],
      sampleEntities: [],
    });
    const iri = ref("urn:shepard:material:batch");
    const { stats } = usePredicateStats(iri, { topValuesLimit: 7, sampleLimit: 3 });
    // Watcher fires immediately; await microtask + the fetch promise chain.
    await nextTick();
    await Promise.resolve();
    await Promise.resolve();

    expect(stats.value).not.toBeNull();
    const url = String(fetchMock.mock.calls[0]![0]);
    // base64url of "urn:shepard:material:batch":
    const expectedB64 = Buffer.from(iri.value, "utf-8").toString("base64url");
    expect(url).toContain(`/v2/semantic/predicates/${expectedB64}/stats`);
    expect(url).toContain("topValuesLimit=7");
    expect(url).toContain("sampleLimit=3");
  });

  it("attaches the Bearer token from useAuth", async () => {
    const fetchMock = mockFetchOnce({
      predicate: "urn:p",
      annotationCount: 0,
      topValues: [],
      sampleEntities: [],
    });
    const iri = ref("urn:p");
    usePredicateStats(iri);
    await nextTick();
    await Promise.resolve();
    await Promise.resolve();
    const init = fetchMock.mock.calls[0]![1] as RequestInit;
    expect((init.headers as Record<string, string>).Authorization).toBe(`Bearer ${TOKEN}`);
  });

  it("skips the fetch when the predicate IRI is empty", async () => {
    const fetchMock = mockFetchOnce({});
    const iri = ref("");
    const { stats, loading, error } = usePredicateStats(iri);
    await nextTick();
    await Promise.resolve();
    expect(fetchMock).not.toHaveBeenCalled();
    expect(stats.value).toBeNull();
    expect(loading.value).toBe(false);
    expect(error.value).toBeNull();
  });

  it("populates stats on a successful response", async () => {
    mockFetchOnce({
      predicate: "urn:p",
      annotationCount: 42,
      topValues: [{ objectIri: "urn:v", objectLabel: "V", count: 10 }],
      sampleEntities: [{ appId: "abc", name: "TR-004", type: "DataObject" }],
    });
    const iri = ref("urn:p");
    const { stats, loading, error } = usePredicateStats(iri);
    await nextTick();
    await Promise.resolve();
    await Promise.resolve();
    expect(loading.value).toBe(false);
    expect(error.value).toBeNull();
    expect(stats.value?.annotationCount).toBe(42);
    expect(stats.value?.topValues).toHaveLength(1);
    expect(stats.value?.sampleEntities[0]?.name).toBe("TR-004");
  });

  it("surfaces a non-2xx response as an error", async () => {
    mockFetchOnce({}, false, 400);
    const iri = ref("urn:bad");
    const { stats, error, loading } = usePredicateStats(iri);
    await nextTick();
    await Promise.resolve();
    await Promise.resolve();
    expect(loading.value).toBe(false);
    expect(stats.value).toBeNull();
    expect(error.value).toMatch(/HTTP 400/);
  });

  it("refetches when the predicate IRI changes", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ predicate: "a", annotationCount: 1, topValues: [], sampleEntities: [] }),
      } as unknown as Response)
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ predicate: "b", annotationCount: 2, topValues: [], sampleEntities: [] }),
      } as unknown as Response);
    globalThis.fetch = fetchMock as unknown as typeof fetch;

    const iri = ref("a");
    const { stats } = usePredicateStats(iri);
    await nextTick();
    await Promise.resolve();
    await Promise.resolve();
    expect(stats.value?.annotationCount).toBe(1);

    iri.value = "b";
    await nextTick();
    await Promise.resolve();
    await Promise.resolve();
    expect(stats.value?.annotationCount).toBe(2);
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });
});
