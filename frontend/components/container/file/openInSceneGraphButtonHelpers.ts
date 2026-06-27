/**
 * SCENEGRAPH-NAV-02 — pure helpers for the OpenInSceneGraphButton.
 *
 * Extracted so unit tests can import without mounting Vue / Vuetify.
 *
 * Rationale (predicate set, 2026-05-30):
 *   The MFFD seed (`examples/mffd-rdk-urdf-showcase/seed.py`) and the
 *   bootstrap script (`scenegraph/build_mffd_scene.py`) write the
 *   following annotations on a FileReference today:
 *     - `urn:shepard:rdk:*` — set by RdkTextScrapeParser on RDK uploads
 *       (appVersion, platform, cadRef, …). Any one of these signals the
 *       FileReference is an RDK station file and is scene-graph eligible.
 *     - `urn:shepard:scenegraph:scene-appId` — back-annotation written by
 *       `build_mffd_scene.py` after a scene has been minted. Object literal
 *       is the appId of the `:DigitalTwinScene`. Presence flips the button
 *       from "Create scene" to "Open in scene-graph editor".
 *   Two sibling predicates the task brief invited us to also accept —
 *   `urn:shepard:rdk:role` and `urn:shepard:urdf:role` — are not in use
 *   today, but we honour them as an additive forward-compat signal so a
 *   future tier-2 RDK scrape that emits `role = scene-graph-source` or a
 *   URDF parser that emits `role = urdf` lights the button without a
 *   second pass through this helper.
 *   The conservative filename fallback (`.urdf` / `.rdk` extension on the
 *   FileReference name) covers the case where a URDF was uploaded but no
 *   parser plugin has yet attached a `urn:shepard:rdk:*` annotation.
 *
 * Why no IRI-prefix MAY-list for RDK: the prefix `urn:shepard:rdk:` is
 * owned by the robotics plugin (`RdkAnnotations.RDK_PREDICATE_PREFIX`)
 * and every key under it implies the FileReference is RDK-shaped. The
 * cheaper presence check therefore reads: "does ANY annotation start
 * with the prefix?" — see `hasSceneGraphRole` below.
 */

export const RDK_PREFIX = "urn:shepard:rdk:";
export const URDF_PREFIX = "urn:shepard:urdf:";
export const SCENE_APP_ID_PREDICATE = "urn:shepard:scenegraph:scene-appId";

/** Optional explicit role markers (not in production seed today, accepted forward-compat). */
export const RDK_ROLE_PREDICATE = "urn:shepard:rdk:role";
export const URDF_ROLE_PREDICATE = "urn:shepard:urdf:role";

/** Minimal shape of a `SemanticAnnotation` we read here. */
export interface AnnotationLike {
  propertyIRI?: string | null;
  valueIRI?: string | null;
}

/**
 * Does this FileReference look like a scene-graph source?
 *
 * Returns true when any of these signals are present:
 *  - The back-annotation `urn:shepard:scenegraph:scene-appId` exists
 *    (scene already minted — strongest signal).
 *  - Any `urn:shepard:rdk:*` predicate exists (RDK file detected by the
 *    fileformat-robotics plugin).
 *  - Any `urn:shepard:urdf:*` predicate exists (URDF parser plugin
 *    detected URDF semantics; not in tree today, accepted forward-compat).
 *  - The filename ends in `.urdf` or `.rdk` (cheap fallback when no
 *    parser has attached annotations yet).
 */
export function hasSceneGraphRole(
  annotations: readonly AnnotationLike[] | null | undefined,
  fileReferenceName?: string | null,
): boolean {
  const ann = annotations ?? [];
  for (const a of ann) {
    const iri = a?.propertyIRI ?? "";
    if (!iri) continue;
    if (iri === SCENE_APP_ID_PREDICATE) return true;
    if (iri.startsWith(RDK_PREFIX)) return true;
    if (iri.startsWith(URDF_PREFIX)) return true;
  }
  const name = (fileReferenceName ?? "").toLowerCase();
  if (name.endsWith(".urdf") || name.endsWith(".rdk")) return true;
  return false;
}

/**
 * Locate the bootstrapped scene appId, if a scene has already been
 * minted for this FileReference. Returns the literal scene appId value
 * of the `urn:shepard:scenegraph:scene-appId` annotation, or null.
 */
export function findSceneAppId(
  annotations: readonly AnnotationLike[] | null | undefined,
): string | null {
  const ann = annotations ?? [];
  for (const a of ann) {
    if (a?.propertyIRI === SCENE_APP_ID_PREDICATE) {
      const v = (a?.valueIRI ?? "").trim();
      if (v) return v;
    }
  }
  return null;
}

/**
 * Build the in-app route to the per-scene editor page. Pure string
 * builder so tests can assert the exact route without mounting the page.
 */
export function sceneGraphRouteFor(sceneAppId: string): string {
  return `/scene-graphs/${encodeURIComponent(sceneAppId)}`;
}
