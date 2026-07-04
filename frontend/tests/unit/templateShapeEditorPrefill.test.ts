/**
 * V2CONV-B6-SHACLPREFILL — unit tests for the TemplateShapeEditor RDF pre-fill.
 *
 * These tests verify the pure helper layer that feeds the prefill:
 *   - shouldFetchDataObjectRdf rejects empty/null focusAppId
 *   - shouldFetchDataObjectRdf rejects collection scope
 *   - shouldFetchDataObjectRdf accepts data-object scope
 *   - buildDataObjectRdfUrl produces the correct endpoint path
 *
 * The Vue component wiring is verified implicitly via the existing
 * shaclPrefill.test.ts dual-prefill integration scenario; this file
 * keeps component-specific assertions lean (no JSDOM / Vue mount needed).
 */
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";

import { shouldFetchDataObjectRdf, buildDataObjectRdfUrl } from "../../utils/shaclPrefill";

const DO_APP_ID = "018f9c5a-7e26-7000-a000-0000000000bb";
const V2_BASE = "https://shepard.test";

// ── shouldFetchDataObjectRdf — TemplateShapeEditor context ────────────────────

describe("TemplateShapeEditor prefill guard — shouldFetchDataObjectRdf", () => {
  it("returns false when focusAppId is absent (editor opened without context)", () => {
    expect(shouldFetchDataObjectRdf(null, "data-object")).toBe(false);
    expect(shouldFetchDataObjectRdf(undefined, "data-object")).toBe(false);
    expect(shouldFetchDataObjectRdf("", "data-object")).toBe(false);
  });

  it("returns false when scope is collection (RDF endpoint is DataObject-scoped)", () => {
    expect(shouldFetchDataObjectRdf(DO_APP_ID, "collection")).toBe(false);
  });

  it("returns true when focusAppId is set and scope is data-object", () => {
    expect(shouldFetchDataObjectRdf(DO_APP_ID, "data-object")).toBe(true);
  });

  it("returns true for null/unknown scope (editor always passes data-object)", () => {
    // The TemplateShapeEditor always passes 'data-object' as the scope
    // argument — this test documents that the guard fires in that context.
    expect(shouldFetchDataObjectRdf(DO_APP_ID, null)).toBe(true);
  });
});

// ── buildDataObjectRdfUrl — path shape ───────────────────────────────────────

describe("TemplateShapeEditor prefill URL — buildDataObjectRdfUrl", () => {
  it("produces the /v2/data-objects/{appId}/rdf endpoint", () => {
    const url = buildDataObjectRdfUrl(V2_BASE, DO_APP_ID);
    expect(url).toBe(`${V2_BASE}/v2/data-objects/${DO_APP_ID}/rdf`);
  });

  it("encodes appIds containing path-unsafe characters", () => {
    const odd = "abc/def?x=1";
    const url = buildDataObjectRdfUrl(V2_BASE, odd);
    expect(url).toContain(encodeURIComponent(odd));
    expect(url).not.toContain("/def?x=1");
  });
});

// ── prefillDataGraph flow — fetch mocking ────────────────────────────────────

describe("prefillDataGraph fetch behaviour", () => {
  const fetchMock = vi.fn();
  const turtle = "@prefix ex: <http://example.org/> .\nex:part123 ex:batchId \"B-2024-007\" .";

  beforeEach(() => {
    fetchMock.mockReset();
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("fetches RDF when focusAppId is set and scope is data-object", async () => {
    fetchMock.mockResolvedValueOnce({
      ok: true,
      status: 200,
      statusText: "OK",
      text: async () => turtle,
    } as unknown as Response);

    // Simulate the prefillDataGraph logic directly (no Vue mount needed).
    let dataGraph = "INITIAL";
    let rdfPrefilled = false;

    if (shouldFetchDataObjectRdf(DO_APP_ID, "data-object")) {
      const res = await fetch(buildDataObjectRdfUrl(V2_BASE, DO_APP_ID), {
        headers: { Accept: "text/turtle" },
      });
      if (res.ok) {
        const body = await res.text();
        if (body.length > 0) { dataGraph = body; rdfPrefilled = true; }
      }
    }

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      `${V2_BASE}/v2/data-objects/${DO_APP_ID}/rdf`,
    );
    expect(dataGraph).toBe(turtle);
    expect(rdfPrefilled).toBe(true);
  });

  it("does NOT fetch when focusAppId is null (editor opened standalone)", async () => {
    let dataGraph = "INITIAL";

    if (shouldFetchDataObjectRdf(null, "data-object")) {
      const res = await fetch(buildDataObjectRdfUrl(V2_BASE, ""), {
        headers: { Accept: "text/turtle" },
      });
      if (res.ok) dataGraph = await res.text();
    }

    expect(fetchMock).not.toHaveBeenCalled();
    expect(dataGraph).toBe("INITIAL");
  });

  it("leaves dataGraph unchanged when the server returns non-OK", async () => {
    fetchMock.mockResolvedValueOnce({
      ok: false,
      status: 404,
      statusText: "Not Found",
    } as unknown as Response);

    let dataGraph = "INITIAL";
    let rdfLoadError: string | null = null;

    if (shouldFetchDataObjectRdf(DO_APP_ID, "data-object")) {
      const res = await fetch(buildDataObjectRdfUrl(V2_BASE, DO_APP_ID), {
        headers: { Accept: "text/turtle" },
      });
      if (!res.ok) {
        rdfLoadError = `${res.status} ${res.statusText}`;
      } else {
        dataGraph = await res.text();
      }
    }

    expect(dataGraph).toBe("INITIAL");
    expect(rdfLoadError).toBe("404 Not Found");
  });
});
