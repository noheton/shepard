/**
 * V2CONV-B6 — the predicate palette for the visual template editor.
 *
 * Merges two read-only semantic sources into one ranked palette of pickable
 * predicates:
 *
 *   1. `GET /v2/shapes/predicates` — the substrate-routing vocabulary table
 *      (`PredicateVocabularyEntryIO`). Typed: carries the predicate IRI, a
 *      cardinality hint (`one | many`), a writable flag, and a description.
 *      Loaded once on mount; this is the "known good" curated set.
 *   2. `GET /v2/semantic/terms/search?q=…` — the n10s autocomplete
 *      (`TermSuggestionIO`). Used when the user types: surfaces ontology terms
 *      that aren't (yet) in the routing table, with `rdfs:label` display.
 *
 * Both are reached with the same raw-fetch + base-URL + bearer-token pattern the
 * sibling semantic composables (`usePredicateStats`, `useSemanticVocabularyPredicates`)
 * already use. Fail-soft: a 404 / network error yields an empty list, never a throw.
 *
 * Design: aidocs/platform/191-v2-surface-convergence.md §3; reuses the
 * annotation-picker data sources per the B6 brief.
 */
import { ref, type Ref } from "vue";

/** One pickable predicate in the palette. */
export interface PalettePredicate {
  uri: string;
  label: string | null;
  description: string | null;
  /** "one" | "many" from the routing table; null for autocomplete-only hits. */
  cardinality: string | null;
  /** Suggested literal datatype IRI when the source implies one; else null. */
  datatype: string | null;
  /** Where this entry came from — drives a small badge in the UI. */
  source: "vocabulary" | "search";
  /**
   * Storage substrate from the routing table (`neo4j | timescaledb | postgres | garage`).
   * Null for autocomplete-only search hits. Used for palette grouping.
   */
  substrate: string | null;
}

function v2Base(): string {
  const { public: publicConfig } = useRuntimeConfig();
  const explicit = (publicConfig as { backendV2ApiUrl?: string }).backendV2ApiUrl;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (publicConfig.backendApiUrl as string).replace(/\/shepard\/api\/?$/, "").replace(/\/$/, "");
}

function authHeaders(): Record<string, string> {
  const { data: session } = useAuth();
  const token = session.value?.accessToken;
  return token ? { Authorization: `Bearer ${token}`, Accept: "application/json" } : { Accept: "application/json" };
}

interface PredicateVocabularyEntry {
  predicateUri: string;
  substrate?: string;
  cardinality?: string | null;
  writable?: boolean;
  description?: string | null;
}

interface TermSuggestion {
  uri: string;
  label: string;
  description?: string | null;
}

/**
 * Map a routing-table cardinality (`one | many`) into a richer hint the DSL
 * helper understands (`1` / `0..*`). Exported for the editor's default-fill.
 */
export function vocabCardinalityHint(cardinality?: string | null): string | null {
  if (!cardinality) return null;
  const c = cardinality.trim().toLowerCase();
  if (c === "one") return "0..1";
  if (c === "many") return "0..*";
  return null;
}

const XSD = "http://www.w3.org/2001/XMLSchema#";

/**
 * Heuristic: map a storage substrate to a suggested literal datatype.
 *
 * The backend `PredicateVocabularyEntryIO` does not carry an explicit
 * `rdfs:range` / datatype field — only a `substrate` routing hint.  This
 * mapping encodes the reasonable default per substrate:
 *
 * - `timescaledb` → numeric measurement (decimal is the widest safe pick)
 * - `postgres`    → plain string (Postgres text columns)
 * - `neo4j`       → no single canonical type; most properties are strings
 * - `garage`      → IRI / blob reference (no literal datatype)
 *
 * Returns `null` (= "no suggestion") when the substrate is unknown or when
 * the safest bet is to leave it unset (IRI objects, garage references).
 * Exported + pure for the editor and for unit tests.
 */
export function substrateToDatatype(substrate?: string | null): string | null {
  if (!substrate) return null;
  const s = substrate.trim().toLowerCase();
  if (s === "timescaledb") return XSD + "decimal";
  if (s === "postgres") return XSD + "string";
  if (s === "neo4j") return XSD + "string";
  // garage = binary blob / IRI — no literal datatype makes sense
  return null;
}

/**
 * Derive a human-readable group label from a predicate URI.
 * Splits on the last `#` or `/` to extract the namespace prefix.
 *
 * Examples:
 *   http://semantics.dlr.de/shepard#status → "http://semantics.dlr.de/shepard"
 *   http://www.w3.org/ns/prov#wasDerivedFrom → "http://www.w3.org/ns/prov"
 *   http://purl.org/dc/terms/title → "http://purl.org/dc/terms"
 */
