<script lang="ts" setup>
import type { AutoCompleteItem } from "~/components/common/AutocompleteInput.vue";
import type { DataObject, ResponseError } from "@dlr-shepard/backend-client";
import { readDataObjectAppId } from "~/utils/appId";

/**
 * BUG-COLL-APPID-ROUTE-005 (2026-06-02): the prefill lookup routes through
 * `GET /v2/collections/{collectionAppId}/data-objects/{dataObjectAppId}`.
 * The generated v1 `getDataObject` expects numeric Neo4j longs; post-reset
 * DataObjects carry UUID v7 only so the prefill chip stayed empty.
 */
function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

async function fetchDataObjectV2(
  collectionId: number,
  dataObjectId: number,
): Promise<DataObject> {
  const { data: session } = useAuth();
  const accessToken = session.value?.accessToken;
  const url =
    `${v2BaseUrl()}/v2/collections/` +
    `${encodeURIComponent(String(collectionId))}/data-objects/` +
    `${encodeURIComponent(String(dataObjectId))}`;
  const headers: Record<string, string> = { Accept: "application/json" };
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`;
  const resp = await fetch(url, { headers });
  if (!resp.ok) {
    throw {
      response: resp,
      message: `HTTP ${resp.status}`,
    } as unknown as ResponseError;
  }
  return (await resp.json()) as DataObject;
}

const props = defineProps<{
  collectionId: number;
  isRequired?: boolean;
}>();

const dataObjectId = defineModel<number | undefined>("dataObjectId", {
  required: true,
});

const searchString = ref<string | undefined>();
const searchDone = ref<boolean>(false);

const selectedItem = ref<AutoCompleteItem | undefined>(undefined);

const { dataObjectSearchResults, isLoading, startSearch } = useDataObjectSearch(
  props.collectionId,
  searchString,
  () => {
    searchDone.value = true;
  },
);

function reset() {
  dataObjectId.value = undefined;
  selectedItem.value = undefined;
  dataObjectSearchResults.value = [];
  searchString.value = undefined;
  searchDone.value = false;
}

watch(dataObjectId, () => {
  if (dataObjectId.value === undefined) reset();
});

watch(selectedItem, () => {
  if (selectedItem.value) {
    const res = selectedItem.value?.value as DataObjectSearchResult;
    dataObjectId.value = res.dataObjectId;
  }
});

if (dataObjectId.value) {
  isLoading.value = true;
  try {
    const dataObject = await fetchDataObjectV2(
      props.collectionId,
      dataObjectId.value,
    );
    selectedItem.value = mapToSearchResultAutoCompleteItem({
      dataObjectId: dataObject.id,
      dataObjectName: dataObject.name,
      // V2-LINKS: carry the appId through the search-result shape.
      dataObjectAppId: readDataObjectAppId(dataObject),
    });
  } catch (error) {
    handleError(error, "fetching data object from url parameters");
  }
  isLoading.value = false;
}

function mapToSearchResultAutoCompleteItem(
  searchResult: DataObjectSearchResult,
): AutoCompleteItem {
  return {
    title: `${searchResult.dataObjectName} (ID: ${searchResult.dataObjectId})`,
    value: searchResult,
  };
}

const itemList = computed(() =>
  dataObjectSearchResults.value.map(mapToSearchResultAutoCompleteItem),
);

watch(searchString, (newValue, _) => {
  if (!newValue) reset();
});
</script>

<template>
  <AutocompleteInput
    v-model="selectedItem"
    v-model:search-done="searchDone"
    v-model:search-string="searchString"
    :is-loading="isLoading"
    :item-list="itemList"
    :label="`Data Object Name or ID...${props.isRequired ? `*` : ``}`"
    :start-search="() => startSearch(collectionId)"
    clearable
    density="compact"
    @click:clear="reset"
  />
</template>
