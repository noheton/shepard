/**
 * REF-EDIT-4 — unit tests for EditFileBundleReferenceDialog logic.
 *
 * Tests cover the pure helper logic (validation, PATCH body construction,
 * saved-emit behaviour) without mounting the full Nuxt / Vuetify component
 * tree. Playwright E2E tests cover the visual rendering (tracked in aidocs/16
 * REF-EDIT-4 row).
 *
 * The functions below are inlined from the component script, mirroring the
 * pattern used in EditFileReferenceDialog.test.ts and
 * EditUriReferenceDialog.test.ts.
 */

import { describe, it, expect } from "vitest";

// ── Inline helpers from the component ────────────────────────────────────────

/**
 * Validation: name must be non-blank; at least one field must differ from
 * the current values. Returns an error string for name, or "" when valid.
 */
function validateBundle(opts: {
  newName: string;
  newDescription: string;
  currentName: string;
  currentDescription?: string | null;
}): { nameError: string; isValid: boolean } {
  const { newName, newDescription, currentName, currentDescription } = opts;
  const trimmedName = newName.trim();
  const nameError = trimmedName ? "" : "Name is required";
  const trimmedDesc = newDescription.trim() || null;
  const origDesc = currentDescription?.trim() || null;
  const hasChanged =
    trimmedName !== currentName.trim() || trimmedDesc !== origDesc;
  const isValid = !nameError && hasChanged;
  return { nameError, isValid };
}

/**
 * Build the RFC 7396 merge-patch body, including only fields that changed.
 * Returns null if name is blank.
 */
function buildPatchBody(opts: {
  newName: string;
  newDescription: string;
  currentName: string;
  currentDescription?: string | null;
}): Record<string, string | null> | null {
  const { newName, newDescription, currentName, currentDescription } = opts;
  const trimmedName = newName.trim();
  if (!trimmedName) return null;
  const body: Record<string, string | null> = {};
  if (trimmedName !== currentName.trim()) {
    body.name = trimmedName;
  }
  const trimmedDesc = newDescription.trim() || null;
  const origDesc = currentDescription?.trim() || null;
  if (trimmedDesc !== origDesc) {
    body.description = trimmedDesc;
  }
  return body;
}

/**
 * Simulate the component's save flow.
 * Returns { emittedSaved, emittedWith } so tests can assert emit behaviour.
 */
async function simulateSave(opts: {
  currentName: string;
  currentDescription?: string | null;
  newName: string;
  newDescription: string;
  fetchOk: boolean;
}): Promise<{
  emittedSaved: boolean;
  emittedWith: { name: string; description: string | null } | null;
}> {
  const { currentName, currentDescription, newName, newDescription, fetchOk } =
    opts;
  const { isValid } = validateBundle({
    newName,
    newDescription,
    currentName,
    currentDescription,
  });
  if (!isValid) return { emittedSaved: false, emittedWith: null };

  const body = buildPatchBody({
    newName,
    newDescription,
    currentName,
    currentDescription,
  });
  if (!body) return { emittedSaved: false, emittedWith: null };

  if (!fetchOk) return { emittedSaved: false, emittedWith: null };

  return {
    emittedSaved: true,
    emittedWith: {
      name: newName.trim(),
      description: newDescription.trim() || null,
    },
  };
}

// ── Tests — validateBundle ────────────────────────────────────────────────────

describe("EditFileBundleReferenceDialog — validateBundle", () => {
  it("is invalid when name is empty", () => {
    const { nameError, isValid } = validateBundle({
      newName: "",
      newDescription: "",
      currentName: "old name",
    });
    expect(nameError).toBe("Name is required");
    expect(isValid).toBe(false);
  });

  it("is invalid when name is only whitespace", () => {
    const { nameError, isValid } = validateBundle({
      newName: "   ",
      newDescription: "",
      currentName: "old name",
    });
    expect(nameError).toBe("Name is required");
    expect(isValid).toBe(false);
  });

  it("is invalid when nothing changed", () => {
    const { isValid } = validateBundle({
      newName: "my bundle",
      newDescription: "some description",
      currentName: "my bundle",
      currentDescription: "some description",
    });
    expect(isValid).toBe(false);
  });

  it("is valid when name changed", () => {
    const { nameError, isValid } = validateBundle({
      newName: "new name",
      newDescription: "",
      currentName: "old name",
    });
    expect(nameError).toBe("");
    expect(isValid).toBe(true);
  });

  it("is valid when description added (name unchanged)", () => {
    const { isValid } = validateBundle({
      newName: "my bundle",
      newDescription: "added description",
      currentName: "my bundle",
      currentDescription: null,
    });
    expect(isValid).toBe(true);
  });

  it("is valid when description cleared (name unchanged)", () => {
    const { isValid } = validateBundle({
      newName: "my bundle",
      newDescription: "",
      currentName: "my bundle",
      currentDescription: "old description",
    });
    expect(isValid).toBe(true);
  });

  it("treats whitespace-only description as null for change detection", () => {
    // "   " and null are equivalent
    const { isValid } = validateBundle({
      newName: "my bundle",
      newDescription: "   ",
      currentName: "my bundle",
      currentDescription: null,
    });
    expect(isValid).toBe(false);
  });
});

