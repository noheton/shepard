<script setup lang="ts">
import { useTimeoutFn } from "@vueuse/core";
import {
  useCollectionSearch,
  type MyCollectionSearchResult,
} from "~/composables/context/useCollectionSearch";

interface AutoCompleteItem {
  title?: string;
  value?: MyCollectionSearchResult;
}

const emit = defineEmits<{
  (e: "searchEnded", value: { id: number; name: string } | null): void;
}>();

const searchString = ref<string>("");
const autoCompleteModel = ref<AutoCompleteItem | undefined>(undefined);
const hideNoDataMessage = ref<boolean>(true);

const { collectionSearchResults, startSearch, isLoading } = useCollectionSearch(
  searchString,
  () => {
    hideNoDataMessage.value = false;
  },
);

const { isPending, start } = useTimeoutFn(() => {
  if (searchString.value.trim() === "") {
    hideNoDataMessage.value = true;
  }
  startSearch();
}, 350);

const onSelection = (selectedItem: AutoCompleteItem | null) => {
  if (selectedItem && selectedItem.value) {
    autoCompleteModel.value = selectedItem;
    emit("searchEnded", {
      id: selectedItem.value.collectionId,
      name: selectedItem.value.collectionName,
    });
  } else {
    searchString.value = "";
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

function mapToSearchResultAutoCompleteItem(
  searchResult: MyCollectionSearchResult,
): AutoCompleteItem {
  return {
    title: `${searchResult.collectionName} (ID: ${searchResult.collectionId})`,
    value: searchResult,
  };
}
</script>

<template>
  <v-autocomplete
    :model-value="autoCompleteModel"
    :items="collectionSearchResults.map(mapToSearchResultAutoCompleteItem)"
    :loading="isLoading"
    :hide-no-data="hideNoDataMessage"
    label="Collection ID or Name...*"
    density="comfortable"
    variant="outlined"
    no-data-text="No Search Results"
    clearable
    color="primary"
    return-object
    hide-details
    @update:model-value="onSelection"
    @update:search="onSearch"
  />
</template>
