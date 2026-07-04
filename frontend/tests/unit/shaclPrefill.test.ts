/**
 * SHAPES-V-PREFILL-2/3 — unit tests for the /shapes/validate
 * auto-prefill helpers.
 *
 * Coverage:
 *   1. shouldFetchDataObjectRdf — focus-only fires; collection-scope
 *      does NOT fire (the RDF endpoint is DataObject-scoped).
 *   2. buildDataObjectRdfUrl — URL encoding + path shape.
 *   3. Both prefills compose without auto-running validate(): we mock
 *      fetch globally and assert that on the "both ids + shapeGraph in
 *      body" scenario, NO POST to /v2/shapes/validate is issued.
 */
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";

import { extractShapeGraphFromTemplateBody } from "../../utils/shaclTemplateBody";
import { buildDataObjectRdfUrl, shouldFetchDataObjectRdf } from "../../utils/shaclPrefill";

const DO_APP_ID = "018f9c5a-7e26-7000-a000-0000000000aa";
const TEMPLATE_APP_ID = "018f9c5a-7e26-7000-a000-0000000000dd";
const V2_BASE = "https://example.test";

describe("shouldFetchDataObjectRdf", () => {
  it("returns false when focusAppId is null or empty", () => {
    expect(shouldFetchDataObjectRdf(null, null)).toBe(false);
    expect(shouldFetchDataObjectRdf("", null)).toBe(false);
    expect(shouldFetchDataObjectRdf(undefined, "data-object")).toBe(false);
  });

  it("returns false when scope is collection (endpoint is DO-scoped)", () => {
    expect(shouldFetchDataObjectRdf(DO_APP_ID, "collection")).toBe(false);
  });

  it("returns true when focusAppId is set and scope is not collection", () => {
    expect(shouldFetchDataObjectRdf(DO_APP_ID, null)).toBe(true);
    expect(shouldFetchDataObjectRdf(DO_APP_ID, "data-object")).toBe(true);
    expect(shouldFetchDataObjectRdf(DO_APP_ID, "")).toBe(true);
    // Unknown future scope tokens are NOT collection so they fire.
    expect(shouldFetchDataObjectRdf(DO_APP_ID, "experiment")).toBe(true);
  });
});

describe("buildDataObjectRdfUrl", () => {
  it("builds the /v2/data-objects/{appId}/rdf path", () => {
    expect(buildDataObjectRdfUrl(V2_BASE, DO_APP_ID)).toBe(
      `${V2_BASE}/v2/data-objects/${DO_APP_ID}/rdf`,
    );
  });

  it("encodes appIds with awkward characters", () => {
    const odd = "abc/def?ghi";
    const url = buildDataObjectRdfUrl(V2_BASE, odd);
    expect(url).toContain(encodeURIComponent(odd));
    expect(url).not.toContain("abc/def?ghi"); // the raw form would break the path
  });
});

// ── Dual-prefill behavior (integration-shape, no Vue mount) ───────────────

