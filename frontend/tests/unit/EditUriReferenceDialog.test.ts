/**
 * REF-EDIT-6 — unit tests for EditUriReferenceDialog logic.
 *
 * Tests cover pure helper logic (validation, PATCH body construction,
 * saved-emit behaviour, relationship-null handling) without mounting the
 * full Nuxt / Vuetify component tree. Playwright E2E tests cover the visual
 * rendering (tracked in aidocs/16 REF-EDIT-6 row).
 *
 * Same inlined-helper pattern as EditFileReferenceDialog.test.ts.
 */

import { describe, it, expect } from "vitest";

// ── Inline helpers from the component ────────────────────────────────────────

/**
 * Validation: name and uri are required.
 */
function isFormValid(name: string, uri: string): boolean {
  return name.trim().length > 0 && uri.trim().length > 0;
}

/**
 * Build the PATCH body for PATCH /v2/references/{appId} (unified surface).
 * Returns null when the form is invalid.
 */
function buildPatchBody(
  name: string,
  uri: string,
  relationship: string,
): { name: string; uri: string; relationship: string | null } | null {
  if (!isFormValid(name, uri)) return null;
  return {
    name: name.trim(),
    uri: uri.trim(),
    // Empty relationship field → send null to clear on backend
    relationship: relationship.trim() || null,
  };
}

/**
 * Simulate the component's save flow.
 */
async function simulateSave(opts: {
  name: string;
  uri: string;
  relationship: string;
  fetchOk: boolean;
}): Promise<{ emittedSaved: boolean }> {
  const { name, uri, relationship, fetchOk } = opts;
  const body = buildPatchBody(name, uri, relationship);
  if (!body) return { emittedSaved: false };
  if (!fetchOk) return { emittedSaved: false };
  return { emittedSaved: true };
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe("EditUriReferenceDialog — isFormValid", () => {
  it("is invalid when name is empty", () => {
    expect(isFormValid("", "https://example.com")).toBe(false);
  });

  it("is invalid when uri is empty", () => {
    expect(isFormValid("My Link", "")).toBe(false);
  });

  it("is invalid when both are empty", () => {
    expect(isFormValid("", "")).toBe(false);
  });

  it("is invalid when name is only whitespace", () => {
    expect(isFormValid("   ", "https://example.com")).toBe(false);
  });

  it("is invalid when uri is only whitespace", () => {
    expect(isFormValid("My Link", "   ")).toBe(false);
  });

  it("is valid when both name and uri are non-empty", () => {
    expect(isFormValid("DLR Homepage", "https://www.dlr.de")).toBe(true);
  });
});

describe("EditUriReferenceDialog — buildPatchBody", () => {
  it("returns null for empty name", () => {
    expect(buildPatchBody("", "https://example.com", "seeAlso")).toBeNull();
  });

  it("returns null for empty uri", () => {
    expect(buildPatchBody("My Link", "", "seeAlso")).toBeNull();
  });

  it("trims name and uri", () => {
    const body = buildPatchBody(
      "  DLR Homepage  ",
      "  https://www.dlr.de  ",
      "seeAlso",
    );
    expect(body?.name).toBe("DLR Homepage");
    expect(body?.uri).toBe("https://www.dlr.de");
  });

  it("keeps non-blank relationship as-is", () => {
    const body = buildPatchBody("My Link", "https://example.com", "seeAlso");
    expect(body?.relationship).toBe("seeAlso");
  });

  it("sends null relationship when relationship is empty string", () => {
    const body = buildPatchBody("My Link", "https://example.com", "");
    expect(body?.relationship).toBeNull();
  });

  it("sends null relationship when relationship is only whitespace", () => {
    const body = buildPatchBody("My Link", "https://example.com", "   ");
    expect(body?.relationship).toBeNull();
  });
});

describe("EditUriReferenceDialog — save emits saved on success", () => {
  it("emits saved when fetch succeeds", async () => {
    const result = await simulateSave({
      name: "DLR Homepage",
      uri: "https://www.dlr.de",
      relationship: "seeAlso",
      fetchOk: true,
    });
    expect(result.emittedSaved).toBe(true);
  });

  it("does NOT emit saved when fetch fails", async () => {
    const result = await simulateSave({
      name: "DLR Homepage",
      uri: "https://www.dlr.de",
      relationship: "seeAlso",
      fetchOk: false,
    });
    expect(result.emittedSaved).toBe(false);
  });

  it("does NOT emit saved when name is blank", async () => {
    const result = await simulateSave({
      name: "  ",
      uri: "https://www.dlr.de",
      relationship: "",
      fetchOk: true,
    });
    expect(result.emittedSaved).toBe(false);
  });

  it("does NOT emit saved when uri is blank", async () => {
    const result = await simulateSave({
      name: "My Link",
      uri: "",
      relationship: "",
      fetchOk: true,
    });
    expect(result.emittedSaved).toBe(false);
  });
});
