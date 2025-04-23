<script setup lang="ts">
import type { ContainerType } from "@dlr-shepard/backend-client";
import { useTimeoutFn } from "@vueuse/core";
import {
  useContainerSearch,
  type MyContainerSearchResult,
} from "~/composables/context/useContainerSearch";

interface AutoCompleteItem {
  title?: string;
  value?: MyContainerSearchResult;
}

interface ContainerAutocompleteProps {
  initialContainerId: number | null;
  inputLabel: string;
}

const props = defineProps<ContainerAutocompleteProps>();
const emit = defineEmits<{
  (e: "searchEnded", id: number | null, type: ContainerType | null): void;
}>();

const autoCompleteModel = ref<AutoCompleteItem | undefined>(undefined);
const searchString = ref<string | undefined>(undefined);
const hideNoDataMessage = ref<boolean>(true);

const { containerSearchResults, startSearch, isLoading } = useContainerSearch(
  searchString,
  () => {
    hideNoDataMessage.value = false;
  },
);

const { isPending, start } = useTimeoutFn(() => {
  if (!searchString.value) {
    hideNoDataMessage.value = true;
  }
  startSearch();
}, 350);

const onSelection = (selectedItem: AutoCompleteItem | null) => {
  if (selectedItem && selectedItem.value) {
    autoCompleteModel.value = selectedItem;
    emit(
      "searchEnded",
      selectedItem.value.containerId,
      selectedItem.value.containerType,
    );
  } else {
    searchString.value = undefined;
    autoCompleteModel.value = undefined;
    emit("searchEnded", null, null);
  }
};

const onSearch = async (search: string) => {
  searchString.value = search;
  if (isPending.value === false) {
    start();
  }
};

function mapToSearchResultAutoCompleteItem(
  searchResult: MyContainerSearchResult,
): AutoCompleteItem {
  return {
    title: `${searchResult.containerName} (ID: ${searchResult.containerId}) (Type: ${searchResult.containerType})`,
    value: searchResult,
  };
}
</script>

<template>
  <v-autocomplete
    :model-value="autoCompleteModel"
    :items="containerSearchResults.map(mapToSearchResultAutoCompleteItem)"
    :loading="isLoading"
    :hide-no-data="hideNoDataMessage"
    :label="props.inputLabel"
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
