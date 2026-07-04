/**
 * V2CONV-B4-FE — pure eligibility helper for the OpenIn3dViewButton.
 *
 * Extracted so unit tests can import without mounting Vue / Vuetify. Replaces
 * the scene-graph-specific `openInSceneGraphButtonHelpers.ts` (deleted with the
 * bespoke scene-graph subsystem). The eligibility signals are unchanged — a
 * FileReference is 3D-view-eligible when it looks like a URDF / RDK robot file:
 *
 *  - any `urn:shepard:rdk:*` predicate (RDK file detected by the robotics
 *    fileformat plugin), or
 *  - any `urn:shepard:urdf:*` predicate (URDF parser plugin), or
 *  - the back-annotation `urn:shepard:mapping:scenegraph-template-appId`
 *    (a play template already exists — strongest signal), or
 *  - the filename ends in `.urdf` / `.rdk` (cheap fallback before any parser
 *    has attached annotations).
 */

export const RDK_PREFIX = "urn:shepard:rdk:";
export const URDF_PREFIX = "urn:shepard:urdf:";
export const MAPPING_TEMPLATE_PREDICATE =
  "urn:shepard:mapping:scenegraph-template-appId";

/** Minimal shape of a `SemanticAnnotation` we read here. */
export interface AnnotationLike {
  propertyIRI?: string | null;
  valueIRI?: string | null;
  valueName?: string | null;
}

/**
 * Does this FileReference look like a 3D-view (URDF/RDK robot) source?
 */
export function hasSceneGraphRole(
  annotations: readonly AnnotationLike[] | null | undefined,
  fileReferenceName?: string | null,
): boolean {
  const ann = annotations ?? [];
  for (const a of ann) {
    const iri = a?.propertyIRI ?? "";
    if (!iri) continue;
    if (iri === MAPPING_TEMPLATE_PREDICATE) return true;
    if (iri.startsWith(RDK_PREFIX)) return true;
    if (iri.startsWith(URDF_PREFIX)) return true;
  }
  const name = (fileReferenceName ?? "").toLowerCase();
  if (name.endsWith(".urdf") || name.endsWith(".rdk")) return true;
  return false;
}
