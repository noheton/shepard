<script setup lang="ts">
import type { AutoCompleteItem } from "~/components/common/AutocompleteInput.vue";
import {
  useCollectionSearch,
  type MyCollectionSearchResult,
} from "~/composables/context/useCollectionSearch";

const props = defineProps<{
  isRequired?: boolean;
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
  <v-row>
    <v-col cols="4">
      <AutocompleteInput
        v-model:search-string="searchString"
        v-model:search-done="searchDone"
        :model-value="autoCompleteModel"
        :label="`Collection Name or ID...${props.isRequired ? `*` : ``}`"
        density="compact"
        :is-loading="isLoading"
        :item-list="
          collectionSearchResults.map(mapToSearchResultAutoCompleteItem)
        "
        clearable
        :start-search="startSearch"
        @search-ended="onSelect"
      />
    </v-col>
  </v-row>
</template>
