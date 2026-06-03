/**
 * TPL-INHERIT — pure-logic tests for the AdminTemplateDialog parent-template
 * picker: cycle-exclusion (self + descendants) and same-kind / non-retired
 * candidate filtering. Mirrors MyTemplatesPane.test.ts: test the logic units
 * rather than mount the component (the Vuetify + Nuxt chain needs the full app
 * context). Design: aidocs/integrations/123 §4.
 */
import { describe, it, expect } from "vitest";
import type { ShepardTemplateIO } from "@dlr-shepard/backend-client";

// ── Logic units (mirror the computed props in AdminTemplateDialog.vue) ──

function forbiddenParentAppIds(
  selfAppId: string | undefined,
  all: ShepardTemplateIO[],
): Set<string> {
  const forbidden = new Set<string>();
  if (!selfAppId) return forbidden;
  forbidden.add(selfAppId);
  let changed = true;
  while (changed) {
    changed = false;
    for (const t of all) {
      if (
        t.parentTemplateAppId &&
        forbidden.has(t.parentTemplateAppId) &&
        !forbidden.has(t.appId)
      ) {
        forbidden.add(t.appId);
        changed = true;
      }
    }
  }
  return forbidden;
}

function parentCandidates(
  selfAppId: string | undefined,
  templateKind: string,
  all: ShepardTemplateIO[],
): string[] {
  const forbidden = forbiddenParentAppIds(selfAppId, all);
  return all
    .filter(
      (t) => t.templateKind === templateKind && !t.retired && !forbidden.has(t.appId),
    )
    .map((t) => t.appId);
}

const mk = (overrides: Partial<ShepardTemplateIO>): ShepardTemplateIO => ({
  appId: "a",
  name: "t",
  templateKind: "DATAOBJECT_RECIPE",
  version: 1,
  body: "{}",
  retired: false,
  ...overrides,
});

describe("AdminTemplateDialog — parent picker cycle exclusion (TPL-INHERIT)", () => {
  it("excludes self", () => {
    const all = [mk({ appId: "a" }), mk({ appId: "b" })];
    expect(forbiddenParentAppIds("a", all).has("a")).toBe(true);
  });

  it("excludes direct children", () => {
    // b extends a → a must not pick b as parent.
    const all = [mk({ appId: "a" }), mk({ appId: "b", parentTemplateAppId: "a" })];
    const forbidden = forbiddenParentAppIds("a", all);
    expect(forbidden.has("b")).toBe(true);
  });

  it("excludes transitive descendants (grandchildren)", () => {
    // a ← b ← c. a must not pick b or c.
    const all = [
      mk({ appId: "a" }),
      mk({ appId: "b", parentTemplateAppId: "a" }),
      mk({ appId: "c", parentTemplateAppId: "b" }),
    ];
    const forbidden = forbiddenParentAppIds("a", all);
    expect(forbidden.has("b")).toBe(true);
    expect(forbidden.has("c")).toBe(true);
  });

  it("allows unrelated templates as parents", () => {
    const all = [
      mk({ appId: "a" }),
      mk({ appId: "b", parentTemplateAppId: "a" }),
      mk({ appId: "x" }),
    ];
    expect(forbiddenParentAppIds("a", all).has("x")).toBe(false);
  });

  it("returns empty forbidden set for a brand-new (no-self) template", () => {
    const all = [mk({ appId: "a" }), mk({ appId: "b" })];
    expect(forbiddenParentAppIds(undefined, all).size).toBe(0);
  });
});

describe("AdminTemplateDialog — parent candidate filtering (TPL-INHERIT)", () => {
  it("only lists same-kind templates", () => {
    const all = [
      mk({ appId: "a", templateKind: "DATAOBJECT_RECIPE" }),
      mk({ appId: "b", templateKind: "EXPERIMENT_RECIPE" }),
    ];
    const cands = parentCandidates("self", "DATAOBJECT_RECIPE", all);
    expect(cands).toContain("a");
    expect(cands).not.toContain("b");
  });

  it("excludes retired templates", () => {
    const all = [
      mk({ appId: "a", retired: true }),
      mk({ appId: "b", retired: false }),
    ];
    const cands = parentCandidates("self", "DATAOBJECT_RECIPE", all);
    expect(cands).not.toContain("a");
    expect(cands).toContain("b");
  });

  it("excludes self and descendants from candidates", () => {
    const all = [
      mk({ appId: "self" }),
      mk({ appId: "child", parentTemplateAppId: "self" }),
      mk({ appId: "other" }),
    ];
    const cands = parentCandidates("self", "DATAOBJECT_RECIPE", all);
    expect(cands).not.toContain("self");
    expect(cands).not.toContain("child");
    expect(cands).toContain("other");
  });
});
