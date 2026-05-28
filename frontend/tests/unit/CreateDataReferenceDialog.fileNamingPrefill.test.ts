/**
 * REF-EDIT-TPL-3 — unit tests for FileReference template-driven create prefill.
 *
 * The Create dialog reads a `urn:shepard:reference:fileNaming` annotation off
 * the parent DataObject and prefills the `name` field with the pattern,
 * substituting known placeholders (`{date}`). User-typed values are never
 * overwritten.
 *
 * Pattern: pure-helper tests (matches `EditFileReferenceDialog.test.ts`).
 * Mounting the full Nuxt + Vuetify component tree is out of scope for an XS
 * task — the existing harness does not wire `@vue/test-utils`. The tests
 * exercise the extracted helpers directly and simulate the open-handler flow.
 */
import { describe, it, expect } from "vitest";
import type { SemanticAnnotation } from "@dlr-shepard/backend-client";
import {
  REFERENCE_PREDICATE,
  extractFileNamingPattern,
  findAnnotationByPredicate,
  resolveFileNamingPlaceholders,
} from "~/composables/references/referenceTemplatePrefill";

// ── Helpers ──────────────────────────────────────────────────────────────────

function mkAnn(
  propertyIRI: string,
  valueName: string | null,
): SemanticAnnotation {
  return {
    propertyName: "",
    propertyIRI,
    valueName: valueName ?? undefined,
    valueIRI: undefined,
  } as unknown as SemanticAnnotation;
}

/**
 * Simulates the dialog's open-handler prefill flow.
 * Returns the name the input would carry after the handler ran.
 */
function simulatePrefillOnOpen(opts: {
  currentName: string;
  annotations: SemanticAnnotation[] | null;
  now?: Date;
}): string {
  const { currentName, annotations, now } = opts;
  // Guard: never overwrite user-typed value.
  if (currentName.trim().length > 0) return currentName;
  const annotation = findAnnotationByPredicate(
    annotations,
    REFERENCE_PREDICATE.FILE_NAMING,
  );
  const pattern = extractFileNamingPattern(annotation);
  if (!pattern) return currentName;
  return resolveFileNamingPlaceholders(pattern, now ?? new Date());
}

// ── Tests ────────────────────────────────────────────────────────────────────

describe("findAnnotationByPredicate", () => {
  it("returns null for empty input", () => {
    expect(findAnnotationByPredicate([], REFERENCE_PREDICATE.FILE_NAMING))
      .toBeNull();
    expect(findAnnotationByPredicate(null, REFERENCE_PREDICATE.FILE_NAMING))
      .toBeNull();
    expect(findAnnotationByPredicate(undefined, REFERENCE_PREDICATE.FILE_NAMING))
      .toBeNull();
  });

  it("returns null when no annotation matches the predicate", () => {
    const annotations = [mkAnn("urn:shepard:other:thing", "foo")];
    expect(
      findAnnotationByPredicate(annotations, REFERENCE_PREDICATE.FILE_NAMING),
    ).toBeNull();
  });

  it("returns the first match when predicate is present", () => {
    const annotations = [
      mkAnn("urn:shepard:other:thing", "foo"),
      mkAnn(REFERENCE_PREDICATE.FILE_NAMING, "first"),
      mkAnn(REFERENCE_PREDICATE.FILE_NAMING, "second"),
    ];
    const found = findAnnotationByPredicate(
      annotations,
      REFERENCE_PREDICATE.FILE_NAMING,
    );
    expect(found?.valueName).toBe("first");
  });
});

describe("extractFileNamingPattern", () => {
  it("returns null for null annotation", () => {
    expect(extractFileNamingPattern(null)).toBeNull();
  });

  it("returns null when valueName is empty", () => {
    expect(extractFileNamingPattern(mkAnn(REFERENCE_PREDICATE.FILE_NAMING, "")))
      .toBeNull();
    expect(
      extractFileNamingPattern(
        mkAnn(REFERENCE_PREDICATE.FILE_NAMING, "   "),
      ),
    ).toBeNull();
  });

  it("returns the pattern verbatim when populated", () => {
    expect(
      extractFileNamingPattern(
        mkAnn(REFERENCE_PREDICATE.FILE_NAMING, "{date}_{instrument}.csv"),
      ),
    ).toBe("{date}_{instrument}.csv");
  });
});

describe("resolveFileNamingPlaceholders", () => {
  const fixedNow = new Date("2026-05-28T12:34:56Z");

  it("substitutes {date} with ISO YYYY-MM-DD", () => {
    expect(resolveFileNamingPlaceholders("{date}_run.csv", fixedNow)).toBe(
      "2026-05-28_run.csv",
    );
  });

  it("substitutes all occurrences of {date}", () => {
    expect(
      resolveFileNamingPlaceholders("{date}/{date}_log.txt", fixedNow),
    ).toBe("2026-05-28/2026-05-28_log.txt");
  });

  it("leaves unknown placeholders verbatim", () => {
    expect(
      resolveFileNamingPlaceholders(
        "{date}_{instrument}_{phase}.csv",
        fixedNow,
      ),
    ).toBe("2026-05-28_{instrument}_{phase}.csv");
  });

  it("returns the input unchanged when no placeholders are present", () => {
    expect(resolveFileNamingPlaceholders("plain.csv", fixedNow)).toBe(
      "plain.csv",
    );
  });
});

describe("simulatePrefillOnOpen — full dialog flow", () => {
  const fixedNow = new Date("2026-05-28T00:00:00Z");

  it("prefills the name when a fileNaming annotation exists and name is empty", () => {
    const annotations = [
      mkAnn(REFERENCE_PREDICATE.FILE_NAMING, "{date}_TR-004.csv"),
    ];
    const result = simulatePrefillOnOpen({
      currentName: "",
      annotations,
      now: fixedNow,
    });
    expect(result).toBe("2026-05-28_TR-004.csv");
  });

  it("does NOT prefill when user has already typed a name", () => {
    const annotations = [
      mkAnn(REFERENCE_PREDICATE.FILE_NAMING, "{date}_TR-004.csv"),
    ];
    const result = simulatePrefillOnOpen({
      currentName: "my custom name",
      annotations,
      now: fixedNow,
    });
    expect(result).toBe("my custom name");
  });

  it("does NOT prefill when no fileNaming annotation is present", () => {
    const annotations = [mkAnn("urn:shepard:other:thing", "foo")];
    const result = simulatePrefillOnOpen({
      currentName: "",
      annotations,
      now: fixedNow,
    });
    expect(result).toBe("");
  });

  it("does NOT prefill when the parent has no annotations at all", () => {
    const result = simulatePrefillOnOpen({
      currentName: "",
      annotations: [],
      now: fixedNow,
    });
    expect(result).toBe("");
  });

  it("treats whitespace-only currentName as empty for prefill purposes", () => {
    const annotations = [
      mkAnn(REFERENCE_PREDICATE.FILE_NAMING, "{date}_run.csv"),
    ];
    const result = simulatePrefillOnOpen({
      currentName: "   ",
      annotations,
      now: fixedNow,
    });
    expect(result).toBe("2026-05-28_run.csv");
  });

  it("returns the prefilled value when the annotation pattern has no placeholders", () => {
    const annotations = [
      mkAnn(REFERENCE_PREDICATE.FILE_NAMING, "calibration.csv"),
    ];
    const result = simulatePrefillOnOpen({
      currentName: "",
      annotations,
      now: fixedNow,
    });
    expect(result).toBe("calibration.csv");
  });
});
