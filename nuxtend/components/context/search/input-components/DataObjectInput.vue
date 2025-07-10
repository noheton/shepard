<script lang="ts" setup>
import type { AutoCompleteItem } from "~/components/common/AutocompleteInput.vue";

const props = defineProps<{
  collectionId: number;
  isRequired?: boolean;
  dataFromQueryParam?: boolean;
}>();

const dataObjectId = defineModel<number | undefined>("dataObjectId", {
  required: true,
});

const currentCollectionId = ref<number>(props.collectionId);

const emit = defineEmits<{
  (e: "dataObjectSelected", id: number): void;
  (e: "selectionCleared"): void;
}>();

const searchString = ref<string>("");
const searchDone = ref<boolean>(false);
const inputFromQueryParam = ref<boolean>(props.dataFromQueryParam);

const autoCompleteModel = ref<AutoCompleteItem | undefined>(undefined);

const { dataObjectSearchResults, isLoading, startSearch } = useDataObjectSearch(
  props.collectionId,
  searchString,
  () => {
    searchDone.value = true;
  },
);

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
    emit("dataObjectSelected", dataObject.dataObjectId);
  } else {
    emit("selectionCleared");
  }
};
</script>

<template>
  <DisplayDataObjectInput
    v-if="collectionId && dataObjectId && inputFromQueryParam"
    v-model:collection-id="currentCollectionId"
    v-model:data-object-id="dataObjectId"
    density="compact"
    @clicked="inputFromQueryParam = false"
  />

  <AutocompleteInput
    v-else
    v-model:search-done="searchDone"
    v-model:search-string="searchString"
    :is-loading="isLoading"
    :item-list="dataObjectSearchResults.map(mapToSearchResultAutoCompleteItem)"
    :label="`Data Object Name or ID...${props.isRequired ? `*` : ``}`"
    :model-value="autoCompleteModel"
    :start-search="startSearch"
    clearable
    density="compact"
    @search-ended="onSelect"
  />
</template>
