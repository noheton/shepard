<script lang="ts" setup>
import type { AutoCompleteItem } from "~/components/common/AutocompleteInput.vue";
import { useShepardApi } from "~/composables/common/api/useShepardApi";
import { DataObjectApi } from "@dlr-shepard/backend-client";

const props = defineProps<{
  collectionId: number;
  isRequired?: boolean;
}>();

const dataObjectId = defineModel<number | undefined>("dataObjectId", {
  required: true,
});

const searchString = ref<string>("");
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
}

watch(props, (newProps, oldProps) => {
  if (newProps.collectionId !== oldProps.collectionId) {
    reset();
  }
});

const fetchIsLoading = ref(false);
if (dataObjectId.value) {
  fetchIsLoading.value = true;
  try {
    const dataObject = await useShepardApi(DataObjectApi).value.getDataObject({
      collectionId: props.collectionId,
      dataObjectId: dataObjectId.value,
    });
    selectedItem.value = mapToSearchResultAutoCompleteItem({
      dataObjectId: dataObject.id,
      dataObjectName: dataObject.name,
    });
  } catch (error) {
    handleError(error, "fetching data object from url parameters");
  }
  fetchIsLoading.value = false;
}

function mapToSearchResultAutoCompleteItem(
  searchResult: DataObjectSearchResult,
): AutoCompleteItem {
  return {
    title: `${searchResult.dataObjectName} (ID: ${searchResult.dataObjectId})`,
    value: searchResult,
  };
}

const onSelect = (selectedItem: AutoCompleteItem | null) => {
  if (selectedItem?.value) {
    const dataObject = selectedItem.value as DataObjectSearchResult;
    dataObjectId.value = dataObject.dataObjectId;
  } else {
    reset();
  }
};
</script>

<template>
  <AutocompleteInput
    v-model:search-done="searchDone"
    v-model:search-string="searchString"
    :is-loading="isLoading || fetchIsLoading"
    :item-list="dataObjectSearchResults.map(mapToSearchResultAutoCompleteItem)"
    :label="`Data Object Name or ID...${props.isRequired ? `*` : ``}`"
    :model-value="selectedItem"
    :start-search="startSearch"
    clearable
    density="compact"
    @search-ended="onSelect"
  />
</template>
