/**
 * UI19 — unit tests for useSkosRelated composable.
 *
 * Tests cover: SPARQL query composition, happy-path result parsing,
 * deduplication, empty-on-error, IRI validation guard, and clear().
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { useSkosRelated } from "~/composables/semantic/useSkosRelated";

const ACCESS_TOKEN = "test-skos-token";

// ─── Mock helpers ─────────────────────────────────────────────────────────────

function makeSparqlResult(bindings: Array<Record<string, { type: string; value: string } | undefined>>) {
  return {
    head: { vars: ["related", "label"] },
    results: { bindings },
  };
}

function mockFetchOk(body: unknown) {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve(body),
    }),
  );
}

function mockFetchError(status: number) {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({
      ok: false,
      status,
      json: () => Promise.resolve({}),
    }),
  );
}

// ─── Setup ────────────────────────────────────────────────────────────────────

beforeEach(() => {
  vi.clearAllMocks();
  (globalThis as unknown as { useAuth: () => unknown }).useAuth = () => ({
    data: ref<{ accessToken: string }>({ accessToken: ACCESS_TOKEN }),
  });
  vi.stubGlobal("fetch", vi.fn());
  vi.stubGlobal("useRuntimeConfig", () => ({
    public: { backendApiUrl: "http://localhost:8080/shepard/api" },
  }));
});

// ─── Initial state ────────────────────────────────────────────────────────────

describe("useSkosRelated — initial state", () => {
  it("starts with empty related array and loading=false", () => {
    const { related, loading } = useSkosRelated();
    expect(related.value).toEqual([]);
    expect(loading.value).toBe(false);
  });
});

// ─── fetchRelated() ───────────────────────────────────────────────────────────

describe("useSkosRelated — fetchRelated()", () => {
  it("populates related[] with uri and label from bindings", async () => {
    mockFetchOk(makeSparqlResult([
      {
        related: { type: "uri", value: "http://example.org/TestRun" },
        label: { type: "literal", value: "Test Run" },
      },
    ]));

    const { related, fetchRelated } = useSkosRelated();
    await fetchRelated("http://example.org/SteadyState");

    expect(related.value).toHaveLength(1);
    expect(related.value[0]).toEqual({ uri: "http://example.org/TestRun", label: "Test Run" });
  });

  it("returns null label when label binding is absent", async () => {
    mockFetchOk(makeSparqlResult([
      {
        related: { type: "uri", value: "http://example.org/SomeConcept" },
        label: undefined,
      },
    ]));

    const { related, fetchRelated } = useSkosRelated();
    await fetchRelated("http://example.org/Source");

    expect(related.value[0]!.label).toBeNull();
  });

  it("deduplicates rows with the same related URI", async () => {
    // The SPARQL endpoint may return multiple rows when both prefLabel and rdfs:label match
    mockFetchOk(makeSparqlResult([
      {
        related: { type: "uri", value: "http://example.org/TestRun" },
        label: { type: "literal", value: "Test Run (skos)" },
      },
      {
        related: { type: "uri", value: "http://example.org/TestRun" },
        label: { type: "literal", value: "Test Run (rdfs)" },
      },
    ]));

    const { related, fetchRelated } = useSkosRelated();
    await fetchRelated("http://example.org/SteadyState");

    expect(related.value).toHaveLength(1);
    expect(related.value[0]!.uri).toBe("http://example.org/TestRun");
  });

  it("skips non-URI related bindings", async () => {
    mockFetchOk(makeSparqlResult([
      {
        related: { type: "literal", value: "not-a-uri" },
        label: undefined,
      },
      {
        related: { type: "uri", value: "http://example.org/Valid" },
        label: { type: "literal", value: "Valid" },
      },
    ]));

    const { related, fetchRelated } = useSkosRelated();
    await fetchRelated("http://example.org/Source");

    expect(related.value).toHaveLength(1);
    expect(related.value[0]!.uri).toBe("http://example.org/Valid");
  });

  it("sends POST to /v2/semantic/internal/sparql with form body", async () => {
    mockFetchOk(makeSparqlResult([]));
    const { fetchRelated } = useSkosRelated();
    await fetchRelated("http://example.org/Term");

    const [url, opts] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock
      .calls.at(-1) as [string, RequestInit];
    expect(url).toContain("/v2/semantic/internal/sparql");
    expect(opts.method).toBe("POST");
    expect((opts.headers as Record<string, string>)["Content-Type"]).toBe(
      "application/x-www-form-urlencoded",
    );
    expect(opts.body as string).toContain("query=");
  });

  it("includes the term URI in the SPARQL query body", async () => {
    mockFetchOk(makeSparqlResult([]));
    const { fetchRelated } = useSkosRelated();
    const termUri = "http://example.org/SpecificTerm";
    await fetchRelated(termUri);

    const [, opts] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock
      .calls.at(-1) as [string, RequestInit];
    expect(decodeURIComponent(opts.body as string)).toContain(termUri);
  });

  it("includes Authorization header", async () => {
    mockFetchOk(makeSparqlResult([]));
    const { fetchRelated } = useSkosRelated();
    await fetchRelated("http://example.org/Term");

    const [, opts] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock
      .calls.at(-1) as [string, RequestInit];
    expect((opts.headers as Record<string, string>)["Authorization"]).toBe(
      `Bearer ${ACCESS_TOKEN}`,
    );
  });

  it("returns empty array on HTTP error (no throw)", async () => {
    mockFetchError(500);
    const { related, fetchRelated } = useSkosRelated();
    await fetchRelated("http://example.org/Term");
    expect(related.value).toEqual([]);
  });

  it("returns empty array on network failure (no throw)", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error("offline")));
    const { related, fetchRelated } = useSkosRelated();
    await expect(fetchRelated("http://example.org/Term")).resolves.toBeUndefined();
    expect(related.value).toEqual([]);
  });

  it("does nothing for blank URI", async () => {
    const { related, fetchRelated } = useSkosRelated();
    await fetchRelated("");
    expect(globalThis.fetch).not.toHaveBeenCalled();
    expect(related.value).toEqual([]);
  });

  it("does nothing for non-http IRI (e.g. urn:)", async () => {
    const { related, fetchRelated } = useSkosRelated();
    await fetchRelated("urn:example:foo");
    expect(globalThis.fetch).not.toHaveBeenCalled();
    expect(related.value).toEqual([]);
  });

  it("does nothing for malformed IRI", async () => {
    const { related, fetchRelated } = useSkosRelated();
    await fetchRelated("not-a-url-at-all");
    expect(globalThis.fetch).not.toHaveBeenCalled();
    expect(related.value).toEqual([]);
  });

  it("resets loading to false after resolution", async () => {
    mockFetchOk(makeSparqlResult([]));
    const { loading, fetchRelated } = useSkosRelated();
    const p = fetchRelated("http://example.org/Term");
    expect(loading.value).toBe(true);
    await p;
    expect(loading.value).toBe(false);
  });
});

// ─── clear() ─────────────────────────────────────────────────────────────────

describe("useSkosRelated — clear()", () => {
  it("empties related[] without a network call", async () => {
    mockFetchOk(makeSparqlResult([
      {
        related: { type: "uri", value: "http://example.org/A" },
        label: { type: "literal", value: "A" },
      },
    ]));

    const { related, fetchRelated, clear } = useSkosRelated();
    await fetchRelated("http://example.org/Source");
    expect(related.value).toHaveLength(1);

    vi.clearAllMocks();
    clear();

    expect(related.value).toEqual([]);
    expect(globalThis.fetch).not.toHaveBeenCalled();
  });
});
