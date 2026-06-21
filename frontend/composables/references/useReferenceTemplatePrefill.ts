/**
 * REF-EDIT-TPL-3 / REF-EDIT-TPL-6 — template-driven create prefill composable.
 *
 * Thin wrapper that fetches the parent DataObject's semantic annotations and
 * returns them for the pure helpers in `referenceTemplatePrefill.ts` to
 * consume. Separated from the helpers so the helpers stay dependency-free and
 * unit-testable.
 *
 * Judgement call: the spec says "fetch the parent DataObject's ShepardTemplate
 * and look for the annotation matching the predicate". `ShepardTemplateIO`
 * exposes only an opaque JSON `body` string with no annotation field. The
 * material hint the user actually sees is the parent DataObject's annotation
 * set — which is what the template instantiation populates. We read parent
 * DataObject annotations here. If a future iteration ships template-level
 * annotations as a separate surface, the predicate lookup can be redirected
 * without changing the caller shape.
 */
import type { SemanticAnnotation } from "@dlr-shepard/backend-client";
import { AnnotatedDataObject } from "~/composables/annotated";

export {
  REFERENCE_PREDICATE,
  extractFileNamingPattern,
  extractChannelSelectionHint,
  findAnnotationByPredicate,
  parseUriRelationshipHint,
  resolveFileNamingPlaceholders,
  type ChannelSelectionHint,
  type UriRelationshipHint,
} from "~/composables/references/referenceTemplatePrefill";

/**
 * Fetch the annotation set for the parent DataObject. Returns [] on error so
 * the caller can fail soft (the create dialog still opens, just without
 * prefill). Keyed by the parent DataObject's appId (v2 polymorphic surface).
 */
export async function fetchReferencePrefillAnnotations(
  dataObjectAppId: string,
): Promise<SemanticAnnotation[]> {
  if (!dataObjectAppId) return [];
  try {
    const annotated = new AnnotatedDataObject(dataObjectAppId);
    return await annotated.fetchAnnotations();
  } catch {
    return [];
  }
}
