/**
 * PROJ-NAV-1 — composable that resolves the "Project" subset of Collections.
 *
 * A Project is an ordinary Collection with a semantic annotation carrying
 *   predicate = 'urn:shepard:project'
 *   value     = 'true'
 *
 * This composable implements the frontend-filter-first approach described in
 * `aidocs/integrations/121-project-and-subcollections.md §4.2`:
 *
 *  1. Fetch all Collections the user can see (paged, up to MAX_COLLECTIONS).
 *  2. For each Collection, fetch its semantic annotations.
 *  3. Retain Collections where the annotation predicate/value match.
 *  4. For retained Collections, also surface `urn:shepard:programme` values
 *     so the caller can render programme chips + side-filter.
 *
 * N+1 calls is acceptable for the alpha stage; replace with a dedicated
 * backend filter endpoint (PROJ-REST-1-FILTER) when collection counts grow.
 *
 * The annotation predicate IRI is compared against `propertyIRI` on each
 * SemanticAnnotation.  The `valueIRI` is empty for free-text literals; the
 * literal is in `valueName`.  We accept `valueName === 'true'` OR
 * `valueIRI === 'true'` to be forward-compatible with SHACL boolean vs
 * string literal serialisations.
 */

import {
  CollectionApi,
  SemanticAnnotationApi,
  type Collection,
  type SemanticAnnotation,
} from "@dlr-shepard/backend-client";
import { useShepardApi } from "~/composables/common/api/useShepardApi";

/** IRI used to mark a Collection as a Project. */
export const PROJECT_PREDICATE = "urn:shepard:project";

/** IRI used to carry the programme name on a Project Collection. */
export const PROGRAMME_PREDICATE = "urn:shepard:programme";

/** Maximum number of Collections to fetch in the filter pass. */
const MAX_COLLECTIONS = 500;

export interface ProjectCollection {
  collection: Collection;
  /** Values of `urn:shepard:programme` annotations on this Collection. */
  programmes: string[];
}

export function useFetchProjectCollections() {
  const collectionApi = useShepardApi(CollectionApi);
  const annotationApi = useShepardApi(SemanticAnnotationApi);

  const projects = ref<ProjectCollection[]>([]);
  const loading = ref(true);
  const error = ref<string | null>(null);

  async function fetch() {
    // Skip SSR — requires auth; runs client-side only.
    if (!import.meta.client) return;

    loading.value = true;
    error.value = null;
    projects.value = [];

    try {
      // Step 1: fetch all visible Collections (capped at MAX_COLLECTIONS).
      const collections = await collectionApi.value.getAllCollections({
        page: 0,
        size: MAX_COLLECTIONS,
      });

      if (collections.length === 0) {
        loading.value = false;
        return;
      }

      // Step 2: fetch annotations for all Collections concurrently.
      const annotationResults = await Promise.allSettled(
        collections.map(c =>
          annotationApi.value
            .getAllCollectionAnnotations({ collectionId: c.id! })
            .catch((): SemanticAnnotation[] => []),
        ),
      );

      // Step 3: filter for Project Collections and extract programme values.
      const resolved: ProjectCollection[] = [];
      for (let i = 0; i < collections.length; i++) {
        const result = annotationResults[i] as PromiseSettledResult<SemanticAnnotation[]> | undefined;
        const annotations: SemanticAnnotation[] =
          result?.status === "fulfilled" ? result.value : [];

        if (isProjectCollection(annotations)) {
          resolved.push({
            collection: collections[i]!,
            programmes: extractProgrammes(annotations),
          });
        }
      }

      projects.value = resolved;
    } catch (e) {
      handleError(e, "fetching project collections");
      error.value = "Could not load Projects. Check your connection and try again.";
    } finally {
      loading.value = false;
    }
  }

  fetch();

  return {
    projects,
    loading,
    error,
    refetch: fetch,
  };
}

/**
 * Returns true when the given annotation list contains a Project marker:
 *   propertyIRI === 'urn:shepard:project'  AND  (valueName OR valueIRI) === 'true'
 */
export function isProjectCollection(annotations: SemanticAnnotation[]): boolean {
  return annotations.some(
    a =>
      a.propertyIRI === PROJECT_PREDICATE &&
      (a.valueName === "true" || a.valueIRI === "true"),
  );
}

/**
 * Extracts `urn:shepard:programme` literal values from the annotation list.
 * Returns an empty array when none are present.
 */
export function extractProgrammes(annotations: SemanticAnnotation[]): string[] {
  return annotations
    .filter(a => a.propertyIRI === PROGRAMME_PREDICATE && !!a.valueName)
    .map(a => a.valueName as string);
}
