/**
 * SEMA-V6-005 — composable for semantic annotation CRUD.
 *
 * Wraps the existing per-entity `Annotated` interface (which routes to the
 * correct per-container or per-entity REST endpoint). When SEMA-V6-004
 * ships the polymorphic `/v2/annotations/*` surface this composable can be
 * updated to call that single endpoint instead — callers are unchanged.
 *
 * Usage:
 *   const { annotations, loading, error, refetch } = useAnnotations(annotated)
 *   const { createAnnotation, deleteAnnotation } = useAnnotations(annotated)
 */
import type { SemanticAnnotation } from "@dlr-shepard/backend-client";
import type { Annotated, AnnotationToAdd } from "~/composables/annotated";

export function useAnnotations(annotated: Annotated) {
  const annotations = ref<SemanticAnnotation[]>([]);
  const loading = ref(false);
  const error = ref<string | null>(null);

  async function fetchAnnotations(): Promise<void> {
    loading.value = true;
    error.value = null;
    try {
      annotations.value = await annotated.fetchAnnotations();
    } catch (e) {
      error.value = "Could not load annotations.";
      handleError(e, "fetching annotations");
    } finally {
      loading.value = false;
    }
  }

  async function createAnnotation(payload: AnnotationToAdd): Promise<SemanticAnnotation> {
    const result = await annotated.addAnnotation(payload);
    await fetchAnnotations();
    return result;
  }

  async function deleteAnnotation(annotationId: number): Promise<void> {
    await annotated.deleteAnnotation(annotationId);
    await fetchAnnotations();
  }

  async function refetch(): Promise<void> {
    await fetchAnnotations();
  }

  fetchAnnotations();

  return { annotations, loading, error, refetch, createAnnotation, deleteAnnotation };
}
