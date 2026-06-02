/**
 * PROJ-BADGE-1 — shared lightweight lookup of "is this Collection a Project?"
 * for use by CollectionList row chips.
 *
 * Loads /v2/projects once (a small array of appIds) and exposes a reactive
 * Set so per-row lookups are O(1). The composable returns the same Set
 * across calls within a request so multiple rows in the same render share
 * one fetch.
 */

const cache = ref<Set<string>>(new Set());
const isLoaded = ref(false);
let inflight: Promise<void> | null = null;

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

async function ensureLoaded(): Promise<void> {
  if (isLoaded.value) return;
  if (inflight) {
    await inflight;
    return;
  }
  inflight = (async () => {
    try {
      const { data: session } = useAuth();
      const accessToken = session.value?.accessToken;
      const response = await fetch(`${v2BaseUrl()}/v2/projects`, {
        headers: {
          Authorization: `Bearer ${accessToken}`,
          Accept: "application/json",
        },
      });
      if (!response.ok) return;
      const appIds: string[] = await response.json();
      cache.value = new Set(appIds);
      isLoaded.value = true;
    } catch (e) {
      // Best-effort; if it fails we just don't render the chips.
      handleError(e, "loading Project membership set");
    } finally {
      inflight = null;
    }
  })();
  await inflight;
}

/**
 * Reactive "is this Collection a Project?" lookup. Triggers the one-shot load
 * if not yet hydrated; safe to call from inside a v-if.
 */
export function useProjectMembership() {
  void ensureLoaded();

  function isProject(collectionAppId: string | null | undefined): boolean {
    if (!collectionAppId) return false;
    return cache.value.has(collectionAppId);
  }

  return { isProject, projectAppIds: cache, isLoaded };
}