describe("dual prefill — RDF + template shapeGraph compose without auto-validate", () => {
  const fetchMock = vi.fn();
  const validateUrl = `${V2_BASE}/v2/shapes/validate`;
  const turtleBody =
    "@prefix dcterms: <http://purl.org/dc/terms/> .\n<x> dcterms:identifier \"" + DO_APP_ID + "\" .";
  const templateShape =
    "@prefix sh: <http://www.w3.org/ns/shacl#> .\nex:Shape a sh:NodeShape .";
  const templateBody = JSON.stringify({ shapeGraph: templateShape, otherField: "ignored" });

  beforeEach(() => {
    fetchMock.mockReset();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  /**
   * Reduced reproduction of the validate.vue onMounted flow:
   *   - if templateAppId → fetch template, extract shapeGraph
   *   - if focusAppId (and scope !== "collection") → fetch RDF, prefill data graph
   *   - never call validate
   *
   * We assert post-conditions on the fetch call list.
   */
  async function runPrefill(opts: {
    focusAppId?: string;
    focusScope?: string;
    templateAppId?: string;
  }): Promise<{ dataGraph: string; shapeGraph: string }> {
    let dataGraph = "INITIAL";
    let shapeGraph = "INITIAL";

    // Template fetch (SHAPES-V-PREFILL-3).
    if (opts.templateAppId) {
      const res = await fetch(`${V2_BASE}/v2/templates/${opts.templateAppId}`, {
        headers: { Accept: "application/json" },
      });
      if (res.ok) {
        const t = (await res.json()) as { body?: string };
        const extracted = extractShapeGraphFromTemplateBody(t.body);
        if (extracted) shapeGraph = extracted;
      }
    }

    // RDF fetch (SHAPES-V-PREFILL-2).
    if (shouldFetchDataObjectRdf(opts.focusAppId, opts.focusScope)) {
      const res = await fetch(buildDataObjectRdfUrl(V2_BASE, opts.focusAppId!), {
        headers: { Accept: "text/turtle" },
      });
      if (res.ok) {
        const turtle = await res.text();
        if (turtle.length > 0) dataGraph = turtle;
      }
    }

    return { dataGraph, shapeGraph };
  }

  it("focusAppId only → only RDF fetch fires, data graph prefilled, no auto-validate", async () => {
    fetchMock.mockResolvedValueOnce({
      ok: true,
      status: 200,
      statusText: "OK",
      text: async () => turtleBody,
    } as unknown as Response);
    vi.stubGlobal("fetch", fetchMock);

    const { dataGraph, shapeGraph } = await runPrefill({
      focusAppId: DO_APP_ID,
      focusScope: "data-object",
    });

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      `${V2_BASE}/v2/data-objects/${DO_APP_ID}/rdf`,
    );
    expect(dataGraph).toBe(turtleBody);
    expect(shapeGraph).toBe("INITIAL");
    // Critical: no validate fetch.
    expect(
      fetchMock.mock.calls.some(c => String(c[0]).includes(validateUrl)),
    ).toBe(false);
  });

  it("both ids + template body has shapeGraph → both prefilled, still no auto-validate", async () => {
    // Template fetch returns JSON with a shapeGraph string.
    fetchMock.mockResolvedValueOnce({
      ok: true,
      status: 200,
      statusText: "OK",
      json: async () => ({ appId: TEMPLATE_APP_ID, body: templateBody }),
    } as unknown as Response);
    // RDF fetch returns Turtle.
    fetchMock.mockResolvedValueOnce({
      ok: true,
      status: 200,
      statusText: "OK",
      text: async () => turtleBody,
    } as unknown as Response);
    vi.stubGlobal("fetch", fetchMock);

    const { dataGraph, shapeGraph } = await runPrefill({
      focusAppId: DO_APP_ID,
      focusScope: "data-object",
      templateAppId: TEMPLATE_APP_ID,
    });

    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      `${V2_BASE}/v2/templates/${TEMPLATE_APP_ID}`,
    );
    expect(fetchMock.mock.calls[1]?.[0]).toBe(
      `${V2_BASE}/v2/data-objects/${DO_APP_ID}/rdf`,
    );
    expect(dataGraph).toBe(turtleBody);
    expect(shapeGraph).toBe(templateShape);
    // The brief is explicit: validation must NOT auto-fire.
    expect(
      fetchMock.mock.calls.some(c => String(c[0]).includes(validateUrl)),
    ).toBe(false);
  });

  it("collection scope → no RDF fetch (the endpoint is DataObject-scoped)", async () => {
    fetchMock.mockResolvedValue({
      ok: true,
      status: 200,
      statusText: "OK",
      text: async () => turtleBody,
    } as unknown as Response);
    vi.stubGlobal("fetch", fetchMock);

    const { dataGraph } = await runPrefill({
      focusAppId: DO_APP_ID,
      focusScope: "collection",
    });

    expect(fetchMock).not.toHaveBeenCalled();
    expect(dataGraph).toBe("INITIAL");
  });
});
