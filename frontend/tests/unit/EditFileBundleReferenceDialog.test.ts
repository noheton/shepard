/**
 * REF-EDIT-4 — unit tests for EditFileBundleReferenceDialog logic.
 *
 * Tests cover the pure helper logic (validation, PATCH body construction,
 * saved-emit behaviour) without mounting the full Nuxt / Vuetify component
 * tree. Mirrors EditFileReferenceDialog.test.ts exactly.
 */

import { describe, it, expect } from "vitest";

// ── Inline helpers from the component ────────────────────────────────────────

function validateName(
  newName: string,
  currentName: string,
): { nameError: string; isValid: boolean } {
  const trimmed = newName.trim();
  const nameError = trimmed ? "" : "Name is required";
  const isValid = !nameError && trimmed !== currentName.trim();
  return { nameError, isValid };
}

function buildPatchBody(newName: string): { name: string } | null {
  const trimmed = newName.trim();
  if (!trimmed) return null;
  return { name: trimmed };
}

async function simulateSave(opts: {
  currentName: string;
  newName: string;
  fetchOk: boolean;
}): Promise<{ emittedSaved: boolean; emittedWith: string | null }> {
  const { currentName, newName, fetchOk } = opts;
  const { isValid } = validateName(newName, currentName);
  if (!isValid) return { emittedSaved: false, emittedWith: null };

  const body = buildPatchBody(newName);
  if (!body) return { emittedSaved: false, emittedWith: null };

  if (!fetchOk) return { emittedSaved: false, emittedWith: null };

  return { emittedSaved: true, emittedWith: body.name };
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe("EditFileBundleReferenceDialog — validateName", () => {
  it("is invalid and shows error when name is empty", () => {
    const { nameError, isValid } = validateName("", "old bundle");
    expect(nameError).toBe("Name is required");
    expect(isValid).toBe(false);
  });

  it("is invalid and shows error when name is only whitespace", () => {
    const { nameError, isValid } = validateName("   ", "old bundle");
    expect(nameError).toBe("Name is required");
    expect(isValid).toBe(false);
  });

  it("is invalid when name equals the current name (unchanged)", () => {
    const { nameError, isValid } = validateName("my bundle", "my bundle");
    expect(nameError).toBe("");
    expect(isValid).toBe(false);
  });

  it("is invalid when trimmed name equals trimmed current name", () => {
    const { nameError, isValid } = validateName("  my bundle  ", "my bundle");
    expect(nameError).toBe("");
    expect(isValid).toBe(false);
  });

  it("is valid when name is non-empty and differs from current", () => {
    const { nameError, isValid } = validateName("renamed bundle", "old bundle");
    expect(nameError).toBe("");
    expect(isValid).toBe(true);
  });
});

describe("EditFileBundleReferenceDialog — buildPatchBody", () => {
  it("returns null for empty name", () => {
    expect(buildPatchBody("")).toBeNull();
  });

  it("returns null for whitespace-only name", () => {
    expect(buildPatchBody("  ")).toBeNull();
  });

  it("returns trimmed name in body", () => {
    expect(buildPatchBody("  AFP run 2026-06-21  ")).toEqual({
      name: "AFP run 2026-06-21",
    });
  });

  it("returns unchanged name when no surrounding whitespace", () => {
    expect(buildPatchBody("TR-004-thermal-frames")).toEqual({
      name: "TR-004-thermal-frames",
    });
  });
});

describe("EditFileBundleReferenceDialog — save emits saved on success", () => {
  it("emits saved with the trimmed new name when fetch succeeds", async () => {
    const result = await simulateSave({
      currentName: "old bundle",
      newName: "  new bundle  ",
      fetchOk: true,
    });
    expect(result.emittedSaved).toBe(true);
    expect(result.emittedWith).toBe("new bundle");
  });

  it("does NOT emit saved when fetch fails", async () => {
    const result = await simulateSave({
      currentName: "old bundle",
      newName: "new bundle",
      fetchOk: false,
    });
    expect(result.emittedSaved).toBe(false);
    expect(result.emittedWith).toBeNull();
  });

  it("does NOT emit saved when name is blank", async () => {
    const result = await simulateSave({
      currentName: "old bundle",
      newName: "",
      fetchOk: true,
    });
    expect(result.emittedSaved).toBe(false);
    expect(result.emittedWith).toBeNull();
  });

  it("does NOT emit saved when name is unchanged", async () => {
    const result = await simulateSave({
      currentName: "same bundle",
      newName: "same bundle",
      fetchOk: true,
    });
    expect(result.emittedSaved).toBe(false);
    expect(result.emittedWith).toBeNull();
  });
});
