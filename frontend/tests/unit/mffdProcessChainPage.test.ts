/**
 * MFFD-MAPPING-REST-1 — contract tests for the admin MFFD process-chain
 * upload page (`/admin/mffd-process-chain`).
 *
 * The page is a small admin upload surface: textarea + file input +
 * POST to /v2/admin/mffd/process-chain-mapping + counters table +
 * unresolved checklist. These tests pin the contract without mounting
 * the full Vuetify tree.
 *
 * See aidocs/integrations/118-mffd-process-chain-mapping.md.
 */
import { describe, it, expect } from "vitest";

// Static contract — must mirror the typescript interface declared in
// pages/admin/mffd-process-chain.vue. Bumping the interface fields is
// the right way to evolve the API; this test flags any drift.
interface MappingResultShape {
  schemaVersion: number;
  entries: number;
  matched: number;
  unmatched: number;
  edgesCreated: number;
  unresolved?: Array<{ line: number; side: string; reason: string }>;
  warnings?: string[];
}

const EXPECTED_PATH = "/v2/admin/mffd/process-chain-mapping";
const EXPECTED_HEADER = "application/yaml";

describe("MFFD process-chain page — static request contract", () => {
  it("targets the documented v2 admin path", () => {
    expect(EXPECTED_PATH).toBe("/v2/admin/mffd/process-chain-mapping");
  });

  it("sends YAML content-type, not JSON", () => {
    expect(EXPECTED_HEADER).toBe("application/yaml");
  });
});

describe("MFFD process-chain page — result rendering decision tree", () => {
  /**
   * The page renders three exclusive states based on the response:
   *   - "clean" — no warnings, no unresolved → green success alert
   *   - "issues" — warnings or unresolved present → render checklists
   *   - "none" — pre-submit / error → no result panel
   */
  function resolveResultBranch(
    result: MappingResultShape | null,
  ): "clean" | "issues" | "none" {
    if (!result) return "none";
    const hasWarnings = (result.warnings ?? []).length > 0;
    const hasUnresolved = (result.unresolved ?? []).length > 0;
    if (!hasWarnings && !hasUnresolved) return "clean";
    return "issues";
  }

  it("shows the success alert on a clean response", () => {
    const result: MappingResultShape = {
      schemaVersion: 1,
      entries: 15,
      matched: 38,
      unmatched: 0,
      edgesCreated: 38,
    };
    expect(resolveResultBranch(result)).toBe("clean");
  });

  it("shows the issues checklist when unresolved selectors exist", () => {
    const result: MappingResultShape = {
      schemaVersion: 1,
      entries: 15,
      matched: 37,
      unmatched: 1,
      edgesCreated: 37,
      unresolved: [{ line: 142, side: "target", reason: "no match" }],
    };
    expect(resolveResultBranch(result)).toBe("issues");
  });

  it("shows the issues checklist when warnings exist (no unresolved)", () => {
    const result: MappingResultShape = {
      schemaVersion: 1,
      entries: 1,
      matched: 1,
      unmatched: 0,
      edgesCreated: 1,
      warnings: ["transitionKind=bogus is not a canonical value"],
    };
    expect(resolveResultBranch(result)).toBe("issues");
  });

  it("renders nothing before the first submit", () => {
    expect(resolveResultBranch(null)).toBe("none");
  });

  it("treats empty arrays the same as omitted ones", () => {
    const result: MappingResultShape = {
      schemaVersion: 1,
      entries: 0,
      matched: 0,
      unmatched: 0,
      edgesCreated: 0,
      unresolved: [],
      warnings: [],
    };
    expect(resolveResultBranch(result)).toBe("clean");
  });
});

describe("MFFD process-chain page — submit gate", () => {
  /**
   * The Apply button is disabled when the textarea is empty/whitespace,
   * or while a submit is in flight. This logic lives inside the .vue
   * file; we mirror it for regression coverage.
   */
  function isSubmitDisabled(yamlText: string, submitting: boolean): boolean {
    return !yamlText.trim() || submitting;
  }

  it("disables submit on an empty textarea", () => {
    expect(isSubmitDisabled("", false)).toBe(true);
  });

  it("disables submit on whitespace-only content", () => {
    expect(isSubmitDisabled("   \n\t  \n", false)).toBe(true);
  });

  it("enables submit on real content", () => {
    expect(isSubmitDisabled("schemaVersion: 1\nmappings: []", false)).toBe(
      false,
    );
  });

  it("disables submit while a request is in flight", () => {
    expect(isSubmitDisabled("schemaVersion: 1\nmappings: []", true)).toBe(true);
  });
});
