import { CollectionApi, type Collection } from "@dlr-shepard/backend-client";
import { MeApi } from "@dlr-shepard/backend-client";
import { useV2ShepardApi } from "../common/api/useV2ShepardApi";
import { useShepardApi } from "../common/api/useShepardApi";
import { handleError } from "~/utils/errorBus";

const PREF_KEY = "bookmarkedCollections";

interface BookmarkEntry {
  collectionId: number;
  name: string;
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
  const collectionApi = useShepardApi(CollectionApi);

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

      // Fetch live collection data and remove any tombstoned IDs in parallel.
      const settled = await Promise.allSettled(
        _rawEntries.map((e) =>
          collectionApi.value.getCollection({ collectionId: e.collectionId }),
        ),
      );

      const live: Collection[] = [];
      const validEntries: BookmarkEntry[] = [];
      settled.forEach((result, idx) => {
        if (result.status === "fulfilled") {
          live.push(result.value);
          validEntries.push({
            collectionId: result.value.id!,
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

  function isBookmarked(collectionId: number): boolean {
    return _rawEntries.some((e) => e.collectionId === collectionId);
  }

  async function toggle(collection: Collection) {
    if (!collection.id) return;
    const id = collection.id;
    const wasBookmarked = isBookmarked(id);

    // Optimistic update.
    if (wasBookmarked) {
      _bookmarks.value = _bookmarks.value.filter((c) => c.id !== id);
      _rawEntries = _rawEntries.filter((e) => e.collectionId !== id);
    } else {
      _bookmarks.value = [..._bookmarks.value, collection];
      _rawEntries = [..._rawEntries, { collectionId: id, name: collection.name ?? "" }];
    }

    try {
      await saveEntries(_rawEntries);
    } catch (error) {
      // Revert on failure.
      if (wasBookmarked) {
        _bookmarks.value = [..._bookmarks.value, collection];
        _rawEntries = [..._rawEntries, { collectionId: id, name: collection.name ?? "" }];
      } else {
        _bookmarks.value = _bookmarks.value.filter((c) => c.id !== id);
        _rawEntries = _rawEntries.filter((e) => e.collectionId !== id);
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
