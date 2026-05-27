/**
 * UI19 — composable for `skos:related` concept discovery.
 *
 * Given a term URI, issues a SELECT query against the INTERNAL n10s
 * SPARQL endpoint (`POST /v2/semantic/internal/sparql`) to retrieve
 * any `skos:related` neighbours and their labels.
 *
 * Returns an empty array when:
 *   - the URI is blank / not a well-formed absolute IRI
 *   - the backend returns an error
 *   - no `skos:related` triples exist for the term
 *
 * The INTERNAL repository is used because the `skos:related` cross-links
 * live in the pre-seeded n10s graph (e.g. `lumen:SteadyState skos:related
 * shex:TestRun` from `lumen-inspired.ttl`).
 */

export interface SkosRelatedTerm {
  uri: string;
  label: string | null;
}

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

/** Build a safe SPARQL query using a VALUES binding to avoid injection. */
function buildQuery(termUri: string): string {
  return `PREFIX skos: <http://www.w3.org/2004/02/skos/core#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT ?related ?label WHERE {
  VALUES ?term { <${termUri}> }
  ?term skos:related ?related .
  OPTIONAL { ?related skos:prefLabel ?label }
  OPTIONAL { ?related rdfs:label ?label }
} LIMIT 20`;
}

export function useSkosRelated() {
  const related = ref<SkosRelatedTerm[]>([]);
  const loading = ref(false);

  async function fetchRelated(termUri: string): Promise<void> {
    related.value = [];

    if (!termUri || !termUri.trim()) return;

    // Validate that it looks like an absolute IRI before sending.
    try {
      const parsed = new URL(termUri.trim());
      if (!parsed.protocol.startsWith("http")) return;
    } catch {
      return;
    }

    loading.value = true;
    try {
      const { data: session } = useAuth();
      const accessToken = session.value?.accessToken;

      const url = `${v2BaseUrl()}/v2/semantic/internal/sparql`;
      const body = "query=" + encodeURIComponent(buildQuery(termUri.trim()));

      const response = await fetch(url, {
        method: "POST",
        headers: {
          "Content-Type": "application/x-www-form-urlencoded",
          Accept: "application/sparql-results+json, application/json",
          ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
        },
        body,
      });

      if (!response.ok) return;

      const json = await response.json() as {
        head: { vars: string[] };
        results: { bindings: Array<Record<string, { type: string; value: string } | undefined>> };
      };

      const seen = new Set<string>();
      const rows: SkosRelatedTerm[] = [];
      for (const binding of json.results?.bindings ?? []) {
        const relatedVal = binding["related"];
        if (!relatedVal || relatedVal.type !== "uri") continue;
        const uri = relatedVal.value;
        if (seen.has(uri)) continue;
        seen.add(uri);

        const labelVal = binding["label"];
        const label = labelVal?.value ?? null;
        rows.push({ uri, label });
      }
      related.value = rows;
    } catch {
      // Swallow errors — the panel simply stays hidden on failure.
    } finally {
      loading.value = false;
    }
  }

  function clear(): void {
    related.value = [];
  }

  return { related, loading, fetchRelated, clear };
}
