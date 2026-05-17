/**
 * N1e — composable for semantic term autocomplete.
 *
 * Wraps GET /v2/semantic/terms/search?q=…&limit=… which returns
 * TermSuggestion objects from n10s :Resource nodes loaded into the
 * INTERNAL semantic repository.
 *
 * Returns an empty array when the query is too short (< 2 chars),
 * when the backend returns an error, or when no ontology data is loaded —
 * the endpoint guarantees 200 + empty array in those cases.
 */

export interface TermSuggestion {
  uri: string;
  label: string;
  description?: string | null;
}

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

export function useTermSearch() {
  async function search(q: string, limit = 20): Promise<TermSuggestion[]> {
    const trimmed = q?.trim() ?? "";
    if (trimmed.length < 2) return [];

    const { data: session } = useAuth();
    const accessToken = session.value?.accessToken;
    const params = new URLSearchParams({
      q: trimmed,
      limit: String(Math.min(limit, 50)),
    });
    const url = `${v2BaseUrl()}/v2/semantic/terms/search?${params}`;

    try {
      const resp = await fetch(url, {
        headers: {
          Authorization: `Bearer ${accessToken}`,
          Accept: "application/json",
        },
      });
      if (!resp.ok) return [];
      return (await resp.json()) as TermSuggestion[];
    } catch {
      return [];
    }
  }

  return { search };
}
