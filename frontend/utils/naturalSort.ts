/**
 * Natural (human) ordering for dropdown / list items.
 *
 * UIRULE-DROPDOWN-SEARCH-SORT (2026-07-10): every dropdown in the app orders its
 * options in natural order — numeric segments compared numerically, so
 * "PlyGroup 2" precedes "PlyGroup 10" and "Track 9" precedes "Track 10", not the
 * lexicographic "10 < 2" default. Case-insensitive, locale-aware.
 *
 * Pairs with search-as-you-type (v-autocomplete over v-select) — see the
 * `## Always: dropdowns are searchable and naturally ordered` rule in CLAUDE.md.
 */

/** Compare two strings in natural (numeric-aware, case-insensitive) order. */
export function naturalCompare(a: string, b: string): number {
  return (a ?? "").localeCompare(b ?? "", undefined, {
    numeric: true,
    sensitivity: "base",
  });
}

/**
 * Return a NEW array of the items sorted in natural order by `label`.
 *
 * @param items the options to order (not mutated)
 * @param label extracts the human-facing string an item sorts by. Defaults to
 *   `String(item)` for plain string/number option arrays; pass a selector for
 *   object options (e.g. `i => i.title`).
 */
export function naturalSort<T>(
  items: readonly T[],
  label: (item: T) => string = item => String(item),
): T[] {
  return [...items].sort((a, b) => naturalCompare(label(a), label(b)));
}
