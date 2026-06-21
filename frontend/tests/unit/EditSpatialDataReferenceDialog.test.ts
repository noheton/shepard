/**
 * REF-EDIT-SPATIAL — unit tests for EditSpatialDataReferenceDialog logic.
 *
 * Tests cover the pure helper logic (validation, PATCH body construction,
 * saved-emit behaviour) without mounting the full Nuxt / Vuetify component
 * tree. Playwright E2E tests cover the visual rendering (tracked in aidocs/16
 * REF-EDIT-SPATIAL row).
 *
 * Mirrors the pattern from EditFileReferenceDialog.test.ts (REF-EDIT-3).
 */

import { describe, it, expect } from "vitest";

// ── Inline helpers from the component ────────────────────────────────────────

/**
 * Validation: name is required and must differ from the current name.
 * Returns an error string, or "" when valid.
 */
function validateName(
  newName: string,
  currentName: string,
): { nameError: string; isValid: boolean } {
  const trimmed = newName.trim();
  const nameError = trimmed ? "" : "Name is required";
  const isValid = !nameError && trimmed !== currentName.trim();
  return { nameError, isValid };
}

/**
 * Build the PATCH body. Returns null when the name would be rejected.
 */
function buildPatchBody(newName: string): { name: string } | null {
  const trimmed = newName.trim();
  if (!trimmed) return null;
  return { name: trimmed };
}

/**
 * Simulate the component's save flow.
 */
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

describe("EditSpatialDataReferenceDialog — validateName", () => {
  it("is invalid and shows error when name is empty", () => {
    const { nameError, isValid } = validateName("", "scan_run_01");
    expect(nameError).toBe("Name is required");
    expect(isValid).toBe(false);
  });

  it("is invalid and shows error when name is only whitespace", () => {
    const { nameError, isValid } = validateName("   ", "scan_run_01");
    expect(nameError).toBe("Name is required");
    expect(isValid).toBe(false);
  });

  it("is invalid when name equals the current name (unchanged)", () => {
    const { nameError, isValid } = validateName("scan_run_01", "scan_run_01");
    expect(nameError).toBe("");
    expect(isValid).toBe(false);
  });

  it("is invalid when trimmed name equals trimmed current name", () => {
    const { nameError, isValid } = validateName("  scan_run_01  ", "scan_run_01");
    expect(nameError).toBe("");
    expect(isValid).toBe(false);
  });

  it("is valid when name is non-empty and differs from current", () => {
    const { nameError, isValid } = validateName("scan_run_02", "scan_run_01");
    expect(nameError).toBe("");
    expect(isValid).toBe(true);
  });
});

describe("EditSpatialDataReferenceDialog — buildPatchBody", () => {
  it("returns null for empty name", () => {
    expect(buildPatchBody("")).toBeNull();
  });

  it("returns null for whitespace-only name", () => {
    expect(buildPatchBody("  ")).toBeNull();
  });

  it("returns trimmed name in body", () => {
    expect(buildPatchBody("  mffd_q1_afp_scan  ")).toEqual({
      name: "mffd_q1_afp_scan",
    });
  });

  it("returns unchanged name when no surrounding whitespace", () => {
    expect(buildPatchBody("lumen_tr004_spatial")).toEqual({
      name: "lumen_tr004_spatial",
    });
  });
});

describe("EditSpatialDataReferenceDialog — save emits saved on success", () => {
  it("emits saved with the trimmed new name when fetch succeeds", async () => {
    const result = await simulateSave({
      currentName: "scan_run_01",
      newName: "  scan_run_01_corrected  ",
      fetchOk: true,
    });
    expect(result.emittedSaved).toBe(true);
    expect(result.emittedWith).toBe("scan_run_01_corrected");
  });

  it("does NOT emit saved when fetch fails", async () => {
    const result = await simulateSave({
      currentName: "scan_run_01",
      newName: "scan_run_02",
      fetchOk: false,
    });
    expect(result.emittedSaved).toBe(false);
    expect(result.emittedWith).toBeNull();
  });

  it("does NOT emit saved when name is blank", async () => {
    const result = await simulateSave({
      currentName: "scan_run_01",
      newName: "",
      fetchOk: true,
    });
    expect(result.emittedSaved).toBe(false);
    expect(result.emittedWith).toBeNull();
  });

  it("does NOT emit saved when name is unchanged", async () => {
    const result = await simulateSave({
      currentName: "same_scan",
      newName: "same_scan",
      fetchOk: true,
    });
    expect(result.emittedSaved).toBe(false);
    expect(result.emittedWith).toBeNull();
  });
});
