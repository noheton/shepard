/**
 * REF-EDIT-TPL-4 — unit tests for FileBundleReference template-driven create
 * prefill.
 *
 * When a parent DataObject carries a `urn:shepard:reference:bundleLayout`
 * annotation, the upload dialog's bundle-mode name field is pre-seeded with
 * the hint's `name` value (after `{date}` placeholder substitution). The user
 * may override the pre-filled value freely.
 *
 * Tests exercise the pure helpers directly (no Nuxt/Vuetify mounting).
 */
import { describe, it, expect } from "vitest";
import type { SemanticAnnotation } from "@dlr-shepard/backend-client";
import {
  REFERENCE_PREDICATE,
  findAnnotationByPredicate,
  parseBundleLayoutHint,
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

/** Simulates the dialog open-handler for bundle-mode prefill. */
function simulateBundlePrefillOnOpen(opts: {
  currentName: string;
  annotations: SemanticAnnotation[] | null;
  now?: Date;
}): string {
  const { currentName, annotations, now } = opts;
  if (currentName.trim().length > 0) return currentName;
  const ann = findAnnotationByPredicate(annotations, REFERENCE_PREDICATE.BUNDLE_LAYOUT);
  const hint = parseBundleLayoutHint(ann);
  if (!hint?.name) return currentName;
  return resolveFileNamingPlaceholders(hint.name, now ?? new Date());
}

/** Simulates updateReferenceNameByFileName for multi-file case with template fallback. */
function simulateMultiFileFallback(opts: {
  currentName: string;
  templateBundleName: string;
}): string {
  const { currentName, templateBundleName } = opts;
  if (!currentName || currentName === templateBundleName) {
    return templateBundleName;
  }
  return currentName;
}

// ── parseBundleLayoutHint tests ───────────────────────────────────────────────

describe("parseBundleLayoutHint", () => {
  it("returns null for null annotation", () => {
    expect(parseBundleLayoutHint(null)).toBeNull();
  });

  it("returns null for empty valueName", () => {
    const ann = mkAnn(REFERENCE_PREDICATE.BUNDLE_LAYOUT, "");
    expect(parseBundleLayoutHint(ann)).toBeNull();
  });

  it("returns null for whitespace-only valueName", () => {
    const ann = mkAnn(REFERENCE_PREDICATE.BUNDLE_LAYOUT, "   ");
    expect(parseBundleLayoutHint(ann)).toBeNull();
  });

  it("returns null for plain string (not JSON)", () => {
    const ann = mkAnn(REFERENCE_PREDICATE.BUNDLE_LAYOUT, "measurements-{date}");
    expect(parseBundleLayoutHint(ann)).toBeNull();
  });

  it("returns null for malformed JSON", () => {
    const ann = mkAnn(REFERENCE_PREDICATE.BUNDLE_LAYOUT, "{bad json");
    expect(parseBundleLayoutHint(ann)).toBeNull();
  });

  it("returns null when JSON carries neither name nor acceptExtensions", () => {
    const ann = mkAnn(REFERENCE_PREDICATE.BUNDLE_LAYOUT, '{"description":"ignored"}');
    expect(parseBundleLayoutHint(ann)).toBeNull();
  });

  it("parses name-only hint", () => {
    const ann = mkAnn(
      REFERENCE_PREDICATE.BUNDLE_LAYOUT,
      '{"name":"measurements-{date}"}',
    );
    expect(parseBundleLayoutHint(ann)).toEqual({
      name: "measurements-{date}",
      acceptExtensions: undefined,
    });
  });

  it("parses acceptExtensions-only hint", () => {
    const ann = mkAnn(
      REFERENCE_PREDICATE.BUNDLE_LAYOUT,
      '{"acceptExtensions":[".tif",".png"]}',
    );
    expect(parseBundleLayoutHint(ann)).toEqual({
      name: undefined,
      acceptExtensions: [".tif", ".png"],
    });
  });

  it("parses full hint with name and acceptExtensions", () => {
    const ann = mkAnn(
      REFERENCE_PREDICATE.BUNDLE_LAYOUT,
      '{"name":"ndt-scan-{date}","acceptExtensions":[".tif",".png",".jpeg"]}',
    );
    expect(parseBundleLayoutHint(ann)).toEqual({
      name: "ndt-scan-{date}",
      acceptExtensions: [".tif", ".png", ".jpeg"],
    });
  });

  it("trims whitespace from name", () => {
    const ann = mkAnn(
      REFERENCE_PREDICATE.BUNDLE_LAYOUT,
      '{"name":"  sensor-bundle  "}',
    );
    expect(parseBundleLayoutHint(ann)?.name).toBe("sensor-bundle");
  });

  it("filters empty strings from acceptExtensions", () => {
    const ann = mkAnn(
      REFERENCE_PREDICATE.BUNDLE_LAYOUT,
      '{"acceptExtensions":[".tif","",".png"]}',
    );
    expect(parseBundleLayoutHint(ann)?.acceptExtensions).toEqual([".tif", ".png"]);
  });

  it("returns null when acceptExtensions is empty after filtering", () => {
    const ann = mkAnn(
      REFERENCE_PREDICATE.BUNDLE_LAYOUT,
      '{"acceptExtensions":[""]}',
    );
    expect(parseBundleLayoutHint(ann)).toBeNull();
  });
});

// ── Bundle prefill open-handler simulation tests ──────────────────────────────

describe("simulateBundlePrefillOnOpen (dialog open-handler simulation)", () => {
  const fixedDate = new Date("2026-06-26T00:00:00Z");

  it("does not overwrite a name the user already typed", () => {
    const anns = [
      mkAnn(REFERENCE_PREDICATE.BUNDLE_LAYOUT, '{"name":"template-name-{date}"}'),
    ];
    const result = simulateBundlePrefillOnOpen({
      currentName: "user-typed-name",
      annotations: anns,
      now: fixedDate,
    });
    expect(result).toBe("user-typed-name");
  });

  it("prefills name from annotation when field is empty", () => {
    const anns = [
      mkAnn(REFERENCE_PREDICATE.BUNDLE_LAYOUT, '{"name":"ndt-scan-{date}"}'),
    ];
    const result = simulateBundlePrefillOnOpen({
      currentName: "",
      annotations: anns,
      now: fixedDate,
    });
    expect(result).toBe("ndt-scan-2026-06-26");
  });

  it("leaves name empty when annotation is absent", () => {
    const result = simulateBundlePrefillOnOpen({
      currentName: "",
      annotations: [],
      now: fixedDate,
    });
    expect(result).toBe("");
  });

  it("leaves name empty when annotation has acceptExtensions only (no name)", () => {
    const anns = [
      mkAnn(
        REFERENCE_PREDICATE.BUNDLE_LAYOUT,
        '{"acceptExtensions":[".tif"]}',
      ),
    ];
    const result = simulateBundlePrefillOnOpen({
      currentName: "",
      annotations: anns,
      now: fixedDate,
    });
    expect(result).toBe("");
  });

  it("resolves {date} placeholder in name pattern", () => {
    const anns = [
      mkAnn(REFERENCE_PREDICATE.BUNDLE_LAYOUT, '{"name":"batch-{date}-images"}'),
    ];
    const result = simulateBundlePrefillOnOpen({
      currentName: "",
      annotations: anns,
      now: new Date("2026-01-15T12:00:00Z"),
    });
    expect(result).toBe("batch-2026-01-15-images");
  });
});

// ── Multi-file fallback simulation tests ──────────────────────────────────────

describe("simulateMultiFileFallback (updateReferenceNameByFileName multi-file case)", () => {
  it("restores template name when current name is empty", () => {
    expect(
      simulateMultiFileFallback({ currentName: "", templateBundleName: "template-name" }),
    ).toBe("template-name");
  });

  it("restores template name when current name equals the template (no override)", () => {
    expect(
      simulateMultiFileFallback({
        currentName: "template-name",
        templateBundleName: "template-name",
      }),
    ).toBe("template-name");
  });

  it("preserves user-typed name when it differs from template", () => {
    expect(
      simulateMultiFileFallback({
        currentName: "my-custom-bundle",
        templateBundleName: "template-name",
      }),
    ).toBe("my-custom-bundle");
  });

  it("restores empty string when no template name is set", () => {
    expect(
      simulateMultiFileFallback({ currentName: "", templateBundleName: "" }),
    ).toBe("");
  });
});
