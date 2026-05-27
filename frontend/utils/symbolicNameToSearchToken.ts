/**
 * UX-ANNO1 — heuristic: extract the most semantically meaningful search
 * token from a channel's symbolicName so the annotation dialog can
 * pre-fill the property search field.
 *
 * Rules (applied in order):
 *  1. Split on `_` separators and camelCase boundaries; lowercase all tokens.
 *  2. Single token → return it as-is (handles `pressure`, `X`→`x`, etc.).
 *  3. Multi-token → prefer the last token with length > 3 (filters short
 *     abbreviations like `rms`, `tcp`, `X`); fall back to the last token.
 *
 * Examples:
 *  compaction_force         → force
 *  tcp_temperature          → temperature
 *  turbopump_vibration_rms  → vibration
 *  pressure                 → pressure
 *  X                        → x
 */
export function symbolicNameToSearchToken(name: string): string {
  if (!name) return "";

  // Split on underscores and camelCase boundaries, lowercase, drop empties
  const tokens = name
    .replace(/([a-z])([A-Z])/g, "$1_$2") // camelCase → snake_case
    .toLowerCase()
    .split("_")
    .filter(t => t.length > 0);

  if (tokens.length === 0) return name.toLowerCase();
  if (tokens.length === 1) return tokens[0]!;

  // Last token with length > 3; fall back to last token
  for (let i = tokens.length - 1; i >= 0; i--) {
    if (tokens[i]!.length > 3) return tokens[i]!;
  }
  return tokens[tokens.length - 1]!;
}
