<script lang="ts" setup>
import type { AutoCompleteItem } from "~/components/common/AutocompleteInput.vue";
import {
  useCollectionSearch,
  type MyCollectionSearchResult,
} from "~/composables/context/useCollectionSearch";
import type { Collection, ResponseError } from "@dlr-shepard/backend-client";
import { readCollectionAppId } from "~/utils/appId";

/**
 * BUG-COLL-APPID-ROUTE-005 (2026-06-02): the prefill lookup routes through
 * `GET /v2/collections/{collectionAppId}`. The generated v1 `getCollection`
 * expects a numeric Neo4j long; post-reset Collections carry UUID v7 only
 * so the prefill chip stayed empty.
 */
function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

async function fetchCollectionV2(collectionIdOrAppId: string): Promise<Collection> {
  const { data: session } = useAuth();
  const accessToken = session.value?.accessToken;
  const url = `${v2BaseUrl()}/v2/collections/${encodeURIComponent(collectionIdOrAppId)}`;
  const headers: Record<string, string> = { Accept: "application/json" };
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`;
  const resp = await fetch(url, { headers });
  if (!resp.ok) {
    throw {
      response: resp,
      message: `HTTP ${resp.status}`,
    } as unknown as ResponseError;
  }
  return (await resp.json()) as Collection;
}

const props = defineProps<{
  isRequired?: boolean;
}>();

/**
 * SEARCH-V2-2: Primary model is now `collectionAppId` (UUID v7).
 * `collectionId` (numeric Neo4j id) is kept for v1-only callers that
 * genuinely need it (e.g. v1 search scope in search/index.vue — V1-EXCEPTION
 * until SEARCH-V2-3 ships v2 scope). When selected from the v2 search,
 * `collectionId` is undefined (v2 search does not return numeric ids).
 * v1-only callers must resolve the numeric id from the loaded Collection entity.
 */
const collectionId = defineModel<number | undefined>("collectionId");
const collectionAppId = defineModel<string | undefined>("collectionAppId");

const searchString = ref<string>("");
const searchDone = ref<boolean>(false);

const selectedItem = ref<AutoCompleteItem | undefined>(undefined);

const { collectionSearchResults, startSearch, isLoading } = useCollectionSearch(
  searchString,
  () => {
    searchDone.value = true;
  },
);

function reset() {
  selectedItem.value = undefined;
  collectionId.value = undefined;
  collectionAppId.value = undefined;
  collectionSearchResults.value = [];
}

const isFetchLoading = ref(false);
// Prefill: accept either a numeric id or an appId string.
const prefillSeed = collectionId.value
  ? String(collectionId.value)
  : collectionAppId.value;
if (prefillSeed) {
  isFetchLoading.value = true;
  try {
    const collection = await fetchCollectionV2(prefillSeed);
    const appId = readCollectionAppId(collection);
    selectedItem.value = mapToSearchResultAutoCompleteItem({
      collectionId: collection.id,
      collectionName: collection.name,
      collectionAppId: appId,
    });
    // Populate both models from the fetched collection.
    if (collection.id) collectionId.value = collection.id;
    if (appId) collectionAppId.value = appId;
  } catch (e) {
    handleError(e, "fetching collection from url parameters");
  }
  isFetchLoading.value = false;
}

function mapToSearchResultAutoCompleteItem(
  searchResult: MyCollectionSearchResult,
): AutoCompleteItem {
  // SEARCH-V2-2: v2 search returns appId only (no numeric id). Display the
  // first 8 chars of the appId as a human-readable identifier.
  const idSuffix = searchResult.collectionAppId
    ? searchResult.collectionAppId.slice(0, 8)
    : searchResult.collectionId || "";
  return {
    title: `${searchResult.collectionName} (${idSuffix})`,
    value: searchResult,
  };
}

const onSelect = (item: AutoCompleteItem | null) => {
  if (item?.value) {
    const collection = item.value as MyCollectionSearchResult;
    // v2 search results have no numeric id — leave collectionId undefined.
    collectionId.value = collection.collectionId || undefined;
    collectionAppId.value = collection.collectionAppId ?? undefined;
  } else {
    reset();
  }
};
</script>

<template>
  <AutocompleteInput
    v-model:search-done="searchDone"
    v-model:search-string="searchString"
    :is-loading="isLoading || isFetchLoading"
    :item-list="collectionSearchResults.map(mapToSearchResultAutoCompleteItem)"
    :label="`Collection Name...${props.isRequired ? `*` : ``}`"
    :model-value="selectedItem"
    :start-search="startSearch"
    clearable
    density="compact"
    @search-ended="onSelect"
  />
</template>
