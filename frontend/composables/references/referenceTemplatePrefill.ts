/**
 * REF-EDIT-TPL-3 / REF-EDIT-TPL-4 / REF-EDIT-TPL-6 — pure helpers for
 * template-driven create prefill on reference types.
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
  /** REF-EDIT-TPL-4 — FileBundleReference bundle name + accepted file extensions. */
  BUNDLE_LAYOUT: "urn:shepard:reference:bundleLayout",
  /** REF-EDIT-TPL-6 — URIReference relationship + uri prefix. */
  URI_RELATIONSHIP: "urn:shepard:reference:uriRelationship",
  /** REF-EDIT-1 — TimeseriesReference default channel selection + window duration. */
  CHANNEL_SELECTION: "urn:shepard:reference:channelSelection",
} as const;

/**
 * REF-EDIT-1 — parsed shape for the urn:shepard:reference:channelSelection hint.
 *
 * The annotation valueName is a JSON object with an optional channel list and
 * an optional default window duration in nanoseconds:
 * {"channels":[{"measurement":"vibration","device":"turbopump",...}],"windowDurationNs":30000000000}
 */
export interface ChannelSelectionHint {
  channels: Array<{
    measurement?: string;
    device?: string;
    location?: string;
    symbolicName?: string;
    field?: string;
  }>;
  windowDurationNs?: number;
}

/**
 * Extract a ChannelSelectionHint from a urn:shepard:reference:channelSelection annotation.
 * Returns null when the annotation is absent, has no valueName, or the valueName is not
 * valid JSON with at least one channel entry.
 */
export function extractChannelSelectionHint(
  annotation: SemanticAnnotation | null,
): ChannelSelectionHint | null {
  if (!annotation) return null;
  const raw = annotation.valueName?.trim();
  if (!raw || raw.length === 0) return null;
  try {
    const parsed = JSON.parse(raw) as Record<string, unknown>;
    const channels = Array.isArray(parsed.channels) ? parsed.channels : [];
    if (channels.length === 0) return null;
    const typedChannels = channels
      .filter((ch): ch is Record<string, unknown> => ch !== null && typeof ch === "object")
      .map(ch => ({
        measurement: typeof ch.measurement === "string" ? ch.measurement : undefined,
        device: typeof ch.device === "string" ? ch.device : undefined,
        location: typeof ch.location === "string" ? ch.location : undefined,
        symbolicName: typeof ch.symbolicName === "string" ? ch.symbolicName : undefined,
        field: typeof ch.field === "string" ? ch.field : undefined,
      }));
    if (typedChannels.length === 0) return null;
    return {
      channels: typedChannels,
      windowDurationNs: typeof parsed.windowDurationNs === "number" ? parsed.windowDurationNs : undefined,
    };
  } catch {
    return null;
  }
}

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

/**
 * REF-EDIT-TPL-4 — parsed shape for the `urn:shepard:reference:bundleLayout` hint.
 *
 * The annotation `valueName` is a JSON object:
 *   {"name":"measurements-{date}","acceptExtensions":[".tif",".png"]}
 *
 * `name` is a naming pattern supporting the same `{date}` placeholder as
 * `urn:shepard:reference:fileNaming` — resolved by `resolveFileNamingPlaceholders`.
 * `acceptExtensions` is an optional list of file extensions (with leading `.`)
 * used to hint the file picker's accept filter.
 */
export interface BundleLayoutHint {
  name?: string;
  acceptExtensions?: string[];
}

/**
 * Parse a bundle-layout hint from an annotation. Returns null when the
 * annotation is absent, has no usable valueName, or the JSON carries neither
 * a name nor acceptExtensions.
 */
export function parseBundleLayoutHint(
  annotation: SemanticAnnotation | null,
): BundleLayoutHint | null {
  if (!annotation) return null;
  const raw = annotation.valueName?.trim();
  if (!raw || raw.length === 0) return null;
  if (!raw.startsWith("{")) return null;
  try {
    const parsed = JSON.parse(raw) as Record<string, unknown>;
    const name = typeof parsed.name === "string" && parsed.name.trim().length > 0
      ? parsed.name.trim()
      : undefined;
    const acceptExtensions = Array.isArray(parsed.acceptExtensions)
      ? parsed.acceptExtensions.filter(
          (e): e is string => typeof e === "string" && e.length > 0,
        )
      : undefined;
    if (!name && (!acceptExtensions || acceptExtensions.length === 0)) return null;
    return {
      name,
      acceptExtensions: acceptExtensions && acceptExtensions.length > 0
        ? acceptExtensions
        : undefined,
    };
  } catch {
    return null;
  }
}
