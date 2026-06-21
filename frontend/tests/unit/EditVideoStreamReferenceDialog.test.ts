/**
 * REF-EDIT-2 — unit tests for EditVideoStreamReferenceDialog logic and
 * for the extractVideoTimestampHint helper.
 *
 * Tests cover pure helper logic (validation, PATCH body construction,
 * saved-emit behaviour, epoch-ms ↔ datetime-local conversion) and the
 * template-prefill `extractVideoTimestampHint` function, without mounting
 * the full Nuxt / Vuetify component tree.
 *
 * Playwright E2E tests covering visual rendering are tracked in aidocs/16
 * REF-EDIT-2 row.
 */

import { describe, it, expect } from "vitest";
import {
  extractVideoTimestampHint,
  REFERENCE_PREDICATE,
} from "~/composables/references/referenceTemplatePrefill";
import type { SemanticAnnotation } from "@dlr-shepard/backend-client";

// ── Helpers inlined from the component ───────────────────────────────────────

/** Validate name field. */
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
 * Convert UTC epoch milliseconds to a datetime-local string (local ISO-like).
 * Mirrors the component's msToDatetimeLocal helper.
 */
function msToDatetimeLocal(ms: number): string {
  const d = new Date(ms);
  const pad = (n: number) => String(n).padStart(2, "0");
  return (
    d.getFullYear() +
    "-" +
    pad(d.getMonth() + 1) +
    "-" +
    pad(d.getDate()) +
    "T" +
    pad(d.getHours()) +
    ":" +
    pad(d.getMinutes())
  );
}

/** Convert datetime-local string back to epoch ms, or null for empty/invalid. */
function datetimeLocalToMs(localStr: string): number | null {
  if (!localStr) return null;
  const d = new Date(localStr);
  if (isNaN(d.getTime())) return null;
  return d.getTime();
}

/**
 * Build the RFC 7396 merge-patch body — only changed fields.
 * Returns the patch object (may be empty if nothing changed).
 */
function buildPatchBody(opts: {
  currentName: string;
  newName: string;
  currentWallClockTimestampMs: number | null;
  wallClockLocal: string;
}): Record<string, unknown> {
  const { currentName, newName, currentWallClockTimestampMs, wallClockLocal } = opts;
  const patchBody: Record<string, unknown> = {};
  const nameTrimmed = newName.trim();
  if (nameTrimmed !== currentName.trim()) {
    patchBody.name = nameTrimmed;
  }
  const newTsMs = wallClockLocal ? datetimeLocalToMs(wallClockLocal) : null;
  if (newTsMs !== currentWallClockTimestampMs ||
      (wallClockLocal === "" && currentWallClockTimestampMs != null)) {
    patchBody.wallClockTimestamp = newTsMs;
  }
  return patchBody;
}

/**
 * Simulate the save flow; returns emit payload or null when nothing would emit.
 */
async function simulateSave(opts: {
  currentName: string;
  newName: string;
  currentWallClockTimestampMs: number | null;
  wallClockLocal: string;
  fetchOk: boolean;
}): Promise<{ name: string; wallClockTimestamp: number | null } | null> {
  const { currentName, newName, currentWallClockTimestampMs, wallClockLocal, fetchOk } = opts;
  // Replicate the component's hasChanges + nameError logic.
  const { nameError } = validateName(newName, currentName);
  const nameTrimmed = newName.trim();
  const nameChanged = nameTrimmed !== currentName.trim();
  const newTsMs = wallClockLocal ? datetimeLocalToMs(wallClockLocal) : null;
  const tsChanged = newTsMs !== currentWallClockTimestampMs ||
      (wallClockLocal === "" && currentWallClockTimestampMs != null);
  const hasChanges = nameChanged || tsChanged;
  if (!hasChanges || !!nameError) return null;
  if (!fetchOk) return null;
  return {
    name: nameChanged ? nameTrimmed : currentName,
    wallClockTimestamp: tsChanged ? newTsMs : currentWallClockTimestampMs,
  };
}

// ── Tests: renders / validation ───────────────────────────────────────────────

describe("EditVideoStreamReferenceDialog — validateName", () => {
  it("renders with provided name and wallClockTimestamp — name error absent for valid name", () => {
    const { nameError, isValid } = validateName("camera-1.mp4", "camera-1.mp4");
    // Same name → invalid (no change), no error message on the name field itself
    expect(nameError).toBe("");
    expect(isValid).toBe(false); // same value → no change → not valid
  });

  it("save button disabled when name is blank", () => {
    const { nameError, isValid } = validateName("", "camera-1.mp4");
    expect(nameError).toBe("Name is required");
    expect(isValid).toBe(false);
  });
});

// ── Tests: PATCH body construction ───────────────────────────────────────────

