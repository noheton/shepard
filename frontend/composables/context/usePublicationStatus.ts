/**
 * KIP1k — composable fetching the publications list for an entity
 * from {@code GET /v2/publications?entityAppId=…}.
 *
 * Returns the full list (most-recent first, including retired rows).
 * The
 * {@link PublicationStatusBadge} uses this to determine whether the
 * entity has at least one active (non-retired) Publication.
 *
 * Follows the same raw-fetch pattern as {@link usePublishEntity}:
 * the {@code @dlr-shepard/backend-client} regeneration for KIP1k
 * hasn't landed yet; raw fetch is the established bridge while the
 * typed client catches up.
 *
 * The composable is reactive on {@code entityAppId} — when the prop
 * changes (e.g. page navigation without a full route reload) the list
 * is re-fetched automatically.
 */

export type PublishableKind = "data-objects" | "collections";

export interface PublicationRecord {
  appId: string;
  pid: string;
  mintedAt: string | null;
  minterId: string | null;
  resolverUrl: string | null;
  publishedBy: string | null;
  entityKind: string | null;
  entityAppId: string | null;
  versionNumber: number | null;
  /** null = active; "retired" = soft-deleted per KIP1f */
  digitalObjectMutability: string | null;
}

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

/**
 * Fetch the Publications list for the given entity.
 *
 * @param kind       - entity kind URL segment ("data-objects" | "collections")
 * @param entityAppId - reactive ref/computed or plain string; pass null to skip
 */
export function usePublicationStatus(
  kind: PublishableKind,
  entityAppId: Ref<string | null | undefined> | ComputedRef<string | null | undefined> | (string | null | undefined),
) {
  const publications = ref<PublicationRecord[]>([]);
  const isFetching = ref(false);
  const fetchError = ref<string | null>(null);

  /**
   * Active publications: those where digitalObjectMutability is null
   * (i.e. not "retired"). Per the KIP1a append-only convention, the
   * most-recent non-retired row is the "current" Publication.
   */
  const activePublications = computed<PublicationRecord[]>(() =>
    publications.value.filter(p => p.digitalObjectMutability !== "retired"),
  );

  /** True when the entity has at least one active (non-retired) Publication. */
  const isPublished = computed(() => activePublications.value.length > 0);

  /** The most-recent active Publication, or null when unpublished. */
  const currentPublication = computed<PublicationRecord | null>(() =>
    activePublications.value[0] ?? null,
  );

  async function fetchPublications(appId: string | null | undefined) {
    if (!appId) {
      publications.value = [];
      return;
    }
    isFetching.value = true;
    fetchError.value = null;
    try {
      const { data: session } = useAuth();
      const accessToken = session.value?.accessToken;
      if (!accessToken) {
        // Not authenticated — silently leave the badge empty rather than
        // surfacing an error (the page will redirect to login if needed).
        publications.value = [];
        return;
      }
      const url = `${v2BaseUrl()}/v2/${kind}/${encodeURIComponent(appId)}/publications`;
      const response = await fetch(url, {
        headers: {
          Authorization: `Bearer ${accessToken}`,
          Accept: "application/json",
        },
      });
      if (!response.ok) {
        // Graceful degradation: 404 = entity has no publications yet
        // (or kind is unsupported), any other status = server error.
        // In all failure cases we just show "Unpublished" rather than
        // surfacing an error chip.
        publications.value = [];
        return;
      }
      const body = (await response.json()) as PublicationRecord[];
      publications.value = Array.isArray(body) ? body : [];
    } catch {
      // Network error — degrade gracefully.
      publications.value = [];
    } finally {
      isFetching.value = false;
    }
  }

  // Resolve the appId ref/computed/plain value and watch for changes.
  // toRef-wrapping a plain value produces a readonly ref; isRef check
  // lets us skip the wrapper when a ref is already provided.
  const appIdRef: Ref<string | null | undefined> = isRef(entityAppId)
    ? (entityAppId as Ref<string | null | undefined>)
    : computed(() => entityAppId as string | null | undefined);

  watch(
    appIdRef,
    val => { fetchPublications(val); },
    { immediate: true },
  );

  return {
    publications,
    activePublications,
    isPublished,
    currentPublication,
    isFetching,
    fetchError,
    /** Manually re-fetch (call after a publish action completes). */
    refresh: () => fetchPublications(appIdRef.value),
  };
}
