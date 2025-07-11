<script lang="ts" setup>
import type { AutoCompleteItem } from "~/components/common/AutocompleteInput.vue";
import {
  useCollectionSearch,
  type MyCollectionSearchResult,
} from "~/composables/context/useCollectionSearch";
import { useShepardApi } from "~/composables/common/api/useShepardApi";
import { CollectionApi } from "@dlr-shepard/backend-client";

const props = defineProps<{
  isRequired?: boolean;
}>();

const collectionId = defineModel<number | undefined>("collectionId", {
  required: true,
});

const searchString = ref<string>("");
const searchDone = ref<boolean>(false);

const selectedItem = ref<AutoCompleteItem | undefined>(undefined);

const { collectionSearchResults, startSearch, isLoading } = useCollectionSearch(
  searchString,
  () => {
    searchDone.value = true;
  },
);

function reset() {
  selectedItem.value = undefined;
  collectionId.value = undefined;
  collectionSearchResults.value = [];
}

const isFetchLoading = ref(false);
if (collectionId.value) {
  isFetchLoading.value = true;
  try {
    const collection = await useShepardApi(CollectionApi).value.getCollection({
      collectionId: collectionId.value,
    });
    selectedItem.value = mapToSearchResultAutoCompleteItem({
      collectionId: collection.id,
      collectionName: collection.name,
    });
  } catch (e) {
    handleError(e, "fetching collection from url parameters");
  }
  isFetchLoading.value = false;
}

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
  } else {
    reset();
  }
};
</script>

<template>
  <AutocompleteInput
    v-model:search-done="searchDone"
    v-model:search-string="searchString"
    :is-loading="isLoading || isFetchLoading"
    :item-list="collectionSearchResults.map(mapToSearchResultAutoCompleteItem)"
    :label="`Collection Name or ID...${props.isRequired ? `*` : ``}`"
    :model-value="selectedItem"
    :start-search="startSearch"
    clearable
    density="compact"
    @search-ended="onSelect"
  />
</template>
