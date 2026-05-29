/**
 * SEMA-V6-UI-FOLLOWUP — composable for the per-vocabulary predicate browse
 * surface (`GET /v2/semantic/vocabularies/{vocabId}/predicates`).
 *
 * Returns a refable list of predicates plus loading / error state. Falls
 * back to an empty list on 404 or network failure rather than throwing —
 * the page renders an empty-state card in that case.
 *
 * Design: aidocs/semantics/100 §4.
 */

export interface SemanticPredicate {
  appId: string;
  uri: string;
  label: string | null;
  vocabularyAppId: string | null;
  expectedObjectType: string | null;
  cardinality: string | null;
  required: boolean;
}

export interface VocabularyPredicatesResponse {
  vocabularyAppId: string;
  predicates: SemanticPredicate[];
}

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

export function useSemanticVocabularyPredicates() {
  const predicates = ref<SemanticPredicate[]>([]);
  const loading = ref(false);
  const error = ref<string | null>(null);
  const notFound = ref(false);

  async function fetchPredicates(vocabId: string): Promise<void> {
    predicates.value = [];
    error.value = null;
    notFound.value = false;

    if (!vocabId || !vocabId.trim()) {
      notFound.value = true;
      return;
    }

    loading.value = true;
    try {
      const { data: session } = useAuth();
      const accessToken = session.value?.accessToken;

      const url = `${v2BaseUrl()}/v2/semantic/vocabularies/${encodeURIComponent(vocabId.trim())}/predicates`;
      const headers: Record<string, string> = { Accept: "application/json" };
      if (accessToken) headers["Authorization"] = `Bearer ${accessToken}`;

      const response = await fetch(url, { headers });
      if (response.status === 404) {
        notFound.value = true;
        return;
      }
      if (!response.ok) {
        error.value = `${response.status} ${response.statusText}`;
        return;
      }

      const body = (await response.json()) as VocabularyPredicatesResponse;
      predicates.value = Array.isArray(body?.predicates) ? body.predicates : [];
    } catch (e: unknown) {
      error.value = e instanceof Error ? e.message : String(e);
    } finally {
      loading.value = false;
    }
  }

  return { predicates, loading, error, notFound, fetchPredicates };
}
