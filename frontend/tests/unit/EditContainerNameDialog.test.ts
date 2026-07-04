/**
 * P21-V2-METADATA-EDIT-2 — unit tests for EditContainerNameDialog logic.
 *
 * Tests cover the pure helper logic (validation, PUT body construction,
 * saved-emit behaviour) without mounting the full Nuxt / Vuetify component
 * tree. Playwright E2E tests cover the visual rendering.
 *
 * The functions below mirror the component script, following the pattern in
 * EditFileReferenceDialog.test.ts.
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

function buildPutBody(
  newName: string,
  fetchedStatus: string | null,
): { name: string; status?: string } | null {
  const trimmed = newName.trim();
  if (!trimmed) return null;
  const body: { name: string; status?: string } = { name: trimmed };
  if (fetchedStatus) body.status = fetchedStatus;
  return body;
}

async function simulateSave(opts: {
  currentName: string;
  newName: string;
  fetchedStatus: string | null;
  fetchOk: boolean;
}): Promise<{ emittedSaved: boolean; emittedWith: string | null }> {
  const { currentName, newName, fetchedStatus, fetchOk } = opts;
  const { isValid } = validateName(newName, currentName);
  if (!isValid) return { emittedSaved: false, emittedWith: null };

  const body = buildPutBody(newName, fetchedStatus);
  if (!body) return { emittedSaved: false, emittedWith: null };

  if (!fetchOk) return { emittedSaved: false, emittedWith: null };

  return { emittedSaved: true, emittedWith: body.name };
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe("EditContainerNameDialog — validateName", () => {
  it("is invalid and shows error when name is empty", () => {
    const { nameError, isValid } = validateName("", "old name");
    expect(nameError).toBe("Name is required");
    expect(isValid).toBe(false);
  });

  it("is invalid and shows error when name is only whitespace", () => {
    const { nameError, isValid } = validateName("   ", "old name");
    expect(nameError).toBe("Name is required");
    expect(isValid).toBe(false);
  });

  it("is invalid when name equals the current name (unchanged)", () => {
    const { nameError, isValid } = validateName("my container", "my container");
    expect(nameError).toBe("");
    expect(isValid).toBe(false);
  });

  it("is invalid when trimmed name equals trimmed current name", () => {
    const { nameError, isValid } = validateName("  my container  ", "my container");
    expect(nameError).toBe("");
    expect(isValid).toBe(false);
  });

  it("is valid when name is non-empty and differs from current", () => {
    const { nameError, isValid } = validateName("new name", "old name");
    expect(nameError).toBe("");
    expect(isValid).toBe(true);
  });
});

describe("EditContainerNameDialog — buildPutBody", () => {
  it("returns null for empty name", () => {
    expect(buildPutBody("", null)).toBeNull();
  });

  it("returns null for whitespace-only name", () => {
    expect(buildPutBody("  ", null)).toBeNull();
  });

  it("returns trimmed name without status when fetchedStatus is null", () => {
    expect(buildPutBody("  LUMEN Campaign 2024  ", null)).toEqual({
      name: "LUMEN Campaign 2024",
    });
  });

  it("returns trimmed name with status when fetchedStatus is set", () => {
    expect(buildPutBody("  LUMEN Campaign 2024  ", "PUBLISHED")).toEqual({
      name: "LUMEN Campaign 2024",
      status: "PUBLISHED",
    });
  });

  it("omits status when fetchedStatus is empty string", () => {
    const result = buildPutBody("Campaign B", "");
    expect(result).toEqual({ name: "Campaign B" });
    expect(result).not.toHaveProperty("status");
  });
});

describe("EditContainerNameDialog — save emits saved on success", () => {
  it("emits saved with trimmed new name when fetch succeeds", async () => {
    const result = await simulateSave({
      currentName: "old name",
      newName: "  new name  ",
      fetchedStatus: null,
      fetchOk: true,
    });
    expect(result.emittedSaved).toBe(true);
    expect(result.emittedWith).toBe("new name");
  });

  it("does NOT emit saved when PUT fails", async () => {
    const result = await simulateSave({
      currentName: "old name",
      newName: "new name",
      fetchedStatus: null,
      fetchOk: false,
    });
    expect(result.emittedSaved).toBe(false);
    expect(result.emittedWith).toBeNull();
  });

  it("does NOT emit saved when name is blank", async () => {
    const result = await simulateSave({
      currentName: "old name",
      newName: "",
      fetchedStatus: null,
      fetchOk: true,
    });
    expect(result.emittedSaved).toBe(false);
    expect(result.emittedWith).toBeNull();
  });

  it("does NOT emit saved when name is unchanged", async () => {
    const result = await simulateSave({
      currentName: "same name",
      newName: "same name",
      fetchedStatus: "READY",
      fetchOk: true,
    });
    expect(result.emittedSaved).toBe(false);
    expect(result.emittedWith).toBeNull();
  });
});