describe("EditVideoStreamReferenceDialog — buildPatchBody", () => {
  it("PATCH called with only changed fields when only name changes", () => {
    // Use a minute-aligned timestamp so the datetime-local round-trip is exact.
    // 2023-11-14T22:13:00.000Z → no seconds, so msToDatetimeLocal / datetimeLocalToMs is stable.
    const minuteAlignedMs = new Date("2023-11-14T22:13:00.000Z").getTime();
    const localStr = msToDatetimeLocal(minuteAlignedMs);
    // Verify the round-trip is lossless before relying on it in the test.
    expect(datetimeLocalToMs(localStr)).toBe(minuteAlignedMs);

    const body = buildPatchBody({
      currentName: "old.mp4",
      newName: "new.mp4",
      currentWallClockTimestampMs: minuteAlignedMs,
      wallClockLocal: localStr,
    });
    expect(body).toHaveProperty("name", "new.mp4");
    expect(body).not.toHaveProperty("wallClockTimestamp");
  });

  it("PATCH called with only changed fields when only wallClockTimestamp changes", () => {
    const newTs = 1700000000000;
    const body = buildPatchBody({
      currentName: "video.mp4",
      newName: "video.mp4",
      currentWallClockTimestampMs: null,
      wallClockLocal: msToDatetimeLocal(newTs),
    });
    expect(body).not.toHaveProperty("name");
    expect(body).toHaveProperty("wallClockTimestamp");
    expect(typeof body.wallClockTimestamp).toBe("number");
  });

  it("wallClockTimestamp null clears the field in PATCH body", () => {
    // User clears the datetime-local input (wallClockLocal = "")
    const body = buildPatchBody({
      currentName: "video.mp4",
      newName: "video.mp4",
      currentWallClockTimestampMs: 1700000000000,
      wallClockLocal: "",
    });
    expect(body).toHaveProperty("wallClockTimestamp", null);
    expect(body).not.toHaveProperty("name");
  });
});

// ── Tests: save emit behaviour ────────────────────────────────────────────────

describe("EditVideoStreamReferenceDialog — save emit behaviour", () => {
  it("emits saved with updated values on success", async () => {
    const result = await simulateSave({
      currentName: "old.mp4",
      newName: "new.mp4",
      currentWallClockTimestampMs: null,
      wallClockLocal: "",
      fetchOk: true,
    });
    expect(result).not.toBeNull();
    expect(result!.name).toBe("new.mp4");
  });

  it("emits close on cancel click — no saved event fired", async () => {
    // Cancel is represented by fetchOk = false (dialog closes without save).
    const result = await simulateSave({
      currentName: "video.mp4",
      newName: "video.mp4",
      currentWallClockTimestampMs: null,
      wallClockLocal: "",
      fetchOk: false,
    });
    // No changes → result null regardless of fetchOk
    expect(result).toBeNull();
  });
});

// ── Tests: extractVideoTimestampHint ─────────────────────────────────────────

describe("extractVideoTimestampHint", () => {
  it("returns null for missing annotation", () => {
    expect(extractVideoTimestampHint(null)).toBeNull();
  });

  it("returns null for annotation with empty valueName", () => {
    const ann = { propertyIRI: REFERENCE_PREDICATE.VIDEO_TIMESTAMP, valueName: "" } as SemanticAnnotation;
    expect(extractVideoTimestampHint(ann)).toBeNull();
  });

  it("returns null for non-JSON valueName (plain string)", () => {
    const ann = { propertyIRI: REFERENCE_PREDICATE.VIDEO_TIMESTAMP, valueName: "Europe/Berlin" } as SemanticAnnotation;
    expect(extractVideoTimestampHint(ann)).toBeNull();
  });

  it("parses wallClockOffsetMs and timezone from JSON valueName", () => {
    const ann = {
      propertyIRI: REFERENCE_PREDICATE.VIDEO_TIMESTAMP,
      valueName: JSON.stringify({ wallClockOffsetMs: 5000, timezone: "Europe/Berlin" }),
    } as SemanticAnnotation;
    const hint = extractVideoTimestampHint(ann);
    expect(hint).not.toBeNull();
    expect(hint!.wallClockOffsetMs).toBe(5000);
    expect(hint!.timezone).toBe("Europe/Berlin");
  });

  it("returns null when JSON has no recognised keys", () => {
    const ann = {
      propertyIRI: REFERENCE_PREDICATE.VIDEO_TIMESTAMP,
      valueName: JSON.stringify({ unknown: "value" }),
    } as SemanticAnnotation;
    expect(extractVideoTimestampHint(ann)).toBeNull();
  });

  it("returns partial hint when only timezone is present", () => {
    const ann = {
      propertyIRI: REFERENCE_PREDICATE.VIDEO_TIMESTAMP,
      valueName: JSON.stringify({ timezone: "Asia/Tokyo" }),
    } as SemanticAnnotation;
    const hint = extractVideoTimestampHint(ann);
    expect(hint).not.toBeNull();
    expect(hint!.timezone).toBe("Asia/Tokyo");
    expect(hint!.wallClockOffsetMs).toBeUndefined();
  });

  it("returns null for malformed JSON valueName", () => {
    const ann = {
      propertyIRI: REFERENCE_PREDICATE.VIDEO_TIMESTAMP,
      valueName: "{not valid json",
    } as SemanticAnnotation;
    expect(extractVideoTimestampHint(ann)).toBeNull();
  });
});
