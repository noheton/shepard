import { describe, it, expect, vi, beforeEach } from "vitest";
import type { OntologyAlignmentIO } from "~/composables/context/admin/useOntologyAlignment";
import { useOntologyAlignment } from "~/composables/context/admin/useOntologyAlignment";

const ACCESS_TOKEN = "test-admin-token";

beforeEach(() => {
  vi.clearAllMocks();
  (globalThis as unknown as { useAuth: () => unknown }).useAuth = () => ({
    data: ref<{ accessToken: string }>({ accessToken: ACCESS_TOKEN }),
  });
  vi.stubGlobal("fetch", vi.fn());
});

function mockFetchOk(body: unknown) {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({
      ok: true,
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
      text: () => Promise.resolve(""),
    }),
  );
}

const sample: OntologyAlignmentIO[] = [
  {
    appId: "01af-0001",
    shepardConcept: "Collection",
    upperOntologyUri: "http://purl.obolibrary.org/obo/IAO_0000100",
    relationshipType: "rdfs:subClassOf",
    confidence: "HIGH",
    source: "aidocs/semantics/96-upper-ontology-alignment.md",
    createdAt: 1717000000000,
  },
  {
    appId: "01af-0002",
    shepardConcept: "DataObject",
    upperOntologyUri: "http://www.w3.org/ns/prov#Entity",
    relationshipType: "owl:equivalentClass",
    confidence: "MEDIUM",
    source: "aidocs/semantics/96-upper-ontology-alignment.md",
    createdAt: 1717000000001,
  },
];

describe("useOntologyAlignment — refresh()", () => {
  it("populates alignments on successful GET", async () => {
    mockFetchOk(sample);
    const { alignments, error, refresh } = useOntologyAlignment();
    await refresh();
    expect(alignments.value).toEqual(sample);
    expect(error.value).toBeNull();
  });

  it("hits the correct endpoint with an auth header when available", async () => {
    mockFetchOk(sample);
    const { refresh } = useOntologyAlignment();
    await refresh();
    const [url, opts] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock
      .calls.at(-1) as [string, RequestInit];
    expect(url).toContain("/v2/semantic/ontology/alignment");
    expect((opts.headers as Record<string, string>)["Authorization"]).toBe(
      `Bearer ${ACCESS_TOKEN}`,
    );
  });

  it("sets a user-facing error on HTTP failure and leaves alignments empty", async () => {
    mockFetchError(500);
    const { alignments, error, refresh } = useOntologyAlignment();
    await refresh();
    expect(error.value).toBe("Failed to load ontology alignment registry");
    expect(alignments.value).toEqual([]);
  });

  it("refreshes idempotently — second refresh replaces, not appends", async () => {
    mockFetchOk(sample);
    const { alignments, refresh } = useOntologyAlignment();
    await refresh();
    expect(alignments.value).toHaveLength(2);
    mockFetchOk([sample[0]]);
    await refresh();
    expect(alignments.value).toHaveLength(1);
    expect(alignments.value[0]?.shepardConcept).toBe("Collection");
  });
});
