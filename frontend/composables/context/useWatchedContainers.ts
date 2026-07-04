/**
 * WATCH1 — Collection "watched containers" CRUD wrapper.
 *
 * Wire endpoints:
 *   GET    /v2/collections/{appId}/watched-containers
 *   POST   /v2/collections/{appId}/watched-containers
 *   DELETE /v2/collections/{appId}/watched-containers/{watchAppId}
 *
 * The generated client doesn't cover these yet (new on this fork),
 * so we hit them via raw fetch — same pattern as the chart-view +
 * safe-delete composables.
 */

export type WatchedContainerKind = "TIMESERIES" | "FILE" | "STRUCTURED_DATA";

export type ContainerAvailability = "available" | "forbidden" | "deleted" | "error";

export interface WatchDto {
  watchAppId: string;
  containerKind: WatchedContainerKind;
  containerAppId: string;
  containerName?: string;
  containerAvailability?: ContainerAvailability;
  since?: number;
  addedBy?: string;
}

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = (config as { backendV2ApiUrl?: string }).backendV2ApiUrl;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

async function authHeaders(): Promise<Record<string, string>> {
  const { data: session } = useAuth();
  const accessToken = session.value?.accessToken;
  if (!accessToken) throw new Error("Not authenticated");
  return {
    Authorization: `Bearer ${accessToken}`,
    Accept: "application/json",
    "Content-Type": "application/json",
  };
}

export function useWatchedContainers(collectionAppId: Ref<string | undefined>) {
  const watches = ref<WatchDto[]>([]);
  const loading = ref(false);
  const mutating = ref(false);

  function url(): string | null {
    if (!collectionAppId.value) return null;
    return `${v2BaseUrl()}/v2/collections/${collectionAppId.value}/watched-containers`;
  }

  async function refresh() {
    const base = url();
    if (!base) {
      watches.value = [];
      return;
    }
    loading.value = true;
    try {
      const headers = await authHeaders();
      const response = await fetch(base, { headers });
      if (response.ok) {
        watches.value = (await response.json()) as WatchDto[];
      } else if (response.status === 404 || response.status === 403) {
        // 404 = collection has no appId yet (pre-L2a) or doesn't exist
        // 403 = caller lacks Read on the collection
        watches.value = [];
      } else {
        throw new Error(`HTTP ${response.status}`);
      }
    } catch (e) {
      handleError(e as Error, "fetching watched containers");
    } finally {
      loading.value = false;
    }
  }

  async function add(
    kind: WatchedContainerKind,
    containerAppId: string,
  ): Promise<boolean> {
    const base = url();
    if (!base) return false;
    mutating.value = true;
    try {
      const headers = await authHeaders();
      const response = await fetch(base, {
        method: "POST",
        headers,
        body: JSON.stringify({ containerKind: kind, containerAppId }),
      });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const created = (await response.json()) as WatchDto;
      // De-dup by watchAppId — POST is idempotent server-side
      const idx = watches.value.findIndex(w => w.watchAppId === created.watchAppId);
      if (idx >= 0) watches.value[idx] = created;
      else watches.value.push(created);
      emitSuccess("Watch added");
      return true;
    } catch (e) {
      handleError(e as Error, "adding watch");
      return false;
    } finally {
      mutating.value = false;
    }
  }

  async function remove(watchAppId: string): Promise<boolean> {
    const base = url();
    if (!base) return false;
    mutating.value = true;
    try {
      const headers = await authHeaders();
      const response = await fetch(`${base}/${watchAppId}`, {
        method: "DELETE",
        headers,
      });
      if (response.ok || response.status === 404) {
        watches.value = watches.value.filter(w => w.watchAppId !== watchAppId);
        emitSuccess("Watch removed");
        return true;
      }
      throw new Error(`HTTP ${response.status}`);
    } catch (e) {
      handleError(e as Error, "removing watch");
      return false;
    } finally {
      mutating.value = false;
    }
  }

  watch(collectionAppId, () => refresh(), { immediate: true });

  return { watches, loading, mutating, refresh, add, remove };
}
