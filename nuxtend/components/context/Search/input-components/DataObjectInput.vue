<script setup lang="ts">
import type { AutoCompleteItem } from "~/components/common/AutocompleteInput.vue";

const props = defineProps<{
  collectionId: number;
  isRequired?: boolean;
}>();

const dataObjectId = defineModel<number | undefined>("dataObjectId", {
  required: true,
});

const emit = defineEmits<{
  (e: "dataObjectSelected", id: number): void;
  (e: "selectionCleared"): void;
}>();

const searchString = ref<string>("");
const searchDone = ref<boolean>(false);

const autoCompleteModel = ref<AutoCompleteItem | undefined>(undefined);

const { dataObjectSearchResults, isLoading, startSearch } = useDataObjectSearch(
  props.collectionId,
  searchString,
  () => {
    searchDone.value = true;
  },
);

function mapToSearchResultAutoCompleteItem(
  searchResult: MyDataObjectSearchResult,
): AutoCompleteItem {
  return {
    title: `${searchResult.dataObjectName} (ID: ${searchResult.dataObjectId})`,
    value: searchResult,
  };
}

const onSelect = (selectedItem: AutoCompleteItem | null) => {
  if (selectedItem?.value) {
    const dataObject = selectedItem.value as MyDataObjectSearchResult;
    dataObjectId.value = dataObject.dataObjectId;
    emit("dataObjectSelected", dataObject.dataObjectId);
  } else {
    emit("selectionCleared");
  }
};
</script>

<template>
  <v-row>
    <v-col cols="4">
      <AutocompleteInput
        v-model:search-string="searchString"
        v-model:search-done="searchDone"
        :model-value="autoCompleteModel"
        :label="`Data Object Name or ID...${props.isRequired ? `*` : ``}`"
        density="compact"
        :is-loading="isLoading"
        :item-list="
          dataObjectSearchResults.map(mapToSearchResultAutoCompleteItem)
        "
        clearable
        :start-search="startSearch"
        @search-ended="onSelect"
      />
    </v-col>
  </v-row>
</template>
