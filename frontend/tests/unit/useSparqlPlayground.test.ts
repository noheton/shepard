import { describe, it, expect, vi, beforeEach } from "vitest";
import { useSparqlPlayground } from "~/composables/context/admin/useSparqlPlayground";
import type { SparqlResultsJson } from "~/composables/context/admin/useSparqlPlayground";

const ACCESS_TOKEN = "test-sparql-token";

// ─── Mock helpers ─────────────────────────────────────────────────────────────

function mockFetchOk(body: unknown) {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      text: () => Promise.resolve(JSON.stringify(body)),
    }),
  );
}

function mockFetchError(status: number, bodyText = '{"title":"bad query"}') {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({
      ok: false,
      status,
      text: () => Promise.resolve(bodyText),
    }),
  );
}

// Minimal W3C SPARQL Results JSON for SELECT
const selectResult: SparqlResultsJson = {
  head: { vars: ["s", "p", "o"] },
  results: {
    bindings: [
      {
        s: { type: "uri", value: "http://example.org/subject" },
        p: { type: "uri", value: "http://www.w3.org/1999/02/22-rdf-syntax-ns#type" },
        o: { type: "uri", value: "http://example.org/Class" },
      },
      {
        s: { type: "literal", value: "hello" },
        p: { type: "uri", value: "http://example.org/label" },
        o: undefined,
      },
    ],
  },
};

// Minimal W3C SPARQL Results JSON for ASK
const askResult = {
  head: { vars: [] },
  results: { bindings: [] },
  boolean: true,
};

// ─── Setup ────────────────────────────────────────────────────────────────────

beforeEach(() => {
  vi.clearAllMocks();
  (globalThis as unknown as { useAuth: () => unknown }).useAuth = () => ({
    data: ref<{ accessToken: string }>({ accessToken: ACCESS_TOKEN }),
  });
  vi.stubGlobal("fetch", vi.fn());
  vi.stubGlobal("handleError", vi.fn());
  vi.stubGlobal("useRuntimeConfig", () => ({
    public: { backendApiUrl: "http://localhost:8080/shepard/api" },
  }));
});

// ─── runQuery() ───────────────────────────────────────────────────────────────

describe("useSparqlPlayground — runQuery()", () => {
  it("starts with no results and no error", () => {
    const { results, error, isLoading } = useSparqlPlayground();
    expect(results.value).toBeNull();
    expect(error.value).toBeNull();
    expect(isLoading.value).toBe(false);
  });

  it("populates results on a successful SELECT response", async () => {
    mockFetchOk(selectResult);
    const { results, error, runQuery } = useSparqlPlayground();
    await runQuery();

    expect(results.value).toEqual(selectResult);
    expect(error.value).toBeNull();
  });

  it("sets rowCount to the binding count", async () => {
    mockFetchOk(selectResult);
    const { rowCount, runQuery } = useSparqlPlayground();
    await runQuery();
    expect(rowCount.value).toBe(2);
  });

  it("handles ASK response (boolean, no vars)", async () => {
    mockFetchOk(askResult);
    const { results, rowCount, runQuery } = useSparqlPlayground();
    await runQuery();

    expect(results.value?.head.vars).toHaveLength(0);
    expect(rowCount.value).toBe(0);
  });

  it("sends POST to /v2/semantic/{repoAppId}/sparql with form body", async () => {
    mockFetchOk(selectResult);
    const { repoId, runQuery } = useSparqlPlayground();
    repoId.value = "my-repo";
    await runQuery();

    const [url, opts] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock
      .calls.at(-1) as [string, RequestInit];
    expect(url).toContain("/v2/semantic/my-repo/sparql");
    expect(opts.method).toBe("POST");
    expect(opts.headers as Record<string, string>).toMatchObject({
      "Content-Type": "application/x-www-form-urlencoded",
    });
    expect(typeof opts.body).toBe("string");
    expect(opts.body as string).toContain("query=");
  });

  it("includes Authorization header with Bearer token", async () => {
    mockFetchOk(selectResult);
    const { runQuery } = useSparqlPlayground();
    await runQuery();

    const [, opts] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock
      .calls.at(-1) as [string, RequestInit];
    expect((opts.headers as Record<string, string>)["Authorization"]).toBe(
      `Bearer ${ACCESS_TOKEN}`,
    );
  });

  it("sets error on HTTP 400 (bad query / read-only violation)", async () => {
    mockFetchError(400, JSON.stringify({ detail: "Only SELECT and ASK queries are permitted." }));
    const { error, results, runQuery } = useSparqlPlayground();
    await runQuery();

    expect(results.value).toBeNull();
    expect(error.value).toMatch(/400/);
    expect(error.value).toContain("Only SELECT and ASK");
  });

  it("extracts title field from RFC 7807 when detail is absent", async () => {
    mockFetchError(404, JSON.stringify({ title: "Semantic repository not found." }));
    const { error, runQuery } = useSparqlPlayground();
    await runQuery();

    expect(error.value).toContain("Semantic repository not found.");
  });

  it("falls back to raw text on non-JSON error body", async () => {
    mockFetchError(503, "Service Unavailable");
    const { error, runQuery } = useSparqlPlayground();
    await runQuery();

    expect(error.value).toContain("503");
    expect(error.value).toContain("Service Unavailable");
  });

  it("sets error on network failure", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error("offline")));
    const { error, runQuery } = useSparqlPlayground();
    await runQuery();

    expect(error.value).toContain("offline");
  });

  it("resets isLoading to false after runQuery resolves", async () => {
    mockFetchOk(selectResult);
    const { isLoading, runQuery } = useSparqlPlayground();
    const p = runQuery();
    expect(isLoading.value).toBe(true);
    await p;
    expect(isLoading.value).toBe(false);
  });

  it("sets error if query is blank", async () => {
    const { query, error, runQuery } = useSparqlPlayground();
    query.value = "   ";
    await runQuery();

    expect(error.value).toBeTruthy();
    expect(globalThis.fetch).not.toHaveBeenCalled();
  });
});

// ─── resetQuery() ─────────────────────────────────────────────────────────────

describe("useSparqlPlayground — resetQuery()", () => {
  it("clears results, error, and resets query to the default", async () => {
    mockFetchOk(selectResult);
    const { query, results, error, runQuery, resetQuery } = useSparqlPlayground();
    await runQuery();
    query.value = "SELECT ?x WHERE { ?x ?y ?z }";
    error.value = "something went wrong";

    resetQuery();

    expect(results.value).toBeNull();
    expect(error.value).toBeNull();
    expect(query.value).toContain("SELECT");
    // The default includes LIMIT 10
    expect(query.value).toContain("LIMIT 10");
  });
});
