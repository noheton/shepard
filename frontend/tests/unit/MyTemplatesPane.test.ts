/**
 * TPL-ME-BROWSE-1 — pure-logic tests for the MyTemplatesPane filter +
 * shippedVia heuristic. Mirrors the pattern used by
 * `DataObjectNotebooksPane.test.ts`: test the logic units rather than
 * mount the component, because the Vuetify + Nuxt rendering chain
 * needs the full app context.
 */
import { describe, it, expect } from "vitest";
import type { ShepardTemplateIO } from "@dlr-shepard/backend-client";

// ── Logic units (mirror the inline functions in MyTemplatesPane.vue) ──

function shippedVia(t: ShepardTemplateIO): string {
  const tags = t.tags ?? [];
  if (tags.some(tag => tag.startsWith("system:") || tag === "system")) return "system";
  if (tags.some(tag => tag.startsWith("git:") || tag === "git")) return "git import";
  return "admin upload";
}

function filterTemplates(
  templates: ShepardTemplateIO[],
  q: string,
): ShepardTemplateIO[] {
  const needle = q.trim().toLowerCase();
  if (!needle) return templates;
  return templates.filter(
    t =>
      (t.name ?? "").toLowerCase().includes(needle) ||
      (t.description ?? "").toLowerCase().includes(needle) ||
      (t.templateKind ?? "").toLowerCase().includes(needle) ||
      (t.tags ?? []).some(tag => tag.toLowerCase().includes(needle)),
  );
}

const mk = (overrides: Partial<ShepardTemplateIO>): ShepardTemplateIO => ({
  appId: "00000000-0000-7000-8000-000000000001",
  name: "default",
  templateKind: "DATAOBJECT_RECIPE",
  version: 1,
  body: "{}",
  retired: false,
  ...overrides,
});

describe("MyTemplatesPane — shippedVia heuristic (TPL-ME-BROWSE-1)", () => {
  it("returns 'system' for a system-tagged template", () => {
    expect(shippedVia(mk({ tags: ["system:bootstrap"] }))).toBe("system");
    expect(shippedVia(mk({ tags: ["system"] }))).toBe("system");
  });

  it("returns 'git import' for a git-tagged template", () => {
    expect(shippedVia(mk({ tags: ["git:github.com/dlr-shepard/templates"] }))).toBe(
      "git import",
    );
    expect(shippedVia(mk({ tags: ["git"] }))).toBe("git import");
  });

  it("returns 'admin upload' as the default", () => {
    expect(shippedVia(mk({ tags: [] }))).toBe("admin upload");
    expect(shippedVia(mk({ tags: ["lumen", "tutorial"] }))).toBe("admin upload");
    expect(shippedVia(mk({}))).toBe("admin upload");
  });

  it("prefers system over git when both tags present", () => {
    expect(shippedVia(mk({ tags: ["git:src", "system:bootstrap"] }))).toBe("system");
  });
});

describe("MyTemplatesPane — filter (TPL-ME-BROWSE-1)", () => {
  const corpus = [
    mk({ name: "LUMEN hotfire run", tags: ["lumen", "rocket"] }),
    mk({
      name: "MFFD AFP step",
      description: "Layup process step recipe",
      templateKind: "COLLECTION_RECIPE",
    }),
    mk({ name: "Generic recipe", tags: ["system:bootstrap"] }),
  ];

  it("returns everything on empty query", () => {
    expect(filterTemplates(corpus, "").length).toBe(3);
    expect(filterTemplates(corpus, "   ").length).toBe(3);
  });

  it("matches case-insensitively against name", () => {
    expect(filterTemplates(corpus, "lumen").map(t => t.name)).toEqual([
      "LUMEN hotfire run",
    ]);
  });

  it("matches against description", () => {
    expect(filterTemplates(corpus, "layup").map(t => t.name)).toEqual([
      "MFFD AFP step",
    ]);
  });

  it("matches against templateKind", () => {
    expect(filterTemplates(corpus, "collection_recipe").map(t => t.name)).toEqual([
      "MFFD AFP step",
    ]);
  });

  it("matches against tags", () => {
    expect(filterTemplates(corpus, "rocket").map(t => t.name)).toEqual([
      "LUMEN hotfire run",
    ]);
    expect(filterTemplates(corpus, "system:bootstrap").map(t => t.name)).toEqual([
      "Generic recipe",
    ]);
  });

  it("returns empty when no match", () => {
    expect(filterTemplates(corpus, "no-such-thing")).toEqual([]);
  });
});
