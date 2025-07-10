<script lang="ts" setup>
import type { AutoCompleteItem } from "~/components/common/AutocompleteInput.vue";
import {
  useCollectionSearch,
  type MyCollectionSearchResult,
} from "~/composables/context/useCollectionSearch";
import DisplayCollectionInput from "./DisplayCollectionInput.vue";

const props = defineProps<{
  isRequired?: boolean;
  dataFromQueryParam?: boolean;
}>();

const collectionId = defineModel<number | undefined>("collectionId", {
  required: true,
});

const emit = defineEmits<{
  (e: "collectionSelected", id: number): void;
  (e: "selectionCleared"): void;
}>();

const searchString = ref<string>("");
const searchDone = ref<boolean>(false);
const inputFromQueryParam = ref<boolean>(props.dataFromQueryParam);

const autoCompleteModel = ref<AutoCompleteItem | undefined>(undefined);

const { collectionSearchResults, startSearch, isLoading } = useCollectionSearch(
  searchString,
  () => {
    searchDone.value = true;
  },
);

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
    emit("collectionSelected", collection.collectionId);
  } else {
    emit("selectionCleared");
  }
};
</script>

<template>
  <DisplayCollectionInput
    v-if="collectionId && inputFromQueryParam"
    :collection-id="collectionId"
    density="compact"
    @clicked="inputFromQueryParam = false"
  />

  <AutocompleteInput
    v-else
    v-model:search-done="searchDone"
    v-model:search-string="searchString"
    :is-loading="isLoading"
    :item-list="collectionSearchResults.map(mapToSearchResultAutoCompleteItem)"
    :label="`Collection Name or ID...${props.isRequired ? `*` : ``}`"
    :model-value="autoCompleteModel"
    :start-search="startSearch"
    clearable
    density="compact"
    @search-ended="onSelect"
  />
</template>
