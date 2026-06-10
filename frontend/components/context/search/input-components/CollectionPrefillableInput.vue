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

async function fetchCollectionV2(collectionId: number): Promise<Collection> {
  const { data: session } = useAuth();
  const accessToken = session.value?.accessToken;
  const url = `${v2BaseUrl()}/v2/collections/${encodeURIComponent(String(collectionId))}`;
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

const collectionId = defineModel<number | undefined>("collectionId", {
  required: true,
});

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
  collectionSearchResults.value = [];
}

const isFetchLoading = ref(false);
if (collectionId.value) {
  isFetchLoading.value = true;
  try {
    const collection = await fetchCollectionV2(collectionId.value);
    selectedItem.value = mapToSearchResultAutoCompleteItem({
      collectionId: collection.id,
      collectionName: collection.name,
      // V2-LINKS: carry the appId through the search-result shape.
      collectionAppId: readCollectionAppId(collection),
    });
  } catch (e) {
    handleError(e, "fetching collection from url parameters");
  }
  isFetchLoading.value = false;
}

function mapToSearchResultAutoCompleteItem(
  searchResult: MyCollectionSearchResult,
): AutoCompleteItem {
  return {
    title: `${searchResult.collectionName} (ID: ${searchResult.collectionId})`,
    value: searchResult,
  };
}

const onSelect = (selectedItem: AutoCompleteItem | null) => {
  if (selectedItem?.value) {
    const collection = selectedItem.value as MyCollectionSearchResult;
    collectionId.value = collection.collectionId;
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
    :label="`Collection Name or ID...${props.isRequired ? `*` : ``}`"
    :model-value="selectedItem"
    :start-search="startSearch"
    clearable
    density="compact"
    @search-ended="onSelect"
  />
</template>
