/**
 * L4 — search-as-you-type composable for the ontology search surface.
 *
 * Wraps the v2 term-search composable (`useTermSearch`, which calls
 * `GET /v2/semantic/terms/search`) with reactive, debounced state so the
 * `/semantic/search` page can bind a single `query` ref and render results
 * live. The actual fetch is debounced 300 ms (matching the shared
 * `SearchField` cadence) to avoid a request per keystroke.
 *
 * Frontend-v2-only: the underlying `useTermSearch` already targets the
 * `/v2/` surface and addresses terms by their IRI (not a numeric id), so this
 * composable inherits that posture. No `useShepardApi` (v1) anywhere.
 *
 * Returns the flat suggestion list plus a derived namespace → term tree
 * (`buildTermTree`) and an ECharts graph projection (`buildTermGraph`) so the
 * page can offer both a tree view and a graph view of the matched terms.
 */

import { useTermSearch, type TermSuggestion } from "~/composables/context/useTermSearch";
import {
  buildTermTree,
  buildTermGraph,
  countTerms,
  type TermTreeNamespace,
} from "~/utils/ontologyTermTree";

/** Minimum query length before a search fires (matches the backend's 2-char floor). */
export const MIN_QUERY_LENGTH = 2;

/** Debounce window for search-as-you-type, in milliseconds. */
export const SEARCH_DEBOUNCE_MS = 300;

export function useOntologySearch() {
  const { search } = useTermSearch();

  const query = ref("");
  const results = ref<TermSuggestion[]>([]);
  const loading = ref(false);
  const error = ref<string | null>(null);
  /** True once at least one query (>= MIN_QUERY_LENGTH) has resolved. */
  const searched = ref(false);

  const tree = computed<TermTreeNamespace[]>(() => buildTermTree(results.value));
  const graph = computed(() => buildTermGraph(tree.value));
  const total = computed(() => countTerms(tree.value));

  let debounce: ReturnType<typeof setTimeout> | null = null;
  // Guards against a slow earlier request resolving after a newer one and
  // clobbering fresher results.
  let runId = 0;

  async function run(q: string): Promise<void> {
    const trimmed = q.trim();
    if (trimmed.length < MIN_QUERY_LENGTH) {
      results.value = [];
      searched.value = false;
      loading.value = false;
      error.value = null;
      return;
    }
    const myRun = ++runId;
    loading.value = true;
    error.value = null;
    try {
      const items = await search(trimmed, 50);
      if (myRun !== runId) return; // a newer query superseded this one
      results.value = Array.isArray(items) ? items : [];
      searched.value = true;
    } catch (e: unknown) {
      if (myRun !== runId) return;
      error.value = e instanceof Error ? e.message : String(e);
      results.value = [];
    } finally {
      if (myRun === runId) loading.value = false;
    }
  }

  // Search-as-you-type: debounce the watcher on `query`.
  watch(query, (next) => {
    if (debounce) clearTimeout(debounce);
    const trimmed = next.trim();
    if (trimmed.length < MIN_QUERY_LENGTH) {
      // Clear immediately on too-short input — no pending request.
      results.value = [];
      searched.value = false;
      loading.value = false;
      error.value = null;
      return;
    }
    debounce = setTimeout(() => run(next), SEARCH_DEBOUNCE_MS);
  });

  onUnmounted(() => {
    if (debounce) clearTimeout(debounce);
  });

  return { query, results, loading, error, searched, tree, graph, total, run };
}
