/**
 * SEMA-V6-UI-FOLLOWUP — Vitest coverage for the
 * useSemanticVocabularyPredicates composable.
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { useSemanticVocabularyPredicates } from "~/composables/semantic/useSemanticVocabularyPredicates";

const flush = () => new Promise<void>((r) => setTimeout(r, 0));

beforeEach(() => {
  vi.restoreAllMocks();
  Object.assign(globalThis, {
    useRuntimeConfig: () => ({
      public: {
        backendApiUrl: "http://localhost:8080/shepard/api",
        backendV2ApiUrl: "http://localhost:8080",
      },
    }),
    useAuth: () => ({
      data: { value: { accessToken: "test-token" } },
    }),
  });
});

describe("useSemanticVocabularyPredicates", () => {
  it("populates predicates on 200 OK", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      statusText: "OK",
      json: async () => ({
        vocabularyAppId: "v-dcterms",
        predicates: [
          {
            appId: "p-creator",
            uri: "http://purl.org/dc/terms/creator",
            label: "Creator",
            vocabularyAppId: "v-dcterms",
            expectedObjectType: "LITERAL",
            cardinality: "MANY",
            required: true,
          },
        ],
      }),
    });
    globalThis.fetch = fetchMock as unknown as typeof fetch;

    const { predicates, loading, error, notFound, fetchPredicates } =
      useSemanticVocabularyPredicates();

    await fetchPredicates("v-dcterms");
    await flush();

    expect(loading.value).toBe(false);
    expect(error.value).toBeNull();
    expect(notFound.value).toBe(false);
    expect(predicates.value).toHaveLength(1);
    expect(predicates.value[0]?.uri).toBe("http://purl.org/dc/terms/creator");
    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/v2/semantic/vocabularies/v-dcterms/predicates",
      expect.objectContaining({
        headers: expect.objectContaining({
          Authorization: "Bearer test-token",
        }),
      }),
    );
  });

  it("sets notFound on 404", async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 404,
      statusText: "Not Found",
      json: async () => ({}),
    }) as unknown as typeof fetch;

    const { predicates, notFound, fetchPredicates } =
      useSemanticVocabularyPredicates();

    await fetchPredicates("missing-id");

    expect(notFound.value).toBe(true);
    expect(predicates.value).toEqual([]);
  });

  it("treats a blank vocabId as not-found and skips the fetch", async () => {
    const fetchMock = vi.fn();
    globalThis.fetch = fetchMock as unknown as typeof fetch;

    const { notFound, fetchPredicates } = useSemanticVocabularyPredicates();

    await fetchPredicates("   ");

    expect(notFound.value).toBe(true);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("sets error on non-OK non-404 response", async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 500,
      statusText: "Internal Server Error",
      json: async () => ({}),
    }) as unknown as typeof fetch;

    const { error, fetchPredicates } = useSemanticVocabularyPredicates();

    await fetchPredicates("v-dcterms");

    expect(error.value).toBe("500 Internal Server Error");
  });

  it("sets error when fetch throws", async () => {
    globalThis.fetch = vi
      .fn()
      .mockRejectedValue(new Error("network down")) as unknown as typeof fetch;

    const { error, fetchPredicates } = useSemanticVocabularyPredicates();

    await fetchPredicates("v-dcterms");

    expect(error.value).toBe("network down");
  });

  it("returns an empty list when the response has no predicates array", async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      statusText: "OK",
      json: async () => ({ vocabularyAppId: "v-empty" }),
    }) as unknown as typeof fetch;

    const { predicates, fetchPredicates } = useSemanticVocabularyPredicates();

    await fetchPredicates("v-empty");

    expect(predicates.value).toEqual([]);
  });

  it("derives the v2 base URL from backendApiUrl when backendV2ApiUrl is missing", async () => {
    Object.assign(globalThis, {
      useRuntimeConfig: () => ({
        public: { backendApiUrl: "https://shepard.example.com/shepard/api" },
      }),
    });
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      statusText: "OK",
      json: async () => ({ vocabularyAppId: "v-x", predicates: [] }),
    });
    globalThis.fetch = fetchMock as unknown as typeof fetch;

    const { fetchPredicates } = useSemanticVocabularyPredicates();
    await fetchPredicates("v-x");

    expect(fetchMock).toHaveBeenCalledWith(
      "https://shepard.example.com/v2/semantic/vocabularies/v-x/predicates",
      expect.any(Object),
    );
  });
});
