<script lang="ts" setup>
import type { DataObject, ResponseError } from "@dlr-shepard/backend-client";
import { useTimeoutFn } from "@vueuse/core";

/**
 * BUG-COLL-APPID-ROUTE-005 (2026-06-02): the initial-selection lookup
 * routes through `GET /v2/collections/{collectionAppId}/data-objects/
 * {dataObjectAppId}`. Pre-fix the generated v1 `getDataObject` was called
 * with numeric Neo4j longs — post-reset Collections / DataObjects carry
 * UUID v7 only, so the autocomplete failed to populate the chip when a
 * dialog opened with an existing `initialDataObjectId`.
 */
function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

interface AutoCompleteItem {
  title?: string;
  value?: DataObjectSearchResult;
}

interface DataObjectAutocompleteProps {
  collectionId: number;
  /** UUID v7 of the owning collection — when supplied, search uses GET /v2/search (SEARCH-V2-3). */
  collectionAppId?: string;
  initialDataObjectId?: number | null;
  inputLabel: string;
  isDisabled?: boolean;
}

const props = defineProps<DataObjectAutocompleteProps>();
const emit = defineEmits<{
  (e: "searchEnded", value: { id: number; appId: string | null; name: string } | null): void;
}>();
defineExpose({
  clearInput,
});

const autoCompleteModel = ref<AutoCompleteItem | undefined>(undefined);
const searchString = ref<string | undefined>(undefined);
const hideNoDataMessage = ref<boolean>(true);

const { dataObjectSearchResults, startSearch, isLoading } = useDataObjectSearch(
  props.collectionId,
  searchString,
  () => {
    hideNoDataMessage.value = false;
  },
  props.collectionAppId,
);

const { isPending, start } = useTimeoutFn(() => {
  if (!searchString.value) {
    hideNoDataMessage.value = true;
  }
  startSearch(props.collectionId, props.collectionAppId);
}, 350);

const onSelection = (selectedItem: AutoCompleteItem | null) => {
  if (selectedItem && selectedItem.value) {
    autoCompleteModel.value = selectedItem;
    emit("searchEnded", {
      id: selectedItem.value.dataObjectId,
      appId: selectedItem.value.dataObjectAppId ?? null,
      name: selectedItem.value.dataObjectName,
    });
  } else {
    searchString.value = undefined;
    autoCompleteModel.value = undefined;
    emit("searchEnded", null);
  }
};

const onSearch = async (search: string) => {
  searchString.value = search;
  if (isPending.value === false) {
    start();
  }
};

onMounted(async () => {
  if (props.initialDataObjectId && props.initialDataObjectId != -1) {
    const initialDataObject = await getDataObjectById(
      props.initialDataObjectId,
    );
    autoCompleteModel.value = {
      title: `${initialDataObject.name} (ID: ${initialDataObject.id})`,
    };
  }
});

async function getDataObjectById(dataObjectId: number): Promise<DataObject> {
  const { data: session } = useAuth();
  const accessToken = session.value?.accessToken;
  const url =
    `${v2BaseUrl()}/v2/collections/` +
    `${encodeURIComponent(String(props.collectionId))}/data-objects/` +
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

function mapToSearchResultAutoCompleteItem(
  searchResult: DataObjectSearchResult,
): AutoCompleteItem {
  return {
    title: `${searchResult.dataObjectName} (ID: ${searchResult.dataObjectId})`,
    value: searchResult,
  };
}

function clearInput() {
  autoCompleteModel.value = undefined;
}
</script>

<template>
  <v-autocomplete
    :disabled="props.isDisabled"
    :hide-no-data="hideNoDataMessage"
    :items="dataObjectSearchResults.map(mapToSearchResultAutoCompleteItem)"
    :label="props.inputLabel"
    :loading="isLoading"
    :model-value="autoCompleteModel"
    clearable
    color="primary"
    density="comfortable"
    hide-details
    no-data-text="No Search Results"
    return-object
    variant="outlined"
    @update:model-value="onSelection"
    @update:search="onSearch"
  />
</template>
