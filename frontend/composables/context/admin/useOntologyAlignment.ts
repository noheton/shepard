/**
 * TPL3a-lite composable wrapping GET /v2/semantic/ontology/alignment.
 *
 * Returns the read-only registry of how Shepard concepts map to upper-ontology
 * classes (BFO 2020, IAO, PROV-O, IOF Core). Seeded by V67 migration; mapping
 * authority is aidocs/semantics/96-upper-ontology-alignment.md.
 *
 * Wire shape (from OntologyAlignmentIO):
 *   {
 *     appId: string,
 *     shepardConcept: string,            // "Collection", "DataObject", ...
 *     upperOntologyUri: string,          // full IRI
 *     relationshipType: string,          // "rdfs:subClassOf" | "owl:equivalentClass"
 *     confidence: string,                // "HIGH" | "MEDIUM" | "LOW"
 *     source: string,                    // aidocs path
 *     createdAt: number | null,
 *   }
 *
 * Endpoint is public (no JWT required) — same posture as the other
 * read-only semantic endpoints. Auth header still sent if available, but
 * not required.
 */

export interface OntologyAlignmentIO {
  appId: string;
  shepardConcept: string;
  upperOntologyUri: string;
  relationshipType: string;
  confidence: string;
  source: string;
  createdAt: number | null;
}

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

const ONTOLOGY_ALIGNMENT_URL = "/v2/semantic/ontology/alignment";

export function useOntologyAlignment() {
  const alignments = ref<OntologyAlignmentIO[]>([]);
  const isLoading = ref(false);
  const error = ref<string | null>(null);

  async function refresh() {
    isLoading.value = true;
    error.value = null;
    try {
      const headers: Record<string, string> = { Accept: "application/json" };
      try {
        const { data: session } = useAuth();
        const accessToken = session.value?.accessToken;
        if (accessToken) headers["Authorization"] = `Bearer ${accessToken}`;
      } catch {
        // composable runs outside an auth context in some tests — ignore.
      }
      const response = await fetch(
        `${v2BaseUrl()}${ONTOLOGY_ALIGNMENT_URL}`,
        { headers },
      );
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const envelope = (await response.json()) as { items: OntologyAlignmentIO[] };
      alignments.value = envelope.items ?? [];
    } catch (e) {
      error.value = "Failed to load ontology alignment registry";
      handleError(e, "fetching ontology alignment");
    } finally {
      isLoading.value = false;
    }
  }

  refresh();

  return { alignments, isLoading, error, refresh };
}
