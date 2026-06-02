import type { Collection, ResponseError } from "@dlr-shepard/backend-client";
import { MeApi } from "@dlr-shepard/backend-client";
import { useV2ShepardApi } from "../common/api/useV2ShepardApi";
import { handleError } from "~/utils/errorBus";

const PREF_KEY = "watchedCollections";

/**
 * BUG-COLL-APPID-ROUTE-003 (2026-06-02): `collectionId` is the
 * stored handle for the watched entry — either a UUID v7 (post-reset
 * Collections) or a numeric long (legacy stored entries pre-reset). The v2
 * `GET /v2/collections/{id}` endpoint accepts both shapes via
 * `EntityIdResolver`. The persisted JSON is forward-compat: existing numeric
 * entries keep working untouched.
 */
interface WatchedEntry {
  collectionId: number | string;
  name: string;
}

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

async function fetchCollectionByAnyIdV2(
  id: number | string,
  accessToken: string | undefined,
): Promise<Collection> {
  const url = `${v2BaseUrl()}/v2/collections/${encodeURIComponent(String(id))}`;
  const resp = await fetch(url, {
    headers: {
      ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
      Accept: "application/json",
    },
  });
  if (!resp.ok) {
    throw {
      response: resp,
      message: `HTTP ${resp.status}`,
    } as unknown as ResponseError;
  }
  return (await resp.json()) as Collection;
}

// Module-level singleton — all callers share one fetch.
const _watched = ref<Collection[]>([]);
const _loading = ref(false);
let _rawEntries: WatchedEntry[] = [];
let _loaded = false;

function parseEntries(raw: string | undefined): WatchedEntry[] {
  if (!raw) return [];
  try {
    return JSON.parse(raw);
  } catch {
    return [];
  }
}

export function useWatchedCollections() {
  const meApi = useV2ShepardApi(MeApi);

  async function saveEntries(entries: WatchedEntry[]) {
    const value = entries.length > 0 ? JSON.stringify(entries) : null;
    await meApi.value.patchPreferences({ [PREF_KEY]: value });
  }

  async function load() {
    if (_loaded || !import.meta.client) return;
    // AUTH-API-CALLS-UNGATED — skip when no session. The unconditional
    // load() call at module bottom previously fired on the landing page
    // and produced a 401 absorbed by useAuthRefreshMiddleware. Re-runs
    // on a later sign-in via the `status` watcher below.
    const { status, data: session } = useAuth();
    if (status.value !== "authenticated") return;
    _loaded = true;
    _loading.value = true;
    try {
      const prefs = await meApi.value.getPreferences();
      _rawEntries = parseEntries(prefs[PREF_KEY]);
      if (_rawEntries.length === 0) return;

      const accessToken = session.value?.accessToken;
      // BUG-COLL-APPID-ROUTE-003: route through v2 so UUID-v7-only
      // Collections (post-reset) resolve. Tombstoned numeric ids surface
      // as 404 and get filtered out as before.
      const settled = await Promise.allSettled(
        _rawEntries.map((e) =>
          fetchCollectionByAnyIdV2(e.collectionId, accessToken),
        ),
      );

      const live: Collection[] = [];
      const validEntries: WatchedEntry[] = [];
      settled.forEach((result, idx) => {
        if (result.status === "fulfilled") {
          live.push(result.value);
          // Prefer the appId for the persisted handle so post-reset
          // Collections (no numeric `id`) round-trip; fall back to the
          // numeric id for back-compat with legacy entries.
          const handle: number | string =
            (result.value as unknown as { appId?: string | null }).appId ??
            result.value.id ??
            _rawEntries[idx]!.collectionId;
          validEntries.push({
            collectionId: handle,
            name: result.value.name ?? _rawEntries[idx]!.name,
          });
        }
        // rejected → 404 or access lost → tombstone, skip
      });

      _watched.value = live;
      _rawEntries = validEntries;

      // Persist cleaned-up list if any tombstones were removed.
      if (validEntries.length !== parseEntries(prefs[PREF_KEY]).length) {
        await saveEntries(validEntries);
      }
    } catch {
      // Non-critical — empty watched list is a safe default.
    } finally {
      _loading.value = false;
    }
  }

  // BUG-COLL-APPID-ROUTE-003: callers still pass a numeric `collectionId`
  // (the page-level cast lies — post-reset that number is actually a UUID
  // string), so this helper compares against both the numeric id and the
  // string appId on each entry.
  function isWatched(collectionId: number | string): boolean {
    return _rawEntries.some(
      (e) =>
        e.collectionId === collectionId ||
        String(e.collectionId) === String(collectionId),
    );
  }

  async function toggle(collection: Collection) {
    const numericId = collection.id;
    const appId = (collection as unknown as { appId?: string | null }).appId;
    const handle: number | string | undefined = appId ?? numericId ?? undefined;
    if (handle === undefined || handle === null) return;
    const wasWatched = isWatched(handle);

    // Optimistic update.
    if (wasWatched) {
      _watched.value = _watched.value.filter((c) => c.id !== numericId);
      _rawEntries = _rawEntries.filter(
        (e) => String(e.collectionId) !== String(handle),
      );
    } else {
      _watched.value = [..._watched.value, collection];
      _rawEntries = [
        ..._rawEntries,
        { collectionId: handle, name: collection.name ?? "" },
      ];
    }

    try {
      await saveEntries(_rawEntries);
    } catch (error) {
      // Revert on failure.
      if (wasWatched) {
        _watched.value = [..._watched.value, collection];
        _rawEntries = [
          ..._rawEntries,
          { collectionId: handle, name: collection.name ?? "" },
        ];
      } else {
        _watched.value = _watched.value.filter((c) => c.id !== numericId);
        _rawEntries = _rawEntries.filter(
          (e) => String(e.collectionId) !== String(handle),
        );
      }
      handleError(error, "saving watched collection");
    }
  }

  // Initial attempt + re-attempt when auth status becomes authenticated.
  const { status } = useAuth();
  watch(status, () => { void load(); }, { immediate: true });

  return {
    watched: _watched,
    watchedLoading: _loading,
    isWatched,
    toggle,
  };
}
