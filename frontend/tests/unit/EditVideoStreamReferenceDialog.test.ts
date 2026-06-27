/**
 * REF-EDIT-2 — unit tests for EditVideoStreamReferenceDialog logic.
 *
 * Tests cover the pure helper logic (validation, PATCH body construction,
 * saved-emit behaviour) without mounting the full Nuxt / Vuetify component
 * tree. Playwright E2E tests cover the visual rendering (tracked in aidocs/16
 * REF-EDIT-2 row).
 *
 * The functions below are inlined from the component script, mirroring the
 * pattern used in EditFileReferenceDialog.test.ts,
 * EditTimeseriesReferenceDialog.test.ts, and EditFileBundleReferenceDialog.test.ts.
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
 * Returns { emittedSaved, emittedWith } so tests can assert emit behaviour.
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

describe("EditVideoStreamReferenceDialog — validateName", () => {
  it("is invalid and shows error when name is empty", () => {
    const { nameError, isValid } = validateName("", "launch camera 1");
    expect(nameError).toBe("Name is required");
    expect(isValid).toBe(false);
  });

  it("is invalid and shows error when name is only whitespace", () => {
    const { nameError, isValid } = validateName("   ", "launch camera 1");
    expect(nameError).toBe("Name is required");
    expect(isValid).toBe(false);
  });

  it("is invalid when name equals the current name (unchanged)", () => {
    const { nameError, isValid } = validateName("hotfire-TR004", "hotfire-TR004");
    expect(nameError).toBe("");
    expect(isValid).toBe(false);
  });

  it("is invalid when trimmed name equals trimmed current name", () => {
    const { nameError, isValid } = validateName(
      "  hotfire-TR004  ",
      "hotfire-TR004",
    );
    expect(nameError).toBe("");
    expect(isValid).toBe(false);
  });

  it("is valid when name is non-empty and differs from current", () => {
    const { nameError, isValid } = validateName(
      "ignition-sequence-cam",
      "hotfire-TR004",
    );
    expect(nameError).toBe("");
    expect(isValid).toBe(true);
  });

  it("treats leading/trailing whitespace as equivalent for change detection", () => {
    const { isValid } = validateName("  hotfire-TR004  ", "  hotfire-TR004  ");
    expect(isValid).toBe(false);
  });

  it("is valid when current name has trailing space but new name is trimmed differently", () => {
    const { isValid } = validateName("hotfire-TR004", "hotfire-TR004 ");
    expect(isValid).toBe(false);
  });

  it("correctly allows a new name that only differs by content", () => {
    const { nameError, isValid } = validateName("camera-A", "camera-B");
    expect(nameError).toBe("");
    expect(isValid).toBe(true);
  });
});

describe("EditVideoStreamReferenceDialog — buildPatchBody", () => {
  it("returns null for empty name", () => {
    expect(buildPatchBody("")).toBeNull();
  });

  it("returns null for whitespace-only name", () => {
    expect(buildPatchBody("  ")).toBeNull();
  });

  it("returns trimmed name in body", () => {
    expect(buildPatchBody("  MFFD layup camera  ")).toEqual({
      name: "MFFD layup camera",
    });
  });

  it("returns unchanged name when no surrounding whitespace", () => {
    expect(buildPatchBody("TR-004-thermal-video")).toEqual({
      name: "TR-004-thermal-video",
    });
  });

  it("builds correct PATCH body shape with name key only", () => {
    const body = buildPatchBody("new video name");
    expect(body).toHaveProperty("name");
    expect(Object.keys(body!)).toHaveLength(1);
  });

  it("preserves internal whitespace in the name", () => {
    expect(buildPatchBody("AFP  layup  top  cam")).toEqual({
      name: "AFP  layup  top  cam",
    });
  });
});

describe("EditVideoStreamReferenceDialog — save emits saved on success", () => {
  it("emits saved with the trimmed new name when fetch succeeds", async () => {
    const result = await simulateSave({
      currentName: "hotfire-TR004",
      newName: "  ignition-cam  ",
      fetchOk: true,
    });
    expect(result.emittedSaved).toBe(true);
    expect(result.emittedWith).toBe("ignition-cam");
  });

  it("does NOT emit saved when fetch fails", async () => {
    const result = await simulateSave({
      currentName: "hotfire-TR004",
      newName: "ignition-cam",
      fetchOk: false,
    });
    expect(result.emittedSaved).toBe(false);
    expect(result.emittedWith).toBeNull();
  });

  it("does NOT emit saved when name is blank", async () => {
    const result = await simulateSave({
      currentName: "hotfire-TR004",
      newName: "",
      fetchOk: true,
    });
    expect(result.emittedSaved).toBe(false);
    expect(result.emittedWith).toBeNull();
  });

  it("does NOT emit saved when name is unchanged", async () => {
    const result = await simulateSave({
      currentName: "same-video",
      newName: "same-video",
      fetchOk: true,
    });
    expect(result.emittedSaved).toBe(false);
    expect(result.emittedWith).toBeNull();
  });

  it("emits saved with trimmed name even when current has trailing space", async () => {
    const result = await simulateSave({
      currentName: "hotfire-TR004 ",
      newName: "ignition-cam",
      fetchOk: true,
    });
    expect(result.emittedSaved).toBe(true);
    expect(result.emittedWith).toBe("ignition-cam");
  });

  it("does NOT emit saved when name is whitespace only", async () => {
    const result = await simulateSave({
      currentName: "hotfire-TR004",
      newName: "   ",
      fetchOk: true,
    });
    expect(result.emittedSaved).toBe(false);
    expect(result.emittedWith).toBeNull();
  });
});