export function paletteGroupLabel(uri: string): string {
  const hashIdx = uri.lastIndexOf("#");
  if (hashIdx > 0) return uri.slice(0, hashIdx);
  const slashIdx = uri.lastIndexOf("/");
  if (slashIdx > 0) return uri.slice(0, slashIdx);
  return uri;
}

/**
 * One section produced by `groupPaletteByNamespace`.
 */
export interface PaletteGroup {
  /** Namespace prefix used as the section header. */
  namespace: string;
  /** Ordered items within this namespace group. */
  items: PalettePredicate[];
}

/**
 * Group a flat predicate list into namespace sections for the palette.
 *
 * Preserves the original ordering within each group. Groups are ordered
 * by first occurrence (so the vocabulary section that appears earliest in
 * the flat list comes first).  Exported + pure for unit tests.
 */
export function groupPaletteByNamespace(items: PalettePredicate[]): PaletteGroup[] {
  const order: string[] = [];
  const map = new Map<string, PalettePredicate[]>();
  for (const item of items) {
    const ns = paletteGroupLabel(item.uri);
    if (!map.has(ns)) {
      order.push(ns);
      map.set(ns, []);
    }
    map.get(ns)!.push(item);
  }
  return order.map((ns) => ({ namespace: ns, items: map.get(ns)! }));
}

export function useShapePalette() {
  const vocabulary = ref<PalettePredicate[]>([]);
  const searchResults = ref<PalettePredicate[]>([]);
  const loadingVocabulary = ref(false);
  const searching = ref(false);
  const error = ref<string | null>(null);

  /** Load the curated substrate-routing vocabulary once. */
  async function loadVocabulary(): Promise<void> {
    loadingVocabulary.value = true;
    error.value = null;
    try {
      const res = await fetch(`${v2Base()}/v2/shapes/predicates`, { headers: authHeaders() });
      if (!res.ok) {
        error.value = `${res.status} ${res.statusText}`;
        vocabulary.value = [];
        return;
      }
      const rows = (await res.json()) as PredicateVocabularyEntry[];
      vocabulary.value = (Array.isArray(rows) ? rows : []).map((r) => ({
        uri: r.predicateUri,
        label: null,
        description: r.description ?? null,
        cardinality: vocabCardinalityHint(r.cardinality),
        datatype: substrateToDatatype(r.substrate),
        source: "vocabulary" as const,
        substrate: r.substrate ?? null,
      }));
    } catch (e) {
      error.value = e instanceof Error ? e.message : String(e);
      vocabulary.value = [];
    } finally {
      loadingVocabulary.value = false;
    }
  }

  /** Autocomplete via n10s term search. Min 2 chars (server enforces it too). */
  async function search(q: string): Promise<void> {
    const query = (q ?? "").trim();
    if (query.length < 2) {
      searchResults.value = [];
      return;
    }
    searching.value = true;
    try {
      const url = `${v2Base()}/v2/semantic/terms/search?q=${encodeURIComponent(query)}&pageSize=25`;
      const res = await fetch(url, { headers: authHeaders() });
      if (!res.ok) {
        searchResults.value = [];
        return;
      }
      const rows = (await res.json()) as TermSuggestion[];
      searchResults.value = (Array.isArray(rows) ? rows : []).map((r) => ({
        uri: r.uri,
        label: r.label ?? null,
        description: r.description ?? null,
        cardinality: null,
        datatype: null,
        source: "search" as const,
        substrate: null,
      }));
    } catch {
      searchResults.value = [];
    } finally {
      searching.value = false;
    }
  }

  return { vocabulary, searchResults, loadingVocabulary, searching, error, loadVocabulary, search };
}

/**
 * Pure client-side filter over the loaded vocabulary list. Exported so the
 * editor can filter the curated set instantly (no round-trip) while typing,
 * and so it is unit-testable. Case-insensitive over URI + label + description.
 */
export function filterPalette(items: PalettePredicate[], query: string): PalettePredicate[] {
  const q = (query ?? "").trim().toLowerCase();
  if (q.length === 0) return items;
  return items.filter(
    (p) =>
      p.uri.toLowerCase().includes(q) ||
      (p.label ?? "").toLowerCase().includes(q) ||
      (p.description ?? "").toLowerCase().includes(q),
  );
}

/**
 * Merge curated vocabulary + search hits, de-duplicated by URI (curated wins).
 * Exported + pure for unit tests and for the editor's combined list.
 */
export function mergePaletteSources(
  vocabulary: PalettePredicate[],
  searchResults: PalettePredicate[],
): PalettePredicate[] {
  const seen = new Set(vocabulary.map((p) => p.uri));
  const merged = [...vocabulary];
  for (const s of searchResults) {
    if (!seen.has(s.uri)) {
      merged.push(s);
      seen.add(s.uri);
    }
  }
  return merged;
}

/** Convenience: keep a typed reference to the loaded vocab for callers. */
export type ShapePaletteVocabularyRef = Ref<PalettePredicate[]>;
