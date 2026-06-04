/**
 * TOOLS-CONTEXT-* — unit tests for the in-context tool entry-point
 * inventory + SPARQL prefill query builder.
 *
 * Pure-helper-pattern tests; no component mount. Mirrors
 * toolsLanding.test.ts shape.
 */
import { describe, it, expect } from "vitest";

import {
  COLLECTION_CONTEXT_TOOLS,
  DATA_OBJECT_CONTEXT_TOOLS,
  buildScopedSparqlQuery,
  getContextTools,
} from "../../utils/toolsContext";

const SAMPLE_APP_ID = "0197b6a2-7b4c-7000-8a3b-1234567890ab";

// ── COLLECTION_CONTEXT_TOOLS ───────────────────────────────────────────────

describe("COLLECTION_CONTEXT_TOOLS", () => {
  it("exposes the three Collection-scope tool entries (SPARQL + Vocab + Create Template)", () => {
    expect(COLLECTION_CONTEXT_TOOLS.map(t => t.id).sort()).toEqual([
      "coll-create-template",
      "coll-sparql",
      "coll-vocab",
    ]);
  });

  it("every entry has a stable id + title + subtitle + icon + path + buildQuery", () => {
    for (const t of COLLECTION_CONTEXT_TOOLS) {
      expect(t.id.length).toBeGreaterThan(0);
      expect(t.title.length).toBeGreaterThan(0);
      expect(t.subtitle.length).toBeGreaterThan(0);
      expect(t.icon).toMatch(/^mdi-/);
      expect(t.path.startsWith("/")).toBe(true);
      expect(typeof t.buildQuery).toBe("function");
    }
  });

  it("coll-sparql builds a query object with the expected params", () => {
    const t = COLLECTION_CONTEXT_TOOLS.find(x => x.id === "coll-sparql")!;
    const q = t.buildQuery(SAMPLE_APP_ID);
    expect(q.repoAppId).toBe("internal");
    expect(q.focusAppId).toBe(SAMPLE_APP_ID);
    expect(q.scope).toBe("collection");
    expect(q.query).toContain("urn:shepard:collection:" + SAMPLE_APP_ID);
    expect(q.query).toContain("SELECT ?predicate ?object");
    expect(q.query).toContain("LIMIT 50");
  });

  it("coll-vocab builds a query carrying the usedBy filter + scope", () => {
    const t = COLLECTION_CONTEXT_TOOLS.find(x => x.id === "coll-vocab")!;
    const q = t.buildQuery(SAMPLE_APP_ID);
    expect(q.usedBy).toBe(SAMPLE_APP_ID);
    expect(q.scope).toBe("collection");
  });

  it("coll-create-template routes to /admin/templates with newTemplate=1 + scope", () => {
    const t = COLLECTION_CONTEXT_TOOLS.find(x => x.id === "coll-create-template")!;
    expect(t.path).toBe("/admin/templates");
    const q = t.buildQuery(SAMPLE_APP_ID);
    expect(q.newTemplate).toBe("1");
    expect(q.targetEntityAppId).toBe(SAMPLE_APP_ID);
    expect(q.scope).toBe("collection");
  });
});

// ── DATA_OBJECT_CONTEXT_TOOLS ──────────────────────────────────────────────

