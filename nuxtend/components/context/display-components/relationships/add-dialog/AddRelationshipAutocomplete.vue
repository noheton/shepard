<script setup lang="ts">
import type { AutoCompleteItem } from "~/components/common/AutocompleteInput.vue";

const emit = defineEmits<{
  (e: "relationshipSelect", value: number): void;
}>();

const searchString = ref<string | undefined>(undefined);
const searchDone = ref<boolean>(false);

const props = defineProps<{
  collectionId: number;
}>();

const { dataObjectSearchResults, isLoading, startSearch } = useDataObjectSearch(
  props.collectionId,
  searchString,
  () => {
    searchDone.value = true;
  },
);

const model = defineModel<DataObjectSearchResult>();

const mapToAutocompleteItem = (
  dataObject: DataObjectSearchResult,
): AutoCompleteItem => ({
  title: `${dataObject.dataObjectName} (ID: ${dataObject.dataObjectId})`,
  value: dataObject,
});

const onSelect = (selectedItem: AutoCompleteItem | null) => {
  if (selectedItem?.value) {
    emit(
      "relationshipSelect",
      (selectedItem.value as DataObjectSearchResult).dataObjectId,
    );
  }
};
</script>

<template>
  <AutocompleteInput
    v-model:search-string="searchString"
    v-model:search-done="searchDone"
    :model-value="model ? mapToAutocompleteItem(model) : undefined"
    label="Predecessor Name or ID..."
    density="compact"
    :is-loading="isLoading"
    :item-list="dataObjectSearchResults.map(mapToAutocompleteItem)"
    :start-search="startSearch"
    @search-ended="onSelect"
  />
</template>
