/**
 * FAIR4 — composable for fetching the server-side metadata completeness score.
 *
 * Calls `GET /v2/collections/{appId}/metadata-completeness` and exposes the
 * parsed response as reactive state.  The score mirrors the client-side
 * `computeMetadataCompleteness()` helper; when both are displayed together a
 * divergence is diagnostic (e.g. an old frontend bundle cached in a browser
 * vs. a newer server).
 *
 * Usage:
 * ```ts
 * const { score, isLoading, isError, refetch } = useMetadataCompleteness(collectionAppId);
 * ```
 *
 * The composable is fail-soft: network / auth errors set `isError = true` and
 * leave `score` as `null` — the widget renders a gentle "unavailable" state
 * rather than crashing.
 */

/** Shape of one check row returned by the server. */
export interface ServerCheckResult {
  checkId: string;
  label: string;
  passed: boolean;
  weight: number;
  hint: string | null;
}

/** Shape of the server response body. */
export interface MetadataCompletenessScore {
  collectionAppId: string;
  score: number;
  maxScore: number;
  percentage: number;
  checks: ServerCheckResult[];
}

/**
 * Fetch the server-side metadata completeness score for a Collection.
 *
 * @param collectionAppId - reactive ref (or plain string) holding the UUID v7
 *   appId of the Collection to score; the composable re-fetches automatically
 *   when this value changes.
 */
export function useMetadataCompleteness(collectionAppId: Ref<string | null> | string) {
  const appIdRef: Ref<string | null> =
    typeof collectionAppId === "string" ? ref(collectionAppId) : collectionAppId;

  const score = ref<MetadataCompletenessScore | null>(null);
  const isLoading = ref(false);
  const isError = ref(false);

  // Derive the v2 base URL the same way useV2ShepardApi does.
  const v2BaseUrl = computed<string>(() => {
    const config = useRuntimeConfig().public;
    const explicit = config.backendV2ApiUrl as string | undefined;
    return explicit && explicit.length > 0
      ? explicit
      : (config.backendApiUrl as string).replace(/\/shepard\/api\/?$/, "");
  });

  const { data: authData } = useAuth();

  async function fetch(appId: string) {
    const token = authData.value?.accessToken;
    if (!token) {
      isError.value = true;
      return;
    }

    isLoading.value = true;
    isError.value = false;

    try {
      const url = `${v2BaseUrl.value}/v2/collections/${encodeURIComponent(appId)}/metadata-completeness`;
      const response = await globalThis.fetch(url, {
        headers: { Authorization: `Bearer ${token}` },
      });

      if (!response.ok) {
        isError.value = true;
        score.value = null;
        return;
      }

      score.value = (await response.json()) as MetadataCompletenessScore;
    } catch {
      // Network error — fail-soft.
      isError.value = true;
      score.value = null;
    } finally {
      isLoading.value = false;
    }
  }

  watch(
    appIdRef,
    appId => {
      if (appId) void fetch(appId);
    },
    { immediate: true },
  );

  // Re-fire when the auth token settles (same race-condition mitigation as
  // MetadataCompletenessCard.vue uses for annotation + ORCID fetches).
  watch(
    () => authData.value?.accessToken,
    newToken => {
      const appId = appIdRef.value;
      if (newToken && appId) void fetch(appId);
    },
  );

  return {
    score,
    isLoading,
    isError,
    refetch: (appId: string) => fetch(appId),
  };
}
