/**
 * MFFD-IMAGEBUNDLE-PANE-MOUNT-1 — fetches the groups of a FileBundleReference
 * via `GET /v2/bundles/{bundleAppId}/groups`.
 *
 * Used by DataObjectImageBundlePane to discover which group to pass to the
 * ImageBundleViewer scrubber. Most image bundles have exactly one group, but
 * the composable handles multi-group bundles by returning the full list so the
 * pane can surface a group picker.
 *
 * Only fetches when `bundleAppId` is non-empty. Returns an empty array on
 * 404/403 so the pane degrades gracefully.
 */

interface BundleGroupIO {
  appId: string;
  name: string;
  containerMongoId?: string | null;
}

interface PagedBundleGroups {
  items: BundleGroupIO[];
  page?: number;
  size?: number;
  totalElements?: number;
  totalPages?: number;
}

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

export function useImageBundleGroups(
  bundleAppId: Ref<string | null | undefined> | string | null | undefined,
) {
  const groups = ref<BundleGroupIO[]>([]);
  const loading = ref(false);
  const error = ref<string | null>(null);

  async function fetchGroups(appId: string | null | undefined): Promise<void> {
    if (!appId) {
      groups.value = [];
      return;
    }
    loading.value = true;
    error.value = null;
    try {
      const { data: session } = useAuth();
      const accessToken = session.value?.accessToken;
      const url = `${v2BaseUrl()}/v2/bundles/${encodeURIComponent(appId)}/groups`;
      const response = await fetch(url, {
        headers: {
          Authorization: `Bearer ${accessToken}`,
          Accept: "application/json",
        },
      });
      if (!response.ok) {
        if (response.status === 404 || response.status === 403) {
          groups.value = [];
          return;
        }
        throw new Error(`HTTP ${response.status}`);
      }
      const paged = (await response.json()) as PagedBundleGroups;
      groups.value = paged.items ?? [];
    } catch (e) {
      error.value = e instanceof Error ? e.message : "Failed to load bundle groups";
      groups.value = [];
    } finally {
      loading.value = false;
    }
  }

  const resolvedRef = isRef(bundleAppId)
    ? bundleAppId
    : computed(() => bundleAppId ?? null);

  watch(
    resolvedRef,
    (appId) => fetchGroups(appId ?? null),
    { immediate: true },
  );

  return { groups, loading, error };
}

export type { BundleGroupIO };
