import type { Collection, ResponseError } from "@dlr-shepard/backend-client";
import { MeApi } from "@dlr-shepard/backend-client";
import { useV2ShepardApi } from "../common/api/useV2ShepardApi";
import { handleError } from "~/utils/errorBus";

const PREF_KEY = "bookmarkedCollections";

/**
 * BUG-COLL-APPID-ROUTE-003 (2026-06-02): `collectionId` is the stored handle —
 * UUID v7 (post-reset) or numeric long (legacy entries). v2 GET accepts both
 * via `EntityIdResolver`.
 */
interface BookmarkEntry {
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
const _bookmarks = ref<Collection[]>([]);
const _loading = ref(false);
let _rawEntries: BookmarkEntry[] = [];
let _loaded = false;

function parseEntries(raw: string | undefined): BookmarkEntry[] {
  if (!raw) return [];
  try {
    return JSON.parse(raw);
  } catch {
    return [];
  }
}

export function useBookmarkedCollections() {
  const meApi = useV2ShepardApi(MeApi);

  async function saveEntries(entries: BookmarkEntry[]) {
    const value = entries.length > 0 ? JSON.stringify(entries) : null;
    await meApi.value.patchPreferences({ [PREF_KEY]: value });
  }

  async function load() {
    if (_loaded || !import.meta.client) return;
    _loaded = true;
    _loading.value = true;
    try {
      const prefs = await meApi.value.getPreferences();
      _rawEntries = parseEntries(prefs[PREF_KEY]);
      if (_rawEntries.length === 0) return;

      const { data: session } = useAuth();
      const accessToken = session.value?.accessToken;
      // BUG-COLL-APPID-ROUTE-003: route through v2 — UUID-v7-only post-reset
      // Collections resolve, tombstoned legacy numeric ids 404 and drop.
      const settled = await Promise.allSettled(
        _rawEntries.map((e) =>
          fetchCollectionByAnyIdV2(e.collectionId, accessToken),
        ),
      );

      const live: Collection[] = [];
      const validEntries: BookmarkEntry[] = [];
      settled.forEach((result, idx) => {
        if (result.status === "fulfilled") {
          live.push(result.value);
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

      _bookmarks.value = live;
      _rawEntries = validEntries;

      // Persist cleaned-up list if any tombstones were removed.
      if (validEntries.length !== parseEntries(prefs[PREF_KEY]).length) {
        await saveEntries(validEntries);
      }
    } catch {
      // Non-critical — empty bookmarks is a safe default.
    } finally {
      _loading.value = false;
    }
  }

  function isBookmarked(collectionId: number | string): boolean {
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
    const wasBookmarked = isBookmarked(handle);

    // Optimistic update.
    if (wasBookmarked) {
      _bookmarks.value = _bookmarks.value.filter((c) => c.id !== numericId);
      _rawEntries = _rawEntries.filter(
        (e) => String(e.collectionId) !== String(handle),
      );
    } else {
      _bookmarks.value = [..._bookmarks.value, collection];
      _rawEntries = [
        ..._rawEntries,
        { collectionId: handle, name: collection.name ?? "" },
      ];
    }

    try {
      await saveEntries(_rawEntries);
    } catch (error) {
      // Revert on failure.
      if (wasBookmarked) {
        _bookmarks.value = [..._bookmarks.value, collection];
        _rawEntries = [
          ..._rawEntries,
          { collectionId: handle, name: collection.name ?? "" },
        ];
      } else {
        _bookmarks.value = _bookmarks.value.filter((c) => c.id !== numericId);
        _rawEntries = _rawEntries.filter(
          (e) => String(e.collectionId) !== String(handle),
        );
      }
      handleError(error, "saving bookmark");
    }
  }

  load();

  return {
    bookmarks: _bookmarks,
    bookmarksLoading: _loading,
    isBookmarked,
    toggle,
  };
}
