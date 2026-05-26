/**
 * SEMA-V6-005 — unit tests for useAnnotations composable.
 *
 * Tests verify:
 * - Initial loading state
 * - Successful fetch populates annotations
 * - Error handling
 * - createAnnotation calls addAnnotation + refetches
 * - deleteAnnotation calls deleteAnnotation + refetches
 * - refetch triggers a second fetch
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import type { SemanticAnnotation } from "@dlr-shepard/backend-client";
import type { Annotated, AnnotationToAdd } from "~/composables/annotated";

// Bring the composable into scope AFTER mocks are registered.
// (Import must come after vi.mock hoisting.)
import { useAnnotations } from "~/composables/semantic/useAnnotations";

// ── Helpers ───────────────────────────────────────────────────────────────────

const flush = () => new Promise<void>(r => setTimeout(r, 0));

function makeAnnotation(id: number): SemanticAnnotation {
  return {
    id,
    name: `annotation-${id}`,
    propertyName: "material",
    propertyIRI: "http://example.org/material",
    valueName: "CF/LMPAEK",
    valueIRI: "http://example.org/CFLMPAEK",
    propertyRepositoryId: 1,
    valueRepositoryId: 1,
  };
}

function makeAnnotated(overrides?: Partial<Annotated>): Annotated {
  return {
    fetchAnnotations: vi.fn().mockResolvedValue([makeAnnotation(1)]),
    addAnnotation: vi.fn().mockResolvedValue(makeAnnotation(2)),
    deleteAnnotation: vi.fn().mockResolvedValue(undefined),
    ...overrides,
  };
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe("useAnnotations", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // Reset the global handleError stub between tests
    const g = globalThis as Record<string, unknown>;
    if (typeof g.handleError === "function") {
      (g.handleError as ReturnType<typeof vi.fn>).mockReset?.();
    }
  });

  it("starts with loading=true and empty annotations", () => {
    const annotated = makeAnnotated({
      fetchAnnotations: vi.fn().mockResolvedValue([]),
    });
    const { loading, annotations } = useAnnotations(annotated);
    expect(loading.value).toBe(true);
    expect(annotations.value).toEqual([]);
  });

  it("populates annotations and clears loading on success", async () => {
    const annotated = makeAnnotated();
    const { loading, annotations, error } = useAnnotations(annotated);
    await flush();

    expect(annotations.value).toHaveLength(1);
    expect(annotations.value[0]!.id).toBe(1);
    expect(loading.value).toBe(false);
    expect(error.value).toBeNull();
  });

  it("sets error and calls handleError on fetch failure", async () => {
    const err = new Error("Network error");
    const annotated = makeAnnotated({
      fetchAnnotations: vi.fn().mockRejectedValue(err),
    });

    const { loading, error } = useAnnotations(annotated);
    await flush();

    expect(error.value).toBe("Could not load annotations.");
    expect(loading.value).toBe(false);
    expect((globalThis as unknown as { handleError: ReturnType<typeof vi.fn> }).handleError)
      .toHaveBeenCalledWith(err, "fetching annotations");
  });

  it("createAnnotation calls addAnnotation and refetches", async () => {
    const fetchMock = vi.fn().mockResolvedValue([makeAnnotation(1)]);
    const addMock = vi.fn().mockResolvedValue(makeAnnotation(2));
    const annotated = makeAnnotated({ fetchAnnotations: fetchMock, addAnnotation: addMock });

    const { createAnnotation } = useAnnotations(annotated);
    await flush();

    const payload: AnnotationToAdd = {
      propertyIRI: "http://example.org/p",
      valueIRI: "http://example.org/v",
      propertyRepositoryId: 0,
      valueRepositoryId: 0,
    };
    const result = await createAnnotation(payload);

    expect(addMock).toHaveBeenCalledWith(payload);
    expect(result.id).toBe(2);
    // fetchAnnotations called once on init + once after createAnnotation
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it("deleteAnnotation calls deleteAnnotation and refetches", async () => {
    const fetchMock = vi.fn().mockResolvedValue([makeAnnotation(1)]);
    const deleteMock = vi.fn().mockResolvedValue(undefined);
    const annotated = makeAnnotated({
      fetchAnnotations: fetchMock,
      deleteAnnotation: deleteMock,
    });

    const { deleteAnnotation } = useAnnotations(annotated);
    await flush();

    await deleteAnnotation(1);

    expect(deleteMock).toHaveBeenCalledWith(1);
    // fetchAnnotations called once on init + once after deleteAnnotation
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it("refetch triggers a second fetch and updates annotations", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce([makeAnnotation(1)])
      .mockResolvedValueOnce([makeAnnotation(1), makeAnnotation(3)]);
    const annotated = makeAnnotated({ fetchAnnotations: fetchMock });

    const { annotations, refetch } = useAnnotations(annotated);
    await flush();

    expect(annotations.value).toHaveLength(1);

    await refetch();
    expect(annotations.value).toHaveLength(2);
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it("refetch resets error from a previous failure", async () => {
    const fetchMock = vi
      .fn()
      .mockRejectedValueOnce(new Error("fail"))
      .mockResolvedValueOnce([]);
    const annotated = makeAnnotated({ fetchAnnotations: fetchMock });

    const { error, refetch } = useAnnotations(annotated);
    await flush();

    expect(error.value).not.toBeNull();

    await refetch();
    expect(error.value).toBeNull();
  });
});
