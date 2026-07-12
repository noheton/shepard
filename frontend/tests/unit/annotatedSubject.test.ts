/**
 * V2UI-CONFORMANCE — unit tests for the v2 polymorphic annotation accessors
 * ({@link AnnotatedCollection} / {@link AnnotatedDataObject} /
 * {@link AnnotatedReference} and the container variants).
 *
 * These replace the old v1 `SemanticAnnotationApi` (numeric collectionId /
 * dataObjectId — crashed once the numeric id was dropped from v2 entities).
 * The new classes route through the typed `SemanticAnnotationsApi` via
 * `useV2ShepardApi`, keyed by `subjectAppId` (UUID v7 string) + `subjectKind`.
 *
 * Verifies:
 *  - listAnnotations is called with the right subjectAppId + subjectKind
 *  - the v6 AnnotationV2 wire shape is mapped onto the legacy SemanticAnnotation
 *  - delete resolves the real annotation appId via the fakeId map
 *  - create posts subjectAppId/subjectKind/predicateIri and maps the result
 *  - an empty subjectAppId fails soft (returns [], no network call)
 *  - AnnotatedReference carries the supplied concrete kind
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { ref } from "vue";
import { useV2ShepardApi } from "~/composables/common/api/useV2ShepardApi";

vi.mock("~/composables/common/api/useV2ShepardApi", () => ({
  useV2ShepardApi: vi.fn(),
}));

const mockListAnnotations = vi.fn();
const mockCreateAnnotation = vi.fn();
const mockDeleteAnnotation = vi.fn();

beforeEach(() => {
  vi.clearAllMocks();
  (useV2ShepardApi as ReturnType<typeof vi.fn>).mockReturnValue(
    ref({
      listAnnotations: mockListAnnotations,
      createAnnotation: mockCreateAnnotation,
      deleteAnnotation: mockDeleteAnnotation,
    }),
  );
});

// APISIMP-ANNOT-LEGACY-FIELDS-DROP: wire shape now uses v6 canonical fields only.
const WIRE_ANNOTATION = {
  appId: "0192aaaa-0000-7000-8000-000000000001",
  predicateLabel: "material",
  predicateIri: "http://example.org/material",
  objectLiteral: "CF/LMPAEK",
  objectIri: "http://example.org/CFLMPAEK",
};

const COLLECTION_APP_ID = "0192bbbb-0000-7000-8000-000000000042";

describe("v2 SubjectAnnotated accessors", () => {
  it("AnnotatedCollection.fetchAnnotations queries by subjectAppId + Collection kind", async () => {
    mockListAnnotations.mockResolvedValue({ items: [WIRE_ANNOTATION], total: 1, page: 0, pageSize: 200 });
    const { AnnotatedCollection } = await import("~/composables/annotated");

    const out = await new AnnotatedCollection(COLLECTION_APP_ID).fetchAnnotations();

    expect(mockListAnnotations).toHaveBeenCalledWith({
      subjectAppId: COLLECTION_APP_ID,
      subjectKind: "Collection",
      pageSize: 200,
    });
    expect(out).toHaveLength(1);
    // mapped onto legacy SemanticAnnotation shape via v6 canonical fields
    expect(out[0]).toMatchObject({
      id: 0,
      propertyName: "material",
      propertyIRI: "http://example.org/material",
      valueName: "CF/LMPAEK",
      valueIRI: "http://example.org/CFLMPAEK",
    });
  });

  it("AnnotatedDataObject uses the DataObject subjectKind", async () => {
    mockListAnnotations.mockResolvedValue({ items: [], total: 0, page: 0, pageSize: 200 });
    const { AnnotatedDataObject } = await import("~/composables/annotated");

    await new AnnotatedDataObject("0192cccc-0000-7000-8000-0000000000aa").fetchAnnotations();

    expect(mockListAnnotations).toHaveBeenCalledWith(
      expect.objectContaining({ subjectKind: "DataObject" }),
    );
  });

  it("AnnotatedReference carries the supplied concrete reference kind", async () => {
    mockListAnnotations.mockResolvedValue({ items: [], total: 0, page: 0, pageSize: 200 });
    const { AnnotatedReference } = await import("~/composables/annotated");

    await new AnnotatedReference(
      "0192dddd-0000-7000-8000-0000000000bb",
      "FileReference",
    ).fetchAnnotations();

    expect(mockListAnnotations).toHaveBeenCalledWith(
      expect.objectContaining({ subjectKind: "FileReference" }),
    );
  });

  it("AnnotatedReference defaults to the generic 'Reference' kind", async () => {
    mockListAnnotations.mockResolvedValue({ items: [], total: 0, page: 0, pageSize: 200 });
    const { AnnotatedReference } = await import("~/composables/annotated");

    await new AnnotatedReference("0192eeee-0000-7000-8000-0000000000cc").fetchAnnotations();

    expect(mockListAnnotations).toHaveBeenCalledWith(
      expect.objectContaining({ subjectKind: "Reference" }),
    );
  });

  it("delete resolves the real annotation appId via the fakeId map", async () => {
    mockListAnnotations.mockResolvedValue({ items: [WIRE_ANNOTATION], total: 1, page: 0, pageSize: 200 });
    mockDeleteAnnotation.mockResolvedValue(undefined);
    const { AnnotatedCollection } = await import("~/composables/annotated");

    const accessor = new AnnotatedCollection(COLLECTION_APP_ID);
    const [annotation] = await accessor.fetchAnnotations();
    await accessor.deleteAnnotation(annotation!.id);

    expect(mockDeleteAnnotation).toHaveBeenCalledWith({
      appId: WIRE_ANNOTATION.appId,
    });
  });

  it("delete throws when the fakeId is unknown", async () => {
    const { AnnotatedCollection } = await import("~/composables/annotated");
    const accessor = new AnnotatedCollection(COLLECTION_APP_ID);
    await expect(accessor.deleteAnnotation(123)).rejects.toThrow(/Unknown annotation/);
    expect(mockDeleteAnnotation).not.toHaveBeenCalled();
  });

  it("addAnnotation posts subjectAppId/subjectKind/predicateIri and maps the result", async () => {
    mockCreateAnnotation.mockResolvedValue(WIRE_ANNOTATION);
    const { AnnotatedCollection } = await import("~/composables/annotated");

    const created = await new AnnotatedCollection(COLLECTION_APP_ID).addAnnotation({
      propertyIRI: "http://example.org/material",
      valueIRI: "http://example.org/CFLMPAEK",
    });

    expect(mockCreateAnnotation).toHaveBeenCalledWith({
      createAnnotationV2: {
        subjectAppId: COLLECTION_APP_ID,
        subjectKind: "Collection",
        predicateIri: "http://example.org/material",
        objectIri: "http://example.org/CFLMPAEK",
      },
    });
    expect(created).toMatchObject({ propertyName: "material", valueName: "CF/LMPAEK" });
  });

  it("addAnnotation sends an empty objectLiteral when no value IRI is given", async () => {
    mockCreateAnnotation.mockResolvedValue(WIRE_ANNOTATION);
    const { AnnotatedDataObject } = await import("~/composables/annotated");

    await new AnnotatedDataObject("0192ffff-0000-7000-8000-0000000000dd").addAnnotation({
      propertyIRI: "http://example.org/p",
      valueIRI: "",
    });

    expect(mockCreateAnnotation).toHaveBeenCalledWith({
      createAnnotationV2: expect.objectContaining({ objectLiteral: "" }),
    });
  });

  it("fetchAnnotations reads rows from the PagedResponseIO envelope", async () => {
    // BUG-ANNOTATIONS-MAP-ENVELOPE: listAnnotations migrated to the
    // PagedResponseIO {items,total,page,pageSize} envelope (#2104) and the
    // generated client was regenerated to match. fetchAnnotations reads the
    // rows from `.items`; a missing/empty envelope degrades to [] (no crash).
    mockListAnnotations.mockResolvedValue({
      items: [WIRE_ANNOTATION],
      total: 1,
      page: 0,
      pageSize: 200,
    });
    const { AnnotatedDataObject } = await import("~/composables/annotated");

    const out = await new AnnotatedDataObject(
      "0192cccc-0000-7000-8000-0000000000ab",
    ).fetchAnnotations();

    expect(out).toHaveLength(1);
    expect(out[0]).toMatchObject({ propertyName: "material", valueName: "CF/LMPAEK" });
  });

  it("fetchAnnotations fails soft (returns [], no network call) for an empty appId", async () => {
    const { AnnotatedCollection } = await import("~/composables/annotated");
    const out = await new AnnotatedCollection("").fetchAnnotations();
    expect(out).toEqual([]);
    expect(mockListAnnotations).not.toHaveBeenCalled();
  });

  it("the container variants carry their own subjectKind", async () => {
    mockListAnnotations.mockResolvedValue({ items: [], total: 0, page: 0, pageSize: 200 });
    const {
      AnnotatedTimeseriesContainer,
      AnnotatedFileContainer,
      AnnotatedStructuredDataContainer,
    } = await import("~/composables/annotated");

    await new AnnotatedTimeseriesContainer("c1").fetchAnnotations();
    await new AnnotatedFileContainer("c2").fetchAnnotations();
    await new AnnotatedStructuredDataContainer("c3").fetchAnnotations();

    const kinds = mockListAnnotations.mock.calls.map(c => c[0].subjectKind);
    expect(kinds).toEqual([
      "TimeseriesContainer",
      "FileContainer",
      "StructuredDataContainer",
    ]);
  });
});
