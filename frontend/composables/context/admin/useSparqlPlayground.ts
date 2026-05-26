/**
 * N1f — composable for the admin SPARQL playground pane.
 *
 * Uses POST /v2/semantic/{repoAppId}/sparql with
 * Content-Type: application/x-www-form-urlencoded (SPARQL 1.1 Protocol §2.1.3)
 * and accepts application/sparql-results+json.
 *
 * The backend's SparqlQueryValidator rejects mutation forms (INSERT, DELETE, …)
 * with 400; this composable surfaces the error detail as `error.value`.
 */

/** W3C SPARQL Results JSON — single binding value. */
export interface SparqlResultValue {
  type: "uri" | "literal" | "bnode";
  value: string;
  datatype?: string;
  "xml:lang"?: string;
}

/** One row: variable name → binding value (absent = unbound). */
export type SparqlBinding = Record<string, SparqlResultValue | undefined>;

/** W3C SPARQL Results JSON top-level shape. */
export interface SparqlResultsJson {
  head: { vars: string[] };
  results: { bindings: SparqlBinding[] };
  /** Present only for ASK queries */
  boolean?: boolean;
}

const DEFAULT_QUERY = `SELECT ?s ?p ?o
WHERE {
  ?s ?p ?o .
}
LIMIT 10`;

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

export function useSparqlPlayground() {
  const repoId = ref<string>("internal");
  const query = ref<string>(DEFAULT_QUERY);
  const isLoading = ref(false);
  const error = ref<string | null>(null);
  const results = ref<SparqlResultsJson | null>(null);

  const rowCount = computed(
    () => results.value?.results?.bindings?.length ?? 0,
  );

  async function runQuery() {
    if (!query.value.trim()) {
      error.value = "Query must not be empty.";
      return;
    }
    isLoading.value = true;
    error.value = null;
    results.value = null;

    try {
      const { data: session } = useAuth();
      const accessToken = session.value?.accessToken;

      const url = `${v2BaseUrl()}/v2/semantic/${encodeURIComponent(repoId.value)}/sparql`;
      const body = "query=" + encodeURIComponent(query.value);

      const response = await fetch(url, {
        method: "POST",
        headers: {
          "Content-Type": "application/x-www-form-urlencoded",
          Accept: "application/sparql-results+json, application/json",
          ...(accessToken
            ? { Authorization: `Bearer ${accessToken}` }
            : {}),
        },
        body,
      });

      const text = await response.text();

      if (!response.ok) {
        // Try to extract RFC 7807 problem detail or raw body.
        let detail = text;
        try {
          const prob = JSON.parse(text) as { detail?: string; title?: string };
          detail = prob.detail ?? prob.title ?? text;
        } catch {
          // not JSON — keep raw text
        }
        error.value = `HTTP ${response.status}: ${detail}`;
        return;
      }

      try {
        results.value = JSON.parse(text) as SparqlResultsJson;
      } catch {
        error.value = "Response was not valid JSON: " + text.slice(0, 200);
      }
    } catch (e: unknown) {
      error.value =
        e instanceof Error ? e.message : "Unexpected error: " + String(e);
      handleError(e, "useSparqlPlayground.runQuery");
    } finally {
      isLoading.value = false;
    }
  }

  function resetQuery() {
    query.value = DEFAULT_QUERY;
    results.value = null;
    error.value = null;
  }

  return {
    repoId,
    query,
    isLoading,
    error,
    results,
    rowCount,
    runQuery,
    resetQuery,
  };
}