// ── Tests — buildPatchBody ────────────────────────────────────────────────────

describe("EditFileBundleReferenceDialog — buildPatchBody", () => {
  it("returns null when name is blank", () => {
    expect(
      buildPatchBody({
        newName: "",
        newDescription: "",
        currentName: "old",
      }),
    ).toBeNull();
  });

  it("includes only changed name", () => {
    const body = buildPatchBody({
      newName: "  new bundle  ",
      newDescription: "same desc",
      currentName: "old bundle",
      currentDescription: "same desc",
    });
    expect(body).toEqual({ name: "new bundle" });
  });

  it("includes only changed description", () => {
    const body = buildPatchBody({
      newName: "same bundle",
      newDescription: "new description",
      currentName: "same bundle",
      currentDescription: null,
    });
    expect(body).toEqual({ description: "new description" });
  });

  it("sets description to null when cleared", () => {
    const body = buildPatchBody({
      newName: "same bundle",
      newDescription: "",
      currentName: "same bundle",
      currentDescription: "old description",
    });
    expect(body).toEqual({ description: null });
  });

  it("includes both name and description when both changed", () => {
    const body = buildPatchBody({
      newName: "new name",
      newDescription: "new desc",
      currentName: "old name",
      currentDescription: "old desc",
    });
    expect(body).toEqual({ name: "new name", description: "new desc" });
  });

  it("returns empty object when name is same and no description change (no-op)", () => {
    // No fields changed → body is empty (save guard prevents this case normally)
    const body = buildPatchBody({
      newName: "same",
      newDescription: "same",
      currentName: "same",
      currentDescription: "same",
    });
    expect(body).toEqual({});
  });
});

// ── Tests — simulateSave ──────────────────────────────────────────────────────

describe("EditFileBundleReferenceDialog — save emits saved on success", () => {
  it("emits saved with trimmed name and description when fetch succeeds", async () => {
    const result = await simulateSave({
      currentName: "old bundle",
      currentDescription: null,
      newName: "  new bundle  ",
      newDescription: "  A description  ",
      fetchOk: true,
    });
    expect(result.emittedSaved).toBe(true);
    expect(result.emittedWith).toEqual({
      name: "new bundle",
      description: "A description",
    });
  });

  it("emits saved with null description when description is cleared", async () => {
    const result = await simulateSave({
      currentName: "old bundle",
      currentDescription: "existing desc",
      newName: "old bundle",
      newDescription: "",
      fetchOk: true,
    });
    expect(result.emittedSaved).toBe(true);
    expect(result.emittedWith?.description).toBeNull();
  });

  it("does NOT emit saved when fetch fails", async () => {
    const result = await simulateSave({
      currentName: "old",
      currentDescription: null,
      newName: "new",
      newDescription: "",
      fetchOk: false,
    });
    expect(result.emittedSaved).toBe(false);
  });

  it("does NOT emit saved when name is blank", async () => {
    const result = await simulateSave({
      currentName: "old",
      currentDescription: null,
      newName: "",
      newDescription: "",
      fetchOk: true,
    });
    expect(result.emittedSaved).toBe(false);
  });

  it("does NOT emit saved when nothing changed", async () => {
    const result = await simulateSave({
      currentName: "same bundle",
      currentDescription: "same desc",
      newName: "same bundle",
      newDescription: "same desc",
      fetchOk: true,
    });
    expect(result.emittedSaved).toBe(false);
  });
});
