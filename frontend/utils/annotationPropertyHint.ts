/**
 * Convert a machine-readable channel identifier (snake_case, kebab-case, or
 * camelCase) into a human-readable search term suitable for pre-filling the
 * annotation property search field.
 *
 * Examples:
 *   "compaction_force"  → "compaction force"
 *   "TCP-temperature"   → "TCP temperature"
 *   "vibrationRmsX"     → "vibration Rms X"
 *
 * The result is passed directly to the ontology term-search endpoint.  It is a
 * hint only — the user still selects from suggestions and can override freely.
 */
export function normalizePropertyHint(raw: string): string {
  return raw
    .replace(/([a-z])([A-Z])/g, "$1 $2") // camelCase → words
    .replace(/[_\-]+/g, " ")             // snake / kebab → space
    .trim();
}