describe("DATA_OBJECT_CONTEXT_TOOLS", () => {
  it("exposes the five DataObject-scope tool entries", () => {
    expect(DATA_OBJECT_CONTEXT_TOOLS.map(t => t.id).sort()).toEqual([
      "do-create-template",
      "do-render",
      "do-shacl",
      "do-sparql",
      "do-vocab",
    ]);
  });

  it("every entry has a stable id + title + subtitle + icon + path + buildQuery", () => {
    for (const t of DATA_OBJECT_CONTEXT_TOOLS) {
      expect(t.id.length).toBeGreaterThan(0);
      expect(t.title.length).toBeGreaterThan(0);
      expect(t.subtitle.length).toBeGreaterThan(0);
      expect(t.icon).toMatch(/^mdi-/);
      expect(t.path.startsWith("/")).toBe(true);
      expect(typeof t.buildQuery).toBe("function");
    }
  });

  it("do-sparql carries scope=data-object and a DO-scoped prefill query", () => {
    const t = DATA_OBJECT_CONTEXT_TOOLS.find(x => x.id === "do-sparql")!;
    const q = t.buildQuery(SAMPLE_APP_ID);
    expect(q.scope).toBe("data-object");
    expect(q.focusAppId).toBe(SAMPLE_APP_ID);
    expect(q.query).toContain("urn:shepard:data-object:" + SAMPLE_APP_ID);
  });

  it("do-shacl routes to /shapes/validate with focusAppId + scope", () => {
    const t = DATA_OBJECT_CONTEXT_TOOLS.find(x => x.id === "do-shacl")!;
    expect(t.path).toBe("/shapes/validate");
    const q = t.buildQuery(SAMPLE_APP_ID);
    expect(q.focusAppId).toBe(SAMPLE_APP_ID);
    expect(q.scope).toBe("data-object");
  });

  it("do-render routes to /shapes/render with focusShepardId (existing param name)", () => {
    const t = DATA_OBJECT_CONTEXT_TOOLS.find(x => x.id === "do-render")!;
    expect(t.path).toBe("/shapes/render");
    const q = t.buildQuery(SAMPLE_APP_ID);
    // The /shapes/render page already reads `focusShepardId` from the
    // query string into its template-render form. We MUST use that
    // exact param name — not `focusAppId` — or the prefill won't fire.
    expect(q.focusShepardId).toBe(SAMPLE_APP_ID);
  });

  // ── TOOLS-CONTEXT-DO-TEMPLATE-DETECT-1 — conditional rendering ─────────

  it("do-shacl is hidden when no template is attached (enabledWhen=false)", () => {
    const t = DATA_OBJECT_CONTEXT_TOOLS.find(x => x.id === "do-shacl")!;
    expect(t.enabledWhen).toBeDefined();
    expect(t.enabledWhen!({ attachedTemplateAppId: null })).toBe(false);
    expect(t.enabledWhen!({})).toBe(false);
  });

  it("do-shacl is visible when a template is attached (enabledWhen=true)", () => {
    const t = DATA_OBJECT_CONTEXT_TOOLS.find(x => x.id === "do-shacl")!;
    expect(t.enabledWhen!({ attachedTemplateAppId: "tpl-001" })).toBe(true);
  });

  it("do-render is hidden when no template is attached", () => {
    const t = DATA_OBJECT_CONTEXT_TOOLS.find(x => x.id === "do-render")!;
    expect(t.enabledWhen).toBeDefined();
    expect(t.enabledWhen!({ attachedTemplateAppId: null })).toBe(false);
  });

  it("do-shacl prefills templateAppId when attachedTemplateAppId is passed in ctx", () => {
    const t = DATA_OBJECT_CONTEXT_TOOLS.find(x => x.id === "do-shacl")!;
    const q = t.buildQuery(SAMPLE_APP_ID, { attachedTemplateAppId: "tpl-001" });
    expect(q.templateAppId).toBe("tpl-001");
    expect(q.focusAppId).toBe(SAMPLE_APP_ID);
  });

  it("do-render prefills templateAppId when attachedTemplateAppId is passed in ctx", () => {
    const t = DATA_OBJECT_CONTEXT_TOOLS.find(x => x.id === "do-render")!;
    const q = t.buildQuery(SAMPLE_APP_ID, { attachedTemplateAppId: "tpl-002" });
    expect(q.templateAppId).toBe("tpl-002");
    expect(q.focusShepardId).toBe(SAMPLE_APP_ID);
  });

  it("do-sparql + do-vocab have no gate (enabledWhen undefined) and always render", () => {
    const sparql = DATA_OBJECT_CONTEXT_TOOLS.find(x => x.id === "do-sparql")!;
    const vocab  = DATA_OBJECT_CONTEXT_TOOLS.find(x => x.id === "do-vocab")!;
    expect(sparql.enabledWhen).toBeUndefined();
    expect(vocab.enabledWhen).toBeUndefined();
  });

  it("do-create-template routes to /admin/templates with newTemplate=1 + scope", () => {
    const t = DATA_OBJECT_CONTEXT_TOOLS.find(x => x.id === "do-create-template")!;
    expect(t.path).toBe("/admin/templates");
    const q = t.buildQuery(SAMPLE_APP_ID);
    expect(q.newTemplate).toBe("1");
    expect(q.targetEntityAppId).toBe(SAMPLE_APP_ID);
    expect(q.scope).toBe("data-object");
  });

  it("do-create-template has no enabledWhen gate (always visible)", () => {
    const t = DATA_OBJECT_CONTEXT_TOOLS.find(x => x.id === "do-create-template")!;
    expect(t.enabledWhen).toBeUndefined();
  });
});

// ── buildScopedSparqlQuery ─────────────────────────────────────────────────

describe("buildScopedSparqlQuery", () => {
  it("returns a query containing the collection IRI when scope is collection", () => {
    const q = buildScopedSparqlQuery("collection", SAMPLE_APP_ID);
    expect(q).toContain("<urn:shepard:collection:" + SAMPLE_APP_ID + ">");
    expect(q).toContain("SELECT ?predicate ?object");
  });

  it("returns a query containing the data-object IRI when scope is data-object", () => {
    const q = buildScopedSparqlQuery("data-object", SAMPLE_APP_ID);
    expect(q).toContain("<urn:shepard:data-object:" + SAMPLE_APP_ID + ">");
  });

  it("includes a LIMIT clause so the playground default doesn't fetch huge result sets", () => {
    const q = buildScopedSparqlQuery("collection", SAMPLE_APP_ID);
    expect(q).toMatch(/LIMIT 50/);
  });

  it("preserves the appId verbatim in the IRI (no URL-encoding double-pass)", () => {
    const q = buildScopedSparqlQuery("data-object", SAMPLE_APP_ID);
    expect(q).toContain(SAMPLE_APP_ID);
  });
});

// ── getContextTools dispatch ───────────────────────────────────────────────

describe("getContextTools", () => {
  it("returns the Collection inventory for scope=collection", () => {
    expect(getContextTools("collection")).toBe(COLLECTION_CONTEXT_TOOLS);
  });

  it("returns the DataObject inventory for scope=data-object", () => {
    expect(getContextTools("data-object")).toBe(DATA_OBJECT_CONTEXT_TOOLS);
  });
});
