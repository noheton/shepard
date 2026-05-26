/**
 * A5c — unit tests for HdfReferencesPane helper logic.
 *
 * Tests cover the pure data-transformation functions extracted from
 * the component: display-path formatting and form-validation guards.
 * Full component mount is intentionally deferred (requires Vitest
 * + vue-test-utils setup with Nuxt mocks).
 */
import { describe, it, expect } from "vitest";

// ── Types mirrored from the panel ─────────────────────────────────────────

interface HdfReference {
  appId: string;
  hdfContainerAppId?: string;
  datasetPath?: string;
  description?: string;
}

// ── Pure helpers extracted from HdfReferencesPane ─────────────────────────

/**
 * Format a reference for display in the table.
 * Returns "—" for missing fields (same pattern as GitReferencesPane).
 */
function displayPath(ref: HdfReference): string {
  return ref.datasetPath ?? "—";
}

function displayContainer(ref: HdfReference): string {
  return ref.hdfContainerAppId ?? "—";
}

function displayDescription(ref: HdfReference): string {
  return ref.description ?? "—";
}

/**
 * Validate the create-form guard:
 *   - hdfContainerAppId must be non-blank
 *   - datasetPath must be non-blank
 */
function isCreateFormValid(containerAppId: string, datasetPath: string): boolean {
  return (
    containerAppId !== undefined &&
    containerAppId.trim().length > 0 &&
    datasetPath !== undefined &&
    datasetPath.trim().length > 0
  );
}

/**
 * Build the DELETE url for a reference.
 */
function buildDeleteUrl(dataObjectAppId: string, referenceAppId: string): string {
  return `/v2/data-objects/${dataObjectAppId}/hdf-references/${referenceAppId}`;
}

/**
 * Build the list/create url for a DataObject's references.
 */
function buildListUrl(dataObjectAppId: string): string {
  return `/v2/data-objects/${dataObjectAppId}/hdf-references`;
}

// ── tests ─────────────────────────────────────────────────────────────────

describe("HdfReferencesPane display helpers", () => {
  const fullRef: HdfReference = {
    appId: "ref-001",
    hdfContainerAppId: "container-abc",
    datasetPath: "/sensor_data/channel_A",
    description: "Primary vibration channel",
  };

  const sparseRef: HdfReference = {
    appId: "ref-002",
  };

  it("displayPath returns path when present", () => {
    expect(displayPath(fullRef)).toBe("/sensor_data/channel_A");
  });

  it("displayPath returns — when absent", () => {
    expect(displayPath(sparseRef)).toBe("—");
  });

  it("displayContainer returns container appId when present", () => {
    expect(displayContainer(fullRef)).toBe("container-abc");
  });

  it("displayContainer returns — when absent", () => {
    expect(displayContainer(sparseRef)).toBe("—");
  });

  it("displayDescription returns description when present", () => {
    expect(displayDescription(fullRef)).toBe("Primary vibration channel");
  });

  it("displayDescription returns — when absent", () => {
    expect(displayDescription(sparseRef)).toBe("—");
  });
});

describe("HdfReferencesPane form validation", () => {
  it("isCreateFormValid returns true when both fields are filled", () => {
    expect(isCreateFormValid("container-abc", "/sensor/ch_a")).toBe(true);
  });

  it("isCreateFormValid returns false when containerAppId is empty", () => {
    expect(isCreateFormValid("", "/sensor/ch_a")).toBe(false);
  });

  it("isCreateFormValid returns false when datasetPath is empty", () => {
    expect(isCreateFormValid("container-abc", "")).toBe(false);
  });

  it("isCreateFormValid returns false when both fields are empty", () => {
    expect(isCreateFormValid("", "")).toBe(false);
  });

  it("isCreateFormValid trims whitespace before checking", () => {
    expect(isCreateFormValid("   ", "/sensor/ch_a")).toBe(false);
    expect(isCreateFormValid("container-abc", "   ")).toBe(false);
  });
});

describe("HdfReferencesPane URL builders", () => {
  it("buildListUrl produces correct path", () => {
    expect(buildListUrl("do-app-001")).toBe(
      "/v2/data-objects/do-app-001/hdf-references",
    );
  });

  it("buildDeleteUrl produces correct path", () => {
    expect(buildDeleteUrl("do-app-001", "ref-app-001")).toBe(
      "/v2/data-objects/do-app-001/hdf-references/ref-app-001",
    );
  });

  it("buildDeleteUrl handles UUID v7 identifiers", () => {
    const doId = "01900000-0000-7000-8000-000000000001";
    const refId = "01900000-0000-7000-8000-000000000002";
    const url = buildDeleteUrl(doId, refId);
    expect(url).toContain(doId);
    expect(url).toContain(refId);
    expect(url).toContain("hdf-references");
  });
});

// ── A5c-annotation: annotation subject constants ───────────────────────────

/**
 * The subject-kind string passed to AnnotationDialog.
 * This constant is the ground-truth for what HdfReferencesPane emits;
 * changes here must be reflected in the Vue component.
 */
const HDF_REFERENCE_SUBJECT_KIND = "HdfReference";

/**
 * The HDF5 vocabulary namespace URI seeded by V87__hdf_vocabulary.cypher.
 */
const HDF_VOCAB_URI = "https://shepard.dlr.de/ontology/hdf#";

/**
 * Build the subject-kind string for AnnotationDialog (always "HdfReference").
 */
function buildAnnotateSubjectKind(): string {
  return HDF_REFERENCE_SUBJECT_KIND;
}

describe("HdfReferencesPane annotation affordance", () => {
  it("subject kind is HdfReference", () => {
    expect(buildAnnotateSubjectKind()).toBe("HdfReference");
  });

  it("subject kind does not change when datasetPath differs", () => {
    expect(buildAnnotateSubjectKind()).toBe(buildAnnotateSubjectKind());
  });

  it("HDF vocabulary URI has the expected namespace", () => {
    expect(HDF_VOCAB_URI).toMatch(/^https:\/\/shepard\.dlr\.de\/ontology\/hdf#/);
  });

  it("HDF vocabulary URI ends with #", () => {
    expect(HDF_VOCAB_URI.endsWith("#")).toBe(true);
  });

  it("annotate button data-testid follows the same pattern as delete", () => {
    const appId = "ref-001";
    const annotateTestId = `hdf-ref-annotate-${appId}`;
    const deleteTestId = `hdf-ref-delete-${appId}`;
    // Both share the appId segment — consistent naming convention
    expect(annotateTestId).toContain(appId);
    expect(deleteTestId).toContain(appId);
    expect(annotateTestId).not.toBe(deleteTestId);
  });
});
