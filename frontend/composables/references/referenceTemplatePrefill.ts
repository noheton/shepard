/**
 * REF-EDIT-TPL-3 / REF-EDIT-TPL-6 — pure helpers for template-driven create
 * prefill on reference types.
 *
 * Kept dependency-free (no Nuxt / Shepard API imports) so it can be unit-tested
 * directly. The thin composable that fetches annotations lives in
 * `useReferenceTemplatePrefill.ts`.
 *
 * The CLAUDE.md cross-cutting rule (2026-05-28) requires every reference type's
 * Create dialog to pre-fill from a parent-DataObject `ShepardTemplate` when the
 * template carries a reference-creation hint. Hints are surfaced as
 * `SemanticAnnotation` rows on the parent DataObject with a well-known
 * `propertyIRI` from the `urn:shepard:reference:*` namespace.
 */
import type { SemanticAnnotation } from "@dlr-shepard/backend-client";

/** Predicate IRIs that carry reference-create hints. */
export const REFERENCE_PREDICATE = {
  /** REF-EDIT-TPL-3 — FileReference name pattern. */
  FILE_NAMING: "urn:shepard:reference:fileNaming",
  /** REF-EDIT-TPL-6 — URIReference relationship + uri prefix. */
  URI_RELATIONSHIP: "urn:shepard:reference:uriRelationship",
} as const;

/**
 * Parsed shape for the `urn:shepard:reference:uriRelationship` hint. The
 * underlying `valueName` can be either a JSON object literal carrying both
 * fields, or a plain string carrying just the relationship.
 */
export interface UriRelationshipHint {
  relationship?: string;
  uriPrefix?: string;
}

/**
 * Find the first annotation matching `propertyIRI`. Returns null when no
 * match exists or the input is empty.
 */
export function findAnnotationByPredicate(
  annotations: SemanticAnnotation[] | undefined | null,
  propertyIRI: string,
): SemanticAnnotation | null {
  if (!annotations || annotations.length === 0) return null;
  return annotations.find(a => a.propertyIRI === propertyIRI) ?? null;
}

/**
 * Extract the file-naming pattern string from an annotation. We deliberately
 * keep the value literal (placeholders like `{date}` are preserved) — the
 * user can resolve them by typing, or `resolveFileNamingPlaceholders` can
 * substitute known placeholders.
 */
export function extractFileNamingPattern(
  annotation: SemanticAnnotation | null,
): string | null {
  if (!annotation) return null;
  const raw = annotation.valueName?.trim();
  return raw && raw.length > 0 ? raw : null;
}

/**
 * Resolve a small set of known placeholders in a file-naming pattern. Today
 * only `{date}` → ISO date string (YYYY-MM-DD). Unknown placeholders stay
 * verbatim so the user can fill them in.
 */
export function resolveFileNamingPlaceholders(
  pattern: string,
  now: Date = new Date(),
): string {
  const iso = now.toISOString().slice(0, 10);
  return pattern.replace(/\{date\}/g, iso);
}

/**
 * Parse a URI-relationship hint. The annotation `valueName` may be:
 *   1. A JSON object literal like
 *      `{"relationship":"prov:wasDerivedFrom","uriPrefix":"https://doi.org/"}`
 *   2. A plain string carrying just the relationship name
 *   3. Empty / null
 *
 * Returns the parsed shape, or null when no usable value is present.
 */
export function parseUriRelationshipHint(
  annotation: SemanticAnnotation | null,
): UriRelationshipHint | null {
  if (!annotation) return null;
  const raw = annotation.valueName?.trim();
  if (!raw || raw.length === 0) return null;

  // Try JSON-object shape first (cheap heuristic: starts with `{`).
  if (raw.startsWith("{")) {
    try {
      const parsed = JSON.parse(raw) as Record<string, unknown>;
      const relationship =
        typeof parsed.relationship === "string" ? parsed.relationship : undefined;
      const uriPrefix =
        typeof parsed.uriPrefix === "string" ? parsed.uriPrefix : undefined;
      // JSON parsed successfully — return the structured shape (or null when
      // neither relationship nor uriPrefix is present). Do not fall through
      // to plain-string treatment, which would otherwise interpret the JSON
      // text itself as a relationship label.
      if (relationship || uriPrefix) {
        return { relationship, uriPrefix };
      }
      return null;
    } catch {
      // Malformed JSON → fall through to plain-string treatment.
    }
  }

  // Plain string → treat as the relationship label.
  return { relationship: raw };
}
