/**
 * SNAPSHOT-LIST-1-FE — composable wrapping the global snapshot list
 * endpoint shipped 2026-05-31 (backend 1935128eb):
 *
 *   GET /v2/snapshots[?collectionAppId=…][&page=N&size=M]
 *
 * Response envelope: `{ items[], total, page, pageSize }` where each item is
 * `{ appId, name, createdAt, collectionAppId, collectionName }`.
 *
 * The `/snapshots/diff` picker fetches a single page (`size=200`) on
 * mount and lets v-autocomplete filter client-side — covers any
 * realistic instance size; remote search with debounce is queued as a
 * follow-up if pagination becomes load-bearing.
 */

export interface SnapshotListItem {
  appId: string;
  name: string;
  createdAt: string;
  collectionAppId: string | null;
  collectionName: string | null;
}

export interface SnapshotListPage {
  items: SnapshotListItem[];
  total: number;
  page: number;
  pageSize: number;
}

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

export function useSnapshotList() {
  const items = ref<SnapshotListItem[]>([]);
  const total = ref(0);
  const isLoading = ref(false);
  const error = ref<string | null>(null);

  async function fetchPage(opts: {
    collectionAppId?: string;
    page?: number;
    size?: number;
  } = {}): Promise<SnapshotListItem[]> {
    isLoading.value = true;
    error.value = null;
    try {
      const params = new URLSearchParams();
      if (opts.collectionAppId) params.set("collectionAppId", opts.collectionAppId);
      params.set("page", String(opts.page ?? 0));
      params.set("size", String(opts.size ?? 200));
      const { data: auth } = useAuth();
      const token = auth.value?.accessToken;
      const headers: Record<string, string> = { Accept: "application/json" };
      if (token) headers["Authorization"] = `Bearer ${token}`;
      const response = await fetch(`${v2BaseUrl()}/v2/snapshots?${params}`, { headers });
      if (!response.ok) {
        const text = await response.text().catch(() => "");
        error.value = `HTTP ${response.status}${text ? ": " + text.slice(0, 200) : ""}`;
        return items.value;
      }
      const page = (await response.json()) as SnapshotListPage;
      items.value = page.items ?? [];
      total.value = page.total ?? items.value.length;
      return items.value;
    } catch (e) {
      error.value = e instanceof Error ? e.message : "Failed to list snapshots";
      return items.value;
    } finally {
      isLoading.value = false;
    }
  }

  return { items, total, isLoading, error, fetchPage };
}
